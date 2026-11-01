package xyz.daikirai.music;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;

/**
 * "Music Player" module: the bind opens the player window.
 *
 * <p>Same lifetime as the Onyx ClickGUI module — pressing the bind opens the screen and the module
 * switches itself back off, so it never shows up as a running module in the ClickGUI.</p>
 */
public final class MusicModule extends Module {

    public static MusicModule instance;

    public MusicModule(int key) {
        super("Music Player", Category.other, key);
        instance = this;
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
