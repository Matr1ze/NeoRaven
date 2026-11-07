package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import xyz.daikirai.gui.onyx.render.TtfFont;
import xyz.daikirai.gui.onyx.render.Typeface;

import java.io.File;

/**
 * The music player's type scale, drawn entirely in MiSans Medium.
 *
 * <p>Everything in the player UI (window, lyric view, compact card) and the rest of the ClickGUI
 * uses this one face for every language, so a single string is always a single run.</p>
 *
 * <p>MiSans covers Latin, Han and kana, which the previous OPPO Sans 4.0 face did not — Japanese
 * lyrics fell through to the symbol face and rendered as blanks. Only glyphs missing from MiSans
 * are read from the system fallback ({@link Typeface#fallback()}).</p>
 *
 * <p>This face ships inside the jar ({@code /assets/clickgui/font/misans-medium.ttf}); the fallback
 * face is still read from disk.</p>
 */
public final class MusicTypeface {

    private static final float RASTER_SIZE = 12.0F;
    private static final float SUPERSAMPLE = 4.0F;
    private static final int ATLAS = 4096;
    private static final String BUNDLED = "/assets/clickgui/font/misans-medium.ttf";
    private static final String MEDIUM_FILE = "MiSans-Medium.ttf";

    private static boolean loaded;
    private static boolean usable;
    private static TtfFont regular;
    private static TtfFont bold;

    public static final Typeface.Style DISPLAY = style(24.0F, 30.0F, true);
    public static final Typeface.Style HEADLINE_LARGE = style(20.0F, 26.0F, true);
    public static final Typeface.Style HEADLINE = style(14.0F, 18.0F, true);
    public static final Typeface.Style BODY = style(12.0F, 16.0F, false);
    public static final Typeface.Style LABEL = style(11.0F, 14.0F, true);
    public static final Typeface.Style LABEL_TIGHT = style(11.0F, 14.0F, true);
    public static final Typeface.Style CAPTION = style(10.0F, 13.0F, false);
    public static final Typeface.Style MICRO = style(9.0F, 12.0F, true);
    public static final Typeface.Style SEARCH = style(15.0F, 20.0F, false);
    public static final Typeface.Style ROW = style(14.0F, 18.0F, true);
    public static final Typeface.Style ROW_SUB = style(12.0F, 16.0F, false);
    public static final Typeface.Style CARD_TITLE = style(12.0F, 16.0F, true);
    /*
     * The compact card's own ramp. It is one face end to end (MiSans), so the vertical centring that
     * UI.text derives from the style's ascent/descent matches the glyphs that are actually drawn.
     */
    /** Collapsed island row: one MiSans run for every script (12px). */
    public static final Typeface.Style ISLAND_ROW = style(12.0F, 15.0F, true);
    /** Compact card headline (lyric or 歌名): 17px. */
    public static final Typeface.Style CARD_HEADLINE = style(17.0F, 22.0F, true);
    /** Compact card artist line: 12.5px. */
    public static final Typeface.Style CARD_ARTIST = style(12.5F, 16.0F, false);
    /** Compact card clock/times: 10.5px. */
    public static final Typeface.Style CARD_CLOCK = style(10.5F, 13.0F, true);
    /** Compact card: MiSans Medium with the player's own raster as the secondary face. */
    public static final Typeface.Style CARD_LYRIC = mixed(14.0F, 18.0F, true);
    public static final Typeface.Style CARD_SUB = mixed(11.0F, 14.0F, true);
    public static final Typeface.Style CARD_TIME = mixed(9.0F, 12.0F, true);

    private MusicTypeface() {
    }

    /** Builds a style at an arbitrary size (the lyric view rescales with the font-size setting). */
    public static Typeface.Style style(float size, float lineHeight, boolean strong) {
        load();
        if (usable) {
            return Typeface.fromFont(strong ? bold : regular, size, lineHeight);
        }
        return Typeface.fixed(size > 16.0F ? 24.0F : 9.0F, size, lineHeight, strong);
    }

    /** MiSans with the player's 12pt raster as the secondary face (the compact card only). */
    private static Typeface.Style mixed(float size, float lineHeight, boolean strong) {
        load();
        // Same optical pick as the ClickGUI ramp: 24pt raster for 14px+ headings, 9pt below.
        float optical = size >= 13.0F ? 24.0F : 9.0F;
        if (usable) {
            return Typeface.fixedMixed(optical, size, lineHeight, strong, strong ? bold : regular);
        }
        return Typeface.fixed(optical, size, lineHeight, strong);
    }

    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        regular = loadMiSans();
        if (regular == null) {
            System.out.println("[music] typeface: MiSans Medium not found, falling back to a system CJK face");
            regular = loadFace(new File("C:\\Windows\\Fonts", "msyh.ttc"));
        }
        if (regular == null) {
            regular = loadFace(new File("C:\\Windows\\Fonts", "Deng.ttf"));
        }
        if (regular == null) {
            regular = loadFace(new File("C:\\Windows\\Fonts", "simhei.ttf"));
        }
        // The player draws one weight: headings are separated by size, not by weight.
        bold = regular;
        usable = regular != null;
        if (!usable) {
            System.out.println("[music] typeface: no CJK face found, using the ClickGUI ramp");
        }
    }

    /** The bundled face first (the normal path), then a copy on disk for stripped builds. */
    private static TtfFont loadMiSans() {
        TtfFont bundled = TtfFont.load(BUNDLED, RASTER_SIZE, SUPERSAMPLE, ATLAS);
        if (bundled != null) {
            System.out.println("[music] typeface: MiSans Medium (bundled)");
            return bundled;
        }
        File[] candidates = {
                new File("D:\\Downloads", MEDIUM_FILE),
                new File(new File(Minecraft.getMinecraft().mcDataDir, "keystrokes"), "fonts/" + MEDIUM_FILE)
        };
        for (File file : candidates) {
            TtfFont font = loadFace(file);
            if (font != null) {
                System.out.println("[music] typeface: MiSans Medium (" + file + ")");
                return font;
            }
        }
        return null;
    }

    private static TtfFont loadFace(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        TtfFont font = TtfFont.loadFile(file, RASTER_SIZE, SUPERSAMPLE, ATLAS);
        if (font == null || !(font.has('中') || font.has('\u3042') || font.has('\ud55c'))) {
            return null;
        }
        return font;
    }

    /** Whether a CJK-capable face was found (used by the debug output). */
    public static boolean usable() {
        load();
        return usable;
    }
}
