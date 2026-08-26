package com.cobblespawnregions.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import org.slf4j.LoggerFactory
import java.io.File

object Migrations {

    private val logger = LoggerFactory.getLogger("CobbleSpawnRegionsMigrations")
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val regionsDir = File("config/cobblespawnregions/regions")

    data class Result(
        val migrated: Int,
        val skipped: Int,
        val failed: Int,
        val message: String
    )

    fun migrateCobbleSpawnersToRegions(): Result {
        val completionMarker = File(regionsDir.parentFile, ".cobblespawners_to_regions_migrated")
        if (completionMarker.exists()) {
            return Result(0, 0, 0, "CobbleSpawners migration has already been completed.")
        }

        val spawnersDir = File("config/cobblespawners/spawners")
        val files = spawnersDir.listFiles { _, name ->
            name.endsWith(".json", ignoreCase = true) || name.endsWith(".jsonc", ignoreCase = true)
        } ?: return Result(0, 0, 0, "No CobbleSpawners files were found in config/cobblespawners/spawners.")
        if (files.isEmpty()) {
            return Result(0, 0, 0, "No CobbleSpawners files were found in config/cobblespawners/spawners.")
        }

        var migrated = 0
        var skipped = 0
        var failed = 0

        files.sortedBy { it.name }.forEach { source ->
            try {
                val spawner = readSpawner(source)
                val region = convertSpawner(spawner)
                val target = File(regionsDir, "region_${region.regionId}.jsonc")

                if (target.exists()) {
                    skipped++
                    return@forEach
                }

                regionsDir.mkdirs()
                target.writeText(gson.toJson(region))
                migrated++
            } catch (e: Exception) {
                failed++
                logger.error("Failed to migrate CobbleSpawners file '${source.path}'.", e)
            }
        }

        if (migrated > 0 || failed > 0) {
            logger.info(
                "CobbleSpawners import finished: {} migrated, {} already present, {} failed. Legacy files were not modified.",
                migrated,
                skipped,
                failed
            )
        }

        if (failed == 0) {
            completionMarker.writeText("CobbleSpawners files imported without modifying the legacy files.\n")
        }

        val message = if (failed == 0) {
            "CobbleSpawners migration finished: $migrated migrated, $skipped already present. Legacy files were not modified."
        } else {
            "CobbleSpawners migration finished with errors: $migrated migrated, $skipped already present, $failed failed. Check the server log and run the command again after fixing the errors."
        }
        return Result(migrated, skipped, failed, message)
    }

    private fun readSpawner(file: File): LegacySpawnerData {
        val reader = JsonReader(file.reader()).apply { isLenient = true }
        return reader.use {
            gson.fromJson(JsonParser.parseReader(it), LegacySpawnerData::class.java)
                ?: throw IllegalArgumentException("Spawner file was empty.")
        }
    }

    private fun convertSpawner(spawner: LegacySpawnerData): RegionData {
        val radius = spawner.spawnRadius ?: LegacySpawnRadius()
        val pos = spawner.spawnerPos
        val wandering = spawner.wanderingSettings ?: LegacyWanderingSettings()
        val id = "migrated_spawner_${coordinatePart(pos.x)}_${coordinatePart(pos.y)}_${coordinatePart(pos.z)}"

        return RegionData(
            regionId = id,
            regionName = spawner.spawnerName.ifBlank { id },
            pos1 = SerializableBlockPos(pos.x - radius.width, pos.y - radius.height, pos.z - radius.width),
            pos2 = SerializableBlockPos(pos.x + radius.width, pos.y + radius.height, pos.z + radius.width),
            dimension = spawner.dimension,
            mode = "COORDS",
            spawnTimerTicks = spawner.spawnTimerTicks.coerceAtLeast(1),
            spawnAmountPerSpawn = spawner.spawnAmountPerSpawn.coerceAtLeast(1),
            requirePlayerInRange = spawner.requirePlayerInRange,
            playerActivationRange = spawner.playerActivationRange.toDouble(),
            selectedPokemon = spawner.selectedPokemon.map { entry ->
                convertPokemon(entry, wandering, spawner.spawnLimit)
            }.toMutableList(),
            maxTotalSpawns = spawner.spawnLimit.coerceAtLeast(1)
        )
    }

    private fun convertPokemon(
        entry: LegacyPokemonSpawnEntry,
        wandering: LegacyWanderingSettings,
        spawnLimit: Int
    ) = PokemonSpawnEntry(
        pokemonName = entry.pokemonName,
        formName = entry.formName,
        aspects = entry.aspects,
        spawnChance = entry.spawnChance,
        spawnChanceType = entry.spawnChanceType,
        minLevel = entry.minLevel,
        maxLevel = entry.maxLevel,
        sizeSettings = entry.sizeSettings,
        captureSettings = entry.captureSettings,
        ivSettings = entry.ivSettings,
        evSettings = entry.evSettings,
        spawnSettings = SpawnSettings(
            spawnTime = entry.spawnSettings.spawnTime,
            spawnWeather = entry.spawnSettings.spawnWeather,
            allowedBlocks = allowedBlocksFor(entry.spawnSettings.spawnLocation)
        ),
        wanderingSettings = RegionWanderingSettings(
            enabled = wandering.enabled,
            returnTarget = "CENTER"
        ),
        heldItemsOnSpawn = entry.heldItemsOnSpawn,
        moves = entry.moves,
        maxSpawnCount = spawnLimit.coerceAtLeast(1)
    )

    private fun allowedBlocksFor(spawnLocation: String): List<String> =
        when (spawnLocation.uppercase()) {
            "WATER" -> listOf("#water")
            "SURFACE" -> listOf("#solid")
            "UNDERGROUND" -> listOf("#air")
            else -> listOf("#solid", "#water", "#air")
        }

    private fun coordinatePart(value: Int): String =
        if (value < 0) "neg${-value.toLong()}" else value.toString()

    private data class LegacySpawnerData(
        val spawnerPos: LegacySerializableBlockPos = LegacySerializableBlockPos(),
        val spawnerName: String = "default_spawner",
        val selectedPokemon: List<LegacyPokemonSpawnEntry> = emptyList(),
        val dimension: String = "minecraft:overworld",
        val spawnTimerTicks: Long = 200,
        val spawnRadius: LegacySpawnRadius? = LegacySpawnRadius(),
        val spawnLimit: Int = 4,
        val spawnAmountPerSpawn: Int = 1,
        val requirePlayerInRange: Boolean = true,
        val playerActivationRange: Int = 30,
        val wanderingSettings: LegacyWanderingSettings? = LegacyWanderingSettings()
    )

    private data class LegacyPokemonSpawnEntry(
        val pokemonName: String = "",
        val formName: String? = null,
        val aspects: Set<String> = emptySet(),
        val spawnChance: Double = 50.0,
        val spawnChanceType: SpawnChanceType = SpawnChanceType.COMPETITIVE,
        val minLevel: Int = 1,
        val maxLevel: Int = 100,
        val sizeSettings: SizeSettings = SizeSettings(),
        val captureSettings: CaptureSettings = CaptureSettings(),
        val ivSettings: IVSettings = IVSettings(),
        val evSettings: EVSettings = EVSettings(),
        val spawnSettings: LegacySpawnSettings = LegacySpawnSettings(),
        val heldItemsOnSpawn: HeldItemsOnSpawn = HeldItemsOnSpawn(),
        val moves: MovesSettings? = null
    )

    private data class LegacySpawnRadius(val width: Int = 4, val height: Int = 4)

    private data class LegacySerializableBlockPos(
        @SerializedName(value = "x", alternate = ["field_11175", "field_11176"])
        val x: Int = 0,
        @SerializedName(value = "y", alternate = ["field_11174", "field_11177"])
        val y: Int = 0,
        @SerializedName(value = "z", alternate = ["field_11173", "field_11178"])
        val z: Int = 0
    )

    private data class LegacyWanderingSettings(
        val enabled: Boolean = true
    )

    private data class LegacySpawnSettings(
        val spawnTime: String = "ALL",
        val spawnWeather: String = "ALL",
        val spawnLocation: String = "ALL"
    )
}
