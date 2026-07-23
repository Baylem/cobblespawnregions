package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblespawnregions.utils.RegionExclusionHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public class PokemonEntityRideMixin {
    @Inject(method = "tryRidingPokemon", at = @At("HEAD"), cancellable = true)
    private void cobblespawnregions$blockRidingInRegion(
            ServerPlayerEntity player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (player.getWorld().isClient()) return;
        PokemonEntity entity = (PokemonEntity) (Object) this;
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        boolean blockRiding = RegionExclusionHelper.shouldBlockRiding(
                entity.getPokemon(), entity.getBlockPos(), dimension
        );
        if (blockRiding) {
            player.sendMessage(
                    Text.literal("Riding is disabled in this region.").formatted(Formatting.RED),
                    true
            );
            cir.setReturnValue(false);
        }
    }
}
