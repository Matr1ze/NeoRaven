package xyz.daikirai.gui.onyx.render;

import java.util.HashMap;
import java.util.Map;

/**
 * The Material type scale used by the Onyx ClickGUI, ported from {@code theme.Util2} and
 * {@code render.font.Util}.
 *
 * <p>Onyx bundled Google Sans Flex at five optical sizes; the ClickGUI only ever used 9pt and
 * 24pt, so those are the two families shipped with this port. Weight and pixel size map exactly
 * to the original records.</p>
 */
public final class Typeface {

    private static final String ROOT = "/assets/onyx/font/ttf/googlesansflex_";
    private static final String ICONS_PATH = "/assets/onyx/font/ttf/materialicons-regular.ttf";

    /** Material Icons glyphs (16px reference size, drawn through {@link UI#icon}). */
    public static final TtfFont ICONS = TtfFont.load(ICONS_PATH, 16.0F);

    private static TtfFont regular9;
    private static TtfFont medium9;
    private static TtfFont regular24;
    private static TtfFont medium24;

    /** Wallpaper clock: 24pt regular blown up to 42px (the atlas rasters at 96px, so it stays sharp). */
    public static final Style DISPLAY = new Style(24.0F, 42.0F, 48.0F, false);
    /** Android greeting: 24pt regular at 22px. */
    public static final Style HEADLINE_LARGE = new Style(24.0F, 22.0F, 28.0F, false);
    /** Heading / module name: 24pt medium at 14px with a 20px line box. */
    public static final Style HEADLINE = new Style(24.0F, 14.0F, 20.0F, true);
    /** Dialogue text: 24pt medium at 14px. */
    public static final Style TITLE = HEADLINE;
    /** Body text: 9pt regular at 12px, 16px line box. */
    public static final Style BODY = new Style(9.0F, 12.0F, 16.0F, false);
    /** Emphasised body text: 9pt medium at 12px. */
    public static final Style BODY_MEDIUM = new Style(9.0F, 12.0F, 16.0F, true);
    /** Labels (category names, card descriptions): 9pt medium at 11px. */
    public static final Style LABEL = new Style(9.0F, 11.0F, 16.0F, true);
    /** Tight labels (setting values): 9pt medium at 11px, 14px line box. */
    public static final Style LABEL_TIGHT = new Style(9.0F, 11.0F, 14.0F, true);
    /** Small captions: 9pt medium at 10px. */
    public static final Style CAPTION = new Style(9.0F, 10.0F, 12.0F, true);
    /** Micro text (badges): 9pt medium at 9px. */
    public static final Style MICRO = new Style(9.0F, 9.0F, 12.0F, true);
    /** Tiny text (numeric suffix): 9pt medium at 8px. */
    public static final Style TINY = new Style(9.0F, 8.0F, 10.0F, true);

    private static TtfFont fallbackSmall;
    private static TtfFont fallbackLarge;
    private static boolean fallbackSmallLoaded;
    private static boolean fallbackLargeLoaded;
    private static final Map<String, Style> CJK_STYLES = new HashMap<String, Style>();

    /**
     * Adds an entry to the type scale for the screens that need sizes the ClickGUI never used
     * (the music player's lyric ramp). Same rasteriser and metrics as the fixed styles.
     */
    public static Style custom(float optical, float size, float lineHeight, boolean medium) {
        return new Style(optical, size, lineHeight, medium);
    }

    /**
     * An entry of the type scale pinned to one concrete face instead of a weight/optical pair.
     *
     * <p>Used by the music player, which draws everything in Microsoft YaHei: mixing Google Sans
     * with the CJK fallback inside one string shifted the two runs vertically, because each face
     * lays its glyphs out from its own em-box top.</p>
     */
    public static Style fromFont(TtfFont font, float size, float lineHeight) {
        return new Style(font, size, lineHeight);
    }

    /**
     * A style that keeps Google Sans for Latin but borrows another face for everything it cannot
     * draw, before falling back to the global CJK face.
     *
     * <p>The compact music card uses this: English stays Google Sans, while Chinese/Japanese
     * lyrics keep the player's own MiSans Medium instead of the heavier global fallback.</p>
     */
    public static Style mixed(float optical, float size, float lineHeight, boolean medium, TtfFont secondary) {
        return new Style(optical, size, lineHeight, medium, null, secondary);
    }

    /**
     * System CJK fallback font. Google Sans Flex has no CJK coverage, so Chinese/Japanese/Korean
     * text is drawn with this face instead of rendering as blanks.
     *
     * <p>The 9pt face is kept for small type. Large ClickGUI headings use Google Sans Flex 24pt,
     * whose atlas is rasterised at 96px; reusing the old 9pt/36px CJK atlas and scaling it up made
     * Chinese strokes look soft. The 24pt face is rasterised at 72px in a 4096px atlas, then
     * minified to the requested style size.</p>
     */
    public static TtfFont fallback() {
        return fallbackFor(9.0F);
    }

    /** Picks the CJK atlas whose nominal size is closest to the primary face. */
    private static synchronized TtfFont fallbackFor(float primarySize) {
        boolean large = primarySize > 16.0F;
        if (large ? fallbackLargeLoaded : fallbackSmallLoaded) {
            return large ? fallbackLarge : fallbackSmall;
        }
        if (large) {
            fallbackLargeLoaded = true;
        } else {
            fallbackSmallLoaded = true;
        }
        TtfFont loaded = loadFallback(large ? 24.0F : 9.0F,
                large ? 3.0F : 4.0F, large ? 4096 : 2048);
        if (large) {
            fallbackLarge = loaded;
        } else {
            fallbackSmall = loaded;
        }
        return loaded;
    }

    private static TtfFont loadFallback(float size, float supersample, int atlas) {
        String[] candidates = {
                "D:\\Downloads\\MiSans-Demibold.ttf",
                new java.io.File(net.minecraft.client.Minecraft.getMinecraft().mcDataDir,
                        "keystrokes/fonts/MiSans-Demibold.ttf").getPath(),
                "C:\\Windows\\Fonts\\msyh.ttc",
                "C:\\Windows\\Fonts\\msyh.ttf",
                "C:\\Windows\\Fonts\\Deng.ttf",
                "C:\\Windows\\Fonts\\simhei.ttf",
                "C:\\Windows\\Fonts\\simsunb.ttf",
                "C:\\Windows\\Fonts\\SimsunExtG.ttf",
                "/Library/Fonts/Arial Unicode.ttf",
                "/System/Library/Fonts/Supplemental/Arial Unicode.ttf"
        };
        for (String path : candidates) {
            java.io.File file = new java.io.File(path);
            if (!file.isFile()) {
                continue;
            }
            TtfFont loaded = TtfFont.loadFile(file, size, supersample, atlas);
            if (loaded != null && (loaded.has('中') || loaded.has('あ') || loaded.has('한'))) {
                System.out.println("[onyx] CJK font: " + path + " @" + size + "pt");
                return loaded;
            }
        }
        return null;
    }

    private static TtfFont symbols;
    private static boolean symbolsLoaded;

    /**
     * System symbol fallback (Segoe UI Symbol on Windows). Google Sans Flex has no coverage for
     * dingbats used by server scoreboards (check marks / crosses), so those glyphs are borrowed
     * from a system font that does.
     */
    public static synchronized TtfFont symbols() {
        if (symbolsLoaded) {
            return symbols;
        }
        symbolsLoaded = true;
        String[] candidates = {
                "C:\\Windows\\Fonts\\seguisym.ttf",
                "C:\\Windows\\Fonts\\segoeui.ttf",
                "C:\\Windows\\Fonts\\arialuni.ttf",
                "/System/Library/Fonts/Apple Symbols.ttf",
                "/Library/Fonts/Arial Unicode.ttf"
        };
        for (String path : candidates) {
            java.io.File file = new java.io.File(path);
            if (!file.isFile()) {
                continue;
            }
            TtfFont loaded = TtfFont.loadFile(file, 9.0F);
            if (loaded != null && loaded.has('✔')) {
                symbols = loaded;
                break;
            }
        }
        return symbols;
    }

    /** Picks the first font that can actually render {@code c}. */
    static TtfFont runFont(TtfFont primary, char c) {
        if (c == ' ' || primary.has(c)) {
            return primary;
        }
        TtfFont cjk = fallbackFor(primary.size());
        if (cjk != null && cjk.has(c)) {
            return cjk;
        }
        TtfFont symbol = symbols();
        if (symbol != null && symbol.has(c)) {
            return symbol;
        }
        return primary;
    }

    /** Builds a style that draws every script in the text with one high-resolution CJK face. */
    private static Style cjkStyle(float primarySize, float size, float lineHeight) {
        TtfFont face = fallbackFor(primarySize);
        if (face == null) {
            return null;
        }
        String key = primarySize + ":" + size + ":" + lineHeight;
        Style style = CJK_STYLES.get(key);
        if (style == null) {
            style = fromFont(face, size, lineHeight);
            CJK_STYLES.put(key, style);
        }
        return style;
    }

    private static boolean isCjk(char c) {
        return c >= '\u2E80' && c <= '\u9FFF'
                || c >= '\u3000' && c <= '\u303F'
                || c >= '\u3040' && c <= '\u30FF'
                || c >= '\uAC00' && c <= '\uD7AF'
                || c >= '\uF900' && c <= '\uFAFF'
                || c >= '\uFF00' && c <= '\uFFEF';
    }

    private Typeface() {
    }

    private static TtfFont font(boolean medium, float optical) {
        if (optical > 16.0F) {
            if (medium) {
                if (medium24 == null) {
                    medium24 = TtfFont.load(ROOT + "24pt-medium.ttf", 24.0F);
                }
                return medium24;
            }
            if (regular24 == null) {
                regular24 = TtfFont.load(ROOT + "24pt-regular.ttf", 24.0F);
            }
            return regular24;
        }
        if (medium) {
            if (medium9 == null) {
                medium9 = TtfFont.load(ROOT + "9pt-medium.ttf", 9.0F);
            }
            return medium9;
        }
        if (regular9 == null) {
            regular9 = TtfFont.load(ROOT + "9pt-regular.ttf", 9.0F);
        }
        return regular9;
    }

    /** One entry of the type scale (Onyx's {@code OpticalWeightRecord}). */
    public static final class Style {
        private final float optical;
        private final float size;
        public final float lineHeight;
        private final boolean medium;
        private final TtfFont explicit;
        private final TtfFont secondary;

        private Style(float optical, float size, float lineHeight, boolean medium) {
            this(optical, size, lineHeight, medium, null, null);
        }

        private Style(TtfFont explicit, float size, float lineHeight) {
            this(0.0F, size, lineHeight, false, explicit, null);
        }

        private Style(float optical, float size, float lineHeight, boolean medium, TtfFont explicit) {
            this(optical, size, lineHeight, medium, explicit, null);
        }

        private Style(float optical, float size, float lineHeight, boolean medium, TtfFont explicit,
                      TtfFont secondary) {
            this.optical = optical;
            this.size = size;
            this.lineHeight = lineHeight;
            this.medium = medium;
            this.explicit = explicit;
            this.secondary = secondary;
        }

        public TtfFont font() {
            return explicit != null ? explicit : Typeface.font(medium, optical);
        }

        /** The face used for one character: primary, then this style's own second face, then the global fallbacks. */
        private TtfFont run(TtfFont primary, char c) {
            if (c == ' ' || primary.has(c)) {
                return primary;
            }
            if (secondary != null && secondary.has(c)) {
                return secondary;
            }
            return Typeface.runFont(primary, c);
        }

        /** Matrix scale from the rasterised optical size to the requested pixel size. */
        public float scale() {
            return size / font().size();
        }

        public float width(String text) {
            if (text == null || text.isEmpty()) {
                return 0.0F;
            }
            Style resolved = forText(text);
            if (resolved != this) {
                return resolved.width(text);
            }
            TtfFont primary = font();
            float total = 0.0F;
            int i = 0;
            while (i < text.length()) {
                TtfFont run = run(primary, text.charAt(i));
                int j = i;
                while (j < text.length() && run(primary, text.charAt(j)) == run) {
                    j++;
                }
                total += run.width(text.substring(i, j)) * (primary.size() / run.size());
                i = j;
            }
            return total * scale();
        }

        /**
         * Draws at the current origin, switching to the CJK or symbol fallback for unsupported
         * glyphs. Each fallback run is scaled relative to the primary raster so mixed text keeps
         * the requested pixel size (the ambient matrix was already scaled by {@link #scale()}).
         */
        public void draw(String text, int color) {
            if (text == null || text.isEmpty()) {
                return;
            }
            Style resolved = forText(text);
            if (resolved != this) {
                resolved.draw(text, color);
                return;
            }
            TtfFont primary = font();
            float primaryBaseline = primary.baseline();
            boolean mixed = mixedScripts(primary, text);
            float x = 0.0F;
            int i = 0;
            while (i < text.length()) {
                TtfFont run = run(primary, text.charAt(i));
                int j = i;
                while (j < text.length() && run(primary, text.charAt(j)) == run) {
                    j++;
                }
                String part = text.substring(i, j);
                float relative = primary.size() / run.size();
                // TtfFont positions glyphs from the top of its raster cell. A fallback face is
                // scaled up to the primary face's nominal size, which also scales its baseline;
                // without this correction a CJK run sits noticeably lower than the Latin run.
                float baselineShift = primaryBaseline - run.baseline() * relative;
                if (mixed && run != primary && primary.size() > 16.0F && primary.size() <= 24.0F) {
                    // CJK outlines sit a little lower than the Latin optical centre in the 24pt
                    // heading ramp. Small 9pt text must not receive this optical nudge: it moved
                    // the Han characters in the wallpaper date above their digits.
                    baselineShift -= primary.size() * 0.18F;
                }
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(x, baselineShift, 0.0F);
                if (relative != 1.0F) {
                    net.minecraft.client.renderer.GlStateManager.scale(relative, relative, 1.0F);
                }
                run.draw(part, color);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
                x += run.width(part) * relative;
                i = j;
            }
        }

        private boolean mixedScripts(TtfFont primary, String text) {
            boolean latin = false;
            boolean fallback = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') {
                    continue;
                }
                if (run(primary, c) == primary) {
                    latin = true;
                } else {
                    fallback = true;
                }
                if (latin && fallback) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Resolves a style for a concrete string. Standard Onyx styles that contain Han/kana/CJK
         * punctuation are drawn end-to-end with one CJK face; this keeps digits and Han characters
         * on the same baseline and uses the high-resolution atlas for every glyph in the line.
         * Explicit music-player faces and symbol-only strings keep their original mixed-run path.
         */
        public Style forText(String text) {
            if (text == null || text.isEmpty() || explicit != null || secondary != null) {
                return this;
            }
            TtfFont primary = font();
            TtfFont cjk = fallbackFor(primary.size());
            if (cjk == null) {
                return this;
            }
            boolean needsCjk = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') {
                    continue;
                }
                if (!cjk.has(c)) {
                    return this;
                }
                if (primary.has(c)) {
                    continue;
                }
                if (!isCjk(c)) {
                    return this;
                }
                needsCjk = true;
            }
            if (!needsCjk) {
                return this;
            }
            Style style = cjkStyle(primary.size(), size, lineHeight);
            return style == null ? this : style;
        }

        public float ascent() {
            return font().ascent() * scale();
        }

        public float descent() {
            return font().descent() * scale();
        }

        public float height() {
            return ascent() + descent();
        }

        public float size() {
            return size;
        }

        /** Whether every character can be rendered (primary, CJK fallback or symbol fallback). */
        public boolean supports(String text) {
            if (text == null || text.isEmpty()) {
                return true;
            }
            TtfFont primary = font();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == ' ') {
                    continue;
                }
                if (run(primary, c).has(c)) {
                    continue;
                }
                return false;
            }
            return true;
        }
    }
}
