package xyz.daikirai.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.EnumAction;
import xyz.daikirai.hook.Hooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.daikirai.via.ViaSwimming;
import xyz.daikirai.via.ViaOffhand;
import xyz.daikirai.via.ViaCompatibility;

@Mixin(EntityPlayer.class)
public abstract class MixinEntityPlayer {
    @Shadow private ItemStack itemInUse;
    @Shadow protected abstract void updateItemUse(ItemStack stack, int particles);
    @Unique private boolean onyx$consumingOffhand;
    @Unique private int onyx$consumingHotbarSlot;

    @Inject(method = "setItemInUse", at = @At("RETURN"))
    private void onyx$rememberConsumptionHand(ItemStack stack, int duration, CallbackInfo ci) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (itemInUse == stack && onyx$serverOwnsConsumption(player)) {
            onyx$consumingOffhand = stack == ViaOffhand.stack(player);
            onyx$consumingHotbarSlot = player.inventory.currentItem;
        }
    }

    @Redirect(method = "onUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/InventoryPlayer;getCurrentItem()Lnet/minecraft/item/ItemStack;"))
    private ItemStack onyx$keepOffhandInUse(InventoryPlayer inventory) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        ItemStack offhand = ViaOffhand.stack(player);
        if (onyx$serverOwnsConsumption(player)) {
            if (!onyx$consumingOffhand && inventory.currentItem != onyx$consumingHotbarSlot) {
                return null;
            }
            ItemStack current = onyx$consumingOffhand ? offhand : inventory.getCurrentItem();
            // The modern client keeps using the same item in that hand when the server updates its count.
            if (current != null && current != itemInUse && current.isItemEqual(itemInUse)) {
                itemInUse = current;
            }
            return current;
        }
        return itemInUse != null && itemInUse == offhand ? offhand : inventory.getCurrentItem();
    }

    @Redirect(method = "onUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/EntityPlayer;onItemUseFinish()V"))
    private void onyx$waitForServerConsumption(EntityPlayer player) {
        if (!onyx$serverOwnsConsumption(player)) {
            onItemUseFinish();
        }
    }

    @Shadow protected abstract void onItemUseFinish();

    @Inject(method = "onItemUseFinish", at = @At("HEAD"), cancellable = true)
    private void onyx$finishOffhandUse(CallbackInfo ci) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (onyx$serverOwnsConsumption(player)) {
            // Status 9 confirms the animation. The inventory update owns the stack and hunger.
            updateItemUse(itemInUse, 16);
            player.clearItemInUse();
            ci.cancel();
            return;
        }
        if (itemInUse != null && itemInUse == ViaOffhand.stack(player)) {
            player.clearItemInUse();
            ci.cancel();
        }
    }

    @Unique
    private boolean onyx$serverOwnsConsumption(EntityPlayer player) {
        if (itemInUse == null || !(player instanceof EntityPlayerSP)) {
            return false;
        }
        EntityPlayerSP local = (EntityPlayerSP) player;
        EnumAction action = itemInUse.getItemUseAction();
        return (action == EnumAction.EAT || action == EnumAction.DRINK)
                && ViaCompatibility.usesMovement1206(local.sendQueue.getNetworkManager());
    }

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    private void onyx$swimmingEyeHeight(CallbackInfoReturnable<Float> cir) {
        EntityPlayer player = (EntityPlayer) (Object) this;
        if (!player.isPlayerSleeping() && ViaSwimming.forPlayer(player) != null) {
            if (player.height == 0.6F) {
                cir.setReturnValue(0.4F);
            } else if (player.height == 1.5F) {
                cir.setReturnValue(1.27F);
            }
        }
    }

    @Shadow
    private int itemInUseCount;

    private static Entity clickgui$attackTarget;

    @Inject(method = "isBlocking", at = @At("HEAD"), cancellable = true)
    private void clickgui$isBlocking(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(Hooks.isBlocking((EntityPlayer) (Object) this));
    }

    @Inject(method = "getItemInUseCount", at = @At("HEAD"), cancellable = true)
    private void clickgui$getItemInUseCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Hooks.getItemInUseCount((EntityPlayer) (Object) this, this.itemInUseCount));
    }

    @Inject(method = "attackTargetEntityWithCurrentItem", at = @At("HEAD"))
    private void clickgui$captureTarget(Entity target, CallbackInfo ci) {
        clickgui$attackTarget = target;
        Hooks.onAttackTarget(target);
    }

    @Redirect(method = "attackTargetEntityWithCurrentItem", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;motionX:D", opcode = 181, ordinal = 0))
    private void clickgui$slowdownX(EntityPlayer player, double d) {
    }

    @Redirect(method = "attackTargetEntityWithCurrentItem", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/EntityPlayer;motionZ:D", opcode = 181, ordinal = 0))
    private void clickgui$slowdownZ(EntityPlayer player, double d) {
    }

    @Redirect(method = "attackTargetEntityWithCurrentItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setSprinting(Z)V"))
    private void clickgui$slowdownSprint(EntityPlayer player, boolean sprint) {
        Hooks.onAttackSlowdown(clickgui$attackTarget);
    }
}
