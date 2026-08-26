package com.cobblespawnregions.utils

import net.minecraft.world.level.block.Block
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ChunkPos
import java.util.concurrent.ConcurrentHashMap

enum class SpawnType { SOLID, AIR, WATER }





data class SpawnFloor(val pos: BlockPos, val floorBlock: Block, val type: SpawnType)

object SpawnPointStore {











    private class RegionFloors {
        var positions: LongArray  = LongArray(0)
        var blockIds:  IntArray   = IntArray(0)
        var types:     ByteArray  = ByteArray(0)
        val size get() = positions.size

        fun append(floors: List<SpawnFloor>) {
            if (floors.isEmpty()) return
            val n = positions.size
            val m = floors.size
            positions = positions.copyOf(n + m)
            blockIds  = blockIds.copyOf(n + m)
            types     = types.copyOf(n + m)
            floors.forEachIndexed { i, floor ->
                positions[n + i] = floor.pos.asLong()
                blockIds [n + i] = BuiltInRegistries.BLOCK.getId(floor.floorBlock)
                types    [n + i] = floor.type.ordinal.toByte()
            }
        }

        fun clear() {
            positions = LongArray(0)
            blockIds  = IntArray(0)
            types     = ByteArray(0)
        }
    }

    private val regionFloors  = ConcurrentHashMap<String, RegionFloors>()
    private val scannedChunks = ConcurrentHashMap<String, MutableSet<Long>>()



    fun isChunkScanned(regionId: String, chunkX: Int, chunkZ: Int): Boolean =
        scannedChunks[regionId]?.contains(ChunkPos.asLong(chunkX, chunkZ)) == true

    fun isEmpty(regionId: String): Boolean =
        (regionFloors[regionId]?.size ?: 0) == 0

    fun size(regionId: String): Int =
        regionFloors[regionId]?.size ?: 0

    fun rawAt(regionId: String, index: Int, action: (posLong: Long, blockId: Int, type: SpawnType) -> Unit): Boolean {
        val data = regionFloors[regionId] ?: return false
        if (index !in 0 until data.size) return false
        action(
            data.positions[index],
            data.blockIds[index],
            SpawnType.entries[data.types[index].toInt()]
        )
        return true
    }





    fun forEach(regionId: String, action: (pos: BlockPos, block: Block, type: SpawnType) -> Unit) {
        val data = regionFloors[regionId] ?: return
        for (i in 0 until data.size) {
            action(
                BlockPos.of(data.positions[i]),
                BuiltInRegistries.BLOCK.byId(data.blockIds[i]),
                SpawnType.entries[data.types[i].toInt()]
            )
        }
    }

    fun forEachRaw(regionId: String, action: (posLong: Long, blockId: Int, type: SpawnType) -> Unit) {
        val data = regionFloors[regionId] ?: return
        for (i in 0 until data.size) {
            action(
                data.positions[i],
                data.blockIds[i],
                SpawnType.entries[data.types[i].toInt()]
            )
        }
    }



    fun addChunkFloors(regionId: String, chunkX: Int, chunkZ: Int, floors: List<SpawnFloor>) {
        regionFloors.getOrPut(regionId) { RegionFloors() }.append(floors)
        scannedChunks.getOrPut(regionId) { mutableSetOf() }.add(ChunkPos.asLong(chunkX, chunkZ))
    }



    fun clearRegion(regionId: String) {
        regionFloors.remove(regionId)
        scannedChunks.remove(regionId)
    }

    fun clearAll() {
        regionFloors.clear()
        scannedChunks.clear()
    }
}
