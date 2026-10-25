package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.BindComponent;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.render.KeybindsModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Material 3 Expressive keybind list, the HUD element behind the Keybinds module.
 *
 * <p>Only modules that actually own a key are listed, so the card never shows a dead "unbound"
 * row. Each row is a pill surface with a physical-looking keycap and an ON/OFF state chip:
 * toggling a module morphs the row from <em>surfaceContainer</em> to <em>secondaryContainer</em>,
 * lights the keycap up to <em>primary</em>, cross-fades the chip to <em>tertiaryContainer</em>
 * and fires a short <em>primary</em> ring plus a springy keycap pop. That colour change is the
 * "this module just changed state" hint; nothing else on screen has to move.</p>
 *
 * <p>The geometry keeps the same interaction contract as the watermark: the card sits at a
 * percentage of the screen, can be dragged while chat is open, and is also exposed through a
 * dedicated position editor. Every animated size is driven by an {@link Anim}, and the one
 * physical bounce (the keycap) is a {@link Spring}.</p>
 */
public final class Keybinds {

    // ------------------------------------------------------------------ //
    // Geometry (Material 3 Expressive spacing: 4dp grid, pill rows)
    // ------------------------------------------------------------------ //

    private static final float PAD = 10.0F;
    private static final float HEADER = 30.0F;
    private static final float HEADER_ICON = 26.0F;
    private static final float HEADER_ICON_RADIUS = 9.0F;
    private static final float HEADER_TITLE_GAP = 8.0F;
    private static final float ROW_HEIGHT = 32.0F;
    private static final float ROW_GAP = 6.0F;
    private static final float ROW_PAD = 5.0F;
    private static final float CARD_RADIUS = 26.0F;
    private static final float KEYCAP_HEIGHT = 24.0F;
    private static final float KEYCAP_RADIUS = 9.0F;
    private static final float KEYCAP_MIN_WIDTH = 26.0F;
    private static final float KEYCAP_MAX_WIDTH = 66.0F;
    private static final float KEYCAP_TEXT_PAD = 12.0F;
    private static final float KEYCAP_NAME_GAP = 10.0F;
    private static final float NAME_MAX_WIDTH = 110.0F;
    private static final float CHIP_WIDTH = 30.0F;
    private static final float CHIP_MORPH = 4.0F;
    private static final float CHIP_HEIGHT = 16.0F;
    private static final float HEADER_COUNT_HEIGHT = 18.0F;
    private static final float MARGIN = 10.0F;
    private static final float EDGE = 20.0F;
    private static final float MIN_CARD_WIDTH = 132.0F;

    private static final String KEYBOARD_ICON = "\ue312";
    private static final float ROW_STAGGER = 35.0F;
    /** How much of the surface tint covers the blurred world (the ClickGUI panels use 0.88). */
    private static final float BLUR_COVERAGE = 0.55F;

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim cardWidth = new Anim(MIN_CARD_WIDTH);
    private final Anim cardHeight = new Anim(54.0F + ROW_HEIGHT);
    private final Map<Module, Entry> cache = new HashMap<Module, Entry>();
    private final List<Entry> entries = new ArrayList<Entry>();

    private long lastFrame;
    private float x;
    private float y;
    private float w;
    private float h;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    /** Draws (and updates) the card; {@code preview} keeps it visible for the position editor. */
    public void render(KeybindsModule module, boolean preview) {
        if (module == null) {
            return;
        }
        float delta = tick();
        updateEntries(module, delta);

        boolean active = (preview || module.isEnabled()) && !entries.isEmpty();
        visible.to(active ? 1.0F : 0.0F, active ? 300.0F : 200.0F, active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (fade <= 0.002F) {
            if (!preview) {
                return;
            }
            fade = 1.0F;
        }

        Scheme scheme = Theme.scheme();
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);

        float keycapWidth = keycapWidth();
        float nameWidth = nameWidth();
        float cardWidthValue = cardWidth(module, keycapWidth, nameWidth);
        float cardHeightValue = cardHeight();
        cardWidth.to(cardWidthValue, 280.0F, Ease.EMPHASIZED_DECELERATE);
        cardHeight.to(cardHeightValue, 280.0F, Ease.EMPHASIZED_DECELERATE);
        cardWidth.update(delta);
        cardHeight.update(delta);

        float base = module.scale();
        float entrance = 0.94F + 0.06F * fade;
        float scale = base * entrance;
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
                // A sibling HUD element (the watermark island, for one) may leave its translate
                // behind in the shared matrix. Re-seat the GUI baseline so the card lands where
                // the layout maths expects it, then restore the caller's matrix afterwards.
                GlStateManager.pushMatrix();
                mc.entityRenderer.setupOverlayRendering();
            }
            if (module.frosted() && fade > 0.01F) {
                // Must run before anything of the card is drawn: prepare() captures the frame,
                // and the card must not end up blurred into its own backdrop.
                Blur.prepare();
            }
            ui.alphaPush(fade);
            // Scale around the card's centre so the entrance grows outwards instead of from the
            // top-left corner (the format Material uses for container transitions).
            ui.push();
            ui.translate(x + w / 2.0F, y + h / 2.0F);
            ui.scale(scale, 0.0F, 0.0F);
            ui.translate(-layoutWidth / 2.0F, -layoutHeight / 2.0F);

            drawCard(module, scheme, layoutWidth, layoutHeight, cardWidth.target());

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
    // Card
    // ------------------------------------------------------------------ //

    private void drawCard(KeybindsModule module, Scheme scheme, float width, float height,
                          float targetWidth) {
        // Opaque backing first: the in-world framebuffer carries an alpha channel and the HUD is
        // composited through it, so every surface needs an opaque layer underneath. The frosted
        // layer is drawn on top of it with a full alpha mask, so it shows the blurred world
        // without ever writing a partial alpha into the world buffer.
        int surface = Material.withAlpha(scheme.surfaceContainerHigh, 1.0F);
        ui.shadow(0.0F, 0.0F, width, height, CARD_RADIUS, 3);
        ui.rounded(0.0F, 0.0F, width, height, CARD_RADIUS, surface);
        if (module.frosted() && Blur.ready()) {
            ui.frost(0.0F, 0.0F, width, height, CARD_RADIUS, scheme.surfaceContainerHigh, BLUR_COVERAGE);
        }
        // Borderless card: the outline is reserved for the per-row toggle flash.

        drawHeader(module, scheme, width);

        float rowsTop = PAD + HEADER + 10.0F;
        float keycapWidth = keycapWidth();
        ui.scissorPush(0.0F, rowsTop - 4.0F, width, Math.max(0.0F, height - rowsTop - PAD + 4.0F));
        float cursor = rowsTop;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float show = Material.clamp(entry.show.value(), 0.0F, 1.0F);
            if (show <= 0.002F && !entry.wanted) {
                continue;
            }
            ui.push();
            ui.alphaPush(show);
            ui.translate(PAD, cursor + (1.0F - show) * 6.0F);
            drawRow(scheme, entry, width - PAD * 2.0F, keycapWidth, surface,
                    targetWidth - PAD * 2.0F);
            ui.alphaPop();
            ui.pop();
            cursor += (ROW_HEIGHT + ROW_GAP) * show;
        }
        ui.scissorPop();
    }

    private void drawHeader(KeybindsModule module, Scheme scheme, float width) {
        float top = PAD;
        float iconY = top + (HEADER - HEADER_ICON) / 2.0F;
        ui.rounded(PAD, iconY, HEADER_ICON, HEADER_ICON, HEADER_ICON_RADIUS, scheme.primaryContainer);
        ui.icon(KEYBOARD_ICON, PAD + HEADER_ICON / 2.0F, iconY + HEADER_ICON / 2.0F, 15.0F,
                scheme.onPrimaryContainer);

        float titleX = PAD + HEADER_ICON + HEADER_TITLE_GAP;
        ui.text(Typeface.HEADLINE, "Keybinds", titleX, top + HEADER / 2.0F, scheme.onSurface,
                UI.ALIGN_LEFT);

        if (module.showCount()) {
            int enabled = 0;
            for (Entry entry : entries) {
                if (entry.wanted && entry.module.isEnabled()) {
                    enabled++;
                }
            }
            String count = enabled + "/" + entries.size();
            float countWidth = Typeface.MICRO.width(count) + 14.0F;
            float countX = width - PAD - countWidth;
            float countY = top + (HEADER - HEADER_COUNT_HEIGHT) / 2.0F;
            ui.rounded(countX, countY, countWidth, HEADER_COUNT_HEIGHT, HEADER_COUNT_HEIGHT / 2.0F,
                    scheme.secondaryContainer);
            ui.text(Typeface.MICRO, count, countX + countWidth / 2.0F,
                    countY + HEADER_COUNT_HEIGHT / 2.0F, scheme.onSecondaryContainer, UI.ALIGN_CENTER);
        }

        ui.rect(PAD, top + HEADER + 3.0F, width - PAD * 2.0F, 1.0F,
                Material.withAlpha(scheme.outlineVariant, 0.6F));
    }

    // ------------------------------------------------------------------ //
    // Rows
    // ------------------------------------------------------------------ //

    private void drawRow(Scheme scheme, Entry entry, float width, float keycapWidth, int surface,
                         float targetWidth) {
        float t = Material.clamp(entry.state.value(), 0.0F, 1.0F);
        float radius = ROW_HEIGHT / 2.0F;

        int rowOff = Material.mix(surface, scheme.surfaceContainerLow, 0.70F);
        int rowOn = Material.mix(surface, scheme.primaryContainer, 0.85F);
        int rowFill = Material.mix(rowOff, rowOn, t);
        ui.rounded(0.0F, 0.0F, width, ROW_HEIGHT, radius, rowFill);

        float flash = Material.clamp(entry.flash.value(), 0.0F, 1.0F);
        if (flash > 0.001F) {
            ui.rounded(0.0F, 0.0F, width, ROW_HEIGHT, radius,
                    Material.withAlpha(scheme.primary, 0.13F * flash));
            ui.outline(0.0F, 0.0F, width, ROW_HEIGHT, radius, 1.0F,
                    Material.withAlpha(scheme.primary, 0.55F * flash));
        }

        drawKeycap(scheme, entry, rowFill, t);
        // Lay the name out for the wider of the animated and target widths: while the card
        // shrinks (a row was removed) the text keeps its full size instead of flashing an
        // ellipsis, and while it grows the state chip covers the part that is still unused.
        drawName(scheme, entry, Math.max(width, targetWidth), keycapWidth, t);
        drawStateChip(scheme, entry, width, rowFill, t);
    }

    private void drawKeycap(Scheme scheme, Entry entry, int rowFill, float t) {
        float keycapWidth = keycapWidth();
        float keycapX = ROW_PAD;
        float keycapY = (ROW_HEIGHT - KEYCAP_HEIGHT) / 2.0F;

        float pop = Math.max(0.7F, Math.min(1.15F, entry.pop.value()));
        float pressed = Material.clamp(entry.press.value(), 0.0F, 1.0F);
        pop *= 1.0F - 0.04F * pressed;

        ui.push();
        ui.translate(keycapX + keycapWidth / 2.0F, keycapY + KEYCAP_HEIGHT / 2.0F + pressed * 0.8F);
        ui.scale(pop, 0.0F, 0.0F);
        ui.translate(-keycapWidth / 2.0F, -KEYCAP_HEIGHT / 2.0F);

        int off = Material.mix(rowFill, scheme.surfaceContainerHighest, 0.92F);
        int fill = Material.mix(off, scheme.primary, t);
        int border = Material.mix(scheme.outlineVariant, scheme.primary, t);
        int label = Material.mix(scheme.onSurfaceVariant, scheme.onPrimary, t);

        ui.rounded(0.0F, 1.5F, keycapWidth, KEYCAP_HEIGHT, KEYCAP_RADIUS,
                Material.mix(fill, scheme.shadow, 0.30F));
        ui.rounded(0.0F, 0.0F, keycapWidth, KEYCAP_HEIGHT - 1.5F, KEYCAP_RADIUS, fill);
        ui.outline(0.0F, 0.0F, keycapWidth, KEYCAP_HEIGHT - 1.5F, KEYCAP_RADIUS, 1.0F,
                Material.withAlpha(border, 1.0F - 0.65F * t));
        ui.text(Typeface.CAPTION, entry.label, keycapWidth / 2.0F, (KEYCAP_HEIGHT - 1.5F) / 2.0F,
                label, UI.ALIGN_CENTER);
        ui.pop();
    }

    private void drawName(Scheme scheme, Entry entry, float width, float keycapWidth, float t) {
        float nameX = ROW_PAD + keycapWidth + KEYCAP_NAME_GAP;
        float chipX = width - ROW_PAD - (CHIP_WIDTH + CHIP_MORPH);
        float available = Math.max(0.0F, chipX - 6.0F - nameX) + 1.0F;
        String name = ui.truncate(Typeface.BODY_MEDIUM, entry.name, Math.min(NAME_MAX_WIDTH, available));
        ui.text(Typeface.BODY_MEDIUM, name, nameX, ROW_HEIGHT / 2.0F,
                Material.mix(scheme.onSurfaceVariant, scheme.onSurface, t), UI.ALIGN_LEFT);
    }

    private void drawStateChip(Scheme scheme, Entry entry, float width, int rowFill, float t) {
        // Expressive shape morph: the chip grows a few pixels as it fills in, the same gesture
        // Material 3 uses for a selected filter chip.
        float chipWidth = CHIP_WIDTH + CHIP_MORPH * t;
        float chipX = width - ROW_PAD - chipWidth;
        float chipY = (ROW_HEIGHT - CHIP_HEIGHT) / 2.0F;
        float radius = CHIP_HEIGHT / 2.0F;

        int offFill = Material.mix(rowFill, scheme.surfaceContainerHighest, 0.9F);
        int onFill = scheme.primary;
        ui.rounded(chipX, chipY, chipWidth, CHIP_HEIGHT, radius, Material.mix(offFill, onFill, t));

        int offText = scheme.onSurfaceVariant;
        int onText = scheme.onPrimary;
        int textColor = Material.mix(offText, onText, t);

        if (t < 0.999F) {
            ui.alphaPush(1.0F - t);
            ui.text(Typeface.MICRO, "OFF", chipX + chipWidth / 2.0F, chipY + CHIP_HEIGHT / 2.0F,
                    textColor, UI.ALIGN_CENTER);
            ui.alphaPop();
        }
        if (t > 0.001F) {
            ui.alphaPush(t);
            ui.text(Typeface.MICRO, "ON", chipX + chipWidth / 2.0F, chipY + CHIP_HEIGHT / 2.0F,
                    textColor, UI.ALIGN_CENTER);
            ui.alphaPop();
        }
    }

    // ------------------------------------------------------------------ //
    // Entry bookkeeping
    // ------------------------------------------------------------------ //

    private void updateEntries(KeybindsModule module, float delta) {
        boolean enabledOnly = module.enabledOnly();
        Minecraft mc = Minecraft.getMinecraft();
        boolean chatOpen = mc.currentScreen instanceof GuiChat;

        List<Module> wanted = new ArrayList<Module>();
        for (Module candidate : ModuleManager.modules) {
            if (!trackable(candidate)) {
                continue;
            }
            if (enabledOnly && !candidate.isEnabled()) {
                continue;
            }
            wanted.add(candidate);
        }
        wanted.sort(Comparator.comparing(m -> Text.strip(m.getName()).toLowerCase()));

        for (Entry entry : cache.values()) {
            entry.wanted = false;
        }

        for (int i = 0; i < wanted.size(); i++) {
            Module candidate = wanted.get(i);
            Entry entry = cache.get(candidate);
            if (entry == null) {
                entry = new Entry(candidate);
                entry.show.set(0.0F);
                entry.state.set(candidate.isEnabled() ? 1.0F : 0.0F);
                entry.delay = i * ROW_STAGGER;
                cache.put(candidate, entry);
            }
            entry.wanted = true;
            entry.refreshLabel();
            entry.show.to(1.0F, 240.0F, Ease.EMPHASIZED_DECELERATE);
            entry.animateToggle(candidate.isEnabled());
            entry.animatePress(candidate, chatOpen, delta);
            entry.update(delta);
        }

        // Rows that lost their module (unbound, hidden, or filtered by "Show") keep animating out.
        List<Module> present = new ArrayList<Module>(cache.keySet());
        for (Module candidate : present) {
            Entry entry = cache.get(candidate);
            if (entry != null && !entry.wanted) {
                entry.show.to(0.0F, 180.0F, Ease.DECELERATE);
                entry.update(delta);
                if (entry.show.value() <= 0.002F && entry.show.isDone()) {
                    cache.remove(candidate);
                }
            }
        }

        entries.clear();
        entries.addAll(cache.values());
        entries.sort(Comparator.comparing(e -> e.name.toLowerCase()));
    }

    private static boolean trackable(Module candidate) {
        if (candidate == null || candidate == KeybindsModule.instance) {
            return false;
        }
        if (candidate.isProfile() || candidate.noToggle || !candidate.canBeBound()) {
            return false;
        }
        if (KeybindsHidden.isHidden(candidate)) {
            return false;
        }
        int key = candidate.getKeycode();
        return key != 0 && key != -1;
    }

    private float keycapWidth() {
        float width = KEYCAP_MIN_WIDTH;
        for (Entry entry : entries) {
            if (!entry.measured()) {
                continue;
            }
            width = Math.max(width, Math.min(KEYCAP_MAX_WIDTH,
                    Typeface.CAPTION.width(entry.label) + KEYCAP_TEXT_PAD));
        }
        return width;
    }

    private float nameWidth() {
        float width = 0.0F;
        for (Entry entry : entries) {
            if (!entry.measured()) {
                continue;
            }
            width = Math.max(width, Math.min(NAME_MAX_WIDTH, Typeface.BODY_MEDIUM.width(entry.name)));
        }
        // A pixel of slack keeps the widest name from being ellipsised by a rounding difference.
        return width + 2.0F;
    }

    private float cardWidth(KeybindsModule module, float keycapWidth, float nameWidth) {
        float rows = PAD * 2.0F + ROW_PAD * 2.0F + keycapWidth + KEYCAP_NAME_GAP + nameWidth
                + 6.0F + CHIP_WIDTH + CHIP_MORPH;
        float title = Typeface.HEADLINE.width("Keybinds");
        float header = PAD * 2.0F + HEADER_ICON + HEADER_TITLE_GAP + title;
        if (module.showCount()) {
            header += 10.0F + Typeface.MICRO.width(entries.size() + "/" + entries.size()) + 14.0F + 8.0F;
        }
        return Math.max(MIN_CARD_WIDTH, Math.max(rows, header));
    }

    private float cardHeight() {
        float rows = 0.0F;
        int visibleRows = 0;
        for (Entry entry : entries) {
            float show = Material.clamp(entry.show.value(), 0.0F, 1.0F);
            if (show <= 0.001F) {
                continue;
            }
            rows += (ROW_HEIGHT + ROW_GAP) * show;
            visibleRows++;
        }
        if (visibleRows > 0) {
            rows -= ROW_GAP;
        } else {
            rows = ROW_HEIGHT;
        }
        return PAD * 2.0F + HEADER + 10.0F + rows;
    }

    // ------------------------------------------------------------------ //
    // Dragging and the position editor
    // ------------------------------------------------------------------ //

    private void handleDrag(KeybindsModule module, Minecraft mc, ScaledResolution resolution) {
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

    /** Moves the card so its top-left corner sits at the requested GUI coordinates. */
    public void dragTo(KeybindsModule module, float desiredX, float desiredY) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        float maxX = Math.max(1.0F, resolution.getScaledWidth() - w - EDGE);
        float maxY = Math.max(1.0F, resolution.getScaledHeight() - h - EDGE);
        float percentX = Material.clamp((desiredX - MARGIN) / maxX * 100.0F, 0.0F, 100.0F);
        float percentY = Material.clamp((desiredY - MARGIN) / maxY * 100.0F, 0.0F, 100.0F);
        module.setPosition(percentX, percentY);
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

    /** Rows currently drawn (harness hook: "Show" mode and .hidekey both change this). */
    public int rows() {
        return entries.size();
    }

    /** Clears every per-session animation so re-enabling starts from a clean card. */
    public void reset() {
        cache.clear();
        entries.clear();
        visible.set(0.0F);
        cardWidth.set(MIN_CARD_WIDTH);
        cardHeight.set(PAD * 2.0F + HEADER + 10.0F + ROW_HEIGHT);
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

    /** One module row and every animation attached to it. */
    private static final class Entry {
        private final Module module;
        private final String name;
        private final Anim state = new Anim(0.0F);
        private final Anim show = new Anim(0.0F);
        private final Anim flash = new Anim(0.0F);
        private final Spring pop = new Spring(1.0F);
        private final Spring press = new Spring(0.0F);
        private String label;
        private boolean wanted;
        private boolean lastEnabled;
        private float delay;

        private Entry(Module module) {
            this.module = module;
            this.name = Text.strip(module.getName());
            this.lastEnabled = module.isEnabled();
            this.label = label(module.getKeycode());
        }

        private void refreshLabel() {
            String next = label(module.getKeycode());
            if (!next.equals(this.label)) {
                this.label = next;
            }
        }

        /** Whether the row is visible enough to influence the card's width. */
        private boolean measured() {
            return this.wanted || this.show.value() > 0.002F;
        }

        private void animateToggle(boolean enabled) {
            if (enabled != this.lastEnabled) {
                this.lastEnabled = enabled;
                this.state.to(enabled ? 1.0F : 0.0F, 300.0F, Ease.EMPHASIZED_DECELERATE);
                this.flash.set(1.0F);
                this.flash.to(0.0F, 520.0F, Ease.DECELERATE);
                this.pop.impulse(enabled ? 2.6F : -1.8F);
            }
        }

        private void animatePress(Module candidate, boolean chatOpen, float delta) {
            boolean down = !chatOpen && candidate.isEnabled() && isDown(candidate.getKeycode());
            this.press.target(down ? 1.0F : 0.0F);
            this.press.update(delta);
        }

        private void update(float delta) {
            if (this.delay > 0.0F) {
                this.delay -= delta;
            } else {
                this.show.update(delta);
            }
            this.state.update(delta);
            this.flash.update(delta);
            this.pop.update(delta);
        }

        private static boolean isDown(int key) {
            if (key < 0) {
                return Mouse.isButtonDown(key + 100);
            }
            return key > 0 && Keyboard.isKeyDown(key);
        }

        /** Same labels the ClickGUI bind row shows, shortened so long key names still fit. */
        private static String label(int key) {
            if (key < 0) {
                return "MB" + (key + 100);
            }
            String name = BindComponent.keyName(key);
            if (name == null || name.isEmpty()) {
                return "?";
            }
            switch (name) {
                case "APOSTROPHE":
                    return "'";
                case "GRAVE":
                    return "`";
                case "BACKSLASH":
                    return "\\";
                case "SEMICOLON":
                    return ";";
                case "LBRACKET":
                    return "[";
                case "RBRACKET":
                    return "]";
                case "EQUALS":
                    return "=";
                case "MINUS":
                    return "-";
                case "COMMA":
                    return ",";
                case "PERIOD":
                    return ".";
                case "SLASH":
                    return "/";
                case "MULTIPLY":
                    return "*";
                case "DIVIDE":
                    return "/";
                case "DECIMAL":
                    return ".";
                case "SUBTRACT":
                    return "-";
                case "ADD":
                    return "+";
                case "LCONTROL":
                    return "LCTRL";
                case "RCONTROL":
                    return "RCTRL";
                case "LMENU":
                    return "LALT";
                case "RMENU":
                    return "RALT";
                case "CAPITAL":
                    return "CAPS";
                case "RETURN":
                    return "ENTER";
                case "BACK":
                    return "BKSP";
                case "ESCAPE":
                    return "ESC";
                case "PRIOR":
                    return "PGUP";
                case "NEXT":
                    return "PGDN";
                case "INSERT":
                    return "INS";
                case "DELETE":
                    return "DEL";
                default:
                    break;
            }
            if (name.startsWith("NUMPAD") && name.length() == 7) {
                return "NUM" + name.charAt(6);
            }
            return name.length() <= 8 ? name : name.substring(0, 8);
        }
    }
}
