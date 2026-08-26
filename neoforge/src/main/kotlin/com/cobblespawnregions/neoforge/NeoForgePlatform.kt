package com.cobblespawnregions.neoforge

import com.cobblespawnregions.platform.Platform
import com.mojang.math.Transformation
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.GoalSelector
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import java.util.function.Consumer

/**
 * NeoForge implementation of [Platform]. Registered via
 * `META-INF/services/com.cobblespawnregions.platform.Platform`.
 *
 * Must keep a public no-arg constructor for [java.util.ServiceLoader].
 */
class NeoForgePlatform : Platform {

    private val bus get() = NeoForge.EVENT_BUS

    // Fabric's SERVER_STARTING fires before worlds load, which matches
    // ServerAboutToStartEvent -- NOT the later ServerStartingEvent.
    override fun onServerStarting(handler: (MinecraftServer) -> Unit) {
        bus.addListener(Consumer<ServerAboutToStartEvent> { handler(it.server) })
    }

    override fun onServerStarted(handler: (MinecraftServer) -> Unit) {
        bus.addListener(Consumer<ServerStartedEvent> { handler(it.server) })
    }

    override fun onServerStopping(handler: (MinecraftServer) -> Unit) {
        bus.addListener(Consumer<ServerStoppingEvent> { handler(it.server) })
    }

    // ChunkEvent hands back a LevelAccessor and a ChunkAccess, so both need
    // narrowing before common/ sees them. Fabric's equivalents are already
    // server-side and already LevelChunk.
    override fun onChunkLoad(handler: (ServerLevel, LevelChunk) -> Unit) {
        bus.addListener(Consumer<ChunkEvent.Load> { event ->
            val level = event.level as? ServerLevel ?: return@Consumer
            val chunk = event.chunk as? LevelChunk ?: return@Consumer
            handler(level, chunk)
        })
    }

    override fun onChunkUnload(handler: (ServerLevel, LevelChunk) -> Unit) {
        bus.addListener(Consumer<ChunkEvent.Unload> { event ->
            val level = event.level as? ServerLevel ?: return@Consumer
            val chunk = event.chunk as? LevelChunk ?: return@Consumer
            handler(level, chunk)
        })
    }

    // EntityJoinLevelEvent fires on BOTH sides, unlike Fabric's
    // ServerEntityEvents which are server-only by construction.
    override fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit) {
        bus.addListener(Consumer<EntityJoinLevelEvent> { event ->
            val level = event.level as? ServerLevel ?: return@Consumer
            handler(event.entity, level)
        })
    }

    override fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit) {
        bus.addListener(Consumer<EntityLeaveLevelEvent> { event ->
            val level = event.level as? ServerLevel ?: return@Consumer
            handler(event.entity, level)
        })
    }

    override fun onServerTick(handler: (MinecraftServer) -> Unit) {
        bus.addListener(Consumer<ServerTickEvent.Post> { handler(it.server) })
    }

    override fun onPlayerDisconnect(handler: (ServerPlayer) -> Unit) {
        bus.addListener(Consumer<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let(handler)
        })
    }

    /**
     * Cancellation is shaped differently here. Fabric returns an
     * InteractionResult; NeoForge cancels the event. `true` from the handler
     * means consume, so the block is not broken -- which matters because the
     * handler also returns true on permission *denial*.
     *
     * LeftClickBlock additionally fires for continued digging, so this guards
     * on Action.START to match Fabric's one-event-per-click behaviour.
     */
    override fun onLeftClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    ) {
        bus.addListener(Consumer<PlayerInteractEvent.LeftClickBlock> { event ->
            if (event.action != PlayerInteractEvent.LeftClickBlock.Action.START) return@Consumer
            val player = event.entity as? ServerPlayer ?: return@Consumer
            val level = event.level as? ServerLevel ?: return@Consumer
            if (handler(player, level, event.hand, event.pos)) {
                event.isCanceled = true
            }
        })
    }

    override fun onRightClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    ) {
        bus.addListener(Consumer<PlayerInteractEvent.RightClickBlock> { event ->
            val player = event.entity as? ServerPlayer ?: return@Consumer
            val level = event.level as? ServerLevel ?: return@Consumer
            if (handler(player, level, event.hand, event.pos)) {
                event.isCanceled = true
            }
        })
    }

    // --- capability accessors ---------------------------------------------
    // Reachable only because META-INF/accesstransformer.cfg widens them; they
    // are private/protected in vanilla. On Fabric the same members are opened
    // by Fabric API's transitive access wideners.

    override fun goalSelector(mob: Mob): GoalSelector = mob.goalSelector

    override fun setDisplayBlockState(display: Display.BlockDisplay, state: BlockState) {
        display.setBlockState(state)
    }

    override fun setDisplayTransformation(
        display: Display,
        transformation: Transformation,
        interpolationDuration: Int,
        interpolationDelay: Int
    ) {
        display.setTransformation(transformation)
        display.setTransformationInterpolationDuration(interpolationDuration)
        display.setTransformationInterpolationDelay(interpolationDelay)
    }
}
