package xyz.daikirai.module.render;

import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.NotificationCenterScreen;
import xyz.daikirai.gui.onyx.hud.NotificationHistory;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/** An action module, like Music Player: its binding opens a screen rather than a running HUD. */
public final class NotificationCenterModule extends Module {
    public static NotificationCenterModule instance;
    public static ButtonSetting swipeToOpen, openButton;
    public static SliderSetting guiScale;
    private static final Input INPUT = new Input();
    private static boolean installed;
    private boolean held;

    public NotificationCenterModule(int key) {
        super("Notification Center", Category.other, key);
        instance = this;
    }

    @Override public void link() {
        swipeToOpen = (ButtonSetting) ModuleManager.setting(this, "Swipe to open");
        openButton = (ButtonSetting) ModuleManager.setting(this, "Open");
        guiScale = (SliderSetting) ModuleManager.setting(this, "GUI Scale");
        if (!installed) {
            installed = true;
            MinecraftForge.EVENT_BUS.register(INPUT);
        }
    }

    public static float scale() { return guiScale == null ? 1.0F : (float) guiScale.getInput(); }

    public static boolean gestureContext(GuiScreen screen) {
        return screen instanceof GuiChat || screen instanceof GuiIngameMenu;
    }

    public static void open() {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (mc.currentScreen instanceof NotificationCenterScreen) {
            ((NotificationCenterScreen) mc.currentScreen).close();
        } else {
            mc.displayGuiScreen(new NotificationCenterScreen(mc.currentScreen));
        }
    }

    @Override public void onEnable() { disable(); open(); }
    @Override public void guiButtonToggled(ButtonSetting setting) {
        if (setting == openButton) open(); else super.guiButtonToggled(setting);
    }

    /** GUI bindings are consumed by Input before chat can insert the bound character. */
    @Override public boolean pollBind() {
        int key = getKeycode();
        boolean down = key != 0 && key != -1 && key >= -100 && key < Keyboard.KEYBOARD_SIZE
                && (key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key));
        boolean pressed = down && !held;
        held = down;
        if (pressed && mc.currentScreen == null) open();
        return pressed;
    }

    @Override public void releaseBind() { super.releaseBind(); held = false; }

    public static boolean boundKey(int key) { return instance != null && key > 0 && key == instance.getKeycode(); }
    public static boolean boundMouse(int button) {
        return instance != null && button >= 0 && instance.getKeycode() == button - 100;
    }

    public static void consumeBind() { if (instance != null) instance.held = true; }

    private static final class Input {
        private GuiScreen origin;
        private float startY;
        private Object world;

        @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.START) return;
            if (world != mc.theWorld) {
                if (world != null) NotificationHistory.clear();
                world = mc.theWorld;
            }
            if (origin != mc.currentScreen) origin = null;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void key(GuiScreenEvent.KeyboardInputEvent.Pre event) {
            if (keyInput(event.gui, Keyboard.getEventKey(), Keyboard.getEventKeyState(), Keyboard.isRepeatEvent()))
                event.setCanceled(true);
        }

        boolean keyInput(GuiScreen screen, int key, boolean down, boolean repeat) {
            if (mc.theWorld == null || screen != mc.currentScreen || !gestureContext(screen)
                    || !down || repeat || !boundKey(key)) return false;
            consumeBind(); open(); return true;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void mouse(GuiScreenEvent.MouseInputEvent.Pre event) {
            float y = event.gui.height - Mouse.getEventY() * event.gui.height / (float) mc.displayHeight - 1.0F;
            if (mouseInput(event.gui, Mouse.getEventButton(), Mouse.getEventButtonState(), Mouse.isButtonDown(0), y))
                event.setCanceled(true);
        }

        boolean mouseInput(GuiScreen screen, int button, boolean down, boolean leftDown, float y) {
            if (mc.theWorld == null || screen != mc.currentScreen || !gestureContext(screen)) {
                origin = null; return false;
            }
            if (down && boundMouse(button)) { consumeBind(); open(); return true; }
            if (swipeToOpen == null || !swipeToOpen.isToggled()) { origin = null; return false; }
            if (button == 0 && down && y >= -1 && y <= Math.max(8.0F, screen.height * 0.025F)) {
                origin = screen; startY = y; return true;
            } else if (origin == screen) {
                if (button == 0 && !down) { origin = null; return true; }
                if (leftDown && y - startY >= 8.0F) {
                    GuiScreen parent = origin; origin = null;
                    mc.displayGuiScreen(new NotificationCenterScreen(parent, startY, y));
                }
                return true;
            }
            return false;
        }
    }
}
