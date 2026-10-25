package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.KeybindsPositionEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.Keybinds;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Material 3 Expressive keybind list, ported from OpenOnyx's {@code KeybindsModule}.
 *
 * <p>Onyx listed every enabled module that owned a keybind. This port keeps the bind filter (no
 * key, no row) but keeps disabled binds visible with a dimmed off state, so the card doubles as
 * a reference sheet. The row morphs colour, the keycap pops and the state chip cross-fades when
 * the module toggles — that is the feedback Onyx drew as a static two-tone row.</p>
 */
public final class KeybindsModule extends Module {

    public static KeybindsModule instance;

    private static final Keybinds HUD = new Keybinds();
    private static Object listener;

    public static ButtonSetting editPosition;
    public static ButtonSetting frostedGlass;
    public static SliderSetting show;
    public static ButtonSetting showCount;

    public static SliderSetting scale;
    public static SliderSetting positionX;
    public static SliderSetting positionY;

    public KeybindsModule(int key) {
        super("Keybinds", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        scale = slider("Scale");
        positionX = slider("Position X");
        positionY = slider("Position Y");
        editPosition = button("Edit position");
        frostedGlass = button("Frosted glass");
        show = slider("Show");
        showCount = button("Show count");

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
     * the module is disabled, exactly like the watermark island does.
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
            mc.displayGuiScreen(new KeybindsPositionEditor());
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
    }

    @Override
    public void onDisable() {
        // No reset here: the card has to keep its last content while it fades out. The next
        // enable clears the animation state instead.
    }

    public static Keybinds hud() {
        return HUD;
    }

    public boolean frosted() {
        return frostedGlass != null && frostedGlass.isToggled();
    }

    /** {@code Show} mode: 0 = every bound module, 1 = only the ones that are switched on. */
    public boolean enabledOnly() {
        return show != null && Math.round(show.getInput()) == 1L;
    }

    public boolean showCount() {
        return showCount == null || showCount.isToggled();
    }

    public float scale() {
        return scale == null ? 1.0F : (float) scale.getInput();
    }

    public float positionX() {
        return positionX == null ? 0.0F : (float) positionX.getInput();
    }

    public float positionY() {
        return positionY == null ? 25.0F : (float) positionY.getInput();
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
