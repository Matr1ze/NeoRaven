package xyz.daikirai.mixin;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.via.ViaOffhand;

@Mixin(LayerHeldItem.class)
public class MixinLayerHeldItem {
    @Shadow @Final private RendererLivingEntity<?> livingEntityRenderer;

    @Inject(method = "doRenderLayer", at = @At("RETURN"))
    private void onyx$renderLeftHand(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                     float ageInTicks, float headYaw, float headPitch, float scale, CallbackInfo ci) {
        if (!(entity instanceof EntityPlayer) || !(livingEntityRenderer.getMainModel() instanceof ModelBiped)) {
            return;
        }
        ItemStack stack = ViaOffhand.stack((EntityPlayer) entity);
        if (stack == null) {
            return;
        }
        ModelBiped model = (ModelBiped) livingEntityRenderer.getMainModel();
        GlStateManager.pushMatrix();
        try {
            if (model.isChild) {
                GlStateManager.translate(0.0F, 0.625F, 0.0F);
                GlStateManager.rotate(-20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(0.5F, 0.5F, 0.5F);
            }
            model.bipedLeftArm.postRender(0.0625F);
            GlStateManager.translate(0.0625F, 0.4375F, 0.0625F);
            if (stack.getItem() instanceof ItemBlock && Block.getBlockFromItem(stack.getItem()).getRenderType() == 2) {
                GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
                GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(-45.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scale(-0.375F, -0.375F, 0.375F);
            }
            if (entity.isSneaking()) {
                GlStateManager.translate(0.0F, 0.203125F, 0.0F);
            }
            Minecraft.getMinecraft().getItemRenderer().renderItem(entity, stack, TransformType.THIRD_PERSON);
        } finally {
            GlStateManager.popMatrix();
        }
    }
}
