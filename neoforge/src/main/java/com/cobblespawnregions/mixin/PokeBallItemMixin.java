package com.cobblespawnregions.mixin;

import com.cobblemon.mod.common.item.PokeBallItem;
import com.cobblespawnregions.utils.ItemStackSerialization;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge variant. Differs from the Fabric copy in one way: the local capture
 * uses MixinExtras {@code @Local} instead of {@code LocalCapture.CAPTURE_FAILHARD}.
 *
 * {@code @Local} matches by type and ordinal rather than by exact
 * local-variable-table position, so it survives recompilation of the target.
 * CAPTURE_FAILHARD hard-fails on any LVT drift, which across two loaders and
 * two toolchains is a matter of when, not if.
 *
 * NOTE: no {@code remap = false} here, deliberately. The @At target is a
 * Cobblemon method whose *arguments* are vanilla types, and those differ per
 * mapping namespace. See CLAUDE.md.
 */
@Mixin(PokeBallItem.class)
public class PokeBallItemMixin {

    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void captureUsedPokeBallStack(
            Level world, Player player, InteractionHand usedHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir,
            @Local(ordinal = 0) ItemStack itemStack) {
        ItemStackSerialization.beginThrow(itemStack);
    }

    /**
     * Pairs with the injection above. Dropping it leaks the thread-local
     * across throws -- the two halves are one mechanism, together with
     * RegionEntitySpawnMixin.
     */
    @Inject(
            method = "use",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/cobblemon/mod/common/item/PokeBallItem;throwPokeBall(Lnet/minecraft/world/level/Level;Lnet/minecraft/server/level/ServerPlayer;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void clearUsedPokeBallStack(
            Level world, Player player, InteractionHand usedHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStackSerialization.endThrow();
    }
}
