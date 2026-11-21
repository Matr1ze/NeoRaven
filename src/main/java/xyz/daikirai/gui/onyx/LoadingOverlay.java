package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.menu.MenuBackdrop;
import xyz.daikirai.gui.onyx.render.M3ShapeMorphProgress;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/** Server connection and terrain screens, using the user-supplied seven-outline morph animation. */
public final class LoadingOverlay {

    private static final UI UI = new UI();
    private static final M3ShapeMorphProgress INDICATOR = new M3ShapeMorphProgress();
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
            MenuBackdrop.draw(ui, width, height);
            ui.rect(0.0F, 0.0F, width, height, Theme.scrim(0.35F));

            float cx = width / 2.0F;
            // Block bottom sits CANCEL_GAP above the pill: subtitle, title, shape, in that order.
            float subtitleY = cancelY(height) - CANCEL_GAP - 8.0F;
            float titleY = subtitleY - 22.0F;
            float cy = titleY - 26.0F - SHAPE / 2.0F;

            INDICATOR.draw(ui, cx, cy, SHAPE, scheme.primary);
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
            ui.text(Typeface.LABEL, "Cancel", bx + CANCEL_WIDTH / 2.0F, by + CANCEL_HEIGHT / 2.0F,
                    scheme.onPrimaryContainer, UI.ALIGN_CENTER);
        } finally {
            ui.end();
        }
    }
}
