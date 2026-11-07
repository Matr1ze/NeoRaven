package xyz.daikirai.gui.onyx;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.UI;

import java.io.IOException;

/**
 * The ported Material You ClickGUI screen, equivalent to Onyx's {@code GuiScreenImpl3}.
 *
 * <p>Fits the 640x420 panel into 62% of the screen width and 72% of its height (the same ratio
 * the original used), scales it by the module's "Scale" setting and opens without an entrance
 * animation. The world behind the panel is blurred with the original frosted-glass pipeline when
 * the "Frosted glass" setting is enabled.</p>
 */
public final class OnyxClickGui extends GuiScreen {

    public static OnyxClickGui instance;

    private final UI ui = new UI();
    private final Panel panel = new Panel();

    private long lastFrame;
    private float delta = 16.0F;
    private float scale = 1.0F;
    private float mouseX;
    private float mouseY;
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

    /**
     * Converts GUI-space coordinates (the units {@link GuiScreen} hands to the mouse callbacks)
     * into the panel's virtual 640x420 space.
     *
     * <p>Mixing raw {@code Mouse.getX()} window pixels with scaled GUI units only looked correct
     * at GUI scale 1; at any larger scale every click was displaced towards the bottom-left
     * corner.</p>
     */
    private void setGuiMouse(float guiX, float guiY) {
        this.mouseX = guiX / scale;
        this.mouseY = guiY / scale;
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
            panel.draw(ui);
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
        ui.rect(0.0F, 0.0F, width, height, Theme.scrim(1.0F));
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
        xyz.daikirai.client.ClientConfig.save();
    }

}
