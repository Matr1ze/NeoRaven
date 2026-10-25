package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.ArrayListPositionEditor;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.ArrayListHud;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Material 3 Expressive module list, ported from OpenOnyx's {@code StyleModule} ("Array List").
 *
 * <p>Onyx listed every enabled module with an accent bar, a pill or plain text, coloured from the
 * theme, a static colour, a gradient, a pulse, a rainbow or the module's category, and let the
 * rows slide/fade/scale in. This port keeps all of that and adds the pieces the other ported HUDs
 * share: a scale slider, percentage position with an "Edit position" screen, chat dragging, and a
 * frosted-glass backdrop built on the shared blur pipeline.</p>
 */
public final class ArrayListModule extends Module {

    public static ArrayListModule instance;

    private static final ArrayListHud HUD = new ArrayListHud();
    private static Object listener;

    private final SliderSetting style = mode("Style", 0, "Accent Bar", "Pills", "Text");
    private final SliderSetting animation = mode("Animation", 2, "Slide", "Fade", "Slide + Fade", "Scale");
    private final SliderSetting sort = mode("Sort", 0, "Longest first", "Shortest first", "A-Z");
    private final SliderSetting colors = mode("Colors", 0, "Accent", "Static", "Fade", "Gradient", "Rainbow", "Category");
    private final ColorSetting color = colorSetting("Color", 0xFF7C4DFF, 0);
    private final ColorSetting firstColor = colorSetting("First Color", 0xFF7C4DFF, 0);
    private final ColorSetting secondColor = colorSetting("Second Color", 0xFF7C4DFF, 60);
    private final SliderSetting colorSpeed = slider("Color speed", 1, 0.1, 5, 0.1, "x");
    private final SliderSetting colorSpread = slider("Color spread", 12, 0, 60, 1, " deg");
    private final ButtonSetting suffix = flag("Suffix", true);
    private final ButtonSetting lowercase = flag("Lowercase", false);
    private final ButtonSetting showCombat = flag("Show combat", true);
    private final ButtonSetting showMovement = flag("Show movement", true);
    private final ButtonSetting showPlayer = flag("Show player", true);
    private final ButtonSetting showRender = flag("Show render", true);
    private final ButtonSetting showOther = flag("Show other categories", false);
    private final ButtonSetting background = flag("Background", true);
    /**
     * How the accent-bar style paints its backdrop: {@code Card} is one block behind the whole list
     * (as wide as the longest row), {@code Rows} gives every row its own block that hugs its text.
     */
    private final SliderSetting backgroundStyle = mode("Background style", 1, "Card", "Rows");
    private final ButtonSetting blur = flag("Blur", true);
    private final ButtonSetting textShadow = flag("Text shadow", true);
    private final ButtonSetting outline = flag("Outline", false);
    private final SliderSetting scale = slider("Scale", 1, 0.5, 2, 0.05, "x");
    private final SliderSetting rowSpacing = slider("Row spacing", 0, 0, 6, 0.5, "");
    private final SliderSetting animationSpeed = slider("Animation speed", 1, 0.25, 3, 0.05, "x");
    private final SliderSetting positionX = slider("Position X", 100, 0, 100, 0.01, "%");
    /** Right column, just under the watermark island, which owns the corner by default. */
    private final SliderSetting positionY = slider("Position Y", 12, 0, 100, 0.01, "%");
    private final ButtonSetting editPosition = button("Edit position");

    public ArrayListModule(int key) {
        super("ArrayList", Category.render, key);
        instance = this;
    }

    // ------------------------------------------------------------------ //
    // Settings
    // ------------------------------------------------------------------ //

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        registerSetting(setting);
        return setting;
    }

    private SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        registerSetting(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        registerSetting(setting);
        return setting;
    }

    private ButtonSetting button(String name) {
        ButtonSetting setting = new ButtonSetting(name);
        registerSetting(setting);
        return setting;
    }

    private ColorSetting colorSetting(String name, int value, float hueShift) {
        ColorSetting setting = new ColorSetting(name, value, hueShift);
        registerSetting(setting);
        return setting;
    }

    @Override
    public void link() {
        registerRenderer();
    }

    /**
     * Registered once and never unregistered: the list has to keep drawing (and fading out) after
     * the module is disabled, exactly like the other ported HUD elements.
     */
    private static void registerRenderer() {
        if (listener != null) {
            return;
        }
        listener = new Object() {
            @SubscribeEvent
            public void onRenderTick(TickEvent.RenderTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) {
                    return;
                }
                if (!Overlay.canRender()) {
                    return;
                }
                OnyxClickGuiModule.sync();
                HUD.render(instance, false);
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == editPosition) {
            mc.displayGuiScreen(new ArrayListPositionEditor());
        } else if (setting instanceof ColorSetting) {
            mc.displayGuiScreen(new ColorEditor(mc.currentScreen, (ColorSetting) setting));
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
    }

    @Override
    public void onDisable() {
        // No reset here: the list has to keep its rows while it fades out.
    }

    public static ArrayListHud hud() {
        return HUD;
    }

    // ------------------------------------------------------------------ //
    // Accessors used by the renderer
    // ------------------------------------------------------------------ //

    public int style() {
        return (int) style.getInput();
    }

    /** 0 = slide, 1 = fade, 2 = slide + fade, 3 = scale. */
    public int animation() {
        return (int) animation.getInput();
    }

    /** 0 = longest first, 1 = shortest first, 2 = A-Z. */
    public int sortMode() {
        return (int) sort.getInput();
    }

    /** 0 = accent, 1 = static, 2 = fade, 3 = gradient, 4 = rainbow, 5 = category. */
    public int colors() {
        return (int) colors.getInput();
    }

    public ColorSetting color() {
        return color;
    }

    public ColorSetting firstColor() {
        return firstColor;
    }

    public ColorSetting secondColor() {
        return secondColor;
    }

    public float colorSpeed() {
        return (float) colorSpeed.getInput();
    }

    public float colorSpread() {
        return (float) colorSpread.getInput();
    }

    public boolean suffix() {
        return suffix.isToggled();
    }

    public boolean lowercase() {
        return lowercase.isToggled();
    }

    public boolean background() {
        return background.isToggled();
    }

    /** 0 = one card behind the whole list, 1 = one block per row (hugs each row's text). */
    public int backgroundStyle() {
        return (int) backgroundStyle.getInput();
    }

    public boolean blur() {
        return blur.isToggled();
    }

    public boolean textShadow() {
        return textShadow.isToggled();
    }

    public boolean outline() {
        return outline.isToggled();
    }

    public float scale() {
        return (float) scale.getInput();
    }

    public void setScale(float value) {
        scale.setValue(value);
    }

    public float rowSpacing() {
        return (float) rowSpacing.getInput();
    }

    public float animationSpeed() {
        return (float) animationSpeed.getInput();
    }

    public float positionX() {
        return (float) positionX.getInput();
    }

    public float positionY() {
        return (float) positionY.getInput();
    }

    public void setPosition(float percentX, float percentY) {
        positionX.setValue(percentX);
        positionY.setValue(percentY);
    }

    /**
     * Whether the list shows this module: the category toggles from Onyx's multi-select, minus the
     * entry itself, hidden modules, profiles and the GUI/HUD containers (those are windows, not
     * features).
     */
    public boolean includes(Module module) {
        if (module == this || module == ModuleManager.hud || module == ModuleManager.gui
                || module == ModuleManager.commandLine) {
            return false;
        }
        if (module.isHidden() || module.isProfile() || module.isScript()) {
            return false;
        }
        switch (module.moduleCategory()) {
            case combat:
                return showCombat.isToggled();
            case movement:
                return showMovement.isToggled();
            case player:
                return showPlayer.isToggled();
            case render:
                return showRender.isToggled();
            default:
                return showOther.isToggled();
        }
    }
}
