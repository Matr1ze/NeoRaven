package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.client.renderer.GlStateManager;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.render.HandState;

/** Material surfaces for vanilla/Forge menu flows whose game logic stays in the original screen. */
public final class AndroidUi {
    private static UI ui;
    private AndroidUi() { }
    public static boolean active(GuiScreen screen) {
        if (MenuStyle.current() != MenuStyle.ANDROID || screen == null
                || screen instanceof GuiContainer || screen instanceof GuiChat
                || screen instanceof GuiMainMenu || screen instanceof GuiScreenBook
                || screen instanceof GuiEditSign) return false;
        String name = screen.getClass().getName();
        return name.startsWith("net.minecraft.client.gui.") || name.startsWith("net.minecraftforge.fml.client.")
                || name.startsWith("net.minecraftforge.client.gui.") || name.startsWith("net.optifine.gui.");
    }
    public static boolean active() { return active(Minecraft.getMinecraft().currentScreen); }
    private static UI ui() { if (ui == null) ui = new UI(); return ui; }

    public static void background(int width, int height) {
        try (HandState state = new HandState()) {
            UI ui = ui(); ui.begin();
            ui.rect(0, 0, width, height, AndroidWallpaper.page());
            if (Minecraft.getMinecraft().currentScreen instanceof GuiYesNo) {
                // Vanilla confirm dialogs keep their callback/button coordinates inside a contained sheet.
                float w = Math.min(width - 24, 420);
                float top = Math.min(48, height * .12F);
                float bottom = Math.min(height - 12, Math.max(196, height / 6F + 140));
                ui.shadow((width - w) / 2, top, w, bottom - top, 24, 3);
                ui.rounded((width - w) / 2, top, w, bottom - top, 24, AndroidWallpaper.card());
            } else {
                ui.rounded(8, 8, width - 16, height - 16, Math.min(24, height * .07F), AndroidWallpaper.card());
            }
            ui.end();
        }
    }

    public static void listSurface(int x, int y, int w, int h) {
        try (HandState state = new HandState()) {
            UI ui = ui(); ui.begin();
            ui.rounded(x, y, w, h, 12, AndroidWallpaper.surfaceHigh());
            ui.end();
        }
    }

    public static void button(GuiButton button, boolean hover) {
        try (HandState state = new HandState()) {
            UI ui = ui(); ui.begin();
            float x = button.xPosition, y = button.yPosition, w = button.width, h = button.height;
            if (!button.enabled) ui.alphaPush(.38F);
            int fill = hover && button.enabled ? AndroidWallpaper.primaryContainer() : AndroidWallpaper.secondaryContainer();
            ui.rounded(x, y, w, h, Math.min(h / 2, 14), fill);
            int ink = button.packedFGColour != 0 ? button.packedFGColour : AndroidWallpaper.onSecondaryContainer();
            String label = button.displayString;
            Typeface.Style font = Typeface.CAPTION;
            float available = Math.max(1, w - 12);
            if (ui.width(font, label) > available) {
                while (label.length() > 1 && ui.width(font, label + "…") > available) label = label.substring(0, label.length() - 1);
                label += "…";
            }
            ui.text(font, label, x + w / 2, y + h / 2, ink, UI.ALIGN_CENTER);
            if (!button.enabled) ui.alphaPop();
            ui.end();
        }
    }

    public static void slider(GuiButton button, float value) {
        try (HandState state = new HandState()) {
            UI ui = ui(); ui.begin();
            float x = button.xPosition + 4, y = button.yPosition + button.height - 4;
            float w = button.width - 8, thumb = x + w * Material.clamp(value, 0, 1);
            if (!button.enabled) ui.alphaPush(.38F);
            ui.rounded(x, y, w, 2, 1, AndroidWallpaper.outlineVariant());
            ui.rounded(x, y, thumb - x, 2, 1, AndroidWallpaper.primary());
            ui.rounded(thumb - 1.5F, y - 2, 3, 6, 1.5F, AndroidWallpaper.primary());
            if (!button.enabled) ui.alphaPop();
            ui.end();
        }
    }

    public static void field(GuiTextField field) {
        try (HandState state = new HandState()) {
            UI ui = ui(); ui.begin();
            ui.rounded(field.xPosition - 1, field.yPosition - 1, field.width + 2, field.height + 2, 6, AndroidWallpaper.surfaceHigh());
            ui.outline(field.xPosition - 1, field.yPosition - 1, field.width + 2, field.height + 2, 6,
                    field.isFocused() ? 1.5F : .75F, field.isFocused() ? AndroidWallpaper.primary() : AndroidWallpaper.outlineVariant());
            ui.end();
        }
    }

    /** Neutral vanilla labels adopt the palette; warnings and formatted colours keep their meaning. */
    public static int textColor(int color) {
        if (!xyz.daikirai.gui.onyx.render.GuiFont.scoped() || !active()) return color;
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        if (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 12) return color;
        int alpha = color >>> 24;
        return (alpha == 0 ? 0xFF000000 : alpha << 24) | ((r >= 190 ? AndroidWallpaper.ink() : AndroidWallpaper.softInk()) & 0xFFFFFF);
    }
}
