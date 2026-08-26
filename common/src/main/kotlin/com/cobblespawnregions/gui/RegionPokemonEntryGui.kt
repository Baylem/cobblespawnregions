package com.cobblespawnregions.gui

import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblespawnregions.gui.pokemonsettings.RegionPokemonSettingsGui
import com.cobblespawnregions.utils.RegionEntityTracker
import com.cobblespawnregions.utils.RegionWanderingGoalManager
import com.cobblespawnregions.utils.RegionsConfig
import com.cobblespawnregions.utils.REGION_POKEMON_DEFAULTS_KEY
import com.everlastingutils.gui.CustomGui
import com.everlastingutils.gui.InteractionContext
import com.everlastingutils.gui.setCustomName
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ClickAction
import net.minecraft.ChatFormatting
import org.joml.Vector4f
import org.slf4j.LoggerFactory

object RegionPokemonEntryGui {

    private val logger = LoggerFactory.getLogger("RegionPokemonEntryGui")

    private object Slots {
        const val MON_DISPLAY = 4

        const val SPAWN_LEVEL = 10
        const val IVS = 11
        const val EVS = 12
        const val SIZE = 13
        const val MOVES = 14
        const val CAPTURE = 15
        const val OTHER = 16

        const val BLOCKS = 21
        const val MAX_COUNT = 22
        const val WANDER_TOGGLE = 23

        const val WANDER_TARGET = 30
        const val WANDER_SPEED = 31
        const val WANDER_DELAY = 32
        const val CLONE_DEFAULTS = 40

        const val BACK = 49
    }

    private object Textures {
        const val BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzI0MzE5MTFmNDE3OGI0ZDJiNDEzYWE3ZjVjNzhhZTQ0NDdmZTkyNDY5NDNjMzFkZjMxMTYzYzBlMDQzZTBkNiJ9fX0="
        const val SPAWN_BLOCKS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNlOTlhNmZkMDQ2NGUwNjhjZDY5ZjNmZGRkMDNiYmFiOTA5YWNlNGY5YzNjNmFmYTFmOTQ3ZWNmODVjMjRmYiJ9fX0="
        const val RETURN_TARGET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTJiOWFiZmM4ODE4MzUzNWE1ZGUwNjcxNTY3ZGJhMGY3ZmM4YzI3MzM4OGVmN2FjMjhiNmRjMzBiZDUxZmI3In19fQ=="
        const val MAX_COUNT_ICON = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWZhMWM2YzdlYWQ3NWEwNDU4NTM5NWY2MzEzNWRjOTZmYTA3OGZiOTIwNDg0Njk5ZWY4ZTU2NGUxNDJkNjRjYiJ9fX0="
        const val RETURN_SPEED = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGExZDU1YjNmOTg5NDEwYTM0NzUyNjUwZTI0OGM5YjZjMTc4M2E3ZWMyYWEzZmQ3Nzg3YmRjNGQwZTYzN2QzOSJ9fX0="
        const val STAY_IN_REGION = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ=="
        const val DELAY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2NjNmUxMGRiNDBiNGU4MzM0MTdkZmQ1NzZiOWE4MGZhMzY2NjI1MTFhMmY2Y2U0Y2IwY2YyZWY3NmI3N2ZlMyJ9fX0="
        const val IV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDg4M2Q2NTZlNDljMzhjNmI1Mzc4NTcyZjMxYzYzYzRjN2E1ZGQ0Mzc1YjZlY2JjYTQzZjU5NzFjMmNjNGZmIn19fQ=="
        const val EV = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM0NTI5NjRmMWNiYjg5MTQ2Njg0YWE1NTYzOTBhOThjZjM0MmNhOTdjZWZhNmE5Mjk0YTVkMzZlZGQ5MzBmOSJ9fX0="
        const val SPAWN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjdkNmJlMWRjYTUzNTJhNTY5M2UyOWVhMzVkODA2YjJhMjdjNGE5N2I2NGVlYmJmNjMyYzk5OGQ1OTQ4ZjFjNCJ9fX0="
        const val SIZE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmI5MmFiZWI0NGMzNGI5OThhMDE4ZWM1YjYwMjJlOGZjMTU4ZWU4YjEzNDA0YzBmZTZkZDA5MTdmZWQ4NDRlYiJ9fX0="
        const val CAPTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY0YzQ0ODZmOTIzNmY5YTFmYjRiMjFiZjgyM2M1NTZkNmUxNWJmNjg4Yzk2ZDZlZjBkMTc1NTNkYjUwNWIifX19"
        const val OTHER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEwMWQxNTZiMTcyMTVjZWYzMzZhZjRjNDRlNmNjOGNjYjI4NWZiMDViYzNmZWI2MmQzMzdmZWIxZjA5MjkwYSJ9fX0="
        const val MOVES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzJlYmJkYjE4ZDc0NzI4MWI1NDYyZjg1N2VlOTg0Njc1YTM5ZDVhMDI3NDQ0NmEyMmY2NjI2NGE1M2QyYjAzNCJ9fX0="
    }

    private const val MIN_COUNT = 0
    private const val MAX_COUNT = 100
    private const val MIN_DELAY = 1
    private const val MAX_DELAY = 200
    private const val MIN_SPEED = 0.1
    private const val MAX_SPEED = 4.0

    fun open(
        player: ServerPlayer,
        regionId: String,
        pokemonName: String,
        formName: String?,
        aspects: Set<String>
    ) {
        if (RegionsConfig.getPokemonFromRegion(regionId, pokemonName, formName, aspects) == null) {
            player.displayClientMessage(Component.literal("§c[CSR] §fEntry not found."), false)
            RegionPokemonSelectionGui.open(player, regionId)
            return
        }

        val isDefaults = pokemonName == REGION_POKEMON_DEFAULTS_KEY
        CustomGui.openGui(
            player,
            if (isDefaults) "Default Pokémon Settings" else "${buildDisplayName(pokemonName, formName, aspects)} Settings",
            buildLayout(regionId, pokemonName, formName, aspects),
            { ctx -> handleClick(ctx, player, regionId, pokemonName, formName, aspects) },
            {}
        )
    }

    private fun handleClick(
        ctx: InteractionContext,
        player: ServerPlayer,
        regionId: String,
        pokemonName: String,
        formName: String?,
        aspects: Set<String>
    ) {
        when (ctx.slotIndex) {
            Slots.SPAWN_LEVEL -> RegionPokemonSettingsGui.openSpawnLevel(player, regionId, pokemonName, formName, aspects)
            Slots.IVS -> RegionPokemonSettingsGui.openIvs(player, regionId, pokemonName, formName, aspects)
            Slots.EVS -> RegionPokemonSettingsGui.openEvs(player, regionId, pokemonName, formName, aspects)
            Slots.SIZE -> RegionPokemonSettingsGui.openSize(player, regionId, pokemonName, formName, aspects)
            Slots.MOVES -> RegionPokemonSettingsGui.openMoves(player, regionId, pokemonName, formName, aspects)
            Slots.CAPTURE -> RegionPokemonSettingsGui.openCapture(player, regionId, pokemonName, formName, aspects)
            Slots.OTHER -> RegionPokemonSettingsGui.openOther(player, regionId, pokemonName, formName, aspects)
            Slots.BLOCKS -> RegionSpawnBlocksGui.open(player, regionId, pokemonName, formName, aspects)

            Slots.MAX_COUNT -> {
                val delta = if (ctx.clickType == ClickAction.SECONDARY) -1 else 1
                adjustMaxSpawnCount(player, regionId, pokemonName, formName, aspects, delta)
            }

            Slots.WANDER_TOGGLE -> updateWandering(player, regionId, pokemonName, formName, aspects) {
                it.enabled = !it.enabled
            }
            Slots.WANDER_TARGET -> updateWandering(player, regionId, pokemonName, formName, aspects) {
                it.returnTarget = when (it.returnTarget.uppercase()) {
                    "RANDOM" -> "CENTER"
                    "CENTER" -> "CLOSEST"
                    else -> "RANDOM"
                }
            }
            Slots.WANDER_SPEED -> updateWandering(player, regionId, pokemonName, formName, aspects) {
                val delta = if (ctx.clickType == ClickAction.SECONDARY) -0.1 else 0.1
                it.speed = roundOneDecimal((it.speed + delta).coerceIn(MIN_SPEED, MAX_SPEED))
            }
            Slots.WANDER_DELAY -> updateWandering(player, regionId, pokemonName, formName, aspects) {
                val delta = if (ctx.clickType == ClickAction.SECONDARY) -1 else 1
                it.tickDelay = (it.tickDelay + delta).coerceIn(MIN_DELAY, MAX_DELAY)
            }

            Slots.CLONE_DEFAULTS -> if (pokemonName == REGION_POKEMON_DEFAULTS_KEY) {
                RegionPokemonDefaultsCloneGui.open(player, regionId)
            }
            Slots.BACK -> RegionPokemonSelectionGui.open(player, regionId)
        }
    }

    private fun adjustMaxSpawnCount(
        player: ServerPlayer,
        regionId: String,
        pokemonName: String,
        formName: String?,
        aspects: Set<String>,
        delta: Int
    ) {
        RegionsConfig.updatePokemonInRegion(regionId, pokemonName, formName, aspects) { entry ->
            entry.maxSpawnCount = (entry.maxSpawnCount + delta).coerceIn(MIN_COUNT, MAX_COUNT)
        }
        refresh(player, regionId, pokemonName, formName, aspects)
    }

    private fun updateWandering(
        player: ServerPlayer,
        regionId: String,
        pokemonName: String,
        formName: String?,
        aspects: Set<String>,
        update: (com.cobblespawnregions.utils.RegionWanderingSettings) -> Unit
    ) {
        val entry = RegionsConfig.updatePokemonInRegion(regionId, pokemonName, formName, aspects) { entry ->
            update(entry.wanderingSettings)
        }
        if (pokemonName != REGION_POKEMON_DEFAULTS_KEY && entry?.wanderingSettings?.enabled == true) {
            RegionWanderingGoalManager.attachLoadedForEntry(player.server, regionId, RegionEntityTracker.entryKey(entry))
        }
        refresh(player, regionId, pokemonName, formName, aspects)
    }

    private fun refresh(player: ServerPlayer, regionId: String, pokemonName: String, formName: String?, aspects: Set<String>) {
        CustomGui.refreshGui(player, buildLayout(regionId, pokemonName, formName, aspects))
    }

    private fun buildLayout(regionId: String, pokemonName: String, formName: String?, aspects: Set<String>): List<ItemStack> {
        val layout = MutableList(54) { filler() }
        val entry = RegionsConfig.getPokemonFromRegion(regionId, pokemonName, formName, aspects)
        for (i in 0..8) layout[i] = purpleGlass()

        val isDefaults = pokemonName == REGION_POKEMON_DEFAULTS_KEY
        layout[Slots.MON_DISPLAY] = if (isDefaults) defaultsDisplayItem() else monDisplayItem(pokemonName, formName, aspects)
        layout[Slots.SPAWN_LEVEL] = menuButton("Spawn / Level", ChatFormatting.DARK_AQUA, listOf("Edit spawn chance, chance type, and levels."), Textures.SPAWN)
        layout[Slots.IVS] = menuButton("IVs", ChatFormatting.GREEN, listOf("Edit custom IV ranges."), Textures.IV)
        layout[Slots.EVS] = menuButton("EVs", ChatFormatting.BLUE, listOf("Edit EVs awarded when defeated."), Textures.EV)
        layout[Slots.SIZE] = menuButton("Size", ChatFormatting.GOLD, listOf("Edit min/max spawned scale."), Textures.SIZE)
        layout[Slots.MOVES] = menuButton("Moves", ChatFormatting.YELLOW, listOf("Edit initial move selection."), Textures.MOVES)
        layout[Slots.CAPTURE] = menuButton("Capture", ChatFormatting.AQUA, listOf("Edit catchability and allowed balls."), Textures.CAPTURE)
        layout[Slots.OTHER] = menuButton("Time / Weather", ChatFormatting.LIGHT_PURPLE, listOf("Edit time and weather spawn rules."), Textures.OTHER)

        layout[Slots.BLOCKS] = spawnBlocksBtn(entry)
        layout[Slots.MAX_COUNT] = maxSpawnCountBtn(entry?.maxSpawnCount ?: 0, pokemonName)
        layout[Slots.WANDER_TOGGLE] = wanderToggleBtn(entry)
        layout[Slots.WANDER_TARGET] = wanderTargetBtn(entry)
        layout[Slots.WANDER_SPEED] = wanderSpeedBtn(entry)
        layout[Slots.WANDER_DELAY] = wanderDelayBtn(entry)
        if (isDefaults) layout[Slots.CLONE_DEFAULTS] = cloneDefaultsBtn()
        layout[Slots.BACK] = backBtn()

        return layout
    }

    private fun menuButton(title: String, color: ChatFormatting, lore: List<String>, texture: String): ItemStack =
        CustomGui.createPlayerHeadButton(
            title.replace(" ", ""),
            Component.literal(title).withStyle(color),
            lore.map { Component.literal("§7$it") } + Component.literal("§eClick to edit"),
            texture
        )

    fun openDefaults(player: ServerPlayer, regionId: String) {
        open(player, regionId, REGION_POKEMON_DEFAULTS_KEY, null, emptySet())
    }

    private fun defaultsDisplayItem() = ItemStack(Items.WRITABLE_BOOK).apply {
        setCustomName(Component.literal("§6§lRegional Pokémon Defaults"))
        CustomGui.setItemLore(this, listOf(
            "§7These settings are copied once",
            "§7when a Pokémon is first selected.",
            "§7Existing Pokémon remain unchanged."
        ))
    }

    private fun cloneDefaultsBtn() = ItemStack(Items.CHEST).apply {
        setCustomName(Component.literal("Clone From Other Region").withStyle(ChatFormatting.YELLOW))
        CustomGui.setItemLore(this, listOf(
            "§7Replace this region's defaults",
            "§7with defaults from another region.",
            "",
            "§eClick §7to choose a region"
        ))
    }

    private fun monDisplayItem(pokemonName: String, formName: String?, aspects: Set<String>): ItemStack {
        return try {
            val pokemon = PokemonProperties.parse(buildPropsString(pokemonName, formName, aspects)).create()
            val item = PokemonItem.from(pokemon, tint = Vector4f(1f, 1f, 1f, 1f))
            item.setCustomName(Component.literal("§f§l${buildDisplayName(pokemonName, formName, aspects)}"))
            CustomGui.setItemLore(item, listOf(
                "§7Species: §f${pokemonName.replaceFirstChar(Char::titlecase)}",
                if (!formName.isNullOrEmpty() && !formName.equals("normal", ignoreCase = true)) "§7Form: §f$formName" else "",
                if (aspects.isNotEmpty()) "§7Aspects: §f${aspects.joinToString(", ") { it.replaceFirstChar(Char::titlecase) }}" else ""
            ).filter(String::isNotEmpty))
            item
        } catch (e: Exception) {
            RegionsConfig.debugError(logger, "Failed to build Pokemon display item for $pokemonName", e)
            filler()
        }
    }

    private fun maxSpawnCountBtn(count: Int, pokemonName: String): ItemStack =
        CustomGui.createPlayerHeadButton(
            "MaxSpawnCount",
            Component.literal("Max Spawn Count").withStyle(ChatFormatting.AQUA),
            listOf(
                Component.literal("§7Max live ${if (pokemonName == REGION_POKEMON_DEFAULTS_KEY) "Pokémon" else pokemonName.replaceFirstChar(Char::titlecase)} in this region."),
                Component.literal("§eCurrent: §f$count §8(0 = unlimited)"),
                Component.literal("§7Left-click: §a+1"),
                Component.literal("§7Right-click: §c-1")
            ),
            Textures.MAX_COUNT_ICON
        )

    private fun spawnBlocksBtn(entry: com.cobblespawnregions.utils.PokemonSpawnEntry?): ItemStack =
        CustomGui.createPlayerHeadButton(
            "SpawnBlocks",
            Component.literal("Spawn Blocks").withStyle(ChatFormatting.GREEN),
            listOf(
                Component.literal("§7Allowed floor blocks for this Pokemon."),
                Component.literal("§eCurrent: §f${entry?.spawnSettings?.allowedBlocks?.size ?: 0} §7block(s)"),
                Component.literal("§8(0 = any block)"),
                Component.literal("§eClick to edit")
            ),
            Textures.SPAWN_BLOCKS
        )

    private fun wanderToggleBtn(entry: com.cobblespawnregions.utils.PokemonSpawnEntry?): ItemStack =
        CustomGui.createPlayerHeadButton(
            "RegionWanderToggle",
            Component.literal("Stay In Region").withStyle(ChatFormatting.GOLD),
            listOf(
                Component.literal("§7Paths back if this Pokemon leaves its region."),
                Component.literal("§eCurrent: ${if (entry?.wanderingSettings?.enabled != false) "§aON" else "§cOFF"}"),
                Component.literal("§eClick to toggle")
            ),
            Textures.STAY_IN_REGION
        )

    private fun wanderTargetBtn(entry: com.cobblespawnregions.utils.PokemonSpawnEntry?): ItemStack =
        CustomGui.createPlayerHeadButton(
            "RegionWanderTarget",
            Component.literal("Return Target").withStyle(ChatFormatting.YELLOW),
            listOf(
                Component.literal("§7Where it paths when returning."),
                Component.literal("§eCurrent: §f${entry?.wanderingSettings?.returnTarget ?: "RANDOM"}"),
                Component.literal("§eClick to switch")
            ),
            Textures.RETURN_TARGET
        )

    private fun wanderSpeedBtn(entry: com.cobblespawnregions.utils.PokemonSpawnEntry?): ItemStack =
        CustomGui.createPlayerHeadButton(
            "RegionWanderSpeed",
            Component.literal("Return Speed").withStyle(ChatFormatting.AQUA),
            listOf(
                Component.literal("§eCurrent: §f${entry?.wanderingSettings?.speed ?: 1.0}"),
                Component.literal("§7Left-click: §a+0.1"),
                Component.literal("§7Right-click: §c-0.1")
            ),
            Textures.RETURN_SPEED
        )

    private fun wanderDelayBtn(entry: com.cobblespawnregions.utils.PokemonSpawnEntry?): ItemStack =
        CustomGui.createPlayerHeadButton(
            "RegionWanderDelay",
            Component.literal("Check Delay").withStyle(ChatFormatting.LIGHT_PURPLE),
            listOf(
                Component.literal("§eCurrent: §f${entry?.wanderingSettings?.tickDelay ?: 10} ticks"),
                Component.literal("§7Left-click: §a+1"),
                Component.literal("§7Right-click: §c-1")
            ),
            Textures.DELAY
        )

    private fun backBtn(): ItemStack =
        CustomGui.createPlayerHeadButton("Back", Component.literal("Back").withStyle(ChatFormatting.RED), listOf(Component.literal("§7Return to Pokemon list")), Textures.BACK)

    private fun buildDisplayName(pokemonName: String, formName: String?, aspects: Set<String>): String {
        val parts = mutableListOf<String>()
        if (!formName.isNullOrEmpty() && !formName.equals("normal", ignoreCase = true)) parts.add(formName)
        parts.addAll(aspects.map { it.replaceFirstChar(Char::titlecase) })
        return if (parts.isNotEmpty()) "${pokemonName.replaceFirstChar(Char::titlecase)} (${parts.joinToString(", ")})"
        else pokemonName.replaceFirstChar(Char::titlecase)
    }

    private fun buildPropsString(pokemonName: String, formName: String?, aspects: Set<String>): String =
        buildString {
            append(pokemonName.lowercase())
            if (!formName.isNullOrEmpty()
                && !formName.equals("normal", ignoreCase = true)
                && !formName.equals("default", ignoreCase = true)
            ) append(" form=${formName.lowercase()}")
            aspects.forEach { aspect ->
                if (aspect.contains("=")) append(" ${aspect.lowercase()}")
                else append(" aspect=${aspect.lowercase()}")
            }
        }

    private fun roundOneDecimal(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
    private fun purpleGlass(): ItemStack = ItemStack(Items.PURPLE_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
    private fun filler(): ItemStack = ItemStack(Items.GRAY_STAINED_GLASS_PANE).apply { setCustomName(Component.literal(" ")) }
}
