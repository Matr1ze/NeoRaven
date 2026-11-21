package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import xyz.daikirai.via.ViaCompatibility;
import xyz.daikirai.via.ViaSwimming;
import xyz.daikirai.via.ViaSwimmingPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP implements ViaSwimmingPlayer {
    @Unique private boolean onyx$lastReportedGround;
    @Unique private final ViaSwimming onyx$swimming = new ViaSwimming();

    @Override
    public ViaSwimming onyx$getSwimming() {
        return onyx$swimming;
    }

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void onyx$captureSwimSprint(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (ViaSwimming.forPlayer(player) != null) {
            onyx$swimming.beforeInput(player);
        }
    }

    @Inject(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V"))
    private void onyx$applySwimming(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (ViaSwimming.forPlayer(player) != null) {
            onyx$swimming.applyInput(player);
        }
    }

    @Inject(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onLivingUpdate()V", shift = At.Shift.AFTER))
    private void onyx$finishSwimmingTravel(CallbackInfo ci) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        if (ViaSwimming.forPlayer(player) != null) {
            onyx$swimming.afterTravel(player);
        }
    }

    @Inject(method = "onUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/AbstractClientPlayer;onUpdate()V"))
    private void clickgui$onUpdate(CallbackInfo ci) {
        Hooks.onUpdate();
    }

    @Inject(method = "onUpdateWalkingPlayer", at = @At("HEAD"), cancellable = true)
    private void clickgui$onUpdateWalkingPlayer(CallbackInfo ci) {
        onyx$lastReportedGround = Hooks.onUpdateWalkingPlayer(onyx$lastReportedGround);
        ci.cancel();
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MovementInput;moveStrafe:F", opcode = 181, ordinal = 0))
    private void clickgui$slowdownStrafe(MovementInput input, float f) {
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD", target = "Lnet/minecraft/util/MovementInput;moveForward:F", opcode = 181, ordinal = 0))
    private void clickgui$slowdownForward(MovementInput input, float f) {
        Hooks.onPlayerSlowdown();
    }

    @Redirect(method = "onLivingUpdate", at = @At(value = "FIELD",
            target = "Lnet/minecraft/util/MovementInput;moveForward:F", opcode = 180, ordinal = 4))
    private float onyx$continuingSprintInput(MovementInput input) {
        EntityPlayerSP player = (EntityPlayerSP) (Object) this;
        // Starting sprint still needs full forward input. Continuing it uses the modern forward-impulse check.
        if (input.moveForward > 1.0E-5F && ViaCompatibility.usesMovement1206(player.sendQueue.getNetworkManager())) {
            return Math.max(input.moveForward, 0.8F);
        }
        return input.moveForward;
    }

    @Inject(method = "updateEntityActionState", at = @At("RETURN"))
    private void clickgui$updateEntityActionState(CallbackInfo ci) {
        Hooks.onUpdateEntityActionState();
    }
}
