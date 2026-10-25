package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.daikirai.render.HandOutlineRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {
    @Shadow
    private ItemStack itemToRender;
    @Unique private float onyx$handSwing;

    @Inject(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;", opcode = 180, ordinal = 0))
    private void clickgui$itemToRender(float partialTicks, CallbackInfo ci) {
        if (!HandOutlineRenderer.capturing()) this.itemToRender = Hooks.getItemToRender(this.itemToRender);
    }

    @ModifyVariable(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;", opcode = 180, ordinal = 0), index = 2)
    private float clickgui$animationProgress(float f) {
        if (HandOutlineRenderer.capturing()) return onyx$handSwing;
        return onyx$handSwing = Hooks.setAnimationProgress(f);
    }
}
