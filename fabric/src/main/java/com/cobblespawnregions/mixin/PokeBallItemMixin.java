package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblespawnregions.utils.ItemStackSerialization;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.level.Level;
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
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;)V"
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void captureUsedPokeBallStack(Level world, Player player, InteractionHand usedHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir, ItemStack itemStack) {
        ItemStackSerialization.beginThrow(itemStack);
    }

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void clearUsedPokeBallStack(Level world, Player player, InteractionHand usedHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStackSerialization.endThrow();
    }
}
