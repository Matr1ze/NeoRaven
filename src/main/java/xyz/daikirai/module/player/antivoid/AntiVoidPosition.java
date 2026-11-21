package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.player.AntiVoidModule;

/**
 * Rise {@code PositionAntiVoid}.
 *
 * <p>Adds the current fall distance to the Y that is reported this tick, so the server keeps
 * seeing the player around the height he started falling from instead of sinking into the void.
 */
public final class AntiVoidPosition extends AntiVoidMode {
    public AntiVoidPosition(AntiVoidModule module) {
        super(module, 5.0, 0.0, 10.0, 1.0);
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        if (mc.thePlayer == null) {
            return;
        }
        if (mc.thePlayer.fallDistance > dist() && !groundBelow()) {
            e.y += mc.thePlayer.fallDistance;
        }
    }
}
