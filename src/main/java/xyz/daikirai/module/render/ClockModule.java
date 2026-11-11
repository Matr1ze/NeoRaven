package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.onyx.hud.AndroidClock;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * The Material 3 expressive clock as a HUD element.
 *
 * <p>Same face the title screen uses ({@link AndroidClock}, ported from pvp_utils'
 * {@code ClockHudRenderer}): scalloped dial, hour/minute hands, the seconds dot riding the rim and the
 * date turning inside the dial. The module only adds what a HUD needs - size, position (drag while
 * chat is open, same gesture as the island), frosted backdrop and the shadow toggle.</p>
 */
public final class ClockModule extends VisualModule {

    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;

    public static ClockModule instance;

    private final SliderSetting clockScale = slider("Scale", 1.0D, 0.5D, 2.5D, 0.05D, "x");
    private final SliderSetting positionX = slider("Position X", 50, 0, 100, 0.01D, "%");
    private final SliderSetting positionY = slider("Position Y", 12, 0, 100, 0.01D, "%");
    private final ButtonSetting shadow = toggle("Shadow", true);
    private final ButtonSetting showDate = toggle("Show date", true);
    private final ButtonSetting dateFollows = toggle("Date follows seconds", false);
    private final ButtonSetting chinese = toggle("Chinese date", true);

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);

    private long lastFrame;
    private float x;
    private float y;
    private float w;
    private float h;
    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    public ClockModule(int key) {
        super("Clock", key);
        instance = this;
    }

    @Override
    public void onEnable() {
        visible.set(0.0F);
        lastFrame = 0L;
        dragging = false;
    }

    @SubscribeEvent
    public void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || instance == null || !instance.isEnabled()) {
            return;
        }
        if (!Overlay.canRender()) {
            visible.set(0.0F);
            return;
        }
        draw(event.renderTickTime);
    }

    /** Draws the clock; also called from {@code HudPass} while a morphing screen is open. */
    public static void renderHud() {
        // HudPass bypasses the tick event, so the enabled check has to happen here as well.
        if (instance != null && instance.isEnabled()) {
            instance.draw(0.0F);
        }
    }

    private void draw(float partialTicks) {
        if (!isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        visible.to(1.0F, 180.0F, Ease.EMPHASIZED_DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.004F) {
            return;
        }

        float size = AndroidClock.DIAL * (float) clockScale.getInput();
        ScaledResolution resolution = new ScaledResolution(mc);
        float fit = Math.min(1.0F, Math.min((resolution.getScaledWidth() - EDGE) / size,
                (resolution.getScaledHeight() - EDGE) / size));
        float scale = Math.max(0.1F, fit) * (0.94F + 0.06F * fade);
        w = size * scale;
        h = w;
        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h - EDGE);
        x = MARGIN + maxX * (Material.clamp((float) positionX.getInput(), 0.0F, 100.0F) / 100.0F);
        y = MARGIN + maxY * (Material.clamp((float) positionY.getInput(), 0.0F, 100.0F) / 100.0F);
        handleDrag(mc, resolution);

        Scheme scheme = Theme.scheme();
        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();
        ui.begin();
        try {
            ui.alphaPush(fade);
            ui.translate(x + w / 2.0F, y + h / 2.0F + (1.0F - fade) * 6.0F);
            AndroidClock.Hands hands = AndroidClock.hands(chinese.isToggled());
            AndroidClock.draw(ui, 0.0F, 0.0F, size * scale,
                    AndroidClock.Palette.scheme(shadow.isToggled()), hands, showDate.isToggled(),
                    dateFollows.isToggled());
            ui.alphaPop();
        } finally {
            ui.end();
            GlStateManager.popMatrix();
            restoreHudState();
        }
    }

    // ------------------------------------------------------------------ //
    // Dragging (chat open) - the same gesture as the island and the cards
    // ------------------------------------------------------------------ //

    private void handleDrag(Minecraft mc, ScaledResolution resolution) {
        boolean chat = mc.currentScreen instanceof GuiChat;
        if (!chat || !Mouse.isButtonDown(0)) {
            dragging = false;
            return;
        }
        float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float mouseY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
        if (dragging) {
            dragTo(mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (inside(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
    }

    private void dragTo(float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h - EDGE);
        positionX.setValue(Material.clamp((desiredX - MARGIN) / maxX * 100.0F, 0.0F, 100.0F));
        positionY.setValue(Material.clamp((desiredY - MARGIN) / maxY * 100.0F, 0.0F, 100.0F));
    }

    public boolean inside(float mouseX, float mouseY) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public float left() {
        return x;
    }

    public float top() {
        return y;
    }

    public float width() {
        return w;
    }

    public float height() {
        return h;
    }

    private static void restoreHudState() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
