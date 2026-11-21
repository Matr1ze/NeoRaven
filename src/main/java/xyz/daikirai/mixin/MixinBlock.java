package xyz.daikirai.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.player.antivoid.AntiVoidVulcan;

import java.util.List;

/**
 * Rise's {@code BlockAABBEvent} hook, used by AntiVoid's {@code Vulcan} mode: every air block under
 * a void-falling player gets a large collision slab so the client "lands" on the void floor. Only
 * the local player's own collision query is touched; every other entity (and every other module)
 * sees vanilla boxes.
 */
@Mixin(Block.class)
public abstract class MixinBlock {
    @Inject(method = "addCollisionBoxesToList", at = @At("HEAD"), cancellable = true)
    private void clickgui$antiVoidVulcanFloor(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask,
                                              List<AxisAlignedBB> list, Entity entity, CallbackInfo ci) {
        if (entity == null || entity != Minecraft.getMinecraft().thePlayer) {
            return;
        }
        if (AntiVoidVulcan.mixinCollision(state, pos, mask, list)) {
            ci.cancel();
        }
    }
}
