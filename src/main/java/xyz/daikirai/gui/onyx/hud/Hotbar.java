package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.render.CustomGuiModule;
import xyz.daikirai.util.RenderUtils;

/**
 * Material 3 hotbar, ported from OpenOnyx's {@code CustomGuiModule} hotbar (drawn by
 * {@code render.hud.Cls4}).
 *
 * <p>Onyx replaced the vanilla hotbar with a 204x28 frosted bar: nine 22px slots, a rounded
 * selection box that glides between slots, items that pop in when the stack changes, stack counts,
 * durability pips, and the experience bar drawn above the bar. This port keeps all of that and
 * exposes it as the "Hotbar" group inside {@link CustomGuiModule}, next to the scoreboard, tablist,
 * font and open-animation replacements.</p>
 *
 * <p>The vanilla hotbar and experience bar are cancelled through Forge's
 * {@code RenderGameOverlayEvent}, so the two never draw on top of each other.</p>
 */
public final class Hotbar {

    private static final float W = 204.0F;
    private static final float H = 28.0F;
    private static final float SLOT = 22.0F;
    private static final float PAD = 3.0F;
    private static final float RADIUS = 7.0F;

    private static final float XP_LINE = 3.0F;
    /**
     * Vanilla puts the hotbar 2px above the window edge; the port does the same so the bar reads as
     * "attached" to the bottom of the screen instead of floating.
     */
    private static final float BOTTOM = 2.0F;
    /**
     * No extra lift for the experience bar: the bar is 28px tall, so lifting it for the XP readout
     * pushed its top into the vanilla health/food rows and left a gap above the window edge. The
     * experience progress is drawn as a rim inside the bar instead.
     */
    private static final float CHAT_LIFT = 14.0F;
    private static final float DURA_W = 12.0F;
    private static final float DURA_H = 2.0F;
    private static final float BLUR_COVERAGE = 0.62F;
    private static final long NAME_MS = 2000L;
    private static final int DURA_LOW = 0xFFE05B5B;
    private static final int DURA_HIGH = 0xFF5FD35F;

    /** Geometry of the vanilla hotbar, which is the frame the vanilla status rows are laid out in. */
    private static final float VANILLA_TOP = 22.0F;
    /**
     * Vanilla puts the hearts/food rows 17px above the hotbar's top edge, because its 5px experience
     * bar sits in that band. The port draws experience inside the bar instead, so the rows are
     * pulled down to 11px and hug the bar — the 9px icons then end 2px above its top edge.
     */
    private static final float VANILLA_ROW = 17.0F;
    private static final float STATUS_ROW = 11.0F;

    private final UI ui = new UI();
    private final Anim visible = new Anim(0.0F);
    private final Anim selection = new Anim(0.0F);
    private final Anim lift = new Anim(0.0F);
    private final Anim[] pops = new Anim[9];
    private final ItemStack[] stacks = new ItemStack[9];

    private long lastFrame;
    private int frames;
    private int lastSlot = -1;
    private long nameUntil;
    private String heldName;
    private float top;
    private float bottom;
    private float lastScale = 1.0F;

    public Hotbar() {
        for (int i = 0; i < pops.length; i++) {
            pops[i] = new Anim(1.0F);
        }
    }

    /** Draws the bar; must be called from the render tick, after the world. */
    public void render(CustomGuiModule module) {
        if (module == null) {
            return;
        }
        float delta = tick();
        frames++;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.thePlayer;
        boolean active = module.hotbarEnabled() && player != null && mc.playerController != null
                && !mc.playerController.isSpectator();
        visible.to(active ? 1.0F : 0.0F, active ? 180.0F : 140.0F,
                active ? Ease.EMPHASIZED : Ease.DECELERATE);
        visible.update(delta);
        float fade = visible.value();
        if (!active || fade <= 0.002F) {
            return;
        }

        Scheme scheme = Theme.scheme();
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = module.hotbarScale();
        animate(module, player, delta);

        float width = W * scale;
        float height = H * scale;
        float x = (resolution.getScaledWidth() - width) / 2.0F;
        // Flush with the window edge: the health/food rows sit 39px up in vanilla, and a 28px bar
        // plus this 2px margin tops out at 30px, i.e. directly underneath them.
        float y = resolution.getScaledHeight() - BOTTOM - lift.value() - height;
        this.top = y;
        this.bottom = y + height;
        this.lastScale = scale;

        ui.begin();
        try {
            GlStateManager.pushMatrix();
            mc.entityRenderer.setupOverlayRendering();
            if (module.hotbarFrosted()) {
                Blur.prepare();
            }
            ui.alphaPush(fade);
            ui.push();
            ui.translate(x, y);
            ui.scale(scale, 0.0F, 0.0F);
            drawBar(module, scheme, player);
            ui.pop();
            ui.alphaPop();
        } finally {
            GlStateManager.popMatrix();
            ui.end();
            restoreHudState();
        }
    }

    // ------------------------------------------------------------------ //
    // Animation state
    // ------------------------------------------------------------------ //

    private void animate(CustomGuiModule module, EntityPlayerSP player, float delta) {
        int current = player.inventory.currentItem;
        if (module.hotbarSlotGlide()) {
            selection.to(current, 200.0F, Ease.EMPHASIZED_DECELERATE);
        } else {
            selection.set(current);
        }
        selection.update(delta);

        boolean chat = Minecraft.getMinecraft().currentScreen instanceof GuiChat;
        lift.to(chat ? CHAT_LIFT : 0.0F, 150.0F, Ease.DECELERATE);
        lift.update(delta);

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (changed(stacks[i], stack)) {
                stacks[i] = stack;
                if (module.hotbarItemPop() && stack != null) {
                    pops[i].set(0.55F);
                    pops[i].to(1.0F, 250.0F, Ease.EMPHASIZED_DECELERATE);
                }
            }
            pops[i].update(delta);
        }

        if (current != lastSlot) {
            lastSlot = current;
            ItemStack held = player.getHeldItem();
            heldName = held == null ? null : held.getDisplayName();
            nameUntil = System.currentTimeMillis() + NAME_MS;
        }
    }

    private static boolean changed(ItemStack previous, ItemStack current) {
        if (previous == null || current == null) {
            return previous != current;
        }
        if (previous.getItem() != current.getItem()) {
            return true;
        }
        return !current.isItemStackDamageable() && previous.getMetadata() != current.getMetadata();
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private void drawBar(CustomGuiModule module, Scheme scheme, EntityPlayerSP player) {
        int surface = Material.withAlpha(scheme.surfaceContainerHigh, 1.0F);
        if (module.hotbarFrosted() && Blur.ready()) {
            ui.frost(0.0F, 0.0F, W, H, RADIUS, surface, BLUR_COVERAGE);
        } else {
            ui.rounded(0.0F, 0.0F, W, H, RADIUS, surface);
        }

        float selected = PAD + selection.value() * SLOT;
        ui.rounded(selected, PAD, SLOT, SLOT, RADIUS, Material.withAlpha(scheme.primary, 0.28F));
        ui.outline(selected, PAD, SLOT, SLOT, RADIUS, 1.5F, scheme.primary);

        drawExperience(scheme, player);

        for (int i = 0; i < 9; i++) {
            drawItem(scheme, player.inventory.mainInventory[i], i);
        }

        if (heldName != null && System.currentTimeMillis() < nameUntil) {
            // Above the status rows, which now ride on the bar's top edge.
            ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, heldName, W - 20.0F),
                    W / 2.0F, -STATUS_ROW - 24.0F, scheme.onSurface, UI.ALIGN_CENTER);
        }
    }

    /**
     * Experience is drawn as a rim along the inside of the bar's top edge, so the bar itself can
     * stay flush against the window edge and the vanilla rows keep their offsets.
     *
     * <p>The level number goes in the gap vanilla leaves in the middle of the hearts/food row,
     * one row above the bar. It used to be a chip on the bar's top-right corner, which landed on
     * top of the food icons once the rows started following the bar.</p>
     */
    private void drawExperience(Scheme scheme, EntityPlayerSP player) {
        float inset = RADIUS + 4.0F;
        float track = W - inset * 2.0F;
        ui.rounded(inset, 1.5F, track, XP_LINE, XP_LINE / 2.0F,
                Material.withAlpha(scheme.surfaceContainerHighest, 0.55F));
        float fill = Material.clamp(player.experience, 0.0F, 1.0F);
        if (fill > 0.002F) {
            ui.rounded(inset, 1.5F, track * fill, XP_LINE, XP_LINE / 2.0F, scheme.tertiary);
        }
        if (player.experienceLevel > 0) {
            // Centred in the gap between the hearts and the food icons, on the same row as them.
            ui.text(Typeface.CAPTION, String.valueOf(player.experienceLevel),
                    W / 2.0F, -STATUS_ROW / 2.0F - 1.0F, scheme.tertiary, UI.ALIGN_CENTER);
        }
    }

    private void drawItem(Scheme scheme, ItemStack stack, int index) {
        if (stack == null) {
            return;
        }
        float centerX = PAD + index * SLOT + SLOT / 2.0F;
        float centerY = PAD + SLOT / 2.0F;
        float pop = Material.clamp(pops[index].value(), 0.0F, 1.0F);

        ui.push();
        ui.scale(pop, centerX, centerY);
        // RenderUtils.drawItem binds the item atlas but never enables texturing, which the UI
        // leaves off after every shape; without this the item renders as a white quad.
        GlStateManager.enableTexture2D();
        RenderUtils.drawItem(stack, centerX - 8.0F, centerY - 8.0F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.enableTexture2D();
        ui.pop();

        if (stack.stackSize > 1) {
            String count = String.valueOf(stack.stackSize);
            ui.text(Typeface.MICRO, count, centerX + 8.0F - Typeface.MICRO.width(count),
                    centerY + 6.0F, scheme.onSurface, UI.ALIGN_LEFT);
        }

        if (stack.isItemDamaged() && stack.getMaxDamage() > 0) {
            float fraction = Material.clamp(
                    1.0F - stack.getItemDamage() / (float) stack.getMaxDamage(), 0.0F, 1.0F);
            float barY = centerY + 4.0F;
            ui.rect(centerX - DURA_W / 2.0F, barY, DURA_W, DURA_H, 0x80000000);
            if (DURA_W * fraction > 0.5F) {
                ui.rect(centerX - DURA_W / 2.0F, barY, DURA_W * fraction, DURA_H,
                        mix(DURA_LOW, DURA_HIGH, fraction));
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Status rows
    // ------------------------------------------------------------------ //

    /**
     * Pushes the transform that maps the vanilla hotbar frame onto the ported bar, so the hearts,
     * food, armour, air and mount rows are drawn scaled and lifted exactly like the bar.
     *
     * <p>The vanilla rows are laid out around the vanilla hotbar — centred on the screen, sitting
     * on the bottom edge, with the hearts and food above its top edge and a 22px gap in the middle
     * for the level number. Scaling about the screen centre therefore keeps the rows where vanilla
     * put them relative to the bar, keeps that middle gap centred under the level badge, and leaves
     * the bar's extra 22px of width as a small inset on both sides. The vertical anchor is lifted
     * by {@code VANILLA_ROW - STATUS_ROW} so the rows end up hugging the bar instead of leaving
     * vanilla's empty experience band between them.</p>
     *
     * @return {@code true} when a matrix was pushed and must be popped with
     *         {@link #popStatusTransform()}
     */
    public boolean pushStatusTransform(CustomGuiModule module) {
        if (module == null || !module.hotbarEnabled() || visible.value() <= 0.002F) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.playerController == null) {
            return false;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float scale = module.hotbarScale();
        float centerX = resolution.getScaledWidth() / 2.0F;
        float y = resolution.getScaledHeight() - BOTTOM - lift.value() - H * scale;
        float anchor = resolution.getScaledHeight() - VANILLA_TOP - (VANILLA_ROW - STATUS_ROW);

        GlStateManager.pushMatrix();
        GlStateManager.translate(centerX, y, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        GlStateManager.translate(-centerX, -anchor, 0.0F);
        return true;
    }

    public void popStatusTransform() {
        GlStateManager.popMatrix();
    }

    /** Top edge of the hearts/food row as the bar places it, in GUI units. */
    public float statusRowTop() {
        return top - STATUS_ROW * lastScale;
    }

    /** Height of the hearts/food row as the bar places it, in GUI units. */
    public float statusRowHeight() {
        return 10.0F * lastScale;
    }

    // ------------------------------------------------------------------ //
    // Misc
    // ------------------------------------------------------------------ //

    /** Frames the bar was drawn in (used by the dev harness). */
    public int frames() {
        return frames;
    }

    /** Screen-space top edge of the bar in GUI units, as of the last frame. */
    public float top() {
        return top;
    }

    /** Screen-space bottom edge of the bar in GUI units, as of the last frame. */
    public float bottom() {
        return bottom;
    }

    public void reset() {
        visible.set(0.0F);
        selection.set(0.0F);
        lift.set(0.0F);
        lastFrame = 0L;
        lastSlot = -1;
        heldName = null;
        nameUntil = 0L;
        top = 0.0F;
        bottom = 0.0F;
        for (int i = 0; i < 9; i++) {
            pops[i].set(1.0F);
            stacks[i] = null;
        }
    }

    private float tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        return delta;
    }

    private static int mix(int from, int to, float t) {
        t = Material.clamp(t, 0.0F, 1.0F);
        return Material.argb(255,
                Math.round(Material.red(from) + (Material.red(to) - Material.red(from)) * t),
                Math.round(Material.green(from) + (Material.green(to) - Material.green(from)) * t),
                Math.round(Material.blue(from) + (Material.blue(to) - Material.blue(from)) * t));
    }

    private static void restoreHudState() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
