package xyz.daikirai.mixin;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiControls.class)
public interface AccessorGuiControls {
    @Accessor("parentScreen") GuiScreen onyx$parent();
}
