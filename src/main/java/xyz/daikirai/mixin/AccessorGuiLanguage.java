package xyz.daikirai.mixin;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GuiLanguage.class)
public interface AccessorGuiLanguage {
    @Accessor("parentScreen") GuiScreen onyx$parent();
}
