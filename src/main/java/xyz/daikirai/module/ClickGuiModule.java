package xyz.daikirai.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.client.Chat;
import xyz.daikirai.gui.CategoryComponent;
import xyz.daikirai.gui.ClickGui;
import xyz.daikirai.gui.Component;
import xyz.daikirai.gui.ModuleComponent;
import xyz.daikirai.gui.onyx.OnyxClickGui;
import xyz.daikirai.gui.onyx.Panel;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.menu.MenuTheme;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.samsara.SamsaraClickGui;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.GuiColor;
import xyz.daikirai.util.RenderUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** One Click GUI launcher and profile entry for the three interfaces. */
public final class ClickGuiModule extends Module {
    public static final String NAME = "Click GUI";
    public static ClickGuiModule instance;

    private enum Style { ONYX, CLASSIC, SAMSARA }
    private static final Style[] STYLE_ORDER = Style.values();

    private final EnumMap<Style, List<Setting>> styleSettings = new EnumMap<>(Style.class);
    private final EnumMap<Style, ArrayList<Setting>> visibleSettings = new EnumMap<>(Style.class);
    private final List<ArrayList<Setting>> samsaraSettings = new ArrayList<>();
    private SliderSetting style, samsaraLayout, samsaraInterface, samsaraScale;
    private ButtonSetting resetSamsara;
    private SamsaraClickGui samsaraScreen;

    public static SliderSetting accent;
    public static SliderSetting backgroundTint;
    public static SliderSetting backgroundOpacity;
    public static SliderSetting blur;
    public static SliderSetting interfaceScale;
    public static SliderSetting appearance;
    public static ButtonSetting hudTextColor;
    public static ButtonSetting followGuiScale;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting liquidGlass;
    public static ButtonSetting toggleSound;
    /** Mode row: 0 = the synthesized tick, 1 = the vanilla lever click. */
    public static SliderSetting soundStyle;
    public static ButtonSetting resetWindow;

    public enum Scale {
        Small(1), Normal(2), Large(3);
        public final int guiScale;

        Scale(int guiScale) {
            this.guiScale = guiScale;
        }
    }

    public static GuiColor watermarkColor = new GuiColor(57, 146, 229, 255);
    public static GuiColor closedOutlineLeft = new GuiColor(81, 99, 149, 255);
    public static GuiColor closedOutlineRight = new GuiColor(97, 67, 133, 255);
    public static GuiColor categoryNameColor = new GuiColor(220, 220, 220, 255);
    public static GuiColor red = new GuiColor(250, 95, 85, 255);
    public static GuiColor green = new GuiColor(135, 238, 144, 255);
    public static GuiColor enabledColor = new GuiColor(24, 154, 255, 255);
    public static GuiColor disabledColor = new GuiColor(192, 192, 192, 255);
    public static GuiColor scriptColor = new GuiColor(116, 202, 255, 255);
    public static GuiColor errorColor = new GuiColor(255, 80, 80, 255);
    public static GuiColor darkFill = new GuiColor(0, 0, 0, 110);
    public static GuiColor lightFill = new GuiColor(0, 0, 0, 130);
    public static GuiColor categoryFill = darkFill;
    public static GuiColor rainbowLeft = new GuiColor(2L, 0L, 130);
    public static GuiColor rainbowRight = new GuiColor(2L, 700L, 130);
    public static GuiColor whiteOutline = new GuiColor(255, 255, 255, 150);
    public static GuiColor outlineLeft = rainbowLeft;
    public static GuiColor outlineRight = rainbowRight;
    public static Scale guiScale = Scale.Normal;
    public static float blurStrength = 1.7f;
    public static int previousGuiScale;
    private static boolean classicScaleApplied;
    public static boolean movementActive;

    public static SliderSetting scale, backgroundBlur, scrollSpeed;
    public static ButtonSetting allowMovement, allowRotating, darkBackground, hidePlayerModel, hideWatermark, liteMode,
            rainbowOutlines, resetPositions, saveCategoryPositions;


    public ClickGuiModule(int key) {
        super(NAME, Category.client, key);
        instance = this;
        for (Style group : Style.values()) {
            styleSettings.put(group, new ArrayList<>());
        }
    }

    void registerStyleSetting(String group, Setting setting) {
        styleSettings.get(Style.valueOf(group.toUpperCase(Locale.ROOT))).add(setting);
        registerSetting(setting);
    }

    private Setting require(Style group, String name) {
        for (Setting setting : styleSettings.get(group)) {
            if (setting.getName().equals(name)) return setting;
        }
        throw new IllegalStateException(group + " is missing " + name);
    }

    private Style selectedStyle() {
        return STYLE_ORDER[(int) style.getInput()];
    }

    @Override
    public ArrayList<Setting> getSettings() {
        if (style == null) return settings;
        if (selectedStyle() == Style.SAMSARA) return samsaraSettings.get(samsaraLayout());
        return visibleSettings.get(selectedStyle());
    }

    public List<Setting> classicSettings() {
        return visibleSettings.get(Style.CLASSIC);
    }

    @Override
    public ArrayList<Setting> persistedSettings() {
        return settings;
    }

    public int samsaraLayout() { return (int) samsaraLayout.getInput(); }
    public boolean samsaraGlass() { return samsaraLayout() == 1 && samsaraInterface.getInput() == 0; }
    public float samsaraScale() { return (float) samsaraScale.getInput(); }

    public String profileSettingKey(Setting setting) {
        for (Style group : Style.values()) {
            if (styleSettings.get(group).contains(setting)) {
                return group.name() + "." + setting.getName();
            }
        }
        return setting.getName();
    }

    /** Folds both pre-unification rows into one profile record without losing either Scale. */
    public void migrateProfile(JsonArray modules) {
        JsonObject classic = null;
        JsonObject onyx = null;
        for (JsonElement entry : modules) {
            JsonObject row = entry.getAsJsonObject();
            if (!row.has("name") || row.get("name").isJsonNull()) continue;
            String name = row.get("name").getAsString();
            if (NAME.equals(name)) return;
            if ("Gui".equals(name)) classic = row;
            if ("Onyx ClickGUI".equals(name)) onyx = row;
        }
        if (classic == null && onyx == null) return;

        boolean useClassic = classic != null && (onyx == null
                || binding(onyx) == 0 && binding(classic) != 0);
        JsonObject source = useClassic ? classic : onyx;
        JsonObject merged = new JsonObject();
        merged.addProperty("name", NAME);
        merged.addProperty("Style", useClassic ? 1 : 0);
        merged.addProperty("keybind", binding(source));
        for (String property : new String[] {"hidden", "holdBind"}) {
            if (source.has(property)) merged.add(property, source.get(property));
        }
        copyLegacySettings(classic, Style.CLASSIC, merged);
        copyLegacySettings(onyx, Style.ONYX, merged);
        modules.add(merged);
    }

    private void copyLegacySettings(JsonObject source, Style group, JsonObject merged) {
        if (source == null) return;
        for (Setting row : styleSettings.get(group)) {
            if (source.has(row.getName())) merged.add(profileSettingKey(row), source.get(row.getName()));
        }
    }

    private static int binding(JsonObject source) {
        JsonElement key = source.get("keybind");
        return key != null && key.isJsonPrimitive() && key.getAsJsonPrimitive().isNumber() ? key.getAsInt() : 0;
    }

    @Override
    public void link() {
        accent = (SliderSetting) require(Style.ONYX, "Accent");
        backgroundTint = (SliderSetting) require(Style.ONYX, "Background tint");
        backgroundOpacity = (SliderSetting) require(Style.ONYX, "Background opacity");
        blur = (SliderSetting) require(Style.ONYX, "Blur");
        interfaceScale = (SliderSetting) require(Style.ONYX, "Scale");
        appearance = (SliderSetting) require(Style.ONYX, "Appearance");
        appearance.notSaved();
        hudTextColor = (ButtonSetting) require(Style.ONYX, "HUD text color");
        followGuiScale = (ButtonSetting) require(Style.ONYX, "Follow GUI scale");
        frostedGlass = (ButtonSetting) require(Style.ONYX, "Frosted glass");
        liquidGlass = (ButtonSetting) require(Style.ONYX, "Liquid Glass");
        toggleSound = (ButtonSetting) require(Style.ONYX, "Toggle sound");
        soundStyle = (SliderSetting) require(Style.ONYX, "Sound style");
        resetWindow = (ButtonSetting) require(Style.ONYX, "Reset window");

        scale = (SliderSetting) require(Style.CLASSIC, "Scale");
        backgroundBlur = (SliderSetting) require(Style.CLASSIC, "Background blur");
        scrollSpeed = (SliderSetting) require(Style.CLASSIC, "Scroll speed");
        allowMovement = (ButtonSetting) require(Style.CLASSIC, "Allow movement");
        allowRotating = (ButtonSetting) require(Style.CLASSIC, "Allow rotating");
        darkBackground = (ButtonSetting) require(Style.CLASSIC, "Dark background");
        hidePlayerModel = (ButtonSetting) require(Style.CLASSIC, "Hide player model");
        hideWatermark = (ButtonSetting) require(Style.CLASSIC, "Hide watermark");
        liteMode = (ButtonSetting) require(Style.CLASSIC, "Lite mode");
        rainbowOutlines = (ButtonSetting) require(Style.CLASSIC, "Rainbow outlines");
        resetPositions = (ButtonSetting) require(Style.CLASSIC, "Reset positions");
        saveCategoryPositions = (ButtonSetting) require(Style.CLASSIC, "Save category positions");
        saveCategoryPositions.notSaved();

        style = (SliderSetting) settings.get(0);
        samsaraLayout = (SliderSetting) require(Style.SAMSARA, "Samsara layout");
        samsaraInterface = (SliderSetting) require(Style.SAMSARA, "Interface");
        samsaraScale = (SliderSetting) require(Style.SAMSARA, "Scale");
        resetSamsara = (ButtonSetting) require(Style.SAMSARA, "Reset Samsara layout");
        for (Style group : Style.values()) {
            ArrayList<Setting> visible = new ArrayList<>();
            visible.add(style);
            visible.addAll(styleSettings.get(group));
            visibleSettings.put(group, visible);
        }
        for (int layout = 0; layout < samsaraLayout.options.length; layout++) {
            ArrayList<Setting> visible = new ArrayList<>(visibleSettings.get(Style.SAMSARA));
            // These are shared theme settings. Keep their existing profile ownership.
            visible.add(2, accent);
            visible.add(3, appearance);
            visible.add(4, liquidGlass);
            if (layout != 1) visible.remove(samsaraInterface);
            samsaraSettings.add(visible);
        }
        syncModeFromMenu(MenuTheme.dark());
        sync();
    }


    @Override
    public void guiButtonToggled(ButtonSetting b) {
        if (b == liquidGlass) {
            syncLiquidGlass();
            return;
        }
        if (b == resetWindow) {
            Panel.resetOffset();
            return;
        }
        if (b == resetSamsara) {
            if (samsaraScreen == null) samsaraScreen = new SamsaraClickGui(this);
            samsaraScreen.resetLayouts();
            return;
        }
        if (b == rainbowOutlines) {
            if (rainbowOutlines.isToggled()) {
                outlineLeft = rainbowLeft;
                outlineRight = rainbowRight;
                return;
            }
            outlineLeft = outlineRight = whiteOutline;
        } else if (b == darkBackground) {
            categoryFill = darkBackground.isToggled() ? darkFill : lightFill;
        } else if (b == resetPositions) {
            resetPositions();
        } else if (b == saveCategoryPositions) {
            xyz.daikirai.client.ClientConfig.save();
        }
    }

    /**
     * Whether toggling a module should play the click. Read straight from the setting (not gated on
     * this module being enabled) so the sound works from a keybind, the ClickGUI or the title screen.
     */
    public static boolean toggleSound() {
        return toggleSound == null || toggleSound.isToggled();
    }

    /**
     * Selected toggle sample set: 0 = tick, 1 = lever, 2 = smooth, 3 = celestial, 4 = nursultan.
     * The order is the {@code Sound style} row's option order in {@code modules.json}.
     */
    public static int soundStyle() {
        return soundStyle == null ? 0 : (int) Math.round(soundStyle.getInput());
    }

    /** {@code true} when the lever click is selected instead of a sample set. */
    public static boolean leverSound() {
        return soundStyle() == 1;
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
        if (backgroundOpacity != null) {
            Theme.panelOpacity((float) (backgroundOpacity.getInput() / 100.0D));
        }
        if (blur != null) {
            Theme.blur((float) (blur.getInput() / 100.0D));
            Blur.radius(Blur.MAX_RADIUS * Theme.blur());
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
        syncLiquidGlass();
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

    private static void syncLiquidGlass() {
        if (liquidGlass != null && Theme.liquidGlass() != liquidGlass.isToggled()) {
            Theme.liquidGlass(liquidGlass.isToggled());
            Blur.request();
        }
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

    public static void restoreScale() {
        if (!classicScaleApplied) return;
        classicScaleApplied = false;
        mc.gameSettings.guiScale = previousGuiScale;
        RenderUtils.updateResolution();
    }

    public static void updateMovement() {
        if (!(mc.currentScreen instanceof ClickGui) || mc.thePlayer == null) {
            movementActive = false;
            return;
        }
        boolean consoleClosed = !CommandLineModule.open;
        boolean move = allowMovement.isToggled() && consoleClosed;
        boolean rotate = allowRotating.isToggled() && consoleClosed;
        if (move) {
            setMoveKeys(false);
            if (ModuleManager.enabled("AntiAFK") != null) {
                xyz.daikirai.module.player.AntiAFKModule.pressKeys();
            }
        } else if (movementActive) {
            setMoveKeys(true);
        }
        if (rotate) {
            rotate();
        }
        movementActive = move;
    }

    private static void setMoveKeys(boolean release) {
        KeyBinding[] keys = {mc.gameSettings.keyBindForward, mc.gameSettings.keyBindBack, mc.gameSettings.keyBindRight,
                mc.gameSettings.keyBindLeft, mc.gameSettings.keyBindJump, mc.gameSettings.keyBindSneak};
        for (KeyBinding k : keys) {
            KeyBinding.setKeyBindState(k.getKeyCode(), !release && GameSettings.isKeyDown(k));
        }
    }

    private static void rotate() {
        float yaw = 0.0f, pitch = 0.0f;
        if (Keyboard.isKeyDown(205)) yaw += 8.0f;
        if (Keyboard.isKeyDown(203)) yaw -= 8.0f;
        if (Keyboard.isKeyDown(200)) pitch -= 5.0f;
        if (Keyboard.isKeyDown(208)) pitch += 5.0f;
        if (yaw != 0.0f) {
            yaw = (float) ((double) yaw + (Math.random() - 0.5));
            mc.thePlayer.rotationYaw += yaw;
        }
        if (pitch != 0.0f) {
            pitch = (float) ((double) pitch + (Math.random() - 0.5));
            mc.thePlayer.rotationPitch = MathHelper.clamp_float(mc.thePlayer.rotationPitch + pitch, -90.0f, 90.0f);
        }
    }

    public static void applyScale() {
        classicScaleApplied = true;
        previousGuiScale = mc.gameSettings.guiScale;
        if (previousGuiScale != guiScale.guiScale) {
            mc.gameSettings.guiScale = guiScale.guiScale;
        }
        RenderUtils.updateResolution();
    }

    public static void resetPositions() {
        float y = 5.0f;
        for (CategoryComponent c : ClickGui.categories) {
            for (Component comp : c.modules) {
                ModuleComponent m = (ModuleComponent) comp;
                if (!m.opened) continue;
                m.toggleOpen();
            }
            c.setX(5.0f);
            c.setY(y);
            c.setOpened(false);
            y += 20.0f;
        }
    }


    @Override
    public void guiUpdate() {
        sync();
        guiScale = Scale.values()[(int) scale.getInput()];
        blurStrength = (float) (3.4 * (backgroundBlur.getInput() / 100.0));
        updateMovement();
    }

    private Style screenStyle(GuiScreen current) {
        if (current instanceof ClickGui) return Style.CLASSIC;
        if (current instanceof OnyxClickGui) return Style.ONYX;
        if (current instanceof SamsaraClickGui) return Style.SAMSARA;
        return null;
    }

    public void updateScreenStyle(GuiScreen current) {
        if (current == mc.currentScreen && screenStyle(current) != selectedStyle()
                && !Mouse.isButtonDown(0)) {
            openSelected();
        }
    }

    @Override
    public boolean canBeEnabled() {
        if (screenStyle(mc.currentScreen) != null) return false;
        if (selectedStyle() == Style.CLASSIC && xyz.daikirai.module.movement.TimerModule.slowed()
                && xyz.daikirai.module.movement.TimerModule.instance.getKeycode() != 0) {
            Chat.moduleMessage(this, "&cDisable timer to open GUI!");
            return false;
        }
        return true;
    }

    @Override
    public void onEnable() {
        try {
            openSelected();
        } finally {
            disable();
        }
    }

    private void openSelected() {
        switch (selectedStyle()) {
            case CLASSIC:
                if (mc.theWorld != null && xyz.daikirai.module.movement.TimerModule.speed > 0.0F) {
                    xyz.daikirai.util.MoveUtils.resetTimer();
                }
                guiUpdate();
                applyScale();
                ClickGui.instance.initMain();
                mc.displayGuiScreen(ClickGui.instance);
                break;
            case SAMSARA:
                if (samsaraScreen == null) samsaraScreen = new SamsaraClickGui(this);
                mc.displayGuiScreen(samsaraScreen);
                break;
            default:
                sync();
                mc.displayGuiScreen(new OnyxClickGui());
        }
    }
}
