package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import xyz.daikirai.render.HandOutlineRenderer;
import xyz.daikirai.via.ViaOffhand;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class MixinItemRenderer {
    @Shadow
    private ItemStack itemToRender;
    @Unique private float onyx$handSwing;
    @Unique private boolean onyx$renderingOffhand;

    @Shadow protected abstract void rotateArroundXAndY(float pitch, float yaw);
    @Shadow protected abstract void setLightMapFromPlayer(AbstractClientPlayer player);
    @Shadow protected abstract void rotateWithPlayerRotations(EntityPlayerSP player, float partialTicks);
    @Shadow protected abstract void transformFirstPersonItem(float equippedProgress, float swingProgress);
    @Shadow protected abstract void performDrinking(AbstractClientPlayer player, float partialTicks);
    @Shadow protected abstract void doBlockTransformations();
    @Shadow protected abstract void doBowTransformations(float partialTicks, AbstractClientPlayer player);

    @Group(name = "mainHandUseCount", min = 1, max = 1)
    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/AbstractClientPlayer;getItemInUseCount()I"), require = 0)
    private int onyx$mainHandUseCount(AbstractClientPlayer player) {
        if (player instanceof EntityPlayerSP) {
            EntityPlayerSP local = (EntityPlayerSP) player;
            ItemStack using = local.getItemInUse();
            if (using != null) {
                // A slot refresh can replace the offhand stack before the active stack catches up.
                if (using == ViaOffhand.stack(local) || (ViaOffhand.hasLocalSwordPose(local, itemToRender)
                        && using.getItemUseAction() != EnumAction.BLOCK)) {
                    return 0;
                }
            }
            if (ViaOffhand.isSwordPosing(local, itemToRender)) {
                return 1;
            }
        }
        return player.getItemInUseCount();
    }

    @Group(name = "mainHandUseCount", min = 1, max = 1)
    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;getItemInUseCount()I"), require = 0)
    private int onyx$mainHandUseCountLocal(EntityPlayerSP player) {
        return onyx$mainHandUseCount(player);
    }

    @Inject(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;", opcode = 180, ordinal = 0))
    private void clickgui$itemToRender(float partialTicks, CallbackInfo ci) {
        if (!HandOutlineRenderer.capturing()) this.itemToRender = Hooks.getItemToRender(this.itemToRender);
    }

    @ModifyVariable(method = "renderItemInFirstPerson", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/ItemRenderer;itemToRender:Lnet/minecraft/item/ItemStack;", opcode = 180, ordinal = 0), index = 2)
    private float clickgui$animationProgress(float f) {
        if (HandOutlineRenderer.capturing()) return onyx$handSwing;
        return onyx$handSwing = Hooks.setAnimationProgress(f);
    }

    /** Vanilla and OptiFine both draw here, even though their outer renderHand methods differ. */
    @Inject(method = "renderItemInFirstPerson", at = @At("RETURN"))
    private void onyx$handOutline(float partialTicks, CallbackInfo ci) {
        if (HandOutlineRenderer.capturing()) return;
        xyz.daikirai.module.render.HandOutlineModule module = xyz.daikirai.module.render.HandOutlineModule.instance;
        if (module != null && module.isEnabled()) module.render((ItemRenderer) (Object) this, partialTicks);
        onyx$renderOffhand(partialTicks);
    }

    @Unique
    private void onyx$renderOffhand(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        ItemStack offhand = ViaOffhand.stack(player);
        if (offhand == null || mc.gameSettings.thirdPersonView != 0 || mc.playerController.isSpectator()) {
            return;
        }
        ItemStack previousItem = itemToRender;
        onyx$renderingOffhand = true;
        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.disableCull();
        try {
            itemToRender = offhand;
            float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;
            float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
            rotateArroundXAndY(pitch, yaw);
            setLightMapFromPlayer(player);
            rotateWithPlayerRotations(player, partialTicks);
            GlStateManager.scale(-1.0F, 1.0F, 1.0F);
            EnumAction action = player.getItemInUse() == offhand ? offhand.getItemUseAction() : EnumAction.NONE;
            if (action == EnumAction.EAT || action == EnumAction.DRINK) {
                performDrinking(player, partialTicks);
            }
            transformFirstPersonItem(0.0F, 0.0F);
            if (action == EnumAction.BLOCK) {
                doBlockTransformations();
            } else if (action == EnumAction.BOW) {
                doBowTransformations(partialTicks, player);
            }
            ((ItemRenderer) (Object) this).renderItem(player, offhand, TransformType.FIRST_PERSON);
        } finally {
            itemToRender = previousItem;
            GlStateManager.enableCull();
            GlStateManager.disableRescaleNormal();
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            onyx$renderingOffhand = false;
        }
    }

    /** The normal held-item transform, excluding HandOutline's mask replay and other entities. */
    @Inject(method = "renderItem", at = @At("RETURN"))
    private void onyx$weaponWisps(net.minecraft.entity.EntityLivingBase owner, ItemStack stack,
            net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType transform, CallbackInfo ci) {
        if (transform == net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType.FIRST_PERSON
                && owner == Minecraft.getMinecraft().thePlayer && !HandOutlineRenderer.capturing() && !onyx$renderingOffhand) {
            xyz.daikirai.module.render.WeaponWispsModule.held(stack);
        }
    }
}
