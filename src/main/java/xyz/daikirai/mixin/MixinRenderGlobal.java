package xyz.daikirai.mixin;

import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.render.SkyboxRenderer;

/**
 * Hooks the head of {@code RenderGlobal.renderSky} so the Skybox module can replace the vanilla
 * sky before it is built. Onyx patched the same entry point ({@code Util6.isEnabled43()} returning
 * {@code true} makes {@code renderSky} return immediately).
 *
 * <p>At this point the projection is a perspective matrix with the world FOV and the modelview
 * holds the camera transform, which is exactly what the dome needs; the renderer strips the
 * translation so the dome is centred on the camera instead of the world origin.</p>
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobal {

    // The descriptor is pinned because RenderGlobal has more than one renderSky overload and the
    // annotation processor otherwise resolves the wrong one.
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void clickgui$skybox(float partialTicks, int pass, CallbackInfo ci) {
        if (SkyboxRenderer.render(partialTicks)) {
            ci.cancel();
        }
    }
}
