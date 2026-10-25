package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * Stateless Material 3 primitives shared by the ClickGUI widgets.
 *
 * <p>The switch is a direct port of Onyx's {@code GuiComponentSub7}: 38x22 track, knob growing
 * from 6px to 8.5px (10px while held), 1.5px outline fading out while the track fills, and a
 * ripple highlight under the knob.</p>
 */
public final class Widgets {

    public static final float SWITCH_WIDTH = 38.0F;
    public static final float SWITCH_HEIGHT = 22.0F;

    private Widgets() {
    }

    /**
     * Draws the Material switch.
     *
     * @param progress 0 = off, 1 = on
     * @param hovered  whether the pointer is over the track
     * @param pressed  whether the track is being held
     */
    public static void toggle(UI ui, Scheme scheme, float x, float y, float progress, boolean hovered, boolean pressed) {
        float width = SWITCH_WIDTH;
        float height = SWITCH_HEIGHT;
        float t = Material.clamp(progress, 0.0F, 1.0F);
        float radius = height / 2.0F;

        int track = Material.mix(scheme.surfaceContainerHighest, scheme.primary, t);
        int knob = Material.mix(scheme.outline, scheme.onPrimary, t);

        ui.rounded(x, y, width, height, radius, track);

        // Material's track state layer stays inside the pill; a circular halo would bleed over the
        // 1.5px outline and read as a torn corner, which is what the old rendering did.
        float state = pressed ? 0.1F : (hovered ? 0.08F : 0.0F);
        if (state > 0.0F) {
            int stateColor = Material.mix(scheme.onSurfaceVariant, scheme.primary, t);
            ui.rounded(x, y, width, height, radius, Material.withAlpha(stateColor, state));
        }

        if (t < 0.999F) {
            ui.outline(x, y, width, height, radius, 1.5F,
                    Material.withAlpha(scheme.outline, 1.0F - t));
        }

        float centerY = y + height / 2.0F;
        float travel = width - height;
        float centerX = x + height / 2.0F + travel * t;
        // Onyx's handle: 12px off, 17px on, 20px while held.
        float knobRadius = 6.0F + 2.5F * t + (pressed ? 1.5F : 0.0F);
        ui.circle(centerX, centerY, knobRadius, knob);
    }

    /**
     * Draws a Material slider track with its filled part and thumb.
     *
     * @param fraction value in 0..1
     */
    public static void slider(UI ui, Scheme scheme, float x, float y, float width, float fraction,
                              boolean hovered, boolean active) {
        float trackHeight = active || hovered ? 6.0F : 4.0F;
        float trackY = y - trackHeight / 2.0F;
        float f = Material.clamp(fraction, 0.0F, 1.0F);
        float thumbRadius = active ? 8.0F : (hovered ? 7.50F : 7.0F);

        ui.rounded(x, trackY, width, trackHeight, trackHeight / 2.0F, scheme.surfaceContainerHighest);
        float filled = width * f;
        if (filled > 0.0F) {
            ui.rounded(x, trackY, filled, trackHeight, trackHeight / 2.0F, scheme.primary);
        }
        if (active || hovered) {
            ui.circle(x + filled, trackY + trackHeight / 2.0F, 12.0F, Material.withAlpha(scheme.primary, 0.12F));
        }
        ui.circle(x + filled, trackY + trackHeight / 2.0F, thumbRadius + 0.5F, Material.withAlpha(scheme.shadow, 0.3F));
        ui.circle(x + filled, trackY + trackHeight / 2.0F, thumbRadius, scheme.primary);
    }

    /** Material menu surface with one row per option. */
    public static void menu(UI ui, Scheme scheme, float x, float y, float width, String[] options, int selected,
                            int hovered, float progress) {
        float rowHeight = 32.0F;
        float height = options.length * rowHeight + 8.0F;
        ui.alphaPush(progress);
        ui.shadow(x, y, width, height, 8.0F, 2);
        ui.rounded(x, y, width, height, 8.0F, scheme.surfaceContainerHigh);
        for (int i = 0; i < options.length; i++) {
            float rowY = y + 4.0F + i * rowHeight;
            if (i == hovered) {
                ui.rounded(x + 4.0F, rowY, width - 8.0F, rowHeight, 6.0F,
                        Material.withAlpha(scheme.onSurface, 0.08F));
            }
            int color = i == selected ? scheme.primary : scheme.onSurface;
            ui.text(Typeface.BODY_MEDIUM, options[i], x + 12.0F, rowY + rowHeight / 2.0F, color, UI.ALIGN_LEFT);
            if (i == selected) {
                ui.icon(Icons.CHECK, x + width - 18.0F, rowY + rowHeight / 2.0F, 14.0F, scheme.primary);
            }
        }
        ui.alphaPop();
    }

    /** Small 1px divider used between setting groups. */
    public static void divider(UI ui, Scheme scheme, float x, float y, float width) {
        ui.rect(x, y, width, 1.0F, Material.withAlpha(scheme.outlineVariant, 0.8F));
    }

    /** Rounded hover highlight ("state layer") used by rows and list items. */
    public static void stateLayer(UI ui, Scheme scheme, float x, float y, float w, float h, float radius,
                                  float hover, float press, int baseColor) {
        float alpha = press > 0.0F ? 0.1F : 0.08F * hover;
        if (alpha <= 0.001F) {
            return;
        }
        ui.rounded(x, y, w, h, Theme.radius(radius, w, h), Material.withAlpha(baseColor, alpha));
    }
}
