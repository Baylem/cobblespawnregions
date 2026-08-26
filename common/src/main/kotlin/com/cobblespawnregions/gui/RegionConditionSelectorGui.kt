package com.cobblespawnregions.gui

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
import java.util.concurrent.ConcurrentHashMap

object RegionConditionSelectorGui {

    private const val PAGE_SIZE = 45
    private val playerPages = ConcurrentHashMap<ServerPlayer, Int>()
    private val playerConditions = ConcurrentHashMap<ServerPlayer, List<String>>()

    private object Slots {
        const val PREV = 45
        const val BACK = 49
        const val NEXT = 53
    }

    private object Textures {
        const val PREV   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTMzYWQ1YzIyZGIxNjQzNWRhYWQ2MTU5MGFiYTUxZDkzNzkxNDJkZDU1NmQ2YzQyMmE3MTEwY2EzYWJlYTUwIn19fQ=="
        const val NEXT   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGU0MDNjYzdiYmFjNzM2NzBiZDU0M2Y2YjA5NTViYWU3YjhlOTEyM2Q4M2JkNzYwZjYyMDRjNWFmZDhiZTdlMSJ9fX0="
        const val BACK   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val COND   = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDcyNjkwOTYxMjg5N2I5Zjk3In19fQ=="
    }

    fun open(player: ServerPlayer, regionId: String, conditions: List<String>, page: Int = 0, target: RestrictionTarget = RestrictionTarget.NATURAL_SPAWNS) {
        playerPages[player] = page
        playerConditions[player] = conditions

        val label = RegionsConfig.scopeLabel(regionId)
        CustomGui.openGui(
            player,
            "Exclusion Conditions — $label",
            buildLayout(player, regionId, target),
            { ctx -> handleClick(ctx, player, regionId, target) },
            {}
        )
    }

    private fun handleClick(ctx: InteractionContext, player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        val conditions = playerConditions[player] ?: return
        val page = playerPages[player] ?: 0

        when (ctx.slotIndex) {
            Slots.PREV -> if (page > 0) { playerPages[player] = page - 1; refresh(player, regionId, target) }
            Slots.NEXT -> if ((page + 1) * PAGE_SIZE < conditions.size) { playerPages[player] = page + 1; refresh(player, regionId, target) }
            Slots.BACK -> RegionConditionScannerGui.open(player, regionId, 0, target)
            in 0 until PAGE_SIZE -> {
                val idx = page * PAGE_SIZE + ctx.slotIndex
                if (idx < conditions.size) toggleCondition(player, regionId, conditions[idx], target)
            }
        }
    }

    private fun toggleCondition(player: ServerPlayer, regionId: String, condition: String, target: RestrictionTarget) {
        val restr = RegionsConfig.getRestriction(regionId, target) ?: return


        if (restr.exclusionConditions.contains(condition)) {
            restr.exclusionConditions.remove(condition)
        } else {
            restr.exclusionConditions.add(condition)
        }

        RegionsConfig.saveRegion(regionId)
        refresh(player, regionId, target)
    }

    private fun refresh(player: ServerPlayer, regionId: String, target: RestrictionTarget) {
        CustomGui.refreshGui(player, buildLayout(player, regionId, target))
    }

    private fun buildLayout(player: ServerPlayer, regionId: String, target: RestrictionTarget): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        val conditions = playerConditions[player] ?: return layout
        val page = playerPages[player] ?: 0


        val blocked = RegionsConfig.getRestriction(regionId, target)?.exclusionConditions ?: emptySet<String>()

        val start = page * PAGE_SIZE
        val end = minOf(start + PAGE_SIZE, conditions.size)

        for (i in start until end) {
            val cond = conditions[i]
            val isBlocked = cond in blocked
            layout[i - start] = conditionItem(cond, isBlocked)
        }

        if (page > 0) layout[Slots.PREV] = navBtn("Previous Page", Textures.PREV)
        if ((page + 1) * PAGE_SIZE < conditions.size) layout[Slots.NEXT] = navBtn("Next Page", Textures.NEXT)
        layout[Slots.BACK] = navBtn("Back to Species List", Textures.BACK)

        return layout
    }

    private fun conditionItem(condition: String, isBlocked: Boolean): ItemStack {
        val item = CustomGui.createPlayerHeadButton(
            "cond_$condition",
            Component.literal(condition).withStyle(if (isBlocked) ChatFormatting.RED else ChatFormatting.GREEN),
            listOf(
                Component.literal(""),
                Component.literal(if (isBlocked) "§c§lEXCLUDED" else "§a§lALLOWED"),
                Component.literal("§7Click to toggle this condition in"),
                Component.literal("§7exclusionConditions.")
            ),
            AlphabetHeadTextures.forFirstLetter(condition, Textures.COND)
        )
        if (isBlocked) CustomGui.addEnchantmentGlint(item)
        return item
    }

    private fun navBtn(label: String, texture: String) = CustomGui.createPlayerHeadButton(
        label.replace(" ", ""), Component.literal(label).withStyle(ChatFormatting.AQUA), emptyList(), texture
    )

    private fun filler() = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
