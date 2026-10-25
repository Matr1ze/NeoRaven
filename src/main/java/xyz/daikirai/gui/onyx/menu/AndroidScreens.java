package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;

/**
 * Routes the vanilla world / server selection screens to the Android rewrites.
 *
 * <p>Only while the title screen is in the Android style: picking "Minecraft" for the menu should
 * still give the untouched game screens. Only the exact vanilla classes are replaced - the
 * create-world and add-server flows are separate screens and stay vanilla.</p>
 *
 * <p>It also owns the "no world, no pause menu" rule: a pause menu requested while no world is
 * loaded cannot work (its quit button dereferences {@code theWorld}), so it is replaced by the
 * title screen instead of being shown and crashing on the first click.</p>
 */
public final class AndroidScreens {

    private AndroidScreens() {
    }

    public static GuiScreen wrap(GuiScreen requested) {
        if (requested == null) {
            return null;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (requested.getClass() == GuiIngameMenu.class && mc.theWorld == null) {
            return MenuStyle.current() == MenuStyle.ANDROID ? new AndroidMainMenu() : new GuiMainMenu();
        }
        // The mirror case: a title screen while a world is still loaded is a dead end. Nothing can
        // be joined from there and the pause menu is gone, so the only way out is restarting the
        // client. Send such a request to the pause menu instead.
        if (mc.theWorld != null && requested instanceof GuiMainMenu) {
            return new GuiIngameMenu();
        }
        if (MenuStyle.current() != MenuStyle.ANDROID) {
            return requested;
        }
        GuiScreen parent = parentFor(mc);
        if (requested.getClass() == GuiSelectWorld.class) {
            return new AndroidSelectWorldScreen(parent);
        }
        if (requested.getClass() == GuiMultiplayer.class) {
            return new AndroidMultiplayerScreen(parent);
        }
        return requested;
    }

    /**
     * Whether {@code screen} only works while a world is loaded. The pause menu is the dangerous
     * one: shown without a world, its "Save and Quit" dereferences {@code mc.theWorld} and crashes.
     */
    /** The "Menu background" picker, shared by both title screens. */
    public static GuiScreen backgroundPicker(GuiScreen parent) {
        return new BackgroundScreen(parent);
    }

    public static boolean needsWorld(GuiScreen screen) {
        return screen instanceof GuiIngameMenu || screen instanceof GuiDisconnected
                || screen.getClass() == GuiMultiplayer.class || screen.getClass() == GuiSelectWorld.class;
    }

    /**
     * The screen a list should return to. Transient screens (the disconnect notice, the pause menu,
     * another list) are dropped: returning to them once the world is gone is what left the client
     * on a pause menu whose quit button then crashed.
     */
    private static GuiScreen parentFor(Minecraft mc) {
        GuiScreen current = mc.currentScreen;
        if (current == null) {
            return null;
        }
        if (needsWorld(current) || current instanceof AndroidListScreen) {
            return null;
        }
        if (mc.theWorld == null && !(current instanceof GuiMainMenu)) {
            return null;
        }
        return current;
    }
}
