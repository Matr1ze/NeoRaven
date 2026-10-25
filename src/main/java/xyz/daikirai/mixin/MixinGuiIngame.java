package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.render.CustomGuiModule;

/**
 * Cancels the vanilla sidebar while the ported scoreboard module is enabled.
 *
 * <p>1.8.9 Forge has no {@code RenderGameOverlayEvent} element for the scoreboard (it is drawn by
 * {@code GuiIngame.renderScoreboard} from inside the overlay), so the port replaces it here.</p>
 */
@Mixin(GuiIngame.class)
public class MixinGuiIngame {

    @Inject(method = "renderScoreboard", at = @At("HEAD"), cancellable = true)
    private void clickgui$renderScoreboard(ScoreObjective objective, ScaledResolution scaledResolution,
                                           CallbackInfo ci) {
        if (CustomGuiModule.replacesScoreboard()) {
            ci.cancel();
        }
    }
}
