package xyz.daikirai.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.inventory.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.render.CustomGuiModule;

@Mixin(InventoryEffectRenderer.class)
public abstract class MixinInventoryEffectRenderer extends GuiContainer {
    protected MixinInventoryEffectRenderer(Container container) {
        super(container);
    }

    @Inject(method = "updateActivePotionEffects", at = @At("RETURN"))
    private void onyx$center(CallbackInfo ci) {
        if (CustomGuiModule.centeredContainers()) guiLeft = (width - xSize) / 2;
    }

    @ModifyVariable(method = "drawActivePotionEffects", at = @At("STORE"), ordinal = 0)
    private int onyx$potionPanelX(int x) {
        return CustomGuiModule.centeredContainers() ? Math.max(2, x) : x;
    }
}
