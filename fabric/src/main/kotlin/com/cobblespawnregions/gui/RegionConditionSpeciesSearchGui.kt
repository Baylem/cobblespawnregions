package com.cobblespawnregions.gui

import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import com.cobblespawnregions.utils.RestrictionTarget

object RegionConditionSpeciesSearchGui {
    private const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    private const val SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="

    fun open(player: ServerPlayer, regionId: String, target: RestrictionTarget) {
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
                (player.containerMenu as? FullyModularAnvilScreenHandler)?.updateSlot(
                    2, if (text.isBlank()) blank() else button("ConditionSearch", "§aSearch: §f$text", SEARCH)
                )
            },
            onClose = {
                player.server.execute {
                    if (player.containerMenu !is FullyModularAnvilScreenHandler) goBack(player, regionId, target)
                }
            }
        )
        player.server.execute {
            (player.containerMenu as? FullyModularAnvilScreenHandler)?.clearTextField()
        }
    }

    private fun goBack(player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        player.server.execute { RegionConditionScannerGui.open(player, regionId, page = 0, target = target) }
    }

    private fun button(id: String, title: String, texture: String) = CustomGui.createPlayerHeadButton(
        id, Component.literal(title), emptyList(), texture
    )

    private fun blank() = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Component.literal(" "))
    }
}
