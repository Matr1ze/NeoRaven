package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.combat.KillAuraModule;
import xyz.daikirai.module.render.TargetHudModule;
import xyz.daikirai.util.Render3D;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

/**
 * Material 3 Expressive target card, ported from OpenOnyx's {@code TargetHUDModule}.
 *
 * <p>Onyx drew a rounded, frosted card with the avatar, name, distance and a health bar, and had a
 * "world" mode that anchored the card above the target's head while it was on screen. This port
 * keeps that behaviour and the KillAura source:</p>
 *
 * <ul>
 *   <li>the card follows the entity KillAura is currently attacking, anchored above its head;</li>
 *   <li>when the target disappears the card holds its last position for the configured linger
 *   time (0.3s by default) and then fades out;</li>
 *   <li>when the target switches, the new info is shown first and the card only starts moving
 *   after a short hold, so it never cross-fades while sliding;</li>
 *   <li>when the target is off screen (behind the camera or outside the viewport) the card glides
 *   back to the user's configured home position.</li>
 * </ul>
 */
public final class TargetHud {

    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    private static final float PAD = 9.0F;
    private static final float AVATAR = 30.0F;
    private static final float AVATAR_RADIUS = 10.0F;
    private static final float AVATAR_GAP = 9.0F;
    private static final float MIN_WIDTH = 152.0F;
    private static final float MAX_WIDTH = 232.0F;
    private static final float MIN_BAR_WIDTH = 86.0F;
    private static final float HEIGHT = 48.0F;
    private static final float RADIUS = 16.0F;
    private static final float NAME_Y = 16.0F;
    private static final float CHIP_HEIGHT = 14.0F;
    private static final float BAR_Y = 24.0F;
    private static final float BAR_HEIGHT = 6.0F;
    private static final float STATS_Y = 37.0F;
    private static final float BLUR_COVERAGE = 0.62F;
    private static final float ANCHOR_GAP = 10.0F;
    private static final float OFF_SCREEN_MARGIN = 28.0F;
    private static final long SWITCH_HOLD_MS = 160L;

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer SCREEN = BufferUtils.createFloatBuffer(3);

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim width = new Anim(MIN_WIDTH);
    private final Anim content = new Anim(1.0F);
    private final Anim healthFill = new Anim(0.0F);
    private final Anim healthLag = new Anim(0.0F);
    private final Anim lowHealth = new Anim(0.0F);

    private EntityLivingBase entity;
    private long lostAt;
    private long switchedAt;
    private boolean placed;

    private String name = "";
    private float health;
    private float maxHealth = 20.0F;
    private float absorption;
    private int armor;
    private double distance;
    private ResourceLocation skin;

    private float x;
    private float y;
    private float w = MIN_WIDTH;
    private float h = HEIGHT;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private long lastFrame;

    /** Draws (and updates) the card; {@code preview} keeps it visible for the position editor. */
    public void render(TargetHudModule module, boolean preview, float partialTicks) {
        if (module == null) {
            return;
        }
        float delta = tick();
        Minecraft mc = Minecraft.getMinecraft();
        long now = System.currentTimeMillis();
        boolean chatOpen = mc.currentScreen instanceof GuiChat;

        updateTarget(module, preview, now, delta);


        boolean active = (preview || module.isEnabled()) && entity != null;
        visible.to(active ? 1.0F : 0.0F, active ? 180.0F : 200.0F,
                active ? Ease.EMPHASIZED : Ease.EMPHASIZED_ACCELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.002F) {
            if (!preview) {
                clearContent();
                return;
            }
            fade = 1.0F;
        }

        Scheme scheme = Theme.scheme();
        ScaledResolution resolution = new ScaledResolution(mc);
        float screenW = resolution.getScaledWidth();
        float screenH = resolution.getScaledHeight();

        float targetWidth = cardWidth(module);
        width.to(targetWidth, 220.0F, Ease.EMPHASIZED_DECELERATE);
        width.update(delta);
        float layoutWidth = width.value();
        this.w = layoutWidth * module.scale();
        this.h = HEIGHT * module.scale();

        updatePosition(module, mc, resolution, screenW, screenH, preview, chatOpen, now, delta, partialTicks);

        ui.begin();
        try {
            GlStateManager.pushMatrix();
            mc.entityRenderer.setupOverlayRendering();
            if (module.frosted() && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.push();
            ui.translate(x + w / 2.0F, y + h / 2.0F);
            ui.scale(module.scale() * (0.96F + 0.04F * fade), 0.0F, 0.0F);
            ui.translate(-layoutWidth / 2.0F, -HEIGHT / 2.0F);

            drawCard(module, scheme, layoutWidth);

            ui.pop();
            ui.alphaPop();
        } finally {
            GlStateManager.popMatrix();
            ui.end();
            restoreHudState();
        }
    }

    // ------------------------------------------------------------------ //
    // Target tracking
    // ------------------------------------------------------------------ //

    private void updateTarget(TargetHudModule module, boolean preview, long now, float delta) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityLivingBase target = preview ? mc.thePlayer : killAuraTarget();
        if (target != null) {
            if (target != entity) {
                boolean switching = entity != null;
                entity = target;
                switchedAt = now;
                // A switch shows the new info immediately; only the movement is held back.
                content.set(switching ? 0.2F : 0.55F);
                content.to(1.0F, switching ? 180.0F : 220.0F, Ease.DECELERATE);
                healthFill.set(healthRatio(target));
                healthLag.set(healthRatio(target));
                if (!switching) {
                    placed = false;
                }
            }
            lostAt = 0;
            refresh(target);
        } else if (entity != null) {
            if (lostAt == 0L) {
                lostAt = now;
            }
            if (now - lostAt >= (long) (module.linger() * 1000.0F)) {
                entity = null;
            }
        }
        content.update(delta);
        healthFill.update(delta);
        healthLag.update(delta);
        lowHealth.to(healthRatio() <= 0.3F ? 1.0F : 0.0F, 250.0F, Ease.DECELERATE);
        lowHealth.update(delta);
    }

    private void refresh(EntityLivingBase target) {
        Minecraft mc = Minecraft.getMinecraft();
        name = target.getDisplayName().getUnformattedText();
        health = target.getHealth();
        maxHealth = Math.max(target.getMaxHealth(), 1.0F);
        absorption = Math.max(target.getAbsorptionAmount(), 0.0F);
        armor = target.getTotalArmorValue();
        skin = target instanceof AbstractClientPlayer ? ((AbstractClientPlayer) target).getLocationSkin() : null;
        if (mc.thePlayer != null && target != mc.thePlayer) {
            distance = target.getDistanceToEntity(mc.thePlayer);
        } else {
            distance = 0.0;
        }
        healthFill.to(healthRatio(), 250.0F, Ease.DECELERATE);
        healthLag.to(healthRatio(), 650.0F, Ease.DECELERATE);
    }

    /** The entity KillAura is attacking, or {@code null} when it is gone. */
    private static EntityLivingBase killAuraTarget() {
        KillAuraModule.Target target = KillAuraModule.target;
        if (target == null || target.entity == null || !(target.entity instanceof EntityLivingBase)) {
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) target.entity;
        Minecraft mc = Minecraft.getMinecraft();
        if (living.isDead || living.worldObj != mc.theWorld) {
            return null;
        }
        return living;
    }

    private float healthRatio(EntityLivingBase target) {
        return Material.clamp(target.getHealth() / Math.max(target.getMaxHealth(), 1.0F), 0.0F, 1.0F);
    }

    private float healthRatio() {
        return Material.clamp(health / maxHealth, 0.0F, 1.0F);
    }

    private float cardWidth(TargetHudModule module) {
        float chipWidth = module.showDistance() ? distanceChipWidth() + 8.0F : 0.0F;
        float nameWidth = Math.min(Typeface.HEADLINE.width(name), 100.0F);
        float contentWidth = CONTENT_X() + Math.max(nameWidth + chipWidth, MIN_BAR_WIDTH) + PAD;
        return Material.clamp(contentWidth, MIN_WIDTH, MAX_WIDTH);
    }

    private static float CONTENT_X() {
        return PAD + AVATAR + AVATAR_GAP;
    }

    private float distanceChipWidth() {
        return Typeface.MICRO.width(distanceText()) + 10.0F;
    }

    private String distanceText() {
        return String.format(Locale.ROOT, "%.1fm", distance);
    }

    // ------------------------------------------------------------------ //
    // Position: follow the target, hold on switch/linger, fall back home
    // ------------------------------------------------------------------ //

    private void updatePosition(TargetHudModule module, Minecraft mc, ScaledResolution resolution,
                                float screenW, float screenH, boolean preview, boolean chatOpen,
                                long now, float delta, float partialTicks) {
        float maxX = Math.max(0.0F, screenW - w - EDGE);
        float maxY = Math.max(0.0F, screenH - h - EDGE);
        float homeX = MARGIN + maxX * (Material.clamp(module.positionX(), 0.0F, 100.0F) / 100.0F);
        float homeY = MARGIN + maxY * (Material.clamp(module.positionY(), 0.0F, 100.0F) / 100.0F);

        float desiredX = homeX;
        float desiredY = homeY;
        boolean follow = false;

        if (!preview && !chatOpen && entity != null && lostAt == 0L) {
            float[] projected = project(entity, partialTicks, screenW, screenH);
            if (projected != null) {
                desiredX = Material.clamp(projected[0] - w / 2.0F, MARGIN, Math.max(MARGIN, screenW - w - MARGIN));
                desiredY = Material.clamp(projected[1] - h - ANCHOR_GAP, MARGIN, Math.max(MARGIN, screenH - h - MARGIN));
                follow = true;
            }
        }

        boolean lingering = lostAt != 0L;
        boolean switching = now - switchedAt < SWITCH_HOLD_MS;
        boolean hidden = entity == null;

        if (!placed) {
            this.x = preview || chatOpen || !follow ? homeX : desiredX;
            this.y = preview || chatOpen || !follow ? homeY : desiredY;
            placed = true;
        } else if (hidden) {
            // Fading out: keep the last position, the card must not run home while it disappears.
        } else if (lingering) {
            // Hold the last position for the linger window.
        } else if (switching) {
            // New target's info is already showing; let it read before the card starts moving.
        } else {
            float blend = 1.0F - (float) Math.exp(-delta / 70.0F);
            this.x += (desiredX - this.x) * blend;
            this.y += (desiredY - this.y) * blend;
        }

        if (!preview) {
            handleDrag(module, mc, resolution, chatOpen, screenW, screenH);
        }
    }

    /** Projects the target's head into GUI coordinates, or {@code null} when it is off screen. */
    private static float[] project(EntityLivingBase target, float partialTicks, float screenW, float screenH) {
        Minecraft mc = Minecraft.getMinecraft();
        RenderManager manager = mc.getRenderManager();
        double px = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks;
        double py = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks
                + target.getEyeHeight() + 0.35D;
        double pz = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks;

        Entity view = mc.getRenderViewEntity();
        if (view != null && view.getLook(partialTicks)
                .dotProduct(new Vec3(px - manager.viewerPosX, py - manager.viewerPosY, pz - manager.viewerPosZ)) <= 0.1D) {
            return null;
        }

        Render3D.setupCamera(partialTicks);
        MODELVIEW.clear();
        PROJECTION.clear();
        VIEWPORT.clear();
        SCREEN.clear();
        GL11.glGetFloat(2982, MODELVIEW);
        GL11.glGetFloat(2983, PROJECTION);
        GL11.glGetInteger(2978, VIEWPORT);
        MODELVIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();
        boolean ok = GLU.gluProject(
                (float) (px - manager.viewerPosX),
                (float) (py - manager.viewerPosY),
                (float) (pz - manager.viewerPosZ),
                MODELVIEW, PROJECTION, VIEWPORT, SCREEN);
        Render3D.setupOverlay();
        if (!ok) {
            return null;
        }
        float depth = SCREEN.get(2);
        if (depth < 0.0F || depth > 1.0F) {
            return null;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = resolution.getScaleFactor();
        float sx = SCREEN.get(0) / scale;
        float sy = (mc.displayHeight - SCREEN.get(1)) / scale;
        if (sx < -OFF_SCREEN_MARGIN || sx > screenW + OFF_SCREEN_MARGIN
                || sy < -OFF_SCREEN_MARGIN || sy > screenH + OFF_SCREEN_MARGIN) {
            return null;
        }
        return new float[]{sx, sy};
    }

    private void handleDrag(TargetHudModule module, Minecraft mc, ScaledResolution resolution,
                            boolean chatOpen, float screenW, float screenH) {
        if (!chatOpen || !Mouse.isButtonDown(0)) {
            dragging = false;
            return;
        }
        float mouseX = Mouse.getX() * screenW / (float) mc.displayWidth;
        float mouseY = screenH - Mouse.getY() * screenH / (float) mc.displayHeight - 1.0F;
        if (dragging) {
            dragTo(module, mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (inside(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
    }

    public void dragTo(TargetHudModule module, float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h - EDGE);
        float percentX = Material.clamp((desiredX - MARGIN) / maxX * 100.0F, 0.0F, 100.0F);
        float percentY = Material.clamp((desiredY - MARGIN) / maxY * 100.0F, 0.0F, 100.0F);
        module.setPosition(percentX, percentY);
        this.x = desiredX;
        this.y = desiredY;
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private void drawCard(TargetHudModule module, Scheme scheme, float width) {
        int surface = Material.withAlpha(scheme.surfaceContainerHigh, 1.0F);
        ui.shadow(0.0F, 0.0F, width, HEIGHT, RADIUS, 3);
        ui.rounded(0.0F, 0.0F, width, HEIGHT, RADIUS, surface);
        if (module.frosted() && Blur.ready()) {
            ui.frost(0.0F, 0.0F, width, HEIGHT, RADIUS, scheme.surfaceContainerHigh, BLUR_COVERAGE);
        }

        ui.alphaPush(content.value());
        drawAvatar(scheme);

        float contentX = CONTENT_X();
        float right = width - PAD;
        float chipWidth = module.showDistance() ? distanceChipWidth() : 0.0F;
        float nameWidth = right - contentX - (chipWidth > 0.0F ? chipWidth + 8.0F : 0.0F);
        String label = ui.truncate(Typeface.HEADLINE, name, Math.max(20.0F, nameWidth));
        ui.text(Typeface.HEADLINE, label, contentX, NAME_Y, scheme.onSurface, UI.ALIGN_LEFT);

        if (chipWidth > 0.0F) {
            float chipX = right - chipWidth;
            ui.rounded(chipX, NAME_Y - CHIP_HEIGHT / 2.0F, chipWidth, CHIP_HEIGHT, CHIP_HEIGHT / 2.0F,
                    scheme.secondaryContainer);
            ui.text(Typeface.MICRO, distanceText(), chipX + chipWidth / 2.0F, NAME_Y,
                    scheme.onSecondaryContainer, UI.ALIGN_CENTER);
        }

        drawHealthBar(scheme, contentX, right - contentX);
        drawStats(module, scheme, contentX, right);
        ui.alphaPop();
    }

    private void drawAvatar(Scheme scheme) {
        ui.rounded(PAD, PAD, AVATAR, AVATAR, AVATAR_RADIUS, scheme.secondaryContainer);
        if (skin != null) {
            ui.image(skin, PAD + 2.0F, PAD + 2.0F, AVATAR - 4.0F, AVATAR - 4.0F,
                    8.0F / 64.0F, 8.0F / 64.0F, 16.0F / 64.0F, 16.0F / 64.0F, 0xFFFFFFFF);
        } else {
            String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
            ui.text(Typeface.HEADLINE, letter, PAD + AVATAR / 2.0F, PAD + AVATAR / 2.0F,
                    scheme.onSecondaryContainer, UI.ALIGN_CENTER);
        }
    }

    private void drawHealthBar(Scheme scheme, float barX, float barWidth) {
        float fill = Material.clamp(healthFill.value(), 0.0F, 1.0F);
        float lag = Material.clamp(healthLag.value(), 0.0F, 1.0F);
        float low = Material.clamp(lowHealth.value(), 0.0F, 1.0F);
        int fillColor = mixSrgb(scheme.primary, scheme.error, low);
        int track = Material.mix(scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, 0.9F);

        ui.rounded(barX, BAR_Y, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2.0F, track);

        if (lag > fill + 0.002F) {
            ui.rounded(barX, BAR_Y, barWidth * lag, BAR_HEIGHT, BAR_HEIGHT / 2.0F,
                    Material.withAlpha(scheme.error, 0.55F));
        }
        if (fill > 0.002F) {
            ui.rounded(barX, BAR_Y, barWidth * fill, BAR_HEIGHT, BAR_HEIGHT / 2.0F, fillColor);
        }
        float absorb = Material.clamp(absorption / maxHealth, 0.0F, 1.0F - fill);
        if (absorb > 0.002F) {
            ui.rounded(barX + barWidth * fill, BAR_Y, barWidth * absorb, BAR_HEIGHT,
                    BAR_HEIGHT / 2.0F, scheme.tertiary);
        }
    }

    /**
     * Plain sRGB mix for the health bar. {@link Material#mix} blends in Onyx's linear working
     * space, which turns the primary → error ramp into a muddy beige halfway through; the bar
     * needs the two roles to stay recognisable.
     */
    private static int mixSrgb(int from, int to, float t) {
        t = Material.clamp(t, 0.0F, 1.0F);
        return Material.argb(255,
                Math.round(Material.red(from) + (Material.red(to) - Material.red(from)) * t),
                Math.round(Material.green(from) + (Material.green(to) - Material.green(from)) * t),
                Math.round(Material.blue(from) + (Material.blue(to) - Material.blue(from)) * t));
    }

    private void drawStats(TargetHudModule module, Scheme scheme, float contentX, float right) {
        if (module.showHealth()) {
            String text = String.format(Locale.ROOT, "%.1f / %d", health, Math.round(maxHealth));
            ui.text(Typeface.CAPTION, text, contentX, STATS_Y, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
        }
        if (module.showArmor() && armor > 0) {
            String text = String.valueOf(armor);
            float textWidth = Typeface.CAPTION.width(text);
            ui.text(Typeface.CAPTION, text, right, STATS_Y, scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
            ui.icon("\ue9e0", right - textWidth - 8.0F, STATS_Y, 10.0F, scheme.onSurfaceVariant);
        }
    }

    // ------------------------------------------------------------------ //
    // Misc
    // ------------------------------------------------------------------ //

    public boolean inside(float mouseX, float mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
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

    public void reset() {
        entity = null;
        lostAt = 0L;
        switchedAt = 0L;
        placed = false;
        dragging = false;
        visible.set(0.0F);
        content.set(1.0F);
        healthFill.set(0.0F);
        healthLag.set(0.0F);
        lowHealth.set(0.0F);
        width.set(MIN_WIDTH);
        lastFrame = 0L;
    }

    private void clearContent() {
        // Keep the cached text while fading out; only drop it once the card is fully hidden.
        if (entity == null && visible.isDone() && visible.value() <= 0.002F) {
            name = "";
            health = 0.0F;
            absorption = 0.0F;
            armor = 0;
            skin = null;
            placed = false;
        }
    }

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    private static void restoreHudState() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
