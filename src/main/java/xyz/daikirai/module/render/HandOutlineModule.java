package xyz.daikirai.module.render;

import net.minecraft.client.renderer.ItemRenderer;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.HandOutlineRenderer;

/**
 * Onyx Hand / Effects / Glint, exposed as a standalone first-person effect.
 *
 * <p>Upstream's surface is two groups: the glint itself ({@code Outline only}, {@code Outline},
 * {@code Glow}, {@code Fill}, {@code Padding}, {@code Smoothing}, {@code Speed}) and a motion trail
 * ({@code Trail}, {@code Trail fade}, {@code Trail intensity}). Both are here; {@code Outline only}
 * starts off like the original, so the default look is the full aurora glint instead of a bare line.
 */
public final class HandOutlineModule extends VisualModule {
    public static HandOutlineModule instance;
    public final ColorSetting primary = color("Color", -3105793);
    public final ColorSetting secondary = color("Second color", -6559745, 14);
    public final SliderSetting alpha = slider("Opacity", 100, 0, 100, 5, "%");
    public final ButtonSetting outlineOnly = toggle("Outline only", false);
    public final SliderSetting outline = slider("Outline", 2, 0, 8, .5, " px");
    public final SliderSetting glow = slider("Glow", 125, 0, 200, 5, "%");
    public final SliderSetting fill = slider("Fill", 35, 0, 200, 5, "%");
    public final SliderSetting padding = slider("Padding", 16, 0, 20, 1, " px");
    public final SliderSetting smoothing = slider("Smoothing", 30, 0, 32, .5, " px");
    public final SliderSetting speed = slider("Speed", 1, .1, 5, .05, "x");
    public final ButtonSetting trail = toggle("Trail", true);
    public final SliderSetting trailFade = slider("Trail fade", 91, 55, 98, 1, "%");
    public final SliderSetting trailIntensity = slider("Trail intensity", 85, 10, 150, 1, "%");
    private final HandOutlineRenderer renderer = new HandOutlineRenderer();

    public HandOutlineModule(int key) { super("HandOutline", key); instance = this; }
    public void render(ItemRenderer itemRenderer, float partialTicks) {
        if (mc.theWorld != null && mc.thePlayer != null && mc.gameSettings.thirdPersonView == 0)
            renderer.render(itemRenderer, partialTicks, this);
    }
    @Override public void onEnable() { renderer.reset(); }
    @Override public void onDisable() { renderer.dispose(); }
}
