package xyz.daikirai.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import xyz.daikirai.client.ServerSrv;

/**
 * SRV for the "host + port" constructor of {@link GuiConnecting}.
 *
 * <p>The vanilla server list goes through {@code new GuiConnecting(parent, mc, ServerData)} which parses
 * its address with {@code ServerAddress.fromString} - that one is already covered by
 * {@code MixinServerAddress}. The direct-connect field, the {@code --server} launch argument and anything
 * else that hands over a raw host string use the {@code (String, int)} constructor instead, which skips
 * the parser entirely and dials the domain's web IP on 25565. That is the path that keeps failing for
 * SRV-published servers (e.g. {@code hypixel.today} -> {@code fks1mon.sakuraorg.com:25567}) while the
 * server list works.</p>
 *
 * <p>Only a bare host on the default port is rewritten; an address the player typed with {@code :port}
 * keeps exactly the port they asked for.</p>
 */
@Mixin(GuiConnecting.class)
public class MixinGuiConnectingSrv {

    @Shadow
    private void connect(String ip, int port) {
    }

    @Redirect(method = "<init>(Lnet/minecraft/client/gui/GuiScreen;Lnet/minecraft/client/Minecraft;Ljava/lang/String;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/GuiConnecting;connect(Ljava/lang/String;I)V"))
    private void onyx$srv(GuiConnecting screen, String host, int port) {
        String[] srv = ServerSrv.resolve(host, port);
        if (srv == null) {
            this.connect(host, port);
        } else {
            this.connect(srv[0], Integer.parseInt(srv[1]));
        }
    }
}
