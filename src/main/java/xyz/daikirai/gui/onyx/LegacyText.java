package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft legacy ({@code §}) text, split into coloured runs for the Material font.
 *
 * <p>The ported fonts have no colour support of their own, and simply stripping the codes would
 * flatten a Hypixel sidebar into one grey wall — so the scoreboard parses {@code §x} into runs and
 * draws each run with its own colour. {@code §m} / {@code §n} are honoured as well: Hypixel draws
 * its separators as struck-through spaces, which would otherwise disappear entirely. Bold, italic
 * and obfuscated are ignored, and the codes are never measured.</p>
 */
public final class LegacyText {

    private static final String ELLIPSIS = "...";

    private LegacyText() {
    }

    /** One styled run of a legacy string. */
    public static final class Run {
        public final String text;
        public final int color;
        public final boolean strikethrough;
        public final boolean underline;

        Run(String text, int color, boolean strikethrough, boolean underline) {
            this.text = text;
            this.color = color;
            this.strikethrough = strikethrough;
            this.underline = underline;
        }
    }

    /** Splits {@code text} into runs; {@code baseColor} is used until the first colour code. */
    public static List<Run> parse(String text, int baseColor) {
        List<Run> runs = new ArrayList<Run>();
        if (text == null || text.isEmpty()) {
            return runs;
        }
        StringBuilder current = new StringBuilder();
        int color = baseColor;
        boolean strikethrough = false;
        boolean underline = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                // A trailing lone code indicator would otherwise be rasterised as a glyph.
                if (i + 1 >= text.length()) {
                    break;
                }
                char code = Character.toLowerCase(text.charAt(++i));
                int parsed = colorOf(code);
                if (parsed != 0) {
                    flush(runs, current, color, strikethrough, underline);
                    color = parsed;
                    // A colour code resets every formatting flag, exactly like the vanilla font.
                    strikethrough = false;
                    underline = false;
                } else if (code == 'm') {
                    flush(runs, current, color, strikethrough, underline);
                    strikethrough = true;
                } else if (code == 'n') {
                    flush(runs, current, color, strikethrough, underline);
                    underline = true;
                } else if (code == 'r') {
                    flush(runs, current, color, strikethrough, underline);
                    color = baseColor;
                    strikethrough = false;
                    underline = false;
                }
                continue;
            }
            current.append(c);
        }
        flush(runs, current, color, strikethrough, underline);
        return runs;
    }

    private static void flush(List<Run> runs, StringBuilder current, int color,
                              boolean strikethrough, boolean underline) {
        if (current.length() > 0) {
            runs.add(new Run(current.toString(), color, strikethrough, underline));
            current.setLength(0);
        }
    }

    /** Width of the visible text, ignoring the formatting codes. */
    public static float width(Typeface.Style style, String text) {
        if (text == null || text.isEmpty()) {
            return 0.0F;
        }
        float width = 0.0F;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\u00a7' && i + 1 < text.length()) {
                if (i > start) {
                    width += style.width(text.substring(start, i));
                }
                i++;
                start = i + 1;
            }
        }
        if (start < text.length()) {
            width += style.width(text.substring(start));
        }
        return width;
    }

    /** Draws with per-run colours, using the same baseline/alignment rules as {@link UI#text}. */
    public static void draw(UI ui, Typeface.Style style, String text, float x, float y, int align,
                            int baseColor) {
        List<Run> runs = parse(text, baseColor);
        if (runs.isEmpty()) {
            return;
        }
        float total = 0.0F;
        for (Run run : runs) {
            total += style.width(run.text);
        }
        float cursor = align == UI.ALIGN_CENTER ? x - total / 2.0F
                : align == UI.ALIGN_RIGHT ? x - total : x;
        for (Run run : runs) {
            float runWidth = style.width(run.text);
            ui.text(style, run.text, cursor, y, run.color, UI.ALIGN_LEFT);
            if (run.strikethrough) {
                ui.rect(cursor, y - 0.5F, runWidth, 1.0F, run.color);
            }
            if (run.underline) {
                ui.rect(cursor, y + style.ascent() * 0.5F + 1.0F, runWidth, 1.0F, run.color);
            }
            cursor += runWidth;
        }
    }

    /** Shortens the visible text to {@code maxWidth}, keeping the colour codes before the cut. */
    public static String truncate(Typeface.Style style, String text, float maxWidth) {
        if (text == null || text.isEmpty() || width(style, text) <= maxWidth) {
            return text;
        }
        float limit = maxWidth - style.width(ELLIPSIS);
        if (limit <= 0.0F) {
            return ELLIPSIS;
        }
        StringBuilder out = new StringBuilder(text.length());
        float used = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                out.append(c).append(text.charAt(++i));
                continue;
            }
            float width = style.width(String.valueOf(c));
            if (used + width > limit) {
                break;
            }
            out.append(c);
            used += width;
        }
        return out.append(ELLIPSIS).toString();
    }

    private static int colorOf(char code) {
        switch (code) {
            case '0':
                return 0xFF000000;
            case '1':
                return 0xFF0000AA;
            case '2':
                return 0xFF00AA00;
            case '3':
                return 0xFF00AAAA;
            case '4':
                return 0xFFAA0000;
            case '5':
                return 0xFFAA00AA;
            case '6':
                return 0xFFFFAA00;
            case '7':
                return 0xFFAAAAAA;
            case '8':
                return 0xFF555555;
            case '9':
                return 0xFF5555FF;
            case 'a':
                return 0xFF55FF55;
            case 'b':
                return 0xFF55FFFF;
            case 'c':
                return 0xFFFF5555;
            case 'd':
                return 0xFFFF55FF;
            case 'e':
                return 0xFFFFFF55;
            case 'f':
                return 0xFFFFFFFF;
            default:
                return 0;
        }
    }
}
