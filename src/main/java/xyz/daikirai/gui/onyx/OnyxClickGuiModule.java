package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * "Onyx ClickGUI" module: opens the ported Material You interface and exposes the theme seeds
 * that Onyx kept on its own ClickGUI module (accent, background tint, scale, frosted glass).
 */
public final class OnyxClickGuiModule extends Module {

    public static OnyxClickGuiModule instance;

    public static SliderSetting accent;
    public static SliderSetting backgroundTint;
    public static SliderSetting interfaceScale;
    public static ButtonSetting followGuiScale;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting resetWindow;

    public OnyxClickGuiModule(int key) {
        super("Onyx ClickGUI", Category.client, key);
        instance = this;
    }

    @Override
    public void link() {
        accent = slider("Accent");
        backgroundTint = slider("Background tint");
        interfaceScale = slider("Scale");
        followGuiScale = button("Follow GUI scale");
        frostedGlass = button("Frosted glass");
        resetWindow = button("Reset window");
        sync();
    }

    private static SliderSetting slider(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    private static ButtonSetting button(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
    }

    /** Pushes the current settings into the theme; called every frame while the GUI is open. */
    public static void sync() {
        if (accent != null) {
            int index = (int) accent.getInput();
            index = Material.clamp(index, 0, Theme.ACCENTS.length - 1);
            Theme.accent(Theme.ACCENTS[index]);
        }
        if (backgroundTint != null) {
            Theme.tint((float) (backgroundTint.getInput() / 100.0D));
        }
        if (interfaceScale != null) {
            Theme.scale((float) interfaceScale.getInput());
        }
        if (followGuiScale != null) {
            Theme.followGuiScale(followGuiScale.isToggled());
        }
        if (frostedGlass != null) {
            Theme.frost(frostedGlass.isToggled());
        }
    }

    @Override
    public void guiButtonToggled(xyz.daikirai.module.setting.ButtonSetting button) {
        if (button == resetWindow) {
            Panel.resetOffset();
        }
    }

    @Override
    public void guiUpdate() {
        sync();
    }

    @Override
    public void onEnable() {
        if (mc.currentScreen instanceof OnyxClickGui) {
            disable();
            return;
        }
        sync();
        mc.displayGuiScreen(new OnyxClickGui());
        disable();
    }
}
