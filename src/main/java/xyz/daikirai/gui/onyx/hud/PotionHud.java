package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.PotionHudModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Material 3 Expressive potion list, ported from OpenOnyx's {@code PotionHUDModule}.
 *
 * <p>Onyx drew a rounded card with a "Potions" header, a divider and one 24px row per active
 * effect: an optional potion-tinted chip holding the vanilla status icon, the localised name with
 * its roman level, and the remaining time right-aligned (red once it is about to run out). This
 * port keeps that layout and adds what the other ported cards have — the shared frosted-glass
 * backdrop, a scale setting and percentage position with an "Edit position" screen.</p>
 *
 * <p>Rows animate in on their own (alpha plus a short slide), the card's width and height follow
 * the content with the emphasized-decelerate curve, and the whole card fades with the same
 * entrance recipe as the scoreboard and target cards. In game the list is usually empty, so the
 * card hides itself; the position editor previews three sample effects, and with the chat open an
 * empty list shows a placeholder row so the card can still be grabbed there.</p>
 */
public final class PotionHud {

    /** Vanilla effect icons live in a 8x3 grid at the bottom of the container texture. */
    private static final ResourceLocation INVENTORY =
            new ResourceLocation("textures/gui/container/inventory.png");

    private static final float PAD = 6.0F;
    private static final float HEADER = 31.0F;
    private static final float DIVIDER_Y = 25.0F;
    private static final float ROW_H = 24.0F;
    private static final float ICON = 18.0F;
    private static final float ICON_X = 6.0F;
    private static final float TEXT_X_ICON = 30.0F;
    private static final float TEXT_X = 8.0F;
    private static final float DURATION_RIGHT = 8.0F;
    private static final float BOTTOM_PAD = 6.0F;
    private static final float RADIUS = 14.0F;
    private static final float MIN_WIDTH = 96.0F;
    private static final float MAX_WIDTH = 240.0F;
    private static final float NAME_MAX = 110.0F;
    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    private static final float BLUR_COVERAGE = 0.55F;
    private static final float ROW_ENTER_MS = 150.0F;
    private static final float ROW_LEAVE_MS = 100.0F;
    private static final int LOW_TIME_TICKS = 200;

    private static final String[] ROMAN = {
            "", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private static final String TITLE = "Potions";
    private static final String HEADER_ICON = "\uea4b";
    private static final String NO_EFFECTS = "No effects";

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim cardWidth = new Anim(MIN_WIDTH);
    private final Anim cardHeight = new Anim(60.0F);
    private final Map<Integer, Anim> rowAnims = new HashMap<Integer, Anim>();

    private long lastFrame;
    private float x;
    private float y;
    private float w = MIN_WIDTH;
    private float h = 60.0F;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    /** Draws (and updates) the card; {@code preview} keeps sample rows for the position editor. */
    public void render(PotionHudModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        Minecraft mc = Minecraft.getMinecraft();
        boolean chatOpen = mc.currentScreen instanceof GuiChat;
        List<Row> rows = collect(module, preview, chatOpen);
        animateRows(rows, delta);

        boolean active = (preview || module.isEnabled()) && !rows.isEmpty();
        visible.to(active ? 1.0F : 0.0F, active ? 240.0F : 180.0F,
                active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.002F) {
            if (!preview) {
                return;
            }
            fade = 1.0F;
        }

        Scheme scheme = Theme.scheme();
        ScaledResolution resolution = new ScaledResolution(mc);
        float targetWidth = contentWidth(module, rows);
        float targetHeight = HEADER + rows.size() * ROW_H + BOTTOM_PAD;
        cardWidth.to(targetWidth, 240.0F, Ease.EMPHASIZED_DECELERATE);
        cardHeight.to(targetHeight, 240.0F, Ease.EMPHASIZED_DECELERATE);
        cardWidth.update(delta);
        cardHeight.update(delta);

        float base = module.scale();
        float scale = base * (0.94F + 0.06F * fade);
        float layoutWidth = cardWidth.value();
        float layoutHeight = cardHeight.value();
        this.w = layoutWidth * base;
        this.h = layoutHeight * base;

        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h - EDGE);
        this.x = MARGIN + maxX * (Material.clamp(module.positionX(), 0.0F, 100.0F) / 100.0F);
        this.y = MARGIN + maxY * (Material.clamp(module.positionY(), 0.0F, 100.0F) / 100.0F);

        if (!preview) {
            handleDrag(module, mc, resolution);
        }

        ui.begin();
        try {
            if (!preview) {
                GlStateManager.pushMatrix();
                mc.entityRenderer.setupOverlayRendering();
            }
            if (module.frosted() && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.push();
            ui.translate(x + w / 2.0F, y + h / 2.0F);
            ui.scale(scale, 0.0F, 0.0F);
            ui.translate(-layoutWidth / 2.0F, -layoutHeight / 2.0F);

            drawCard(module, scheme, rows, layoutWidth, layoutHeight);

            ui.pop();
            ui.alphaPop();
        } finally {
            if (!preview) {
                GlStateManager.popMatrix();
            }
            ui.end();
            restoreHudState();
        }
    }

    // ------------------------------------------------------------------ //
    // Content
    // ------------------------------------------------------------------ //

    private List<Row> collect(PotionHudModule module, boolean preview, boolean chatOpen) {
        List<Row> rows = new ArrayList<Row>();
        if (preview) {
            rows.add(sample(module, Potion.moveSpeed, 1, "1:23", false));
            rows.add(sample(module, Potion.fireResistance, 0, "4:56", false));
            rows.add(sample(module, Potion.regeneration, 0, "0:08", true));
            return rows;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) {
            return rows;
        }
        for (PotionEffect effect : new ArrayList<PotionEffect>(mc.thePlayer.getActivePotionEffects())) {
            if (effect == null) {
                continue;
            }
            if (module.hideAmbient() && effect.getIsAmbient()) {
                continue;
            }
            Potion potion = effect.getPotionID() >= 0 && effect.getPotionID() < Potion.potionTypes.length
                    ? Potion.potionTypes[effect.getPotionID()] : null;
            if (potion == null) {
                continue;
            }
            String name = name(module, potion, effect.getAmplifier());
            rows.add(new Row(potion.getId(), name, duration(effect),
                    potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1,
                    potion.getLiquidColor(), low(effect), ticks(effect), effect.getAmplifier()));
        }
        sort(module, rows);
        if (rows.isEmpty() && chatOpen) {
            // Onyx showed a placeholder row so the card could still be grabbed with the chat open.
            rows.add(new Row(-1, NO_EFFECTS, null, -1, Theme.scheme().primary, false,
                    Integer.MAX_VALUE, 0));
        }
        return rows;
    }

    private static Row sample(PotionHudModule module, Potion potion, int amplifier,
                              String duration, boolean low) {
        String name = I18n.format(potion.getName());
        if (module.showAmplifier() && amplifier > 0) {
            name = name + " " + ROMAN[Math.min(amplifier, ROMAN.length - 1)];
        }
        return new Row(potion.getId(), name, duration,
                potion.hasStatusIcon() ? potion.getStatusIconIndex() : -1,
                potion.getLiquidColor(), low, Integer.MAX_VALUE, amplifier);
    }

    private static String name(PotionHudModule module, Potion potion, int amplifier) {
        String base = I18n.format(potion.getName());
        if (!module.showAmplifier() || amplifier <= 0) {
            return base;
        }
        return base + " " + ROMAN[Math.min(amplifier, ROMAN.length - 1)];
    }

    private static String duration(PotionEffect effect) {
        return effect.getIsPotionDurationMax() ? "**:**" : Potion.getDurationString(effect);
    }

    private static int ticks(PotionEffect effect) {
        return effect.getIsPotionDurationMax() ? Integer.MAX_VALUE : effect.getDuration();
    }

    private static boolean low(PotionEffect effect) {
        return !effect.getIsPotionDurationMax() && effect.getDuration() < LOW_TIME_TICKS;
    }

    private static void sort(PotionHudModule module, List<Row> rows) {
        int mode = module.sortMode();
        if (mode == 1) {
            rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        } else if (mode == 2) {
            rows.sort((a, b) -> {
                int level = Integer.compare(b.level, a.level);
                return level != 0 ? level : a.name.compareToIgnoreCase(b.name);
            });
        } else {
            rows.sort((a, b) -> Integer.compare(a.ticks, b.ticks));
        }
    }

    private static float contentWidth(PotionHudModule module, List<Row> rows) {
        float width = 29.0F + Typeface.HEADLINE.width(TITLE);
        for (Row row : rows) {
            float nameWidth = Math.min(Typeface.BODY_MEDIUM.width(row.name), NAME_MAX);
            float rowWidth = 18.0F + nameWidth;
            if (module.showIcons()) {
                rowWidth += 18.0F;
            }
            if (module.showDuration() && row.durationText != null) {
                rowWidth += 10.0F + Typeface.CAPTION.width(row.durationText);
            }
            width = Math.max(width, rowWidth);
        }
        return Material.clamp(width, MIN_WIDTH, MAX_WIDTH);
    }

    // ------------------------------------------------------------------ //
    // Per-row animation
    // ------------------------------------------------------------------ //

    private void animateRows(List<Row> rows, float delta) {
        for (Row row : rows) {
            Anim anim = rowAnims.get(row.id);
            if (anim == null) {
                anim = new Anim(0.0F);
                rowAnims.put(row.id, anim);
            }
            anim.to(1.0F, ROW_ENTER_MS, Ease.DECELERATE);
        }
        for (Iterator<Map.Entry<Integer, Anim>> it = rowAnims.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Anim> entry = it.next();
            if (!contains(rows, entry.getKey())) {
                entry.getValue().to(0.0F, ROW_LEAVE_MS, Ease.DECELERATE);
            }
            Anim anim = entry.getValue();
            anim.update(delta);
            if (anim.target() <= 0.0F && anim.value() <= 0.002F) {
                it.remove();
            }
        }
    }

    private static boolean contains(List<Row> rows, int id) {
        for (Row row : rows) {
            if (row.id == id) {
                return true;
            }
        }
        return false;
    }

    private static float rowAlpha(List<Row> rows, Map<Integer, Anim> anims, Row row) {
        Anim anim = anims.get(row.id);
        return anim == null ? 1.0F : Material.clamp(anim.value(), 0.0F, 1.0F);
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private void drawCard(PotionHudModule module, Scheme scheme, List<Row> rows, float width, float height) {
        int surface = Material.withAlpha(scheme.surfaceContainerHigh, 1.0F);
        ui.shadow(0.0F, 0.0F, width, height, RADIUS, 3);
        ui.rounded(0.0F, 0.0F, width, height, RADIUS, surface);
        if (module.frosted() && Blur.ready()) {
            ui.frost(0.0F, 0.0F, width, height, RADIUS, scheme.surfaceContainerHigh, BLUR_COVERAGE);
        }

        ui.scissorPush(0.0F, 0.0F, width, height);
        ui.icon(HEADER_ICON, 12.0F, 15.0F, 12.0F, scheme.primary);
        ui.text(Typeface.HEADLINE, TITLE, 23.0F, 15.0F, scheme.onSurface, UI.ALIGN_LEFT);
        ui.rect(PAD, DIVIDER_Y, Math.max(0.0F, width - PAD * 2.0F), 1.0F,
                Material.withAlpha(scheme.outlineVariant, 0.6F));

        boolean icons = module.showIcons();
        boolean duration = module.showDuration();
        boolean tinted = module.potionColor();
        boolean warn = module.lowTimeWarning();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float alpha = rowAlpha(rows, rowAnims, row);
            if (alpha <= 0.004F) {
                continue;
            }
            float top = HEADER + i * ROW_H;
            float centerY = top + ROW_H / 2.0F;
            float iconY = top + (ROW_H - ICON) / 2.0F;

            ui.alphaPush(alpha);
            ui.push();
            ui.translate((1.0F - alpha) * 4.0F, 0.0F);

            if (icons) {
                if (tinted) {
                    ui.rounded(ICON_X, iconY, ICON, ICON, 4.0F,
                            Material.withAlpha(opaque(row.color), 0.22F));
                }
                if (row.icon >= 0) {
                    drawIcon(row.icon, iconY);
                }
            }

            int nameColor = tinted ? lift(row.color, scheme.onSurface) : scheme.onSurface;
            ui.text(Typeface.BODY_MEDIUM, ui.truncate(Typeface.BODY_MEDIUM, row.name, NAME_MAX),
                    icons ? TEXT_X_ICON : TEXT_X, centerY, nameColor, UI.ALIGN_LEFT);
            if (duration && row.durationText != null) {
                ui.text(Typeface.CAPTION, row.durationText, width - DURATION_RIGHT, centerY,
                        row.low && warn ? scheme.error : scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
            }

            ui.pop();
            ui.alphaPop();
        }
        ui.scissorPop();
    }

    /** Draws one vanilla effect icon out of the container texture (8 icons per row). */
    private void drawIcon(int index, float iconY) {
        float u = (index % 8) * ICON / 256.0F;
        float v = (198.0F + (index / 8) * ICON) / 256.0F;
        float size = ICON / 256.0F;
        ui.image(INVENTORY, ICON_X, iconY, ICON, ICON, u, v, u + size, v + size, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------------ //
    // Position and dragging
    // ------------------------------------------------------------------ //

    private void handleDrag(PotionHudModule module, Minecraft mc, ScaledResolution resolution) {
        boolean chat = mc.currentScreen instanceof GuiChat;
        if (!chat || !Mouse.isButtonDown(0)) {
            dragging = false;
            return;
        }
        float mouseX = Mouse.getX() * resolution.getScaledWidth() / (float) mc.displayWidth;
        float mouseY = resolution.getScaledHeight()
                - Mouse.getY() * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
        if (dragging) {
            dragTo(module, mouseX - dragOffsetX, mouseY - dragOffsetY);
        } else if (inside(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
    }

    public void dragTo(PotionHudModule module, float desiredX, float desiredY) {
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
        rowAnims.clear();
        visible.set(0.0F);
        cardWidth.set(MIN_WIDTH);
        cardHeight.set(60.0F);
        dragging = false;
        lastFrame = 0L;
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

    private static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /**
     * Lifts a potion's liquid colour towards the surface text role. The role colours are meant for
     * white text on a bright background; strength, slowness and night vision are dark enough that
     * their own colour would disappear into the card. The lift grows the darker the colour is, so
     * vivid potions (speed, fire resistance, regeneration) keep their own hue.
     */
    private static int lift(int rgb, int onSurface) {
        int from = opaque(rgb);
        int luminance = (299 * Material.red(from) + 587 * Material.green(from)
                + 114 * Material.blue(from)) / 1000;
        float t = luminance < 70 ? 0.55F : (luminance < 130 ? 0.38F : 0.20F);
        return mixSrgb(from, onSurface, t);
    }

    private static int mixSrgb(int from, int to, float t) {
        return Material.argb(255,
                Math.round(Material.red(from) + (Material.red(to) - Material.red(from)) * t),
                Math.round(Material.green(from) + (Material.green(to) - Material.green(from)) * t),
                Math.round(Material.blue(from) + (Material.blue(to) - Material.blue(from)) * t));
    }

    /** One effect row: what to draw plus the keys used for sorting and the enter animation. */
    private static final class Row {
        private final int id;
        private final String name;
        private final String durationText;
        private final int icon;
        private final int color;
        private final boolean low;
        private final int ticks;
        private final int level;

        private Row(int id, String name, String durationText, int icon, int color, boolean low,
                    int ticks, int level) {
            this.id = id;
            this.name = name;
            this.durationText = durationText;
            this.icon = icon;
            this.color = color;
            this.low = low;
            this.ticks = ticks;
            this.level = level;
        }
    }
}
