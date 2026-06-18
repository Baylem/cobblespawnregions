package com.cobblespawnregions.utils

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class RegionCatchingTracker {

    private data class PokeballTrackingInfo(
        val pokeBallUuid: UUID,
        val pokeBallEntity: EmptyPokeBallEntity
    )

    private val playerTrackingMap = ConcurrentHashMap<ServerPlayerEntity, ConcurrentLinkedQueue<PokeballTrackingInfo>>()

    fun registerEvents() {
        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            handlePokeBallCaptureCalculated(event)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            val mapIterator = playerTrackingMap.entries.iterator()
            while (mapIterator.hasNext()) {
                val entry = mapIterator.next()
                val player = entry.key
                val queue = entry.value
                val world = player.world as? ServerWorld ?: continue

                val queueIterator = queue.iterator()
                while (queueIterator.hasNext()) {
                    val trackingInfo = queueIterator.next()
                    if (world.getEntity(trackingInfo.pokeBallUuid) == null) {
                        returnPokeballToPlayer(player, trackingInfo.pokeBallEntity)
                        queueIterator.remove()
                    }
                }

                if (queue.isEmpty()) mapIterator.remove()
            }
        }
    }

    private fun handlePokeBallCaptureCalculated(event: PokeBallCaptureCalculatedEvent) {
        val pokeBallEntity = event.pokeBallEntity
        val pokemonEntity = event.pokemonEntity
        val thrower = pokeBallEntity.owner as? ServerPlayerEntity ?: return
        val entry = resolveEntry(pokemonEntity) ?: return
        val captureSettings = entry.captureSettings

        var blockCapture = false
        if (!captureSettings.isCatchable) {
            thrower.sendMessage(Text.literal("§c[CSR] §fThis Pokemon cannot be captured."), false)
            blockCapture = true
        } else if (captureSettings.restrictCaptureToLimitedBalls) {
            val usedBall = Registries.ITEM.getId(pokeBallEntity.pokeBall.item()).toString()
            val allowedBalls = prepareAllowedPokeBallList(captureSettings.requiredPokeBalls)
            val usedStack = ItemStackSerialization.peekThrownBallStack(pokeBallEntity.uuid)
            val customAllowed = captureSettings.customRequiredPokeBalls
            val customMatches = usedStack != null && customAllowed.any { ItemStackSerialization.equivalent(it, usedStack) }
            if (!allowedBalls.contains("ALL") && allowedBalls.none { it.equals(usedBall, ignoreCase = true) } && !customMatches) {
                val allowedDisplay = allowedBallDisplay(allowedBalls, customAllowed, thrower.serverWorld)
                thrower.sendMessage(Text.literal("§c[CSR] §fOnly specific Poke Balls work. §7Allowed: §e$allowedDisplay"), false)
                blockCapture = true
            }
        }

        if (blockCapture) {
            event.captureResult = CaptureContext(
                numberOfShakes = 0,
                isSuccessfulCapture = false,
                isCriticalCapture = false
            )
            playerTrackingMap.computeIfAbsent(thrower) { ConcurrentLinkedQueue() }
                .add(PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity))
        } else {
            ItemStackSerialization.takeThrownBallStack(pokeBallEntity.uuid)
        }
    }

    private fun resolveEntry(entity: PokemonEntity): PokemonSpawnEntry? {
        val data = entity.pokemon.persistentData
        val regionId = data.getString(RegionEntityTracker.REGION_KEY)
        val entryKey = data.getString(RegionEntityTracker.ENTRY_KEY)
        if (regionId.isEmpty() || entryKey.isEmpty()) return null
        val region = RegionsConfig.getRegion(regionId) ?: return null
        return region.selectedPokemon.find { RegionEntityTracker.entryKey(it) == entryKey }
    }

    private fun prepareAllowedPokeBallList(allowedPokeBalls: List<String>): List<String> =
        allowedPokeBalls.map {
            val lower = it.lowercase()
            when {
                lower == "all" -> "ALL"
                !lower.contains(":") -> "cobblemon:$lower"
                else -> lower
            }
        }

    private fun allowedBallDisplay(allowedPokeBalls: List<String>, customAllowed: List<SerializableItemStack>, world: ServerWorld): String {
        val ops: RegistryOps<JsonElement> = RegistryOps.of(JsonOps.INSTANCE, world.server.registryManager)
        val standard = allowedPokeBalls.map { if (it == "ALL") "all" else it.substringAfter(":") }
        val custom = customAllowed.mapNotNull {
            runCatching { it.toItemStack(ops).name.string }.getOrNull()
        }
        return (standard + custom).ifEmpty { listOf("none") }.joinToString()
    }

    private fun returnPokeballToPlayer(player: ServerPlayerEntity, pokeBallEntity: EmptyPokeBallEntity) {
        val pokeBallStack = restoredPokeBallStack(player, pokeBallEntity)
        if (pokeBallStack.isEmpty) return
        if (!pokeBallEntity.isRemoved) pokeBallEntity.discard()

        val ballPos = pokeBallEntity.blockPos
        if (!player.inventory.insertStack(pokeBallStack)) {
            val itemEntity = ItemEntity(player.world, ballPos.x + 0.5, ballPos.y + 0.5, ballPos.z + 0.5, pokeBallStack)
            itemEntity.setToDefaultPickupDelay()
            player.world.spawnEntity(itemEntity)
        }
    }

    private fun restoredPokeBallStack(player: ServerPlayerEntity, pokeBallEntity: EmptyPokeBallEntity): ItemStack {
        val serialized = ItemStackSerialization.takeThrownBallStack(pokeBallEntity.uuid) ?: return pokeBallEntity.pokeBall.item().defaultStack
        val ops: RegistryOps<JsonElement> = RegistryOps.of(JsonOps.INSTANCE, player.server.registryManager)
        return runCatching { serialized.toItemStack(ops) }.getOrElse { pokeBallEntity.pokeBall.item().defaultStack }
    }
}
