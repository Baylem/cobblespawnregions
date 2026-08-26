package com.cobblespawnregions.gui

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Species
import com.cobblespawnregions.utils.PokemonConditionExtractor
import com.cobblespawnregions.utils.RegionsConfig
import com.cobblespawnregions.utils.RestrictionTarget
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting
import org.joml.Vector4f
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object RegionConditionScannerGui {

    private enum class SortMethod { ALPHABETICAL, TYPE, SEARCH }

    private val logger = LoggerFactory.getLogger("RegionConditionScannerGui")
    private const val PAGE_SIZE = 45
    private val playerPages = ConcurrentHashMap<ServerPlayer, Int>()
    private val playerSortMethods = ConcurrentHashMap<ServerPlayer, SortMethod>()
    private val playerSearchTerms = ConcurrentHashMap<ServerPlayer, String>()

    private val allSpecies: List<Species> by lazy {
        com.cobblemon.mod.common.api.pokemon.PokemonSpecies.species
            .filter { it.implemented }.sortedBy { it.name }
    }

    private object Slots {
        const val PREV      = 45
        const val SORT      = 48
        const val BACK      = 49
        const val EXCLUDED  = 50
        const val NEXT      = 53
    }

    private object Textures {
        const val PREV    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SORT    = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWI1ZWU0MTlhZDljMDYwYzE2Y2I1M2IxZGNmZmFjOGJhY2EwYjJhMjI2NWIxYjZjN2U4ZTc4MGMzN2IxMDRjMCJ9fX0="
    }

    fun open(player: ServerPlayer, regionId: String, page: Int = 0, target: RestrictionTarget = RestrictionTarget.NATURAL_SPAWNS) {
        playerPages[player] = page
        val label = RegionsConfig.scopeLabel(regionId)

        CustomGui.openGui(
            player,
            "Scan Conditions — $label",
            buildLayout(player, regionId, target),
            { ctx -> handleClick(ctx, player, regionId, target) },
            { playerPages.remove(player) }
        )
    }

    private fun handleClick(ctx: InteractionContext, player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        when (ctx.slotIndex) {
            Slots.PREV -> {
                val page = (playerPages[player] ?: 0) - 1
                if (page >= 0) { playerPages[player] = page; refresh(player, regionId, target) }
            }
            Slots.NEXT -> {
                val page = (playerPages[player] ?: 0) + 1
                if (page * PAGE_SIZE < speciesFor(player).size) { playerPages[player] = page; refresh(player, regionId, target) }
            }
            Slots.SORT -> if (ctx.button == 1) {
                RegionConditionSpeciesSearchGui.open(player, regionId, target)
            } else {
                val next = when (playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)) {
                    SortMethod.ALPHABETICAL -> SortMethod.TYPE
                    SortMethod.TYPE, SortMethod.SEARCH -> SortMethod.ALPHABETICAL
                }
                playerSortMethods[player] = next
                playerSearchTerms.remove(player)
                playerPages[player] = 0
                refresh(player, regionId, target)
            }
            Slots.EXCLUDED ->
                RegionExcludedConditionsListGui.open(player, regionId, target = target)
            Slots.BACK -> RegionNaturalSpawnGui.open(player, regionId, target)
            in 0 until PAGE_SIZE -> scanSpecies(ctx.slotIndex, player, regionId, target)
        }
    }

    private fun scanSpecies(slot: Int, player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        val page = playerPages[player] ?: 0
        val idx = page * PAGE_SIZE + slot
        val availableSpecies = speciesFor(player)
        if (idx >= availableSpecies.size) return

        val species = availableSpecies[idx]

        val filteredConditions = PokemonConditionExtractor.scanSpeciesForConditions(player, species.name)

        if (filteredConditions.isEmpty()) {
            player.displayClientMessage(
                Component.literal("§c[CSR] §fFailed to scan §e${species.name}§f. §7Check console for errors."),
                false
            )
            return
        }

        RegionConditionSelectorGui.open(player, regionId, filteredConditions, target = target)
    }

    private fun refresh(player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        CustomGui.refreshGui(player, buildLayout(player, regionId, target))
    }

    private fun buildLayout(player: ServerPlayer, regionId: String, target: RestrictionTarget): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        val page = playerPages[player] ?: 0
        val availableSpecies = speciesFor(player)

        val start = page * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, availableSpecies.size)
        for (i in start until end) {
            layout[i - start] = speciesItem(availableSpecies[i])
        }

        if (page > 0)                            layout[Slots.PREV]     = navBtn("Previous Page", Textures.PREV)
        if ((page + 1) * PAGE_SIZE < availableSpecies.size) layout[Slots.NEXT] = navBtn("Next Page", Textures.NEXT)
        layout[Slots.SORT] = sortBtn(player)
        layout[Slots.BACK]     = navBtn("Back", Textures.BACK)
        layout[Slots.EXCLUDED] = excludedBtn(regionId, target)

        return layout
    }

    fun applySearch(player: ServerPlayer, term: String, regionId: String, target: RestrictionTarget) {
        playerSearchTerms[player] = term.trim()
        playerSortMethods[player] = SortMethod.SEARCH
        open(player, regionId, page = 0, target = target)
    }

    private fun speciesFor(player: ServerPlayer): List<Species> {
        val term = playerSearchTerms.getOrDefault(player, "")
        return when (playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)) {
            SortMethod.ALPHABETICAL -> allSpecies
            SortMethod.TYPE -> allSpecies.sortedBy { it.primaryType.name }
            SortMethod.SEARCH -> if (term.isBlank()) allSpecies
                else allSpecies.filter { it.name.contains(term, ignoreCase = true) }
        }
    }

    private fun sortBtn(player: ServerPlayer): ItemStack {
        val sort = playerSortMethods.getOrDefault(player, SortMethod.ALPHABETICAL)
        val term = playerSearchTerms.getOrDefault(player, "")
        val title = if (sort == SortMethod.SEARCH && term.isNotBlank())
            "Searching: ${if (term.length > 12) term.take(9) + "..." else term}"
        else "Sort: ${sort.name.lowercase().replaceFirstChar { it.uppercase() }}"
        return CustomGui.createPlayerHeadButton(
            "ConditionSort", Component.literal(title).withStyle(ChatFormatting.AQUA),
            listOf(
                Component.literal("§eLeft-click §7to cycle sort"),
                Component.literal("§eRight-click §7to search by name")
            ),
            Textures.SORT
        )
    }



    private fun speciesItem(species: Species): ItemStack {
        val item = try {
            val pokemon = PokemonProperties.parse(species.name.lowercase()).create()
            PokemonItem.from(pokemon, tint = Vector4f(1f, 1f, 1f, 1f))
        } catch (e: Exception) {
            RegionsConfig.debugError(logger, "Failed to build species item for ${species.name}", e)
            ItemStack(Items.BARRIER)
        }

        item.setCustomName(Component.literal(species.name).withStyle(ChatFormatting.GOLD))
        CustomGui.setItemLore(item, listOf(
            "§8${species.resourceIdentifier}",
            "",
            "§eClick to Scan:",
            "§7Finds every excludable tag",
            "§7for this Pokémon and opens",
            "§7the condition selector menu."
        ))
        return item
    }


    private fun excludedBtn(regionId: String, target: RestrictionTarget): ItemStack {
        val count = RegionsConfig.getRestriction(regionId, target)?.exclusionConditions?.size ?: 0
        val item = ItemStack(Items.BARRIER)
        item.setCustomName(Component.literal("Excluded Conditions").withStyle(ChatFormatting.RED))
        CustomGui.setItemLore(item, listOf(
            "§7Currently §f$count §7condition(s) excluded.",
            "",
            "§eClick §7to view & remove"
        ))
        return item
    }

    private fun navBtn(label: String, texture: String) = CustomGui.createPlayerHeadButton(
        label.replace(" ", ""), Component.literal(label).withStyle(ChatFormatting.GREEN), emptyList(), texture
    )

    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
