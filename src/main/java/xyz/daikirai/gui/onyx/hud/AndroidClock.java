package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.renderer.GlStateManager;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.Calendar;

/**
 * Material 3 "expressive" clock, ported from pvp_utils' {@code ClockHudRenderer} (Skia
 * {@code DIAL_SIZE} 128 face).
 *
 * <p>The face is the Material You watch widget: a scalloped dial (radius modulated by
 * {@code 61.8 + 2.2 * cos(12t)}, i.e. twelve soft lobes), two round-capped hands, and the seconds as a
 * dot orbiting the rim instead of a third hand. The date is drawn inside the dial and travels around
 * it opposite the seconds dot - one full turn per minute - exactly like the original.</p>
 *
 * <p>The Skia version rastered a date image and a soft shadow to textures; here the dial is built from
 * the UI's SDF circles (a base disc plus twelve lobe discs, which reproduces the modulated radius) and
 * the shadow uses the Material elevation shadow. Colours are passed in so the title screen can use the
 * wallpaper palette and the HUD module its own scheme.</p>
 */
public final class AndroidClock {

    /** Design size of the face; every length below is in these units (the source's DIAL_SIZE). */
    public static final float DIAL = 128.0F;
    private static final float CENTER = DIAL * 0.5F;
    /**
     * Dial outline: the source modulates the radius as {@code 61.8 + 2.2 * cos(12t)}, i.e. the rim
     * dips to 61.8 and swells to 64.0 twelve times. Drawing that with small circles on the rim turns
     * the lobes into beads, so the scallops are made from big circles instead: a disc at the notch
     * radius plus twelve circles whose arcs swell to the outer radius and meet the disc ~16 units
     * wide - a wide shallow wave, which is what the Material face looks like.
     */
    private static final float DIAL_INNER = 58.5F;
    private static final float DIAL_OUTER = 64.0F;
    private static final float LOBE_RADIUS = 16.0F;
    private static final float LOBE_DISTANCE = DIAL_OUTER - LOBE_RADIUS;
    private static final int LOBES = 12;
    /** Hands: length and thickness from the source, plus the seconds dot. */
    private static final float HOUR_LENGTH = 26.0F;
    private static final float MINUTE_LENGTH = 37.0F;
    private static final float HAND_THICKNESS = 10.5F;
    private static final float DOT_RADIUS = 4.5F;
    private static final float DOT_ORBIT = 49.0F;
    private static final float DATE_ORBIT = 54.0F;
    /** Still date: parked in the lower-left of the dial, tilted like the reference screenshot. */
    private static final float DATE_STILL_ORBIT = 34.0F;
    private static final float DATE_STILL_ANGLE = 225.0F;
    private static final float DATE_STILL_TILT = 45.0F;
    private static final float DATE_SIZE = 10.5F;
    /** The tone the source mixes into the seconds dot on top of the accent. */
    private static final int DOT_TINT = 0xFFFFE9AD;

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final String[] WEEKDAYS_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final Calendar CALENDAR = Calendar.getInstance();

    private AndroidClock() {
    }

    /** Colours of one face; the title screen and the HUD module each build their own. */
    public static final class Palette {
        public final int face;
        public final int ink;
        public final int accent;
        public final int dateInk;
        public final boolean shadow;

        public Palette(int face, int ink, int accent, int dateInk, boolean shadow) {
            this.face = face;
            this.ink = ink;
            this.accent = accent;
            this.dateInk = dateInk;
            this.shadow = shadow;
        }

        /** Title screen: the wallpaper's own palette. */
        public static Palette wallpaper(boolean shadow) {
            return new Palette(xyz.daikirai.gui.onyx.menu.AndroidWallpaper.surfaceHigh(),
                    xyz.daikirai.gui.onyx.menu.AndroidWallpaper.ink(),
                    xyz.daikirai.gui.onyx.menu.AndroidWallpaper.primary(),
                    xyz.daikirai.gui.onyx.menu.AndroidWallpaper.softInk(), shadow);
        }

        /** HUD: the active Material scheme, face tinted with the accent like the source does. */
        public static Palette scheme(boolean shadow) {
            Scheme scheme = Theme.scheme();
            // The source's face is a deep, slightly blue-tinted surface; surfaceDim keeps the dial
            // darker than the cards around it in both palettes.
            int face = Material.mix(scheme.surfaceDim, scheme.primary, 0.16F);
            return new Palette(face, scheme.onSurface, scheme.primary, scheme.onSurfaceVariant, shadow);
        }
    }

    /** One frame of hands/date, resolved once so the drawing code stays linear. */
    public static final class Hands {
        public final float hourAngle;
        public final float minuteAngle;
        public final float secondAngle;
        public final String date;

        Hands(float hourAngle, float minuteAngle, float secondAngle, String date) {
            this.hourAngle = hourAngle;
            this.minuteAngle = minuteAngle;
            this.secondAngle = secondAngle;
            this.date = date;
        }
    }

    /** Reads the wall clock; call once per frame and pass the result to {@link #draw}. */
    public static Hands hands(boolean chinese) {
        long millis = System.currentTimeMillis();
        CALENDAR.setTimeInMillis(millis);
        float seconds = CALENDAR.get(Calendar.SECOND) + (millis % 1000L) / 1000.0F;
        float minutes = CALENDAR.get(Calendar.MINUTE) + seconds / 60.0F;
        float hours = (CALENDAR.get(Calendar.HOUR) % 12) + minutes / 60.0F;
        String weekday = chinese ? WEEKDAYS[CALENDAR.get(Calendar.DAY_OF_WEEK) - 1]
                : WEEKDAYS_EN[CALENDAR.get(Calendar.DAY_OF_WEEK) - 1];
        String date = chinese ? weekday + " " + CALENDAR.get(Calendar.DAY_OF_MONTH)
                : weekday + " " + CALENDAR.get(Calendar.DAY_OF_MONTH);
        return new Hands(hours * 30.0F, minutes * 6.0F, seconds * 6.0F, date);
    }

    /**
     * Draws the face centred on {@code (cx, cy)} with an overall diameter of {@code size}.
     *
     */
    public static void draw(UI ui, float cx, float cy, float size, Palette palette, Hands hands,
                            boolean showDate, boolean dateFollowsSeconds) {
        float scale = size / DIAL;
        ui.push();
        ui.translate(cx - CENTER * scale, cy - CENTER * scale);
        ui.scale(scale, 0.0F, 0.0F);
        if (palette.shadow) {
            ui.shadow(0.0F, 0.0F, DIAL, DIAL, DIAL / 2.0F, 3);
        }
        // The face is a flat Material surface on purpose: the frost primitive is a rounded rect, so a
        // frosted dial would either lose the scalloped silhouette or turn the lobes into beads.
        dial(ui, palette.face);

        int minuteColor = Material.mix(palette.accent, palette.ink, 0.64F);
        int hourColor = Material.mix(minuteColor, palette.face, 0.44F);
        hand(ui, hands.hourAngle, HOUR_LENGTH, hourColor);
        hand(ui, hands.minuteAngle, MINUTE_LENGTH, minuteColor);

        double radians = Math.toRadians(hands.secondAngle);
        ui.circle(CENTER + (float) Math.sin(radians) * DOT_ORBIT,
                CENTER - (float) Math.cos(radians) * DOT_ORBIT, DOT_RADIUS,
                Material.mix(palette.accent, DOT_TINT, 0.86F));

        if (showDate) {
            ui.push();
            if (dateFollowsSeconds) {
                // Upstream behaviour: the label rides the rim opposite the seconds dot.
                ui.translate(CENTER, CENTER);
                GlStateManager.rotate(hands.secondAngle + 180.0F, 0.0F, 0.0F, 1.0F);
                ui.text(Typeface.LABEL_TIGHT, hands.date, 0.0F, -DATE_ORBIT, palette.dateInk,
                        UI.ALIGN_CENTER);
            } else {
                // Reference look: a still label in the lower-left, tilted with the dial's contour.
                double still = Math.toRadians(DATE_STILL_ANGLE);
                ui.translate(CENTER + (float) Math.sin(still) * DATE_STILL_ORBIT,
                        CENTER - (float) Math.cos(still) * DATE_STILL_ORBIT);
                GlStateManager.rotate(DATE_STILL_TILT, 0.0F, 0.0F, 1.0F);
                ui.text(Typeface.LABEL_TIGHT, hands.date, 0.0F, 0.0F, palette.dateInk, UI.ALIGN_CENTER);
            }
            ui.pop();
        }
        ui.pop();
    }

    /** The scalloped dial: a disc plus twelve lobe discs, which is the source's modulated radius. */
    private static void dial(UI ui, int color) {
        ui.circle(CENTER, CENTER, DIAL_INNER, color);
        for (int i = 0; i < LOBES; i++) {
            double radians = Math.toRadians(i * 360.0F / LOBES);
            ui.circle(CENTER + (float) Math.sin(radians) * LOBE_DISTANCE,
                    CENTER - (float) Math.cos(radians) * LOBE_DISTANCE, LOBE_RADIUS, color);
        }
    }

    /**
     * One round-capped hand pointing outwards from the centre. The menu/HUD space is y-down, so a
     * positive GL rotation sweeps clockwise from twelve o'clock, matching the calendar angles.
     */
    private static void hand(UI ui, float angle, float length, int color) {
        ui.push();
        ui.translate(CENTER, CENTER);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        ui.rounded(-HAND_THICKNESS / 2.0F, -length, HAND_THICKNESS, length,
                HAND_THICKNESS / 2.0F, color);
        ui.pop();
    }

    /** The little dial preview drawn in the title screen's "Clock face" row. */
    public static void glyph(UI ui, float cx, float cy, float radius, Scheme scheme) {
        float scale = radius * 2.0F / DIAL;
        ui.push();
        ui.translate(cx - CENTER * scale, cy - CENTER * scale);
        ui.scale(scale, 0.0F, 0.0F);
        dial(ui, Material.withAlpha(scheme.onSurface, 0.9F));
        ui.pop();
    }

    /** Size of the date label at a given clock size, for callers that reserve space. */
    public static float dateBudget(float size) {
        return size / DIAL * DATE_SIZE * 2.0F;
    }
}
