package xyz.daikirai.module.render;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Island alarm for low health.
 *
 * <p>While enabled and the player's health is at or below {@code Min hearts}, the island shows the
 * warning icon with "Low Health"; {@code Red island} also tints the pill with the theme's error
 * colours. The module draws nothing by itself - it only feeds {@code Watermark}'s island.</p>
 */
public final class LowHealthModule extends Module {

    public static LowHealthModule instance;

    private SliderSetting minHearts;
    private ButtonSetting redIsland;

    public LowHealthModule(int key) {
        super("Low Health", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        minHearts = slider("Min hearts");
        redIsland = button("Red island");
    }

    /** Alert threshold in health points (the slider is in hearts, 1 heart = 2 HP). */
    public float threshold() {
        return minHearts == null ? 8.0F : (float) (minHearts.getInput() * 2.0D);
    }

    public boolean triggered(float health) {
        return health > 0.0F && health <= threshold();
    }

    public boolean redIsland() {
        return redIsland == null || redIsland.isToggled();
    }

    private static SliderSetting slider(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    private static ButtonSetting button(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
    }
}
