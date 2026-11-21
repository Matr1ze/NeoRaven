package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import xyz.daikirai.client.Overlay;

/** A small, damped rebound after leaving the notification shade; waits for the HUD to be visible. */
public final class HudReturn {
    private static boolean pending, shadePass;
    private static long start;
    private static Object world;
    private HudReturn() { }
    public static void arm() { pending = true; start = 0; world = Minecraft.getMinecraft().theWorld; }
    public static void shadePass(boolean value) { shadePass = value; }
    public static void cancel() { pending = false; start = 0; }
    public static float elapsed() {
        Minecraft mc = Minecraft.getMinecraft();
        if (world != mc.theWorld || mc.theWorld == null) { cancel(); return -1; }
        if ((!Overlay.canRender() && !shadePass) || !pending) return -1;
        long now = System.nanoTime();
        if (start == 0) start = now;
        float seconds = (now - start) / 1.0E9F;
        if (seconds >= 0.85F) { cancel(); return -1; }
        return seconds;
    }
    public static float displacement(float seconds) {
        return (float)(Math.exp(-8 * seconds) * (Math.cos(14 * seconds) + 8.0 / 14 * Math.sin(14 * seconds)));
    }
}
