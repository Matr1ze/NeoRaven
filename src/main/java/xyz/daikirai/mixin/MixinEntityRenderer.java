package xyz.daikirai.mixin;

import xyz.daikirai.module.render.CameraModules;
import xyz.daikirai.module.render.FreelookModule;
import xyz.daikirai.module.render.HandOutlineModule;
import xyz.daikirai.module.render.ZoomModule;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import xyz.daikirai.gui.onyx.render.GuiOpenAnimation;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {
    @Redirect(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItemInFirstPerson(F)V"))
    private void onyx$handOutline(ItemRenderer renderer, float partialTicks) {
        renderer.renderItemInFirstPerson(partialTicks);
        HandOutlineModule module = HandOutlineModule.instance;
        if (module != null && module.isEnabled()) module.render(renderer, partialTicks);
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/entity/EntityPlayerSP;setAngles(FF)V"))
    private void onyx$mouse(EntityPlayerSP player, float yaw, float pitch) {
        FreelookModule module = FreelookModule.instance;
        if (module == null || !module.isEnabled() || !module.mouse(yaw, pitch)) player.setAngles(yaw, pitch);
    }

    @Inject(method = "orientCamera", at = @At("HEAD"))
    private void onyx$prepareCamera(float partialTicks, CallbackInfo ci) {
        if (FreelookModule.instance != null && FreelookModule.instance.isEnabled())
            FreelookModule.instance.prepareCamera();
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationYaw:F", opcode = 180))
    private float onyx$cameraYaw(Entity entity) {
        return FreelookModule.cameraAngle(entity, entity.rotationYaw, false, false);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationYaw:F", opcode = 180))
    private float onyx$previousCameraYaw(Entity entity) {
        return FreelookModule.cameraAngle(entity, entity.prevRotationYaw, false, true);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;rotationPitch:F", opcode = 180))
    private float onyx$cameraPitch(Entity entity) {
        return FreelookModule.cameraAngle(entity, entity.rotationPitch, true, false);
    }

    @Redirect(method = "orientCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;prevRotationPitch:F", opcode = 180))
    private float onyx$previousCameraPitch(Entity entity) {
        return FreelookModule.cameraAngle(entity, entity.prevRotationPitch, true, true);
    }

    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V", remap = false))
    private void onyx$drawScreen(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        GuiOpenAnimation.drawScreen(screen, mouseX, mouseY, partialTicks);
    }

    /**
     * One custom-font scope per rendered frame. {@code updateCameraAndRender} contains the RenderTick
     * events (where the HUD modules draw), the world, the screen and the chat, so every vanilla
     * {@code FontRenderer} call inside it - chat lines, tab-list header/footer, F3, tooltips - gets
     * the ported font. Loading screens draw text outside this method and keep the bitmap font.
     */
    @Inject(method = "updateCameraAndRender", at = @At("HEAD"))
    private void onyx$fontScopeBegin(float partialTicks, long nanoTime, CallbackInfo ci) {
        xyz.daikirai.gui.onyx.render.GuiFont.begin();
    }

    @Inject(method = "updateCameraAndRender", at = @At("RETURN"))
    private void onyx$fontScopeEnd(float partialTicks, long nanoTime, CallbackInfo ci) {
        xyz.daikirai.gui.onyx.render.GuiFont.end();
    }

    @ModifyConstant(method = "hurtCameraEffect", constant = @Constant(floatValue = 14.0F))
    private float clickgui$hurtCam(float f) {
        return CameraModules.hurtCam();
    }

    /** Frame boundary for the smooth zoom: one camera setup per frame, and it keeps ticking after the module is released. */
    @Inject(method = "setupCameraTransform", at = @At("HEAD"))
    private void clickgui$zoomFrame(float partialTicks, int pass, CallbackInfo ci) {
        ZoomModule.tickStatic();
    }

    @Inject(method = "getFOVModifier", at = @At("RETURN"), cancellable = true)
    private void clickgui$zoomFov(float partialTicks, boolean useFOVSetting, CallbackInfoReturnable<Float> cir) {
        // Only the world camera zooms: the hand pass asks for the FOV with useFOVSetting = false.
        if (!useFOVSetting) {
            return;
        }
        cir.setReturnValue(ZoomModule.applyStatic(cir.getReturnValueF()));
    }

    @Redirect(method = "orientCamera", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/WorldClient;rayTraceBlocks(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;"))
    private MovingObjectPosition clickgui$clip(WorldClient world, Vec3 from, Vec3 to) {
        return CameraModules.shouldCameraClip() ? world.rayTraceBlocks(from, to) : null;
    }
}
