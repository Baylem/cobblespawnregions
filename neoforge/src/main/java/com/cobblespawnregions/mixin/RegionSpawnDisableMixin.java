package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.api.spawning.SpawnCause;
import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawner;
import com.cobblemon.mod.common.api.spawning.spawner.SpawningZoneInput;
import com.cobblespawnregions.utils.RegionExclusionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;









@Mixin(value = PlayerSpawner.class, remap = false)
public class RegionSpawnDisableMixin {

    @Inject(method = "getZoneInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void cancelSpawnZoneInDisabledRegion(SpawnCause cause, CallbackInfoReturnable<SpawningZoneInput> cir) {
        Entity entity = cause.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;

        Level world = player.level();
        String dimensionId = world.dimension().location().toString();
        BlockPos playerPos  = player.blockPosition();

        if (RegionExclusionHelper.INSTANCE.isSpawnDisabledAt(playerPos, dimensionId)) {
            cir.setReturnValue(null);
        }
    }
}
