package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.menu.MenuBackdrop;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.M3LoadingIndicator;

/**
 * The client's "joining a server" screen.
 *
 * <p>Vanilla draws a dirt background with one line of text; this replaces that drawing with the same
 * Onyx cloud backdrop the menus use, the Material 3 Expressive morphing loading indicator
 * ({@link M3LoadingIndicator}) and the connection status.
 * The screen objects themselves stay vanilla ({@code GuiConnecting} / {@code GuiDownloadTerrain}),
 * so the connection state machine, the Cancel button and its click handling are untouched.</p>
 */
public final class LoadingOverlay {

    private static final UI UI = new UI();
    /** Diameter of the wavy ring, in GUI pixels. */
    private static final float RING = 48.0F;
    /** The connecting screen's Cancel button lives here (GuiConnecting.initGui places it there). */
    private static final int CANCEL_WIDTH = 200;
    private static final int CANCEL_HEIGHT = 20;

    private LoadingOverlay() {
    }

    /** Backdrop, wavy ring and status text for the whole screen. */
    public static void render(int width, int height, String title, String subtitle) {
        Scheme scheme = Theme.scheme();
        UI ui = UI;
        ui.begin();
        try {
            MenuBackdrop.draw(ui, width, height);
            ui.rect(0.0F, 0.0F, width, height, Theme.scrim(0.35F));

            float cx = width / 2.0F;
            // Above the Cancel button, so the two never overlap.
            float cy = height / 2.0F - 58.0F;
            M3LoadingIndicator.draw(ui, cx, cy, RING);
            ui.text(Typeface.HEADLINE, title, cx, cy + RING / 2.0F + 22.0F,
                    scheme.onSurface, UI.ALIGN_CENTER);
            if (subtitle != null && !subtitle.isEmpty()) {
                ui.text(Typeface.BODY, subtitle, cx, cy + RING / 2.0F + 40.0F,
                        scheme.onSurfaceVariant, UI.ALIGN_CENTER);
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
            float by = height / 4.0F + 132.0F;
            boolean hover = mouseX >= bx && mouseX < bx + CANCEL_WIDTH
                    && mouseY >= by && mouseY < by + CANCEL_HEIGHT;
            int fill = hover ? Material.mix(scheme.primaryContainer, scheme.primary, 0.18F)
                    : scheme.primaryContainer;
            ui.rounded(bx, by, CANCEL_WIDTH, CANCEL_HEIGHT, CANCEL_HEIGHT / 2.0F, fill);
            ui.text(Typeface.LABEL, "Cancel", bx + CANCEL_WIDTH / 2.0F, by + CANCEL_HEIGHT / 2.0F,
                    scheme.onPrimaryContainer, UI.ALIGN_CENTER);
        } finally {
            ui.end();
        }
    }
}
