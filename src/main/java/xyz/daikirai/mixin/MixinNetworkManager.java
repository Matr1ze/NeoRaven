package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetworkManager.class)
public class MixinNetworkManager {
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
        Hooks.onReceive(packet, handler);
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void clickgui$send(Packet packet, CallbackInfo ci) {
        if (!Hooks.onSend(packet)) {
            ci.cancel();
        }
    }
}
