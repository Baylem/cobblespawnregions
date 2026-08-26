package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblespawnregions.utils.RegionExclusionHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokemonEntity.class)
public class PokemonEntityRideMixin {
    @Inject(method = "tryRidingPokemon", at = @At("HEAD"), cancellable = true)
    private void cobblespawnregions$blockRidingInRegion(
            ServerPlayer player,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (player.level().isClientSide()) return;
        PokemonEntity entity = (PokemonEntity) (Object) this;
        String dimension = player.level().dimension().location().toString();
        boolean blockRiding = RegionExclusionHelper.shouldBlockRiding(
                entity.getPokemon(), entity.blockPosition(), dimension
        );
        if (blockRiding) {
            player.displayClientMessage(
                    Component.literal("Riding is disabled in this region.").withStyle(ChatFormatting.RED),
                    true
            );
            cir.setReturnValue(false);
        }
    }
}
