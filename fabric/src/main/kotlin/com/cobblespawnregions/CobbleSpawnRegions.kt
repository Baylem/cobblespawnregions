package com.cobblespawnregions

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblespawnregions.utils.RegionCommands
import com.cobblespawnregions.utils.RegionBattleTracker
import com.cobblespawnregions.utils.RegionCatchingTracker
import com.cobblespawnregions.utils.RegionData
import com.cobblespawnregions.utils.RegionEntityTracker
import com.cobblespawnregions.utils.RegionParticleUtils
import com.cobblespawnregions.utils.RegionSpawnHelper
import com.cobblespawnregions.utils.RegionWanderingGoalManager
import com.cobblespawnregions.utils.RegionsConfig
import com.cobblespawnregions.utils.SpawnPointScanner
import com.cobblespawnregions.utils.SpawnPointStore
import com.everlastingutils.command.CommandManager
import com.everlastingutils.scheduling.SchedulerManager
import com.everlastingutils.utils.logDebug
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.Entity
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.resources.ResourceLocation
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class StickMode { COORDS, CHUNK }

data class PlayerSelection(
    val mode: StickMode = StickMode.COORDS,
    var pos1: BlockPos? = null,
    var pos2: BlockPos? = null,
    var chunkPos1: ChunkPos? = null,
    var chunkPos2: ChunkPos? = null
) {
    val hasFirst   get() = if (mode == StickMode.CHUNK) chunkPos1 != null else pos1 != null
    val hasSecond  get() = if (mode == StickMode.CHUNK) chunkPos2 != null else pos2 != null
    val isBothSet  get() = hasFirst && hasSecond
}

object CobbleSpawnRegions : ModInitializer {

    private val logger = LoggerFactory.getLogger("cobblespawnregions")
    const val MOD_ID = "cobblespawnregions"
    private val battleTracker = RegionBattleTracker()
    private val catchingTracker = RegionCatchingTracker()

    val playerSelections = ConcurrentHashMap<UUID, PlayerSelection>()
    val activeVisualizations = ConcurrentHashMap<UUID, MutableSet<String>>()
    val particleUpdatePlayers = ConcurrentHashMap.newKeySet<UUID>()

    @Volatile private var serverReady = false
    @Volatile private var automaticSpawningReady = false
    @Volatile private var nextSpawnLoopCheckAtMs = 0L
    private val dimensionKeyCache = ConcurrentHashMap<String, ResourceKey<Level>>()

    override fun onInitialize() {
        RegionsConfig.initializeAndLoad()
        RegionsConfig.debugLog(logger, "Initializing CobbleSpawnRegions")
        RegionEntityTracker.loadFromDisk()
        RegionEntityTracker.registerEvents()
        RegionCommands.register()
        battleTracker.registerEvents()
        catchingTracker.registerEvents()

        ServerLifecycleEvents.SERVER_STARTING.register { server ->
            SchedulerManager.onServerStarting(server)
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            serverReady = true
            automaticSpawningReady = false
            SpawnPointScanner.enqueueAllLoadedChunks(server)



            for (region in RegionsConfig.allRegions()) {
                val rWorld = server.getLevel(parseDimension(region.dimension)) ?: continue
                val box    = RegionSpawnHelper.regionBoundingBox(region)
                RegionEntityTracker.rebuildFromWorld(rWorld, region.regionId, box)
                RegionsConfig.debugLog(
                    logger,
                    "[CSR] Tracker rebuilt for '${region.regionId}': " +
                            "${RegionEntityTracker.countTotal(region.regionId)} entity/ies tracked."
                )
            }

            SchedulerManager.schedule(
                "cobblespawnregions-startup-reconcile",
                server, 5L, TimeUnit.SECONDS
            ) {
                try {
                    for (region in RegionsConfig.allRegions()) {
                        val rWorld = server.getLevel(parseDimension(region.dimension)) ?: continue
                        RegionEntityTracker.rebuildFromWorld(rWorld, region.regionId, RegionSpawnHelper.regionBoundingBox(region))
                        reconcileLoadedRegionChunks(rWorld, region)
                    }
                } finally {
                    automaticSpawningReady = true
                    nextSpawnLoopCheckAtMs = 0L
                }
            }

            SchedulerManager.scheduleAtFixedRate(
                "cobblespawnregions-particle-loop",
                server, 0L, 500L, TimeUnit.MILLISECONDS
            ) {
                RegionParticleUtils.updateParticles(server)
            }

            SchedulerManager.scheduleAtFixedRate(
                "cobblespawnregions-scan-loop",
                server, 0L, 1L, TimeUnit.SECONDS
            ) {
                SpawnPointScanner.processPendingScans(count = 2)
            }

            SchedulerManager.scheduleAtFixedRate(
                "cobblespawnregions-spawn-loop",
                server, 0L, 1L, TimeUnit.SECONDS
            ) {
                processAllRegionSpawns(server)
            }

            SchedulerManager.scheduleAtFixedRate(
                "cobblespawnregions-tracker-save-loop",
                server, 5L, 5L, TimeUnit.SECONDS
            ) {
                RegionEntityTracker.flushIfDirty()
            }

            battleTracker.startCleanupScheduler(server)
        }

        ServerLifecycleEvents.SERVER_STOPPING.register { server ->
            serverReady = false
            automaticSpawningReady = false
            SpawnPointScanner.clearQueue()
            SchedulerManager.shutdown("cobblespawnregions-startup-reconcile")
            SchedulerManager.shutdown("cobblespawnregions-particle-loop")
            SchedulerManager.shutdown("cobblespawnregions-scan-loop")
            SchedulerManager.shutdown("cobblespawnregions-spawn-loop")
            SchedulerManager.shutdown("cobblespawnregions-tracker-save-loop")
            SchedulerManager.shutdown("cobblespawnregions-battle-cleanup")
            if (RegionsConfig.config.killTrackedPokemonOnServerStop) {
                val removed = removeLoadedTrackedPokemon(server)
                RegionEntityTracker.clearAllAndMarkDirty()
                RegionsConfig.debugLog(logger, "[CSR] Removed $removed loaded tracked Pokemon on server stop.")
            }
            RegionEntityTracker.flushIfDirty()
            playerSelections.clear()
            activeVisualizations.clear()
            particleUpdatePlayers.clear()
            SpawnPointStore.clearAll()
            RegionEntityTracker.clearAll()
            RegionWanderingGoalManager.clearAll()
            SchedulerManager.onServerStopping(server)
        }

        ServerChunkEvents.CHUNK_LOAD.register { world, chunk ->
            if (!serverReady) return@register
            if (world !is ServerLevel) return@register

            val dim      = world.dimension().location().toString()
            val chunkPos = chunk.pos
            RegionEntityTracker.markChunkLoaded(world, chunkPos)

            RegionsConfig.allRegions().forEach { region ->
                if (region.dimension != dim) return@forEach

                val rMinCX = minOf(region.pos1.x, region.pos2.x) shr 4
                val rMaxCX = maxOf(region.pos1.x, region.pos2.x) shr 4
                val rMinCZ = minOf(region.pos1.z, region.pos2.z) shr 4
                val rMaxCZ = maxOf(region.pos1.z, region.pos2.z) shr 4

                if (chunkPos.x < rMinCX || chunkPos.x > rMaxCX) return@forEach
                if (chunkPos.z < rMinCZ || chunkPos.z > rMaxCZ) return@forEach

                SpawnPointScanner.enqueueScan(region.regionId, region, chunkPos, world)


                val box = RegionSpawnHelper.regionBoundingBox(region)
                RegionEntityTracker.rebuildFromWorld(world, region.regionId, box)
                world.server.execute {
                    RegionEntityTracker.reconcileLoadedChunk(world, region.regionId, chunkPos)
                }
            }
        }

        ServerChunkEvents.CHUNK_UNLOAD.register { world, chunk ->
            if (!serverReady) return@register
            if (world !is ServerLevel) return@register
            RegionEntityTracker.markChunkUnloading(world, chunk.pos)
        }




        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity is PokemonEntity && RegionEntityTracker.isManaged(entity)) {
                RegionEntityTracker.trackLoadedEntity(entity)
            }
        }

        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
            if (entity !is PokemonEntity) return@register
            RegionWanderingGoalManager.forget(entity.uuid)
            val chunkIsUnloading = RegionEntityTracker.isChunkUnloading(entity)
            val reason = entity.removalReason
            if (chunkIsUnloading) {
                RegionEntityTracker.forgetLiveUuid(entity.uuid)


                return@register
            }
            if (reason == null) return@register
            when (reason) {
                Entity.RemovalReason.UNLOADED_TO_CHUNK,
                Entity.RemovalReason.UNLOADED_WITH_PLAYER -> {
                    RegionEntityTracker.forgetLiveUuid(entity.uuid)

                }
                else -> {

                    val wasTracked = RegionEntityTracker.isManaged(entity)
                    RegionEntityTracker.untrack(entity.uuid)
                    if (wasTracked) {
                        logDebug(
                            "Untracked ${entity.uuid} (reason=${reason.name}, " +
                                    "species=${entity.pokemon.species.name})", MOD_ID
                        )
                    }
                }
            }
        }

        registerInteractionEvents()
    }



    private fun processAllRegionSpawns(server: MinecraftServer) {
        if (!automaticSpawningReady) return
        val now = System.currentTimeMillis()
        if (now < nextSpawnLoopCheckAtMs) return

        var nextDueAt = Long.MAX_VALUE
        var hasActiveRegion = false

        for (region in RegionsConfig.allRegions()) {
            if (region.selectedPokemon.isEmpty()) continue
            hasActiveRegion = true

            val dueAt = RegionSpawnHelper.nextSpawnDueAt(region)
            if (now < dueAt) {
                if (dueAt < nextDueAt) nextDueAt = dueAt
                continue
            }

            val world = server.getLevel(parseDimension(region.dimension)) ?: continue
            try {
                if (region.requirePlayerInRange && !hasPlayerNearRegion(world, region)) {
                    val nextRegionDueAt = RegionSpawnHelper.nextSpawnDueAt(region)
                    if (nextRegionDueAt < nextDueAt) nextDueAt = nextRegionDueAt
                    continue
                }

                RegionSpawnHelper.attemptSpawnInRegion(
                    world,
                    region,
                    amount = region.spawnAmountPerSpawn.coerceAtLeast(1),
                    respectTimer = false
                )
                val nextRegionDueAt = RegionSpawnHelper.nextSpawnDueAt(region)
                if (nextRegionDueAt < nextDueAt) nextDueAt = nextRegionDueAt
            } catch (e: Exception) {
                RegionsConfig.debugError(logger, "Error spawning for region '${region.regionId}'", e)
            }
        }

        nextSpawnLoopCheckAtMs = when {
            nextDueAt != Long.MAX_VALUE -> nextDueAt
            hasActiveRegion -> now + 1_000L
            else -> now + 5_000L
        }
    }

    private fun hasPlayerNearRegion(world: ServerLevel, region: RegionData): Boolean {
        val range = region.playerActivationRange.coerceAtLeast(0.0)
        val minX = minOf(region.pos1.x, region.pos2.x).toDouble() - range
        val minY = minOf(region.pos1.y, region.pos2.y).toDouble() - range
        val minZ = minOf(region.pos1.z, region.pos2.z).toDouble() - range
        val maxX = maxOf(region.pos1.x, region.pos2.x).toDouble() + 1.0 + range
        val maxY = maxOf(region.pos1.y, region.pos2.y).toDouble() + 1.0 + range
        val maxZ = maxOf(region.pos1.z, region.pos2.z).toDouble() + 1.0 + range

        return world.players().any { player ->
            player.x in minX..maxX && player.y in minY..maxY && player.z in minZ..maxZ
        }
    }

    private fun reconcileLoadedRegionChunks(world: ServerLevel, region: RegionData) {
        val minCX = minOf(region.pos1.x, region.pos2.x) shr 4
        val maxCX = maxOf(region.pos1.x, region.pos2.x) shr 4
        val minCZ = minOf(region.pos1.z, region.pos2.z) shr 4
        val maxCZ = maxOf(region.pos1.z, region.pos2.z) shr 4

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                if (world.hasChunk(cx, cz)) {
                    RegionEntityTracker.reconcileLoadedChunk(world, region.regionId, ChunkPos(cx, cz))
                }
            }
        }
    }

    private fun removeLoadedTrackedPokemon(server: MinecraftServer): Int {
        var removed = 0
        for (region in RegionsConfig.allRegions()) {
            val world = server.getLevel(parseDimension(region.dimension)) ?: continue
            val box = RegionSpawnHelper.regionBoundingBox(region)
            val entities = RegionEntityTracker.loadedManagedEntities(world, region.regionId, box)

            for (entity in entities) {
                if (!entity.isRemoved) {
                    entity.discard()
                    removed++
                }
            }
        }
        return removed
    }

    private fun parseDimension(str: String): ResourceKey<Level> {
        dimensionKeyCache[str]?.let { return it }
        val parts = str.split(":")
        val key = if (parts.size != 2) {
            RegionsConfig.debugWarn(logger, "Invalid dimension '$str', using 'minecraft:overworld'")
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"))
        } else {
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]))
        }
        dimensionKeyCache[str] = key
        return key
    }



    private fun registerInteractionEvents() {

        AttackBlockCallback.EVENT.register { player, world, hand, pos, _ ->
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            val mode = getStickMode(player) ?: return@register InteractionResult.PASS
            if (!hasClaimStickPermission(player)) return@register InteractionResult.SUCCESS

            val sel = freshOrExisting(player.uuid, mode)

            when (mode) {
                StickMode.CHUNK -> {
                    val cp = ChunkPos(pos)
                    sel.chunkPos1 = cp
                    player.displayClientMessage(Component.literal(
                        "§a[Regions] §fChunk §e1 §fset to §b[${cp.x}, ${cp.z}] §7(${cp.minBlockX},${cp.minBlockZ} → ${cp.maxBlockX},${cp.maxBlockZ})"
                    ), false)
                }
                else -> {
                    sel.pos1 = pos
                    player.displayClientMessage(Component.literal(
                        "§a[Regions] §fPos §e1 §fset to §b(${pos.x}, ${pos.y}, ${pos.z})"
                    ), false)
                }
            }

            logDebug("${player.name.string} [${mode.name}] first point = $pos", MOD_ID)
            requestParticleUpdate(player.uuid)
            sendStatus(player, sel)
            InteractionResult.SUCCESS
        }

        UseBlockCallback.EVENT.register { player, world, hand, hitResult ->
            if (world.isClientSide || hand != InteractionHand.MAIN_HAND) return@register InteractionResult.PASS
            if (player !is ServerPlayer) return@register InteractionResult.PASS
            val mode = getStickMode(player) ?: return@register InteractionResult.PASS
            if (!hasClaimStickPermission(player)) return@register InteractionResult.SUCCESS

            val pos = hitResult.blockPos
            val sel = freshOrExisting(player.uuid, mode)

            when (mode) {
                StickMode.CHUNK -> {
                    val cp = ChunkPos(pos)
                    sel.chunkPos2 = cp
                    player.displayClientMessage(Component.literal(
                        "§a[Regions] §fChunk §e2 §fset to §b[${cp.x}, ${cp.z}] §7(${cp.minBlockX},${cp.minBlockZ} → ${cp.maxBlockX},${cp.maxBlockZ})"
                    ), false)
                }
                else -> {
                    sel.pos2 = pos
                    player.displayClientMessage(Component.literal(
                        "§a[Regions] §fPos §e2 §fset to §b(${pos.x}, ${pos.y}, ${pos.z})"
                    ), false)
                }
            }

            logDebug("${player.name.string} [${mode.name}] second point = $pos", MOD_ID)
            requestParticleUpdate(player.uuid)
            sendStatus(player, sel)
            InteractionResult.SUCCESS
        }
    }

    private fun hasClaimStickPermission(player: ServerPlayer): Boolean {
        val permission = RegionsConfig.commandPermission("claimstick.use")
        val allowed = CommandManager.hasPermissionOrOp(player.createCommandSourceStack(), permission, 2, 2)
        if (!allowed) {
            player.displayClientMessage(Component.literal("§c[CSR] §fYou do not have permission to use the claim stick."), false)
        }
        return allowed
    }

    fun requestParticleUpdate(uuid: UUID) {
        particleUpdatePlayers.add(uuid)
    }

    fun refreshRuntimeAfterConfigReload(server: MinecraftServer) {
        automaticSpawningReady = false
        nextSpawnLoopCheckAtMs = 0L
        RegionsConfig.lastSpawnTicks.clear()
        SpawnPointScanner.clearQueue()

        for (region in RegionsConfig.allRegions()) {
            val world = server.getLevel(parseDimension(region.dimension)) ?: continue
            RegionEntityTracker.rebuildFromWorld(world, region.regionId, RegionSpawnHelper.regionBoundingBox(region))
        }

        automaticSpawningReady = true
        nextSpawnLoopCheckAtMs = 0L
    }

    fun requestParticleUpdate(player: ServerPlayer, reason: String, logRequest: Boolean = false) {
        particleUpdatePlayers.add(player.uuid)
        if (logRequest) {
            RegionsConfig.debugLog(logger, "[CSR-VISUAL] ${player.name.string} (${player.uuid}) requested visual update: $reason")
        }
    }

    private fun freshOrExisting(uuid: UUID, mode: StickMode): PlayerSelection {
        val existing = playerSelections[uuid]
        return if (existing != null && existing.mode == mode) existing
        else PlayerSelection(mode).also { playerSelections[uuid] = it }
    }

    fun getStickMode(player: ServerPlayer): StickMode? {
        val nbt = player.getItemInHand(InteractionHand.MAIN_HAND)
            .get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return null
        if (!nbt.getBoolean("cobblespawnregions:is_region_stick")) return null
        return when (nbt.getString("cobblespawnregions:mode")) {
            "CHUNK" -> StickMode.CHUNK
            else    -> StickMode.COORDS
        }
    }

    private fun sendStatus(player: ServerPlayer, sel: PlayerSelection) {
        when {
            sel.isBothSet  -> player.displayClientMessage(Component.literal(
                "§a[Regions] §fBoth points set — run §e/csr region create <n> §fto save."
            ), false)
            sel.hasFirst   -> player.displayClientMessage(Component.literal(
                "§a[Regions] §fNow §eright-click §fa block to set the second point."
            ), false)
            sel.hasSecond  -> player.displayClientMessage(Component.literal(
                "§a[Regions] §fNow §eleft-click §fa block to set the first point."
            ), false)
        }
    }
}
