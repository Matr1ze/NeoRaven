package xyz.daikirai.gui.onyx.render;

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

    private static TtfFont fallback;
    private static boolean fallbackLoaded;

    /**
     * System CJK fallback font. Google Sans Flex has no CJK coverage, so song titles or script
     * names in Chinese/Japanese/Korean are drawn with a system font instead of rendering as blanks.
     *
     * <p>Microsoft YaHei (微软雅黑) is preferred: it ships as {@code msyh.ttc} on Windows 8+ and as
     * {@code msyh.ttf} on Windows 7. {@code Font.createFont} reads the first face of a TTC, which is
     * the regular weight.</p>
     */
    public static synchronized TtfFont fallback() {
        if (fallbackLoaded) {
            return fallback;
        }
        fallbackLoaded = true;
        String[] candidates = {
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
            TtfFont loaded = TtfFont.loadFile(file, 9.0F);
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

        private Style(float optical, float size, float lineHeight, boolean medium) {
            this.optical = optical;
            this.size = size;
            this.lineHeight = lineHeight;
            this.medium = medium;
        }

        public TtfFont font() {
            return Typeface.font(medium, optical);
        }

        /** Matrix scale from the rasterised optical size to the requested pixel size. */
        public float scale() {
            return size / font().size();
        }

        public float width(String text) {
            if (text == null || text.isEmpty()) {
                return 0.0F;
            }
            TtfFont primary = font();
            float total = 0.0F;
            int i = 0;
            while (i < text.length()) {
                TtfFont run = Typeface.runFont(primary, text.charAt(i));
                int j = i;
                while (j < text.length() && Typeface.runFont(primary, text.charAt(j)) == run) {
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
            TtfFont primary = font();
            float x = 0.0F;
            int i = 0;
            while (i < text.length()) {
                TtfFont run = Typeface.runFont(primary, text.charAt(i));
                int j = i;
                while (j < text.length() && Typeface.runFont(primary, text.charAt(j)) == run) {
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
                if (Typeface.runFont(primary, c).has(c)) {
                    continue;
                }
                return false;
            }
            return true;
        }
    }
}
