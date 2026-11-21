package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.module.player.AntiVoidModule;

/**
 * Rise {@code BlinkAntiVoid}.
 *
 * <p>Remembers the last position/motion/rotation that had ground within 50 blocks, and snaps the
 * player back to it (client side, motion zeroed except for the predicted Y) as soon as the tracked
 * server fall distance passes 4 blocks.
 */
public final class AntiVoidBlink extends AntiVoidMode {
    private final double[] position = new double[3];
    private final double[] motion = new double[3];
    private final float[] rotation = new float[2];
    private boolean hasSample;

    public AntiVoidBlink(AntiVoidModule module) {
        super(module, -1.0, 0.0, 0.0, 1.0);
    }

    @Override
    public void onPostMotion() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.thePlayer.ticksExisted <= 60) {
            return;
        }
        if (!this.hasSample || groundWithin(50.0)) {
            sample();
            return;
        }
        if (AntiVoidModule.serverFall > 4.0F) {
            mc.thePlayer.setPosition(this.position[0], this.position[1], this.position[2]);
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = (this.motion[1] - 0.08) * 0.98F;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.rotationYaw = this.rotation[0];
            mc.thePlayer.rotationPitch = this.rotation[1];
            AntiVoidModule.serverFall = 0.0F;
        }
    }

    @Override
    public void onDisable() {
        this.hasSample = false;
    }

    private void sample() {
        this.position[0] = mc.thePlayer.posX;
        this.position[1] = mc.thePlayer.posY;
        this.position[2] = mc.thePlayer.posZ;
        this.motion[0] = mc.thePlayer.motionX;
        this.motion[1] = mc.thePlayer.motionY;
        this.motion[2] = mc.thePlayer.motionZ;
        this.rotation[0] = mc.thePlayer.rotationYaw;
        this.rotation[1] = mc.thePlayer.rotationPitch;
        this.hasSample = true;
    }
}
