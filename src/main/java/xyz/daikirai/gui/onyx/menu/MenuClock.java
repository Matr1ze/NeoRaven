package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.renderer.GlStateManager;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.TtfFont;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.Calendar;

/**
 * The tablet-style clock in the middle of the Android title screen.
 *
 * <p>Two Material 3 Expressive faces, both driven by the real system clock:</p>
 *
 * <ul>
 *   <li><b>Digital</b> — the huge display time with the date underneath, the way a stock Android
 *   tablet's lock screen / widget stack reads. The digits get their own 192px raster so a 100px
 *   clock stays sharp instead of being an upscaled 24px atlas.</li>
 *   <li><b>Analog</b> — a tonal clock face with twelve ticks, rounded hour and minute hands and a
 *   small accent dot that rides the rim for the seconds.</li>
 * </ul>
 *
 * <p>Everything is authored in the menu's 640x360 design space and centred horizontally, so it keeps
 * its proportions at any window size. The style is persisted by {@link MenuTheme}.</p>
 */
public final class MenuClock {

    /** The Google Sans Flex face the rest of the UI uses; loaded at 48px so the atlas rasters 192px. */
    private static final String DISPLAY_FACE =
            "/assets/onyx/font/ttf/googlesansflex_24pt-regular.ttf";

    /** Design-space centre line of the 640x360 title screen. */
    private static final float CENTER_X = 320.0F;
    private static final float DIGITAL_Y = 122.0F;
    private static final float DIGITAL_SIZE = 104.0F;
    private static final float DIGITAL_DATE_Y = 190.0F;
    private static final float ANALOG_Y = 128.0F;
    private static final float ANALOG_RADIUS = 70.0F;
    private static final float ANALOG_DATE_Y = 238.0F;

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final Calendar CALENDAR = Calendar.getInstance();

    private static TtfFont digits;
    private static Typeface.Style display;
    private static boolean digitsTried;

    private MenuClock() {
    }

    /** 192px raster for the big readout: the stock 24pt atlas would be a 4x blow-up at this size. */
    private static Typeface.Style display() {
        if (!digitsTried) {
            digitsTried = true;
            digits = TtfFont.load(DISPLAY_FACE, 48.0F, 4.0F, 1024);
        }
        if (display == null) {
            display = digits == null ? Typeface.DISPLAY
                    : Typeface.fromFont(digits, DIGITAL_SIZE, DIGITAL_SIZE * 1.12F);
        }
        return display;
    }

    /** Reads the wall clock; every frame so the menu always shows the real time. */
    private static void now() {
        CALENDAR.setTimeInMillis(System.currentTimeMillis());
    }

    private static String time() {
        int hour = CALENDAR.get(Calendar.HOUR_OF_DAY);
        int minute = CALENDAR.get(Calendar.MINUTE);
        return hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    private static String date() {
        return (CALENDAR.get(Calendar.MONTH) + 1) + "月" + CALENDAR.get(Calendar.DAY_OF_MONTH) + "日"
                + WEEKDAYS[CALENDAR.get(Calendar.DAY_OF_WEEK) - 1];
    }

    /** The style the user picked (MenuTheme.CLOCK_DIGITAL / CLOCK_ANALOG). */
    public static int style() {
        return MenuTheme.clockStyle();
    }

    /**
     * Draws the clock for the current style, centred on the design-space centre line.
     *
     * @param appear 0..1 entrance progress (the title screen fades the clock in with the dock)
     * @param dim    1 = normal, lower while the settings sheet is open
     */
    public static void draw(UI ui, float appear, float dim) {
        now();
        float enter = Ease.EMPHASIZED.get(Material.clamp(appear, 0.0F, 1.0F));
        ui.alphaPush(enter * dim);
        ui.push();
        ui.translate(0.0F, (1.0F - enter) * 18.0F);
        if (style() == MenuTheme.CLOCK_ANALOG) {
            analog(ui);
        } else {
            digital(ui);
        }
        ui.pop();
        ui.alphaPop();
    }

    // ------------------------------------------------------------------ //
    // Digital
    // ------------------------------------------------------------------ //

    private static void digital(UI ui) {
        ui.text(display(), time(), CENTER_X, DIGITAL_Y, AndroidWallpaper.ink(), UI.ALIGN_CENTER);
        ui.text(Typeface.BODY, date(), CENTER_X, DIGITAL_DATE_Y, AndroidWallpaper.softInk(),
                UI.ALIGN_CENTER);
    }

    // ------------------------------------------------------------------ //
    // Analog
    // ------------------------------------------------------------------ //

    private static void analog(UI ui) {
        int ink = AndroidWallpaper.ink();
        ui.circle(CENTER_X, ANALOG_Y, ANALOG_RADIUS, AndroidWallpaper.surfaceHigh());
        ui.outline(CENTER_X - ANALOG_RADIUS, ANALOG_Y - ANALOG_RADIUS, ANALOG_RADIUS * 2.0F,
                ANALOG_RADIUS * 2.0F, ANALOG_RADIUS, 1.0F,
                Material.withAlpha(AndroidWallpaper.outlineVariant(), 0.7F));

        // Twelve ticks; the quarter hours are longer and stronger, the way MD3 watch faces read.
        for (int i = 0; i < 12; i++) {
            boolean major = i % 3 == 0;
            float length = major ? ANALOG_RADIUS * 0.13F : ANALOG_RADIUS * 0.07F;
            float thickness = major ? Math.max(2.4F, ANALOG_RADIUS * 0.045F)
                    : Math.max(1.6F, ANALOG_RADIUS * 0.026F);
            bar(ui, i * 30.0F, ANALOG_RADIUS * 0.80F, ANALOG_RADIUS * 0.80F + length, thickness,
                    Material.withAlpha(ink, major ? 0.8F : 0.32F));
        }

        int hour = CALENDAR.get(Calendar.HOUR);
        int minute = CALENDAR.get(Calendar.MINUTE);
        int second = CALENDAR.get(Calendar.SECOND);

        // Seconds ride the rim as an accent dot instead of a third hand: MD3 faces stay calm.
        double seconds = Math.toRadians(second * 6.0F);
        float dotRadius = Math.max(2.4F, ANALOG_RADIUS * 0.045F);
        ui.circle(CENTER_X + (float) Math.sin(seconds) * ANALOG_RADIUS * 0.86F,
                ANALOG_Y - (float) Math.cos(seconds) * ANALOG_RADIUS * 0.86F,
                dotRadius, AndroidWallpaper.primary());

        bar(ui, minute * 6.0F + second * 0.1F, -ANALOG_RADIUS * 0.10F, ANALOG_RADIUS * 0.46F,
                Math.max(3.2F, ANALOG_RADIUS * 0.075F), ink);
        bar(ui, (hour % 12) * 30.0F + minute * 0.5F, -ANALOG_RADIUS * 0.08F, ANALOG_RADIUS * 0.30F,
                Math.max(4.2F, ANALOG_RADIUS * 0.095F), ink);
        ui.circle(CENTER_X, ANALOG_Y, Math.max(3.0F, ANALOG_RADIUS * 0.055F), ink);

        ui.text(Typeface.BODY, date(), CENTER_X, ANALOG_DATE_Y, AndroidWallpaper.softInk(),
                UI.ALIGN_CENTER);
    }

    /**
     * One rounded bar pointing outwards from the face centre, from {@code start} to {@code end}
     * (design-space pixels along twelve o'clock, positive is away from the centre).
     *
     * <p>The menu draws in a y-down space, so a positive GL rotation sweeps the bar clockwise from
     * twelve o'clock — exactly the hour/minute angle the calendar reports.</p>
     */
    private static void bar(UI ui, float angle, float start, float end, float thickness, int color) {
        ui.push();
        ui.translate(CENTER_X, ANALOG_Y);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        ui.rounded(-thickness / 2.0F, -end, thickness, end - start, thickness / 2.0F, color);
        ui.pop();
    }

    /** Label for the clock-style button's accessibility text (English like the rest of the menu). */
    public static String styleName() {
        return style() == MenuTheme.CLOCK_ANALOG ? "Analog clock" : "Digital clock";
    }
}
