package xyz.daikirai.module.render;

import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.module.ClickGuiModule;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.M3WavyProgress;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.module.setting.ButtonSetting;

/** The supplied M3 wavy circle, driven by the player's real Strength effect in game ticks. */
public final class StrengthTimerModule extends Module {
    public static StrengthTimerModule instance;
    private final M3WavyProgress indicator = new M3WavyProgress();
    private final UI ui = new UI();
    private SliderSetting size, thickness;
    private ButtonSetting showTime, followTheme;
    private Object world;
    private int totalTicks, remainingTicks, amplifier = -1;

    public StrengthTimerModule(int key) {
        super("Strength Timer", Category.render, key);
        instance = this;
    }

    @Override public void link() {
        size = (SliderSetting) ModuleManager.setting(this, "Size");
        thickness = (SliderSetting) ModuleManager.setting(this, "Thickness");
        showTime = (ButtonSetting) ModuleManager.setting(this, "Show time");
        followTheme = (ButtonSetting) ModuleManager.setting(this, "Follow theme");
    }
    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }

    private void reset() {
        indicator.close();
        totalTicks = remainingTicks = 0; amplifier = -1; world = null;
    }

    private void sample() {
        if (world != mc.theWorld) { reset(); world = mc.theWorld; }
        PotionEffect effect = mc.thePlayer == null ? null : mc.thePlayer.getActivePotionEffect(Potion.damageBoost);
        if (effect == null || effect.getDuration() <= 0 || mc.theWorld == null) {
            if (remainingTicks > 0) { reset(); world = mc.theWorld; }
            return;
        }
        int ticks = effect.getDuration(), level = effect.getAmplifier();
        // Keep one duration baseline through small server/client timing corrections. A slightly
        // higher remaining tick count is not a newly consumed potion and must not refill the ring.
        if (totalTicks == 0 || level != amplifier) {
            totalTicks = ticks;
            indicator.setProgress(1, false);
        } else {
            int refreshThreshold = Math.max(40, totalTicks / 10);
            if ((long)ticks - remainingTicks >= refreshThreshold) {
                // A substantial real extension starts a fresh period, with a smooth refill.
                totalTicks = ticks;
            } else {
                totalTicks = Math.max(totalTicks, ticks);
            }
        }
        remainingTicks = ticks; amplifier = level;
    }

    @SubscribeEvent public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        sample();
        if (Overlay.canRender()) draw(event.renderTickTime);
    }

    public static void renderHud() {
        if (instance != null && instance.isEnabled()) { instance.sample(); instance.draw(0); }
    }

    private void draw(float partialTicks) {
        if (remainingTicks <= 0 || totalTicks <= 0) return;
        ClickGuiModule.sync();
        ScaledResolution resolution = new ScaledResolution(mc);
        float side = Math.max(16, Math.min((float)size.getInput(),
                Math.min(resolution.getScaledWidth(), resolution.getScaledHeight()) - 16));
        float cx = resolution.getScaledWidth() / 2.0F, cy = resolution.getScaledHeight() / 2.0F;
        float ticks = Math.max(0, remainingTicks - Material.clamp(partialTicks, 0, 1));
        indicator.setProgress(ticks / Math.max(1, totalTicks));
        indicator.setThickness((float)thickness.getInput());
        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();
        ui.begin();
        try {
            indicator.setColors(Material.withAlpha(followTheme.isToggled() ? Theme.scheme().primary : 0xFF6750A4, ui.alpha()),
                    Material.withAlpha(followTheme.isToggled() ? Theme.scheme().secondaryContainer : 0xFFE8DEF8, ui.alpha()));
            Shader.unbind();
            indicator.draw(cx - side / 2, cy - side / 2, side, resolution.getScaleFactor());
            if (showTime.isToggled()) {
                int seconds = remainingTicks / 20 + (remainingTicks % 20 == 0 ? 0 : 1);
                String text = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
                // A small dark backing keeps the countdown readable over the vanilla crosshair.
                ui.push(); ui.translate(cx, cy); ui.scale(Math.min(1, side / 64), 0, 0);
                float textWidth = Typeface.BODY_MEDIUM.width(text);
                ui.rounded(-textWidth / 2 - 4, -8, textWidth + 8, 16, 6, 0xF0080C14);
                ui.text(Typeface.BODY_MEDIUM, text, 0, 0, 0xFFFFFFFF, UI.ALIGN_CENTER);
                ui.pop();
            }
        } finally { ui.end(); GlStateManager.popMatrix(); }
    }
}
