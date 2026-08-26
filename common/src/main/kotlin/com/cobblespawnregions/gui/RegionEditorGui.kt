package com.cobblespawnregions.gui

import com.cobblespawnregions.utils.RegionData
import com.cobblespawnregions.utils.RegionsConfig
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ClickAction
import net.minecraft.ChatFormatting





object RegionEditorGui {

    private object Slots {
        const val SUMMARY = 4

        const val PRIORITY = 22

        const val NATURAL = 30
        const val CUSTOM = 32

        const val BACK = 49
    }

    private object Limits {
        const val MIN_PRIORITY = -1_000
        const val MAX_PRIORITY = 1_000
    }

    private object Textures {
        const val REGION = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjlhMjhiYTNiYTc5YmUxOTU0NzEwZDRkYjJhM2ZkMjI3NzNmNjE5ZjE4ZmVjZjU5ODIzNTNmYjdhYzE4MzkzYSJ9fX0="
        const val NATURAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTdiMjE4OTMwMGYzMzliYTA1MGUwMWFlMmE1NDBiN2U4OWVmODk2YTU1Yzc5MTZkY2M5ZTU4NTFhZjg2NDExZSJ9fX0="
        const val CUSTOM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGQ4YjUxZGM5NTljMzNjMjUxNWJhZDY1ODk5N2Y2Y2VlOWY4NmRmMGU3ODdiNmM2ZjhkNTA3MDY0N2JkYyJ9fX0="
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
    }

    fun open(player: ServerPlayer, regionId: String) {
        val region = RegionsConfig.getRegion(regionId) ?: run {
            player.displayClientMessage(Component.literal("§c[CSR] Region '$regionId' not found."), false)
            return
        }
        CustomGui.openGui(
            player,
            "Region - ${region.regionName}",
            buildLayout(region),
            { ctx -> handleClick(ctx, player, region.regionId) },
            {}
        )
    }

    private fun handleClick(ctx: InteractionContext, player: ServerPlayer, regionId: String) {
        when (ctx.slotIndex) {
            Slots.PRIORITY -> {
                val delta = if (ctx.clickType == ClickAction.SECONDARY) -1 else 1
                adjustPriority(player, regionId, delta)
            }

            Slots.NATURAL -> RegionNaturalAndRidingGui.open(player, regionId)
            Slots.CUSTOM -> RegionUnnaturalSpawnGui.open(player, regionId)

            Slots.BACK -> RegionListGui.open(player)
        }
    }

    private fun buildLayout(region: RegionData): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        for (i in 0..8) layout[i] = glass()

        layout[Slots.SUMMARY] = summaryItem(region)

        layout[Slots.PRIORITY] = priorityItem(region)

        layout[Slots.NATURAL] = naturalSettingsItem(region)
        layout[Slots.CUSTOM] = customSpawnItem(region)

        layout[Slots.BACK] = backBtn()
        return layout
    }

    private fun refresh(player: ServerPlayer, regionId: String) {
        val region = RegionsConfig.getRegion(regionId) ?: return
        player.refreshGuiSlots(
            Slots.SUMMARY to summaryItem(region),
            Slots.PRIORITY to priorityItem(region)
        )
    }

    private fun adjustPriority(player: ServerPlayer, regionId: String, delta: Int) {
        RegionsConfig.updateRegion(regionId) {
            it.priority = (it.priority + delta).coerceIn(Limits.MIN_PRIORITY, Limits.MAX_PRIORITY)
        }
        refresh(player, regionId)
    }

    private fun summaryItem(region: RegionData) = CustomGui.createPlayerHeadButton(
        "region_${region.regionId}",
        Component.literal(region.regionName).withStyle(ChatFormatting.YELLOW),
        listOf(
            Component.literal("§8${region.regionId}"),
            Component.literal("§7Mode: §f${region.mode}"),
            Component.literal("§7Dimension: §f${region.dimension}"),
            Component.literal(boundsLine(region)),
            Component.literal("§7Priority: §f${region.priority}"),
            Component.literal(""),
            Component.literal("§8Higher priority controls overlapping positions.")
        ),
        Textures.REGION
    )

    private fun priorityItem(region: RegionData): ItemStack {
        val overlaps = RegionsConfig.regions.values
            .filter { it.regionId != region.regionId && it.dimension == region.dimension && regionsOverlap(region, it) }
            .sortedWith(compareByDescending<RegionData> { it.priority }.thenBy { it.regionId })

        return ItemStack(Items.COMPARATOR).apply {
            setCustomName(Component.literal("Priority: ${region.priority}").withStyle(ChatFormatting.GOLD))
            CustomGui.setItemLore(this, buildList {
                add("§7Higher priority controls overlapping areas.")
                add("§8Tie-breaker: smaller region, then region id.")
                add("")
                add("§7Left-click: §a+1")
                add("§7Right-click: §c-1")
                add("")
                if (overlaps.isEmpty()) {
                    add("§7Overlaps: §fnone")
                } else {
                    add("§7Overlaps:")
                    overlaps.take(5).forEach {
                        add("§8- §f${it.regionName} §7priority §f${it.priority}")
                    }
                    if (overlaps.size > 5) add("§8...and ${overlaps.size - 5} more")
                }
            })
        }
    }

    private fun naturalSettingsItem(region: RegionData) = CustomGui.createPlayerHeadButton(
        "NaturalSettings",
        Component.literal("Natural Spawns and Riding").withStyle(ChatFormatting.GREEN),
        listOf(
            Component.literal("§7Controls wild/natural Pokemon where"),
            Component.literal("§7this region wins priority."),
            Component.literal(""),
            Component.literal("§7Disable All: ${flag(region.spawnRestrictions.disableAll)}"),
            Component.literal("§7Blocked Species: §f${region.spawnRestrictions.disallowedSpecies.size}"),
            Component.literal("§7Labels: §f${region.spawnRestrictions.disallowedLabels.size}"),
            Component.literal("§7Conditions: §f${region.spawnRestrictions.exclusionConditions.size}"),
            Component.literal("§7Riding Disabled: ${flag(region.ridingRestrictions.disableAll)}"),
            Component.literal("§7Riding Species: §f${region.ridingRestrictions.disallowedSpecies.size}"),
            Component.literal("§7Riding Conditions: §f${region.ridingRestrictions.exclusionConditions.size}"),
            Component.literal(""),
            Component.literal("§eClick §7to configure")
        ),
        Textures.NATURAL
    )

    private fun customSpawnItem(region: RegionData) = CustomGui.createPlayerHeadButton(
        "CustomSpawns",
        Component.literal("Custom Spawns").withStyle(ChatFormatting.LIGHT_PURPLE),
        listOf(
            Component.literal("§7Pokemon this region spawns itself"),
            Component.literal("§7where it wins priority."),
            Component.literal(""),
            Component.literal("§7Configured Pokemon: §f${region.selectedPokemon.size}"),
            Component.literal("§7Timer: §f${region.spawnTimerTicks} ticks §8(${region.spawnTimerTicks / 20.0}s)"),
            Component.literal("§7Max Alive: §f${region.maxTotalSpawns} §8(0 = unlimited)"),
            Component.literal(""),
            Component.literal("§eClick §7to configure")
        ),
        Textures.CUSTOM
    )


    private fun backBtn() = CustomGui.createPlayerHeadButton(
        "Back",
        Component.literal("Back").withStyle(ChatFormatting.RED),
        listOf(Component.literal("§7Return to region list")),
        Textures.BACK
    )

    private fun regionsOverlap(a: RegionData, b: RegionData): Boolean {
        val aMinX = minOf(a.pos1.x, a.pos2.x); val aMaxX = maxOf(a.pos1.x, a.pos2.x)
        val aMinY = minOf(a.pos1.y, a.pos2.y); val aMaxY = maxOf(a.pos1.y, a.pos2.y)
        val aMinZ = minOf(a.pos1.z, a.pos2.z); val aMaxZ = maxOf(a.pos1.z, a.pos2.z)
        val bMinX = minOf(b.pos1.x, b.pos2.x); val bMaxX = maxOf(b.pos1.x, b.pos2.x)
        val bMinY = minOf(b.pos1.y, b.pos2.y); val bMaxY = maxOf(b.pos1.y, b.pos2.y)
        val bMinZ = minOf(b.pos1.z, b.pos2.z); val bMaxZ = maxOf(b.pos1.z, b.pos2.z)
        return aMinX <= bMaxX && aMaxX >= bMinX &&
                aMinY <= bMaxY && aMaxY >= bMinY &&
                aMinZ <= bMaxZ && aMaxZ >= bMinZ
    }

    private fun flag(b: Boolean) = if (b) "§atrue" else "§cfalse"
    private fun boundsLine(region: RegionData): String =
        "§7Bounds: §f(${region.pos1.x}, ${region.pos1.y}, ${region.pos1.z}) -> " +
                "(${region.pos2.x}, ${region.pos2.y}, ${region.pos2.z})"
    private fun glass() = ItemStack(Items.CYAN_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
