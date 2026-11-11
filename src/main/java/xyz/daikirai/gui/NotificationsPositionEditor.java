package xyz.daikirai.gui;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.hud.Notifications;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.NotificationsModule;

import java.io.IOException;
import java.util.Locale;

/**
 * Drag editor for the Material notification stack.
 *
 * <p>The card follows the watermark's interaction model: drag it in game while chat is open, or
 * open this screen from the module's "Edit position" button. The position is stored as screen
 * percentages, so it survives resolution changes.</p>
 *
 * <p>The mouse wheel resizes the card here (the same 0.05 step the "Scale" setting uses), which
 * makes the size adjustable without leaving the preview.</p>
 */
public final class NotificationsPositionEditor extends GuiScreen {

    private static final double SIZE_STEP = 0.05D;

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
        NotificationsModule module = NotificationsModule.instance;
        if (module == null) {
            return;
        }
        Notifications hud = NotificationsModule.hud();
        hud.render(module, true);

        Scheme scheme = Theme.scheme();
        ui.begin();
        try {
            ui.text(Typeface.HEADLINE, "Notifications position", this.width / 2.0F, 26.0F,
                    scheme.onSurface, UI.ALIGN_CENTER);
            ui.text(Typeface.BODY, "Drag to move  ·  Scroll to resize  ·  Esc when done",
                    this.width / 2.0F, 46.0F, scheme.onSurfaceVariant, UI.ALIGN_CENTER);

            String position = "Position " + Math.round(module.positionX()) + "% / "
                    + Math.round(module.positionY()) + "%"
                    + "   ·   Size " + String.format(Locale.ROOT, "%.2fx", module.scale());
            float boxWidth = Math.max(Typeface.BODY.width(position) + 40.0F,
                    Typeface.BODY.width("Drag to move  ·  Scroll to resize  ·  Esc when done") + 40.0F);
            ui.rounded(this.width / 2.0F - boxWidth / 2.0F, this.height - 46.0F, boxWidth, 26.0F, 13.0F,
                    scheme.surfaceContainerHigh);
            ui.text(Typeface.BODY, position, this.width / 2.0F, this.height - 33.0F,
                    scheme.onSurfaceVariant, UI.ALIGN_CENTER);
        } finally {
            ui.end();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || NotificationsModule.instance == null || NotificationsModule.scale == null) {
            return;
        }
        double next = NotificationsModule.scale.getInput() + (wheel > 0 ? SIZE_STEP : -SIZE_STEP);
        NotificationsModule.scale.setValue(next);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        Notifications hud = NotificationsModule.hud();
        if (hud.inside(mouseX, mouseY)) {
            this.dragging = true;
            this.offsetX = mouseX - hud.left();
            this.offsetY = mouseY - hud.top();
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        if (this.dragging && button == 0 && NotificationsModule.instance != null) {
            NotificationsModule.hud().dragTo(NotificationsModule.instance, mouseX - offsetX, mouseY - offsetY);
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
