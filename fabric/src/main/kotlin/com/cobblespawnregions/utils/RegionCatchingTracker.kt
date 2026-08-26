package com.cobblespawnregions.utils

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.pokeball.PokeBallCaptureCalculatedEvent
import com.cobblemon.mod.common.api.events.pokeball.ThrownPokeballHitEvent
import com.cobblemon.mod.common.api.pokeball.catching.CaptureContext
import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.RegistryOps
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class RegionCatchingTracker {
    private val logger = LoggerFactory.getLogger("RegionCatchingTracker")

    private data class PokeballTrackingInfo(
        val pokeBallUuid: UUID,
        val pokeBallEntity: EmptyPokeBallEntity
    )

    private data class CustomBallCheck(
        val index: Int,
        val itemId: String?,
        val matches: Boolean,
        val summary: String
    )

    private val playerTrackingMap = ConcurrentHashMap<ServerPlayer, ConcurrentLinkedQueue<PokeballTrackingInfo>>()
    private val blockedPokeBalls = ConcurrentHashMap<UUID, Component>()

    fun registerEvents() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe { event ->
            handleThrownPokeBallHit(event)
        }

        CobblemonEvents.POKE_BALL_CAPTURE_CALCULATED.subscribe { event ->
            handlePokeBallCaptureCalculated(event)
        }

        ServerTickEvents.END_SERVER_TICK.register {
            val mapIterator = playerTrackingMap.entries.iterator()
            while (mapIterator.hasNext()) {
                val entry = mapIterator.next()
                val player = entry.key
                val queue = entry.value
                val world = player.level() as? ServerLevel ?: continue

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

    private fun handleThrownPokeBallHit(event: ThrownPokeballHitEvent) {
        val pokeBallEntity = event.pokeBall
        val thrower = pokeBallEntity.owner as? ServerPlayer
        if (thrower == null) {
            debug("hit ignored uuid=${pokeBallEntity.uuid} reason=owner_not_server_player entityBall=${entityBallId(pokeBallEntity)} pokemon=${pokemonDebugSummary(event.pokemon)}")
            return
        }
        val entry = resolveEntry(event.pokemon)
        if (entry == null) {
            debug("hit ignored uuid=${pokeBallEntity.uuid} reason=no_region_entry player=${thrower.name.string} pokemon=${pokemonDebugSummary(event.pokemon)} entityBall=${entityBallId(pokeBallEntity)}")
            return
        }
        val message = blockedCaptureMessage(pokeBallEntity, event.pokemon, thrower, entry)
        if (message != null) {
            blockedPokeBalls[pokeBallEntity.uuid] = message
            debug("hit stored block uuid=${pokeBallEntity.uuid} player=${thrower.name.string} pokemon=${pokemonDebugSummary(event.pokemon)} message='${message.string}'")
        } else {
            blockedPokeBalls.remove(pokeBallEntity.uuid)
            debug("hit allowed uuid=${pokeBallEntity.uuid} player=${thrower.name.string} pokemon=${pokemonDebugSummary(event.pokemon)}")
        }
    }

    private fun handlePokeBallCaptureCalculated(event: PokeBallCaptureCalculatedEvent) {
        val pokeBallEntity = event.pokeBallEntity
        val pokemonEntity = event.pokemonEntity
        val thrower = pokeBallEntity.owner as? ServerPlayer
        if (thrower == null) {
            debug("captureCalculated ignored uuid=${pokeBallEntity.uuid} reason=owner_not_server_player entityBall=${entityBallId(pokeBallEntity)} pokemon=${pokemonDebugSummary(pokemonEntity)}")
            return
        }
        val entry = resolveEntry(pokemonEntity)
        if (entry == null) {
            debug("captureCalculated ignored uuid=${pokeBallEntity.uuid} reason=no_region_entry player=${thrower.name.string} pokemon=${pokemonDebugSummary(pokemonEntity)} entityBall=${entityBallId(pokeBallEntity)}")
            return
        }

        val blockedMessage = blockedPokeBalls.remove(pokeBallEntity.uuid)
            ?: blockedCaptureMessage(pokeBallEntity, pokemonEntity, thrower, entry)

        if (blockedMessage != null) {
            debug("captureCalculated blocking uuid=${pokeBallEntity.uuid} player=${thrower.name.string} pokemon=${pokemonDebugSummary(pokemonEntity)} message='${blockedMessage.string}'")
            thrower.displayClientMessage(blockedMessage, false)
            event.captureResult = CaptureContext(
                numberOfShakes = 0,
                isSuccessfulCapture = false,
                isCriticalCapture = false
            )
            playerTrackingMap.computeIfAbsent(thrower) { ConcurrentLinkedQueue() }
                .add(PokeballTrackingInfo(pokeBallEntity.uuid, pokeBallEntity))
        } else {
            debug("captureCalculated allowed uuid=${pokeBallEntity.uuid} player=${thrower.name.string} pokemon=${pokemonDebugSummary(pokemonEntity)} clearingTrackedStack=true")
            ItemStackSerialization.takeThrownBallStack(pokeBallEntity.uuid)
        }
    }

    private fun blockedCaptureMessage(
        pokeBallEntity: EmptyPokeBallEntity,
        pokemonEntity: PokemonEntity,
        thrower: ServerPlayer,
        entry: PokemonSpawnEntry
    ): Component? {
        val captureSettings = entry.captureSettings
        if (!captureSettings.isCatchable) {
            debugDecision(pokeBallEntity, pokemonEntity, thrower, entry, "BLOCK", "pokemon_not_catchable")
            return Component.literal("§c[CSR] §fThis Pokemon cannot be captured.")
        }
        if (!captureSettings.restrictCaptureToLimitedBalls) {
            debugDecision(pokeBallEntity, pokemonEntity, thrower, entry, "ALLOW", "restricted_balls_disabled")
            return null
        }

        val usedBall = BuiltInRegistries.ITEM.getKey(pokeBallEntity.pokeBall.item()).toString()
        val allowedBalls = prepareAllowedPokeBallList(captureSettings.requiredPokeBalls)
        val usedStack = ItemStackSerialization.peekThrownBallStack(pokeBallEntity.uuid)
        val customAllowed = captureSettings.customRequiredPokeBalls
        val ops: RegistryOps<JsonElement> = RegistryOps.create(JsonOps.INSTANCE, thrower.server.registryAccess())
        val customChecks = customAllowed.mapIndexed { index, allowed ->
            customBallCheck(index, allowed, usedStack, ops)
        }
        val customMatches = customChecks.any { it.matches }
        val customItemIds = customChecks.mapNotNull { it.itemId }.toSet()
        val standardMatches = allowedBalls.any { it.equals(usedBall, ignoreCase = true) } && usedBall !in customItemIds

        if (!allowedBalls.contains("ALL") && !standardMatches && !customMatches) {
            val allowedDisplay = allowedBallDisplay(allowedBalls, customAllowed, thrower.serverLevel())
            debugBallDecision(
                pokeBallEntity,
                pokemonEntity,
                thrower,
                entry,
                "BLOCK",
                usedBall,
                allowedBalls,
                captureSettings.requiredPokeBalls,
                usedStack,
                customChecks,
                customItemIds,
                standardMatches,
                customMatches,
                "no_allowed_ball_match"
            )
            return Component.literal("§c[CSR] §fOnly specific Poke Balls work. §7Allowed: §e$allowedDisplay")
        }

        debugBallDecision(
            pokeBallEntity,
            pokemonEntity,
            thrower,
            entry,
            "ALLOW",
            usedBall,
            allowedBalls,
            captureSettings.requiredPokeBalls,
            usedStack,
            customChecks,
            customItemIds,
            standardMatches,
            customMatches,
            if (allowedBalls.contains("ALL")) "all_allowed" else "matched_allowed_ball"
        )
        return null
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

    private fun allowedBallDisplay(allowedPokeBalls: List<String>, customAllowed: List<SerializableItemStack>, world: ServerLevel): String {
        val ops: RegistryOps<JsonElement> = RegistryOps.create(JsonOps.INSTANCE, world.server.registryAccess())
        val standard = allowedPokeBalls.map { if (it == "ALL") "all" else it.substringAfter(":") }
        val custom = customAllowed.mapNotNull {
            runCatching { it.toItemStack(ops).hoverName.string }.getOrNull()
        }
        return (standard + custom).ifEmpty { listOf("none") }.joinToString()
    }

    private fun returnPokeballToPlayer(player: ServerPlayer, pokeBallEntity: EmptyPokeBallEntity) {
        val pokeBallStack = restoredPokeBallStack(player, pokeBallEntity)
        if (pokeBallStack.isEmpty) {
            debug("returnPokeballToPlayer skipped uuid=${pokeBallEntity.uuid} player=${player.name.string} reason=empty_stack")
            return
        }
        if (!pokeBallEntity.isRemoved) pokeBallEntity.discard()

        val ballPos = pokeBallEntity.blockPosition()
        if (!player.inventory.add(pokeBallStack)) {
            val itemEntity = ItemEntity(player.level(), ballPos.x + 0.5, ballPos.y + 0.5, ballPos.z + 0.5, pokeBallStack)
            itemEntity.setDefaultPickUpDelay()
            player.level().addFreshEntity(itemEntity)
            debug("returnPokeballToPlayer dropped uuid=${pokeBallEntity.uuid} player=${player.name.string} stack=${ItemStackSerialization.stackDebugSummary(pokeBallStack)} pos=$ballPos")
        } else {
            debug("returnPokeballToPlayer inserted uuid=${pokeBallEntity.uuid} player=${player.name.string} stack=${ItemStackSerialization.stackDebugSummary(pokeBallStack)}")
        }
    }

    private fun restoredPokeBallStack(player: ServerPlayer, pokeBallEntity: EmptyPokeBallEntity): ItemStack {
        val serialized = ItemStackSerialization.takeThrownBallStack(pokeBallEntity.uuid)
        if (serialized == null) {
            val fallback = pokeBallEntity.pokeBall.item().defaultInstance
            debug("restoredPokeBallStack fallback uuid=${pokeBallEntity.uuid} player=${player.name.string} reason=missing_serialized entityBall=${entityBallId(pokeBallEntity)} fallback=${ItemStackSerialization.stackDebugSummary(fallback)}")
            return fallback
        }
        val ops: RegistryOps<JsonElement> = RegistryOps.create(JsonOps.INSTANCE, player.server.registryAccess())
        return runCatching { serialized.toItemStack(ops) }.getOrElse {
            val fallback = pokeBallEntity.pokeBall.item().defaultInstance
            RegionsConfig.debugError(logger, "[CSR-CAPTURE-BALL] restoredPokeBallStack deserialize failed uuid=${pokeBallEntity.uuid} player=${player.name.string} serialized=${serialized.itemStackString.abbreviateForDebug()} fallback=${ItemStackSerialization.stackDebugSummary(fallback)}", it)
            fallback
        }
    }

    private fun customBallCheck(
        index: Int,
        allowed: SerializableItemStack,
        usedStack: SerializableItemStack?,
        ops: RegistryOps<JsonElement>
    ): CustomBallCheck {
        val allowedStack = runCatching { allowed.toItemStack(ops) }
        val itemId = allowedStack.getOrNull()?.let { BuiltInRegistries.ITEM.getKey(it.item).toString() }
        val matches = if (usedStack == null) {
            false
        } else {
            runCatching { ItemStackSerialization.equivalentCaptureBall(allowed, usedStack) }.getOrElse {
                RegionsConfig.debugError(logger, "[CSR-CAPTURE-BALL] custom ball comparison failed index=$index allowed=${allowed.itemStackString.abbreviateForDebug()} used=${usedStack.itemStackString.abbreviateForDebug()}", it)
                false
            }
        }
        val summary = allowedStack.fold(
            onSuccess = { ItemStackSerialization.stackDebugSummary(it) },
            onFailure = { "stackError='${it.message}'" }
        ) + " serialized=${allowed.itemStackString.abbreviateForDebug()}"
        return CustomBallCheck(index, itemId, matches, summary)
    }

    private fun debugDecision(
        pokeBallEntity: EmptyPokeBallEntity,
        pokemonEntity: PokemonEntity,
        thrower: ServerPlayer,
        entry: PokemonSpawnEntry,
        decision: String,
        reason: String
    ) {
        debug(
            "decision=$decision reason=$reason uuid=${pokeBallEntity.uuid} player=${thrower.name.string} " +
                    "pokemon=${pokemonDebugSummary(pokemonEntity)} region=${regionId(pokemonEntity)} entry=${RegionEntityTracker.entryKey(entry)} entityBall=${entityBallId(pokeBallEntity)}"
        )
    }

    private fun debugBallDecision(
        pokeBallEntity: EmptyPokeBallEntity,
        pokemonEntity: PokemonEntity,
        thrower: ServerPlayer,
        entry: PokemonSpawnEntry,
        decision: String,
        usedBall: String,
        allowedBalls: List<String>,
        rawAllowedBalls: List<String>,
        usedStack: SerializableItemStack?,
        customChecks: List<CustomBallCheck>,
        customItemIds: Set<String>,
        standardMatches: Boolean,
        customMatches: Boolean,
        reason: String
    ) {
        if (!RegionsConfig.config.debugEnabled) return
        val ops: RegistryOps<JsonElement> = RegistryOps.create(JsonOps.INSTANCE, thrower.server.registryAccess())
        val usedSerialized = usedStack?.let { ItemStackSerialization.serializedDebugSummary(it, ops) } ?: "none"
        debug(
            "decision=$decision reason=$reason uuid=${pokeBallEntity.uuid} player=${thrower.name.string} " +
                    "pokemon=${pokemonDebugSummary(pokemonEntity)} region=${regionId(pokemonEntity)} entry=${RegionEntityTracker.entryKey(entry)} " +
                    "entityBall=${entityBallId(pokeBallEntity)} usedBall=$usedBall rawAllowed=$rawAllowedBalls preparedAllowed=$allowedBalls " +
                    "customItemIds=$customItemIds standardMatches=$standardMatches customMatches=$customMatches usedSerialized=$usedSerialized"
        )
        customChecks.forEach {
            debug("customAllowed index=${it.index} itemId=${it.itemId ?: "unresolved"} matches=${it.matches} ${it.summary}")
        }
    }

    private fun pokemonDebugSummary(entity: PokemonEntity): String =
        "${entity.pokemon.species.name} uuid=${entity.uuid} pos=${entity.blockPosition()}"

    private fun regionId(entity: PokemonEntity): String =
        entity.pokemon.persistentData.getString(RegionEntityTracker.REGION_KEY).ifEmpty { "none" }

    private fun entityBallId(entity: EmptyPokeBallEntity): String =
        BuiltInRegistries.ITEM.getKey(entity.pokeBall.item()).toString()

    private fun debug(message: String) {
        RegionsConfig.debugLog(logger, "[CSR-CAPTURE-BALL] $message")
    }

    private fun String.abbreviateForDebug(maxLength: Int = 500): String =
        if (length <= maxLength) this else take(maxLength) + "...(${length} chars)"
}
