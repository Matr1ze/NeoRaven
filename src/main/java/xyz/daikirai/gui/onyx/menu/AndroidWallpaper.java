package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * Title-screen wallpaper for the Android menu: light tonal surface plus the light ink scale used
 * by every element drawn on top of it.
 *
 * <p>The palette is derived from the live ClickGUI accent, so switching the accent in the module
 * settings retints the whole menu (exactly what a Material You wallpaper does). Tones are pushed
 * into the very light end - base 97, blobs 86..93 - which is what makes the screen read as an
 * AOSP/Pixel wallpaper instead of the dark Onyx backdrop.</p>
 */
public final class AndroidWallpaper {

    private static final long START = System.nanoTime();

    private static Shader shader;
    private static boolean attempted;

    private static int builtAccent;
    private static boolean builtDark;
    private static int base;
    private static int cool;
    private static int warm;
    private static int glow;
    private static int ink;
    private static int softInk;
    private static int faintInk;
    private static int primary;
    private static int onPrimary;
    private static int primaryContainer;
    private static int onPrimaryContainer;
    private static int secondaryContainer;
    private static int onSecondaryContainer;
    private static int tertiaryContainer;
    private static int onTertiaryContainer;
    private static int neutralContainer;
    private static int onNeutralContainer;
    private static int surfaceHigh;
    private static int onSurfaceVariant;
    private static int outlineVariant;
    private static int outline;
    private static int card;
    private static int sheet;
    private static int errorInk;
    private static int errorContainer;
    private static int onErrorContainer;
    private static int tooltip;
    private static int tooltipInk;

    private AndroidWallpaper() {
    }

    /** Builds the tonal palette for the current accent and mode, lazily. */
    private static void ensure() {
        int accent = Theme.accent();
        boolean dark = MenuTheme.dark();
        if (builtAccent == accent && builtDark == dark && base != 0) {
            return;
        }
        builtAccent = accent;
        builtDark = dark;
        Material.Hct hct = Material.Hct.fromInt(accent);
        double hue = hct.hue;
        double chroma = hct.chroma;
        if (!dark) {
            // A wallpaper, not a poster: the surface sits at tone 88 (the reference screen's light
            // grey) and the blobs only borrow a tenth of the accent's chroma, so the tint stays a
            // hint.
            base = Material.TonalPalette.of(hue, chroma * 0.10).tone(88.0);
            cool = Material.TonalPalette.of(hue + 18.0, chroma * 0.22).tone(84.0);
            warm = Material.TonalPalette.of(hue - 40.0, chroma * 0.18).tone(86.0);
            glow = Material.TonalPalette.of(hue + 70.0, chroma * 0.20).tone(80.0);
            ink = Material.TonalPalette.of(hue, Math.min(chroma, 8.0)).tone(13.0);
            softInk = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(38.0);
            faintInk = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(52.0);

            // Light Material 3 roles, used by the themed dock icons and the settings sheet.
            primary = Material.TonalPalette.of(hue, chroma).tone(42.0);
            onPrimary = Material.TonalPalette.of(hue, chroma * 0.2).tone(98.0);
            primaryContainer = Material.TonalPalette.of(hue, chroma).tone(84.0);
            onPrimaryContainer = Material.TonalPalette.of(hue, chroma * 0.7).tone(22.0);
            secondaryContainer = Material.TonalPalette.of(hue, chroma / 3.0).tone(88.0);
            onSecondaryContainer = Material.TonalPalette.of(hue, chroma / 3.0).tone(24.0);
            tertiaryContainer = Material.TonalPalette.of(hue + 55.0, chroma * 0.6).tone(86.0);
            onTertiaryContainer = Material.TonalPalette.of(hue + 55.0, chroma * 0.6).tone(24.0);
            neutralContainer = Material.TonalPalette.of(hue, 10.0).tone(88.0);
            onNeutralContainer = Material.TonalPalette.of(hue, 8.0).tone(32.0);
            outline = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(58.0);
            surfaceHigh = Material.TonalPalette.of(hue, chroma * 0.12).tone(94.0);
            onSurfaceVariant = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(42.0);
            outlineVariant = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(74.0);
            card = 0xFFFFFFFF;
            sheet = 0xFFFDFCFF;
            errorInk = 0xFFB3261E;
            errorContainer = 0xFFF9DEDC;
            onErrorContainer = 0xFF8C1D18;
            tooltip = 0xFF1F1F26;
            tooltipInk = 0xFFFFFFFF;
        } else {
            // Dark scheme: the page goes to near-black, the blobs to tone 13..19 so the wallpaper
            // still reads as a soft gradient instead of a flat black fill.
            base = Material.TonalPalette.of(hue, chroma * 0.14).tone(9.0);
            cool = Material.TonalPalette.of(hue + 18.0, chroma * 0.24).tone(13.0);
            warm = Material.TonalPalette.of(hue - 40.0, chroma * 0.20).tone(15.0);
            glow = Material.TonalPalette.of(hue + 70.0, chroma * 0.22).tone(19.0);
            ink = Material.TonalPalette.of(hue, Math.min(chroma, 8.0)).tone(93.0);
            softInk = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(78.0);
            faintInk = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(62.0);

            primary = Material.TonalPalette.of(hue, chroma).tone(80.0);
            onPrimary = Material.TonalPalette.of(hue, chroma * 0.25).tone(20.0);
            primaryContainer = Material.TonalPalette.of(hue, chroma).tone(30.0);
            onPrimaryContainer = Material.TonalPalette.of(hue, chroma * 0.6).tone(90.0);
            secondaryContainer = Material.TonalPalette.of(hue, chroma / 3.0).tone(30.0);
            onSecondaryContainer = Material.TonalPalette.of(hue, chroma / 3.0).tone(90.0);
            tertiaryContainer = Material.TonalPalette.of(hue + 55.0, chroma * 0.6).tone(32.0);
            onTertiaryContainer = Material.TonalPalette.of(hue + 55.0, chroma * 0.6).tone(90.0);
            neutralContainer = Material.TonalPalette.of(hue, 10.0).tone(30.0);
            onNeutralContainer = Material.TonalPalette.of(hue, 8.0).tone(86.0);
            outline = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(62.0);
            surfaceHigh = Material.TonalPalette.of(hue, chroma * 0.12).tone(22.0);
            onSurfaceVariant = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(80.0);
            outlineVariant = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(42.0);
            card = Material.TonalPalette.of(hue, chroma * 0.10).tone(16.0);
            sheet = Material.TonalPalette.of(hue, chroma * 0.10).tone(21.0);
            errorInk = 0xFFFFB4AB;
            errorContainer = 0xFF93000A;
            onErrorContainer = 0xFFFFDAD6;
            tooltip = Material.TonalPalette.of(hue, chroma * 0.10).tone(26.0);
            tooltipInk = Material.TonalPalette.of(hue, Math.min(chroma, 8.0)).tone(94.0);
        }
    }

    /** Wallpaper base tone (tone 97): the surface behind every card. */
    public static int base() {
        ensure();
        return base;
    }

    /** Primary text: near black with a trace of the accent hue. */
    public static int ink() {
        ensure();
        return ink;
    }

    /** Secondary text: the "on surface variant" of the light scheme. */
    public static int softInk() {
        ensure();
        return softInk;
    }

    /** Tertiary text: status bar items and deselected action labels. */
    public static int faintInk() {
        ensure();
        return faintInk;
    }

    /** Accent role: the mode-switch button and the selected radio. */
    public static int primary() {
        ensure();
        return primary;
    }

    public static int onPrimary() {
        ensure();
        return onPrimary;
    }

    /** MD3 themed-icon container / glyph pairs, one role per dock entry. */
    public static int primaryContainer() {
        ensure();
        return primaryContainer;
    }

    public static int onPrimaryContainer() {
        ensure();
        return onPrimaryContainer;
    }

    public static int secondaryContainer() {
        ensure();
        return secondaryContainer;
    }

    public static int onSecondaryContainer() {
        ensure();
        return onSecondaryContainer;
    }

    public static int tertiaryContainer() {
        ensure();
        return tertiaryContainer;
    }

    public static int onTertiaryContainer() {
        ensure();
        return onTertiaryContainer;
    }

    /** Neutral container used by utility icons (the settings gear). */
    public static int surfaceHigh() {
        ensure();
        return surfaceHigh;
    }

    /** Neutral themed-icon container: a grey chip that still reads on the white dock. */
    public static int neutralContainer() {
        ensure();
        return neutralContainer;
    }

    public static int onNeutralContainer() {
        ensure();
        return onNeutralContainer;
    }

    public static int onSurfaceVariant() {
        ensure();
        return onSurfaceVariant;
    }

    public static int outlineVariant() {
        ensure();
        return outlineVariant;
    }

    /** Stronger divider / off-state outline (tone 58), used by switch tracks and page splits. */
    public static int outline() {
        ensure();
        return outline;
    }

    /**
     * Page surface behind the settings cards: a very pale tint of the theme's secondary container,
     * so the white cards have an edge without the page turning into a flat white fill.
     */
    public static int page() {
        ensure();
        return builtDark ? Material.mix(base, secondaryContainer, 0.55F)
                : Material.mix(0xFFFFFFFF, secondaryContainer, 0.62F);
    }

    /** Card surface: white in the light scheme, near-black in the dark one. */
    public static int card() {
        ensure();
        return card;
    }

    /** Bottom sheet surface, a step lighter than a card so it still reads as a layer. */
    public static int sheet() {
        ensure();
        return sheet;
    }

    public static int errorInk() {
        ensure();
        return errorInk;
    }

    public static int errorContainer() {
        ensure();
        return errorContainer;
    }

    public static int onErrorContainer() {
        ensure();
        return onErrorContainer;
    }

    public static int tooltip() {
        ensure();
        return tooltip;
    }

    public static int tooltipInk() {
        ensure();
        return tooltipInk;
    }

    /**
     * The floating dock pill. It follows the wallpaper's own average colour so the dock sits on the
     * artwork instead of punching a white hole in it, and the colour sheet can pin a colour
     * instead ({@link MenuTheme#dockColor()}).
     */
    public static int dock() {
        ensure();
        int custom = MenuTheme.dockColor();
        if (custom != 0) {
            return custom;
        }
        return Material.mix(card, MenuBackground.averageColor(), builtDark ? 0.42F : 0.26F);
    }

    /** Whether the wallpaper shader compiled; the harness asserts this so a silent fallback fails the run. */
    public static boolean ready() {
        ensure();
        if (!attempted) {
            attempted = true;
            shader = Shader.load("android_wallpaper");
        }
        return shader != null && shader.valid();
    }

    /** Fills the whole screen with the wallpaper. */
    public static void draw(UI ui, int width, int height) {
        ensure();
        if (!ready()) {
            ui.gradient4(0.0F, 0.0F, width, height, 0.0F, base, warm, cool, base);
            return;
        }
        GlStateManager.disableTexture2D();
        shader.use();
        shader.uniform1f("uAspect", width / (float) Math.max(height, 1));
        shader.uniform1f("uTime", (System.nanoTime() - START) / 1.0E9F);
        shader.uniformColor("uBase", base);
        shader.uniformColor("uCool", cool);
        shader.uniformColor("uWarm", warm);
        shader.uniformColor("uGlow", glow);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(0.0F, height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(width, height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(width, 0.0F);
        GL11.glEnd();
        Shader.unbind();
    }
}
