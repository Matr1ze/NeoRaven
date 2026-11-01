package xyz.daikirai.module.player;

import xyz.daikirai.client.Chat;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.module.render.WatermarkModule;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MathUtils;
import xyz.daikirai.util.PacketUtils;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;

/**
 * Port of OpenSSNG's {@code Scaffold} (ScaffoldAndClutch) to NeoRaven.
 *
 * <p>Kept as a second scaffold so the Raven B4 {@code Scaffold} stays untouched: this one owns the
 * "Telly" placement rhythm (jump, hold the silent down-rotation for a few ticks, place on the way
 * down), the Snap/Normal drop modes and the fall prediction that turns a knock-back into a placed
 * block under the player's predicted path (the "clutch" part of the upstream module).</p>
 *
 * <p>1.8.9 differences: no offhand slot, so blocks come from the hotbar; the "Spoof item" and
 * "Block fly" switches of the upstream module relied on packet-order tricks that this client does
 * not have, and the held-item/FOV hooks live in the upstream event bus, so those settings are not
 * ported.</p>
 */
public final class Scaffold2Module extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_TELLY = 0;
    private static final int MODE_SNAP = 1;
    private static final int MODE_NORMAL = 2;

    private static final int JUMP_PARKOUR = 0;
    private static final int JUMP_NORMAL = 1;
    private static final int JUMP_NONE = 2;

    private static final int SLOT_FARTHEST = 0;
    private static final int SLOT_MOST = 1;

    private static final int STYLE_RETRO = 0;
    private static final int STYLE_OLD = 1;

    public static Scaffold2Module instance;

    /** Settings of the SSNG implementation; the visible list depends on the Client setting. */
    private final java.util.List<Setting> ssngSettings = new java.util.ArrayList<Setting>();

    private final SliderSetting modeSetting = mode("Mode", MODE_TELLY, "Telly", "Snap", "Normal");
    private final ButtonSetting alwaysUpdateRot = flag("Always update rotation", false);
    private final SliderSetting placeTick = slider("Place tick", 1, 1, 5, 1, "");
    private final SliderSetting rotTick = slider("Rotation tick", 1, 1, 5, 1, "");
    private final ButtonSetting noSwing = flag("No swing", false);
    private final ButtonSetting eagle = flag("Eagle", false);
    private final ButtonSetting snap = flag("Snap", false);
    private final ButtonSetting noUptelly = flag("No uptelly", true);
    private final ButtonSetting smoothed = flag("Heypixel uptelly", true);
    private final ButtonSetting safeMode = flag("Safe mode", false);
    private final ButtonSetting testOnGround = flag("Test on ground", false);
    private final ButtonSetting fixRotation = flag("Fix rotation", true);
    private final ButtonSetting slowUpTelly = flag("Slow uptelly", false);
    private final SliderSetting jumpMode = mode("Jump mode", JUMP_NORMAL, "Parkour", "Normal", "None");
    private final SliderSetting blockSlotMode = mode("Block slot mode", SLOT_FARTHEST, "Farthest", "Most blocks");
    private final SliderSetting safeDistance = slider("Clutch safe distance", 4.5, 1, 5, 0.25, "");
    private final ButtonSetting debug = flag("Debug", false);
    private final ButtonSetting mark = flag("Mark", true);
    private final ColorSetting markSide = color("Mark side color", 0xFF7C4DFF, 0.0f);
    private final ColorSetting markLine = color("Mark line color", 0xFF7C4DFF, 0.0f);
    private final ButtonSetting blockCount = flag("Block count", true);
    private final SliderSetting blockCountStyle = mode("Block count style", STYLE_RETRO, "Retro", "Old");
    private final SliderSetting blockCountOffset = slider("Block count Y offset", 0, 0, 200, 1, "");

    private int blockSlot = -1;
    private int oldSlot = -1;
    private boolean active;
    private int onGroundTicks;
    private int offGroundTicks;
    private int posY;
    private BlockData blockData;
    private BlockData lastBlockData;
    private float[] lastRotation;
    private float[] pendingRot;
    private int rotateCount;
    private int placedCount;
    private int ups;
    private float lastPlaceYaw = Float.NaN;
    private float lastPlacePitch = Float.NaN;
    private BlockPos lastPlacedPos;
    private int count;
    private int startHotbarCount = 1;
    private String countText;
    private BlockPos cachedPos;
    private EnumFacing cachedFacing;
    private float[] cachedFaceRot;
    private boolean sneakForced;
    private boolean pendingForceRotation;
    private boolean canPlace;

    /** Settings of the SSNG implementation; the visible list depends on the Client setting. */
    private final SliderSetting clientSetting = new SliderSetting("Client", "", "", 0, 0, 2, 1,
            new String[]{"Rise", "SSNG", "Moonlight"}, null);
    private Scaffold2Moonlight moon;
    private Scaffold2Rise rise;
    private int activeClient = -1;

    private static final int CLIENT_RISE = 0;
    private static final int CLIENT_SSNG = 1;
    private static final int CLIENT_MOONLIGHT = 2;

    public Scaffold2Module(int key) {
        super("Scaffold2", Category.player, key);
        instance = this;
        moon = new Scaffold2Moonlight(this);
        rise = new Scaffold2Rise(this);
        rebuildSettings();
        activeClient = CLIENT_RISE;
    }

    // ------------------------------------------------------------------ //
    // Client switching
    // ------------------------------------------------------------------ //

    @Override
    public ArrayList<Setting> getSettings() {
        syncClient();
        return super.getSettings();
    }

    private void rebuildSettings() {
        settings.clear();
        settings.add(clientSetting);
        if (clientIndex() == CLIENT_RISE) {
            settings.addAll(rise.settings());
        } else if (clientIndex() == CLIENT_MOONLIGHT) {
            settings.addAll(moon.settings());
        } else {
            settings.addAll(ssngSettings);
        }
    }

    /** Called on every settings read (GUI) and every tick, so both paths see the same list. */
    private void syncClient() {
        int wanted = clientIndex();
        if (wanted == activeClient) {
            return;
        }
        stopClient(activeClient);
        activeClient = wanted;
        rebuildSettings();
        if (isEnabled()) {
            startClient(wanted);
        }
    }

    private void stopClient(int client) {
        if (client == CLIENT_MOONLIGHT) {
            moon.onDisableState();
        } else if (client == CLIENT_RISE) {
            rise.onDisable();
        } else {
            stop();
            resetSneak();
        }
    }

    private void startClient(int client) {
        if (client == CLIENT_MOONLIGHT) {
            moon.onEnable();
        } else if (client == CLIENT_RISE) {
            rise.onEnable();
        }
    }

    private int clientIndex() {
        return (int) clientSetting.getInput();
    }

    // ------------------------------------------------------------------ //
    // Settings helpers (code-defined, same style as ArrayList)
    // ------------------------------------------------------------------ //

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        ssngSettings.add(setting);
        return setting;
    }

    private SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        ssngSettings.add(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        ssngSettings.add(setting);
        return setting;
    }

    private ColorSetting color(String name, int value, float hueShift) {
        ColorSetting setting = new ColorSetting(name, value, hueShift);
        ssngSettings.add(setting);
        return setting;
    }

    private int modeIndex() {
        return (int) modeSetting.getInput();
    }

    private int jumpIndex() {
        return (int) jumpMode.getInput();
    }

    private int slotIndex() {
        return (int) blockSlotMode.getInput();
    }

    private int styleIndex() {
        return (int) blockCountStyle.getInput();
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void onEnable() {
        syncClient();
        if (clientIndex() == CLIENT_MOONLIGHT) {
            moon.onEnable();
            return;
        }
        if (clientIndex() == CLIENT_RISE) {
            rise.onEnable();
            return;
        }
        active = false;
        blockSlot = -1;
        oldSlot = mc.thePlayer == null ? -1 : mc.thePlayer.inventory.currentItem;
        blockData = null;
        lastBlockData = null;
        lastRotation = null;
        pendingRot = null;
        cachedPos = null;
        cachedFacing = null;
        cachedFaceRot = null;
        rotateCount = 0;
        placedCount = 0;
        ups = 0;
        lastPlaceYaw = Float.NaN;
        lastPlacePitch = Float.NaN;
        onGroundTicks = mc.thePlayer != null && mc.thePlayer.onGround ? 1 : 0;
        offGroundTicks = 0;
        posY = mc.thePlayer == null ? 0 : MathHelper.floor_double(mc.thePlayer.posY - 1.0);
        startHotbarCount = 1;
        count = 0;
        countText = null;
    }

    @Override
    public void onDisable() {
        moon.onDisableState();
        rise.onDisable();
        stop();
        resetSneak();
        if (mc.thePlayer != null && mc.thePlayer.onGround) {
            mc.thePlayer.motionX *= 0.75;
            mc.thePlayer.motionZ *= 0.75;
        }
        RotationManager.scaffold2NoMovementFix = false;
    }

    @Override
    public boolean isScaffolding() {
        if (clientIndex() == CLIENT_MOONLIGHT) {
            return moon.isActive();
        }
        if (clientIndex() == CLIENT_RISE) {
            return rise.isActive();
        }
        return active;
    }

    @Override
    public String getInfo() {
        if (clientIndex() == CLIENT_MOONLIGHT) {
            int m = (int) moon.clientMode().getInput();
            return "ML " + (m == 0 ? "Normal" : (m == 1 ? "Telly" : "Snap"));
        }
        if (clientIndex() == CLIENT_RISE) {
            int m = (int) rise.modeIndex();
            String[] names = {"Normal", "Godbridge", "Breesily", "Snap", "Telly", "Eagle"};
            return "Rise " + names[Math.max(0, Math.min(names.length - 1, m))];
        }
        if (modeIndex() == MODE_SNAP) {
            return "SSNG Snap";
        }
        if (modeIndex() == MODE_NORMAL) {
            return "SSNG Normal";
        }
        return "SSNG Telly";
    }

    // ------------------------------------------------------------------ //
    // Tick
    // ------------------------------------------------------------------ //

    @Override
    public void onPreUpdate() {
        syncClient();
        if (clientIndex() == CLIENT_MOONLIGHT) {
            RotationManager.scaffold2NoMovementFix = moon.isActive() && moon.movementFixDisabled();
            moon.tick();
            return;
        }
        if (clientIndex() == CLIENT_RISE) {
            RotationManager.scaffold2NoMovementFix = rise.movementFixDisabled();
            rise.tick();
            return;
        }
        RotationManager.scaffold2NoMovementFix = false;
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        if (PacketUtils.sentCloseWindow || Overlay.screenOpen() || PlayerUtils.isAdventure()) {
            stop();
            return;
        }

        if (mc.thePlayer.onGround) {
            ++onGroundTicks;
            offGroundTicks = 0;
        } else {
            ++offGroundTicks;
            onGroundTicks = 0;
        }

        int slot = findBlockSlot();
        if (slot == -1) {
            if (active) {
                stop();
            }
            return;
        }
        blockSlot = slot;
        if (!active) {
            // Capture the user's slot before switching to the block hotbar slot.
            begin();
        }
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
            PlayerUtils.setSlot(blockSlot, true);
        }

        updateCount();
        if (modeIndex() == MODE_TELLY) {
            jumpTick();
        }
        gather();
        pendingRot = computeRotation(pendingForceRotation);
        if (!PacketUtils.noUseOrDig()) {
            return;
        }
        place();
    }

    @Override
    public void onPostPlayerInput() {
        if (clientIndex() == CLIENT_MOONLIGHT) {
            moon.postInput();
            return;
        }
        if (clientIndex() == CLIENT_RISE) {
            rise.postInput();
            return;
        }
        if (!active || !eagle.isToggled() || modeIndex() != MODE_TELLY) {
            if (sneakForced) {
                mc.thePlayer.movementInput.sneak = false;
                sneakForced = false;
            }
            return;
        }
        // Upstream sneaks on every fourth placement; the count only moves when a block is placed,
        // so the sneak is held for the whole of that block and released for the next three.
        boolean want = mc.gameSettings.keyBindSneak.isKeyDown() || placedCount % 4 == 0;
        if (want) {
            mc.thePlayer.movementInput.sneak = true;
            sneakForced = true;
        } else if (sneakForced) {
            mc.thePlayer.movementInput.sneak = false;
            sneakForced = false;
        }
    }

    private void begin() {
        active = true;
        oldSlot = mc.thePlayer.inventory.currentItem;
        posY = MathHelper.floor_double(mc.thePlayer.posY - 1.0);
        startHotbarCount = Math.max(1, countHotbarBlocks());
        sneakForced = false;
    }

    private void stop() {
        if (active) {
            active = false;
            resetSneak();
            if (mc.thePlayer != null && oldSlot != -1 && mc.thePlayer.inventory.currentItem != oldSlot) {
                PlayerUtils.setSlot(oldSlot, true);
            }
        }
        blockSlot = -1;
        blockData = null;
        lastBlockData = null;
        pendingRot = null;
        lastRotation = null;
        rotateCount = 0;
        posY = mc.thePlayer == null ? 0 : MathHelper.floor_double(mc.thePlayer.posY - 1.0);
    }

    private void reset() {
        active = false;
        blockSlot = -1;
        blockData = null;
        lastBlockData = null;
        pendingRot = null;
        lastRotation = null;
        resetSneak();
    }

    private void resetSneak() {
        if (sneakForced && mc.thePlayer != null) {
            mc.thePlayer.movementInput.sneak = false;
        }
        sneakForced = false;
    }

    // ------------------------------------------------------------------ //
    // Jump / eagle
    // ------------------------------------------------------------------ //

    private void jumpTick() {
        if (!mc.thePlayer.onGround || mc.gameSettings.keyBindJump.isKeyDown() || !PlayerUtils.isMoving()) {
            return;
        }
        int need = smoothed.isToggled() && safeMode.isToggled() && !testOnGround.isToggled() ? 2 : 1;
        if (onGroundTicks < need) {
            return;
        }
        if (jumpIndex() == JUMP_NONE) {
            return;
        }
        if (jumpIndex() == JUMP_PARKOUR) {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            BlockPos first = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX + fx),
                    MathHelper.floor_double(mc.thePlayer.posY - 0.1),
                    MathHelper.floor_double(mc.thePlayer.posZ + fz));
            BlockPos second = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX + fx * 2.0),
                    MathHelper.floor_double(mc.thePlayer.posY - 0.1),
                    MathHelper.floor_double(mc.thePlayer.posZ + fz * 2.0));
            if (isIgnored(first) || isIgnored(second)) {
                PlayerUtils.jump();
            }
            return;
        }
        PlayerUtils.jump();
    }

    // ------------------------------------------------------------------ //
    // Target search
    // ------------------------------------------------------------------ //

    private void gather() {
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround) {
            posY = py - 1;
        }
        if (PlayerUtils.jumpKeyDown()) {
            posY = py - 1;
        }

        BlockPos feet = new BlockPos(px, py, pz);
        BlockData possible = isIgnored(feet) ? getBlockData(new BlockPos(px, posY, pz)) : null;
        if (possible != null) {
            blockData = possible;
        }
        lastBlockData = possible;

        if (modeIndex() == MODE_NORMAL) {
            canPlace = true;
        } else if (modeIndex() == MODE_SNAP) {
            canPlace = isIgnored(new BlockPos(px, py - 1, pz));
        } else {
            canPlace = offGroundTicks >= (int) placeTick.getInput();
            if (safeMode.isToggled() && testOnGround.isToggled() && !canPlace && PlayerUtils.jumpKeyDown()) {
                canPlace = onGroundTicks == 1;
            }
        }

        pendingForceRotation = safeMode.isToggled() && testOnGround.isToggled() && onGroundTicks == 1 && PlayerUtils.jumpKeyDown();

        // ---- clutch: predict the next two ticks of falling and place under the path ----
        Fall fall = new Fall();
        fall.advance();
        Vec3 nextEye = fall.eye();
        fall.advance();
        BlockData rescue = getBlockData(new BlockPos(px, py - 1, pz));
        boolean reachable = true;
        if (rescue != null) {
            Vec3 center = new Vec3(rescue.pos.getX() + 0.5, rescue.pos.getY() + 0.5, rescue.pos.getZ() + 0.5);
            double distance = nextEye.distanceTo(center);
            if (distance >= safeDistance.getInput() || rescue.pos.getY() > fall.y) {
                canPlace = true;
                reachable = false;
                blockData = lastBlockData = rescue;
            }
        }
        if (blockData != null) {
            AxisAlignedBB box = new AxisAlignedBB(
                    blockData.pos.getX(), blockData.pos.getY() - 1, blockData.pos.getZ(),
                    blockData.pos.getX() + 1, blockData.pos.getY() + 1, blockData.pos.getZ() + 1);
            Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
            if (blockData.pos.getY() > fall.y && !box.isVecInside(playerPos)) {
                canPlace = true;
                reachable = false;
                posY = py - 1;
                blockData = lastBlockData = getBlockData(new BlockPos(px, posY, pz));
            }
        }
        if (!reachable && rotateCount < 8) {
            ++rotateCount;
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            if (debug.isToggled() && rotateCount == 1) {
                Chat.print("§7[Scaffold2] §fclutch");
            }
        } else if (reachable) {
            rotateCount = 0;
        }
    }

    private BlockData getBlockData(BlockPos target) {
        if (target == null) {
            return null;
        }
        BlockData direct = getPos(target);
        if (direct != null) {
            return isIgnored(target) ? direct : null;
        }
        BlockPos solid = nearestSolidBelow();
        if (solid == null) {
            return null;
        }
        EnumFacing facing = getPlaceSide(solid);
        if (facing == null) {
            return null;
        }
        BlockPos placedPos = solid.offset(facing);
        if (!isIgnored(placedPos) || placedPos.equals(playerFeet())) {
            return null;
        }
        return new BlockData(solid, facing);
    }

    private BlockData getPos(BlockPos pos) {
        if (isSolid(pos.west())) {
            return new BlockData(pos.west(), EnumFacing.EAST);
        }
        if (isSolid(pos.east())) {
            return new BlockData(pos.east(), EnumFacing.WEST);
        }
        if (isSolid(pos.south())) {
            return new BlockData(pos.south(), EnumFacing.NORTH);
        }
        if (isSolid(pos.north())) {
            return new BlockData(pos.north(), EnumFacing.SOUTH);
        }
        if (isSolid(pos.down())) {
            return new BlockData(pos.down(), EnumFacing.UP);
        }
        return null;
    }

    private BlockPos nearestSolidBelow() {
        int px = MathHelper.floor_double(mc.thePlayer.posX);
        int py = MathHelper.floor_double(mc.thePlayer.posY);
        int pz = MathHelper.floor_double(mc.thePlayer.posZ);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -5; dx < 5; ++dx) {
            for (int dy = -5; dy < 5; ++dy) {
                for (int dz = -5; dz < 5; ++dz) {
                    BlockPos pos = new BlockPos(px + dx, py + dy, pz + dz);
                    if (pos.getY() >= py || !isSolid(pos)) {
                        continue;
                    }
                    double distance = mc.thePlayer.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private EnumFacing getPlaceSide(BlockPos solid) {
        EnumFacing[] sides = {EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST};
        BlockPos feet = playerFeet();
        EnumFacing best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EnumFacing side : sides) {
            BlockPos candidate = solid.offset(side);
            if (!isIgnored(candidate) || candidate.equals(feet)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSq(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = side;
            }
        }
        return best;
    }

    private BlockPos playerFeet() {
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ));
    }

    private static boolean isIgnored(BlockPos pos) {
        if (mc.theWorld == null) {
            return true;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == net.minecraft.init.Blocks.air
                || block == net.minecraft.init.Blocks.fire
                || block.getMaterial().isReplaceable()
                || block instanceof net.minecraft.block.BlockLiquid;
    }

    private static boolean isSolid(BlockPos pos) {
        if (mc.theWorld == null) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block.getMaterial().isSolid() && !isIgnored(pos) && !BlockUtils.isInteractable(block);
    }

    // ------------------------------------------------------------------ //
    // Rotation
    // ------------------------------------------------------------------ //

    private float[] computeRotation(boolean forceRotation) {
        if (blockData == null) {
            return null;
        }
        float serverYaw = lastServerYaw();
        float serverPitch = lastServerPitch();
        float[] face = faceRotation(serverYaw);
        if (face == null) {
            return null;
        }
        float yaw = face[0];
        float pitch = face[1];
        boolean jumping = PlayerUtils.jumpKeyDown();

        if (modeIndex() == MODE_TELLY) {
            if (jumping && noUptelly.isToggled()) {
                return finishRotation(yaw, pitch);
            }
            if (jumping && slowUpTelly.isToggled() && ++ups % 2 == 0) {
                return finishRotation(yaw, pitch);
            }
            double diff = MathHelper.wrapAngleTo180_float(yaw - serverYaw);
            if (smoothed.isToggled() && (offGroundTicks < (int) rotTick.getInput() || safeMode.isToggled())) {
                if (onGroundTicks > 0) {
                    if (safeMode.isToggled() && (!testOnGround.isToggled() || jumping)) {
                        if (onGroundTicks == 1) {
                            if (!forceRotation) {
                                yaw = serverYaw + smooth((float) diff, (float) (diff / 2.0));
                                pitch = 75.5f;
                            } else {
                                pitch = serverPitch;
                            }
                        } else if (onGroundTicks == 2) {
                            yaw = mc.thePlayer.rotationYaw;
                            pitch = 75.5f;
                        }
                    } else {
                        yaw = mc.thePlayer.rotationYaw;
                        pitch = 75.5f;
                    }
                } else {
                    float limit = offGroundTicks == 1 ? 80.0f : 50.0f;
                    limit -= (float) MathUtils.randomBetween(0.001, 0.005);
                    yaw = serverYaw + smooth((float) diff, limit);
                }
            } else if (offGroundTicks < (int) rotTick.getInput()) {
                yaw = mc.thePlayer.rotationYaw;
                pitch = 85.0f + (float) Math.random();
            } else if (snap.isToggled() && jumping && (lastBlockData == null || offGroundTicks < (int) rotTick.getInput())) {
                yaw = mc.thePlayer.rotationYaw;
                pitch = 85.0f + (float) Math.random();
            }
        }

        if (lastRotation != null && lastBlockData != null && hits(lastRotation[0], lastRotation[1])) {
            return lastRotation;
        }
        if (!alwaysUpdateRot.isToggled() && offGroundTicks >= (int) rotTick.getInput() && !hits(yaw, pitch) && lastRotation != null) {
            lastRotation[0] += (float) Math.random();
            return lastRotation;
        }
        lastRotation = new float[]{yaw, pitch};
        return finishRotation(yaw, pitch);
    }

    private float[] finishRotation(float yaw, float pitch) {
        // Upstream's "DuplicateRotPlace": nudge every placement off the previous exact angle.
        pitch -= (float) MathUtils.randomBetween(0.001, 0.003);
        yaw -= (float) MathUtils.randomBetween(0.0001, 0.0003);
        pitch = MathHelper.clamp_float(pitch, -90.0f, 90.0f);
        if (fixRotation.isToggled()) {
            return RotationUtils.fixSensitivity(yaw, pitch, lastServerYaw(), lastServerPitch());
        }
        return new float[]{yaw, pitch};
    }

    private float[] faceRotation(float serverYaw) {
        if (blockData == null) {
            return null;
        }
        if (!blockData.pos.equals(cachedPos) || blockData.facing != cachedFacing) {
            cachedPos = blockData.pos;
            cachedFacing = blockData.facing;
            cachedFaceRot = null;
        }
        if (cachedFaceRot == null) {
            float[] base = RotationUtils.toBlock(blockData.pos, blockData.facing);
            if (base == null) {
                return null;
            }
            if (!hits(base[0], base[1])) {
                float[] searched = RotationUtils.searchBlockRotation(blockData.pos, blockData.facing, -170.0f,
                        RotationUtils.Precision.High, serverYaw, false);
                if (searched != null) {
                    base = searched;
                }
            }
            cachedFaceRot = base;
        }
        return new float[]{cachedFaceRot[0], cachedFaceRot[1]};
    }

    private static float lastServerYaw() {
        float yaw = RotationManager.lastYaw;
        if (yaw == 0.0f && RotationManager.lastPitch == 0.0f) {
            return mc.thePlayer.rotationYaw;
        }
        return yaw;
    }

    private static float lastServerPitch() {
        float pitch = RotationManager.lastPitch;
        if (pitch == 0.0f && RotationManager.lastYaw == 0.0f) {
            return mc.thePlayer.rotationPitch;
        }
        return pitch;
    }

    private static float smooth(float diff, float limit) {
        return MathHelper.clamp_float(diff, -limit, limit);
    }

    private boolean hits(float yaw, float pitch) {
        return blockData != null && PlayerUtils.rayTraceBlock(blockData.pos, blockData.facing, reach(), yaw, pitch) != null;
    }

    private static double reach() {
        return mc.playerController.getBlockReachDistance();
    }

    @Override
    public float[] getRotations(float yaw, float pitch) {
        if (!isEnabled()) {
            return null;
        }
        if (clientIndex() == CLIENT_MOONLIGHT) {
            return moon.serverRotation();
        }
        if (clientIndex() == CLIENT_RISE) {
            return rise.serverRotation();
        }
        if (!active || pendingRot == null) {
            return null;
        }
        return new float[]{pendingRot[0], pendingRot[1]};
    }

    // ------------------------------------------------------------------ //
    // Placement
    // ------------------------------------------------------------------ //

    private void place() {
        if (blockData == null || pendingRot == null || !canPlace || mc.playerController == null) {
            return;
        }
        if (hits(pendingRot[0], pendingRot[1]) == false) {
            return;
        }
        if (!Float.isNaN(lastPlacePitch)
                && Math.abs(pendingRot[1] - lastPlacePitch) < 1.0E-4
                && Math.abs(pendingRot[0] - lastPlaceYaw) < 1.0E-4) {
            return;
        }
        MovingObjectPosition hit = PlayerUtils.rayTraceBlock(blockData.pos, blockData.facing, reach(), pendingRot[0], pendingRot[1]);
        if (hit == null) {
            return;
        }
        BlockPos target = BlockUtils.placedPos(hit);
        if (!BlockUtils.isReplaceable(target)) {
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !ItemUtils.isScaffoldBlock(stack)) {
            return;
        }
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
            PlayerUtils.setSlot(blockSlot, true);
        }
        PlayerUtils.place(hit, stack);
        PlayerUtils.swing(noSwing.isToggled(), true);

        lastPlaceYaw = pendingRot[0];
        lastPlacePitch = pendingRot[1];
        lastPlacedPos = target;
        ++placedCount;
        rotateCount = 0;
        // The target was just filled: re-resolve next tick instead of patching the same face twice.
        blockData = null;
        lastBlockData = null;
        cachedPos = null;
        cachedFacing = null;
        cachedFaceRot = null;
    }

    // ------------------------------------------------------------------ //
    // Block slot / counts
    // ------------------------------------------------------------------ //

    private int findBlockSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem);
        if (slotIndex() == SLOT_FARTHEST && ItemUtils.isScaffoldBlock(held)) {
            return mc.thePlayer.inventory.currentItem;
        }
        int found = -1;
        int bestCount = -1;
        for (int slot = 0; slot <= 8; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!ItemUtils.isScaffoldBlock(stack)) {
                continue;
            }
            if (slotIndex() == SLOT_MOST) {
                if (stack.stackSize > bestCount) {
                    bestCount = stack.stackSize;
                    found = slot;
                }
            } else {
                found = slot;
            }
        }
        return found;
    }

    private int countHotbarBlocks() {
        if (mc.thePlayer == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot <= 8; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (ItemUtils.isScaffoldBlock(stack)) {
                total += stack.stackSize;
            }
        }
        return total;
    }

    private ItemStack displayStack() {
        ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.getHeldItem();
        if (ItemUtils.isScaffoldBlock(held)) {
            return held;
        }
        return null;
    }

    private void updateCount() {
        int blocks = countHotbarBlocks();
        if (blocks > startHotbarCount) {
            startHotbarCount = blocks;
        }
        count = blocks;
        if (blockCount.isToggled()) {
            String color = null;
            if (blocks < 16) {
                color = "§c";
            } else if (blocks < 32) {
                color = "§e";
            }
            countText = color == null ? blocks + " Blocks" : color + blocks + "§f Blocks";
        } else {
            countText = null;
        }
    }

    // ------------------------------------------------------------------ //
    // Rendering (called from RenderDispatcher)
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (clientIndex() == CLIENT_MOONLIGHT) {
            moon.render3d();
            return;
        }
        if (clientIndex() == CLIENT_RISE) {
            rise.render3d();
            return;
        }
        if (!isActiveWithMark() || lastPlacedPos == null) {
            return;
        }
        Render3D.block(lastPlacedPos, RenderUtils.withAlpha(markSide.color(), 70), false, true);
        Render3D.block(lastPlacedPos, RenderUtils.withAlpha(markLine.color(), 150), true, false);
    }

    private boolean isActiveWithMark() {
        return mark.isToggled() && Chat.inWorld();
    }

    /** Hotbar scaffold blocks; the island gauge reads this for every Scaffold2 client. */
    public static int blockCount() {
        Scaffold2Module m = instance;
        return m == null ? 0 : m.countHotbarBlocks();
    }

    /**
     * The island owns the counter while its "Scaffold blocks" gauge is enabled, so the module stops
     * drawing its own centre-screen counter and the two never show up at once.
     */
    public static boolean islandOwnsCount() {
        WatermarkModule watermark = WatermarkModule.instance;
        return watermark != null && watermark.isEnabled() && watermark.scaffoldGauge();
    }

    public static void renderCount() {
        Scaffold2Module m = instance;
        if (m == null || islandOwnsCount()) {
            return;
        }
        if (m.clientIndex() == CLIENT_MOONLIGHT) {
            m.moon.renderCount();
            return;
        }
        if (m.clientIndex() == CLIENT_RISE) {
            return;
        }
        if (!m.blockCount.isToggled() || m.countText == null) {
            return;
        }
        float centerX = RenderUtils.scaledWidth / 2.0f;
        float centerY = RenderUtils.scaledHeight / 2.0f;
        float y = centerY + 15.0f + (float) m.blockCountOffset.getInput();
        int color = m.count < 16 ? 0xFFFF5050 : (m.count < 32 ? 0xFFFFDC50 : 0xFFFFFFFF);
        if (m.styleIndex() == STYLE_OLD) {
            String text = m.count + " Blocks";
            float x = centerX - (float) mc.fontRendererObj.getStringWidth(text) / 2.0f;
            mc.fontRendererObj.drawString(text, x, y, color, true);
            return;
        }
        ItemStack stack = m.displayStack();
        String label = "Blocks";
        String value = String.valueOf(m.count);
        float labelWidth = mc.fontRendererObj.getStringWidth(label);
        float valueWidth = mc.fontRendererObj.getStringWidth(value);
        float pad = 5.0f;
        float itemSize = 18.0f;
        float itemOffset = 4.0f;
        float gap = 3.0f;
        float width = itemOffset + itemSize + pad + labelWidth + gap + valueWidth + pad;
        float height = 26.0f;
        float x = centerX - width / 2.0f;
        float rate = m.startHotbarCount <= 0 ? 0.0f : Math.min(1.0f, (float) m.count / (float) m.startHotbarCount);

        RenderUtils.roundedRect(x, y, x + width, y + height, 6.0f, 0x96000000);
        if (rate > 0.0f) {
            RenderUtils.roundedRect(x, y, x + width * rate, y + height, 6.0f, 0x5AFFFFFF);
        }
        if (stack != null) {
            RenderUtils.drawItem(stack, x + itemOffset, y + (height - 16.0f) / 2.0f);
        }
        float textY = y + height / 2.0f - 3.0f;
        float textX = x + itemOffset + itemSize + pad;
        mc.fontRendererObj.drawString(label, textX, textY, 0xFFDCDCDC, true);
        mc.fontRendererObj.drawString(value, textX + labelWidth + gap, textY, color, true);
    }

    // ------------------------------------------------------------------ //
    // Helpers
    // ------------------------------------------------------------------ //

    /** One-line state for the dev probe; harmless in release. */
    public String debugState() {
        if (mc.thePlayer == null) {
            return "no-player";
        }
        return "active=" + active + " slot=" + blockSlot + " cur=" + mc.thePlayer.inventory.currentItem
                + " posY=" + posY + " data=" + (blockData == null ? "null" : blockData.pos + "/" + blockData.facing)
                + " canPlace=" + canPlace
                + " rot=" + (pendingRot == null ? "null" : ((int) pendingRot[0]) + "," + ((int) pendingRot[1]))
                + " placed=" + placedCount + " last=" + lastPlacedPos
                + " held=" + mc.thePlayer.getHeldItem() + " sneaking=" + sneakForced;
    }

    private static final class BlockData {
        final BlockPos pos;
        final EnumFacing facing;

        BlockData(BlockPos pos, EnumFacing facing) {
            this.pos = pos;
            this.facing = facing;
        }
    }

    /** Two-tick copy of the upstream FallingPlayer (client motion, gravity, drag only). */
    private static final class Fall {
        double x;
        double y;
        double z;
        double motionX;
        double motionY;
        double motionZ;
        final float yaw;
        final float strafe;
        final float forward;
        final float factor;
        final float eyeHeight;

        Fall() {
            this.x = mc.thePlayer.posX;
            this.y = mc.thePlayer.posY;
            this.z = mc.thePlayer.posZ;
            this.motionX = mc.thePlayer.motionX;
            this.motionY = mc.thePlayer.motionY;
            this.motionZ = mc.thePlayer.motionZ;
            this.yaw = lastServerYaw();
            this.strafe = mc.thePlayer.movementInput.moveStrafe;
            this.forward = mc.thePlayer.movementInput.moveForward;
            this.factor = mc.thePlayer.isSprinting() ? 0.026f : 0.02f;
            this.eyeHeight = mc.thePlayer.getEyeHeight();
        }

        void advance() {
            double lengthSq = (double) strafe * strafe + (double) forward * forward;
            if (lengthSq >= 1.0E-7) {
                double scale = lengthSq > 1.0 ? 1.0 / Math.sqrt(lengthSq) : 1.0;
                double sx = (double) strafe * scale * factor;
                double sz = (double) forward * scale * factor;
                float sin = MathHelper.sin(yaw * (float) Math.PI / 180.0f);
                float cos = MathHelper.cos(yaw * (float) Math.PI / 180.0f);
                motionX += sx * (double) cos - sz * (double) sin;
                motionZ += sz * (double) cos + sx * (double) sin;
            }
            x += motionX;
            y += motionY;
            z += motionZ;
            motionY -= 0.08;
            motionX *= 0.91;
            motionY *= 0.98;
            motionZ *= 0.91;
        }

        Vec3 eye() {
            return new Vec3(x, y + (double) eyeHeight, z);
        }
    }
}
