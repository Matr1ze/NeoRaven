package xyz.daikirai.mixin;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import xyz.daikirai.via.ViaOffhand;

@Mixin(PlayerControllerMP.class)
public abstract class MixinPlayerControllerMP {
    @Inject(method = "onPlayerRightClick", at = @At(value = "NEW",
            target = "net/minecraft/network/play/client/C08PacketPlayerBlockPlacement"),
            locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void onyx$skipSwordUseOnInactiveBlock(EntityPlayerSP player, WorldClient world, ItemStack held,
                                                  BlockPos pos, EnumFacing face, Vec3 hit,
                                                  CallbackInfoReturnable<Boolean> cir,
                                                  float hitX, float hitY, float hitZ, boolean activated) {
        PlayerControllerMP controller = (PlayerControllerMP) (Object) this;
        if (!activated && !controller.isSpectator() && ViaOffhand.hasLocalSwordPose(player, held)) {
            // Vanilla 1.8 would still send a sword use on stone before falling back to blocking.
            cir.setReturnValue(false);
        }
    }
}
