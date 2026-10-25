package xyz.daikirai.module.render;

import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;

abstract class VisualModule extends Module {
    VisualModule(String name, int key) { super(name, Category.render, key); }

    protected SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        registerSetting(setting);
        return setting;
    }

    protected SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        registerSetting(setting);
        return setting;
    }

    protected ButtonSetting toggle(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        registerSetting(setting);
        return setting;
    }

    protected ColorSetting color(String name, int value) {
        return color(name, value, 0);
    }

    protected ColorSetting color(String name, int value, float hueShift) {
        ColorSetting setting = new ColorSetting(name, value, hueShift);
        registerSetting(setting);
        return setting;
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting instanceof ColorSetting) mc.displayGuiScreen(new ColorEditor(mc.currentScreen, (ColorSetting) setting));
    }
}
