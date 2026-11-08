package xyz.daikirai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import xyz.daikirai.gui.onyx.material.Theme;

import java.util.Locale;

public final class Chat {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static final String COLOR = "§";
    /** Visible prefix text; the colour is the theme's primary role, emitted as §#RRGGBB. */
    public static final String PREFIX_TEXT = "Neo >> ";
    public static final char SECTION = '§';

    private Chat() {
    }

    public static boolean inWorld() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    /** {@code §#RRGGBB} for a colour role (only our own text pipeline understands this). */
    public static String hex(int argb) {
        return "§#" + String.format(Locale.ROOT, "%06X", argb & 0xFFFFFF);
    }

    /**
     * Legacy code per accent preset: chat is drawn by the vanilla renderer, which only understands
     * the sixteen two-character codes, so the closest palette entry stands in for the Material tone.
     */
    private static final char[] ACCENT_CODES = {'d', '9', 'b', 'a', '6', 'c', 'd', '8'};

    /** The sixteen palette colours, for the nearest-match fallback. */
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private static final String LEGACY = "0123456789abcdef";

    /** Chat prefix: the label in the theme accent, reset afterwards so messages keep their own codes. */
    public static String prefix() {
        return "§" + accentCode() + PREFIX_TEXT + "§r";
    }

    private static char accentCode() {
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            if (Theme.ACCENTS[i] == Theme.accent()) {
                return ACCENT_CODES[i];
            }
        }
        int rgb = Theme.accent() & 0xFFFFFF;
        char best = 'f';
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < LEGACY_RGB.length; i++) {
            int distance = distance(rgb, LEGACY_RGB[i]);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = LEGACY.charAt(i);
            }
        }
        return best;
    }

    private static int distance(int a, int b) {
        int dr = (a >> 16 & 0xFF) - (b >> 16 & 0xFF);
        int dg = (a >> 8 & 0xFF) - (b >> 8 & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    public static String color(String s) {
        if (s.isEmpty()) {
            return s;
        }
        return s.replace("&", "§");
    }

    public static String strip(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean skip = false;
        for (int i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (skip) {
                skip = false;
                continue;
            }
            if (c == SECTION) {
                // §#RRGGBB is a seven character code; legacy codes are two.
                if (i + 1 < s.length() && s.charAt(i + 1) == '#') {
                    i += 7;
                    continue;
                }
                skip = true;
                continue;
            }
            if (c <= '\u0014' || c >= '\u007f') continue;
            sb.append(c);
        }
        return sb.toString();
    }

    public static void print(String s) {
        if (!inWorld()) {
            return;
        }
        mc.thePlayer.addChatMessage(new ChatComponentText(color(prefix() + s)));
    }

    public static void moduleMessage(xyz.daikirai.module.Module m, String s) {
        print("&b" + m.getName() + "&7: &r" + s);
    }

    public static void raw(String s) {
        if (!inWorld()) {
            return;
        }
        mc.thePlayer.addChatMessage(new ChatComponentText(color(s)));
    }
}
