package com.cobblespawnregions.utils

import com.cobblespawnregions.CobbleSpawnRegions
import com.cobblespawnregions.StickMode
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Display
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import com.mojang.math.Transformation
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


object RegionParticleUtils {



    data class BoxRequest(
        val minX: Double, val minY: Double, val minZ: Double,
        val maxX: Double, val maxY: Double, val maxZ: Double,
        val state: BlockState
    )

    class PlayerVisualState {
        var basePos: Vec3 = Vec3.ZERO
        val entities = mutableListOf<Display.BlockDisplay>()

        fun clear(player: ServerPlayer) {
            if (entities.isNotEmpty()) {
                player.connection.send(ClientboundRemoveEntitiesPacket(*entities.map { it.id }.toIntArray()))
                entities.clear()
            }
        }
    }

    private val activeVisualStates = ConcurrentHashMap<UUID, PlayerVisualState>()
    private val spawnParticleLastSentAt = ConcurrentHashMap<String, Long>()

    private const val SPAWN_PARTICLE_RADIUS_SQ = 25.0 * 25.0
    private const val SPAWN_PARTICLE_INTERVAL_MS = 2_000L

    private data class RegionVisualPalette(
        val face: BlockState,
        val frame: BlockState,
        val edge: BlockState
    )

    private val priorityPalettes = listOf(
        RegionVisualPalette(Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState()),
        RegionVisualPalette(Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState(), Blocks.ORANGE_CONCRETE.defaultBlockState()),
        RegionVisualPalette(Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState()),
        RegionVisualPalette(Blocks.LIME_STAINED_GLASS.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState(), Blocks.LIME_CONCRETE.defaultBlockState()),
        RegionVisualPalette(Blocks.GREEN_STAINED_GLASS.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState())
    )



    fun updateParticles(server: MinecraftServer) {
        val playersToCheck = HashSet<UUID>()
        playersToCheck.addAll(CobbleSpawnRegions.particleUpdatePlayers)
        playersToCheck.addAll(activeVisualStates.keys)
        if (playersToCheck.isEmpty()) return

        val playersToUpdate = mutableSetOf<UUID>()
        var priorityIndexCache: Map<String, Int>? = null

        playersToCheck.forEach { uuid ->
            val player = server.playerList.getPlayer(uuid)
            if (player == null) {
                CobbleSpawnRegions.particleUpdatePlayers.remove(uuid)
                activeVisualStates.remove(uuid)
                clearSpawnParticleState(uuid)
                return@forEach
            }

            val requests = mutableListOf<BoxRequest>()

            val sel = CobbleSpawnRegions.playerSelections[player.uuid]
            if (sel != null) {
                playersToUpdate.add(player.uuid)
                buildSelectionRequests(player, sel, requests)
            }

            val regionIds = CobbleSpawnRegions.activeVisualizations[player.uuid]
            if (regionIds != null) {
                val missing = mutableListOf<String>()
                val priorityIndex = priorityIndexCache ?: RegionsConfig.regionsInPriorityOrder()
                    .mapIndexed { index, region -> region.regionId to index }
                    .toMap()
                    .also { priorityIndexCache = it }
                val priorityRegionCount = priorityIndex.size
                regionIds.forEach { regionId ->
                    val region = RegionsConfig.getRegion(regionId)
                    if (region != null) {
                        playersToUpdate.add(player.uuid)
                        buildRegionRequests(
                            region,
                            priorityIndex[region.regionId] ?: 0,
                            priorityRegionCount,
                            requests
                        )
                        spawnPointParticles(player, regionId)
                    } else {
                        missing.add(regionId)
                    }
                }
                missing.forEach { regionIds.remove(it) }
                if (regionIds.isEmpty()) {
                    CobbleSpawnRegions.activeVisualizations.remove(player.uuid)
                } else {
                    playersToUpdate.add(player.uuid)
                }
            }

            if (requests.isNotEmpty() || activeVisualStates.containsKey(player.uuid)) {
                updatePlayerVisuals(player, requests)
            }
        }


        val it = activeVisualStates.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            val uuid = entry.key
            if (uuid !in playersToUpdate) {
                val player = server.playerList.getPlayer(uuid)
                if (player != null) {
                    entry.value.clear(player)
                }
                it.remove()
                CobbleSpawnRegions.particleUpdatePlayers.remove(uuid)
                clearSpawnParticleState(uuid)
            }
        }

        playersToCheck.forEach { uuid ->
            if (uuid !in playersToUpdate && !activeVisualStates.containsKey(uuid)) {
                CobbleSpawnRegions.particleUpdatePlayers.remove(uuid)
                clearSpawnParticleState(uuid)
            }
        }
    }

    private fun clearSpawnParticleState(uuid: UUID) {
        val prefix = "$uuid:"
        spawnParticleLastSentAt.keys.removeIf { it.startsWith(prefix) }
    }



    private fun updatePlayerVisuals(player: ServerPlayer, requests: List<BoxRequest>) {
        val state = activeVisualStates.getOrPut(player.uuid) { PlayerVisualState() }

        if (requests.size != state.entities.size) {
            state.clear(player)
            state.basePos = player.position()

            requests.forEach { req ->
                val entity = Display.BlockDisplay(EntityType.BLOCK_DISPLAY, player.level() as ServerLevel)
                entity.setPos(state.basePos)
                applyTransformation(entity, req, state.basePos)
                entity.setBlockState(req.state)

                state.entities.add(entity)

                player.connection.send(
                    ClientboundAddEntityPacket(
                        entity.id, entity.uuid, entity.x, entity.y, entity.z,
                        entity.xRot, entity.yRot, entity.type, 0, entity.deltaMovement, 0.0
                    )
                )
                val entries = entity.entityData.packDirty()
                if (entries != null) {
                    player.connection.send(ClientboundSetEntityDataPacket(entity.id, entries))
                }
            }
        } else {
            if (player.position().distanceTo(state.basePos) > 16.0) {
                state.basePos = player.position()
                state.entities.forEach { entity ->
                    entity.setPos(state.basePos)
                    player.connection.send(ClientboundTeleportEntityPacket(entity))
                }
            }
            state.entities.forEachIndexed { i, entity ->
                val req = requests[i]
                applyTransformation(entity, req, state.basePos)
                entity.setBlockState(req.state)
                val entries = entity.entityData.packDirty()
                if (entries != null) {
                    player.connection.send(ClientboundSetEntityDataPacket(entity.id, entries))
                }
            }
        }
    }

    private fun applyTransformation(entity: Display.BlockDisplay, req: BoxRequest, basePos: Vec3) {
        val scaleX = (req.maxX - req.minX).toFloat().coerceAtLeast(0.001f)
        val scaleY = (req.maxY - req.minY).toFloat().coerceAtLeast(0.001f)
        val scaleZ = (req.maxZ - req.minZ).toFloat().coerceAtLeast(0.001f)

        val offsetX = (req.minX - basePos.x).toFloat()
        val offsetY = (req.minY - basePos.y).toFloat()
        val offsetZ = (req.minZ - basePos.z).toFloat()

        val transform = Transformation(
            Matrix4f().translate(offsetX, offsetY, offsetZ).scale(scaleX, scaleY, scaleZ)
        )
        entity.setTransformation(transform)
        entity.setTransformationInterpolationDuration(3)
        entity.setTransformationInterpolationDelay(0)
    }











    private fun spawnPointParticles(player: ServerPlayer, regionId: String) {
        val now = System.currentTimeMillis()
        val stateKey = "${player.uuid}:$regionId"
        val lastSentAt = spawnParticleLastSentAt[stateKey] ?: 0L
        if (now - lastSentAt < SPAWN_PARTICLE_INTERVAL_MS) return
        spawnParticleLastSentAt[stateKey] = now

        val region = RegionsConfig.getRegion(regionId) ?: return
        val px = player.x; val py = player.y; val pz = player.z

        SpawnPointStore.forEach(regionId) { pos, _, type ->
            if (!RegionsConfig.isControllingRegion(regionId, pos, region.dimension)) return@forEach

            val fx = pos.x + 0.5
            val fy = pos.y + 0.5
            val fz = pos.z + 0.5
            val dx = fx - px; val dy = fy - py; val dz = fz - pz
            if (dx * dx + dy * dy + dz * dz > SPAWN_PARTICLE_RADIUS_SQ) return@forEach

            when (type) {
                SpawnType.SOLID -> sendParticle(player, ParticleTypes.HAPPY_VILLAGER, fx, fy, fz)
                SpawnType.AIR   -> sendParticle(player, ParticleTypes.END_ROD, fx, fy, fz)
                SpawnType.WATER -> sendParticle(player, ParticleTypes.BUBBLE,  fx, fy, fz)
            }
        }
    }

    private fun sendParticle(player: ServerPlayer, particle: ParticleOptions, x: Double, y: Double, z: Double) {
        player.connection.send(
            ClientboundLevelParticlesPacket(particle, true, x, y, z, 0f, 0f, 0f, 0f, 1)
        )
    }



    private fun drawHollowBox(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        faceState: BlockState, frameState: BlockState,
        requests: MutableList<BoxRequest>,
        drawFaces: Boolean = true
    ) {
        val e = 0.05
        val f = 0.01

        requests.add(BoxRequest(minX - e, minY, minZ - e, minX + e, maxY, minZ + e, frameState))
        requests.add(BoxRequest(maxX - e, minY, minZ - e, maxX + e, maxY, minZ + e, frameState))
        requests.add(BoxRequest(minX - e, minY, maxZ - e, minX + e, maxY, maxZ + e, frameState))
        requests.add(BoxRequest(maxX - e, minY, maxZ - e, maxX + e, maxY, maxZ + e, frameState))

        requests.add(BoxRequest(minX + e, minY - e, minZ - e, maxX - e, minY + e, minZ + e, frameState))
        requests.add(BoxRequest(minX + e, minY - e, maxZ - e, maxX - e, minY + e, maxZ + e, frameState))
        requests.add(BoxRequest(minX - e, minY - e, minZ + e, minX + e, minY + e, maxZ - e, frameState))
        requests.add(BoxRequest(maxX - e, minY - e, minZ + e, maxX + e, minY + e, maxZ - e, frameState))

        requests.add(BoxRequest(minX + e, maxY - e, minZ - e, maxX - e, maxY + e, minZ + e, frameState))
        requests.add(BoxRequest(minX + e, maxY - e, maxZ - e, maxX - e, maxY + e, maxZ + e, frameState))
        requests.add(BoxRequest(minX - e, maxY - e, minZ + e, minX + e, maxY + e, maxZ - e, frameState))
        requests.add(BoxRequest(maxX - e, maxY - e, minZ + e, maxX + e, maxY + e, maxZ - e, frameState))

        if (drawFaces) {
            requests.add(BoxRequest(minX + e, minY + e, minZ - f, maxX - e, maxY - e, minZ + f, faceState))
            requests.add(BoxRequest(minX + e, minY + e, maxZ - f, maxX - e, maxY - e, maxZ + f, faceState))
            requests.add(BoxRequest(minX - f, minY + e, minZ + e, minX + f, maxY - e, maxZ - e, faceState))
            requests.add(BoxRequest(maxX - f, minY + e, minZ + e, maxX + f, maxY - e, maxZ - e, faceState))
            requests.add(BoxRequest(minX + e, minY - f, minZ + e, maxX - e, minY + f, maxZ - e, faceState))
            requests.add(BoxRequest(minX + e, maxY - f, minZ + e, maxX - e, maxY + f, maxZ - e, faceState))
        }
    }

    private fun drawWireframeEdges(
        minX: Double, minY: Double, minZ: Double,
        maxX: Double, maxY: Double, maxZ: Double,
        edgeState: BlockState,
        thickness: Double = 0.12,
        requests: MutableList<BoxRequest>
    ) {
        val t = thickness / 2

        requests.add(BoxRequest(minX - t, minY - t, minZ - t, maxX + t, minY + t, minZ + t, edgeState))
        requests.add(BoxRequest(minX - t, minY - t, maxZ - t, maxX + t, minY + t, maxZ + t, edgeState))
        requests.add(BoxRequest(minX - t, minY - t, minZ - t, minX + t, minY + t, maxZ + t, edgeState))
        requests.add(BoxRequest(maxX - t, minY - t, minZ - t, maxX + t, minY + t, maxZ + t, edgeState))

        requests.add(BoxRequest(minX - t, maxY - t, minZ - t, maxX + t, maxY + t, minZ + t, edgeState))
        requests.add(BoxRequest(minX - t, maxY - t, maxZ - t, maxX + t, maxY + t, maxZ + t, edgeState))
        requests.add(BoxRequest(minX - t, maxY - t, minZ - t, minX + t, maxY + t, maxZ + t, edgeState))
        requests.add(BoxRequest(maxX - t, maxY - t, minZ - t, maxX + t, maxY + t, maxZ + t, edgeState))

        requests.add(BoxRequest(minX - t, minY - t, minZ - t, minX + t, maxY + t, minZ + t, edgeState))
        requests.add(BoxRequest(maxX - t, minY - t, minZ - t, maxX + t, maxY + t, minZ + t, edgeState))
        requests.add(BoxRequest(minX - t, minY - t, maxZ - t, minX + t, maxY + t, maxZ + t, edgeState))
        requests.add(BoxRequest(maxX - t, minY - t, maxZ - t, maxX + t, maxY + t, maxZ + t, edgeState))
    }



    private fun buildSelectionRequests(
        player: ServerPlayer,
        sel: com.cobblespawnregions.PlayerSelection,
        requests: MutableList<BoxRequest>
    ) {
        when (sel.mode) {
            StickMode.COORDS -> {
                val p1 = sel.pos1
                val p2 = sel.pos2

                val faceBlock  = Blocks.ORANGE_STAINED_GLASS.defaultBlockState()
                val frameBlock = Blocks.ORANGE_CONCRETE.defaultBlockState()
                val edgeBlock  = Blocks.RED_CONCRETE.defaultBlockState()

                if (p1 != null && p2 != null) {
                    val bMinX = minOf(p1.x, p2.x).toDouble()
                    val bMinY = minOf(p1.y, p2.y).toDouble()
                    val bMinZ = minOf(p1.z, p2.z).toDouble()
                    val bMaxX = maxOf(p1.x, p2.x).toDouble() + 1.0
                    val bMaxY = maxOf(p1.y, p2.y).toDouble() + 1.0
                    val bMaxZ = maxOf(p1.z, p2.z).toDouble() + 1.0

                    drawHollowBox(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, faceBlock, frameBlock, requests)
                    drawWireframeEdges(bMinX, bMinY, bMinZ, bMaxX, bMaxY, bMaxZ, edgeBlock, requests = requests)
                } else {
                    if (p1 != null) drawHollowBox(p1.x + 0.25, p1.y + 0.25, p1.z + 0.25, p1.x + 0.75, p1.y + 0.75, p1.z + 0.75, faceBlock, frameBlock, requests)
                    if (p2 != null) drawHollowBox(p2.x + 0.25, p2.y + 0.25, p2.z + 0.25, p2.x + 0.75, p2.y + 0.75, p2.z + 0.75, faceBlock, frameBlock, requests)
                }
            }
            StickMode.CHUNK -> {
                val c1 = sel.chunkPos1
                val c2 = sel.chunkPos2
                val yMin = player.level().minBuildHeight.toDouble()
                val yMax = player.level().maxBuildHeight.toDouble()

                val faceBlock  = Blocks.YELLOW_STAINED_GLASS.defaultBlockState()
                val frameBlock = Blocks.YELLOW_CONCRETE.defaultBlockState()
                val edgeBlock  = Blocks.RED_CONCRETE.defaultBlockState()

                if (c1 != null && c2 != null) {
                    val bMinX = minOf(c1.minBlockX, c2.minBlockX).toDouble()
                    val bMaxX = maxOf(c1.maxBlockX,   c2.maxBlockX  ).toDouble() + 1.0
                    val bMinZ = minOf(c1.minBlockZ, c2.minBlockZ).toDouble()
                    val bMaxZ = maxOf(c1.maxBlockZ,   c2.maxBlockZ  ).toDouble() + 1.0

                    drawHollowBox(bMinX, yMin, bMinZ, bMaxX, yMax, bMaxZ, faceBlock, frameBlock, requests)
                    drawWireframeEdges(bMinX, yMin, bMinZ, bMaxX, yMax, bMaxZ, edgeBlock, requests = requests)
                } else {
                    if (c1 != null) drawHollowBox(c1.minBlockX.toDouble(), yMin, c1.minBlockZ.toDouble(), c1.minBlockX + 16.0, yMax, c1.minBlockZ + 16.0, faceBlock, frameBlock, requests)
                    if (c2 != null) drawHollowBox(c2.minBlockX.toDouble(), yMin, c2.minBlockZ.toDouble(), c2.minBlockX + 16.0, yMax, c2.minBlockZ + 16.0, faceBlock, frameBlock, requests)
                }
            }
        }
    }

    private fun buildRegionRequests(
        region: RegionData,
        priorityRank: Int,
        priorityRegionCount: Int,
        requests: MutableList<BoxRequest>
    ) {
        val rMinX = minOf(region.pos1.x, region.pos2.x).toDouble()
        val rMinY = minOf(region.pos1.y, region.pos2.y).toDouble()
        val rMinZ = minOf(region.pos1.z, region.pos2.z).toDouble()
        val rMaxX = maxOf(region.pos1.x, region.pos2.x).toDouble() + 1.0
        val rMaxY = maxOf(region.pos1.y, region.pos2.y).toDouble() + 1.0
        val rMaxZ = maxOf(region.pos1.z, region.pos2.z).toDouble() + 1.0
        val palette = priorityPalette(priorityRank, priorityRegionCount)

        drawHollowBox(rMinX, rMinY, rMinZ, rMaxX, rMaxY, rMaxZ, palette.face, palette.frame, requests)
        drawWireframeEdges(rMinX, rMinY, rMinZ, rMaxX, rMaxY, rMaxZ, palette.edge, requests = requests)

    }

    private fun priorityPalette(priorityRank: Int, priorityRegionCount: Int): RegionVisualPalette {
        if (priorityRegionCount <= 1) return priorityPalettes.first()
        val clampedRank = priorityRank.coerceIn(0, priorityRegionCount - 1)
        val paletteIndex = clampedRank * (priorityPalettes.size - 1) / (priorityRegionCount - 1)
        return priorityPalettes[paletteIndex.coerceIn(priorityPalettes.indices)]
    }
}
