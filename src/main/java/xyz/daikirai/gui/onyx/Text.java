package xyz.daikirai.gui.onyx;

/**
 * Small string helpers for text that comes from the Raven module registry.
 *
 * <p>Raven formats its status suffixes with Minecraft colour codes ({@code §7}, {@code §b}, …).
 * The Material font cannot interpret those, so they are stripped before anything is measured or
 * drawn — otherwise a card would literally read "Velocity§7 100%".</p>
 */
public final class Text {

    private Text() {
    }

    /** Removes Minecraft formatting codes ({@code §} plus the following character). */
    public static String strip(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder builder = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                if (builder == null) {
                    builder = new StringBuilder(text.length());
                    builder.append(text, 0, i);
                }
                i++;
                continue;
            }
            if (builder != null) {
                builder.append(c);
            }
        }
        return builder == null ? text : builder.toString().trim();
    }
}
