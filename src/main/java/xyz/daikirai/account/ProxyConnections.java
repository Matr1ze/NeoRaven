package xyz.daikirai.account;

import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import net.minecraft.network.*;
import net.minecraft.util.MessageDeserializer;
import net.minecraft.util.MessageDeserializer2;
import net.minecraft.util.MessageSerializer;
import net.minecraft.util.MessageSerializer2;
import java.net.*;
import java.io.IOException;

public final class ProxyConnections {
    private static final OioEventLoopGroup GROUP = new OioEventLoopGroup(0, new DefaultThreadFactory("Raven proxy", true));
    private ProxyConnections() { }

    public static NetworkManager connect(InetAddress address, int port) {
        ProxyConfig config = AccountManager.instance().proxy();
        Socket socket = null;
        try {
            socket = ProxyTunnel.open(config, address.getHostAddress(), port);
            NetworkManager manager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
            OioSocketChannel channel = new OioSocketChannel(socket);
            channel.pipeline().addLast("timeout", new ReadTimeoutHandler(30))
                    .addLast("splitter", new MessageDeserializer2())
                    .addLast("decoder", new MessageDeserializer(EnumPacketDirection.CLIENTBOUND))
                    .addLast("prepender", new MessageSerializer2())
                    .addLast("encoder", new MessageSerializer(EnumPacketDirection.SERVERBOUND))
                    .addLast("packet_handler", manager);
            GROUP.register(channel).syncUninterruptibly();
            return manager;
        } catch (IOException | RuntimeException e) {
            if (socket != null) try { socket.close(); } catch (IOException ignored) { }
            throw new IllegalStateException("Proxy connection failed: " + e.getMessage(), e);
        }
    }
}
