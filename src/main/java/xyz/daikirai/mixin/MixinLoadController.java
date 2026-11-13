package xyz.daikirai.mixin;

import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.LoaderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.StartupAnimation;

/** Reports lifecycle boundaries without treating temporary resource bars as the whole load. */
@Mixin(value = LoadController.class, remap = false)
public abstract class MixinLoadController {
    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V",
            at = @At("HEAD"), remap = false)
    private void neoraven$loadingStage(LoaderState state, Object[] arguments, CallbackInfo ci) {
        StartupAnimation.loadingStage(state.name());
    }
}
