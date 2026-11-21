package xyz.daikirai.via;

import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.packet.ServerboundPacketType;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

import java.util.function.Consumer;
import java.util.logging.Level;

public final class ViaOffhand {
    public static final short SLOT = 45;
    private static final int OFF_HAND = 1;
    private static final KeyBinding SWAP = new KeyBinding("key.swapHands", Keyboard.KEY_NONE, "key.categories.gameplay");
    private static Item posingSword;
    private static int posingSwordSlot;

    private ViaOffhand() {
    }

    static void registerKeys() {
        ClientRegistry.registerKeyBinding(SWAP);
    }

    public static void enableInventory(EntityPlayerSP player) {
        if (player != null && connection(player) != null) {
            ((ViaOffhandContainer) player.inventoryContainer).onyx$enableOffhand();
        }
    }

    public static ItemStack stack(EntityPlayer player) {
        return player instanceof EntityPlayerSP && connection((EntityPlayerSP) player) != null
                && player.inventoryContainer.inventorySlots.size() > SLOT
                ? player.inventoryContainer.getSlot(SLOT).getStack() : null;
    }

    public static void pollSwap(Minecraft mc) {
        if (posingSword != null && (!holdingPosingSword(mc.thePlayer)
                || !mc.gameSettings.keyBindUseItem.isKeyDown() || mc.thePlayer.isUsingItem()
                || mc.playerController.getIsHittingBlock() || mc.playerController.isSpectator())) {
            clearSwordPose();
        }
        while (SWAP.isPressed()) {
            if (mc.currentScreen == null && mc.thePlayer != null && !mc.playerController.isSpectator()) {
                send(mc.thePlayer, ServerboundPackets1_9.PLAYER_ACTION, packet -> {
                    packet.write(Types.VAR_INT, 6);
                    packet.write(Types.BLOCK_POSITION1_8, new BlockPosition(0, 0, 0));
                    packet.write(Types.UNSIGNED_BYTE, (short) 0);
                });
            }
        }
    }

    public static boolean useFallback(Minecraft mc) {
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || player.isUsingItem() || mc.playerController.isSpectator()
                || mc.playerController.getIsHittingBlock()) {
            return false;
        }
        ItemStack held = stack(player);
        if (held == null || held.getItem() instanceof ItemSword) {
            return false;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        boolean blockTarget = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && hit.sideHit != null && !player.worldObj.isAirBlock(hit.getBlockPos());
        boolean entityTarget = hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && hit.entityHit != null;
        if (held.getItem() instanceof ItemBlock && !entityTarget) {
            if (!blockTarget) {
                return false;
            }
            if (hasLocalSwordPose(player, player.getHeldItem())
                    && !((ItemBlock) held.getItem()).canPlaceBlockOnSide(
                            mc.theWorld, hit.getBlockPos(), hit.sideHit, player, held)) {
                return false;
            }
        }
        EnumAction action = held.getItemUseAction();
        boolean food = held.getItem() instanceof ItemFood && action == EnumAction.EAT;
        if (food) {
            // ItemFood checks hunger and the always-edible flag before starting use.
            held.useItemRightClick(player.worldObj, player);
            if (player.getItemInUse() != held) {
                return false;
            }
        }
        if (blockTarget && action != EnumAction.EAT && action != EnumAction.DRINK) {
            BlockPos pos = hit.getBlockPos();
            float hitX = (float) (hit.hitVec.xCoord - pos.getX());
            float hitY = (float) (hit.hitVec.yCoord - pos.getY());
            float hitZ = (float) (hit.hitVec.zCoord - pos.getZ());
            WorldClient world = mc.theWorld;
            boolean inside = pos.equals(new BlockPos(player.posX, player.posY + player.getEyeHeight(), player.posZ));
            OffhandPlacement prediction = OffhandPlacement.predict(world, player, held, pos, hit.sideHit, hitX, hitY, hitZ);
            send(player, ServerboundPackets1_20_5.USE_ITEM_ON, packet -> {
                packet.write(Types.VAR_INT, OFF_HAND);
                packet.write(Types.BLOCK_POSITION1_14, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
                packet.write(Types.VAR_INT, hit.sideHit.getIndex());
                packet.write(Types.FLOAT, hitX);
                packet.write(Types.FLOAT, hitY);
                packet.write(Types.FLOAT, hitZ);
                packet.write(Types.BOOLEAN, inside);
                int sequence = ViaPacketFixes.nextBlockActionSequence(packet.user());
                packet.write(Types.VAR_INT, sequence);
                if (prediction != null) {
                    ViaPacketFixes.onBlockActionAcknowledged(packet.user(), sequence, () -> prediction.acknowledged(world));
                }
            });
        } else if (entityTarget && action != EnumAction.EAT && action != EnumAction.DRINK) {
            Entity target = hit.entityHit;
            int entityId = target.getEntityId();
            Vec3 point = ViaInteractCursor.offset(target, hit.hitVec);
            send(player, ServerboundPackets1_9.INTERACT, packet -> {
                packet.write(Types.VAR_INT, entityId);
                packet.write(Types.VAR_INT, 2);
                packet.write(Types.FLOAT, (float) point.xCoord);
                packet.write(Types.FLOAT, (float) point.yCoord);
                packet.write(Types.FLOAT, (float) point.zCoord);
                packet.write(Types.VAR_INT, OFF_HAND);
            });
            send(player, ServerboundPackets1_9.INTERACT, packet -> {
                packet.write(Types.VAR_INT, entityId);
                packet.write(Types.VAR_INT, 0);
                packet.write(Types.VAR_INT, OFF_HAND);
            });
        }
        if ((!blockTarget && !entityTarget) || action != EnumAction.NONE) {
            send(player, ServerboundPackets1_9.USE_ITEM, packet -> packet.write(Types.VAR_INT, OFF_HAND));
        }
        if (!food && action != EnumAction.NONE) {
            held.useItemRightClick(player.worldObj, player);
        }
        return true;
    }

    public static boolean supports(EntityPlayerSP player) {
        return connection(player) != null;
    }

    public static boolean hasLocalSwordPose(EntityPlayer player, ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword && player instanceof EntityPlayerSP
                && supports((EntityPlayerSP) player);
    }

    public static void beginSwordPose(EntityPlayerSP player, ItemStack stack) {
        posingSword = stack.getItem();
        posingSwordSlot = player.inventory.currentItem;
    }

    public static void clearSwordPose() {
        posingSword = null;
    }

    public static boolean isSwordPosing(EntityPlayerSP player, ItemStack stack) {
        Minecraft mc = Minecraft.getMinecraft();
        // ItemRenderer can keep an older, equivalent stack after the server refreshes the hotbar.
        return stack != null && stack.getItem() == posingSword && holdingPosingSword(player)
                && !player.isUsingItem() && mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown()
                && !mc.playerController.getIsHittingBlock() && !mc.playerController.isSpectator()
                && hasLocalSwordPose(player, stack);
    }

    private static boolean holdingPosingSword(EntityPlayerSP player) {
        ItemStack held = player == null ? null : player.getHeldItem();
        return held != null && held.getItem() == posingSword && player.inventory.currentItem == posingSwordSlot;
    }

    private static UserConnection connection(EntityPlayerSP player) {
        NetworkManager network = player.sendQueue.getNetworkManager();
        return ViaCompatibility.usesMovement1206(network) ? ViaCompatibility.connection(network.channel()) : null;
    }

    private static void send(EntityPlayerSP player, ServerboundPacketType packetType, Consumer<PacketWrapper> fields) {
        UserConnection connection = connection(player);
        if (connection == null) {
            return;
        }
        connection.getChannel().eventLoop().execute(() -> {
            if (!connection.getChannel().isActive() || connection.getProtocolInfo().getServerState() != State.PLAY) {
                return;
            }
            PacketWrapper packet = PacketWrapper.create(packetType, connection);
            fields.accept(packet);
            try {
                if (packetType instanceof ServerboundPackets1_20_5) {
                    packet.sendToServer(Protocol1_20_5To1_20_3.class);
                } else {
                    packet.sendToServer(Protocol1_9To1_8.class);
                }
            } catch (Exception e) {
                Via.getPlatform().getLogger().log(Level.WARNING, "Could not translate offhand " + packetType, e);
            }
        });
    }
}
