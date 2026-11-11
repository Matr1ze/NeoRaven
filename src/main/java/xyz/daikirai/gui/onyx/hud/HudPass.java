package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.module.render.ArrayListModule;
import xyz.daikirai.module.render.ClockModule;
import xyz.daikirai.module.render.CustomGuiModule;
import xyz.daikirai.module.render.KeybindsModule;
import xyz.daikirai.module.render.NotificationsModule;
import xyz.daikirai.module.render.PotionHudModule;
import xyz.daikirai.module.render.TargetHudModule;
import xyz.daikirai.module.render.WatermarkModule;

/**
 * Draws the client's HUD elements once, from inside a screen.
 *
 * <p>The HUD normally renders on {@code RenderTickEvent.END}, which Forge fires <em>after</em> the
 * current screen, so every HUD element hides itself while a screen is open. The ClickGUI and the
 * music player both grow out of the Dynamic Island, and having the whole HUD disappear on the first
 * frame of a 420ms morph made the transition feel like a cut. Those screens call this pass between
 * their world backdrop (the scrim) and their morphing shell: the HUD stays where it was while the
 * shell grows over it, and is simply covered by the window once it is full size.</p>
 *
 * <p>Only the elements that would otherwise blink out are included. The island itself is left out
 * on purpose - the morphing screen already draws it as the animation's starting point.</p>
 */
public final class HudPass {

    private HudPass() {
    }

    /** @param partialTicks frame interpolation, needed by the target card's health smoothing */
    public static void render(float partialTicks) {
        if (Minecraft.getMinecraft().theWorld == null) {
            return;
        }
        OnyxClickGuiModule.sync();
        ArrayListModule.renderHud();
        ClockModule.renderHud();
        CustomGuiModule.renderHud();
        KeybindsModule.renderHud();
        PotionHudModule.renderHud();
        TargetHudModule.renderHud(partialTicks);
        NotificationsModule.renderHud();
        WatermarkModule.renderHud(false, true);
    }
}
