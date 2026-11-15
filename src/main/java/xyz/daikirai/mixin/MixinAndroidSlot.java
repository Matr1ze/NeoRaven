package xyz.daikirai.mixin;

import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.menu.AndroidUi;
import xyz.daikirai.gui.onyx.menu.AndroidWallpaper;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.render.HandState;

/** Retains vanilla list entries and their input/scroll coordinates, replacing only list chrome. */
@Mixin(GuiSlot.class)
public abstract class MixinAndroidSlot {
    @Shadow public int width, height, top, bottom, left, right, headerPadding;
    @Shadow @Final public int slotHeight;
    @Shadow protected int mouseX, mouseY;
    @Shadow protected float amountScrolled;
    @Shadow protected boolean field_178041_q, showSelectionBox, hasListHeader;
    @Shadow protected abstract int getSize();
    @Shadow protected abstract boolean isSelected(int index);
    @Shadow protected abstract void drawSlot(int index, int x, int y, int height, int mouseX, int mouseY);
    @Shadow protected abstract void bindAmountScrolled();
    @Shadow public abstract int getListWidth();
    @Shadow public abstract int func_148135_f();
    @Shadow protected abstract int getContentHeight();
    @Shadow protected abstract int getScrollBarX();
    @Shadow protected abstract void drawListHeader(int x, int y, Tessellator tessellator);
    @Shadow protected abstract void func_178040_a(int index, int x, int y);
    @Shadow protected abstract void func_148142_b(int x, int y);
    @Unique private UI onyx$listUi;

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    private void onyx$list(int x, int y, float ticks, CallbackInfo ci) {
        if (!AndroidUi.active()) return;
        ci.cancel();
        if (!field_178041_q) return;
        mouseX = x; mouseY = y;
        bindAmountScrolled();
        if (onyx$listUi == null) onyx$listUi = new UI();
        UI ui = onyx$listUi;
        int listX = left + width / 2 - getListWidth() / 2 + 2;
        int startY = top + 4 - (int) amountScrolled;
        int boxLeft = Math.max(left, listX - 8);
        int boxRight = Math.min(right, Math.max(listX + getListWidth() + 4, getScrollBarX() + 6));
        try (HandState state = new HandState()) {
            ui.begin();
            ui.rounded(boxLeft, top, boxRight - boxLeft, bottom - top, 12, AndroidWallpaper.surfaceHigh());
            ui.scissorPush(boxLeft, top + 2, boxRight - boxLeft, bottom - top - 4);
            if (hasListHeader) {
                GlStateManager.enableTexture2D();
                drawListHeader(listX, startY, Tessellator.getInstance());
            }
            for (int i = 0; i < getSize(); i++) {
                int rowY = startY + i * slotHeight + headerPadding;
                int rowH = slotHeight - 4;
                if (rowY > bottom || rowY + rowH < top) { func_178040_a(i, listX, rowY); continue; }
                boolean selected = showSelectionBox && isSelected(i);
                if (selected || (y >= rowY && y < rowY + slotHeight && x >= boxLeft && x < boxRight)) {
                    ui.rounded(boxLeft + 3, rowY - 1, boxRight - boxLeft - 12, slotHeight - 2, Math.min(10, slotHeight / 2F),
                            selected ? AndroidWallpaper.primaryContainer() : Material.withAlpha(AndroidWallpaper.ink(), .06F));
                }
                GlStateManager.enableTexture2D();
                GlStateManager.color(1, 1, 1, 1);
                drawSlot(i, listX, rowY, rowH, x, y);
            }
            ui.scissorPop();
            int range = func_148135_f();
            if (range > 0) {
                float viewport = bottom - top;
                float thumb = Math.max(16, Math.min(viewport - 8, viewport * viewport / Math.max(1, getContentHeight())));
                float thumbY = top + (viewport - thumb) * amountScrolled / range;
                ui.rounded(getScrollBarX() + 1, thumbY, 4, thumb, 2, AndroidWallpaper.outline());
            }
            ui.end();
            func_148142_b(x, y);
        }
    }
}
