package xyz.daikirai.gui;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.gui.onyx.hud.Island;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.WatermarkModule;

/**
 * Drag editor for the Material island watermark.
 *
 * <p>Onyx let you drag the island while chat was open; that gesture is kept, and this screen is the
 * discoverable version of it: the island is drawn at full opacity and can be dragged anywhere, the
 * position is stored as screen percentages (the same values the "Position X/Y" settings show).</p>
 */
public final class IslandPositionEditor extends GuiScreen {

    private final UI ui = new UI();

    private boolean dragging;
    private float offsetX;
    private float offsetY;

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, this.width, this.height, 0x77000000);
        WatermarkModule module = WatermarkModule.instance;
        if (module == null) {
            return;
        }
        Island island = WatermarkModule.island();
        island.render(module, true);

        Scheme scheme = Theme.scheme();
        ui.begin();
        try {
            ui.text(Typeface.HEADLINE, "Watermark position", this.width / 2.0F, 26.0F,
                    scheme.onSurface, UI.ALIGN_CENTER);
            ui.text(Typeface.BODY, "Drag the island  ·  Esc when done", this.width / 2.0F, 46.0F,
                    scheme.onSurfaceVariant, UI.ALIGN_CENTER);

            float boxWidth = Math.max(160.0F, Typeface.BODY.width("Esc when done") + 40.0F);
            ui.rounded(this.width / 2.0F - boxWidth / 2.0F, this.height - 46.0F, boxWidth, 26.0F, 13.0F,
                    scheme.surfaceContainerHigh);
            ui.text(Typeface.BODY, "Position " + Math.round(module.positionX()) + "% / "
                            + Math.round(module.positionY()) + "%",
                    this.width / 2.0F, this.height - 33.0F, scheme.onSurfaceVariant, UI.ALIGN_CENTER);
        } finally {
            ui.end();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        Island island = WatermarkModule.island();
        if (island.inside(mouseX, mouseY)) {
            this.dragging = true;
            this.offsetX = mouseX - island.left();
            this.offsetY = mouseY - island.top();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (this.dragging && button == 0 && WatermarkModule.instance != null) {
            WatermarkModule.island().dragTo(WatermarkModule.instance, mouseX - offsetX, mouseY - offsetY);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
    }

    @Override
    protected void keyTyped(char typed, int key) {
        if (key == 1 || key == 28) {
            mc.displayGuiScreen(null);
        }
    }
}
