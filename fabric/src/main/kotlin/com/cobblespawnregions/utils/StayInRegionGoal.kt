package com.cobblespawnregions.utils

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.levelgen.Heightmap
import java.util.EnumSet

class StayInRegionGoal(
    private val entity: PokemonEntity,
    private val regionId: String,
    private val settings: RegionWanderingSettings,
    private val allowedBlocks: List<String>,
    private val customBlockSpawnModes: Map<String, CustomBlockSpawnMode>
) : Goal() {

    private var targetPos: Vec3? = null
    private var ticksSinceCheck = entity.random.nextInt(settings.tickDelay.coerceAtLeast(1))
    private var nextPathAttemptTick = 0L
    private var cachedRegion: RegionData? = null
    private var lastDistanceToTargetSq = Double.MAX_VALUE
    private var stuckTicks = 0
    private var lastRepathTick = 0L

    init {
        setFlags(EnumSet.of(Goal.Flag.MOVE))
    }

    override fun canUse(): Boolean {
        if (!entity.pokemon.isWild()) return false
        if (!settings.enabled) return false
        if (entity.level().gameTime < nextPathAttemptTick) return false

        val delay = settings.tickDelay.coerceAtLeast(1)
        if (--ticksSinceCheck > 0) return false
        ticksSinceCheck = delay

        val region = RegionsConfig.getRegion(regionId) ?: return false
        val dimension = entity.level().dimension().location().toString()
        if (region.dimension != dimension) return false

        val outside = !RegionsConfig.contains(region, entity.blockPosition())
        if (!outside) return false

        cachedRegion = region
        return true
    }

    override fun start() {
        val region = cachedRegion ?: RegionsConfig.getRegion(regionId) ?: return
        targetPos = targetForMode(region, chooseNewRandom = true)

        val target = targetPos ?: return
        lastDistanceToTargetSq = Double.MAX_VALUE
        stuckTicks = 0
        if (!startPathTo(target)) {
            nextPathAttemptTick = entity.level().gameTime + (settings.tickDelay.coerceAtLeast(1) * 4).coerceAtLeast(40)
        }
    }

    override fun canContinueToUse(): Boolean {
        if (!entity.pokemon.isWild()) return false
        if (!settings.enabled) return false
        val region = cachedRegion ?: RegionsConfig.getRegion(regionId)?.also { cachedRegion = it } ?: return false
        return !RegionsConfig.contains(region, entity.blockPosition())
    }

    override fun tick() {
        if (!entity.pokemon.isWild()) {
            entity.navigation.stop()
            return
        }
        val region = cachedRegion ?: RegionsConfig.getRegion(regionId)?.also { cachedRegion = it } ?: return
        if (RegionsConfig.contains(region, entity.blockPosition())) return

        val target = targetPos ?: targetForMode(region, chooseNewRandom = true).also { targetPos = it }
        val now = entity.level().gameTime
        val distanceSq = target.distanceToSqr(entity.position())

        if (entity.navigation.isDone) {
            repath(region, now, chooseNewTarget = false)
            return
        }

        if (distanceSq + 0.25 < lastDistanceToTargetSq) {
            lastDistanceToTargetSq = distanceSq
            stuckTicks = 0
            return
        }

        stuckTicks++
        if (stuckTicks >= STUCK_REPATH_TICKS) {
            repath(region, now, chooseNewTarget = true)
        }
    }

    override fun stop() {
        targetPos = null
        entity.navigation.stop()
        lastDistanceToTargetSq = Double.MAX_VALUE
        stuckTicks = 0
    }

    private fun repath(region: RegionData, now: Long, chooseNewTarget: Boolean) {
        if (now - lastRepathTick < REPATH_COOLDOWN_TICKS) return

        val target = if (chooseNewTarget) targetForMode(region, chooseNewRandom = true) else targetPos ?: targetForMode(region, chooseNewRandom = false)
        targetPos = target

        if (startPathTo(target)) {
            lastRepathTick = now
            lastDistanceToTargetSq = target.distanceToSqr(entity.position())
            stuckTicks = 0
        } else {
            lastRepathTick = now
            nextPathAttemptTick = now + REPATH_COOLDOWN_TICKS
        }
    }

    private fun startPathTo(target: Vec3): Boolean {
        val path = entity.navigation.createPath(target.x, target.y, target.z, 0) ?: return false
        entity.navigation.stop()
        entity.navigation.moveTo(path, settings.speed.coerceAtLeast(0.05))
        nextPathAttemptTick = entity.level().gameTime + settings.tickDelay.coerceAtLeast(1)
        lastRepathTick = entity.level().gameTime
        lastDistanceToTargetSq = target.distanceToSqr(entity.position())
        return true
    }

    private fun targetForMode(region: RegionData, chooseNewRandom: Boolean): Vec3 =
        when (settings.returnTarget.uppercase()) {
            "CENTER" -> spawnPointNear(centerTarget(region)) ?: centerTarget(region)
            "CLOSEST" -> spawnPointNear(entity.position()) ?: closestTarget(region)
            else -> {
                if (chooseNewRandom) {
                    RegionSpawnHelper.pickRandomSpawnPos(regionId, allowedBlocks, customBlockSpawnModes)?.let(Vec3::atBottomCenterOf)
                        ?: randomTarget(region)
                        ?: centerTarget(region)
                } else {
                    targetPos ?: spawnPointNear(centerTarget(region)) ?: centerTarget(region)
                }
            }
        }

    private fun spawnPointNear(origin: Vec3): Vec3? =
        RegionSpawnHelper.pickClosestSpawnPos(regionId, allowedBlocks, origin, customBlockSpawnModes)?.let(Vec3::atBottomCenterOf)

    private fun randomTarget(region: RegionData): Vec3? {
        val minX = minOf(region.pos1.x, region.pos2.x)
        val maxX = maxOf(region.pos1.x, region.pos2.x)
        val minZ = minOf(region.pos1.z, region.pos2.z)
        val maxZ = maxOf(region.pos1.z, region.pos2.z)

        repeat(12) {
            val x = entity.random.nextIntBetweenInclusive(minX, maxX)
            val z = entity.random.nextIntBetweenInclusive(minZ, maxZ)
            val y = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
            val pos = BlockPos(x, y, z)

            if (RegionsConfig.contains(region, pos)) {
                return Vec3.atBottomCenterOf(pos)
            }
        }

        return null
    }

    private fun closestTarget(region: RegionData): Vec3 {
        val minX = minOf(region.pos1.x, region.pos2.x)
        val maxX = maxOf(region.pos1.x, region.pos2.x)
        val minY = minOf(region.pos1.y, region.pos2.y)
        val maxY = maxOf(region.pos1.y, region.pos2.y)
        val minZ = minOf(region.pos1.z, region.pos2.z)
        val maxZ = maxOf(region.pos1.z, region.pos2.z)

        val x = entity.blockPosition().x.coerceIn(minX, maxX)
        val z = entity.blockPosition().z.coerceIn(minZ, maxZ)
        val surfaceY = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        val y = surfaceY.coerceIn(minY, maxY)

        return Vec3.atBottomCenterOf(BlockPos(x, y, z))
    }

    private fun centerTarget(region: RegionData): Vec3 {
        val x = (region.pos1.x + region.pos2.x) / 2
        val z = (region.pos1.z + region.pos2.z) / 2
        val y = entity.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        return Vec3.atBottomCenterOf(BlockPos(x, y, z))
    }

    private companion object {
        private const val STUCK_REPATH_TICKS = 30
        private const val REPATH_COOLDOWN_TICKS = 20L
    }
}
