package com.cobblespawnregions.gui

import com.cobblespawnregions.utils.RegionRestrictionConfig
import com.cobblespawnregions.utils.RestrictionTarget
import com.cobblespawnregions.utils.RegionsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting




object RegionNaturalSpawnGui {

    private object Slots {
        const val DISABLE_ALL = 20
        const val EXCLUDE_OWNED = 24
        const val SPECIES = 28
        const val LABELS = 31
        const val CONDITIONS = 34
        const val BACK = 49
    }

    private object Textures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun open(player: ServerPlayer, regionId: String, target: RestrictionTarget = RestrictionTarget.NATURAL_SPAWNS) {
        val region = RegionsConfig.getRegion(regionId) ?: run {
            player.displayClientMessage(Component.literal("§c[CSR] Region not found."), false)
            return
        }
        CustomGui.openGui(
            player,
            "${if (target == RestrictionTarget.RIDING) "Riding" else "Natural"} - ${region.regionName}",
            buildLayout(regionId, target),
            { ctx -> handleClick(ctx, player, regionId, target) },
            {}
        )
    }

    private fun handleClick(ctx: InteractionContext, player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        when (ctx.slotIndex) {
            Slots.DISABLE_ALL -> toggleRestriction(player, regionId, target) { it.disableAll = !it.disableAll }
            Slots.EXCLUDE_OWNED -> toggleRestriction(player, regionId, target) { it.excludeOwnedPokemon = !it.excludeOwnedPokemon }
            Slots.SPECIES -> RegionSpeciesBlocklistGui.open(player, regionId, target = target)
            Slots.LABELS -> RegionLabelSelectorGui.open(player, regionId, target = target)
            Slots.CONDITIONS -> RegionConditionScannerGui.open(player, regionId, target = target)
            Slots.BACK -> RegionNaturalAndRidingGui.open(player, regionId)
        }
    }

    private fun buildLayout(regionId: String, target: RestrictionTarget): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        val restr = RegionsConfig.getRestriction(regionId, target) ?: return layout

        for (i in 0..8) layout[i] = glass()

        layout[Slots.DISABLE_ALL] = toggleItem(if (target == RestrictionTarget.RIDING) "Disable Riding" else "Disable Natural Spawns", restr.disableAll, listOf(
            if (target == RestrictionTarget.RIDING) "§7Blocks riding matching Pokemon" else "§7Blocks every natural Pokemon spawn",
            "§7where this region controls the position.",
            "",
            "§eClick §7to toggle"
        ))
        layout[Slots.EXCLUDE_OWNED] = toggleItem("Exclude Owned Pokemon", restr.excludeOwnedPokemon, listOf(
            "§7When ON, player-owned Pokemon",
            "§7bypass these ${if (target == RestrictionTarget.RIDING) "riding" else "spawn"} restrictions.",
            "",
            "§eClick §7to toggle"
        ))
        layout[Slots.SPECIES] = listItem(Items.BOOK, "Blocked Species", ChatFormatting.AQUA, restr.disallowedSpecies.size)
        layout[Slots.LABELS] = listItem(Items.NAME_TAG, "Excluded Labels", ChatFormatting.LIGHT_PURPLE, restr.disallowedLabels.size)
        layout[Slots.CONDITIONS] = conditionItem(restr.exclusionConditions.size)
        layout[Slots.BACK] = backBtn()
        return layout
    }

    private fun refresh(player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        CustomGui.refreshGui(player, buildLayout(regionId, target))
    }

    private fun toggleRestriction(
        player: ServerPlayer,
        regionId: String,
        target: RestrictionTarget,
        update: (RegionRestrictionConfig) -> Unit
    ) {
        val restr = RegionsConfig.getRestriction(regionId, target) ?: return
        update(restr)
        RegionsConfig.saveRegion(regionId)
        refresh(player, regionId, target)
    }

    private fun toggleItem(label: String, enabled: Boolean, lore: List<String>): ItemStack {
        val item = ItemStack(if (enabled) Items.LIME_CONCRETE else Items.RED_CONCRETE)
        item.setCustomName(
            Component.literal("$label: ").withStyle(ChatFormatting.WHITE)
                .append(
                    if (enabled) Component.literal("ON").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    else Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                )
        )
        CustomGui.setItemLore(item, lore)
        return item
    }

    private fun listItem(item: net.minecraft.world.item.Item, label: String, color: ChatFormatting, count: Int): ItemStack {
        return ItemStack(item).apply {
            setCustomName(Component.literal(label).withStyle(color))
            CustomGui.setItemLore(this, listOf(
                "§7Currently blocking §f$count§7.",
                "",
                "§eClick §7to manage"
            ))
        }
    }

    private fun conditionItem(count: Int): ItemStack {
        return ItemStack(Items.SPYGLASS).apply {
            setCustomName(Component.literal("Excluded Conditions").withStyle(ChatFormatting.YELLOW))
            CustomGui.setItemLore(this, listOf(
                "§7Currently blocking §f$count §7condition(s).",
                "§8Experimental property scanner.",
                "",
                "§eClick §7to scan or manage"
            ))
        }
    }

    private fun backBtn() = CustomGui.createPlayerHeadButton(
        "Back",
        Component.literal("Back").withStyle(ChatFormatting.RED),
        listOf(Component.literal("§7Return to region settings")),
        Textures.BACK
    )

    private fun glass() = ItemStack(Items.CYAN_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
