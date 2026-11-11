package xyz.daikirai.gui.onyx.menu;

import xyz.daikirai.gui.onyx.hud.AndroidClock;
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

    /** The shared MiSans face used across the client; loaded at 48px so the atlas rasters 192px. */
    private static final String DISPLAY_FACE =
            xyz.daikirai.gui.onyx.render.Typeface.familyPath();

    /** Design-space centre line of the 640x360 title screen. */
    private static final float CENTER_X = 320.0F;
    private static final float DIGITAL_Y = 122.0F;
    private static final float DIGITAL_SIZE = 104.0F;
    private static final float DIGITAL_DATE_Y = 190.0F;
    private static final float ANALOG_Y = 128.0F;
    private static final float ANALOG_RADIUS = 70.0F;

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
                    : Typeface.fixedFromFont(digits, DIGITAL_SIZE, DIGITAL_SIZE * 1.12F);
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
    // Analog (Material 3 expressive face, ported from pvp_utils' ClockHudRenderer)
    // ------------------------------------------------------------------ //

    private static void analog(UI ui) {
        AndroidClock.Hands hands = AndroidClock.hands(true);
        AndroidClock.draw(ui, CENTER_X, ANALOG_Y, ANALOG_RADIUS * 2.0F,
                AndroidClock.Palette.wallpaper(true), hands, true, false);
    }

    /** Label for the clock-style button's accessibility text (English like the rest of the menu). */
    public static String styleName() {
        return xyz.daikirai.util.Lang.text(style() == MenuTheme.CLOCK_ANALOG ? "Analog clock" : "Digital clock");
    }
}
