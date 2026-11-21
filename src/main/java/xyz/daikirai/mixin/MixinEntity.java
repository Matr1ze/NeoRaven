package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.entity.Entity;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.daikirai.via.ViaSwimming;

@Mixin(Entity.class)
public abstract class MixinEntity {
    @Shadow protected boolean inWater;
    @Shadow protected boolean firstUpdate;
    @Shadow private int fire;
    @Shadow protected abstract void resetHeight();

    @Inject(method = "handleWaterMovement", at = @At("HEAD"), cancellable = true)
    private void onyx$modernWaterContact(CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        ViaSwimming swimming = ViaSwimming.forPlayer(entity);
        if (swimming == null || entity.isRiding()) {
            return;
        }
        boolean touching = swimming.updateWater((EntityPlayerSP) entity);
        if (touching) {
            if (!inWater && !firstUpdate) {
                resetHeight();
            }
            entity.fallDistance = 0.0F;
            fire = 0;
        }
        inWater = touching;
        cir.setReturnValue(touching);
    }

    @Inject(method = "isInsideOfMaterial", at = @At("HEAD"), cancellable = true)
    private void onyx$modernWaterAtEye(Material material, CallbackInfoReturnable<Boolean> cir) {
        if (material == Material.water) {
            Entity entity = (Entity) (Object) this;
            ViaSwimming swimming = ViaSwimming.forPlayer(entity);
            if (swimming != null && !entity.isRiding()) {
                cir.setReturnValue(swimming.eyesInWater((EntityPlayerSP) entity));
            }
        }
    }

    @ModifyVariable(method = "moveEntity", at = @At("STORE"), index = 19)
    private boolean clickgui$stopOnEdge(boolean flag) {
        return Hooks.stopOnEdge((Entity) (Object) this);
    }

    @Redirect(method = "moveFlying", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = 180))
    private float clickgui$movementYaw(Entity entity) {
        return Hooks.getMovementYaw(entity);
    }
}
