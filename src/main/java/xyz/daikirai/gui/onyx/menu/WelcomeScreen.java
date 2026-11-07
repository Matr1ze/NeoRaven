package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.client.FirstRun;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * The Pixel Tablet style first-launch screen: a huge solid scalloped "cookie" slowly turning in the
 * top-left corner, a two-line welcome headline on the right, the language / accessibility rows under
 * it, and the "Get started" pill in the bottom-right.
 *
 * <p>It is only shown when {@link FirstRun} saw a fresh install (no {@code keystrokes} config folder
 * yet). The layout is authored in a 640x396 design space (the reference screenshot's 1.62 aspect)
 * and scaled to fit, so the composition survives any window size. The palette is a forced dark
 * Material scheme derived from the ClickGUI accent, independent of the title screen's light/dark
 * setting — the screen is meant to be the shut-off tablet look.</p>
 *
 * <p>Both rows jump straight into the vanilla screens ({@code GuiLanguage} / {@code GuiOptions});
 * "Get started" hands over to the configured title screen.</p>
 */
public final class WelcomeScreen extends GuiScreen {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 396.0F;

    /** One full turn of the cookie. */
    private static final long SPIN_PERIOD_MS = 8000L;

    /** Scalloped cookie: big enough that the top and left are cropped by the window edges. */
    private static final float COOKIE_X = 150.0F;
    private static final float COOKIE_Y = 20.0F;
    private static final float COOKIE_RADIUS = 150.0F;
    /** Material 3 {@code Cookie12Sided}: twelve identical round lobes, no asymmetry. */
    private static final int COOKIE_LOBES = 12;
    /** Lobe radius as a fraction of the silhouette radius; 0.235 gives the reference's notches. */
    private static final float COOKIE_LOBE = 0.235F;
    /** Core disc radius as a fraction of the silhouette radius: exactly the notch radius of 12 lobes. */
    private static final float COOKIE_CORE = 0.865F;
    /** Big circle whose upper-left arc sweeps across the middle of the reference screenshot. */
    private static final float ARC_X = 437.0F;
    private static final float ARC_Y = 459.0F;
    private static final float ARC_RADIUS = 345.0F;
    private static final float ARC_FROM = 186.0F;
    private static final float ARC_TO = 308.0F;

    private static final float TEXT_LEFT = 330.0F;
    private static final float HEADLINE_Y = 188.0F;
    private static final float HEADLINE_LINE = 46.0F;
    private static final float ROW_LANGUAGE = 284.0F;
    private static final float ROW_ACCESSIBILITY = 324.0F;
    private static final float ROW_HEIGHT = 34.0F;
    private static final float BUTTON_RIGHT = 616.0F;
    private static final float BUTTON_Y = 366.0F;

    private static final Typeface.Style HEADLINE = Typeface.custom(24.0F, 36.0F, 44.0F, false);
    private static final Typeface.Style LABEL = Typeface.custom(9.0F, 14.0F, 18.0F, false);
    private static final Typeface.Style BUTTON_LABEL = Typeface.custom(9.0F, 12.0F, 16.0F, true);

    private final UI ui = new UI();
    private float scale = 1.0F;
    private float ox;
    private float oy;
    private float mx;
    private float my;
    private float dt;
    private long lastFrame;
    private long shownAt;
    private float angle;

    // Palette, resolved once per frame from the accent.
    private int background;
    private int ink;
    private int softInk;
    private int ring;
    private int face;
    private int primary;
    private int onPrimary;

    public WelcomeScreen() {
        buildPalette();
    }

    private void buildPalette() {
        Material.Hct hct = Material.Hct.fromInt(Theme.accent());
        double hue = hct.hue;
        double chroma = hct.chroma;
        background = Material.TonalPalette.of(hue, chroma * 0.12).tone(7.0) | 0xFF000000;
        ink = Material.TonalPalette.of(hue, Math.min(chroma, 8.0)).tone(93.0) | 0xFF000000;
        softInk = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(72.0) | 0xFF000000;
        ring = Material.TonalPalette.of(hue, Math.min(chroma, 10.0)).tone(44.0) | 0xFF000000;
        face = Material.TonalPalette.of(hue, chroma * 0.12).tone(16.0) | 0xFF000000;
        primary = Material.TonalPalette.of(hue, chroma).tone(78.0) | 0xFF000000;
        onPrimary = Material.TonalPalette.of(hue, chroma * 0.25).tone(14.0) | 0xFF000000;
    }

    @Override
    public void initGui() {
        this.scale = fitScale();
        // Offsets are kept in design units, exactly like MenuScreen, so input inverts the same matrix.
        this.ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        this.oy = (height / scale - DESIGN_HEIGHT) / 2.0F;
        this.lastFrame = 0L;
        this.shownAt = System.currentTimeMillis();
        this.angle = 0.0F;
    }

    private float fitScale() {
        float fit = Math.min((width - 16.0F) / DESIGN_WIDTH, (height - 16.0F) / DESIGN_HEIGHT);
        return Math.max(0.1F, Theme.screenScale(fit));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void tickFrame() {
        long now = System.nanoTime();
        this.dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1000000.0F);
        this.lastFrame = now;
        // 45 deg/s: a full turn every 8s - clearly moving without looking frantic.
        // 360 degrees every 8 seconds, straight from the wall clock: an accumulated field can be
        // reset by a screen re-init and then the cookie sits frozen at the same angle.
        this.angle = (System.currentTimeMillis() % SPIN_PERIOD_MS) * (360.0F / SPIN_PERIOD_MS);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tickFrame();
        this.mx = mouseX / scale - ox;
        this.my = mouseY / scale - oy;
        float appear = Ease.EMPHASIZED.get(Material.clamp(
                (System.currentTimeMillis() - shownAt) / 620.0F, 0.0F, 1.0F));

        ui.begin();
        try {
            ui.rect(0.0F, 0.0F, width, height, background);
            ui.push();
            ui.scale(scale, 0.0F, 0.0F);
            ui.translate(ox, oy);
            drawArc();
            drawCookie();
            ui.alphaPush(appear);
            drawText(appear);
            ui.alphaPop();
            ui.pop();
        } finally {
            ui.end();
        }
    }

    /**
     * The long thin ellipse/circle arc that sweeps from the top edge down to the bottom-left corner.
     *
     * <p>Only the slice of the circle that crosses the design space is drawn, so the stroke stays one
     * clean curve instead of hundreds of invisible segments off screen.</p>
     */
    private void drawArc() {
        int segments = 260;
        float thickness = 1.4F;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.toRadians(ARC_FROM + (ARC_TO - ARC_FROM) * i / segments);
            double a1 = Math.toRadians(ARC_FROM + (ARC_TO - ARC_FROM) * (i + 1) / segments);
            float x0 = ARC_X + (float) Math.cos(a0) * ARC_RADIUS;
            float y0 = ARC_Y + (float) Math.sin(a0) * ARC_RADIUS;
            float x1 = ARC_X + (float) Math.cos(a1) * ARC_RADIUS;
            float y1 = ARC_Y + (float) Math.sin(a1) * ARC_RADIUS;
            stroke(x0, y0, x1, y1, thickness, ring, 0.85F);
        }
    }

    /** One rounded segment of a thin outline between two design-space points. */
    private void stroke(float x0, float y0, float x1, float y1, float thickness, int color, float alpha) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.01F) {
            return;
        }
        float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
        ui.alphaPush(alpha);
        ui.push();
        ui.translate((x0 + x1) / 2.0F, (y0 + y1) / 2.0F);
        net.minecraft.client.renderer.GlStateManager.rotate(deg, 0.0F, 0.0F, 1.0F);
        // Overlap the neighbours by one cap so the line reads as one smooth stroke.
        ui.rounded(-length / 2.0F - thickness / 2.0F, -thickness / 2.0F, length + thickness,
                thickness, thickness / 2.0F, color);
        ui.pop();
        ui.alphaPop();
    }

    /**
     * The filled cookie, built as Material 3's {@code Cookie12Sided} silhouette: a core disc up to the
     * notch radius plus twelve identical round lobes riding the rim.
     *
     * <p>The lobes are real, separate discs, so their union *is* the scalloped outline — that is why
     * the notches read as genuine concave dips instead of a wavy circle. The core stops exactly at the
     * notch radius (computed from the lobe geometry) so it fills the body without rounding the notches
     * away. No outline: the design wants the solid cookie only.</p>
     */
    private void drawCookie() {
        float lobe = COOKIE_RADIUS * COOKIE_LOBE;
        float distance = COOKIE_RADIUS - lobe;
        ui.circle(COOKIE_X, COOKIE_Y, COOKIE_RADIUS * COOKIE_CORE, face);
        for (int i = 0; i < COOKIE_LOBES; i++) {
            double a = Math.toRadians(angle + i * 360.0D / COOKIE_LOBES);
            ui.circle(COOKIE_X + (float) Math.cos(a) * distance,
                    COOKIE_Y + (float) Math.sin(a) * distance, lobe, face);
        }
    }

    private void drawText(float appear) {
        float slide = (1.0F - appear) * 14.0F;
        ui.text(HEADLINE, xyz.daikirai.util.Lang.text("Welcome to your"), TEXT_LEFT, HEADLINE_Y + slide, ink, UI.ALIGN_LEFT);
        ui.text(HEADLINE, "NeoRaven", TEXT_LEFT, HEADLINE_Y + HEADLINE_LINE + slide, ink,
                UI.ALIGN_LEFT);

        drawRow(0, ROW_LANGUAGE + slide, "\ue894", languageLabel(), false);
        drawRow(1, ROW_ACCESSIBILITY + slide, "\ue853", xyz.daikirai.util.Lang.text("Accessibility"), true);
        drawButton(slide);
    }

    private void drawRow(int index, float y, String glyph, String label, boolean chevron) {
        boolean hover = rowHit(index);
        if (hover) {
            ui.rounded(TEXT_LEFT - 10.0F, y - ROW_HEIGHT / 2.0F + 2.0F, BUTTON_RIGHT - TEXT_LEFT + 10.0F,
                    ROW_HEIGHT - 4.0F, 12.0F, Material.withAlpha(ink, 0.06F));
        }
        ui.icon(glyph, TEXT_LEFT + 4.0F, y, 16.0F, softInk);
        ui.text(LABEL, label, TEXT_LEFT + 24.0F, y, hover ? ink : softInk, UI.ALIGN_LEFT);
        if (chevron) {
            ui.icon(Icons.EXPAND_MORE, BUTTON_RIGHT - 8.0F, y, 14.0F,
                    Material.withAlpha(softInk, hover ? 0.9F : 0.7F));
        }
    }

    private String languageLabel() {
        try {
            String name = mc.getLanguageManager().getCurrentLanguage().toString();
            return name == null || name.isEmpty() ? xyz.daikirai.util.Lang.text("English (United States)") : name;
        } catch (Throwable ignored) {
            return xyz.daikirai.util.Lang.text("English (United States)");
        }
    }

    private void drawButton(float slide) {
        String label = xyz.daikirai.util.Lang.text("Get started");
        float textWidth = BUTTON_LABEL.width(label);
        float buttonWidth = Math.max(86.0F, textWidth + 32.0F);
        float buttonHeight = 28.0F;
        float x = BUTTON_RIGHT - buttonWidth;
        float y = BUTTON_Y + slide;
        boolean hover = mouseOver(x, y, buttonWidth, buttonHeight);
        ui.shadow(x, y, buttonWidth, buttonHeight, buttonHeight / 2.0F, 3);
        ui.rounded(x, y, buttonWidth, buttonHeight, buttonHeight / 2.0F,
                hover ? Material.mix(primary, 0xFFFFFFFF, 0.12F) : primary);
        ui.text(BUTTON_LABEL, label, x + buttonWidth / 2.0F, y + buttonHeight / 2.0F, onPrimary,
                UI.ALIGN_CENTER);
    }

    // ------------------------------------------------------------------ //
    // Layout helpers (design space)
    // ------------------------------------------------------------------ //

    private boolean rowHit(int index) {
        float y = index == 0 ? ROW_LANGUAGE : ROW_ACCESSIBILITY;
        return mx >= TEXT_LEFT - 10.0F && mx < BUTTON_RIGHT && my >= y - ROW_HEIGHT / 2.0F
                && my < y + ROW_HEIGHT / 2.0F;
    }

    private boolean mouseOver(float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean buttonHit() {
        String label = xyz.daikirai.util.Lang.text("Get started");
        float buttonWidth = Math.max(86.0F, BUTTON_LABEL.width(label) + 32.0F);
        return mouseOver(BUTTON_RIGHT - buttonWidth, BUTTON_Y, buttonWidth, 28.0F);
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        this.mx = mouseX / scale - ox;
        this.my = mouseY / scale - oy;
        if (buttonHit()) {
            click();
            start();
            return;
        }
        if (rowHit(0)) {
            click();
            mc.displayGuiScreen(new GuiLanguage(this, mc.gameSettings, mc.getLanguageManager()));
            return;
        }
        if (rowHit(1)) {
            click();
            mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        // Escape is "I know my way around": go straight to the title screen.
        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
            start();
        }
    }

    /** "Get started": hand over to the configured title screen. */
    private void start() {
        FirstRun.dismissed();
        mc.displayGuiScreen(MenuStyle.current().screen());
    }

    private void click() {
        mc.getSoundHandler().playSound(
                PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }
}
