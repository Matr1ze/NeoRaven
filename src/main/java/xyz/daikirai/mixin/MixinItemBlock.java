package xyz.daikirai.mixin;

import net.minecraft.item.ItemBlock;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.daikirai.via.ViaCompatibility;

@Mixin(ItemBlock.class)
public class MixinItemBlock {
    @Redirect(method = "onItemUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;playSoundEffect(DDDLjava/lang/String;FF)V"))
    private void onyx$placementSound(World world, double x, double y, double z, String sound, float volume, float pitch) {
        ViaCompatibility.playPlacementSound(world, x, y, z, sound, volume, pitch);
    }
}
