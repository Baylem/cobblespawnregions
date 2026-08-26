package com.cobblespawnregions.gui

import com.everlastingutils.gui.CustomScreenHandler
import net.minecraft.world.item.ItemStack
import net.minecraft.server.level.ServerPlayer

fun ServerPlayer.refreshGuiSlots(vararg updates: Pair<Int, ItemStack>) {
    val current = containerMenu as? CustomScreenHandler ?: return
    val syncId = current.containerId

    server.execute {
        val handler = containerMenu as? CustomScreenHandler ?: return@execute
        if (handler.containerId != syncId) return@execute

        updates.forEach { (slot, stack) ->
            if (slot in 0 until handler.rows * 9) {
                handler.getSlot(slot).set(stack.copy())
            }
        }
        handler.broadcastChanges()
    }
}
