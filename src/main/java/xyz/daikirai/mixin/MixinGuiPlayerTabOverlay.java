package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.hud.TabListData;
import xyz.daikirai.module.render.WatermarkModule;

@Mixin(GuiPlayerTabOverlay.class)
public class MixinGuiPlayerTabOverlay implements TabListData {
    @Shadow private IChatComponent header;
    @Shadow private IChatComponent footer;

    @Override
    public IChatComponent onyx$header() {
        return header;
    }

    @Override
    public IChatComponent onyx$footer() {
        return footer;
    }

    @Inject(method = "renderPlayerlist", at = @At("HEAD"), cancellable = true)
    private void onyx$replacePlayerList(int width, Scoreboard scoreboard, ScoreObjective objective,
                                      CallbackInfo ci) {
        if (WatermarkModule.replacesTablist()) {
            ci.cancel();
        }
    }
}
