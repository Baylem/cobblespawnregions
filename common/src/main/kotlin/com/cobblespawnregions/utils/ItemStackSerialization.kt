package com.cobblespawnregions.utils

import com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.resources.RegistryOps
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.GsonHelper
import org.slf4j.LoggerFactory
import java.util.UUID

data class SerializableItemStack(val itemStackString: String) {
    fun toItemStack(ops: DynamicOps<JsonElement>): ItemStack = deserializeItemStack(itemStackString, ops)

    companion object {
        fun fromItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): SerializableItemStack {
            val copy = itemStack.copy()
            copy.count = 1
            return SerializableItemStack(serializeItemStack(copy, ops))
        }
    }
}

object ItemStackSerialization {
    private val logger = LoggerFactory.getLogger("ItemStackSerialization")
    private val gson = Gson()
    private val thrownBallStacks = java.util.concurrent.ConcurrentHashMap<UUID, SerializableItemStack>()
    private val threadThrownStack = ThreadLocal<ItemStack>()

    @JvmStatic
    fun beginThrow(stack: ItemStack) {
        threadThrownStack.set(stack.copy())
        debug("beginThrow thread=${Thread.currentThread().name} stack=${stackDebugSummary(stack)}")
    }

    @JvmStatic
    fun endThrow() {
        val stack = threadThrownStack.get()
        debug("endThrow thread=${Thread.currentThread().name} stack=${stack?.let(::stackDebugSummary) ?: "none"}")
        threadThrownStack.remove()
    }

    @JvmStatic
    fun recordThrownBall(entity: Entity) {
        if (entity !is EmptyPokeBallEntity) return
        val stack = threadThrownStack.get()
        if (stack == null) {
            debug("recordThrownBall uuid=${entity.uuid} missing captured thread stack entityBall=${entityBallDebugSummary(entity)}")
            return
        }

        val world = entity.level() as? ServerLevel
        if (world == null) {
            debug("recordThrownBall uuid=${entity.uuid} missing server world entityBall=${entityBallDebugSummary(entity)} captured=${stackDebugSummary(stack)}")
            return
        }

        val ops = RegistryOps.create(JsonOps.INSTANCE, world.server.registryAccess())
        val serialized = runCatching { SerializableItemStack.fromItemStack(stack, ops) }.getOrElse {
            RegionsConfig.debugError(logger, "[CSR-CAPTURE-BALL] recordThrownBall failed uuid=${entity.uuid} entityBall=${entityBallDebugSummary(entity)} captured=${stackDebugSummary(stack)}", it)
            return
        }
        thrownBallStacks[entity.uuid] = serialized
        debug(
            "recordThrownBall uuid=${entity.uuid} entityBall=${entityBallDebugSummary(entity)} captured=${stackDebugSummary(stack)} " +
                    "serialized=${serializedDebugSummary(serialized, ops)} trackedCount=${thrownBallStacks.size}"
        )
    }

    fun takeThrownBallStack(uuid: UUID): SerializableItemStack? {
        val serialized = thrownBallStacks.remove(uuid)
        debug("takeThrownBallStack uuid=$uuid found=${serialized != null} trackedCount=${thrownBallStacks.size} serialized=${serialized?.itemStackString?.abbreviateForDebug() ?: "none"}")
        return serialized
    }

    fun peekThrownBallStack(uuid: UUID): SerializableItemStack? {
        val serialized = thrownBallStacks[uuid]
        debug("peekThrownBallStack uuid=$uuid found=${serialized != null} trackedCount=${thrownBallStacks.size} serialized=${serialized?.itemStackString?.abbreviateForDebug() ?: "none"}")
        return serialized
    }

    fun equivalent(a: SerializableItemStack, b: SerializableItemStack): Boolean =
        normalizeJson(a.itemStackString) == normalizeJson(b.itemStackString)

    fun equivalentCaptureBall(a: SerializableItemStack, b: SerializableItemStack): Boolean =
        normalizeCaptureBallJson(a.itemStackString) == normalizeCaptureBallJson(b.itemStackString)

    fun stackDebugSummary(stack: ItemStack): String =
        "item=${BuiltInRegistries.ITEM.getKey(stack.item)} name='${stack.hoverName.string}' count=${stack.count} empty=${stack.isEmpty}"

    fun serializedDebugSummary(serialized: SerializableItemStack, ops: DynamicOps<JsonElement>): String {
        val stack = runCatching { serialized.toItemStack(ops) }
            .fold(
                onSuccess = { stackDebugSummary(it) },
                onFailure = { "stackError='${it.message}'" }
            )
        val normalized = runCatching { normalizeJson(serialized.itemStackString) }
            .getOrElse { "normalizeError='${it.message}'" }
        val captureNormalized = runCatching { normalizeCaptureBallJson(serialized.itemStackString) }
            .getOrElse { "captureNormalizeError='${it.message}'" }
        return "$stack json=${serialized.itemStackString.abbreviateForDebug()} normalized=${normalized.abbreviateForDebug()} captureNormalized=${captureNormalized.abbreviateForDebug()}"
    }

    private fun normalizeJson(raw: String): String =
        gson.toJson(GsonHelper.parse(raw))

    private fun normalizeCaptureBallJson(raw: String): String {
        val json = GsonHelper.parse(raw)
        val itemObject = json.asJsonObject
        val components = itemObject.getAsJsonObject("components") ?: return gson.toJson(json)
        removeEmptyEnchantments(components)
        removeEmptyObjectComponent(components, "minecraft:hide_additional_tooltip")
        if (components.entrySet().isEmpty()) itemObject.remove("components")
        return gson.toJson(json)
    }

    private fun removeEmptyEnchantments(components: com.google.gson.JsonObject) {
        val enchantments = components.getAsJsonObject("minecraft:enchantments") ?: return
        val levels = enchantments.getAsJsonObject("levels")
        val levelsEmpty = levels == null || levels.entrySet().isEmpty()
        val showInTooltip = enchantments.get("show_in_tooltip")?.takeIf { it.isJsonPrimitive }?.asBoolean
        val onlyDefaultKeys = enchantments.entrySet().all { it.key == "levels" || it.key == "show_in_tooltip" }
        if (levelsEmpty && showInTooltip == false && onlyDefaultKeys) {
            components.remove("minecraft:enchantments")
        }
    }

    private fun removeEmptyObjectComponent(components: com.google.gson.JsonObject, key: String) {
        val component = components.get(key) ?: return
        if (component.isJsonObject && component.asJsonObject.entrySet().isEmpty()) {
            components.remove(key)
        }
    }

    private fun entityBallDebugSummary(entity: EmptyPokeBallEntity): String =
        "item=${BuiltInRegistries.ITEM.getKey(entity.pokeBall.item())} name='${entity.pokeBall.item().defaultInstance.hoverName.string}'"

    private fun debug(message: String) {
        RegionsConfig.debugLog(logger, "[CSR-CAPTURE-BALL] $message")
    }

    private fun String.abbreviateForDebug(maxLength: Int = 500): String =
        if (length <= maxLength) this else take(maxLength) + "...(${length} chars)"
}

private val itemStackGson = Gson()

fun serializeItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): String {
    val result = ItemStack.CODEC.encodeStart(ops, itemStack)
    val jsonElement = result.getOrThrow { error -> throw RuntimeException("Failed to serialize ItemStack: $error") }
    return itemStackGson.toJson(jsonElement)
}

fun deserializeItemStack(jsonString: String, ops: DynamicOps<JsonElement>): ItemStack {
    val jsonElement = GsonHelper.parse(jsonString)
    val result = ItemStack.CODEC.parse(ops, jsonElement)
    return result.getOrThrow { error -> throw RuntimeException("Failed to deserialize ItemStack: $error") }
}
