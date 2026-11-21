package xyz.daikirai.mixin;

import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import xyz.daikirai.via.PositionCorrection;
import xyz.daikirai.via.ViaTeleportPacket;

@Mixin({S08PacketPlayerPosLook.class, C06PacketPlayerPosLook.class})
public class MixinPlayerPositionCorrection implements ViaTeleportPacket {
    @Unique private PositionCorrection onyx$correction;

    @Override
    public void onyx$setCorrection(PositionCorrection correction) {
        onyx$correction = correction;
    }

    @Override
    public PositionCorrection onyx$getCorrection() {
        return onyx$correction;
    }
}
