package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.player.AntiVoidModule;
import xyz.daikirai.util.MoveUtils;
import xyz.daikirai.util.PacketQueue;
import xyz.daikirai.util.PacketUtils;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;

/**
 * Rise {@code WatchdogAntiVoid}.
 *
 * <p>Every tick the movement is projected forward ({@code Distance * 40} ticks) and the column
 * underneath each projected position is scanned: when all of them are air, the player counts as
 * "over void". While that is the case the outgoing packet stream is queued (blink) so the server
 * never sees the fall, and once the tracked server fall distance passes {@code Distance} a single
 * position packet is dropped in at {@code lastSafeY + 0.098} and the queued packets are discarded,
 * then normal traffic resumes.
 *
 * <p>Upstream conditions that break the loop (Fly, any Long Jump, a compass in slot 0, Scaffold,
 * landing on ground, or being past the safe window) are all kept, except the two Long Jump mode
 * names which do not exist in this client; here "Long Jump enabled" already resets the state.
 */
public final class AntiVoidWatchdog extends AntiVoidMode {
    private static final double LAST_SAFE_Y_OFFSET = 0.09800000190735147;

    private final double[] lastSafePosition = new double[3];
    private final double[] lastSafeMotion = new double[3];
    private boolean hasSafePosition;
    private boolean blinking;
    private boolean positionSent;
    private int voidTicks;
    private boolean moving;

    public AntiVoidWatchdog(AntiVoidModule module) {
        super(module, 3.0, 0.0, 10.0, 0.1);
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.ticksExisted <= 75) {
            return;
        }
        if (ModuleManager.enabled("Fly") != null) {
            release();
            return;
        }

        this.moving = Math.abs(PlayerUtils.getForward()) >= 0.8F || Math.abs(PlayerUtils.getStrafe()) >= 0.8F;

        boolean overVoid = false;
        int limit = (int) (dist() * 40.0);
        for (int i = 0; i <= limit; i++) {
            double[] projected = project(i);
            BlockPos pos = new BlockPos(projected[0], mc.thePlayer.posY - i, projected[1]);
            if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.air || mc.thePlayer.onGround) {
                overVoid = false;
                break;
            }
            overVoid = true;
        }

        if (overVoid) {
            this.voidTicks++;
        } else if (mc.thePlayer.onGround) {
            this.voidTicks = 0;
        }

        boolean safe = !overVoid
                || !this.hasSafePosition
                || !(this.voidTicks < 30.0 + dist() * 30.0)
                || ModuleManager.enabled("Long Jump") != null
                || ModuleManager.enabled("Fly") != null
                || (mc.thePlayer.inventory.getStackInSlot(0) != null
                    && mc.thePlayer.inventory.getStackInSlot(0).getItem() == Items.compass)
                || ModuleManager.enabled("Scaffold") != null;

        if (safe) {
            this.positionSent = false;
            release();
            this.lastSafeMotion[0] = mc.thePlayer.motionX;
            this.lastSafeMotion[1] = mc.thePlayer.motionY;
            this.lastSafeMotion[2] = mc.thePlayer.motionZ;
            this.lastSafePosition[0] = mc.thePlayer.posX;
            this.lastSafePosition[1] = mc.thePlayer.posY;
            this.lastSafePosition[2] = mc.thePlayer.posZ;
            this.hasSafePosition = true;
        } else if (!this.positionSent) {
            if (!this.blinking) {
                this.blinking = true;
                PacketQueue.open();
            }
            if (AntiVoidModule.serverFall > dist()) {
                PacketUtils.sendNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                        this.lastSafePosition[0],
                        this.lastSafePosition[1] + LAST_SAFE_Y_OFFSET,
                        this.lastSafePosition[2],
                        false));
                PacketQueue.drop();
                release();
                AntiVoidModule.serverFall = 0.0F;
                this.positionSent = true;
            }
        }
    }

    @Override
    public void onDisable() {
        release();
        this.hasSafePosition = false;
        this.positionSent = false;
        this.voidTicks = 0;
        this.moving = false;
    }

    public boolean isBlinking() {
        return this.blinking;
    }

    private void release() {
        if (this.blinking) {
            this.blinking = false;
            PacketQueue.close();
        }
    }

    /**
     * Rise {@code MoveUtil.a(player, input, ticks, true)}: walk the vanilla-ish horizontal motion
     * forward, decaying the carried motion by {@code 0.546} per tick and adding a {@code 0.02}
     * movement step in the input direction.
     */
    private double[] project(int ticks) {
        double dx = mc.thePlayer.posX - mc.thePlayer.lastTickPosX;
        double dz = mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ;
        double x = mc.thePlayer.posX;
        double z = mc.thePlayer.posZ;
        for (int i = 0; i <= ticks; i++) {
            dx *= 0.54600006F;
            dz *= 0.54600006F;
            double[] step = inputStep();
            x += dx + step[0];
            z += dz + step[1];
        }
        return new double[]{x, z};
    }

    private double[] inputStep() {
        float forward = PlayerUtils.getForward();
        float strafe = PlayerUtils.getStrafe();
        if (forward == 0.0F && strafe == 0.0F) {
            return new double[]{0.0, 0.0};
        }
        double yaw = Math.toRadians(MoveUtils.moveYaw(RotationManager.movementYaw(), forward, strafe));
        double speed = 0.02;
        return new double[]{-Math.sin(yaw) * speed, Math.cos(yaw) * speed};
    }
}
