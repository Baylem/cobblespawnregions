package com.cobblespawnregions.fabric

import com.cobblespawnregions.CobbleSpawnRegions
import net.fabricmc.api.ModInitializer

object CobbleSpawnRegionsFabric : ModInitializer {
    override fun onInitialize() {
        CobbleSpawnRegions.init()
    }
}
