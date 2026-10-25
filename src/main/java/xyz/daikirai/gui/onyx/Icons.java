package xyz.daikirai.gui.onyx;

import xyz.daikirai.module.Category;

/** Material Icons codepoints used by the ClickGUI (same set the Onyx client shipped). */
public final class Icons {

    public static final String ARROW_BACK = "\ue5c4";
    public static final String SEARCH = "\ue8b6";
    public static final String CLOSE = "\ue5cd";
    public static final String CHECK = "\ue5ca";
    public static final String EXPAND_MORE = "\ue5cf";
    public static final String CHEVRON_RIGHT = "\ue5cc";
    public static final String ADD = "\ue145";
    public static final String TUNE = "\ue8b8";
    public static final String PALETTE = "\ue40a";
    /** Halftone-style disc, used for the colour picker's "follow theme" segment. */
    public static final String TONALITY = "\ue427";
    /** Brush, used for the colour picker's "custom" segment. */
    public static final String BRUSH = "\ue3ae";
    public static final String WARNING = "\ue001";
    public static final String REFRESH = "\ue5d5";
    public static final String KEYBOARD = "\ue312";
    public static final String SAVE = "\ue161";
    public static final String COPY = "\ue14d";
    public static final String DELETE = "\ue872";
    public static final String FOLDER = "\ue2c8";

    private Icons() {
    }

    /** Title-cased category label, as the Onyx rail showed it. */
    public static String label(Category category) {
        String name = category.name();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /** Category glyph; Onyx used the same five icons, the rest map to the closest match. */
    public static String forCategory(Category category) {
        switch (category) {
            case combat:
                return "\ue9e0";
            case movement:
                return "\ue566";
            case player:
                return "\ue7fd";
            case world:
                return "\ue894";
            case render:
                return "\ue8f4";
            case network:
                return "\ue640";
            case minigames:
                return "\uea28";
            case other:
                return "\ue8b8";
            case client:
                return "\ue871";
            case scripts:
                return "\ue14d";
            case profiles:
                return "\ue161";
            default:
                return "\ue8b8";
        }
    }
}
