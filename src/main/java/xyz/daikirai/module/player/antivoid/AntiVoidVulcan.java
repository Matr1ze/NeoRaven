package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.player.AntiVoidModule;
import xyz.daikirai.util.MoveUtils;
import xyz.daikirai.util.PlayerUtils;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

import java.util.List;

/**
 * Rise {@code VulcanAntiVoid}.
 *
 * <p>Same state machine as upstream: while the player is falling with nothing below, every air
 * block underneath him gets a huge collision slab (so the client physically "lands" on the void
 * floor), the movement input is forced to +1 / -1 forward every other tick, {@code Speed} is
 * toggled off for the duration and jumping is suppressed. Any of the following ends the state:
 * non-air collision, sneaking, horizontal collision, {@code S08PacketPlayerPosLook}, a world
 * change, or an active Fly / Long Jump.
 *
 * <p>The block slabs are added from {@code MixinBlock}, which calls {@link #mixinCollision}.
 */
public final class AntiVoidVulcan extends AntiVoidMode {
    private static AntiVoidVulcan instance;

    private boolean voidFalling;
    private boolean toggledSpeed;
    private WorldClient lastWorld;

    public AntiVoidVulcan(AntiVoidModule module) {
        super(module, 2.6, 0.0, 10.0, 0.1);
        instance = this;
    }

    // ------------------------------------------------------------------ //
    // State machine
    // ------------------------------------------------------------------ //

    @Override
    public void onPreUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        if (this.lastWorld != mc.theWorld) {
            this.lastWorld = mc.theWorld;
            reset();
        }
        if (ModuleManager.enabled("Fly") != null || ModuleManager.enabled("Long Jump") != null) {
            this.voidFalling = false;
        }
        if (mc.thePlayer.fallDistance > dist() && !groundBelow()) {
            this.voidFalling = true;
        }
        Module speed = ModuleManager.get("Speed");
        if (speed != null) {
            if (speed.isEnabled() && this.voidFalling) {
                this.toggledSpeed = true;
                speed.disable();
            }
            if (!this.voidFalling && !speed.isEnabled() && this.toggledSpeed) {
                speed.enable();
                this.toggledSpeed = false;
            }
        }
    }

    @Override
    public void onPostPlayerInput() {
        if (!this.voidFalling || mc.thePlayer == null) {
            return;
        }
        // Rise: MoveUtil.strafe(0.1) + StrafeEvent.setForward(...)
        MoveUtils.strafe(0.1);
        if (mc.thePlayer.ticksExisted % 2 != 1 && PlayerUtils.getForward() == 0.0F) {
            MoveUtils.strafe(0.0);
            PlayerUtils.setForward(-1.0F);
        } else {
            PlayerUtils.setForward(1.0F);
        }
        // Rise: JumpEvent.setJumpMotion(0)
        mc.thePlayer.movementInput.jump = false;
    }

    @Override
    public void onReceive(Packet<?> packet) {
        if (packet instanceof S08PacketPlayerPosLook) {
            this.voidFalling = false;
        }
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        this.voidFalling = false;
        if (this.toggledSpeed) {
            this.toggledSpeed = false;
            Module speed = ModuleManager.get("Speed");
            if (speed != null && !speed.isEnabled()) {
                speed.enable();
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Block collision (MixinBlock)
    // ------------------------------------------------------------------ //

    /**
     * Called from {@code MixinBlock} for every block whose collision list is built for the local
     * player. Returns {@code true} when the vanilla handling must be skipped because the void-floor
     * slab was added.
     */
    public static boolean mixinCollision(IBlockState state, BlockPos pos, AxisAlignedBB mask, List<AxisAlignedBB> list) {
        AntiVoidVulcan mode = instance;
        if (mode == null) {
            return false;
        }
        return mode.onBlock(state, pos, mask, list);
    }

    private boolean onBlock(IBlockState state, BlockPos pos, AxisAlignedBB mask, List<AxisAlignedBB> list) {
        if (!this.module.isEnabled() || this.module.activeIndex() != AntiVoidModule.VULCAN || mc.thePlayer == null) {
            return false;
        }
        boolean air = state.getBlock() instanceof BlockAir;
        if (air) {
            if (this.voidFalling && !mc.thePlayer.isSneaking() && pos.getY() < mc.thePlayer.posY) {
                AxisAlignedBB box = new AxisAlignedBB(-15.0, -1.0, -15.0, 15.0, 1.0, 15.0)
                        .offset(pos.getX(), pos.getY(), pos.getZ());
                if (mask.intersectsWith(box)) {
                    list.add(box);
                }
                return true;
            }
            return false;
        }
        if (this.voidFalling && !mc.thePlayer.isCollidedHorizontally) {
            this.voidFalling = false;
        }
        return false;
    }
}
