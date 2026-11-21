package xyz.daikirai.mixin;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.EnumPacketDirection;
import xyz.daikirai.hook.Hooks;
import xyz.daikirai.via.ViaCompatibility;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
    @Shadow private Channel channel;
    @Shadow @Final private EnumPacketDirection direction;

    @Inject(method = "channelActive", at = @At("RETURN"))
    private void onyx$attachVia(ChannelHandlerContext context, CallbackInfo ci) {
        if (direction == EnumPacketDirection.CLIENTBOUND) {
            ViaCompatibility.attach(context.channel());
        }
    }

    @Inject(method = "setCompressionTreshold", at = @At("RETURN"))
    private void onyx$reorderVia(int threshold, CallbackInfo ci) {
        ViaCompatibility.reorderAfterCompression(channel);
    }

    @Inject(method = "createNetworkManagerAndConnect", at = @At("HEAD"), cancellable = true)
    private static void onyx$proxy(java.net.InetAddress address, int port, boolean nativeTransport,
                                   CallbackInfoReturnable<NetworkManager> ci) {
        if (xyz.daikirai.account.AccountManager.instance().connectionProxy().enabled) {
            ci.setReturnValue(xyz.daikirai.account.ProxyConnections.connect(address, port));
        }
    }

    @Redirect(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Packet;processPacket(Lnet/minecraft/network/INetHandler;)V"))
    @SuppressWarnings("rawtypes")
    private void clickgui$receive(Packet packet, INetHandler handler) {
        if (direction == EnumPacketDirection.CLIENTBOUND && packet instanceof S08PacketPlayerPosLook) {
            packet = ViaCompatibility.restorePositionCorrection(channel, (S08PacketPlayerPosLook) packet);
        }
        Hooks.onReceive(packet, handler);
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void clickgui$send(Packet packet, CallbackInfo ci) {
        if (!Hooks.onSend(packet)) {
            ci.cancel();
        }
    }

}
