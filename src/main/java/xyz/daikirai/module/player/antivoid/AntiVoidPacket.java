package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.player.AntiVoidModule;
import xyz.daikirai.util.PacketUtils;
import net.minecraft.network.play.client.C03PacketPlayer;

/**
 * Rise {@code PacketAntiVoid}.
 *
 * <p>While the player is falling further than {@code Distance} and there is no ground within 10
 * blocks, an extra "position" packet is queued every tick. Upstream builds it with the no-argument
 * {@link C03PacketPlayer.C04PacketPlayerPosition} constructor, which sets {@code moving = true}
 * while leaving x/y/z at zero; that is kept byte-for-byte so the packet layout matches upstream.
 * The packet goes out through {@code sendNoEvent} so our own packet pipeline (Lag Range / Fake Lag
 * / Blink queue / module send hooks) cannot re-queue or cancel it.
 */
public final class AntiVoidPacket extends AntiVoidMode {
    public AntiVoidPacket(AntiVoidModule module) {
        super(module, 5.0, 0.0, 10.0, 1.0);
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        if (mc.thePlayer == null) {
            return;
        }
        if (mc.thePlayer.fallDistance > dist() && !groundBelow()) {
            PacketUtils.sendNoEvent(new C03PacketPlayer.C04PacketPlayerPosition());
        }
    }
}
