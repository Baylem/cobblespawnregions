package com.cobblespawnregions.fabric

import com.cobblespawnregions.mixin.MobEntityAccessor
import com.cobblespawnregions.platform.Platform
import com.mojang.math.Transformation
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.GoalSelector
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Fabric implementation of [Platform]. Registered via
 * `META-INF/services/com.cobblespawnregions.platform.Platform`.
 *
 * Must keep a public no-arg constructor for [java.util.ServiceLoader].
 */
class FabricPlatform : Platform {

    override fun onServerStarting(handler: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STARTING.register { server -> handler(server) }
    }

    override fun onServerStarted(handler: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STARTED.register { server -> handler(server) }
    }

    override fun onServerStopping(handler: (MinecraftServer) -> Unit) {
        ServerLifecycleEvents.SERVER_STOPPING.register { server -> handler(server) }
    }

    override fun onChunkLoad(handler: (ServerLevel, LevelChunk) -> Unit) {
        ServerChunkEvents.CHUNK_LOAD.register { level, chunk -> handler(level, chunk) }
    }

    override fun onChunkUnload(handler: (ServerLevel, LevelChunk) -> Unit) {
        ServerChunkEvents.CHUNK_UNLOAD.register { level, chunk -> handler(level, chunk) }
    }

    override fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_LOAD.register { entity, level -> handler(entity, level) }
    }

    override fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit) {
        ServerEntityEvents.ENTITY_UNLOAD.register { entity, level -> handler(entity, level) }
    }

    override fun onServerTick(handler: (MinecraftServer) -> Unit) {
        ServerTickEvents.END_SERVER_TICK.register { server -> handler(server) }
    }

    override fun onPlayerDisconnect(handler: (ServerPlayer) -> Unit) {
        ServerPlayConnectionEvents.DISCONNECT.register { networkHandler, _ ->
            handler(networkHandler.player)
        }
    }

    // Narrowing lives here so common/ never sees a client-side call, an
    // off-hand call, or a non-server Player. `true` from the handler means
    // "consume", which on Fabric is SUCCESS -- note the handler returns true
    // on permission DENIAL as well, so the click is swallowed and the block
    // is not broken.
    override fun onLeftClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    ) {
        AttackBlockCallback.EVENT.register { player, level, hand, pos, _ ->
            if (level.isClientSide || player !is ServerPlayer || level !is ServerLevel) {
                InteractionResult.PASS
            } else if (handler(player, level, hand, pos)) {
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }
    }

    override fun onRightClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    ) {
        UseBlockCallback.EVENT.register { player, level, hand, hitResult ->
            if (level.isClientSide || player !is ServerPlayer || level !is ServerLevel) {
                InteractionResult.PASS
            } else if (handler(player, level, hand, hitResult.blockPos)) {
                InteractionResult.SUCCESS
            } else {
                InteractionResult.PASS
            }
        }
    }

    // --- capability accessors ---------------------------------------------
    // These members are private/protected in raw vanilla, which is why
    // common/ cannot touch them. Here they are reachable: goalSelector via
    // the MobEntityAccessor mixin, and the Display setters via Fabric API's
    // transitive access wideners.

    override fun goalSelector(mob: Mob): GoalSelector =
        (mob as MobEntityAccessor).`cobblespawnregions$getGoalSelector`()

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
