package com.cobblespawnregions.gui

import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import com.cobblespawnregions.utils.RestrictionTarget

object RegionConditionSpeciesSearchGui {
    private const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    private const val SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="

    fun open(player: ServerPlayerEntity, regionId: String, target: RestrictionTarget) {
        AnvilGuiManager.openAnvilGui(
            player = player, id = "csr_condition_species_search_$regionId",
            title = "Search Pokémon", initialText = "",
            leftItem = button("Cancel", "§cCancel", CANCEL), rightItem = blank(), resultItem = blank(),
            onLeftClick = { goBack(player, regionId, target) }, onRightClick = null,
            onResultClick = { context ->
                val term = context.handler.currentText.trim()
                if (term.isNotBlank()) RegionConditionScannerGui.applySearch(player, term, regionId, target)
                else goBack(player, regionId, target)
            },
            onTextChange = { text ->
                (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.updateSlot(
                    2, if (text.isBlank()) blank() else button("ConditionSearch", "§aSearch: §f$text", SEARCH)
                )
            },
            onClose = {
                player.server.execute {
                    if (player.currentScreenHandler !is FullyModularAnvilScreenHandler) goBack(player, regionId, target)
                }
            }
        )
        player.server.execute {
            (player.currentScreenHandler as? FullyModularAnvilScreenHandler)?.clearTextField()
        }
    }

    private fun goBack(player: ServerPlayerEntity, regionId: String, target: RestrictionTarget) {
        player.server.execute { RegionConditionScannerGui.open(player, regionId, page = 0, target = target) }
    }

    private fun button(id: String, title: String, texture: String) = CustomGui.createPlayerHeadButton(
        id, Text.literal(title), emptyList(), texture
    )

    private fun blank() = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Text.literal(" "))
    }
}
