package com.cobblespawnregions.utils

import net.minecraft.world.level.material.Fluids
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.resources.ResourceKey
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentLinkedQueue

object SpawnPointScanner {

    private const val SPARSE_STEP    = 4
    private const val MAX_FLOOR_DEPTH = 25



    private data class ScanJob(
        val regionId: String,
        val region: RegionData,
        val chunkPos: ChunkPos,
        val world: ServerLevel
    )

    private val scanQueue = ConcurrentLinkedQueue<ScanJob>()






    fun enqueueScan(regionId: String, region: RegionData, chunkPos: ChunkPos, world: ServerLevel) {
        scanQueue.add(ScanJob(regionId, region, chunkPos, world))
    }





    fun processPendingScans(count: Int = 2) {
        repeat(count) {
            val job = scanQueue.poll() ?: return

            if (SpawnPointStore.isChunkScanned(job.regionId, job.chunkPos.x, job.chunkPos.z)) return@repeat
            if (!job.world.hasChunk(job.chunkPos.x, job.chunkPos.z)) return@repeat
            scanChunkForRegion(job.regionId, job.region, job.chunkPos, job.world)
        }
    }

    fun clearQueue() = scanQueue.clear()







    fun enqueueLoadedChunks(regionId: String, region: RegionData, server: MinecraftServer) {
        val worldKey = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.tryParse(region.dimension) ?: return
        )
        val world = server.getLevel(worldKey) ?: return

        val minCX = minOf(region.pos1.x, region.pos2.x) shr 4
        val maxCX = maxOf(region.pos1.x, region.pos2.x) shr 4
        val minCZ = minOf(region.pos1.z, region.pos2.z) shr 4
        val maxCZ = maxOf(region.pos1.z, region.pos2.z) shr 4

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                if (world.hasChunk(cx, cz)) {
                    enqueueScan(regionId, region, ChunkPos(cx, cz), world)
                }
            }
        }
    }


    fun enqueueAllLoadedChunks(server: MinecraftServer) {
        RegionsConfig.allRegions().forEach { region ->
            enqueueLoadedChunks(region.regionId, region, server)
        }
    }

    fun scanLoadedChunks(regionId: String, region: RegionData, server: MinecraftServer): Int {
        val worldKey = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.tryParse(region.dimension) ?: return 0
        )
        val world = server.getLevel(worldKey) ?: return 0

        val minCX = minOf(region.pos1.x, region.pos2.x) shr 4
        val maxCX = maxOf(region.pos1.x, region.pos2.x) shr 4
        val minCZ = minOf(region.pos1.z, region.pos2.z) shr 4
        val maxCZ = maxOf(region.pos1.z, region.pos2.z) shr 4
        var scanned = 0

        for (cx in minCX..maxCX) {
            for (cz in minCZ..maxCZ) {
                if (!world.hasChunk(cx, cz)) continue
                if (SpawnPointStore.isChunkScanned(regionId, cx, cz)) continue
                scanChunkForRegion(regionId, region, ChunkPos(cx, cz), world)
                scanned++
            }
        }

        return scanned
    }

    fun scanAllLoadedChunks(server: MinecraftServer): Int {
        var scanned = 0
        RegionsConfig.allRegions().forEach { region ->
            scanned += scanLoadedChunks(region.regionId, region, server)
        }
        return scanned
    }








    fun scanChunkForRegion(regionId: String, region: RegionData, chunkPos: ChunkPos, world: ServerLevel) {
        if (SpawnPointStore.isChunkScanned(regionId, chunkPos.x, chunkPos.z)) return

        val regionMinX = minOf(region.pos1.x, region.pos2.x)
        val regionMaxX = maxOf(region.pos1.x, region.pos2.x)
        val regionMinZ = minOf(region.pos1.z, region.pos2.z)
        val regionMaxZ = maxOf(region.pos1.z, region.pos2.z)

        val minX = maxOf(regionMinX, chunkPos.minBlockX)
        val maxX = minOf(regionMaxX, chunkPos.maxBlockX)
        val minZ = maxOf(regionMinZ, chunkPos.minBlockZ)
        val maxZ = minOf(regionMaxZ, chunkPos.maxBlockZ)

        if (minX > maxX || minZ > maxZ) {
            SpawnPointStore.addChunkFloors(regionId, chunkPos.x, chunkPos.z, emptyList())
            return
        }

        val useHeightmap = region.pos1.y == 0 && region.pos2.y == 0
        val fixedMinY    = if (!useHeightmap) minOf(region.pos1.y, region.pos2.y) else 0
        val fixedMaxY    = if (!useHeightmap) maxOf(region.pos1.y, region.pos2.y) else 0

        val floors = mutableListOf<SpawnFloor>()




        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val feetY = if (useHeightmap) {


                    world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
                } else {
                    topSolidInRange(world, x, z, fixedMinY, fixedMaxY)
                } ?: continue

                if (!useHeightmap && (feetY < fixedMinY || feetY > fixedMaxY)) continue

                val floorPos = BlockPos(x, feetY - 1, z)
                val feetPos  = BlockPos(x, feetY,     z)
                val airPos   = feetPos.above()

                val floorState = world.getBlockState(floorPos)
                val feetState  = world.getBlockState(feetPos)
                val airState   = world.getBlockState(airPos)

                if (!floorState.getCollisionShape(world, floorPos).isEmpty && feetState.isAir) {
                    floors.add(SpawnFloor(feetPos, floorState.block, SpawnType.SOLID))
                    if ((useHeightmap || airPos.y <= fixedMaxY) && airState.isAir) {
                        floors.add(SpawnFloor(airPos, net.minecraft.world.level.block.Blocks.AIR, SpawnType.AIR))
                    }
                }
            }
        }



        for (x in minX..maxX step SPARSE_STEP) {
            for (z in minZ..maxZ step SPARSE_STEP) {
                val (scanMin, scanMax) = yBounds(world, useHeightmap, fixedMinY, fixedMaxY, x, z)
                for (y in scanMin..scanMax) {
                    val pos = BlockPos(x, y, z)
                    if (!world.getBlockState(pos).isAir) continue
                    val floorBlock = solidWithin(world, x, y, z, scanMin) ?: continue
                    floors.add(SpawnFloor(pos, floorBlock, SpawnType.AIR))
                }
            }
        }



        for (x in minX..maxX step SPARSE_STEP) {
            for (z in minZ..maxZ step SPARSE_STEP) {
                val (scanMin, scanMax) = yBounds(world, useHeightmap, fixedMinY, fixedMaxY, x, z)
                for (y in scanMin..scanMax) {
                    val pos   = BlockPos(x, y, z)
                    val fluid = world.getBlockState(pos).fluidState.type
                    if (fluid != Fluids.WATER && fluid != Fluids.FLOWING_WATER) continue
                    val floorBlock = solidWithin(world, x, y, z, scanMin) ?: continue
                    floors.add(SpawnFloor(pos, floorBlock, SpawnType.WATER))
                }
            }
        }

        SpawnPointStore.addChunkFloors(regionId, chunkPos.x, chunkPos.z, floors)
    }




    private fun yBounds(
        world: ServerLevel,
        useHeightmap: Boolean,
        fixedMin: Int, fixedMax: Int,
        x: Int, z: Int
    ): Pair<Int, Int> = if (useHeightmap) {
        Pair(world.minBuildHeight, world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z))
    } else {
        Pair(fixedMin, fixedMax)
    }





    private fun topSolidInRange(world: ServerLevel, x: Int, z: Int, minY: Int, maxY: Int): Int? {
        for (y in maxY downTo minY + 1) {
            val floorState = world.getBlockState(BlockPos(x, y - 1, z))
            val feetState  = world.getBlockState(BlockPos(x, y, z))
            if (!floorState.getCollisionShape(world, BlockPos(x, y - 1, z)).isEmpty && feetState.isAir) return y
        }
        return null
    }


    private fun solidWithin(
        world: ServerLevel,
        x: Int, startY: Int, z: Int,
        absoluteMin: Int
    ): net.minecraft.world.level.block.Block? {
        for (dy in 1..MAX_FLOOR_DEPTH) {
            val checkY = startY - dy
            if (checkY < absoluteMin) break
            val pos   = BlockPos(x, checkY, z)
            val state = world.getBlockState(pos)
            if (!state.getCollisionShape(world, pos).isEmpty) return state.block
        }
        return null
    }
}
