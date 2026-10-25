package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.PotionHudPositionEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.PotionHud;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Material 3 Expressive potion list, ported from OpenOnyx's {@code PotionHUDModule}.
 *
 * <p>Onyx's card listed the player's active effects with the vanilla status icon, the effect name
 * plus its roman level, the remaining time (red while it runs out) and a potion-coloured tint per
 * row. This port keeps those settings and adds the ones the other ported HUDs share: a scale
 * slider, percentage position with an "Edit position" screen, and the frosted backdrop.</p>
 */
public final class PotionHudModule extends Module {

    public static PotionHudModule instance;

    private static final PotionHud HUD = new PotionHud();
    private static Object listener;

    public static ButtonSetting editPosition;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting showIcons;
    public static ButtonSetting showDuration;
    public static ButtonSetting showAmplifier;
    public static ButtonSetting potionColor;
    public static ButtonSetting lowTimeWarning;
    public static ButtonSetting hideAmbient;

    public static SliderSetting scale;
    public static SliderSetting positionX;
    public static SliderSetting positionY;
    public static SliderSetting sort;

    public PotionHudModule(int key) {
        super("PotionHUD", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        scale = slider("Scale");
        positionX = slider("Position X");
        positionY = slider("Position Y");
        sort = slider("Sort");
        frostedGlass = button("Frosted glass");
        showIcons = button("Icons");
        showDuration = button("Duration");
        showAmplifier = button("Amplifier");
        potionColor = button("Potion color");
        lowTimeWarning = button("Low time warning");
        hideAmbient = button("Hide ambient");
        editPosition = button("Edit position");

        registerRenderer();
    }

    private static ButtonSetting button(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
    }

    private static SliderSetting slider(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    /**
     * Registered once and never unregistered: the card has to keep drawing (and fading out) after
     * the module is disabled, exactly like the other ported HUD cards.
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
    public void guiButtonToggled(ButtonSetting button) {
        if (button == editPosition) {
            mc.displayGuiScreen(new PotionHudPositionEditor());
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
    }

    @Override
    public void onDisable() {
        // No reset here: the card has to keep its rows while it fades out.
    }

    public static PotionHud hud() {
        return HUD;
    }

    public boolean frosted() {
        return frostedGlass == null || frostedGlass.isToggled();
    }

    public boolean showIcons() {
        return showIcons == null || showIcons.isToggled();
    }

    public boolean showDuration() {
        return showDuration == null || showDuration.isToggled();
    }

    public boolean showAmplifier() {
        return showAmplifier == null || showAmplifier.isToggled();
    }

    public boolean potionColor() {
        return potionColor == null || potionColor.isToggled();
    }

    /** Kept for the row colouring: the timer turns red once the effect is about to end. */
    public boolean lowTimeWarning() {
        return lowTimeWarning == null || lowTimeWarning.isToggled();
    }

    public boolean hideAmbient() {
        return hideAmbient != null && hideAmbient.isToggled();
    }

    /** 0 = time left, 1 = name, 2 = level. */
    public int sortMode() {
        return sort == null ? 0 : (int) sort.getInput();
    }

    public float scale() {
        return scale == null ? 1.0F : (float) scale.getInput();
    }

    public float positionX() {
        return positionX == null ? 0.0F : (float) positionX.getInput();
    }

    public float positionY() {
        return positionY == null ? 60.0F : (float) positionY.getInput();
    }

    public void setPosition(float percentX, float percentY) {
        if (positionX != null) {
            positionX.setValue(percentX);
        }
        if (positionY != null) {
            positionY.setValue(percentY);
        }
    }
}
