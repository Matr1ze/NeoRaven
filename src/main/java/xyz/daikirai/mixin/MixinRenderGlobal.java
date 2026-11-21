package xyz.daikirai.mixin;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.render.SkyboxRenderer;
import xyz.daikirai.render.furiah.module.ambience.FuriahAmbienceModule;

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

    @Redirect(method = "renderSky(FI)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/WorldClient;getSkyColor(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/util/Vec3;"),
            require = 0)
    private Vec3 clickgui$ambienceSky(WorldClient world, Entity view, float partialTicks) {
        return FuriahAmbienceModule.tintSky(world.getSkyColor(view, partialTicks));
    }

    @Redirect(method = {"renderClouds(FI)V", "renderCloudsFancy(FI)V"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/WorldClient;getCloudColour(F)Lnet/minecraft/util/Vec3;"),
            require = 0)
    private Vec3 clickgui$ambienceClouds(WorldClient world, float partialTicks) {
        return FuriahAmbienceModule.tintClouds(world.getCloudColour(partialTicks));
    }

    // The descriptor is pinned because RenderGlobal has more than one renderSky overload and the
    // annotation processor otherwise resolves the wrong one.
    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void clickgui$skybox(float partialTicks, int pass, CallbackInfo ci) {
        if (SkyboxRenderer.render(partialTicks)) {
            ci.cancel();
        }
    }
}
