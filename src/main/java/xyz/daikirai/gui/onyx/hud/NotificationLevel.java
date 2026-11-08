package xyz.daikirai.gui.onyx.hud;

import xyz.daikirai.gui.onyx.material.Scheme;

/** Original Onyx notification levels, glyphs and Material colour roles. */
public enum NotificationLevel {
    INFO("\ue88e"), SUCCESS("\ue5ca"), WARNING("\ue002"), ERROR("\ue001");

    public final String icon;
    NotificationLevel(String icon) { this.icon = icon; }

    public int color(Scheme scheme) {
        switch (this) {
            case SUCCESS: return scheme.tertiary;
            case WARNING: return scheme.secondary;
            case ERROR: return scheme.error;
            default: return scheme.primary;
        }
    }
}
