package xyz.daikirai.mixin;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiCustomizeSkin.class)
public interface AccessorGuiSkin {
    @Accessor("parentScreen") GuiScreen onyx$parent();
}
