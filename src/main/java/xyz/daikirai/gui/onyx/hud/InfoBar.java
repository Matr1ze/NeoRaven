package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.ResourceLocation;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.WatermarkModule;

/**
 * Top-left information pill that ships with Onyx's watermark.
 *
 * <p>A 22px frosted capsule with the player head, name, FPS, server address and ping. It mirrors
 * the island's Material surface (same radius, 1px outlineVariant border, elevation shadow) and
 * sits at a fixed 10px offset from the top-left corner, exactly like the reference screenshot.</p>
 */
public final class InfoBar {

    private static final float MARGIN = 10.0F;
    private static final float HEIGHT = 22.0F;
    private static final float PADDING = 8.0F;
    private static final float HEAD = 12.0F;
    private static final float ICON = 13.0F;
    private static final float GAP = 10.0F;
    private static final float ICON_GAP = 4.0F;
    private static final float MAX_SERVER = 90.0F;
    /** Same frosted recipe as the keybind card: the surface tint covers 58% of the blurred scene. */
    private static final float FROST_COVERAGE = 0.58F;

    private static final String FPS_ICON = "\ue9e4";
    private static final String SERVER_ICON = "\ue875";
    private static final String PING_ICON = "\ue640";

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private long lastFrame;
    private float lastX;
    private float lastY;
    private float lastWidth;
    private float lastHeight;

    public void render(WatermarkModule module) {
        float delta = tick();
        boolean active = module != null && module.isEnabled() && module.infoPanel();
        visible.to(active ? 1.0F : 0.0F, active ? 200.0F : 160.0F, Ease.EMPHASIZED_DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.01F) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.getSession() == null) {
            return;
        }
        String name = mc.getSession().getUsername();
        int fps = Minecraft.getDebugFPS();
        String server = mc.getCurrentServerData() == null ? "Singleplayer" : mc.getCurrentServerData().serverIP;
        int ping = 0;
        if (mc.getNetHandler() != null) {
            NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            if (info != null) {
                ping = info.getResponseTime();
            }
        }
        String fpsText = fps + " fps";
        String pingText = ping + " ms";
        server = ui.truncate(Typeface.BODY_MEDIUM, server == null ? "Singleplayer" : server, MAX_SERVER);

        float width = PADDING + HEAD + 6.0F + Typeface.BODY_MEDIUM.width(name);
        if (module.infoFps()) {
            width += GAP + ICON + ICON_GAP + Typeface.BODY_MEDIUM.width(fpsText);
        }
        if (module.infoServer()) {
            width += GAP + ICON + ICON_GAP + Typeface.BODY_MEDIUM.width(server);
        }
        if (module.infoPing()) {
            width += GAP + ICON + ICON_GAP + Typeface.BODY_MEDIUM.width(pingText);
        }
        width += PADDING;

        this.lastX = MARGIN;
        this.lastY = MARGIN + (1.0F - fade) * 4.0F;
        this.lastWidth = width;
        this.lastHeight = HEIGHT;

        Scheme scheme = Theme.scheme();
        ui.begin();
        try {
            if (module.frosted() && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            float radius = HEIGHT / 2.0F;
            ui.shadow(lastX, lastY, width, HEIGHT, radius, 2);
            ui.rounded(lastX, lastY, width, HEIGHT, radius,
                    Material.withAlpha(scheme.surfaceContainerHigh, module.frosted() ? 0.90F : 0.97F));
            if (module.frosted() && Blur.ready()) {
                ui.frost(lastX, lastY, width, HEIGHT, radius, scheme.surfaceContainerHigh,
                        FROST_COVERAGE);
            }
            // Borderless surface, matching the rest of the ported HUD.

            float centerY = lastY + HEIGHT / 2.0F;
            float cursor = lastX + PADDING;

            ResourceLocation skin = mc.thePlayer.getLocationSkin();
            ui.image(skin, cursor, centerY - HEAD / 2.0F, HEAD, HEAD,
                    8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, 0xFFFFFFFF);
            cursor += HEAD + 6.0F;

            ui.text(Typeface.BODY_MEDIUM, name, cursor, centerY, scheme.onSurface, UI.ALIGN_LEFT);
            cursor += Typeface.BODY_MEDIUM.width(name);

            if (module.infoFps()) {
                cursor += GAP;
                ui.icon(FPS_ICON, cursor + ICON / 2.0F, centerY, ICON, scheme.onSurfaceVariant);
                cursor += ICON + ICON_GAP;
                ui.text(Typeface.BODY_MEDIUM, fpsText, cursor, centerY, scheme.onSurface, UI.ALIGN_LEFT);
                cursor += Typeface.BODY_MEDIUM.width(fpsText);
            }
            if (module.infoServer()) {
                cursor += GAP;
                ui.icon(SERVER_ICON, cursor + ICON / 2.0F, centerY, ICON, scheme.onSurfaceVariant);
                cursor += ICON + ICON_GAP;
                ui.text(Typeface.BODY_MEDIUM, server, cursor, centerY, scheme.onSurface, UI.ALIGN_LEFT);
                cursor += Typeface.BODY_MEDIUM.width(server);
            }
            if (module.infoPing()) {
                cursor += GAP;
                ui.icon(PING_ICON, cursor + ICON / 2.0F, centerY, ICON, scheme.onSurfaceVariant);
                cursor += ICON + ICON_GAP;
                ui.text(Typeface.BODY_MEDIUM, pingText, cursor, centerY, scheme.onSurface, UI.ALIGN_LEFT);
            }
            ui.alphaPop();
        } finally {
            ui.end();
            restoreHudState();
        }
    }

    public boolean inside(float mouseX, float mouseY) {
        return mouseX >= lastX && mouseX < lastX + lastWidth && mouseY >= lastY && mouseY < lastY + lastHeight;
    }

    public float left() {
        return lastX;
    }

    public float top() {
        return lastY;
    }

    public float width() {
        return lastWidth;
    }

    public float height() {
        return lastHeight;
    }

    public void reset() {
        visible.set(0.0F);
        lastFrame = 0L;
    }

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    private static void restoreHudState() {
        net.minecraft.client.renderer.GlStateManager.enableTexture2D();
        net.minecraft.client.renderer.GlStateManager.enableBlend();
        net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        net.minecraft.client.renderer.GlStateManager.disableDepth();
        net.minecraft.client.renderer.GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
