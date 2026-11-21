package xyz.daikirai.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.daikirai.via.OffhandPlacement;

@Mixin(WorldClient.class)
public class MixinWorldClient {
    @Inject(method = "invalidateRegionAndSetBlock", at = @At("HEAD"))
    private void onyx$acceptOffhandBlockUpdate(BlockPos position, IBlockState state, CallbackInfoReturnable<Boolean> cir) {
        OffhandPlacement.authoritativeUpdate((WorldClient) (Object) this, position);
    }
}
