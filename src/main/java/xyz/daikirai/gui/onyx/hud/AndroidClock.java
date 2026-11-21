package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.Calendar;

/** Shared real-time analog dial styled from the user-supplied md3-clock.html. */
public final class AndroidClock {

    /** Design size of the face; every length below is in these units (the source's DIAL_SIZE). */
    public static final float DIAL = 128.0F;
    private static final float CENTER = DIAL * 0.5F;
    private static final float SVG_SCALE = DIAL / ClockFaceGeometry.SVG_SIZE;
    private static final float HOUR_LENGTH = ClockFaceGeometry.HOUR_LENGTH * SVG_SCALE;
    private static final float MINUTE_LENGTH = ClockFaceGeometry.MINUTE_LENGTH * SVG_SCALE;
    private static final float HAND_THICKNESS = ClockFaceGeometry.STROKE * SVG_SCALE;
    private static final float DOT_RADIUS = ClockFaceGeometry.DOT_RADIUS * SVG_SCALE;
    private static final float DOT_ORBIT = ClockFaceGeometry.DOT_ORBIT * SVG_SCALE;
    private static Shader faceShader;
    private static boolean faceShaderTried;
    private static final float DATE_ORBIT = 46.0F;
    /** Still date: parked in the lower-left of the dial, tilted like the reference screenshot. */
    private static final float DATE_STILL_ORBIT = 34.0F;
    private static final float DATE_STILL_ANGLE = 225.0F;
    private static final float DATE_STILL_TILT = 45.0F;
    private static final float DATE_SIZE = 10.5F;
    private static final int DOT_COLOR = 0xFF876F90;

    private static final String[] WEEKDAYS = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
    private static final String[] WEEKDAYS_EN = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
    private static final Calendar CALENDAR = Calendar.getInstance();

    private AndroidClock() {
    }

    /** Reference colour roles, shared by the menu and HUD. */
    public static final class Palette {
        public final int face;
        public final int ink;
        public final int accent;
        public final int dateInk;
        public final int dot;
        public final boolean shadow;

        public Palette(int face, int ink, int accent, int dateInk, boolean shadow) {
            this(face, ink, accent, dateInk, DOT_COLOR, shadow);
        }

        private Palette(int face, int ink, int accent, int dateInk, int dot, boolean shadow) {
            this.face = face;
            this.ink = ink;
            this.accent = accent;
            this.dateInk = dateInk;
            this.dot = dot;
            this.shadow = shadow;
        }

        private static Palette reference(boolean shadow) {
            if (Theme.dark()) return new Palette(0xFF242A35, 0xFFBBC5D7, 0xFF9ABAF0,
                    0xFFDCE2F0, 0xFFC9A5CF, shadow);
            return new Palette(0xFFEBF1FE, 0xFF414857, 0xFF5979A8, 0xFF414857, shadow);
        }
        public static Palette wallpaper(boolean shadow) { return reference(shadow); }
        public static Palette scheme(boolean shadow) { return reference(shadow); }

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
        return handsAt(System.currentTimeMillis(), chinese);
    }

    private static Hands handsAt(long millis, boolean chinese) {
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
            float inset = DIAL * 0.055F;
            ui.shadow(inset, inset, DIAL - inset * 2, DIAL - inset * 2, DIAL / 2.0F - inset, 3);
        }
        // The reference face is flat; its harmonic outline is drawn independently of the hands.
        dial(ui, palette.face);

        hand(ui, hands.hourAngle, HOUR_LENGTH, palette.ink);
        hand(ui, hands.minuteAngle, MINUTE_LENGTH, palette.accent);
        double radians = Math.toRadians(hands.secondAngle);
        ui.circle(CENTER + (float) Math.sin(radians) * DOT_ORBIT,
                CENTER - (float) Math.cos(radians) * DOT_ORBIT, DOT_RADIUS, palette.dot);

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

    /** Analytic harmonic contour with pixel-space antialiasing; no per-frame bitmap upload. */
    private static void dial(UI ui, int color) {
        if (!faceShaderTried) { faceShaderTried = true; faceShader = Shader.load("m3_clock_face", "onyx_shape"); }
        int ink = Material.withAlpha(color, Material.alpha(color) / 255.0F * ui.alpha());
        GlStateManager.disableTexture2D();
        GL11.glColor4f(Material.red(ink)/255.0F,Material.green(ink)/255.0F,Material.blue(ink)/255.0F,Material.alpha(ink)/255.0F);
        if (faceShader != null && faceShader.valid()) {
            faceShader.use(); GL11.glBegin(GL11.GL_QUADS);
            vertex(0,0); vertex(0,DIAL); vertex(DIAL,DIAL); vertex(DIAL,0);
            GL11.glEnd(); Shader.unbind();
        } else {
            // Keep the supplied contour even on drivers without a usable shader.
            GL11.glBegin(GL11.GL_TRIANGLE_FAN);GL11.glVertex2f(CENTER,CENTER);
            for (int i=ClockFaceGeometry.OUTLINE.length-1;i>=0;i--) {
                double[] p=ClockFaceGeometry.OUTLINE[i];GL11.glVertex2d(p[0]*SVG_SCALE,p[1]*SVG_SCALE);
            }
            GL11.glEnd();
        }
        GL11.glColor4f(1,1,1,1);GlStateManager.resetColor();
    }
    private static void vertex(float x,float y) {
        GL11.glTexCoord2f((x-CENTER)/SVG_SCALE,(y-CENTER)/SVG_SCALE);GL11.glVertex2f(x,y);
    }

    /**
     * One round-capped hand pointing outwards from the centre. The menu/HUD space is y-down, so a
     * positive GL rotation sweeps clockwise from twelve o'clock, matching the calendar angles.
     */
    private static void hand(UI ui, float angle, float length, int color) {
        ui.push();
        ui.translate(CENTER, CENTER);
        GlStateManager.rotate(angle, 0.0F, 0.0F, 1.0F);
        ui.rounded(-HAND_THICKNESS / 2.0F, -length - HAND_THICKNESS / 2.0F, HAND_THICKNESS, length + HAND_THICKNESS,
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
