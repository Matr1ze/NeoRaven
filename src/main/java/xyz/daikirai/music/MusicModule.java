package xyz.daikirai.music;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * "Music Player" module: the bind opens the player window.
 *
 * <p>Same lifetime as the Onyx ClickGUI module — pressing the bind opens the screen and the module
 * switches itself back off, so it never shows up as a running module in the ClickGUI.</p>
 */
public final class MusicModule extends Module {

    public static MusicModule instance;

    /** Window size of the player, relative to the fitted size ("GUI Scale"). */
    public static SliderSetting guiScale;

    public MusicModule(int key) {
        super("Music Player", Category.other, key);
        instance = this;
    }

    @Override
    public void link() {
        Setting setting = ModuleManager.setting(this, "GUI Scale");
        guiScale = setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    /** The "GUI Scale" multiplier; {@code 1} keeps the window at its fitted size. */
    public static float guiScale() {
        return guiScale == null ? 1.0F : (float) guiScale.getInput();
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen instanceof MusicScreen) {
            disable();
            return;
        }
        MusicManager.get().boot();
        mc.displayGuiScreen(new MusicScreen());
        disable();
    }
}
