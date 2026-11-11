package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.module.setting.ColorSetting;

/**
 * "Onyx ClickGUI" module: opens the ported Material You interface and exposes the theme seeds
 * that Onyx kept on its own ClickGUI module (accent, background tint, scale, frosted glass).
 */
public final class OnyxClickGuiModule extends Module {

    public static OnyxClickGuiModule instance;

    public static ColorSetting accent;
    public static SliderSetting backgroundTint;
    public static SliderSetting interfaceScale;
    public static ButtonSetting followGuiScale;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting liquidGlass;
    public static ButtonSetting resetWindow;
    public static SliderSetting surfaceTheme, frostOpacity, liquidOpacity, liquidFrost;
    public static ButtonSetting colouredFrost, matteLiquid;

    public OnyxClickGuiModule(int key) {
        super("Onyx ClickGUI", Category.client, key);
        instance = this;
    }

    @Override
    public void link() {
        accent = colour("Accent");
        backgroundTint = slider("Background tint");
        interfaceScale = slider("Scale");
        followGuiScale = button("Follow GUI scale");
        frostedGlass = button("Frosted glass");
        liquidGlass = button("Liquid glass");
        resetWindow = button("Reset window");
        surfaceTheme = slider("Surface theme");
        frostOpacity = slider("Frost opacity");
        liquidOpacity = slider("Liquid opacity");
        liquidFrost = slider("Liquid frost amount");
        colouredFrost = button("Coloured frost");
        matteLiquid = button("Matte liquid glass");
        Theme.loadPreferences();
        accent.setAppearance(Theme.accent(), false);
        surfaceTheme.setValue(Theme.surfaceTheme());
        frostOpacity.setValue(Theme.frostOpacity * 100.0F);
        liquidOpacity.setValue(Theme.liquidOpacity * 100.0F);
        liquidFrost.setValue(Theme.liquidFrost * 100.0F);
        colouredFrost.set(Theme.colouredFrost);
        matteLiquid.set(Theme.matteLiquid);
        frostedGlass.set(Theme.frost());
        liquidGlass.set(Theme.liquidGlass());
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

    private static ColorSetting colour(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ColorSetting ? (ColorSetting) setting : null;
    }

    /** Pushes the current settings into the theme; called every frame while the GUI is open. */
    public static void sync() {
        if (surfaceTheme != null) Theme.surfaceTheme((int) surfaceTheme.getInput());
        if (frostOpacity != null) Theme.frostOpacity = (float) frostOpacity.getInput() / 100.0F;
        if (liquidOpacity != null) Theme.liquidOpacity = (float) liquidOpacity.getInput() / 100.0F;
        if (liquidFrost != null) Theme.liquidFrost = (float) liquidFrost.getInput() / 100.0F;
        if (colouredFrost != null) Theme.colouredFrost = colouredFrost.isToggled();
        if (matteLiquid != null) Theme.matteLiquid = matteLiquid.isToggled();
        if (accent != null) Theme.accent(accent.color());
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
        if (liquidGlass != null) {
            Theme.liquidGlass(liquidGlass.isToggled());
        }
        Theme.savePreferencesIfChanged();
    }

    @Override
    public void guiButtonToggled(xyz.daikirai.module.setting.ButtonSetting button) {
        if (button == resetWindow) {
            Panel.resetOffset();
        } else {
            super.guiButtonToggled(button);
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
