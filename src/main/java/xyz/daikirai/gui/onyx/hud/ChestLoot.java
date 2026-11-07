package xyz.daikirai.gui.onyx.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import xyz.daikirai.gui.onyx.Text;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.WavyProgressIndicator;
import xyz.daikirai.module.player.ChestStealerModule.LootSession;
import xyz.daikirai.module.player.ChestStealerModule.LootTransfer;

/** Content only, like TabList: Island owns the geometry and the shared frosted surface. */
public final class ChestLoot {
    public static final float WIDTH = 256.0F;
    public static final float HEIGHT = 152.0F;
    private static final float GRID_Y = 36.0F;
    private static final float CELL = 24.0F;
    private static final float PITCH = 26.0F;
    private static final float RIPPLE_MS = 460.0F;
    private final Anim pageReveal = new Anim(1.0F);
    private final Anim progress = new Anim(0.0F);
    private LootSession session;
    private int page;
    private int lastProcessed;
    private long lastProgressAt;

    public boolean update(LootSession current, float delta) {
        if (current != null && current != session) {
            session = current;
            page = current.nextSlot / 27;
            pageReveal.set(1.0F);
            progress.set(0.0F);
            lastProcessed = 0;
            lastProgressAt = 0L;
        }
        if (session == null) return false;
        if (current != null) {
            int target = session.nextSlot / 27;
            if (!session.transfers.isEmpty()) {
                LootTransfer latest = session.transfers.get(session.transfers.size() - 1);
                if (System.currentTimeMillis() - latest.time < RIPPLE_MS) target = latest.slot / 27;
            }
            if (target != page) {
                page = target;
                pageReveal.set(0.0F);
                pageReveal.to(1.0F, 250.0F, Ease.EMPHASIZED_DECELERATE);
            }
        }
        pageReveal.update(delta);
        if (session.processed != lastProcessed) {
            long now = System.currentTimeMillis();
            // Follow confirmed steps, with a longer ease when clicks are spaced further apart.
            float duration = lastProgressAt == 0L ? 300.0F
                    : Material.clamp((now - lastProgressAt) * 0.9F, 180.0F, 600.0F);
            progress.to(session.processed / (float) Math.max(1, session.total), duration, Ease.STANDARD);
            lastProcessed = session.processed;
            lastProgressAt = now;
        }
        progress.update(delta);
        return current != null || session.completed
                && (System.currentTimeMillis() - session.endedAt < RIPPLE_MS || !progress.isDone());
    }

    public void clear() {
        session = null;
        pageReveal.set(1.0F);
        progress.set(0.0F);
        lastProcessed = 0;
        lastProgressAt = 0L;
    }

    public void draw(UI ui, Scheme scheme, float expansion) {
        if (session == null) return;
        long now = System.currentTimeMillis();
        int pages = Math.max(1, (session.container.getLowerChestInventory().getSizeInventory() + 26) / 27);
        ui.icon("\ue8b1", 20.0F, 19.0F, 16.0F, scheme.primary);
        String count = session.transfers.size() + " / " + session.total;
        float badgeWidth = Math.max(44.0F, Typeface.CAPTION.width(count) + 18.0F);
        String title = Text.strip(session.container.getLowerChestInventory().getDisplayName().getUnformattedText());
        ui.text(Typeface.LABEL, ui.truncate(Typeface.LABEL, title, WIDTH - badgeWidth - 59.0F), 35.0F, 19.0F,
                scheme.onSurface, UI.ALIGN_LEFT);
        ui.rounded(WIDTH - badgeWidth - 12.0F, 10.0F, badgeWidth, 18.0F, 9.0F, scheme.primaryContainer);
        ui.text(Typeface.CAPTION, count, WIDTH - 12.0F - badgeWidth / 2.0F, 19.0F,
                scheme.onPrimaryContainer, UI.ALIGN_CENTER);

        for (int i = 0; i < 27; i++) {
            int slot = page * 27 + i;
            float x = 12.0F + (i % 9) * PITCH;
            float entry = Material.clamp((expansion - (i / 9) * 0.035F) / 0.72F, 0.0F, 1.0F);
            float y = GRID_Y + (i / 9) * PITCH + (1.0F - pageReveal.value()) * 7.0F;
            boolean next = !session.finished && slot == session.nextSlot;
            ui.alphaPush(entry * pageReveal.value());
            int surface = Material.mix(scheme.surfaceContainerHigh, scheme.onSurface, 0.045F);
            ui.rounded(x, y, CELL, CELL, next ? 9.0F : 6.0F,
                    Material.withAlpha(next ? scheme.primaryContainer : surface, next ? 0.85F : 0.38F));
            if (next) {
                ui.outline(x, y, CELL, CELL, 9.0F, 1.0F, Material.withAlpha(scheme.primary, 0.55F));
            }
            if (slot < session.container.getLowerChestInventory().getSizeInventory()) {
                ItemStack stack = session.container.getLowerChestInventory().getStackInSlot(slot);
                float pop = Ease.EMPHASIZED_DECELERATE.get(entry) * pageReveal.value();
                if (stack != null && pop > 0.05F) drawItem(ui, scheme, stack, x + CELL / 2.0F, y + CELL / 2.0F, pop, true);
            }
            ui.alphaPop();
        }
        for (LootTransfer transfer : session.transfers) {
            float age = now - transfer.time;
            float t = age / RIPPLE_MS;
            if (transfer.slot / 27 != page || t < 0.0F || t >= 1.0F) continue;
            int cell = transfer.slot % 27;
            float x = 12.0F + CELL / 2.0F + (cell % 9) * PITCH;
            float y = GRID_Y + CELL / 2.0F + (cell / 9) * PITCH
                    + (1.0F - pageReveal.value()) * 7.0F;
            // Keep the taken item briefly at its original slot, underneath the expanding ring.
            float itemFade = Material.clamp(1.0F - age / 180.0F, 0.0F, 1.0F);
            if (itemFade > 0.01F && session.container.getLowerChestInventory().getStackInSlot(transfer.slot) == null) {
                ui.alphaPush(itemFade * pageReveal.value());
                drawItem(ui, scheme, transfer.stack, x, y, 1.0F, false);
                ui.alphaPop();
            }
            float radius = 3.0F + (CELL / 2.0F - 4.0F) * Ease.DECELERATE.get(t);
            float opacity = (1.0F - t) * (1.0F - t) * pageReveal.value();
            ui.circle(x, y, radius, Material.withAlpha(scheme.primary, opacity * 0.12F));
            ui.outline(x - radius, y - radius, radius * 2.0F, radius * 2.0F, radius,
                    1.4F - t * 0.6F, Material.withAlpha(scheme.primary, opacity * 0.95F));
        }
        boolean done = session.finished && session.completed;
        ui.text(Typeface.CAPTION, xyz.daikirai.util.Lang.text(done ? "Finished" : "Taking items"), 14.0F, 128.0F,
                scheme.onSurfaceVariant, UI.ALIGN_LEFT);
        String hint = pages > 1 ? (page + 1) + " / " + pages : xyz.daikirai.util.Lang.module("ChestStealer");
        ui.text(Typeface.MICRO, hint, WIDTH - 14.0F, 128.0F, scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
        WavyProgressIndicator.Style style = new WavyProgressIndicator.Style(16.0F, 0.65F, 22.0F,
                scheme.primary, scheme.surfaceContainerHighest);
        WavyProgressIndicator.drawLinear(ui, 14.0F, 138.0F, WIDTH - 28.0F, 5.0F,
                progress.value(), style);
    }

    private static void drawItem(UI ui, Scheme scheme, ItemStack stack, float x, float y, float scale, boolean count) {
        // Vanilla item rendering resets vertex alpha; shrink with the card's opacity instead.
        scale *= (float) Math.sqrt(ui.alpha());
        if (scale <= 0.02F) return;
        Minecraft mc = Minecraft.getMinecraft();
        RenderItem renderer = mc.getRenderItem();
        float oldZ = renderer.zLevel;
        ui.push();
        try {
            ui.translate(x, y);
            ui.scale(scale, 0.0F, 0.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            renderer.zLevel = 100.0F;
            renderer.renderItemAndEffectIntoGUI(stack, -8, -8);
        } finally {
            renderer.zLevel = oldZ;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.disableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            ui.pop();
        }
        if (count && stack.stackSize > 1) ui.text(Typeface.MICRO, String.valueOf(stack.stackSize),
                x + 9.0F, y + 7.0F, scheme.onSurface, UI.ALIGN_RIGHT);
    }
}
