package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.player.AntiVoidModule;

/**
 * Rise {@code CollisionAntiVoid}.
 *
 * <p>Uses the module's server-side fall-distance counter ({@code FallDistanceComponent.cY}
 * upstream). Once it passes {@code Distance} the vertical motion is clamped so the next
 * {@code moveEntity} lands exactly on the floor of the current block, and a zero motion reports
 * ground contact to the server.
 */
public final class AntiVoidCollision extends AntiVoidMode {
    public AntiVoidCollision(AntiVoidModule module) {
        super(module, 5.0, 0.0, 10.0, 1.0);
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        if (mc.thePlayer == null) {
            return;
        }
        if (AntiVoidModule.serverFall > (float) ((int) dist()) && !groundBelow()
                && mc.thePlayer.posY + mc.thePlayer.motionY < Math.floor(mc.thePlayer.posY)) {
            mc.thePlayer.motionY = Math.floor(mc.thePlayer.posY) - mc.thePlayer.posY;
            if (mc.thePlayer.motionY == 0.0) {
                mc.thePlayer.onGround = true;
                e.onGround = true;
            }
        }
    }
}
