package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.NotificationsModule;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Geometry, type scale, stacking and transitions ported from Onyx NotificationsModule. */
public final class Notifications {
    private static final float HEIGHT = 34.0F, GAP = 5.0F, RADIUS = 12.0F, MARGIN = 10.0F;
    private static final Typeface.Style TITLE = Typeface.custom(9.0F, 11.0F, 14.0F, true);
    private static final Typeface.Style SUBTITLE = Typeface.custom(9.0F, 8.0F, 10.0F, true);
    private static final Ease EXIT = Ease.NOTIFICATION_EXIT;
    private final UI ui = new UI();
    private final List<Entry> entries = new ArrayList<Entry>();
    private final Entry example = new Entry("Notifications", "Drag to move", NotificationLevel.INFO, null, 3000.0F);
    private long lastFrame;
    private float x, y, w, h;
    private boolean dragging;
    private float offsetX, offsetY;

    public void post(String title, String message, NotificationLevel level, Integer accent, float hold, int maximum) {
        entries.add(new Entry(Text.strip(title == null ? "Notification" : title),
                message == null ? null : Text.strip(message), level == null ? NotificationLevel.INFO : level, accent, hold));
        trim(maximum);
    }
    private void trim(int maximum) {
        while (entries.size() > Math.max(1, maximum)) entries.remove(0);
    }
    private void advance(float delta) {
        for (Iterator<Entry> iterator = entries.iterator(); iterator.hasNext();) {
            Entry entry = iterator.next();
            entry.remaining -= delta;
            boolean expired = entry.remaining <= 0.0F;
            entry.show.to(expired ? 0.0F : 1.0F, expired ? 200.0F : 300.0F, expired ? EXIT : Ease.EMPHASIZED);
            entry.show.update(delta);
            if (expired && entry.show.isDone()) iterator.remove();
        }
    }

    public void render(NotificationsModule module, boolean preview) {
        if (module == null || !preview && !module.isEnabled()) { suspend(); return; }
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        trim(module.maxStack());
        if (!preview) advance(delta);
        Minecraft mc = Minecraft.getMinecraft();
        boolean chat = mc.currentScreen instanceof GuiChat;
        List<Entry> drawn = entries;
        if (preview || entries.isEmpty() && chat) {
            example.show.set(1.0F);
            drawn = java.util.Collections.singletonList(example);
        }
        if (drawn.isEmpty()) { w = h = 0.0F; dragging = false; return; }
        float width = 130.0F;
        for (Entry entry : drawn) {
            float text = TITLE.width(entry.title);
            if (module.subtitles() && entry.message != null) text = Math.max(text, SUBTITLE.width(entry.message));
            width = Math.max(width, 45.0F + text);
        }
        width = Math.min(width, 240.0F);
        float height = drawn.size() * HEIGHT + Math.max(0, drawn.size() - 1) * GAP;
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = Math.min(module.scale(), Math.min(Math.max(1, resolution.getScaledWidth() - 20) / width,
                Math.max(1, resolution.getScaledHeight() - 20) / height));
        this.w = width * scale; this.h = height * scale;
        layout(module, resolution);
        if (!preview) handleDrag(module, mc, resolution, chat);

        GlStateManager.pushMatrix();
        mc.entityRenderer.setupOverlayRendering();
        ui.begin();
        try {
            // Same one-capture-per-frame backdrop as the island, Keybinds and Scoreboard.
            if (module.frosted()) Blur.prepare();
            ui.push();
            ui.translate(x, y); ui.scale(scale, 0.0F, 0.0F);
            Scheme scheme = Theme.scheme();
            boolean bottom = module.positionY() > 50.0F;
            for (int i = 0; i < drawn.size(); i++) {
                int index = bottom ? drawn.size() - 1 - i : i;
                draw(module, scheme, drawn.get(i), index * (HEIGHT + GAP), width);
            }
            ui.pop();
        } finally {
            ui.end(); GlStateManager.popMatrix();
            GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.disableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private void draw(NotificationsModule module, Scheme scheme, Entry entry, float top, float width) {
        float show = entry.show.value();
        if (show <= 0.001F) return;
        int accent = entry.accent == null ? entry.level.color(scheme) : entry.accent;
        ui.alphaPush(show); ui.push();
        ui.translate((1.0F - show) * 14.0F, top);
        ui.shadow(0, 0, width, HEIGHT, RADIUS, 3);
        ui.rounded(0, 0, width, HEIGHT, RADIUS, scheme.surfaceContainerHigh);
        if (module.frosted() && Blur.ready()) ui.frost(0, 0, width, HEIGHT, RADIUS, scheme.surfaceContainerHigh, 0.55F);
        ui.icon(entry.level.icon, 18.0F, 17.0F, 16.0F, accent);
        boolean subtitle = module.subtitles() && entry.message != null;
        ui.text(TITLE, ui.truncate(TITLE, entry.title, width - 45.0F), 35.0F, subtitle ? 11.5F : 17.0F,
                Theme.hudText(accent, scheme.onSurface), UI.ALIGN_LEFT);
        if (subtitle) ui.text(SUBTITLE, ui.truncate(SUBTITLE, entry.message, width - 45.0F),
                35.0F, 21.5F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
        if (module.progress() && entry.hold > 0.0F) {
            float progress = Material.clamp(entry.remaining / entry.hold, 0.0F, 1.0F);
            ui.rounded(10.0F, 29.0F, (width - 20.0F) * progress, 2.0F, 1.0F, Material.withAlpha(accent, 0.85F));
        }
        ui.pop(); ui.alphaPop();
    }

    private void layout(NotificationsModule module, ScaledResolution resolution) {
        x = MARGIN + Math.max(0.0F, resolution.getScaledWidth() - w - 20.0F) * Material.clamp(module.positionX(), 0, 100) / 100.0F;
        y = MARGIN + Math.max(0.0F, resolution.getScaledHeight() - h - 20.0F) * Material.clamp(module.positionY(), 0, 100) / 100.0F;
    }
    private void handleDrag(NotificationsModule module, Minecraft mc, ScaledResolution resolution, boolean chat) {
        if (!chat || !Mouse.isButtonDown(0)) { dragging = false; return; }
        float mx = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float my = resolution.getScaledHeight() - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
        if (dragging) dragTo(module, mx - offsetX, my - offsetY);
        else if (inside(mx, my)) { dragging = true; offsetX = mx - x; offsetY = my - y; }
    }
    public void dragTo(NotificationsModule module, float desiredX, float desiredY) {
        ScaledResolution resolution = new ScaledResolution(Minecraft.getMinecraft());
        float maxX = Math.max(0, resolution.getScaledWidth() - w - 20);
        float maxY = Math.max(0, resolution.getScaledHeight() - h - 20);
        module.setPosition(maxX > 0 ? Material.clamp((desiredX - MARGIN) / maxX * 100, 0, 100) : 0,
                maxY > 0 ? Material.clamp((desiredY - MARGIN) / maxY * 100, 0, 100) : 0);
        layout(module, resolution);
    }
    public boolean inside(float mx, float my) { return w > 0 && h > 0 && mx >= x && mx < x + w && my >= y && my < y + h; }
    public float left() { return x; }
    public float top() { return y; }
    public float width() { return w; }
    public float height() { return h; }
    public void suspend() { lastFrame = 0L; dragging = false; }
    public void reset() { entries.clear(); w = h = 0; suspend(); }

    private static final class Entry {
        final String title, message;
        final NotificationLevel level;
        final Integer accent;
        final float hold;
        float remaining;
        final Anim show = new Anim(0.0F);
        Entry(String title, String message, NotificationLevel level, Integer accent, float hold) {
            this.title = title; this.message = message; this.level = level; this.accent = accent;
            this.hold = this.remaining = hold;
        }
    }
}
