package xyz.daikirai.mixin;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.render.CustomGuiModule;

/**
 * Rewrites two pieces of the vanilla overlay for the ported hotbar.
 *
 * <p>1.9 Forge draws the hotbar itself inside {@code GuiIngameForge.renderTooltip}, which is gated
 * by {@code RenderGameOverlayEvent.Pre(HOTBAR)}, but the item name lives in a separate
 * {@code renderToolHightlight} pass with no event at all. Without the first injection the Material
 * hotbar shows two labels, and the vanilla one is positioned for the vanilla bar so it collides
 * once the ported bar is scaled up.</p>
 *
 * <p>The five status-row methods are wrapped so the hearts, food, armour, air and mount rows are
 * drawn in the ported bar's frame: the matrix is pushed at the head and popped at the return, so
 * the rows scale with the bar, sit 17px above it like vanilla put them above the vanilla bar, and
 * lift with it when the chat opens.</p>
 */
@Mixin(value = GuiIngameForge.class, remap = false)
public class MixinGuiIngameForge {

    @Inject(method = "renderToolHightlight", at = @At("HEAD"), cancellable = true, remap = false)
    private void clickgui$renderToolHighlight(ScaledResolution resolution, CallbackInfo ci) {
        if (CustomGuiModule.replacesHotbar()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHealth(II)V", at = @At("HEAD"), remap = false)
    private void clickgui$healthBegin(int width, int height, CallbackInfo ci) {
        CustomGuiModule.pushStatsTransform();
    }

    @Inject(method = "renderHealth(II)V", at = @At("RETURN"), remap = false)
    private void clickgui$healthEnd(int width, int height, CallbackInfo ci) {
        CustomGuiModule.popStatsTransform();
    }

    @Inject(method = "renderArmor(II)V", at = @At("HEAD"), remap = false)
    private void clickgui$armorBegin(int width, int height, CallbackInfo ci) {
        CustomGuiModule.pushStatsTransform();
    }

    @Inject(method = "renderArmor(II)V", at = @At("RETURN"), remap = false)
    private void clickgui$armorEnd(int width, int height, CallbackInfo ci) {
        CustomGuiModule.popStatsTransform();
    }

    @Inject(method = "renderFood(II)V", at = @At("HEAD"), remap = false)
    private void clickgui$foodBegin(int width, int height, CallbackInfo ci) {
        CustomGuiModule.pushStatsTransform();
    }

    @Inject(method = "renderFood(II)V", at = @At("RETURN"), remap = false)
    private void clickgui$foodEnd(int width, int height, CallbackInfo ci) {
        CustomGuiModule.popStatsTransform();
    }

    @Inject(method = "renderAir(II)V", at = @At("HEAD"), remap = false)
    private void clickgui$airBegin(int width, int height, CallbackInfo ci) {
        CustomGuiModule.pushStatsTransform();
    }

    @Inject(method = "renderAir(II)V", at = @At("RETURN"), remap = false)
    private void clickgui$airEnd(int width, int height, CallbackInfo ci) {
        CustomGuiModule.popStatsTransform();
    }

    @Inject(method = "renderHealthMount(II)V", at = @At("HEAD"), remap = false)
    private void clickgui$mountBegin(int width, int height, CallbackInfo ci) {
        CustomGuiModule.pushStatsTransform();
    }

    @Inject(method = "renderHealthMount(II)V", at = @At("RETURN"), remap = false)
    private void clickgui$mountEnd(int width, int height, CallbackInfo ci) {
        CustomGuiModule.popStatsTransform();
    }
}
