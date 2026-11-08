package xyz.daikirai.gui.onyx;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.hud.Island;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.WatermarkModule;
import xyz.daikirai.music.MusicIsland;

import java.io.IOException;

/**
 * The ported Material You ClickGUI screen, equivalent to Onyx's {@code GuiScreenImpl3}.
 *
 * <p>Fits the 640x420 panel into 62% of the screen width and 72% of its height (the same ratio
 * the original used), scales it by the module's "Scale" setting and animates the entrance with a
 * Dynamic Island morph matching Tablist and MusicScreen. The world behind the panel is blurred with the
 * original frosted-glass pipeline when the "Frosted glass" setting is enabled.</p>
 */
public final class OnyxClickGui extends GuiScreen {

    public static OnyxClickGui instance;

    /** Above this morph the window covers the screen, so the HUD pass stops being drawn. */

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
    private int layoutWidth, layoutHeight;
    private boolean initialized;
    private boolean closing;
    private boolean closeCommitted;
    private boolean endpointMini;
    private float[] islandBounds;
    private float[] closingBounds;
    private float shellX, shellY, shellW, shellH, shellRadius;
    private float closingRadius;
    private float closingMorph;
    private float morph;

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
        this.themeScale = Theme.scale();
        this.lastFrame = System.nanoTime();
        panel.mouseReleased(0);
        panel.layout(this.width / scale, this.height / scale);
        if (!initialized) {
            Island island = WatermarkModule.island();
            islandBounds = island.width() > 0.0F
                    ? new float[] {island.left(), island.top(), island.width(), island.height()}
                    : island.measure();
            endpointMini = !WatermarkModule.samsaraMode() && MusicIsland.hasTrack()
                    && islandBounds[3] / islandBounds[2] > 0.3F;
            closing = closeCommitted = false;
            open.set(0.0F);
            open.to(1.0F, 420.0F, Ease.LINEAR);
            scrim.set(0.0F);
            scrim.to(1.0F, 320.0F, Ease.STANDARD);
            initialized = true;
        } else {
            islandBounds = WatermarkModule.island().measure();
            if (closing) {
                float sx = width / (float) Math.max(1, layoutWidth);
                float sy = height / (float) Math.max(1, layoutHeight);
                closingBounds[0] *= sx;
                closingBounds[1] *= sy;
                closingBounds[2] *= sx;
                closingBounds[3] *= sy;
                closingRadius *= Math.min(sx, sy);
            }
        }
        layoutWidth = width;
        layoutHeight = height;
        updateShell();
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

    /** Input is enabled once the shell reaches the panel's final, unsquashed layout. */
    private void setGuiMouse(float guiX, float guiY) {
        this.screenMouseX = guiX;
        this.screenMouseY = guiY;
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
        if (closing && open.isDone()) {
            closeCommitted = true;
            if (!WatermarkModule.samsaraMode()) {
                if (endpointMini) MusicIsland.returned();
                WatermarkModule.island().returnFromMusic(endpointMini);
            }
            mc.displayGuiScreen(null);
            return;
        }
        updateMouse();
        Scheme scheme = Theme.scheme();

        ui.begin();
        try {
            // In glass mode the surfaces carry their own alpha, so the UI factor stays neutral.
            ui.surfaceAlpha(1.0F);
            drawBackdrop(scheme);
            panel.frame(delta, interactive() ? this.mouseX : -10000.0F,
                    interactive() ? this.mouseY : -10000.0F);
            updateShell();
            // The rest of the HUD stays on screen for as long as this screen is open - drawn under
            // the shell, so it simply sits behind the panel instead of vanishing when the shell fills.
            xyz.daikirai.gui.onyx.hud.HudPass.render(partialTicks);
            drawMorph(scheme);
        } finally {
            ui.end();
        }
    }

    private boolean interactive() {
        return !closing && open.isDone();
    }

    private float endpointRadius() {
        return endpointMini ? 24.0F * islandBounds[2] / MusicIsland.miniWidth()
                : islandBounds[3] / 2.0F;
    }

    private void updateShell() {
        float progress = Material.clamp(open.progress(), 0.0F, 1.0F);
        float amount = closing ? 1.0F - Ease.STANDARD.get(progress) : Ease.EMPHASIZED.get(progress);
        morph = closing ? closingMorph * amount : amount;
        float targetX = closing ? closingBounds[0] : panel.x() * scale;
        float targetY = closing ? closingBounds[1] : panel.y() * scale;
        float targetW = closing ? closingBounds[2] : Panel.WIDTH * scale;
        float targetH = closing ? closingBounds[3] : Panel.HEIGHT * scale;
        shellX = islandBounds[0] + (targetX - islandBounds[0]) * amount;
        shellY = islandBounds[1] + (targetY - islandBounds[1]) * amount;
        shellW = islandBounds[2] + (targetW - islandBounds[2]) * amount;
        shellH = islandBounds[3] + (targetH - islandBounds[3]) * amount;
        float smallRadius = endpointRadius();
        shellRadius = smallRadius + ((closing ? closingRadius : Panel.RADIUS * scale) - smallRadius) * amount;
    }

    private void drawMorph(Scheme scheme) {
        int color = Material.mix(scheme.surfaceContainerHigh, scheme.surfaceContainer, morph);
        ui.shadow(shellX, shellY, shellW, shellH, shellRadius, Math.round(3.0F + morph));
        ui.rounded(shellX, shellY, shellW, shellH, shellRadius, color);
        if ((Theme.frost() || Theme.translucent()) && Blur.ready()) {
            float coverage = 0.58F + 0.35F * morph;
            if (Theme.translucent()) {
                coverage = Math.max(coverage, Theme.TRANSLUCENT_FROST);
            }
            ui.frost(shellX, shellY, shellW, shellH, shellRadius, color, coverage);
        }
        drawIsland(scheme);
        float alpha = Material.clamp((morph - 0.15F) / 0.65F, 0.0F, 1.0F);
        if (alpha <= 0.003F) return;
        // Release the transition clip when fully open, so toasts and popovers retain their bounds.
        boolean clip = !interactive();
        if (clip) {
            float inset = shellRadius * (1.0F - morph) * 0.3F + 1.0F;
            ui.scissorPush(shellX + inset, shellY + inset, Math.max(0.0F, shellW - inset * 2.0F),
                    Math.max(0.0F, shellH - inset * 2.0F));
        }
        ui.push();
        ui.translate(shellX, shellY - (1.0F - morph) * 8.0F);
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(-panel.x(), -panel.y());
        ui.alphaPush(alpha);
        panel.drawContents(ui);
        ui.alphaPop();
        ui.pop();
        if (clip) ui.scissorPop();
    }

    private void drawIsland(Scheme scheme) {
        float alpha = 1.0F - Material.clamp(morph * 3.0F, 0.0F, 1.0F);
        if (alpha <= 0.003F) return;
        Island island = WatermarkModule.island();
        boolean mini = endpointMini && MusicIsland.hasTrack();
        float w = mini ? MusicIsland.miniWidth() : island.designWidth();
        float h = mini ? MusicIsland.miniHeight() : island.designHeight();
        if (w < 1.0F || h < 1.0F) return;
        float factor = Math.min(Math.min(islandBounds[2] / w, islandBounds[3] / h),
                Math.min(shellW / w, shellH / h));
        ui.scissorPush(shellX - 1.5F, shellY - 1.5F, shellW + 3.0F, shellH + 3.0F);
        ui.push();
        ui.translate(shellX + (shellW - w * factor) / 2.0F, shellY + (shellH - h * factor) / 2.0F);
        ui.scale(factor, 0.0F, 0.0F);
        ui.alphaPush(alpha);
        if (mini) MusicIsland.draw(ui, scheme, w, h, 1.0F);
        else island.snapshot(ui, scheme, WatermarkModule.instance == null || WatermarkModule.instance.logo());
        ui.alphaPop();
        ui.pop();
        ui.scissorPop();
    }

    /** Forge intercepts null-screen closes so the screen survives until the collapse finishes. */
    public boolean deferClose() {
        if (closeCommitted) return false;
        if (!closing) {
            if (!initialized) return false;
            updateShell();
            closingBounds = new float[] {shellX, shellY, shellW, shellH};
            closingRadius = shellRadius;
            closingMorph = morph;
            endpointMini = !WatermarkModule.samsaraMode() && MusicIsland.miniVisible();
            Island island = WatermarkModule.island();
            if (!WatermarkModule.samsaraMode()) island.returnFromMusic(endpointMini);
            islandBounds = island.measure();
            closing = true;
            open.to(0.0F, 280.0F, Ease.LINEAR);
            scrim.to(0.0F, 220.0F, Ease.DECELERATE);
            panel.mouseReleased(0);
            panel.search().focus(false);
        }
        return true;
    }

    private void drawBackdrop(Scheme scheme) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            ui.rect(0.0F, 0.0F, width, height, scheme.surface);
            ui.gradient(0.0F, 0.0F, width, height, scheme.surfaceContainerLow, scheme.surface);
            return;
        }
        if (Theme.frost() || Theme.translucent()) {
            Blur.prepare(Theme.translucent() ? 4 : 2, Theme.translucent() ? 4 : 2);
        }
        ui.rect(0.0F, 0.0F, width, height, Theme.scrim(scrim.value()));
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        if (!interactive()) return;
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
        if (!interactive()) return;
        setGuiMouse(mouseX, mouseY);
        panel.mouseDragged(this.mouseX, this.mouseY, button);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && interactive()) {
            float guiX = Mouse.getEventX() * this.width / (float) this.mc.displayWidth;
            float guiY = this.height - Mouse.getEventY() * this.height / (float) this.mc.displayHeight - 1.0F;
            setGuiMouse(guiX, guiY);
            panel.wheel(this.mouseX, this.mouseY, wheel / 120.0D);
        }
    }

    @Override
    protected void keyTyped(char typed, int key) throws IOException {
        if (!interactive()) {
            if (!closing && (key == 1 || OnyxClickGuiModule.instance != null
                    && key != 0 && key == OnyxClickGuiModule.instance.getKeycode())) {
                deferClose();
            }
            return;
        }
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
        panel.mouseReleased(0);
        initialized = false;
        Blur.request();
        OnyxClickGuiModule.sync();
    }

}
