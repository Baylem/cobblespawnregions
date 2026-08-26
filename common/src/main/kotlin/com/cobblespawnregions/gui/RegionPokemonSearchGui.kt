package com.cobblespawnregions.gui

import com.everlastingutils.gui.AnvilGuiManager
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.FullyModularAnvilScreenHandler
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component













object RegionPokemonSearchGui {

    private object Textures {
        const val CANCEL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SEARCH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY4M2RjN2JjNmRiZGI1ZGM0MzFmYmUyOGRjNGI5YWU2MjViOWU1MzE3YTI5ZjJjNGVjZmU3YmY1YWU1NmMzOCJ9fX0="
    }



    fun open(player: ServerPlayer, regionId: String) {
        val guiId = "csr_pokemon_search_$regionId"

        AnvilGuiManager.openAnvilGui(
            player       = player,
            id           = guiId,
            title        = "Search Pokémon",
            initialText  = "",
            leftItem     = cancelBtn(),
            rightItem    = blockedPane(),
            resultItem   = placeholderResult(),

            onLeftClick  = {

                goBack(player, regionId)
            },

            onRightClick = null,

            onResultClick = { context ->
                val text = context.handler.currentText.trim()
                if (text.isNotBlank()) {

                    RegionPokemonSelectionGui.applySearch(player, text, regionId)
                } else {
                    goBack(player, regionId)
                }
            },

            onTextChange = { text ->
                val handler = player.containerMenu as? FullyModularAnvilScreenHandler
                handler?.updateSlot(
                    2,
                    if (text.isNotEmpty()) confirmBtn(text) else placeholderResult()
                )
            },

            onClose = {

                player.server.execute {
                    if (player.containerMenu !is FullyModularAnvilScreenHandler) {
                        goBack(player, regionId)
                    }
                }
            }
        )


        player.server.execute {
            (player.containerMenu as? FullyModularAnvilScreenHandler)?.clearTextField()
        }

        player.displayClientMessage(
            Component.literal("§7[CSR] §fType a Pokémon name, then click the §agreen button §fto search, or §cX §fto cancel."),
            false
        )
    }



    private fun goBack(player: ServerPlayer, regionId: String) {
        player.server.execute {
            RegionPokemonSelectionGui.open(player, regionId, page = 0)
        }
    }



    private fun cancelBtn() = CustomGui.createPlayerHeadButton(
        textureName  = "CancelSearch",
        title        = Component.literal("§cCancel").withStyle { it.withBold(true).withItalic(false) },
        lore         = listOf(Component.literal("§7Return to Pokémon list without searching")),
        textureValue = Textures.CANCEL
    )

    private fun confirmBtn(term: String) = CustomGui.createPlayerHeadButton(
        textureName  = "ConfirmSearch",
        title        = Component.literal("§aSearch: §f$term").withStyle { it.withBold(true).withItalic(false) },
        lore         = listOf(
            Component.literal("§aClick to search for §f$term"),
            Component.literal("§7Keep typing to refine")
        ),
        textureValue = Textures.SEARCH
    )

    private fun placeholderResult() = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Component.literal(" "))
    }

    private fun blockedPane() = ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE).apply {
        setCustomName(Component.literal(" "))
    }
}
