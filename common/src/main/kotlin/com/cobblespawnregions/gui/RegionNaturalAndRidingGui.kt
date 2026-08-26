package com.cobblespawnregions.gui

import com.cobblespawnregions.utils.RegionsConfig
import com.cobblespawnregions.utils.RestrictionTarget
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

object RegionNaturalAndRidingGui {
    private object Textures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun open(player: ServerPlayer, regionId: String) {
        val region = RegionsConfig.getRegion(regionId) ?: return
        val layout = MutableList(54) { filler() }
        layout[21] = option(Items.GRASS_BLOCK, "Natural Spawns", "Configure natural spawn blocking")
        layout[23] = option(Items.SADDLE, "Riding", "Configure riding restrictions")
        layout[49] = backBtn()
        CustomGui.openGui(player, "Natural Spawns and Riding - ${region.regionName}", layout, { ctx ->
            when (ctx.slotIndex) {
                21 -> RegionNaturalSpawnGui.open(player, regionId, RestrictionTarget.NATURAL_SPAWNS)
                23 -> RegionNaturalSpawnGui.open(player, regionId, RestrictionTarget.RIDING)
                49 -> RegionEditorGui.open(player, regionId)
            }
        }, {})
    }

    private fun option(item: net.minecraft.world.item.Item, name: String, lore: String) = ItemStack(item).apply {
        setCustomName(Component.literal(name).withStyle(ChatFormatting.AQUA))
        CustomGui.setItemLore(this, listOf("§7$lore", "", "§eClick §7to open"))
    }

    private fun backBtn() = CustomGui.createPlayerHeadButton(
        "Back",
        Component.literal("Back").withStyle(ChatFormatting.RED),
        listOf(Component.literal("§7Return to region settings")),
        Textures.BACK
    )

    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
