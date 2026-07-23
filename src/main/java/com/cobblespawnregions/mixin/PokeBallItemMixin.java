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
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(PokeBallItem.class)
public class PokeBallItemMixin {

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/World;Lnet/minecraft/server/network/ServerPlayerEntity;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void captureUsedPokeBallStack(World world, PlayerEntity player, Hand usedHand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir, ItemStack itemStack) {
        ItemStackSerialization.beginThrow(itemStack);
    }

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/World;Lnet/minecraft/server/network/ServerPlayerEntity;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void clearUsedPokeBallStack(World world, PlayerEntity player, Hand usedHand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStackSerialization.endThrow();
    }
}
