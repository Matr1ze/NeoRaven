package xyz.daikirai.via;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import xyz.daikirai.mixin.IC02PacketUseEntity;

/**
 * Keeps the legacy interact-at cursor inside the target's real bounding box.
 *
 * <p>The 1.8 client raycasts against player boxes expanded by 0.1, so right clicking another
 * player reports an offset up to 0.1 outside the 0.6 x 1.8 box, for example z = 0.4 instead of
 * 0.3. A 1.8 server accounts for that expansion, but once Via reports a 1.20.5+ client the server
 * applies the modern rule instead, and GrimAC's InvalidInteractCursor rejects anything past
 * 0.3001 horizontally or 1.8001 vertically. Modern clients hit the unexpanded box, so the legacy
 * offset is projected back onto it before the packet leaves.
 */
public final class ViaInteractCursor {
    private ViaInteractCursor() {
    }

    /** Projects the outgoing use-entity cursor onto the target box when the server is 1.20.5+. */
    public static void clamp(C02PacketUseEntity packet) {
        Minecraft mc = Minecraft.getMinecraft();
        Vec3 offset = packet.getHitVec();
        if (packet.getAction() != C02PacketUseEntity.Action.INTERACT_AT || offset == null || mc.theWorld == null
                || mc.thePlayer == null || mc.getNetHandler() == null
                || !ViaCompatibility.usesMovement1206(mc.getNetHandler().getNetworkManager())) {
            return;
        }
        Entity target = packet.getEntityFromWorld(mc.theWorld);
        if (target == null) {
            return;
        }
        Vec3 clamped = cursor(target, offset);
        if (clamped != offset) {
            ((IC02PacketUseEntity) packet).setHitVec(clamped);
        }
    }

    /** Converts a world-space point on {@code target} into the offset a modern client reports. */
    public static Vec3 offset(Entity target, Vec3 hit) {
        return hit == null ? null
                : cursor(target, new Vec3(hit.xCoord - target.posX, hit.yCoord - target.posY, hit.zCoord - target.posZ));
    }

    private static Vec3 cursor(Entity target, Vec3 offset) {
        // The server checks players only; other entities keep the legacy offset untouched.
        if (!(target instanceof EntityPlayer)) {
            return offset;
        }
        AxisAlignedBB box = target.getEntityBoundingBox();
        double x = clamp(offset.xCoord, box.minX - target.posX, box.maxX - target.posX);
        double y = clamp(offset.yCoord, box.minY - target.posY, box.maxY - target.posY);
        double z = clamp(offset.zCoord, box.minZ - target.posZ, box.maxZ - target.posZ);
        return x == offset.xCoord && y == offset.yCoord && z == offset.zCoord ? offset : new Vec3(x, y, z);
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }
}
