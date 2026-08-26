package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.api.riding.behaviour.RidingController;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblespawnregions.utils.RegionExclusionHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dismounts a rider after the mount crosses into a position where riding is blocked. */
@Mixin(RidingController.class)
public class RidingRegionBoundaryMixin {
    @Shadow @Final private PokemonEntity entity;

    @Unique private long cobblespawnregions$lastCheckedBlock = Long.MIN_VALUE;
    @Unique private String cobblespawnregions$lastCheckedDimension;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cobblespawnregions$checkRidingRegionEntry(CallbackInfo ci) {
        if (entity.level().isClientSide()
                || !(entity.getControllingPassenger() instanceof ServerPlayer player)) {
            cobblespawnregions$resetBoundaryCache();
            return;
        }

        long block = entity.blockPosition().asLong();
        String dimension = entity.level().dimension().location().toString();
        if (block == cobblespawnregions$lastCheckedBlock
                && dimension.equals(cobblespawnregions$lastCheckedDimension)) {
            return;
        }

        cobblespawnregions$lastCheckedBlock = block;
        cobblespawnregions$lastCheckedDimension = dimension;
        if (!RegionExclusionHelper.shouldBlockRiding(entity.getPokemon(), entity.blockPosition(), dimension)) return;

        player.stopRiding();
        player.displayClientMessage(
                Component.literal("Riding is disabled in this region.").withStyle(ChatFormatting.RED),
                true
        );
        cobblespawnregions$resetBoundaryCache();
    }

    @Unique
    private void cobblespawnregions$resetBoundaryCache() {
        cobblespawnregions$lastCheckedBlock = Long.MIN_VALUE;
        cobblespawnregions$lastCheckedDimension = null;
    }
}
