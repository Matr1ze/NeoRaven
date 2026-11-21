package xyz.daikirai.module.player;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.PacketQueue;
import xyz.daikirai.util.PacketUtils;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationSource;
import xyz.daikirai.util.RotationUtils;

/** OpenExpo ChestAura's selection/open/wait cycle, using this client's rotation arbitration. */
public final class ChestAuraModule extends Module {
    public static ChestAuraModule instance;
    private SliderSetting range, playerRange, moveFix;
    private ButtonSetting throughWalls, rotation;
    private final Set<BlockPos> opened = new HashSet<BlockPos>();
    private World world;
    private EntityPlayerSP owner;
    private BlockPos target, pending;
    private EnumFacing face;
    private Vec3 hit;
    private ContainerChest lastContainer;
    private long selectedAt, pendingAt, closedAt;

    public ChestAuraModule(int key) { super("ChestAura", Category.player, key); instance = this; }
    @Override public void link() {
        range = (SliderSetting) ModuleManager.setting(this, "Range");
        playerRange = (SliderSetting) ModuleManager.setting(this, "Player check range");
        moveFix = (SliderSetting) ModuleManager.setting(this, "Move-fix");
        throughWalls = (ButtonSetting) ModuleManager.setting(this, "Through walls");
        rotation = (ButtonSetting) ModuleManager.setting(this, "Rotation");
    }
    private void clearTarget() { target = null; face = null; hit = null; selectedAt = 0; }
    private void clearPending() { clearTarget(); pending = null; pendingAt = 0; lastContainer = null; }
    private void sync() {
        if (world != mc.theWorld || owner != mc.thePlayer) {
            world = mc.theWorld; owner = mc.thePlayer; opened.clear(); clearPending(); closedAt = System.currentTimeMillis();
        }
    }
    @Override public void onEnable() { sync(); clearPending(); closedAt = System.currentTimeMillis(); }
    @Override public void onDisable() { clearPending(); }
    @Override public String getInfo() { return range.getDisplay(false); }
    public int movementMode() { return (int) Math.round(moveFix.getInput()); }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world == world) { opened.clear(); clearPending(); world = null; owner = null; }
    }
    /** Observe accepted block-use packets, including physical clicks and controller-based clicks. */
    public static void interacted(BlockPos pos) {
        ChestAuraModule aura = instance;
        if (aura == null || !aura.isEnabled() || pos == null || mc.theWorld == null) return;
        if (!mc.isCallingFromMinecraftThread()) {
            final World sentWorld = mc.theWorld; final BlockPos copy = new BlockPos(pos);
            mc.addScheduledTask(() -> { if (mc.theWorld == sentWorld) interacted(copy); });
            return;
        }
        if (mc.theWorld.getBlockState(pos).getBlock() instanceof BlockChest) {
            aura.sync(); aura.pending = new BlockPos(pos); aura.pendingAt = System.currentTimeMillis(); aura.clearTarget();
        }
    }
    /** Confirm before Instant closes the window inside the same S30 callback. */
    public static void contentsReceived(int id) {
        ChestAuraModule aura = instance;
        if (aura == null || !aura.isEnabled() || mc.thePlayer == null
                || !(mc.thePlayer.openContainer instanceof ContainerChest) || mc.thePlayer.openContainer.windowId != id) return;
        aura.sync(); aura.confirmOpen((ContainerChest) mc.thePlayer.openContainer);
    }
    private void confirmOpen(ContainerChest container) {
        if (pending != null) mark(pending);
        pending = null; pendingAt = 0; clearTarget(); lastContainer = container;
    }
    private void mark(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof BlockChest)) return;
        opened.add(new BlockPos(pos));
        // Only matching chest types can form a double chest.
        for (EnumFacing side : EnumFacing.HORIZONTALS) {
            BlockPos adjacent = pos.offset(side);
            if (world.getBlockState(adjacent).getBlock() == block) opened.add(adjacent);
        }
    }
    private boolean markedEmpty(BlockPos pos) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX()-1, pos.getY()+.8, pos.getZ()-1,
                pos.getX()+2, pos.getY()+1.5, pos.getZ()+2);
        for (EntityArmorStand stand : world.getEntitiesWithinAABB(EntityArmorStand.class, box)) {
            if (!stand.hasCustomName()) continue;
            String name = stand.getDisplayName().getUnformattedText().toLowerCase(Locale.ROOT);
            if (name.contains("empty") || name.contains(":")) return true;
        }
        return false;
    }
    private boolean playersNear() {
        double distance = playerRange.getInput();
        if (distance <= 0) return false;
        for (EntityPlayer player : world.playerEntities)
            if (player != mc.thePlayer && !player.isDead && player.isEntityAlive() && !player.isSpectator()
                    && mc.thePlayer.getDistanceSqToEntity(player) <= distance*distance) return true;
        return false;
    }
    private boolean usable(BlockPos pos) {
        if (pos == null || opened.contains(pos) || mc.thePlayer.getDistanceSqToCenter(pos) > range.getInput()*range.getInput()) return false;
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof BlockChest) || ((BlockChest) block).getLockableContainer(world, pos) == null || markedEmpty(pos)) return false;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntityChest) || ((TileEntityChest) tile).numPlayersUsing > 0) return false;
        Vec3 eyes = mc.thePlayer.getPositionEyes(1), center = new Vec3(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
        MovingObjectPosition trace = world.rayTraceBlocks(eyes, center, false, true, false);
        if (trace != null && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && pos.equals(trace.getBlockPos())) return true;
        return throughWalls.isToggled();
    }
    private void aim(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1), center = new Vec3(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
        MovingObjectPosition trace = world.rayTraceBlocks(eyes, center, false, true, false);
        if (trace != null && pos.equals(trace.getBlockPos())) { face = trace.sideHit; hit = trace.hitVec; }
        else {
            face = EnumFacing.getFacingFromVector((float)(eyes.xCoord-center.xCoord), (float)(eyes.yCoord-center.yCoord), (float)(eyes.zCoord-center.zCoord));
            hit = BlockUtils.hitVec(pos, face);
        }
    }
    @Override public void onPreUpdate() {
        sync();
        if (world == null || owner == null || owner.isDead || !owner.isEntityAlive() || owner.isSpectator()) { clearPending(); return; }
        long now = System.currentTimeMillis();
        if (owner.openContainer instanceof ContainerChest) { confirmOpen((ContainerChest) owner.openContainer); return; }
        if (lastContainer != null) { lastContainer = null; closedAt = now; }
        if (pending != null) {
            if (now-pendingAt > 1200) { pending = null; pendingAt = 0; }
            clearTarget(); return;
        }
        if (mc.currentScreen != null || PacketQueue.active || !PacketUtils.noInteraction()
                || owner.isUsingItem() || playersNear() || now-closedAt <= 100) { clearTarget(); return; }
        BlockPos nearest = null; double best = Double.MAX_VALUE;
        for (TileEntity tile : world.loadedTileEntityList) {
            if (!(tile instanceof TileEntityChest)) continue;
            BlockPos pos = tile.getPos();
            if (opened.contains(pos)) continue;
            if (((TileEntityChest) tile).numPlayersUsing > 0 || markedEmpty(pos)) { mark(pos); continue; }
            if (owner.getDistanceSqToCenter(pos) > range.getInput()*range.getInput()) continue;
            if (!usable(pos)) continue;
            double distance = owner.getDistanceSqToCenter(pos);
            if (distance < best) { best = distance; nearest = pos; }
        }
        if (nearest == null) { clearTarget(); return; }
        if (!nearest.equals(target)) { target = new BlockPos(nearest); selectedAt = now; }
        aim(target);
    }
    @Override public float[] getRotations(float yaw, float pitch) {
        if (!isEnabled() || !rotation.isToggled() || target == null || pending != null || mc.currentScreen != null) return null;
        return RotationUtils.toBlockFixed(target, face, yaw, pitch);
    }
    @Override public void onPostMotion() {
        if (target == null || mc.theWorld != world || owner != mc.thePlayer || mc.currentScreen != null
                || PacketQueue.active || !PacketUtils.noInteraction() || owner.isUsingItem() || playersNear()
                || System.currentTimeMillis()-selectedAt < 80 || !usable(target)) return;
        if (RotationManager.rotating && RotationManager.source != RotationSource.CHESTAURA) return;
        if (rotation.isToggled() && (!RotationManager.rotating || RotationManager.source != RotationSource.CHESTAURA)) return;
        BlockPos opening = target;
        if (mc.playerController.onPlayerRightClick(owner, mc.theWorld, owner.getHeldItem(), opening, face, hit)) {
            pending = opening; pendingAt = System.currentTimeMillis(); clearTarget(); owner.swingItem();
        } else clearTarget();
    }
}
