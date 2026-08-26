package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblespawnregions.utils.ItemStackSerialization;
import com.cobblespawnregions.utils.RegionExclusionHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;








@Mixin(ServerLevel.class)
public class RegionEntitySpawnMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"), cancellable = true)
    private void filterPokemonSpawnByRegion(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ItemStackSerialization.recordThrownBall(entity);
        if (!(entity instanceof PokemonEntity pokemonEntity)) return;

        Pokemon pokemon = pokemonEntity.getPokemon();
        if (pokemon.getPersistentData().contains("csr_region")) return;

        ServerLevel world = (ServerLevel) (Object) this;
        String dimensionId = world.dimension().location().toString();
        BlockPos spawnPos  = entity.blockPosition();


        if (RegionExclusionHelper.INSTANCE.shouldExcludePokemon(pokemon, spawnPos, dimensionId)) {
            cir.setReturnValue(false);
        }
    }
}
