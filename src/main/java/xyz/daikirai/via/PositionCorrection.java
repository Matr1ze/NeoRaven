package xyz.daikirai.via;

import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S08PacketPlayerPosLook.EnumFlags;

import java.util.Set;

public final class PositionCorrection {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final Set<EnumFlags> flags;
    final int id;

    PositionCorrection(double x, double y, double z, float yaw, float pitch, byte flags, int id) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.flags = EnumFlags.func_180053_a(flags);
        this.id = id;
    }

    S08PacketPlayerPosLook legacyPacket() {
        // The legacy game handler also understands relative corrections. Resolve them against its live pose.
        S08PacketPlayerPosLook packet = new S08PacketPlayerPosLook(x, y, z, yaw, pitch, flags);
        ((ViaTeleportPacket) packet).onyx$setCorrection(this);
        return packet;
    }

    C06PacketPlayerPosLook protocolReply(C06PacketPlayerPosLook reply) {
        // The legacy player wraps its angles. Preserve the server's absolute angles in the position reply.
        float replyYaw = flags.contains(EnumFlags.Y_ROT) ? reply.getYaw() : yaw;
        float replyPitch = flags.contains(EnumFlags.X_ROT) ? reply.getPitch() : pitch;
        return new C06PacketPlayerPosLook(reply.getPositionX(), reply.getPositionY(), reply.getPositionZ(),
                replyYaw, replyPitch, reply.isOnGround());
    }
}
