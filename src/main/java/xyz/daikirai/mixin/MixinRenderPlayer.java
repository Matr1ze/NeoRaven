package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.via.ViaOffhand;

@Mixin(RenderPlayer.class)
public class MixinRenderPlayer {
    @Inject(method = "setModelVisibilities", at = @At("RETURN"))
    private void clickgui$updateModelVisibilities(AbstractClientPlayer player, CallbackInfo ci) {
        ModelPlayer model = ((RenderPlayer) (Object) this).getMainModel();
        if (player instanceof EntityPlayerSP && ViaOffhand.hasLocalSwordPose(player, player.getHeldItem())) {
            ItemStack using = player.getItemInUse();
            if (ViaOffhand.isSwordPosing((EntityPlayerSP) player, player.getHeldItem())) {
                model.heldItemRight = 3;
            } else if (using != null && (using.getItemUseAction() != EnumAction.BLOCK
                    || using == ViaOffhand.stack(player))) {
                model.heldItemRight = 1;
            }
        }
        Hooks.updateModelVisibilities(player, model);
    }
}
