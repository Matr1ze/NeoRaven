package xyz.daikirai.module.render;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Port of <a href="https://github.com/llamalad7/BetterChat">Better Chat 1.8.9</a> (8 files, one
 * mixin on {@code GuiNewChat}): chat lines fade and slide in instead of appearing instantly, the
 * chat background can be made transparent, and the whole chat can be moved with an x/y offset.
 *
 * <p>The original kept its state in a Forge config file; here the four options are ordinary module
 * settings and {@link xyz.daikirai.mixin.MixinGuiNewChat} reads them from {@link #instance}. The
 * mixin's numbers (0.004 per millisecond, the cubic ease, the 9 px slide, the alpha scaling and the
 * {@code getChatComponent} offset flips) are copied verbatim.</p>
 */
public final class BetterChatModule extends Module {

    public static BetterChatModule instance;

    private static ButtonSetting smooth, clear;
    private static SliderSetting xOffset, yOffset;

    public BetterChatModule(int key) {
        super("Better Chat", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        smooth = (ButtonSetting) ModuleManager.setting(this, "Smooth");
        clear = (ButtonSetting) ModuleManager.setting(this, "Clear");
        xOffset = (SliderSetting) ModuleManager.setting(this, "X offset");
        yOffset = (SliderSetting) ModuleManager.setting(this, "Y offset");
    }

    /** True while the chat should animate its lines. */
    public static boolean smooth() {
        return instance != null && instance.isEnabled() && smooth != null && smooth.isToggled();
    }

    /** True when the dark chat backdrop must not be drawn. */
    public static boolean clear() {
        return instance != null && instance.isEnabled() && clear != null && clear.isToggled();
    }

    public static float xOffset() {
        return xOffset == null ? 0.0F : (float) xOffset.getInput();
    }

    public static float yOffset() {
        return yOffset == null ? 0.0F : (float) yOffset.getInput();
    }

    /** The mixin still needs the offsets when the module is off, so they must read 0 then. */
    public static float appliedXOffset() {
        return instance != null && instance.isEnabled() ? xOffset() : 0.0F;
    }

    public static float appliedYOffset() {
        return instance != null && instance.isEnabled() ? yOffset() : 0.0F;
    }
}
