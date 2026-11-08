package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.menu.MenuTheme;
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
    public static SliderSetting appearance;
    public static ButtonSetting hudTextColor;
    public static SliderSetting language;
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
        appearance = slider("Appearance");
        if (appearance != null) {
            // The shared preference is stored with the other menu appearance options.
            appearance.notSaved();
        }
        hudTextColor = button("HUD text color");
        language = slider("Language");
        if (language != null) {
            // Persisted through ClientConfig as well, so a fresh profile keeps the choice.
            language.notSaved();
        }
        followGuiScale = button("Follow GUI scale");
        frostedGlass = button("Frosted glass");
        resetWindow = button("Reset window");
        syncModeFromMenu(MenuTheme.dark());
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

    /** Mirrors the saved language into the setting, after the config has been read. */
    public static void refreshLanguageSetting() {
        if (language != null) {
            double value = xyz.daikirai.util.Lang.chinese() ? 0 : 1;
            language.value = value;
            language.setVisualInput(value);
        }
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
        if (language != null) {
            String wanted = language.getInput() < 0.5D
                    ? xyz.daikirai.util.Lang.CHINESE : xyz.daikirai.util.Lang.ENGLISH;
            if (!wanted.equals(xyz.daikirai.util.Lang.locale())) {
                xyz.daikirai.util.Lang.setLocale(wanted);
                xyz.daikirai.client.ClientConfig.save();
            }
        }
        if (followGuiScale != null) {
            Theme.followGuiScale(followGuiScale.isToggled());
        }
        if (frostedGlass != null) {
            Theme.frost(frostedGlass.isToggled());
        }
        if (hudTextColor != null) {
            Theme.hudTextColor(hudTextColor.isToggled());
        }
        if (appearance != null) {
            int mode = appearanceMode();
            // 0 = Dark, 1 = Light, 2 = Translucent (bright glass over the dark ink roles).
            Theme.translucent(mode == 2);
            MenuTheme.setDark(mode != 1);
            MenuTheme.setGlass(mode == 2);
        }
        syncModeFromMenu(MenuTheme.dark());
    }

    /** The Appearance row: 0 Dark, 1 Light, 2 Translucent. */
    private static int appearanceMode() {
        return Material.clamp((int) Math.round(appearance.getInput()), 0, 2);
    }

    /** Keeps the Theme-page switch in step with the shared preference and the Background screen. */
    public static void syncModeFromMenu(boolean dark) {
        if (appearance != null) {
            double wanted = MenuTheme.glass() ? 2.0D : (dark ? 0.0D : 1.0D);
            if (appearance.getInput() != wanted) {
                // Mirror the shared preference without marking the active profile as edited.
                appearance.value = wanted;
                appearance.input = wanted;
            }
        }
    }

    @Override
    public void guiButtonToggled(xyz.daikirai.module.setting.ButtonSetting button) {
        if (button == resetWindow) {
            Panel.resetOffset();
            xyz.daikirai.client.ClientConfig.save();
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
