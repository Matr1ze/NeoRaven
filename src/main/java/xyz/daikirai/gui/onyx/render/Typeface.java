package xyz.daikirai.gui.onyx.render;

/**
 * The Material type scale used by the Onyx ClickGUI, ported from {@code theme.Util2} and
 * {@code render.font.Util}.
 *
 * <p>Every text style uses the bundled MiSans Medium. The 9pt and 24pt entries still select
 * separate rasters so small text and headings keep their original pixel sizes.</p>
 */
public final class Typeface {

    private static final String TEXT_PATH = "/assets/clickgui/font/misans-medium.ttf";
    private static final String ICONS_PATH = "/assets/clickgui/font/materialicons-regular.ttf";

    /** Material Icons glyphs (16px reference size, drawn through {@link UI#icon}). */
    public static final TtfFont ICONS = TtfFont.load(ICONS_PATH, 16.0F);

    private static TtfFont text9;
    private static TtfFont text24;

    static TtfFont text9() {
        if (text9 == null) {
            text9 = loadText(9.0F, 2048);
        }
        return text9;
    }

    static TtfFont text24() {
        if (text24 == null) {
            text24 = loadText(24.0F, 4096);
        }
        return text24;
    }

    private static TtfFont loadText(float size, int atlas) {
        TtfFont font = TtfFont.load(TEXT_PATH, size, 4.0F, atlas);
        return font != null ? font : TtfFont.load(TEXT_PATH, size);
    }

    /** User scale for every Material style, driven by the CustomGui font settings. */
    public static float zoom() {
        return xyz.daikirai.module.render.CustomGuiModule.interfaceFontSize();
    }

    /** Extra advance per glyph, in the style's own pixel space. */
    public static float spacing() {
        return xyz.daikirai.module.render.CustomGuiModule.interfaceFontSpacing();
    }

    /** Whether Onyx's Material text draws the 1px offset shadow. */
    public static boolean shadow() {
        return xyz.daikirai.module.render.CustomGuiModule.interfaceFontShadow();
    }

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

    private static TtfFont fallback;
    private static boolean fallbackLoaded;

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
     * <p>Used by the music player, which draws everything in MiSans Medium: mixing faces inside
     * one string shifted the two runs vertically, because each face lays its glyphs out from its
     * own em-box top.</p>
     */
    public static Style fromFont(TtfFont font, float size, float lineHeight) {
        return new Style(font, size, lineHeight);
    }

    /** {@link #fromFont} exempt from the interface font scale. */
    public static Style fixedFromFont(TtfFont font, float size, float lineHeight) {
        return new Style(0.0F, size, lineHeight, false, font, null, true);
    }

    /**
     * A style with a secondary face for glyphs the primary one cannot draw.
     *
     * <p>The compact music card uses this to share its own MiSans raster before falling back to
     * the global system face.</p>
     */
    public static Style mixed(float optical, float size, float lineHeight, boolean medium, TtfFont secondary) {
        return new Style(optical, size, lineHeight, medium, null, secondary);
    }

    /** A style exempt from the interface font scale (fixed-layout screens with their own size control). */
    public static Style fixed(float optical, float size, float lineHeight, boolean medium) {
        return new Style(optical, size, lineHeight, medium, null, null, true);
    }

    /** {@link #fixed} with a secondary face. */
    public static Style fixedMixed(float optical, float size, float lineHeight, boolean medium, TtfFont secondary) {
        return new Style(optical, size, lineHeight, medium, null, secondary, true);
    }

    /**
     * System fallback for characters the bundled MiSans face does not contain.
     *
     * <p>MiSans covers Latin, Han and kana, so this face is only consulted for rare glyphs.
     * It is read from disk ({@code D:\Downloads} first), then Microsoft YaHei as the last
     * resort.</p>
     */
    public static synchronized TtfFont fallback() {
        if (fallbackLoaded) {
            return fallback;
        }
        fallbackLoaded = true;
        // 2048 keeps ~2000 CJK/kana glyphs resident; the old 1024 (≈500) filled up on a long
        // server name list and then silently dropped characters.
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
            TtfFont loaded = TtfFont.loadFile(file, 9.0F, 4.0F, 2048);
            if (loaded != null && (loaded.has('中') || loaded.has('あ') || loaded.has('한'))) {
                fallback = loaded;
                System.out.println("[onyx] CJK font: " + path);
                break;
            }
        }
        return fallback;
    }

    private static TtfFont symbols;
    private static boolean symbolsLoaded;

    /**
     * System symbol fallback (Segoe UI Symbol on Windows) for dingbats used by server
     * scoreboards (check marks / crosses) that MiSans does not contain.
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
        TtfFont cjk = fallback();
        if (cjk != null && cjk.has(c)) {
            return cjk;
        }
        TtfFont symbol = symbols();
        if (symbol != null && symbol.has(c)) {
            return symbol;
        }
        return primary;
    }

    private Typeface() {
    }

    private static int darken(int color) {
        int alpha = Math.round((color >>> 24) * 0.55F);
        return alpha << 24;
    }

    private static TtfFont font(boolean medium, float optical) {
        return optical > 16.0F ? text24() : text9();
    }

    /** One entry of the type scale (Onyx's {@code OpticalWeightRecord}). */
    public static final class Style {
        private final float optical;
        private final float size;
        public final float lineHeight;
        private final boolean medium;
        private final TtfFont explicit;
        private final TtfFont secondary;
        private final boolean fixed;

        private Style(float optical, float size, float lineHeight, boolean medium) {
            this(optical, size, lineHeight, medium, null, null, false);
        }

        private Style(TtfFont explicit, float size, float lineHeight) {
            this(0.0F, size, lineHeight, false, explicit, null, true);
        }

        private Style(float optical, float size, float lineHeight, boolean medium, TtfFont explicit) {
            this(optical, size, lineHeight, medium, explicit, null, false);
        }

        private Style(float optical, float size, float lineHeight, boolean medium, TtfFont explicit,
                      TtfFont secondary) {
            this(optical, size, lineHeight, medium, explicit, secondary, false);
        }

        private Style(float optical, float size, float lineHeight, boolean medium, TtfFont explicit,
                      TtfFont secondary, boolean fixed) {
            this.optical = optical;
            this.size = size;
            this.lineHeight = lineHeight;
            this.medium = medium;
            this.explicit = explicit;
            this.secondary = secondary;
            this.fixed = fixed;
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
            return size * (fixed ? 1.0F : Typeface.zoom()) / font().size();
        }

        public float width(String text) {
            if (text == null || text.isEmpty()) {
                return 0.0F;
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
            float gap = fixed ? 0.0F : Typeface.spacing();
            float extra = text.length() > 1 ? gap * (text.length() - 1) : 0.0F;
            return Math.max(0.0F, total * scale() + extra);
        }

        /**
         * Draws at the current origin, switching to the CJK or symbol fallback for unsupported
         * glyphs. Each fallback run is scaled relative to the primary raster so mixed text keeps
         * the requested pixel size (the ambient matrix was already scaled by {@link #scale()}).
         */
        public void draw(String text, int color) {
            TtfFont primary = font();
            float gap = fixed ? 0.0F : Typeface.spacing();
            boolean shadow = !fixed && Typeface.shadow();
            float inverse = gap == 0.0F ? 0.0F : gap / Math.max(0.0001F, scale());
            if (gap == 0.0F && !shadow) {
                runDraw(primary, text, color);
                return;
            }
            int shadowColor = shadow ? darken(color) : 0;
            float x = 0.0F;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                TtfFont run = run(primary, c);
                float relative = primary.size() / run.size();
                String part = String.valueOf(c);
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(x, 0.0F, 0.0F);
                if (relative != 1.0F) {
                    net.minecraft.client.renderer.GlStateManager.scale(relative, relative, 1.0F);
                }
                if (shadow) {
                    float shift = 2.0F / Math.max(0.0001F, scale() * relative);
                    net.minecraft.client.renderer.GlStateManager.pushMatrix();
                    net.minecraft.client.renderer.GlStateManager.translate(shift, shift, 0.0F);
                    run.draw(part, shadowColor);
                    net.minecraft.client.renderer.GlStateManager.popMatrix();
                }
                run.draw(part, color);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
                x += run.width(part) * relative + (i == text.length() - 1 ? 0.0F : inverse);
            }
        }

        private void runDraw(TtfFont primary, String text, int color) {
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
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(x, 0.0F, 0.0F);
                if (relative != 1.0F) {
                    net.minecraft.client.renderer.GlStateManager.scale(relative, relative, 1.0F);
                }
                run.draw(part, color);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
                x += run.width(part) * relative;
                i = j;
            }
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
