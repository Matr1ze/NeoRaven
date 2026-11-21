package xyz.daikirai.via;

import com.viaversion.viabackwards.ViaBackwardsPlatformImpl;
import com.viaversion.viarewind.ViaRewindPlatformImpl;
import com.viaversion.viarewind.protocol.v1_9to1_8.storage.PlayerPositionTracker;
import com.viaversion.viaversion.ViaManagerImpl;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.commands.ViaCommandHandler;
import com.viaversion.viaversion.configuration.AbstractViaConfig;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform;
import com.viaversion.viaversion.platform.ViaChannelInitializer;
import com.viaversion.viaversion.platform.ViaDecodeHandler;
import com.viaversion.viaversion.platform.ViaEncodeHandler;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocol.version.BaseVersionProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.MovementTransmitterProvider;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.util.AttributeKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C03PacketPlayer.C06PacketPlayerPosLook;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xyz.daikirai.gui.onyx.menu.ViaProtocolScreen;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ViaCompatibility {
    private static final Logger LOG = LogManager.getLogger("NeoRaven/Via");
    private static final ProtocolVersion NATIVE = ProtocolVersion.v1_8;
    private static final AttributeKey<ProtocolVersion> CHANNEL_TARGET = AttributeKey.valueOf("neoraven:via-target");
    private static final AttributeKey<UserConnection> CHANNEL_CONNECTION = AttributeKey.valueOf("neoraven:via-connection");
    private static final AttributeKey<ArrayDeque<PositionCorrection>> CORRECTIONS = AttributeKey.valueOf("neoraven:position-corrections");
    private static volatile ProtocolVersion target = NATIVE;
    private static List<ProtocolVersion> versions = Collections.emptyList();
    private static Path targetFile;

    private ViaCompatibility() {
    }

    public static void initialize(File gameDirectory) {
        if (Loader.isModLoaded("viaforge")) {
            LOG.warn("ViaForge is installed. Remove its mod jar to use NeoRaven's built-in Via.");
            return;
        }

        File folder = new File(gameDirectory, "ViaMCP");
        targetFile = new File(folder, "target.txt").toPath();
        UserConnectionViaVersionPlatform platform = new ClientPlatform(folder);
        ViaManagerImpl.initAndLoad(platform, new NoopInjector(), new ViaCommandHandler(false), new ClientLoader(),
                ViaBackwardsPlatformImpl::new, ViaRewindPlatformImpl::new);
        ViaPacketFixes.installPacketFixes();
        ViaOffhand.registerKeys();

        List<ProtocolVersion> supported = new ArrayList<>();
        for (ProtocolVersion version : ProtocolVersion.getReversedProtocols()) {
            if (version.newerThan(NATIVE) && Via.getManager().getProtocolManager().getProtocolPath(NATIVE, version) != null) {
                supported.add(version);
            }
        }
        supported.add(0, NATIVE);
        versions = Collections.unmodifiableList(supported);
        target = readTarget();
        MinecraftForge.EVENT_BUS.register(new ViaMenuButton());
        LOG.info("Built-in Via ready. Target {}, {} protocol choices", target.getName(), versions.size());
    }

    public static boolean available() {
        return !versions.isEmpty();
    }

    public static List<ProtocolVersion> versions() {
        return versions;
    }

    public static ProtocolVersion target() {
        return target;
    }

    public static String name(ProtocolVersion version) {
        return version.equalTo(NATIVE) ? "1.8.9" : version.getName();
    }

    public static String protocolLabel() {
        return "Protocol " + name(target);
    }

    public static void openSelector(GuiScreen parent) {
        Minecraft.getMinecraft().displayGuiScreen(new ViaProtocolScreen(parent));
    }

    public static void select(ProtocolVersion version) throws IOException {
        if (version.equalTo(target)) {
            return;
        }
        Path temporary = Files.createTempFile(targetFile.getParent(), "target-", ".tmp");
        try {
            Files.write(temporary, Collections.singletonList(Integer.toString(version.getVersion())), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            target = version;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static ProtocolVersion readTarget() {
        try {
            if (Files.isRegularFile(targetFile)) {
                String saved = new String(Files.readAllBytes(targetFile), StandardCharsets.UTF_8).trim();
                ProtocolVersion version = ProtocolVersion.getProtocol(Integer.parseInt(saved));
                if (versions.contains(version)) {
                    return version;
                }
            }
        } catch (IOException | NumberFormatException e) {
            LOG.warn("Unable to read the saved Via target", e);
        }
        return NATIVE;
    }

    public static void attach(Channel channel) {
        ProtocolVersion selected = target;
        ChannelPipeline pipeline = channel.pipeline();
        if (selected.equalTo(NATIVE) || pipeline.get("encoder") == null || pipeline.get("decoder") == null) {
            return;
        }
        channel.attr(CHANNEL_TARGET).set(selected);
        UserConnection connection = new UserConnectionImpl(channel, true);
        channel.attr(CHANNEL_CONNECTION).set(connection);
        new ProtocolPipelineImpl(connection);
        pipeline.addBefore("decoder", "via-decoder", new ViaDecodeHandler(connection));
        pipeline.addBefore("encoder", "via-encoder", new ViaEncodeHandler(connection));
        if (selected == ProtocolVersion.v1_20_5) {
            pipeline.addAfter("encoder", "neoraven-teleport-confirm", new ChannelOutboundHandlerAdapter() {
                @Override
                public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof C06PacketPlayerPosLook) {
                        PositionCorrection correction = ((ViaTeleportPacket) message).onyx$getCorrection();
                        if (correction != null) {
                            ViaPacketFixes.confirmPosition(connection, correction.id);
                            message = correction.protocolReply((C06PacketPlayerPosLook) message);
                        }
                    }
                    context.write(message, promise);
                }
            });
        }
    }

    public static void reorderAfterCompression(Channel channel) {
        if (channel.attr(CHANNEL_TARGET).get() != null) {
            ViaChannelInitializer.reorderPipeline(channel.pipeline(), "compress", "decompress");
        }
    }

    public static boolean usesMovement1206(NetworkManager network) {
        return network != null && network.channel() != null
                && network.channel().attr(CHANNEL_TARGET).get() == ProtocolVersion.v1_20_5;
    }

    public static void flushAttackSwing(NetworkManager network) {
        Channel channel = network.channel();
        if (channel == null) {
            return;
        }
        UserConnection via = connection(channel);
        ProtocolVersion protocol = channel.attr(CHANNEL_TARGET).get();
        if (via == null || protocol == null || protocol.olderThan(ProtocolVersion.v1_9)) {
            return;
        }
        // ViaRewind buffers the legacy swing until movement. Drain it on the same event loop,
        // after the attack write and before the next game packet, as on a modern client.
        channel.eventLoop().execute(() -> {
            PlayerPositionTracker tracker = via.get(PlayerPositionTracker.class);
            if (channel.isActive() && tracker != null) {
                tracker.sendAnimations();
            }
        });
    }

    public static void playPlacementSound(World world, double x, double y, double z, String sound, float volume, float pitch) {
        if (world instanceof WorldClient) {
            EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
            if (player != null && player.worldObj == world && usesMovement1206(player.sendQueue.getNetworkManager())) {
                // The modern server excludes the placing player. The legacy world effect is silent on that client.
                ((WorldClient) world).playSound(x, y, z, sound, volume, pitch, false);
                return;
            }
        }
        world.playSoundEffect(x, y, z, sound, volume, pitch);
    }

    static UserConnection connection(Channel channel) {
        return channel.attr(CHANNEL_CONNECTION).get();
    }

    static void rememberCorrection(Channel channel, PositionCorrection correction) {
        // Both translation and legacy packet delivery use this channel's event loop.
        ArrayDeque<PositionCorrection> pending = channel.attr(CORRECTIONS).get();
        if (pending == null) {
            pending = new ArrayDeque<>();
            channel.attr(CORRECTIONS).set(pending);
        }
        pending.addLast(correction);
    }

    public static S08PacketPlayerPosLook restorePositionCorrection(Channel channel, S08PacketPlayerPosLook packet) {
        ArrayDeque<PositionCorrection> pending = channel.attr(CORRECTIONS).get();
        PositionCorrection correction = pending == null ? null : pending.pollFirst();
        return correction == null ? packet : correction.legacyPacket();
    }

    private static final class ClientPlatform extends UserConnectionViaVersionPlatform {
        ClientPlatform(File folder) {
            super(folder);
        }

        @Override
        protected AbstractViaConfig createConfig() {
            return new AbstractViaConfig(new File(getDataFolder(), "viaversion.yml"), getLogger()) {
                @Override
                public boolean isCheckForUpdates() {
                    return false;
                }
            };
        }

        @Override
        public java.util.logging.Logger createLogger(String name) {
            return java.util.logging.Logger.getLogger("NeoRaven." + name);
        }

        @Override
        public String getPlatformName() {
            return "NeoRaven";
        }

        @Override
        public String getPlatformVersion() {
            return "1.8.9";
        }
    }

    private static final class ClientLoader implements ViaPlatformLoader {
        @Override
        public void load() {
            Via.getManager().getProviders().use(VersionProvider.class, new BaseVersionProvider() {
                @Override
                public ProtocolVersion getClosestServerProtocol(UserConnection connection) throws Exception {
                    if (connection.isClientSide()) {
                        ProtocolVersion selected = connection.getChannel().attr(CHANNEL_TARGET).get();
                        return selected == null ? NATIVE : selected;
                    }
                    return super.getClosestServerProtocol(connection);
                }
            });
            Via.getManager().getProviders().use(MovementTransmitterProvider.class, new MovementTransmitterProvider() {
                @Override
                public void sendPlayer(UserConnection connection) {
                    // Minecraft already sends its own movement updates.
                }
            });
        }

        @Override
        public void unload() {
        }
    }
}
