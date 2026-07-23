package com.cobblespawnregions.gui

import com.cobblespawnregions.utils.RegionsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.concurrent.ConcurrentHashMap

object RegionPokemonDefaultsCloneGui {
    private const val PAGE_SIZE = 45
    private const val PREV = 45
    private const val BACK = 49
    private const val NEXT = 53
    private val pages = ConcurrentHashMap<ServerPlayerEntity, Int>()
    private val visibleRegions = ConcurrentHashMap<ServerPlayerEntity, Map<Int, String>>()

    fun open(player: ServerPlayerEntity, targetRegionId: String, page: Int = 0) {
        pages[player] = page
        CustomGui.openGui(
            player,
            "Clone Pokémon Defaults",
            buildLayout(player, targetRegionId),
            { ctx -> handleClick(ctx, player, targetRegionId) },
            {}
        )
    }

    private fun handleClick(ctx: InteractionContext, player: ServerPlayerEntity, targetRegionId: String) {
        val page = pages[player] ?: 0
        when (ctx.slotIndex) {
            PREV -> if (page > 0) open(player, targetRegionId, page - 1)
            NEXT -> {
                val count = sourceRegions(targetRegionId).size
                if ((page + 1) * PAGE_SIZE < count) open(player, targetRegionId, page + 1)
            }
            BACK -> {
                clear(player)
                RegionPokemonEntryGui.openDefaults(player, targetRegionId)
            }
            in 0 until PAGE_SIZE -> {
                val sourceRegionId = visibleRegions[player]?.get(ctx.slotIndex) ?: return
                if (RegionsConfig.copyDefaultsFromRegion(targetRegionId, sourceRegionId)) {
                    player.sendMessage(Text.literal("§a[CSR] §fCopied Pokémon defaults from §e${RegionsConfig.getRegion(sourceRegionId)?.regionName ?: sourceRegionId}§f."), false)
                    clear(player)
                    RegionPokemonEntryGui.openDefaults(player, targetRegionId)
                }
            }
        }
    }

    private fun buildLayout(player: ServerPlayerEntity, targetRegionId: String): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        val page = pages[player] ?: 0
        val sources = sourceRegions(targetRegionId)
        val pageSources = sources.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        visibleRegions[player] = pageSources.mapIndexed { slot, region -> slot to region.regionId }.toMap()

        pageSources.forEachIndexed { slot, region ->
            layout[slot] = ItemStack(Items.MAP).apply {
                setCustomName(Text.literal(region.regionName).formatted(Formatting.YELLOW))
                CustomGui.setItemLore(this, listOf(
                    "§7Region ID: §f${region.regionId}",
                    "§7Configured Pokémon: §f${region.selectedPokemon.size}",
                    "",
                    "§eClick §7to copy its defaults"
                ))
            }
        }
        if (page > 0) layout[PREV] = button(Items.ARROW, "Previous Page")
        layout[BACK] = button(Items.BARRIER, "Back")
        if ((page + 1) * PAGE_SIZE < sources.size) layout[NEXT] = button(Items.ARROW, "Next Page")
        return layout
    }

    private fun sourceRegions(targetRegionId: String) = RegionsConfig.allRegions()
        .filter { it.regionId != targetRegionId }
        .sortedBy { it.regionName.lowercase() }

    private fun clear(player: ServerPlayerEntity) {
        pages.remove(player)
        visibleRegions.remove(player)
    }

    private fun button(item: net.minecraft.item.Item, name: String) = ItemStack(item).apply {
        setCustomName(Text.literal(name).formatted(if (name == "Back") Formatting.RED else Formatting.GREEN))
    }

    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Text.literal(" ")) }
}
