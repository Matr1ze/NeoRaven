package xyz.daikirai.gui;

import java.awt.Color;
import java.io.IOException;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.setting.ColorSetting;

/**
 * Material 3 Expressive colour picker for a {@link ColorSetting}, opened from a module row.
 *
 * <p>The sheet is built from the same parts as the ClickGUI and the HUD cards: a frosted surface
 * ({@link UI#frost} over {@link Blur}, exactly the material the panel and the cards use), the
 * scheme's own colour roles, the Google Sans type scale and the SDF shape pipeline. The dialog
 * sits on the usual scrim, so opening it reads as the panel turning into a colour sheet rather
 * than a second, differently styled window.</p>
 *
 * <p>Expressive traits, as opposed to the plain Material 3 the port started from: a 30px sheet
 * with a circular icon badge, a segmented button whose thumb slides on a {@link Spring} (the
 * under-damped one from {@code render.Spring}, so it lands with a small bounce), gradient HSV
 * plane and hue ramp with spring-grown handles, swatch chips that scale under the pointer, and
 * "Cancel"/"Save" as a text button and a filled button that both compress while held. The sheet
 * enters with a spring-driven scale and a corner-radius morph instead of appearing on one frame.</p>
 *
 * <p>Behaviour is unchanged from the previous picker: all edits stay local until Save, the HSV
 * fields drive both the plane and the hue strip, following the theme previews
 * {@link ColorSetting#themeColor()} without touching the stored custom colour, and Save writes
 * {@link ColorSetting#setAppearance(int, boolean)} so the custom colour survives a theme-following
 * save. Escape and the close button cancel, Enter saves.</p>
 */
public final class ColorEditor extends GuiScreen {

    // ------------------------------------------------------------------ //
    // Sheet geometry (ClickGUI units, scaled to fit the window)
    // ------------------------------------------------------------------ //

    private static final float W = 340.0F;
    private static final float H = 415.0F;
    private static final float RADIUS = 30.0F;
    private static final float MARGIN = 24.0F;
    private static final float COLUMN = W - MARGIN * 2.0F;

    private static final float HEADER_Y = 18.0F;
    private static final float BADGE = 40.0F;

    private static final float SEGMENT_Y = 70.0F;
    private static final float SEGMENT_H = 44.0F;

    private static final float PLANE_Y = 126.0F;
    private static final float PLANE_H = 140.0F;
    private static final float PLANE_RADIUS = 16.0F;
    /**
     * Keeps the HSV handle's ring fully on the plane instead of hanging over its rounded corner:
     * at the diagonal the corner arc is 10px from the inset point, which just clears the 9.5px
     * shadow ring.
     */
    private static final float PLANE_INSET = 12.0F;

    private static final float HUE_Y = 278.0F;
    private static final float HUE_H = 20.0F;

    private static final float PRESET_Y = 328.0F;
    private static final float PRESET_RADIUS = 14.0F;

    private static final float FOOTER_Y = 357.0F;
    private static final float FOOTER_H = 40.0F;
    private static final float BUTTON_RADIUS = FOOTER_H / 2.0F;
    private static final float SAVE_W = 104.0F;
    private static final float SAVE_X = W - MARGIN - SAVE_W;
    private static final float CANCEL_W = 88.0F;
    private static final float CANCEL_X = SAVE_X - 8.0F - CANCEL_W;
    private static final float PREVIEW_W = CANCEL_X - 8.0F - MARGIN;

    private static final float CLOSE_SIZE = 32.0F;
    private static final float CLOSE_X = W - MARGIN - CLOSE_SIZE;
    private static final float CLOSE_Y = HEADER_Y + 4.0F;

    /** Number of hue stops drawn across the ramp (each slice interpolates fifteen degrees). */
    private static final int HUE_SLICES = 24;

    private final GuiScreen parent;
    private final ColorSetting setting;
    private final UI ui = new UI();

    /** Entrance progress; the sheet scales and fades in, then settles at 1. */
    private final Spring open = new Spring(0.0F);
    /** 0 = follow theme, 1 = custom. Drives the segmented thumb. */
    private final Spring segment = new Spring(0.0F);
    /** 0..1 while a picker control is held; grows the handles so they read as grabbed. */
    private final Spring grab = new Spring(0.0F);
    private final Spring close = new Spring(0.0F);
    private final Spring cancel = new Spring(0.0F);
    private final Spring save = new Spring(0.0F);
    private final Spring[] swatches = new Spring[Theme.ACCENTS.length];

    private float hue;
    private float saturation;
    private float value;
    private float scale = 1.0F;
    private float left;
    private float top;
    private float delta = 16.0F;
    private long lastFrame;
    private boolean firstFrame = true;
    private boolean entered;

    private boolean theme;
    /** 0 = nothing, 1 = the HSV plane, 2 = the hue ramp. */
    private int dragging;
    /** 0 = nothing, 1 = close, 2 = cancel, 3 = save. */
    private int pressing;

    public ColorEditor(GuiScreen parent, ColorSetting setting) {
        this.parent = parent;
        this.setting = setting;
        this.theme = setting.followsTheme();
        for (int i = 0; i < swatches.length; i++) {
            swatches[i] = new Spring(0.0F);
        }
        segment.set(theme ? 0.0F : 1.0F);
        select(setting.customColor());
    }

    private void select(int color) {
        float[] hsv = Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, null);
        hue = hsv[0];
        saturation = hsv[1];
        value = hsv[2];
    }

    @Override
    public void initGui() {
        scale = Math.min(1.0F, Math.min((width - 16.0F) / W, (height - 16.0F) / H));
        scale = Math.max(0.1F, scale);
        left = (width - W * scale) / 2.0F;
        top = (height - H * scale) / 2.0F;
        dragging = 0;
        pressing = 0;
        Blur.request();
        lastFrame = System.nanoTime();
        firstFrame = true;
        if (!entered) {
            // Only the first open animates; `initGui` runs again on every window resize and the
            // sheet must not replay its entrance under the cursor while it is being dragged.
            entered = true;
            open.set(0.0F);
            open.target(1.0F);
        }
    }

    // ------------------------------------------------------------------ //
    // Frame timing and animation
    // ------------------------------------------------------------------ //

    private void tick() {
        long now = System.nanoTime();
        delta = firstFrame ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        firstFrame = false;
        lastFrame = now;

        segment.target(theme ? 0.0F : 1.0F);
        grab.target(dragging != 0 ? 1.0F : 0.0F);
        open.update(delta);
        segment.update(delta);
        grab.update(delta);
    }

    /** Entrance scale, 1 once the spring has settled. */
    private float entrance() {
        return 0.94F + 0.06F * open.value();
    }

    /**
     * Sheet corner radius while entering. Expressive motion morphs the shape as it settles: the
     * sheet starts nearly round and tightens to {@link #RADIUS}. The extra 22px never reaches the
     * content, which is inset by {@link #MARGIN}, so nothing gets clipped mid-transition.
     */
    private float sheetRadius() {
        return RADIUS + 22.0F * (1.0F - clamp(open.value()));
    }

    /** Advances a pointer-driven spring and returns its value. */
    private float interaction(Spring spring, boolean hover, boolean pressed) {
        spring.target(pressed ? 1.0F : (hover ? 0.5F : 0.0F));
        spring.update(delta);
        return spring.value();
    }

    private float localX(float mouseX) {
        return W / 2.0F + ((mouseX - left) / scale - W / 2.0F) / entrance();
    }

    private float localY(float mouseY) {
        return H / 2.0F + ((mouseY - top) / scale - H / 2.0F) / entrance();
    }

    // ------------------------------------------------------------------ //
    // Colour state
    // ------------------------------------------------------------------ //

    private int custom() {
        return Color.HSBtoRGB(hue, saturation, value);
    }

    private int preview() {
        return theme ? setting.themeColor() : custom();
    }

    /** The HSV the plane and the ramp show: the theme colour's while following it. */
    private float[] shown() {
        if (!theme) {
            return new float[]{hue, saturation, value};
        }
        int rgb = setting.themeColor();
        return Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, null);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static boolean in(float x, float y, float l, float t, float w, float h) {
        return x >= l && y >= t && x < l + w && y < t + h;
    }

    private static boolean same(int first, int second) {
        return Math.abs((first >> 16 & 255) - (second >> 16 & 255)) <= 2
                && Math.abs((first >> 8 & 255) - (second >> 8 & 255)) <= 2
                && Math.abs((first & 255) - (second & 255)) <= 2;
    }

    /** Black or white, whichever stays readable on top of {@code rgb}. */
    private static int contrast(int rgb) {
        int luminance = ((rgb >> 16 & 255) * 299 + (rgb >> 8 & 255) * 587 + (rgb & 255) * 114) / 1000;
        return luminance > 145 ? 0xFF141218 : 0xFFFFFFFF;
    }

    private static float presetX(int index) {
        float spacing = (COLUMN - PRESET_RADIUS * 2.0F) / (Theme.ACCENTS.length - 1);
        return MARGIN + PRESET_RADIUS + index * spacing;
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tick();
        Scheme s = Theme.scheme();
        boolean scene = mc.theWorld != null && mc.thePlayer != null;
        boolean frosted = scene && Theme.frost();
        if (frosted) {
            // The backdrop is captured before anything is drawn on top of it, exactly like the
            // ClickGUI does: the scene behind the sheet must not contain the sheet's own scrim.
            Blur.prepare();
        }
        float mx = localX(mouseX);
        float my = localY(mouseY);

        ui.begin();
        try {
            drawBackdrop(s, scene);

            ui.push();
            ui.translate(left, top);
            ui.scale(scale, 0.0F, 0.0F);
            ui.scale(entrance(), W / 2.0F, H / 2.0F);

            float radius = sheetRadius();
            ui.shadow(0.0F, 0.0F, W, H, radius, 3);
            ui.rounded(0.0F, 0.0F, W, H, radius, s.surfaceContainer);
            if (frosted && Blur.ready()) {
                // Same call the ClickGUI panel makes, so the sheet is the identical material.
                ui.frost(0.0F, 0.0F, W, H, radius, s.surfaceContainer);
            }
            ui.outline(0.0F, 0.0F, W, H, radius, 1.0F, Material.withAlpha(s.outlineVariant, 0.5F));

            // The content is not alpha-faded with the entrance: a frosted surface that is
            // see-through would show the sharp world for the first frames, and any two touching
            // gradient quads (the hue ramp) would double-composite into visible seams below full
            // opacity. The sheet ends up entering with scale, shape morph and scrim only, which is
            // also exactly how the ClickGUI panel enters.
            drawHeader(s, mx, my);
            drawSegment(s, mx, my);
            drawPlane(s);
            drawHue();
            drawPresets(s, mx, my);
            drawFooter(s, mx, my);

            ui.pop();
        } finally {
            ui.end();
        }
    }

    private void drawBackdrop(Scheme s, boolean scene) {
        if (!scene) {
            // No world to blur (the ClickGUI can be opened from the main menu).
            ui.rect(0.0F, 0.0F, width, height, s.surface);
            ui.gradient(0.0F, 0.0F, width, height, s.surfaceContainerLow, s.surface);
            return;
        }
        ui.rect(0.0F, 0.0F, width, height, Theme.scrim(clamp(open.value())));
    }

    private void drawHeader(Scheme s, float mx, float my) {
        ui.rounded(MARGIN, HEADER_Y, BADGE, BADGE, BADGE / 2.0F, s.primaryContainer);
        ui.icon(Icons.PALETTE, MARGIN + BADGE / 2.0F, HEADER_Y + BADGE / 2.0F, 21.0F, s.onPrimaryContainer);

        String title = Text.strip(setting.getName());
        float titleWidth = CLOSE_X - 12.0F - (MARGIN + 52.0F);
        ui.text(Typeface.HEADLINE, ui.truncate(Typeface.HEADLINE, title, titleWidth),
                MARGIN + 52.0F, HEADER_Y + BADGE / 2.0F, s.onSurface, UI.ALIGN_LEFT);

        boolean hover = in(mx, my, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE);
        float state = interaction(close, hover, pressing == 1);
        ui.push();
        ui.scale(1.0F - 0.06F * state, CLOSE_X + CLOSE_SIZE / 2.0F, CLOSE_Y + CLOSE_SIZE / 2.0F);
        if (state > 0.01F) {
            ui.rounded(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, CLOSE_SIZE / 2.0F,
                    Material.withAlpha(s.onSurface, 0.16F * state));
        }
        ui.icon(Icons.CLOSE, CLOSE_X + CLOSE_SIZE / 2.0F, CLOSE_Y + CLOSE_SIZE / 2.0F, 18.0F,
                s.onSurfaceVariant);
        ui.pop();
    }

    private void drawSegment(Scheme s, float mx, float my) {
        float inner = 4.0F;
        float thumbW = (COLUMN - inner * 2.0F) / 2.0F;
        float thumbH = SEGMENT_H - inner * 2.0F;
        ui.rounded(MARGIN, SEGMENT_Y, COLUMN, SEGMENT_H, SEGMENT_H / 2.0F, s.surfaceContainerHighest);

        // The spring overshoots on purpose; clamping the travel keeps the thumb inside its track
        // instead of poking past the rounded end.
        float travel = clamp(segment.value());
        ui.rounded(MARGIN + inner + thumbW * travel, SEGMENT_Y + inner, thumbW, thumbH,
                thumbH / 2.0F, s.primaryContainer);

        float themeCenter = MARGIN + inner + thumbW / 2.0F;
        float customCenter = themeCenter + thumbW;
        float centerY = SEGMENT_Y + SEGMENT_H / 2.0F;
        boolean themeHover = in(mx, my, MARGIN + inner, SEGMENT_Y + inner, thumbW, thumbH);
        boolean customHover = in(mx, my, MARGIN + inner + thumbW, SEGMENT_Y + inner, thumbW, thumbH);
        if (themeHover) {
            ui.rounded(MARGIN + inner, SEGMENT_Y + inner, thumbW, thumbH, thumbH / 2.0F,
                    Material.withAlpha(s.onSurface, 0.07F));
        }
        if (customHover) {
            ui.rounded(MARGIN + inner + thumbW, SEGMENT_Y + inner, thumbW, thumbH, thumbH / 2.0F,
                    Material.withAlpha(s.onSurface, 0.07F));
        }

        segmentLabel(Icons.TONALITY, "Follow theme", themeCenter, centerY, theme, s);
        segmentLabel(Icons.BRUSH, "Custom", customCenter, centerY, !theme, s);
    }

    private void segmentLabel(String icon, String label, float centerX, float y, boolean active, Scheme s) {
        int color = active ? s.onPrimaryContainer : s.onSurfaceVariant;
        float total = 18.0F + 7.0F + ui.width(Typeface.LABEL, label);
        float start = centerX - total / 2.0F;
        ui.icon(icon, start + 9.0F, y, 18.0F, color);
        ui.text(Typeface.LABEL, label, start + 25.0F, y, color, UI.ALIGN_LEFT);
    }

    private void drawPlane(Scheme s) {
        float[] hsv = shown();
        int pure = Color.HSBtoRGB(hsv[0], 1.0F, 1.0F);
        // White-to-hue horizontally, then a transparent-to-black sheet on top: together they are
        // the HSV square, and the overlay's alpha ramp is what darkens the value axis.
        ui.gradient4(MARGIN, PLANE_Y, COLUMN, PLANE_H, PLANE_RADIUS,
                0xFFFFFFFF, 0xFFFFFFFF, pure, pure);
        ui.gradient4(MARGIN, PLANE_Y, COLUMN, PLANE_H, PLANE_RADIUS,
                0x00000000, 0xFF000000, 0xFF000000, 0x00000000);
        ui.outline(MARGIN, PLANE_Y, COLUMN, PLANE_H, PLANE_RADIUS, 1.0F,
                Material.withAlpha(s.outlineVariant, 0.45F));

        // The handle travels inside an inset box so it never hangs over the plane's corners; the
        // drag mapping below uses the same box, so the pointer always lands on the handle.
        float spanX = COLUMN - PLANE_INSET * 2.0F;
        float spanY = PLANE_H - PLANE_INSET * 2.0F;
        float handleX = MARGIN + PLANE_INSET + hsv[1] * spanX;
        float handleY = PLANE_Y + PLANE_INSET + (1.0F - hsv[2]) * spanY;
        float grow = grab.value();
        ui.circle(handleX, handleY, 13.0F + 2.0F * grow,
                Material.withAlpha(preview(), 0.14F + 0.12F * grow));
        ui.circle(handleX, handleY, 9.5F, Material.withAlpha(0xFF000000, 0.30F));
        ui.circle(handleX, handleY, 8.0F, 0xFFFFFFFF);
        ui.circle(handleX, handleY, 5.0F, preview());
    }

    private void drawHue() {
        float[] hsv = shown();
        float radius = HUE_H / 2.0F;
        float span = COLUMN - radius * 2.0F;
        float step = span / HUE_SLICES;
        int first = Color.HSBtoRGB(0.0F, 1.0F, 1.0F);
        for (int i = 0; i < HUE_SLICES; i++) {
            int from = Color.HSBtoRGB(i / (float) HUE_SLICES, 1.0F, 1.0F);
            int to = Color.HSBtoRGB((i + 1.0F) / HUE_SLICES, 1.0F, 1.0F);
            // Neighbouring slices overlap by half a pixel: two SDF feathers meeting edge to edge
            // would otherwise leave a 0.75-alpha seam every fifteen degrees.
            ui.gradient4(MARGIN + radius + i * step - 0.5F, HUE_Y, step + 1.0F, HUE_H, 0.0F,
                    from, from, to, to);
        }
        // The two ends of the ramp are the same red, so the pill's caps can be a single colour.
        ui.roundedCorners(MARGIN, HUE_Y, radius, HUE_H, radius, 0.0F, 0.0F, radius, first);
        ui.roundedCorners(MARGIN + COLUMN - radius, HUE_Y, radius, HUE_H, 0.0F, radius, radius, 0.0F, first);

        float thumbX = MARGIN + radius + hsv[0] * span;
        float thumbY = HUE_Y + HUE_H / 2.0F;
        float grow = grab.value();
        ui.circle(thumbX, thumbY, 11.5F + grow, Material.withAlpha(0xFF000000, 0.28F));
        ui.circle(thumbX, thumbY, 9.5F + grow, 0xFFFFFFFF);
        ui.circle(thumbX, thumbY, 5.5F, Color.HSBtoRGB(hsv[0], 1.0F, 1.0F));
    }

    private void drawPresets(Scheme s, float mx, float my) {
        int selected = preview();
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            float cx = presetX(i);
            boolean hover = in(mx, my, cx - 16.0F, PRESET_Y - 16.0F, 32.0F, 32.0F);
            float grow = interaction(swatches[i], hover, false);
            float radius = PRESET_RADIUS * (1.0F + 0.10F * grow);
            if (grow > 0.01F) {
                ui.circle(cx, PRESET_Y, radius + 5.0F, Material.withAlpha(s.onSurface, 0.08F * grow));
            }
            ui.circle(cx, PRESET_Y, radius, Theme.ACCENTS[i]);
            if (same(Theme.ACCENTS[i], selected)) {
                float ring = PRESET_RADIUS + 4.5F;
                ui.outline(cx - ring, PRESET_Y - ring, ring * 2.0F, ring * 2.0F, ring, 2.0F, s.onSurface);
            }
        }
    }

    private void drawFooter(Scheme s, float mx, float my) {
        int rgb = preview();
        ui.rounded(MARGIN, FOOTER_Y, PREVIEW_W, FOOTER_H, BUTTON_RADIUS, rgb);
        ui.outline(MARGIN, FOOTER_Y, PREVIEW_W, FOOTER_H, BUTTON_RADIUS, 1.0F,
                Material.withAlpha(s.outlineVariant, 0.6F));
        ui.text(Typeface.LABEL, Material.hex(rgb), MARGIN + PREVIEW_W / 2.0F,
                FOOTER_Y + FOOTER_H / 2.0F, contrast(rgb), UI.ALIGN_CENTER);

        float centerY = FOOTER_Y + FOOTER_H / 2.0F;
        boolean cancelHover = in(mx, my, CANCEL_X, FOOTER_Y, CANCEL_W, FOOTER_H);
        float cancelState = interaction(cancel, cancelHover, pressing == 2);
        ui.push();
        ui.scale(1.0F - 0.03F * cancelState, CANCEL_X + CANCEL_W / 2.0F, centerY);
        if (cancelState > 0.01F) {
            ui.rounded(CANCEL_X, FOOTER_Y, CANCEL_W, FOOTER_H, BUTTON_RADIUS,
                    Material.withAlpha(s.onSurface, 0.16F * cancelState));
        }
        ui.text(Typeface.LABEL, "Cancel", CANCEL_X + CANCEL_W / 2.0F, centerY,
                s.onSurfaceVariant, UI.ALIGN_CENTER);
        ui.pop();

        boolean saveHover = in(mx, my, SAVE_X, FOOTER_Y, SAVE_W, FOOTER_H);
        float saveState = interaction(save, saveHover, pressing == 3);
        ui.push();
        ui.scale(1.0F - 0.03F * saveState, SAVE_X + SAVE_W / 2.0F, centerY);
        ui.rounded(SAVE_X, FOOTER_Y, SAVE_W, FOOTER_H, BUTTON_RADIUS, s.primary);
        if (saveState > 0.01F) {
            ui.rounded(SAVE_X, FOOTER_Y, SAVE_W, FOOTER_H, BUTTON_RADIUS,
                    Material.withAlpha(s.onPrimary, 0.16F * saveState));
        }
        String label = "Save";
        float total = 18.0F + 7.0F + Typeface.LABEL.width(label);
        float start = SAVE_X + (SAVE_W - total) / 2.0F;
        ui.icon(Icons.CHECK, start + 9.0F, centerY, 18.0F, s.onPrimary);
        ui.text(Typeface.LABEL, label, start + 25.0F, centerY, s.onPrimary, UI.ALIGN_LEFT);
        ui.pop();
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (button != 0) {
            return;
        }
        float x = localX(mouseX);
        float y = localY(mouseY);

        if (in(x, y, CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE)) {
            pressing = 1;
            cancel();
            return;
        }
        if (in(x, y, MARGIN, SEGMENT_Y, COLUMN, SEGMENT_H)) {
            theme = x < MARGIN + COLUMN / 2.0F;
            return;
        }
        if (in(x, y, SAVE_X, FOOTER_Y, SAVE_W, FOOTER_H)) {
            pressing = 3;
            save();
            return;
        }
        if (in(x, y, CANCEL_X, FOOTER_Y, CANCEL_W, FOOTER_H)) {
            pressing = 2;
            cancel();
            return;
        }
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            if (in(x, y, presetX(i) - 16.0F, PRESET_Y - 16.0F, 32.0F, 32.0F)) {
                select(Theme.ACCENTS[i]);
                theme = false;
                swatches[i].impulse(4.0F);
                return;
            }
        }
        if (in(x, y, MARGIN, PLANE_Y, COLUMN, PLANE_H)) {
            dragging = 1;
        } else if (in(x, y, MARGIN, HUE_Y, COLUMN, HUE_H)) {
            dragging = 2;
        }
        if (dragging != 0) {
            if (theme) {
                // Leaving the theme-following mode keeps the colour that was on screen as the
                // starting point, so the first drag continues from what the user saw.
                select(setting.themeColor());
                theme = false;
            }
            drag(x, y);
        }
    }

    private void drag(float x, float y) {
        if (dragging == 1) {
            saturation = clamp((x - MARGIN - PLANE_INSET) / (COLUMN - PLANE_INSET * 2.0F));
            value = 1.0F - clamp((y - PLANE_Y - PLANE_INSET) / (PLANE_H - PLANE_INSET * 2.0F));
        } else if (dragging == 2) {
            float radius = HUE_H / 2.0F;
            hue = clamp((x - MARGIN - radius) / (COLUMN - radius * 2.0F));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (button == 0) {
            drag(localX(mouseX), localY(mouseY));
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            dragging = 0;
            pressing = 0;
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            cancel();
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            save();
        }
    }

    private void save() {
        // `custom()` rather than `preview()`: the stored colour must stay the user's own even
        // while "Follow theme" is on, or the next switch would silently lose it.
        setting.setAppearance(custom(), theme);
        mc.displayGuiScreen(parent);
    }

    private void cancel() {
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        Blur.request();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
