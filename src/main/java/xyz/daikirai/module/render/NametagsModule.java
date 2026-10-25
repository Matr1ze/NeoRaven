package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.Nametags;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Material 3 name plates, ported from OpenOnyx's {@code NamesModule} ("ESP").
 *
 * <p>Onyx drew a rounded plate above every player with the name plus optional rank, health and
 * ping readouts, a strip of equipment tiles and a distance-based size. This port keeps all of
 * that and adds the pieces the other ported HUD elements share: a scale setting, a frosted-glass
 * backdrop on the plate, and colour settings for the plate and its text, both wired to the
 * Material colour picker.</p>
 */
public final class NametagsModule extends Module {

    public static NametagsModule instance;

    private static final Nametags HUD = new Nametags();
    private static Object listener;

    private final SliderSetting scale = slider("Scale", 1, 0.5, 2, 0.05, "x");
    private final ButtonSetting autoScale = flag("Auto scale", true);
    private final ButtonSetting frostedGlass = flag("Frosted glass", true);
    private final ButtonSetting background = flag("Background", true);
    private final SliderSetting backgroundOpacity = slider("Background opacity", 62, 0, 100, 1, "%");
    private final ColorSetting backgroundColor = colorSetting("Background color", 0xFF101216, 0);
    private final ColorSetting textColor = colorSetting("Text color", 0xFFFFFFFF, 0);
    private final ButtonSetting outline = flag("Outline", false);
    private final ButtonSetting showRank = flag("Show rank", true);
    private final ButtonSetting showHealth = flag("Show health", true);
    private final ButtonSetting showPing = flag("Show ping", true);
    private final ButtonSetting showDistance = flag("Show distance", false);
    private final ButtonSetting showItems = flag("Show items", true);
    private final ButtonSetting renderSelf = flag("Render self", false);
    private final ButtonSetting showInvis = flag("Show invis", false);
    private final SliderSetting distance = slider("Distance", 64, 8, 256, 4, "m");
    private final ButtonSetting disableInLobby = flag("Disable in lobby", true);

    public NametagsModule(int key) {
        super("Nametags", Category.render, key);
        instance = this;
        // The plate and its text are literals by default: a theme-following plate would tint every
        // name in the accent colour. The colour picker can still switch either one to the theme.
        backgroundColor.setAppearance(0xFF101216, false);
        textColor.setAppearance(0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ //
    // Settings
    // ------------------------------------------------------------------ //

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        registerSetting(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
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
     * Registered once and never unregistered: the plates have to keep drawing (and fading out)
     * after the module is disabled, exactly like the other ported HUD elements.
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
                HUD.render(instance, event.renderTickTime);
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting instanceof ColorSetting) {
            mc.displayGuiScreen(new ColorEditor(mc.currentScreen, (ColorSetting) setting));
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
    }

    @Override
    public void onDisable() {
        // No reset here: the plates have to keep drawing while they fade out.
    }

    public static Nametags hud() {
        return HUD;
    }

    // ------------------------------------------------------------------ //
    // Accessors used by the renderer
    // ------------------------------------------------------------------ //

    public float scale() {
        return (float) scale.getInput();
    }

    public boolean autoScale() {
        return autoScale.isToggled();
    }

    public boolean frosted() {
        return frostedGlass.isToggled();
    }

    public boolean background() {
        return background.isToggled();
    }

    public float backgroundOpacity() {
        return (float) backgroundOpacity.getInput();
    }

    public ColorSetting backgroundColor() {
        return backgroundColor;
    }

    public ColorSetting textColor() {
        return textColor;
    }

    public boolean outline() {
        return outline.isToggled();
    }

    public boolean showRank() {
        return showRank.isToggled();
    }

    public boolean showHealth() {
        return showHealth.isToggled();
    }

    public boolean showPing() {
        return showPing.isToggled();
    }

    public boolean showDistance() {
        return showDistance.isToggled();
    }

    public boolean showItems() {
        return showItems.isToggled();
    }

    public boolean renderSelf() {
        return renderSelf.isToggled();
    }

    public boolean showInvis() {
        return showInvis.isToggled();
    }

    public float distance() {
        return (float) distance.getInput();
    }

    public boolean disableInLobby() {
        return disableInLobby.isToggled();
    }
}
