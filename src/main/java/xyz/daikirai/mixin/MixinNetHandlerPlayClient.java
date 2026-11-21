package xyz.daikirai.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.module.player.ChestAuraModule;
import xyz.daikirai.module.player.ChestStealerModule;
import xyz.daikirai.via.ViaTeleportPacket;
import xyz.daikirai.via.ViaOffhand;

@Mixin(NetHandlerPlayClient.class)
public final class MixinNetHandlerPlayClient {
    @Inject(method = {"handleJoinGame", "handleRespawn"}, at = @At("RETURN"))
    private void onyx$enableOffhand(CallbackInfo ci) {
        ViaOffhand.enableInventory(Minecraft.getMinecraft().thePlayer);
    }

    @Redirect(method = "handlePlayerPosLook", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkManager;sendPacket(Lnet/minecraft/network/Packet;)V"))
    private void onyx$sendPositionReply(NetworkManager network, Packet<?> reply, S08PacketPlayerPosLook packet) {
        ((ViaTeleportPacket) reply).onyx$setCorrection(((ViaTeleportPacket) packet).onyx$getCorrection());
        network.sendPacket(reply);
    }

    // PacketThreadUtil exits the network-thread call before RETURN; this is the applied client state.
    @Inject(method = "handleWindowItems", at = @At("RETURN"), require = 1)
    private void onyx$chestContents(S30PacketWindowItems packet, CallbackInfo ci) {
        ChestAuraModule.contentsReceived(packet.func_148911_c());
        ChestStealerModule.contentsReceived(packet.func_148911_c());
    }

    @Inject(method = "handleSetSlot", at = @At("RETURN"), require = 1)
    private void onyx$chestSlot(S2FPacketSetSlot packet, CallbackInfo ci) {
        ChestStealerModule.slotReceived(packet.func_149175_c(), packet.func_149173_d(), packet.func_149174_e());
    }

    @Inject(method = "handleConfirmTransaction", at = @At("RETURN"), require = 1)
    private void onyx$chestConfirmation(S32PacketConfirmTransaction packet, CallbackInfo ci) {
        ChestStealerModule.confirmationReceived(packet.getWindowId());
    }
}
