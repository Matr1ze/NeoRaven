package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import xyz.daikirai.module.render.CustomGuiModule;

/** Onyx's vanilla GUI font: a 7px cap height inside Minecraft's unchanged 9px line box. */
public final class GuiFont {
    private static final String ROOT = "/assets/onyx/font/ttf/googlesansflex_24pt-";
    private static final String COLORS = "0123456789abcdef";
    private static final String OBFUSCATED = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BOLD = 1;
    private static final int ITALIC = 2;
    private static final int RANDOM = 4;
    private static final int STRIKE = 8;
    private static final int UNDERLINE = 16;
    private static final TtfFont[] FONTS = new TtfFont[4];
    private static final ThreadLocal<Integer> SCOPE = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };
    private static int bypass;
    private static boolean failed;
    private static int draws;

    private GuiFont() {
    }

    public static void begin() {
        SCOPE.set(Integer.valueOf(SCOPE.get().intValue() + 1));
    }

    public static void end() {
        SCOPE.set(Integer.valueOf(Math.max(0, SCOPE.get().intValue() - 1)));
    }

    public static boolean active() {
        // Forge's splash renderer has its own GL context and must keep the bitmap font.
        return SCOPE.get().intValue() > 0 && bypass == 0 && !failed && CustomGuiModule.replacesFont();
    }

    public static void reset() {
        failed = false;
    }

    /** Number of {@code draw} calls taken over by the ported font (used by the dev harness). */
    public static int drawCount() {
        return draws;
    }

    public static void resetDrawCount() {
        draws = 0;
    }

    private static void fail(Throwable error) {
        failed = true;
        System.err.println("[onyx] GUI font fell back to Minecraft's bitmap font: " + error);
    }

    private static TtfFont font(int index) {
        if (FONTS[index] == null) {
            String[] weights = {"regular", "medium", "semibold", "bold"};
            FONTS[index] = TtfFont.load(ROOT + weights[index] + ".ttf", 24.0F);
        }
        return FONTS[index];
    }

    private static final class Metrics {
        final TtfFont normal = font(CustomGuiModule.mediumFont() ? 1 : 0);
        final TtfFont bold = font(CustomGuiModule.mediumFont() ? 3 : 2);
        final float scale = 7.0F / Math.max(1.0F, normal.ascent()) * CustomGuiModule.fontSize();
        final float spacing = CustomGuiModule.fontSpacing();

        TtfFont resolve(char c, boolean heavy) {
            return Typeface.runFont(heavy ? bold : normal, c);
        }

        float scale(TtfFont font) {
            return scale * normal.size() / font.size();
        }

        float advance(FontRenderer renderer, char c, boolean heavy) {
            if (c == '\u00a7' || c == '\n' || c == '\r') {
                return 0.0F;
            }
            TtfFont font = resolve(c, false);
            float width = font.has(c) ? font.advance(c) * scale(font) : bitmapWidth(renderer, c);
            return Math.max(0.0F, width + spacing) + (heavy ? 1.0F : 0.0F);
        }

        void prepare(String text) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                resolve(c, false).glyph(c);
                resolve(c, true).glyph(c);
            }
            if (text.indexOf('\u00a7') >= 0) {
                for (int i = 0; i < OBFUSCATED.length(); i++) {
                    char c = OBFUSCATED.charAt(i);
                    normal.glyph(c);
                    bold.glyph(c);
                }
            }
        }
    }

    private static int format(int flags, char code) {
        if (COLORS.indexOf(code) >= 0 || code == 'r') {
            return 0;
        }
        switch (code) {
            case 'k': return flags | RANDOM;
            case 'l': return flags | BOLD;
            case 'm': return flags | STRIKE;
            case 'n': return flags | UNDERLINE;
            case 'o': return flags | ITALIC;
            default: return flags;
        }
    }

    private static float bitmapWidth(FontRenderer renderer, char c) {
        bypass++;
        try {
            return renderer.getCharWidth(c);
        } finally {
            bypass--;
        }
    }

    private static void bitmapGlyph(FontRenderer renderer, char c, float x, float y, int color, int flags) {
        String text = ((flags & BOLD) != 0 ? "\u00a7l" : "")
                + ((flags & ITALIC) != 0 ? "\u00a7o" : "") + c;
        bypass++;
        try {
            renderer.drawString(text, x, y, color, false);
        } finally {
            bypass--;
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
    }

    private static int darken(int color) {
        return (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2);
    }

    private static void line(float x, float y, float width, int color) {
        GlStateManager.disableTexture2D();
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + 1.0F);
        GL11.glVertex2f(x + width, y + 1.0F);
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();
        GlStateManager.enableTexture2D();
    }

    private static float layout(FontRenderer renderer, Metrics metrics, String text, float x, float y,
                                int base, boolean shadow, boolean draw) {
        if (text == null) {
            return 0.0F;
        }
        float width = 0.0F;
        int flags = 0;
        int color = shadow ? darken(base) : base;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                flags = format(flags, code);
                if (COLORS.indexOf(code) >= 0) {
                    int palette = renderer.getColorCode(code);
                    color = (base & 0xFF000000) | (shadow ? darken(palette) : palette);
                } else if (code == 'r') {
                    color = shadow ? darken(base) : base;
                }
                continue;
            }
            float advance = metrics.advance(renderer, c, (flags & BOLD) != 0);
            if (draw && c != '\u00a7' && c != '\n' && c != '\r') {
                char glyph = (flags & RANDOM) != 0 && c != ' '
                        ? OBFUSCATED.charAt(renderer.fontRandom.nextInt(OBFUSCATED.length())) : c;
                TtfFont font = metrics.resolve(glyph, (flags & BOLD) != 0);
                if (font.has(glyph)) {
                    font.drawGlyph(glyph, x + width, y + 7.0F, metrics.scale(font), color, (flags & ITALIC) != 0);
                } else {
                    bitmapGlyph(renderer, glyph, x + width, y, color, flags);
                }
                if ((flags & STRIKE) != 0) line(x + width, y + 3.85F, advance, color);
                if ((flags & UNDERLINE) != 0) line(x + width, y + 8.0F, advance, color);
            }
            width += advance;
        }
        return width;
    }

    /** A null result lets the Mixin continue through vanilla after a font failure. */
    public static Integer draw(FontRenderer renderer, String text, float x, float y, int color,
                               boolean shadow, boolean singlePass) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean colorLogic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        int activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int envMode = GL11.glGetTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            if (text == null) return Integer.valueOf(0);
            draws++;
            if ((color & 0xFC000000) == 0) color |= 0xFF000000;
            Metrics metrics = new Metrics();
            // Match Onyx's upload-before-draw order for both shadow and foreground passes.
            metrics.prepare(text);
            // The glyph atlas is a GL_ALPHA texture: it only shows the text colour while
            // GL_MODULATE is the env mode, unit 0 is sampled, texturing and blending are on and
            // lighting is off. World renderers leave raw glEnable/glDisable calls behind, and any
            // one of those states being wrong renders every glyph black, so rebuild them here.
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();
            GlStateManager.disableColorLogic();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);
            if (singlePass) {
                if (!shadow || CustomGuiModule.fontShadow()) {
                    layout(renderer, metrics, text, x, y, color, shadow, true);
                }
            } else {
                if (shadow && CustomGuiModule.fontShadow()) {
                    layout(renderer, metrics, text, x + 1.0F, y + 1.0F, color, true, true);
                }
                layout(renderer, metrics, text, x, y, color, false, true);
            }
            return Integer.valueOf((int) (x + Math.round(layout(renderer, metrics, text, 0, 0, 0, false, false))));
        } catch (Throwable error) {
            fail(error);
            return null;
        } finally {
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, envMode);
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            // Restore each cap with a toggle so GlStateManager's cache and the driver end up
            // describing the same state again (a plain enable() is a no-op when the cache is stale).
            if (blend) {
                GlStateManager.disableBlend();
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (texture) {
                GlStateManager.disableTexture2D();
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            if (lighting) {
                GlStateManager.disableLighting();
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (alphaTest) {
                GlStateManager.disableAlpha();
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (colorLogic) {
                GlStateManager.disableColorLogic();
                GlStateManager.enableColorLogic();
            } else {
                GlStateManager.disableColorLogic();
            }
            GlStateManager.setActiveTexture(activeUnit);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static Integer width(FontRenderer renderer, String text) {
        try {
            return Integer.valueOf(Math.round(layout(renderer, new Metrics(), text, 0, 0, 0, false, false)));
        } catch (Throwable error) {
            fail(error);
            return null;
        }
    }

    public static Integer charWidth(FontRenderer renderer, char c) {
        try {
            return Integer.valueOf(c == '\u00a7' ? -1 : Math.round(new Metrics().advance(renderer, c, false)));
        } catch (Throwable error) {
            fail(error);
            return null;
        }
    }

    private static float[] advances(FontRenderer renderer, String text) {
        Metrics metrics = new Metrics();
        float[] widths = new float[text.length()];
        int flags = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                flags = format(flags, Character.toLowerCase(text.charAt(++i)));
            } else {
                widths[i] = metrics.advance(renderer, c, (flags & BOLD) != 0);
            }
        }
        return widths;
    }

    public static String trim(FontRenderer renderer, String text, int limit, boolean reverse) {
        try {
            if (text == null || text.isEmpty()) return text;
            float[] widths = advances(renderer, text);
            float width = 0.0F;
            if (reverse) {
                int start = text.length();
                for (int i = text.length() - 1; i >= 0; i--) {
                    if (i > 0 && text.charAt(i - 1) == '\u00a7') {
                        start = --i;
                        continue;
                    }
                    if (Math.round(width + widths[i]) > limit) break;
                    width += widths[i];
                    start = i;
                }
                return text.substring(start);
            }
            int end = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                    end = ++i + 1;
                    continue;
                }
                if (Math.round(width + widths[i]) > limit) break;
                width += widths[i];
                end = i + 1;
            }
            return text.substring(0, end);
        } catch (Throwable error) {
            fail(error);
            return null;
        }
    }

    public static Integer wrapIndex(FontRenderer renderer, String text, int limit) {
        try {
            float[] widths = advances(renderer, text);
            float width = 0.0F;
            int space = -1;
            int visible = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\u00a7' && i + 1 < text.length()) {
                    i++;
                    continue;
                }
                if (c == '\n') return Integer.valueOf(i);
                if (c == ' ') space = i;
                width += widths[i];
                if (Math.round(width) > limit) {
                    return Integer.valueOf(space >= 0 ? space : visible == 0 ? i + 1 : i);
                }
                visible++;
            }
            return Integer.valueOf(text.length());
        } catch (Throwable error) {
            fail(error);
            return null;
        }
    }
}
