package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RendererLivingEntity.class)
public abstract class MixinRendererLivingEntity {
    private static float clickgui$partialTicks;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void clickgui$capture(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        clickgui$partialTicks = partialTicks;
    }

    @Redirect(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/EntityLivingBase;prevRotationPitch:F", opcode = Opcodes.GETFIELD))
    private float clickgui$prevPitch(EntityLivingBase entity) {
        return Hooks.getRotationPitchHead(entity, clickgui$partialTicks);
    }

    @Redirect(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/EntityLivingBase;rotationPitch:F", opcode = Opcodes.GETFIELD))
    private float clickgui$pitch(EntityLivingBase entity) {
        return Hooks.getRotationPitchHead(entity, clickgui$partialTicks);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/LayerRenderer;doRenderLayer(Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V"))
    private void clickgui$doRenderLayer(LayerRenderer layer, EntityLivingBase e, float f, float f2, float f3, float f4, float f5, float f6, float f7) {
        Hooks.doRenderLayer((RendererLivingEntity) (Object) this, layer, e, f, f2, f3, f4, f5, f6, f7);
    }

    /**
     * Vanilla draws the nameplate background as an untextured quad. That switch goes through
     * {@code GlStateManager}, so if any module left the cache out of step with the driver the call
     * is skipped and the plate is filled with whatever texture happens to be bound — which is what
     * "the name tag's box covers the skybox" looks like once the Skybox module has been rendering.
     *
     * <p>Enabling first makes the cache and GL agree on "on", so the following disable really turns
     * texturing off. The redirect only fires on that one line of {@code renderName} and vanilla
     * re-enables texturing right after the plate, so an early return (a cancelled nametag, for
     * example) can never leave the world rendering untextured.</p>
     */
    /**
     * Jelly (friends wine doll) render hook.
     *
     * <p>{@code RendererLivingEntity#doRender} has already translated to the entity's feet and
     * applied {@code scale(-1,-1,1)} (plus the -1.5078125 offset that puts the model's origin back on
     * the ground) when it reaches {@code renderModel}, so pushing a matrix and scaling/rotating there
     * squashes the model around its feet and leaves the nameplate, shadows and hitbox alone. The pop
     * sits on the single {@code disableRescaleNormal()} that both the outline and normal branches run
     * through, which keeps it balanced even though {@code renderModel} has two call sites.</p>
     */
    private static boolean clickgui$jellyPushed;

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At("HEAD"))
    private void clickgui$jellyHeal(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        // An exception inside the previous frame's model render skips the pop; unwind it here so the
        // matrix stack cannot grow by one entry per broken frame.
        if (clickgui$jellyPushed) {
            clickgui$jellyPushed = false;
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RendererLivingEntity;renderModel(Lnet/minecraft/entity/EntityLivingBase;FFFFFF)V",
            shift = At.Shift.BEFORE))
    private void clickgui$jellyBegin(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (!xyz.daikirai.module.render.JellyModule.applies(entity)) {
            return;
        }
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        clickgui$jellyPushed = true;
        xyz.daikirai.module.render.JellyModule.transform();
    }

    @Inject(method = "doRender(Lnet/minecraft/entity/EntityLivingBase;DDDFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;disableRescaleNormal()V",
            shift = At.Shift.BEFORE))
    private void clickgui$jellyEnd(EntityLivingBase entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
        if (!clickgui$jellyPushed) {
            return;
        }
        clickgui$jellyPushed = false;
        net.minecraft.client.renderer.GlStateManager.popMatrix();
    }

    @Redirect(method = "renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;disableTexture2D()V"))
    private void clickgui$disableNameplateTexture() {
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.disableTexture2D();
    }
}
