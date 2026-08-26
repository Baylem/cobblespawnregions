package com.cobblespawnregions.platform

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
import java.util.ServiceLoader

/**
 * The entire loader-specific surface of CobbleSpawnRegions: eleven event
 * registrations, no logic.
 *
 * Note there is deliberately **no permission hook here**. The mod's only
 * permission check goes through EverlastingUtils'
 * `CommandManager.hasPermissionOrOp`, which already abstracts the backend
 * behind its own Platform. Adding a second one here would mean two
 * independent fallback paths to keep in sync.
 *
 * Implementations live in the loader modules and are discovered through
 * `META-INF/services/com.cobblespawnregions.platform.Platform`.
 */
interface Platform {

    /**
     * Fabric `SERVER_STARTING` / NeoForge `ServerAboutToStartEvent`.
     *
     * Load-bearing: this primes `SchedulerManager`. Miss it and every
     * scheduled loop — particle, scan, spawn, tracker-save, battle-cleanup —
     * silently never runs while the mod looks perfectly healthy.
     */
    fun onServerStarting(handler: (MinecraftServer) -> Unit)

    /** Fabric `SERVER_STARTED` / NeoForge `ServerStartedEvent`. */
    fun onServerStarted(handler: (MinecraftServer) -> Unit)

    /** Fabric `SERVER_STOPPING` / NeoForge `ServerStoppingEvent`. */
    fun onServerStopping(handler: (MinecraftServer) -> Unit)

    /**
     * Fabric `CHUNK_LOAD` / NeoForge `ChunkEvent.Load`.
     * Implementations must narrow to [ServerLevel] before invoking.
     */
    fun onChunkLoad(handler: (ServerLevel, LevelChunk) -> Unit)

    /**
     * Fabric `CHUNK_UNLOAD` / NeoForge `ChunkEvent.Unload`.
     *
     * Must fire *before* [onEntityUnload] for the same chunk — the entity
     * handler reads a flag this one sets to decide whether a Pokémon is
     * merely unloading or genuinely gone, and that decides whether it is
     * dropped from the persisted tracker. See docs/PLATFORM-ABSTRACTION.md.
     */
    fun onChunkUnload(handler: (ServerLevel, LevelChunk) -> Unit)

    /** Fabric `ENTITY_LOAD` / NeoForge `EntityJoinLevelEvent` (server side only). */
    fun onEntityLoad(handler: (Entity, ServerLevel) -> Unit)

    /**
     * Fabric `ENTITY_UNLOAD` / NeoForge `EntityLeaveLevelEvent`.
     * `entity.removalReason` must already be set when this fires.
     */
    fun onEntityUnload(handler: (Entity, ServerLevel) -> Unit)

    /** Fabric `END_SERVER_TICK` / NeoForge `ServerTickEvent.Post`. */
    fun onServerTick(handler: (MinecraftServer) -> Unit)

    /** Fabric `ServerPlayConnectionEvents.DISCONNECT` / NeoForge `PlayerLoggedOut`. */
    fun onPlayerDisconnect(handler: (ServerPlayer) -> Unit)

    /**
     * Fabric `AttackBlockCallback` / NeoForge `PlayerInteractEvent.LeftClickBlock`.
     *
     * Return `true` to **consume** the interaction. The handler returns true
     * both on success and on permission denial — if denial is not consumed,
     * an unauthorised player still breaks the block they clicked.
     *
     * Implementations narrow Player→ServerPlayer and Level→ServerLevel, and
     * skip client-side and off-hand calls, before invoking the handler.
     */
    fun onLeftClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    )

    /**
     * Fabric `UseBlockCallback` / NeoForge `PlayerInteractEvent.RightClickBlock`.
     * Same consume semantics as [onLeftClickBlock].
     */
    fun onRightClickBlock(
        handler: (ServerPlayer, ServerLevel, InteractionHand, BlockPos) -> Boolean
    )

    // ---------------------------------------------------------------------
    // Capability accessors, not events.
    //
    // These exist because common/ compiles against *raw vanilla* Mojmap
    // Minecraft (ModDevGradle NeoForm). Fabric API ships transitive access
    // wideners that make several vanilla members public, and the pre-split
    // code silently relied on them. Without a loader on the classpath those
    // members are private again, so the capability has to be delegated.
    //
    // Fabric implements these directly (its access wideners are in effect);
    // NeoForge will need an Access Transformer entry for the same members.
    // ---------------------------------------------------------------------

    /**
     * `Mob.goalSelector` is `protected final` in vanilla. Fabric reaches it
     * through the `MobEntityAccessor` mixin, which lives in the loader module
     * and so is not importable from here.
     */
    fun goalSelector(mob: Mob): GoalSelector

    /** `Display.BlockDisplay.setBlockState` is private in vanilla. */
    fun setDisplayBlockState(display: Display.BlockDisplay, state: BlockState)

    /**
     * `Display.setTransformation` and the two interpolation setters are all
     * private in vanilla.
     */
    fun setDisplayTransformation(
        display: Display,
        transformation: Transformation,
        interpolationDuration: Int,
        interpolationDelay: Int
    )

    companion object {
        val INSTANCE: Platform by lazy {
            ServiceLoader.load(Platform::class.java).findFirst().orElseThrow {
                IllegalStateException(
                    "No com.cobblespawnregions.platform.Platform implementation on " +
                        "the classpath. The loader module is missing or its " +
                        "META-INF/services entry was not packaged."
                )
            }
        }
    }
}
