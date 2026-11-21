package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import xyz.daikirai.via.ViaCompatibility;
import xyz.daikirai.via.ViaSwimming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInputFromOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MovementInputFromOptions.class)
public class MixinMovementInputFromOptions {
    @Unique private boolean onyx$modernMovement;
    @Unique private boolean onyx$slowPoseBeforeInput;

    @Inject(method = "updatePlayerMoveState", at = @At("HEAD"))
    private void onyx$captureSlowPose(CallbackInfo ci) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        onyx$modernMovement = player != null && player.movementInput == (Object) this
                && ViaCompatibility.usesMovement1206(player.sendQueue.getNetworkManager());
        if (onyx$modernMovement) {
            // Dry players can still have the swimming pose until after their first land movement.
            ViaSwimming swimming = ViaSwimming.forPlayer(player);
            boolean crouching = player.isSneaking() && !player.capabilities.isFlying && !player.isRiding()
                    && (swimming == null || !swimming.isSwimming());
            onyx$slowPoseBeforeInput = crouching || (swimming != null && swimming.isCrawlingOnLand(player));
        }
    }

    @Redirect(method = "updatePlayerMoveState", at = @At(value = "FIELD",
            target = "Lnet/minecraft/util/MovementInputFromOptions;sneak:Z", opcode = 180))
    private boolean onyx$movementSlowdownPose(MovementInputFromOptions input) {
        return onyx$modernMovement ? onyx$slowPoseBeforeInput : input.sneak;
    }

    @Inject(method = "updatePlayerMoveState", at = @At("RETURN"))
    private void clickgui$postPlayerInput(CallbackInfo ci) {
        Hooks.postPlayerInput();
    }
}
