package xyz.daikirai.mixin;
import net.minecraftforge.fml.client.GuiScrollingList;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;
import xyz.daikirai.gui.onyx.menu.AndroidWallpaper;

/** Forge has its own scrolling-list implementation; keep its input and mod actions intact. */
@Mixin(value = GuiScrollingList.class, remap = false)
public abstract class MixinAndroidForgeList {
    @Shadow @Final protected int left, right, top, bottom;
    @Redirect(method = "drawScreen(IIF)V", at = @At(value = "INVOKE", ordinal = 0, remap = true,
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V"))
    private void onyx$listSurface(Tessellator tessellator) {
        if (!AndroidUi.active()) { tessellator.draw(); return; }
        tessellator.getWorldRenderer().finishDrawing();
        tessellator.getWorldRenderer().reset();
        AndroidUi.listSurface(left, top, right - left, bottom - top);
    }
    @Inject(method = "drawGradientRect", at = @At("HEAD"), cancellable = true)
    private void onyx$worldSurface(int x, int y, int right, int bottom, int c1, int c2, CallbackInfo ci) {
        if (AndroidUi.active()) {
            AndroidUi.listSurface(x, y, right - x, bottom - y);
            ci.cancel();
        }
    }
    @Redirect(method = "drawScreen(IIF)V", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/client/renderer/WorldRenderer;color(IIII)Lnet/minecraft/client/renderer/WorldRenderer;"))
    private WorldRenderer onyx$tones(WorldRenderer renderer, int r, int g, int b, int a) {
        if (!AndroidUi.active()) return renderer.color(r, g, b, a);
        int color = r >= 128 ? AndroidWallpaper.primaryContainer() : AndroidWallpaper.surfaceHigh();
        return renderer.color(color >> 16 & 255, color >> 8 & 255, color & 255, a);
    }
}
