package com.cobblespawnregions.utils

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.mojang.serialization.DynamicOps
import net.minecraft.item.ItemStack
import net.minecraft.util.JsonHelper

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
    private val gson = Gson()
    private val thrownBallStacks = java.util.concurrent.ConcurrentHashMap<java.util.UUID, SerializableItemStack>()
    private val threadThrownStack = ThreadLocal<ItemStack>()

    @JvmStatic
    fun beginThrow(stack: ItemStack) {
        threadThrownStack.set(stack.copy())
    }

    @JvmStatic
    fun endThrow() {
        threadThrownStack.remove()
    }

    @JvmStatic
    fun recordThrownBall(entity: net.minecraft.entity.Entity) {
        if (entity !is com.cobblemon.mod.common.entity.pokeball.EmptyPokeBallEntity) return
        val stack = threadThrownStack.get() ?: return
        val world = entity.world as? net.minecraft.server.world.ServerWorld ?: return
        val ops = net.minecraft.registry.RegistryOps.of(com.mojang.serialization.JsonOps.INSTANCE, world.server.registryManager)
        thrownBallStacks[entity.uuid] = SerializableItemStack.fromItemStack(stack, ops)
    }

    fun takeThrownBallStack(uuid: java.util.UUID): SerializableItemStack? =
        thrownBallStacks.remove(uuid)

    fun peekThrownBallStack(uuid: java.util.UUID): SerializableItemStack? =
        thrownBallStacks[uuid]

    fun equivalent(a: SerializableItemStack, b: SerializableItemStack): Boolean =
        normalizeJson(a.itemStackString) == normalizeJson(b.itemStackString)

    private fun normalizeJson(raw: String): String =
        gson.toJson(JsonHelper.deserialize(raw))
}

private val itemStackGson = Gson()

fun serializeItemStack(itemStack: ItemStack, ops: DynamicOps<JsonElement>): String {
    val result = ItemStack.CODEC.encodeStart(ops, itemStack)
    val jsonElement = result.getOrThrow { error -> throw RuntimeException("Failed to serialize ItemStack: $error") }
    return itemStackGson.toJson(jsonElement)
}

fun deserializeItemStack(jsonString: String, ops: DynamicOps<JsonElement>): ItemStack {
    val jsonElement = JsonHelper.deserialize(jsonString)
    val result = ItemStack.CODEC.parse(ops, jsonElement)
    return result.getOrThrow { error -> throw RuntimeException("Failed to deserialize ItemStack: $error") }
}
