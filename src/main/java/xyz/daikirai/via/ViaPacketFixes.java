package xyz.daikirai.via;

import com.viaversion.viabackwards.protocol.v1_17_1to1_17.Protocol1_17_1To1_17;
import com.viaversion.viabackwards.protocol.v1_17_1to1_17.storage.InventoryStateIds;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viarewind.protocol.v1_9to1_8.storage.PlayerPositionTracker;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.exception.InformativeException;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ClientboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_16_4to1_17.packet.ServerboundPackets1_17;
import com.viaversion.viaversion.protocols.v1_17to1_17_1.packet.ClientboundPackets1_17_1;
import com.viaversion.viaversion.protocols.v1_20_2to1_20_3.packet.ClientboundPackets1_20_3;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ClientboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ClientboundPackets1_9;
import net.minecraft.client.Minecraft;

import java.util.NavigableMap;
import java.util.TreeMap;

final class ViaPacketFixes {
    private ViaPacketFixes() {
    }

    static void installPacketFixes() {
        preserveContainerRevisions();
        addBlockActionSequences();
        orderPlayPongReplies();
        preservePositionCorrections();
        preserveOffhandSlotUpdates();
        deliverBlockActionAcknowledgements();
    }

    private static void preserveContainerRevisions() {
        Protocol1_17_1To1_17 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_17_1To1_17.class);
        // The last revision stays current until the server updates it, including across rapid clicks.
        // Keep all revisions keyed by the unsigned container ID used by CONTAINER_SET_CONTENT.
        protocol.registerClientbound(ClientboundPackets1_17_1.CONTAINER_SET_SLOT, ClientboundPackets1_17.CONTAINER_SET_SLOT, wrapper -> {
            byte containerId = wrapper.passthrough(Types.BYTE);
            int stateId = wrapper.read(Types.VAR_INT);
            if (containerId != -1 && containerId != -2) {
                wrapper.user().get(InventoryStateIds.class).setStateId(Byte.toUnsignedInt(containerId), stateId);
            }
        }, true);
        protocol.registerServerbound(ServerboundPackets1_17.CONTAINER_CLICK, ServerboundPackets1_17.CONTAINER_CLICK, wrapper -> {
            int containerId = Byte.toUnsignedInt(wrapper.passthrough(Types.BYTE));
            InventoryStateIds states = wrapper.user().get(InventoryStateIds.class);
            int stateId = states.removeStateId(containerId);
            if (stateId != Integer.MAX_VALUE) {
                states.setStateId(containerId, stateId);
            }
            wrapper.write(Types.VAR_INT, stateId == Integer.MAX_VALUE ? 0 : stateId);
        }, true);
        protocol.registerServerbound(ServerboundPackets1_17.CONTAINER_CLOSE, ServerboundPackets1_17.CONTAINER_CLOSE, wrapper -> {
            int containerId = Byte.toUnsignedInt(wrapper.passthrough(Types.BYTE));
            wrapper.user().get(InventoryStateIds.class).removeStateId(containerId);
        }, true);
    }

    private static void addBlockActionSequences() {
        Protocol1_19To1_18_2 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_19To1_18_2.class);
        // This bridge creates the sequence field before newer protocols change IDs or layouts.
        protocol.appendServerbound(ServerboundPackets1_17.USE_ITEM, wrapper -> writeSequence(wrapper, 1));
        protocol.appendServerbound(ServerboundPackets1_17.USE_ITEM_ON, wrapper -> writeSequence(wrapper, 2));
        protocol.appendServerbound(ServerboundPackets1_17.PLAYER_ACTION, wrapper -> {
            int action = wrapper.get(Types.VAR_INT, 0);
            if (action >= 0 && action <= 2) {
                writeSequence(wrapper, 1);
            }
        });
    }

    private static void writeSequence(PacketWrapper wrapper, int index) throws InformativeException {
        wrapper.set(Types.VAR_INT, index, nextBlockActionSequence(wrapper.user()));
    }

    static int nextBlockActionSequence(UserConnection connection) {
        BlockActionSequence sequence = connection.get(BlockActionSequence.class);
        if (sequence == null) {
            sequence = new BlockActionSequence();
            connection.put(sequence);
        }
        return sequence.next();
    }

    private static void orderPlayPongReplies() {
        Protocol1_20_5To1_20_3 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_20_5To1_20_3.class);
        protocol.registerClientbound(ClientboundPackets1_20_5.PING, ClientboundPackets1_20_3.PING, wrapper -> {
            UserConnection connection = wrapper.user();
            if (connection.getProtocolInfo().serverProtocolVersion() != ProtocolVersion.v1_20_5) {
                return;
            }
            int id = wrapper.read(Types.INT);
            wrapper.cancel();
            // Vanilla handles play pings on the game thread, in order with position corrections.
            // Return to the channel's event loop after earlier game-thread packet handlers enqueue their replies.
            Minecraft.getMinecraft().addScheduledTask(() -> connection.getChannel().eventLoop().execute(() -> {
                if (!connection.getChannel().isActive() || connection.getProtocolInfo().getServerState() != State.PLAY) {
                    return;
                }
                PacketWrapper pong = PacketWrapper.create(ServerboundPackets1_20_5.PONG, connection);
                pong.write(Types.INT, id);
                try {
                    pong.sendToServer(Protocol1_20_5To1_20_3.class);
                } catch (Exception e) {
                    Via.getPlatform().getLogger().log(java.util.logging.Level.WARNING, "Could not send play pong", e);
                }
            }));
        }, true);
    }

    private static void preservePositionCorrections() {
        Protocol1_20_5To1_20_3 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_20_5To1_20_3.class);
        protocol.registerClientbound(ClientboundPackets1_20_5.PLAYER_POSITION, ClientboundPackets1_20_3.PLAYER_POSITION, wrapper -> {
            UserConnection connection = wrapper.user();
            if (connection.getProtocolInfo().serverProtocolVersion() != ProtocolVersion.v1_20_5) {
                return;
            }
            double x = wrapper.passthrough(Types.DOUBLE);
            double y = wrapper.passthrough(Types.DOUBLE);
            double z = wrapper.passthrough(Types.DOUBLE);
            float yaw = wrapper.passthrough(Types.FLOAT);
            float pitch = wrapper.passthrough(Types.FLOAT);
            byte flags = wrapper.passthrough(Types.BYTE);
            int id = wrapper.passthrough(Types.VAR_INT);
            ViaCompatibility.rememberCorrection(connection.getChannel(), new PositionCorrection(x, y, z, yaw, pitch, flags, id));
        }, true);
        Protocol1_9To1_8 rewind = Via.getManager().getProtocolManager().getProtocol(Protocol1_9To1_8.class);
        rewind.appendClientbound(ClientboundPackets1_9.PLAYER_POSITION, wrapper -> {
            if (wrapper.user().getProtocolInfo().serverProtocolVersion() == ProtocolVersion.v1_20_5) {
                // We confirm the particular correction applied by the game, even if another arrives before it runs.
                wrapper.user().get(PlayerPositionTracker.class).setConfirmId(-1);
            }
        });
    }

    static void confirmPosition(UserConnection connection, int id) throws Exception {
        PacketWrapper confirm = PacketWrapper.create(ServerboundPackets1_20_5.ACCEPT_TELEPORTATION, connection);
        confirm.write(Types.VAR_INT, id);
        confirm.sendToServer(Protocol1_20_5To1_20_3.class);
    }

    private static void preserveOffhandSlotUpdates() {
        Protocol1_9To1_8 rewind = Via.getManager().getProtocolManager().getProtocol(Protocol1_9To1_8.class);
        rewind.appendClientbound(ClientboundPackets1_9.CONTAINER_SET_SLOT, wrapper -> {
            if (wrapper.user().getProtocolInfo().serverProtocolVersion() == ProtocolVersion.v1_20_5
                    && wrapper.get(Types.BYTE, 0) == 0 && wrapper.get(Types.SHORT, 0) == ViaOffhand.SLOT) {
                wrapper.setCancelled(false);
            }
        });
    }

    static void onBlockActionAcknowledged(UserConnection connection, int sequence, Runnable callback) {
        connection.get(BlockActionSequence.class).pending.put(sequence, callback);
    }

    private static void deliverBlockActionAcknowledgements() {
        Protocol1_20_5To1_20_3 protocol = Via.getManager().getProtocolManager().getProtocol(Protocol1_20_5To1_20_3.class);
        protocol.registerClientbound(ClientboundPackets1_20_5.BLOCK_CHANGED_ACK, ClientboundPackets1_20_3.BLOCK_CHANGED_ACK,
                wrapper -> {
                    int confirmed = wrapper.passthrough(Types.VAR_INT);
                    BlockActionSequence sequence = wrapper.user().get(BlockActionSequence.class);
                    if (sequence != null) {
                        while (!sequence.pending.isEmpty() && sequence.pending.firstKey() <= confirmed) {
                            Runnable callback = sequence.pending.pollFirstEntry().getValue();
                            Minecraft.getMinecraft().addScheduledTask(callback);
                        }
                    }
                }, true);
    }

    private static final class BlockActionSequence implements StorableObject {
        private final NavigableMap<Integer, Runnable> pending = new TreeMap<>();
        private int sequence;

        int next() {
            sequence = sequence == Integer.MAX_VALUE ? 1 : sequence + 1;
            return sequence;
        }
    }
}
