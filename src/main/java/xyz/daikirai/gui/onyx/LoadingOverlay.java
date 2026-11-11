package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.M3LoadingIndicator;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * The client's "joining a server" screen.
 *
 * <p>Vanilla draws a dirt background with one line of text; this replaces that drawing with the
 * client's surface gradient and the Material 3 Expressive morphing loading indicator
 * ({@link M3LoadingIndicator}, twelve-sided cookie -> nine-sided cookie -> pentagon -> circle, the
 * {@code Shape Morph 3.mp4} cycle) with the connection status underneath. No plate behind the shape:
 * the reference clip's light disc only exists because that clip has a flat field, and on the
 * gradient it read as a sticker.</p>
 *
 * <p>The whole block is anchored to the Cancel button instead of the screen centre, because
 * {@code GuiConnecting} places that button at {@code height / 4 + 132} — centring the status text put
 * it right on top of the pill on short windows.</p>
 *
 * <p>The screen objects themselves stay vanilla ({@code GuiConnecting} / {@code GuiDownloadTerrain}),
 * so the connection state machine, the Cancel button and its click handling are untouched.</p>
 */
public final class LoadingOverlay {

    private static final UI UI = new UI();
    /** Diameter of the morphing shape, in GUI pixels. */
    private static final float SHAPE = 64.0F;
    /** The connecting screen's Cancel button lives here (GuiConnecting.initGui places it there). */
    private static final int CANCEL_WIDTH = 200;
    private static final int CANCEL_HEIGHT = 20;
    /** Gap between the bottom of the status block and the top of the Cancel pill. */
    private static final float CANCEL_GAP = 26.0F;

    private LoadingOverlay() {
    }

    /**
     * The y of the Cancel pill. {@code GuiConnecting} puts it at {@code height / 4 + 132}; the terrain
     * screen has no button, so it uses the same anchor and the block simply sits a little higher.
     */
    public static float cancelY(float height) {
        return height / 4.0F + 132.0F;
    }

    /** Backdrop, morphing shape and status text for the whole screen. */
    public static void render(int width, int height, String title, String subtitle) {
        Scheme scheme = Theme.scheme();
        UI ui = UI;
        ui.begin();
        try {
            ui.rect(0.0F, 0.0F, width, height, scheme.surface);
            ui.gradient(0.0F, 0.0F, width, height, scheme.surfaceContainerLow, scheme.surface);
            ui.rect(0.0F, 0.0F, width, height, Theme.scrim(0.35F));

            float cx = width / 2.0F;
            // Block bottom sits CANCEL_GAP above the pill: subtitle, title, shape, in that order.
            float subtitleY = cancelY(height) - CANCEL_GAP - 8.0F;
            float titleY = subtitleY - 22.0F;
            float cy = titleY - 26.0F - SHAPE / 2.0F;

            M3LoadingIndicator.draw(ui, cx, cy, SHAPE, scheme.primary);
            ui.text(Typeface.HEADLINE, title, cx, titleY, scheme.onSurface, UI.ALIGN_CENTER);
            if (subtitle != null && !subtitle.isEmpty()) {
                ui.text(Typeface.BODY, subtitle, cx, subtitleY, scheme.onSurfaceVariant,
                        UI.ALIGN_CENTER);
            }
        } finally {
            ui.end();
        }
    }

    /**
     * Repaints the connecting screen's Cancel button in the Material style, exactly over the vanilla
     * button's hit box (that invisible button keeps handling the click).
     *
     * <p>Called after the vanilla screen drew, so the dirt-textured button ends up underneath.</p>
     */
    public static void renderCancelButton(int width, int height, int mouseX, int mouseY) {
        Scheme scheme = Theme.scheme();
        UI ui = UI;
        ui.begin();
        try {
            float bx = width / 2.0F - CANCEL_WIDTH / 2.0F;
            float by = cancelY(height);
            boolean hover = mouseX >= bx && mouseX < bx + CANCEL_WIDTH
                    && mouseY >= by && mouseY < by + CANCEL_HEIGHT;
            int fill = hover ? Material.mix(scheme.primaryContainer, scheme.primary, 0.18F)
                    : scheme.primaryContainer;
            ui.rounded(bx, by, CANCEL_WIDTH, CANCEL_HEIGHT, CANCEL_HEIGHT / 2.0F, fill);
            ui.text(Typeface.LABEL, xyz.daikirai.util.Lang.text("Cancel"), bx + CANCEL_WIDTH / 2.0F, by + CANCEL_HEIGHT / 2.0F,
                    scheme.onPrimaryContainer, UI.ALIGN_CENTER);
        } finally {
            ui.end();
        }
    }
}
