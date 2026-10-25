package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.InfoSuffix;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.render.ArrayListModule;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Material 3 Expressive module list, ported from OpenOnyx's {@code StyleModule} ("Array List").
 *
 * <p>Onyx drew one compact row per enabled module: an accent bar on the outer edge of the list, a
 * right-aligned name with the module's mode as a trailing suffix, and four ways of settling the
 * rows in (slide, fade, slide + fade, scale). The row colours could follow the theme, a static
 * colour, a two-colour gradient, a pulse, a rainbow or the module's category. This port keeps the
 * layout, all three row styles (accent bar / pills / text), the four animations, all six colour
 * modes and the category filter.</p>
 *
 * <p>Added relative to Onyx: the same scale slider, percentage position + "Edit position" screen
 * and chat dragging the other ported cards have, and the frosted-glass backdrop is now a toggle
 * that uses the shared {@link Blur} pipeline (Onyx called it "Blur" and required the ClickGUI's
 * frosted glass; the material here matches the keybind and scoreboard cards).</p>
 */
public final class ArrayListHud {

    public static final int STYLE_ACCENT_BAR = 0;
    public static final int STYLE_PILLS = 1;
    public static final int STYLE_TEXT = 2;

    private static final float ROW_H = 16.0F;
    private static final float BAR = 2.0F;
    private static final float PAD = 6.0F;
    private static final float PILL_RADIUS = 8.0F;
    private static final float BLOCK_RADIUS = 6.0F;
    /** "Rows" backdrop: the same frosted surface the other HUD cards use, but hugging each row. */
    private static final float STRIPE_PAD = 3.0F;
    private static final float MAX_NAME = 140.0F;
    private static final float SUFFIX_GAP = 4.0F;
    private static final float BLUR_COVERAGE = 0.55F;
    private static final float SLIDE = 14.0F;
    private static final float ENTER_MS = 250.0F;
    private static final float MOVE_MS = 100.0F;
    /** Soft drop shadow for text drawn straight over the world (no background behind it). */
    private static final int TEXT_SHADOW = 0x73000000;

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim cardWidth = new Anim(60.0F);
    private final Anim cardHeight = new Anim(ROW_H);
    private final Map<Module, Row> rows = new IdentityHashMap<Module, Row>();
    private final List<Module> order = new ArrayList<Module>();

    private long lastFrame;
    private float x;
    private float y;
    private float w = 60.0F;
    private float h = ROW_H;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    /** Draws (and updates) the list; {@code preview} keeps it visible for the position editor. */
    public void render(ArrayListModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        Minecraft mc = Minecraft.getMinecraft();
        boolean chatOpen = mc.currentScreen instanceof GuiChat;
        refresh(module, delta);

        boolean active = (preview || module.isEnabled()) && !order.isEmpty();
        visible.to(active ? 1.0F : 0.0F, active ? 260.0F : 180.0F,
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
        int style = module.style();
        float rowH = ROW_H + module.rowSpacing();
        boolean rowStripes = style == STYLE_ACCENT_BAR && module.background() && module.backgroundStyle() == 1;
        float inset;
        if (style == STYLE_PILLS) {
            inset = PAD;
        } else if (rowStripes) {
            // The stripe layout hugs the text: only the bar plus a couple of pixels of padding.
            inset = BAR + STRIPE_PAD;
        } else {
            inset = style == STYLE_ACCENT_BAR ? BAR + PAD : PAD;
        }

        float textMax = 0.0F;
        for (Module entry : order) {
            textMax = Math.max(textMax, rows.get(entry).textWidth);
        }
        float targetWidth = textMax + inset * 2.0F;
        float targetHeight = order.size() * rowH - module.rowSpacing();
        cardWidth.to(targetWidth, 300.0F, Ease.EMPHASIZED_DECELERATE);
        cardHeight.to(targetHeight, 300.0F, Ease.EMPHASIZED_DECELERATE);
        cardWidth.update(delta);
        cardHeight.update(delta);

        float base = module.scale();
        float scale = base * (0.94F + 0.06F * fade);
        float layoutWidth = cardWidth.value();
        float layoutHeight = Math.max(cardHeight.value(), ROW_H);
        this.w = layoutWidth * base;
        this.h = layoutHeight * base;

        // 0% and 100% sit exactly on the screen edges: the list is the one HUD element users line up
        // against the border, and a hard margin made "flush right" impossible to reach (the old
        // MARGIN = 10 / EDGE = 20 pair left a 10-unit gap at both ends that read as ~1 cm).
        float maxX = Math.max(0.0F, resolution.getScaledWidth() - w);
        float maxY = Math.max(0.0F, resolution.getScaledHeight() - h);
        this.x = maxX * (Material.clamp(module.positionX(), 0.0F, 100.0F) / 100.0F);
        this.y = maxY * (Material.clamp(module.positionY(), 0.0F, 100.0F) / 100.0F);

        if (!preview) {
            handleDrag(module, mc, resolution, chatOpen);
        }

        boolean rightAnchored = module.positionX() > 50.0F;
        boolean frost = module.background() && module.blur();
        ui.begin();
        try {
            if (!preview) {
                GlStateManager.pushMatrix();
                mc.entityRenderer.setupOverlayRendering();
            }
            if (frost && fade > 0.01F) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.push();
            ui.translate(x, y);
            ui.scale(scale, 0.0F, 0.0F);

            drawList(module, scheme, style, inset, layoutWidth, layoutHeight, rightAnchored, frost);

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

    private void refresh(ArrayListModule module, float delta) {
        order.clear();
        float speed = Math.max(0.25F, module.animationSpeed());
        for (Module candidate : ModuleManager.modules) {
            if (!module.includes(candidate) || !candidate.isEnabled()) {
                continue;
            }
            Row row = rows.get(candidate);
            if (row == null) {
                row = new Row(candidate);
                rows.put(candidate, row);
            }
            String base = candidate.getName();
            if (base == null) {
                continue;
            }
            row.name = ui.truncate(Typeface.HEADLINE, base, MAX_NAME);
            String suffix = module.suffix() ? suffix(candidate) : null;
            if (suffix != null && module.lowercase()) {
                suffix = suffix.toLowerCase(Locale.ROOT);
            }
            row.suffix = suffix == null || suffix.isEmpty() ? null : suffix;
            row.textWidth = Typeface.HEADLINE.width(row.name)
                    + (row.suffix == null ? 0.0F : SUFFIX_GAP + Typeface.BODY.width(row.suffix));
            row.visible.to(1.0F, ENTER_MS / speed, Ease.EMPHASIZED_DECELERATE);
            order.add(candidate);
        }

        for (Module candidate : new ArrayList<Module>(rows.keySet())) {
            if (!order.contains(candidate)) {
                rows.remove(candidate);
            }
        }

        sort(module);
        float rowH = ROW_H + module.rowSpacing();
        for (int i = 0; i < order.size(); i++) {
            Row row = rows.get(order.get(i));
            row.offset.to(i * rowH, MOVE_MS / speed, Ease.DECELERATE);
        }
        for (Row row : rows.values()) {
            row.visible.update(delta);
            row.offset.update(delta);
        }
    }

    private void sort(ArrayListModule module) {
        int mode = module.sortMode();
        if (mode == 1) {
            order.sort((a, b) -> Float.compare(rows.get(a).textWidth, rows.get(b).textWidth));
        } else if (mode == 2) {
            order.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } else {
            order.sort((a, b) -> Float.compare(rows.get(b).textWidth, rows.get(a).textWidth));
        }
    }

    /** The module's own mode string, falling back to its first mode setting like Onyx did. */
    private static String suffix(Module module) {
        String info = InfoSuffix.get(module);
        if (info != null && !info.isEmpty()) {
            return info;
        }
        for (Setting setting : module.getSettings()) {
            if (setting instanceof SliderSetting && ((SliderSetting) setting).isMode()) {
                return ((SliderSetting) setting).getDisplay(false);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private void drawList(ArrayListModule module, Scheme scheme, int style, float inset,
                          float layoutWidth, float layoutHeight, boolean rightAnchored,
                          boolean frost) {
        boolean background = module.background() && style != STYLE_TEXT;
        boolean blurred = frost && Blur.ready();
        // "Rows" gives every row its own block that hugs its own text, so short rows no longer sit
        // on a slab that is as wide as the longest module name.
        boolean rowBlocks = background && style == STYLE_ACCENT_BAR && module.backgroundStyle() == 1;

        if (background && style == STYLE_ACCENT_BAR && !rowBlocks) {
            if (blurred) {
                // An opaque tint plus a coverage, the same recipe the HUD cards use: a translucent
                // tint would let the sharp world through and read as no blur at all.
                ui.frost(0.0F, 0.0F, layoutWidth, layoutHeight, BLOCK_RADIUS,
                        Material.withAlpha(scheme.surface, 1.0F), BLUR_COVERAGE);
            } else {
                ui.rounded(0.0F, 0.0F, layoutWidth, layoutHeight, BLOCK_RADIUS,
                        Material.withAlpha(scheme.surface, 0.55F));
            }
        }

        boolean shadowText = module.textShadow() && !background;
        int count = order.size();
        for (int i = 0; i < count; i++) {
            Row row = rows.get(order.get(i));
            float alpha = Material.clamp(row.visible.value(), 0.0F, 1.0F);
            if (alpha <= 0.004F) {
                continue;
            }
            float top = row.offset.value();
            float rowWidth = row.textWidth + inset * 2.0F;
            float left = rightAnchored ? layoutWidth - rowWidth : 0.0F;
            int accent = accentColor(module, scheme, row, i, count);
            int nameColor = style == STYLE_ACCENT_BAR ? scheme.onSurface : accent;
            int suffixColor = Material.withAlpha(style == STYLE_TEXT ? accent : scheme.onSurface, 0.6F);
            float centerY = top + ROW_H / 2.0F;

            ui.alphaPush(alpha);
            ui.push();
            int animation = module.animation();
            if (animation == 0 || animation == 2) {
                ui.translate(rightAnchored ? (1.0F - alpha) * SLIDE : -(1.0F - alpha) * SLIDE, 0.0F);
            }
            if (animation == 3) {
                float pivotX = rightAnchored ? layoutWidth : 0.0F;
                ui.scale(0.9F + 0.1F * alpha, pivotX, centerY);
            }

            if (background && style == STYLE_PILLS) {
                float pillY = top;
                float pillH = ROW_H;
                if (blurred) {
                    ui.frost(left, pillY, rowWidth, pillH, PILL_RADIUS,
                            Material.withAlpha(scheme.surface, 1.0F), BLUR_COVERAGE);
                } else {
                    ui.rounded(left, pillY, rowWidth, pillH, PILL_RADIUS,
                            Material.withAlpha(scheme.surface, 0.72F));
                }
                ui.rounded(left, pillY, rowWidth, pillH, PILL_RADIUS, Material.withAlpha(accent, 0.22F));
                if (module.outline()) {
                    ui.outline(left, pillY, rowWidth, pillH, PILL_RADIUS, 1.0F,
                            Material.withAlpha(accent, 0.5F));
                }
            }

            if (background && style == STYLE_ACCENT_BAR && rowBlocks) {
                // Same frosted recipe as the other HUD cards, drawn per row so it hugs this row's
                // text. Rows stay flush with each other: only the outer corners of the column are
                // rounded, so the list reads as one continuous frosted shape, not stacked pills.
                float tl = i == 0 ? BLOCK_RADIUS : 0.0F;
                float tr = i == 0 ? BLOCK_RADIUS : 0.0F;
                float br = i == count - 1 ? BLOCK_RADIUS : 0.0F;
                float bl = i == count - 1 ? BLOCK_RADIUS : 0.0F;
                if (blurred) {
                    ui.frostCorners(left, top, rowWidth, ROW_H, tl, tr, br, bl,
                            Material.withAlpha(scheme.surface, 1.0F), BLUR_COVERAGE);
                } else {
                    ui.roundedCorners(left, top, rowWidth, ROW_H, tl, tr, br, bl,
                            Material.withAlpha(scheme.surface, 0.55F));
                }
            }

            if (background && style == STYLE_ACCENT_BAR) {
                // A plain rect, not a rounded one: consecutive rows must join into one continuous
                // stripe, and a per-row pill would leave a wedge notch at every row boundary. The
                // first and last bars are inset so their square ends stay inside the block's
                // rounded corners (the stripe variant runs edge to edge like Onyx's did).
                float barX = rightAnchored ? layoutWidth - BAR : 0.0F;
                float topInset = rowBlocks ? 0.0F : (i == 0 ? 3.0F : 0.0F);
                float bottomInset = rowBlocks ? 0.0F : (i == count - 1 ? 3.0F : 0.0F);
                ui.rect(barX, top + topInset, BAR, ROW_H - topInset - bottomInset, accent);
            }

            float textRight = layoutWidth - inset;
            float textLeft = inset;
            if (rightAnchored) {
                float cursor = textRight;
                if (row.suffix != null) {
                    if (shadowText) {
                        ui.text(Typeface.BODY, row.suffix, cursor + 1.0F, centerY + 1.0F, shadow(), UI.ALIGN_RIGHT);
                    }
                    ui.text(Typeface.BODY, row.suffix, cursor, centerY, suffixColor, UI.ALIGN_RIGHT);
                    cursor -= Typeface.BODY.width(row.suffix) + SUFFIX_GAP;
                }
                if (shadowText) {
                    ui.text(Typeface.HEADLINE, row.name, cursor + 1.0F, centerY + 1.0F, shadow(), UI.ALIGN_RIGHT);
                }
                ui.text(Typeface.HEADLINE, row.name, cursor, centerY, nameColor, UI.ALIGN_RIGHT);
            } else {
                float cursor = textLeft;
                if (shadowText) {
                    ui.text(Typeface.HEADLINE, row.name, cursor + 1.0F, centerY + 1.0F, shadow(), UI.ALIGN_LEFT);
                }
                ui.text(Typeface.HEADLINE, row.name, cursor, centerY, nameColor, UI.ALIGN_LEFT);
                cursor += Typeface.HEADLINE.width(row.name) + SUFFIX_GAP;
                if (row.suffix != null) {
                    if (shadowText) {
                        ui.text(Typeface.BODY, row.suffix, cursor + 1.0F, centerY + 1.0F, shadow(), UI.ALIGN_LEFT);
                    }
                    ui.text(Typeface.BODY, row.suffix, cursor, centerY, suffixColor, UI.ALIGN_LEFT);
                }
            }

            ui.pop();
            ui.alphaPop();
        }
    }

    private int accentColor(ArrayListModule module, Scheme scheme, Row row, int index, int count) {
        switch (module.colors()) {
            case 1:
                return module.color().color();
            case 2: {
                float phase = (float) (Math.sin(System.nanoTime() / 1.0E9D * module.colorSpeed()
                        + Math.toRadians(index * module.colorSpread())) * 0.5D + 0.5D);
                return mix(module.firstColor().color(), module.secondColor().color(), phase);
            }
            case 3: {
                float t = count <= 1 ? 0.0F : index / (float) (count - 1);
                return mix(module.firstColor().color(), module.secondColor().color(), t);
            }
            case 4: {
                float hue = (float) ((System.nanoTime() / 1.0E9D * 60.0D * module.colorSpeed()
                        + index * module.colorSpread()) % 360.0D);
                return java.awt.Color.HSBtoRGB((hue < 0.0F ? hue + 360.0F : hue) / 360.0F, 0.75F, 1.0F);
            }
            case 5: {
                float hue = (row.module.moduleCategory().ordinal() * 67.0F) % 360.0F;
                return java.awt.Color.HSBtoRGB(hue / 360.0F, 0.7F, 1.0F);
            }
            default:
                return scheme.primary;
        }
    }

    private static int shadow() {
        return TEXT_SHADOW;
    }

    private static int mix(int from, int to, float t) {
        t = Material.clamp(t, 0.0F, 1.0F);
        return Material.argb(255,
                Math.round(Material.red(from) + (Material.red(to) - Material.red(from)) * t),
                Math.round(Material.green(from) + (Material.green(to) - Material.green(from)) * t),
                Math.round(Material.blue(from) + (Material.blue(to) - Material.blue(from)) * t));
    }

    // ------------------------------------------------------------------ //
    // Position and dragging
    // ------------------------------------------------------------------ //

    private void handleDrag(ArrayListModule module, Minecraft mc, ScaledResolution resolution,
                            boolean chatOpen) {
        if (!chatOpen || !Mouse.isButtonDown(0)) {
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

    public void dragTo(ArrayListModule module, float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        // Same range the renderer uses, so a drag can reach the screen edge and stays there.
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h);
        float percentX = Material.clamp(desiredX / maxX * 100.0F, 0.0F, 100.0F);
        float percentY = Material.clamp(desiredY / maxY * 100.0F, 0.0F, 100.0F);
        module.setPosition(percentX, percentY);
        // Snap the hit box to the clamped spot too, so the list stops at the edge instead of
        // overshooting it for a frame while the cursor keeps moving.
        this.x = maxX * (percentX / 100.0F);
        this.y = maxY * (percentY / 100.0F);
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
        rows.clear();
        order.clear();
        visible.set(0.0F);
        cardWidth.set(60.0F);
        cardHeight.set(ROW_H);
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

    /** One list row: the drawn text plus the two animations that place and reveal it. */
    private static final class Row {
        private final Module module;
        private final Anim visible = new Anim(0.0F);
        private final Anim offset = new Anim(0.0F);
        private String name = "";
        private String suffix;
        private float textWidth;

        private Row(Module module) {
            this.module = module;
        }
    }
}
