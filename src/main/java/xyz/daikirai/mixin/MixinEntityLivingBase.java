package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import xyz.daikirai.via.ViaCompatibility;
import xyz.daikirai.via.ViaSwimming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityLivingBase.class)
public abstract class MixinEntityLivingBase {
    @Inject(method = "moveEntityWithHeading", at = @At("HEAD"), cancellable = true)
    private void onyx$modernWaterTravel(float strafe, float forward, CallbackInfo ci) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        ViaSwimming swimming = ViaSwimming.forPlayer(entity);
        if (swimming != null && entity.isInWater()) {
            EntityPlayerSP player = (EntityPlayerSP) entity;
            if (!player.capabilities.isFlying && !player.isRiding()) {
                swimming.travel(player, strafe, forward);
                ci.cancel();
            }
        }
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/EntityLivingBase;isInWater()Z"))
    private boolean onyx$modernWaterJump(EntityLivingBase entity) {
        ViaSwimming swimming = ViaSwimming.forPlayer(entity);
        return entity.isInWater() && (swimming == null || !entity.onGround || swimming.waterDepth() > 0.4);
    }

    @ModifyConstant(method = "onLivingUpdate", constant = @Constant(doubleValue = 0.005D), require = 3)
    private double onyx$minimumMotion(double legacyMinimum) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        // Match the modern client's velocity cutoff when coming to a stop.
        return player == (Object) this && ViaCompatibility.usesMovement1206(player.sendQueue.getNetworkManager())
                ? 0.003D : legacyMinimum;
    }

    @Redirect(method = "moveEntityWithHeading", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/EntityLivingBase;jumpMovementFactor:F", opcode = 180))
    private float onyx$airMovementSpeed(EntityLivingBase entity) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == entity && !player.capabilities.isFlying && !player.isRiding()
                && ViaCompatibility.usesMovement1206(player.sendQueue.getNetworkManager())) {
            // The legacy player updates its air speed after travel. Modern travel uses this tick's sprint state.
            return player.isSprinting() ? 0.025999999F : 0.02F;
        }
        return entity.jumpMovementFactor;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void clickgui$onJump(CallbackInfo ci) {
        if (Hooks.onJump((EntityLivingBase) (Object) this)) {
            ci.cancel();
        }
    }
}
