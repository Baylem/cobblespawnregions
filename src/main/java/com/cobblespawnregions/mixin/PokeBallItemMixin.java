package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblespawnregions.utils.ItemStackSerialization;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PokeBallItem.class)
public class PokeBallItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void captureUsedPokeBallStack(World world, PlayerEntity player, Hand usedHand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!world.isClient()) {
            ItemStackSerialization.beginThrow(player.getStackInHand(usedHand));
        }
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void clearUsedPokeBallStack(World world, PlayerEntity player, Hand usedHand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!world.isClient()) {
            ItemStackSerialization.endThrow();
        }
    }
}
