package xyz.daikirai.gui.onyx;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.UI;

import java.io.IOException;

/**
 * The ported Material You ClickGUI screen, equivalent to Onyx's {@code GuiScreenImpl3}.
 *
 * <p>Fits the 640x420 panel into 62% of the screen width and 72% of its height (the same ratio
 * the original used), scales it by the module's "Scale" setting and animates the entrance with a
 * 400ms emphasized scale/fade plus a 250ms scrim. The world behind the panel is blurred with the
 * original frosted-glass pipeline when the "Frosted glass" setting is enabled.</p>
 */
public final class OnyxClickGui extends GuiScreen {

    public static OnyxClickGui instance;

    private final UI ui = new UI();
    private final Panel panel = new Panel();
    private final Anim open = new Anim(0.0F);
    private final Anim scrim = new Anim(0.0F);

    private long lastFrame;
    private float delta = 16.0F;
    private float scale = 1.0F;
    private float mouseX;
    private float mouseY;
    private float screenMouseX;
    private float screenMouseY;
    private float themeScale = -1.0F;

    public OnyxClickGui() {
        instance = this;
    }

    public Panel panel() {
        return panel;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void initGui() {
        super.initGui();
        OnyxClickGuiModule.sync();
        this.scale = fitScale();
        this.lastFrame = System.nanoTime();
        panel.layout(this.width / scale, this.height / scale);
        open.set(0.0F);
        open.to(1.0F, 400.0F, Ease.EMPHASIZED);
        scrim.set(0.0F);
        scrim.to(1.0F, 250.0F, Ease.DECELERATE);
        Blur.request();
    }

    private float fitScale() {
        float fit = Math.min(this.width * 0.62F / Panel.WIDTH, this.height * 0.72F / Panel.HEIGHT);
        return Math.max(0.1F, Math.min(Theme.scale(), fit));
    }

    private void tick() {
        long now = System.nanoTime();
        this.delta = Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        this.lastFrame = now;
        open.update(delta);
        scrim.update(delta);
        OnyxClickGuiModule.sync();
        if (themeScale != Theme.scale()) {
            themeScale = Theme.scale();
            float fitted = fitScale();
            if (Math.abs(fitted - scale) > 0.001F) {
                scale = fitted;
                panel.layout(width / scale, height / scale);
            }
        }
    }

    /** Scales the panel during its entrance; input has to invert the same transform. */
    private float entranceScale() {
        return 0.92F + 0.08F * open.value();
    }

    /**
     * Converts GUI-space coordinates (the units {@link GuiScreen} hands to the mouse callbacks)
     * into the panel's virtual 640x420 space.
     *
     * <p>Drawing applies {@code glScale(panelScale)} and then the entrance scale about the panel
     * centre, so the inverse must undo both. Mixing raw {@code Mouse.getX()} window pixels with
     * scaled GUI units only looked correct at GUI scale 1; at any larger scale every click was
     * displaced towards the bottom-left corner.</p>
     */
    private void setGuiMouse(float guiX, float guiY) {
        this.screenMouseX = guiX;
        this.screenMouseY = guiY;
        float virtualX = guiX / scale;
        float virtualY = guiY / scale;
        float centerX = panel.x() + Panel.WIDTH / 2.0F;
        float centerY = panel.y() + Panel.HEIGHT / 2.0F;
        float entrance = entranceScale();
        this.mouseX = centerX + (virtualX - centerX) / entrance;
        this.mouseY = centerY + (virtualY - centerY) / entrance;
    }

    /** Current cursor position, converted exactly like {@code GuiScreen.handleMouseInput}. */
    private void updateMouse() {
        float guiX = Mouse.getX() * this.width / (float) this.mc.displayWidth;
        float guiY = this.height - Mouse.getY() * this.height / (float) this.mc.displayHeight - 1.0F;
        setGuiMouse(guiX, guiY);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        tick();
        updateMouse();
        Scheme scheme = Theme.scheme();

        ui.begin();
        try {
            drawBackdrop(scheme);
            panel.frame(delta, this.mouseX, this.mouseY);

            ui.push();
            ui.scale(scale, 0.0F, 0.0F);
            float centerX = panel.x() + Panel.WIDTH / 2.0F;
            float centerY = panel.y() + Panel.HEIGHT / 2.0F;
            ui.push();
            ui.scale(0.92F + 0.08F * open.value(), centerX, centerY);
            panel.draw(ui);
            ui.pop();
            ui.pop();
        } finally {
            ui.end();
        }
    }

    private void drawBackdrop(Scheme scheme) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            ui.rect(0.0F, 0.0F, width, height, scheme.surface);
            ui.gradient(0.0F, 0.0F, width, height, scheme.surfaceContainerLow, scheme.surface);
            return;
        }
        if (Theme.frost()) {
            Blur.prepare();
        }
        ui.rect(0.0F, 0.0F, width, height, Theme.scrim(scrim.value()));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        setGuiMouse(mouseX, mouseY);
        panel.mouseClicked(this.mouseX, this.mouseY, button);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        setGuiMouse(mouseX, mouseY);
        panel.mouseReleased(button);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        setGuiMouse(mouseX, mouseY);
        panel.mouseDragged(this.mouseX, this.mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            float guiX = Mouse.getEventX() * this.width / (float) this.mc.displayWidth;
            float guiY = this.height - Mouse.getEventY() * this.height / (float) this.mc.displayHeight - 1.0F;
            setGuiMouse(guiX, guiY);
            panel.wheel(this.mouseX, this.mouseY, wheel / 120.0D);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (panel.key(typed, key)) {
            return;
        }
        if (key == 1) {
            if (panel.search().focused()) {
                panel.search().focus(false);
                return;
            }
            mc.displayGuiScreen(null);
            return;
        }
        if (OnyxClickGuiModule.instance != null && OnyxClickGuiModule.instance.getKeycode() != 0
                && key == OnyxClickGuiModule.instance.getKeycode() && !panel.search().focused()) {
            mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        Blur.request();
        OnyxClickGuiModule.sync();
    }

}
