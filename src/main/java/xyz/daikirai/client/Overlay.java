package xyz.daikirai.client;

import xyz.daikirai.gui.ClickGui;
import xyz.daikirai.gui.HudEditor;
import xyz.daikirai.module.SettingsModule;
import xyz.daikirai.module.render.WatermarkModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;

public final class Overlay {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private Overlay() {
    }

    public static boolean canRender() {
        return Chat.inWorld() && !mc.gameSettings.showDebugInfo && !mc.gameSettings.hideGUI
                // Island replaces this container with a HUD card; keep the other HUDs visible too.
                && (mc.currentScreen == null || WatermarkModule.replacesChestScreen()
                || !SettingsModule.chatAsInventory.isToggled() && mc.currentScreen instanceof GuiChat);
    }

    public static boolean screenOpen() {
        return !(mc.currentScreen == null || ownScreen() || !SettingsModule.chatAsInventory.isToggled() && mc.currentScreen instanceof GuiChat);
    }

    public static boolean ownScreen() {
        return mc.currentScreen instanceof ClickGui || mc.currentScreen instanceof HudEditor;
    }
}
