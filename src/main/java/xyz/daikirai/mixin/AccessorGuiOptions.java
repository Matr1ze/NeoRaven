package xyz.daikirai.mixin;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiOptions.class)
public interface AccessorGuiOptions {
    @Accessor("field_146441_g") GuiScreen onyx$parent();
}
