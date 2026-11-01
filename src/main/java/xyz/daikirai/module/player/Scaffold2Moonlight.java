package xyz.daikirai.module.player;

import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MathUtils;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;
import xyz.daikirai.util.RotationUtils;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Moonlight ({@code wtf.moonlight.module.impl.movement.Scaffold}) port used by
 * {@link Scaffold2Module} when its Client setting is {@code Moonlight}.
 *
 * <p>Ported core: target search ({@code ScaffoldUtil.getPlaceData}/{@code grab}), extra search, fall
 * fix, telly timing, the rotation-mode list, Place Block Mode Simulate/Packet, stuck/knockback
 * rescue, keep-Y, tower, sprint/swing/movement-fix/safe-walk/sneak/jump/AD-strafe/hover addons,
 * keep-FOV and the block counter HUD.</p>
 *
 * <p>Deliberately not ported (they hang off Moonlight's own components): Blink/PingSpoof, the
 * SpoofSlot component (Spoof is treated as Silent), GrimPlace's packet-order placement
 * ({@code Grim}/{@code OnTick} rotation modes, NCP/WD sprint modes), the Bezier/elastic rotation
 * curve modes and the 8 exotic counter styles (only None/Basic/Normal are offered).</p>
 */
public final class Scaffold2Moonlight {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_NORMAL = 0;
    private static final int MODE_TELLY = 1;
    private static final int MODE_SNAP = 2;

    private static final int SWITCH_SILENT = 0;
    private static final int SWITCH_SWITCH = 1;
    private static final int SWITCH_SPOOF = 2;

    private static final int PLACE_SIMULATE = 0;
    private static final int PLACE_PACKET = 1;

    private static final int HOVER_JUMP = 0;
    private static final int HOVER_FALL = 1;
    private static final int HOVER_DONE = 2;

    private final Scaffold2Module host;
    private final List<Setting> settings = new ArrayList<Setting>();

    // ---- values, names mirror the Moonlight config ----
    private final SliderSetting mode = mode("Mode", MODE_TELLY, "Normal", "Telly", "Snap");
    private final SliderSetting switchBlock = mode("Switch Block", SWITCH_SPOOF, "Silent", "Switch", "Spoof");
    private final ButtonSetting biggestStack = flag("Biggest Stack", false);
    private final SliderSetting placeMode = mode("Place Block Mode", PLACE_SIMULATE, "Simulate", "Packet");
    private final ButtonSetting extraSearch = flag("Extra Search", true);
    private final ButtonSetting fallFix = flag("Fall Fix", false);
    private final ButtonSetting stuckRescue = flag("Stuck Rescue", false);
    private final ButtonSetting knockbackRescue = flag("Knockback Rescue", true);
    private final SliderSetting minTellyTicks = slider("Min Telly Ticks", 1, 1, 5, 1, "");
    private final SliderSetting maxTellyTicks = slider("Max Telly Ticks", 1, 1, 5, 1, "");
    private final SliderSetting tellyTiming = mode("Telly Timing", 0, "Fixed", "Flux");
    private final ButtonSetting earlyRotation = flag("Early Rotation", false);
    private final ButtonSetting smoothRotations = flag("Smooth Rotations", false);
    private final SliderSetting rotations = mode("Rotations", 11, "Normal", "Normal 2", "Normal 3", "Smart Center",
            "Back", "Strict", "God Bridge", "Custom", "Unfair Pitch", "Hypixel", "Derp", "Unfair Smart Center");
    private final ButtonSetting applyGcd = flag("Rotation Apply GCD", false);
    private final SliderSetting customYaw = slider("Custom Yaw", 180, 0, 180, 1, "");
    private final SliderSetting minPitch = slider("Min Pitch Range", 55, 50, 90, 0.1, "");
    private final SliderSetting maxPitch = slider("Max Pitch Range", 75, 50, 90, 0.1, "");
    private final SliderSetting minYawSpeed = slider("Min Yaw Rotation Speed", 45, 1, 180, 1, "");
    private final SliderSetting minPitchSpeed = slider("Min Pitch Rotation Speed", 45, 1, 180, 1, "");
    private final SliderSetting maxYawSpeed = slider("Max Yaw Rotation Speed", 90, 1, 180, 1, "");
    private final SliderSetting maxPitchSpeed = slider("Max Pitch Rotation Speed", 90, 1, 180, 1, "");

    private final ButtonSetting addonSprint = flag("Addon: Sprint", true);
    private final ButtonSetting addonSwing = flag("Addon: Swing", true);
    private final ButtonSetting addonMovementFix = flag("Addon: Movement Fix", false);
    private final ButtonSetting addonRayTrace = flag("Addon: Ray Trace", false);
    private final ButtonSetting addonKeepY = flag("Addon: Keep Y", true);
    private final ButtonSetting addonSpeedKeepY = flag("Addon: Speed Keep Y", false);
    private final ButtonSetting addonSafeWalk = flag("Addon: Safe Walk", false);
    private final ButtonSetting addonSafeWalkNoData = flag("Addon: Safe Walk When No Data", false);
    private final ButtonSetting addonAdStrafe = flag("Addon: AD Strafe", false);
    private final ButtonSetting addonHover = flag("Addon: Hover", true);
    private final ButtonSetting addonSneak = flag("Addon: Sneak", false);
    private final ButtonSetting addonJump = flag("Addon: Jump", false);
    private final ButtonSetting addonTargetEsp = flag("Addon: Target Block ESP", false);

    private final SliderSetting keepYMode = mode("Keep Y Mode", 0, "Extra", "Vanilla");
    private final SliderSetting blocksToJump = slider("Blocks To Jump", 7, 1, 8, 1, "");
    private final SliderSetting blocksToSneak = slider("Blocks To Sneak", 7, 1, 8, 1, "");
    private final SliderSetting sneakDistance = slider("Sneak Distance", 0, 0, 0.5, 0.01, "");
    private final SliderSetting straightSpeed = slider("Keep Y Straight Speed", 1, 0.5, 1, 0.01, "x");
    private final SliderSetting diagonalSpeed = slider("Keep Y Diagonal Speed", 0.95, 0.5, 1, 0.01, "x");
    private final ButtonSetting sprintBoost = flag("Sprint Boost Test", true);
    private final SliderSetting tower = mode("Tower", 0, "Jump", "Vanilla");
    private final SliderSetting towerMove = mode("Tower Move", 0, "Jump", "Vanilla", "Low");
    private final ButtonSetting towerMoveNoSpeed = flag("Tower Move No Speed", false);
    private final ButtonSetting keepFov = flag("Keep fov", false);
    private final SliderSetting fovValue = slider("Fov", 1.2, 0.8, 1.5, 0.05, "");
    private final SliderSetting counter = mode("Counter", 2, "None", "Basic", "Normal");

    // ---- runtime ----
    private boolean active;
    private int blockSlot = -1;
    private int oldSlot = -1;
    private double onGroundY;
    private double smoothY;
    private BlockPos targetBlock;
    private BlockPos underPos;
    private BlockPos previousBlock;
    private PlaceData data;
    private float[] rotation = new float[]{0.0f, 85.0f};
    private float[] previousRotation = new float[]{0.0f, 85.0f};
    private float[] smoothRotation = new float[]{0.0f, 85.0f};
    private float derpYaw;
    private int tellyTicks = 1;
    private int offGroundTicks;
    private int onGroundTicks;
    private int blocksPlaced;
    private int hoverState = HOVER_DONE;
    private boolean canPlace;
    private boolean placed;
    private boolean rescuing;
    private int rescueBlocks;
    private int stuckBudget = 16;
    private int bigVelocityTicks;
    private float fovOriginal = -1.0f;
    private double lastFovValue;
    private final List<BlockPos> sortedSearch = new ArrayList<BlockPos>();

    public Scaffold2Moonlight(Scaffold2Module host) {
        this.host = host;
        for (int x = -4; x <= 4; ++x) {
            for (int y = -4; y <= -2; ++y) {
                for (int z = -4; z <= 4; ++z) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d <= 4.0) {
                        sortedSearch.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        sortedSearch.sort((a, b) -> Double.compare(a.getX() * a.getX() + a.getY() * a.getY() + a.getZ() * a.getZ(),
                b.getX() * b.getX() + b.getY() * b.getY() + b.getZ() * b.getZ()));
    }

    // ------------------------------------------------------------------ //
    // Settings
    // ------------------------------------------------------------------ //

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        settings.add(setting);
        return setting;
    }

    private SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        settings.add(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        settings.add(setting);
        return setting;
    }

    public List<Setting> settings() {
        return settings;
    }

    public SliderSetting clientMode() {
        return mode;
    }

    public boolean isActive() {
        return active;
    }

    /** The host asks each tick whether the silent rotation should skip the movement correction. */
    public boolean movementFixDisabled() {
        return !addonMovementFix.isToggled();
    }

    private boolean keepYActive() {
        return addonKeepY.isToggled() || addonSpeedKeepY.isToggled();
    }

    private boolean towering() {
        return PlayerUtils.jumpKeyDown() && !PlayerUtils.isMoving();
    }

    private boolean towerMoving() {
        return PlayerUtils.jumpKeyDown() && PlayerUtils.isMoving();
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    public void onEnable() {
        active = false;
        blockSlot = -1;
        oldSlot = mc.thePlayer == null ? -1 : mc.thePlayer.inventory.currentItem;
        data = null;
        targetBlock = null;
        underPos = null;
        previousBlock = null;
        placed = false;
        canPlace = true;
        rescuing = false;
        rescueBlocks = 0;
        stuckBudget = 16;
        bigVelocityTicks = 0;
        blocksPlaced = 0;
        offGroundTicks = 0;
        onGroundTicks = 0;
        tellyTicks = 1;
        derpYaw = 0.0f;
        hoverState = addonHover.isToggled() && mc.thePlayer != null && mc.thePlayer.onGround ? HOVER_JUMP : HOVER_DONE;
        onGroundY = mc.thePlayer == null ? 0.0 : mc.thePlayer.getEntityBoundingBox().minY;
        smoothY = mc.thePlayer == null ? 0.0 : mc.thePlayer.posY;
        previousRotation = new float[]{(mc.thePlayer == null ? 0.0f : mc.thePlayer.rotationYaw) + 180.0f, 82.0f};
        smoothRotation = new float[]{(mc.thePlayer == null ? 0.0f : mc.thePlayer.rotationYaw) - 125.0f, 85.0f};
        fovOriginal = mc.gameSettings.fovSetting;
    }

    public void onDisable() {
        restoreFov();
        active = false;
        data = null;
        targetBlock = null;
        underPos = null;
        restorSlot();
        if (mc.thePlayer != null) {
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    private void restoreFov() {
        if (fovOriginal > 0.0f) {
            mc.gameSettings.fovSetting = fovOriginal;
            fovOriginal = -1.0f;
        }
    }

    private void restorSlot() {
        if (mc.thePlayer != null && oldSlot != -1) {
            if (silentSwitch()) {
                // Silent/Spoof only changed the server-side slot; tell it the original one again.
                PlayerUtils.sendSlot(oldSlot);
            } else if (mc.thePlayer.inventory.currentItem != oldSlot) {
                PlayerUtils.setSlot(oldSlot, true);
            }
        }
        oldSlot = -1;
    }

    private boolean silentSwitch() {
        int index = (int) switchBlock.getInput();
        // Silent and Spoof both leave the client-side slot alone (Spoof has no slot component here).
        return index == SWITCH_SILENT || index == SWITCH_SPOOF;
    }

    // ------------------------------------------------------------------ //
    // Tick
    // ------------------------------------------------------------------ //

    public void tick() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            active = false;
            return;
        }
        if (Overlay.screenOpen() || PlayerUtils.isAdventure()) {
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
        if (!active) {
            begin();
        }
        applySlot(slot);

        if (bigVelocityTicks > 0) {
            --bigVelocityTicks;
        }
        if (mc.thePlayer.onGround) {
            stuckBudget = 16;
        }

        if (mc.thePlayer.onGround) {
            onGroundY = mc.thePlayer.getEntityBoundingBox().minY;
        }
        double targetY = mc.thePlayer.getEntityBoundingBox().minY;
        if ((hoverState != HOVER_DONE || keepYActive()) && !mc.gameSettings.keyBindJump.isKeyDown()) {
            targetY = onGroundY;
        }
        if (keepYActive() && keepYModeIndex() == 0 && !towering() && !mc.gameSettings.keyBindJump.isKeyDown()) {
            if (mc.thePlayer.ticksExisted % 6 == 0 && mc.thePlayer.getEntityBoundingBox().minY < onGroundY + 1.0) {
                targetY = onGroundY;
            }
        }
        if (towering() || towerMoving()) {
            onGroundY = targetY = mc.thePlayer.getEntityBoundingBox().minY;
        }
        if (fallFix.isToggled() && !mc.thePlayer.onGround && mc.thePlayer.getEntityBoundingBox().minY < targetY - 1.0E-4) {
            targetY = mc.thePlayer.getEntityBoundingBox().minY;
        }

        targetBlock = new BlockPos(mc.thePlayer.posX, targetY, mc.thePlayer.posZ).down();
        underPos = new BlockPos(mc.thePlayer.posX, smoothY, mc.thePlayer.posZ);
        if (mc.gameSettings.keyBindJump.isKeyDown() || mc.thePlayer.onGround) {
            smoothY = mc.thePlayer.posY;
        }

        data = getPlaceData(targetBlock);
        if (data != null && isContainer(BlockUtils.getBlock(data.blockPos))) {
            data = null;
        }
        if (data == null && underPos != null) {
            data = grab(underPos);
            if (data != null && isContainer(BlockUtils.getBlock(data.blockPos))) {
                data = null;
            }
        }
        if (extraSearch.isToggled() && data == null) {
            for (BlockPos offset : sortedSearch) {
                BlockPos candidate = new BlockPos(
                        Math.floor(mc.thePlayer.posX + offset.getX()),
                        (int) (mc.thePlayer.posY + offset.getY() + mc.thePlayer.getEyeHeight()),
                        Math.floor(mc.thePlayer.posZ + offset.getZ()));
                if (getPlaceData(candidate) == null || BlockUtils.isContainer(BlockUtils.getBlock(candidate))) {
                    continue;
                }
                data = getPlaceData(candidate.down());
                if (data != null) {
                    break;
                }
            }
        }
        if (fallFix.isToggled() && data == null && !mc.thePlayer.onGround) {
            PlaceData fall = fallSearch();
            if (fall != null) {
                data = fall;
            }
        }

        if (modeIndex() == MODE_TELLY && mc.thePlayer.onGround) {
            tellyTicks = MathUtils.randomInt((int) minTellyTicks.getInput(), (int) maxTellyTicks.getInput());
        }

        canPlace = true;
        if (modeIndex() == MODE_TELLY) {
            canPlace = tellyReady();
        } else if (modeIndex() == MODE_SNAP) {
            canPlace = data != null && (mc.thePlayer.isPotionActive(Potion.moveSpeed) || doesNotContainBlock(1));
        }

        hoverTick();
        if (keepFov.isToggled()) {
            applyFov();
        }

        if (data == null) {
            previousRotation = rotation;
            return;
        }

        computeRotation();
        if (rescueCheck()) {
            previousRotation = rotation;
            return;
        }
        placeNow();
        previousRotation = rotation;
    }

    private void begin() {
        active = true;
        oldSlot = mc.thePlayer.inventory.currentItem;
        onGroundY = mc.thePlayer.getEntityBoundingBox().minY;
        smoothY = mc.thePlayer.posY;
        offGroundTicks = mc.thePlayer.onGround ? 0 : 1;
        onGroundTicks = mc.thePlayer.onGround ? 1 : 0;
        previousRotation = new float[]{mc.thePlayer.rotationYaw + 180.0f, 82.0f};
        smoothRotation = new float[]{mc.thePlayer.rotationYaw - 125.0f, 85.0f};
    }

    private void stop() {
        if (active) {
            active = false;
            restorSlot();
            if (mc.thePlayer != null) {
                mc.thePlayer.movementInput.sneak = false;
            }
            restoreFov();
        }
        data = null;
        targetBlock = null;
        underPos = null;
        placed = false;
        canPlace = false;
        blocksPlaced = 0;
        rescueBlocks = 0;
    }

    public void onDisableState() {
        stop();
    }

    private void hoverTick() {
        if (hoverState == HOVER_JUMP) {
            if (mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()) {
                PlayerUtils.jump();
            }
            hoverState = HOVER_FALL;
        } else if (hoverState == HOVER_FALL && mc.thePlayer.onGround) {
            hoverState = HOVER_DONE;
        }
    }

    private boolean tellyReady() {
        if (tellyTimingIndex() == 0) {
            return offGroundTicks >= tellyTicks;
        }
        if (mc.thePlayer.onGround) {
            return false;
        }
        if (mc.thePlayer.getEntityBoundingBox().minY < onGroundY - 0.5) {
            return true;
        }
        float f = (mc.thePlayer.rotationYaw % 360.0f + 360.0f) % 360.0f % 90.0f;
        boolean diagonal = f > 10.0f && f < 80.0f;
        boolean jump = mc.gameSettings.keyBindJump.isKeyDown();
        int need = jump ? (diagonal ? 1 : 2) : (diagonal ? 3 : 4);
        double mx = Math.abs(mc.thePlayer.motionX);
        double mz = Math.abs(mc.thePlayer.motionZ);
        if (Math.max(mx, mz) <= 0.4) {
            need = Math.max(need, mx >= 0.03 && mz >= 0.03 ? 2 : 3);
        }
        return offGroundTicks >= need;
    }

    private boolean doesNotContainBlock(int down) {
        BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - down, mc.thePlayer.posZ);
        return BlockUtils.isReplaceable(pos);
    }

    // ------------------------------------------------------------------ //
    // Slots / counts
    // ------------------------------------------------------------------ //

    private int findBlockSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        boolean biggest = biggestStack.isToggled();
        int found = -1;
        int best = -1;
        for (int slot = 0; slot <= 8; ++slot) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (!ItemUtils.isScaffoldBlock(stack)) {
                continue;
            }
            if (!biggest) {
                return slot;
            }
            if (stack.stackSize > best) {
                best = stack.stackSize;
                found = slot;
            }
        }
        return found;
    }

    private void applySlot(int slot) {
        blockSlot = slot;
        if (silentSwitch()) {
            if (mc.thePlayer.inventory.currentItem != slot) {
                PacketUtilsBridge.sendSlot(slot);
            }
        } else if (mc.thePlayer.inventory.currentItem != slot) {
            PlayerUtils.setSlot(slot, true);
        }
    }

    public int countBlocks() {
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

    // ------------------------------------------------------------------ //
    // Target search (ScaffoldUtil port)
    // ------------------------------------------------------------------ //

    private static final EnumFacing[] PLACE_FACINGS = {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP};
    private static final BlockPos[] PLACE_OFFSETS = {
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1), new BlockPos(0, -1, 0)};

    private PlaceData getPlaceData(BlockPos target) {
        if (target == null) {
            return null;
        }
        for (int i = 0; i < PLACE_OFFSETS.length; ++i) {
            BlockPos anchor = target.add(PLACE_OFFSETS[i]);
            if (canBePlacedOn(anchor)) {
                return new PlaceData(anchor, PLACE_FACINGS[i]);
            }
        }
        for (int ring = 0; ring < 2; ++ring) {
            for (int i = 0; i < PLACE_OFFSETS.length; ++i) {
                BlockPos near = target.add(PLACE_OFFSETS[i]);
                for (int j = 0; j < PLACE_OFFSETS.length; ++j) {
                    BlockPos anchor = near.add(PLACE_OFFSETS[j]);
                    if (canBePlacedOn(anchor)) {
                        return new PlaceData(anchor, PLACE_FACINGS[j]);
                    }
                }
            }
        }
        return null;
    }

    private PlaceData grab(BlockPos under) {
        BlockPos below = under.down();
        if (!isAir(below)) {
            return null;
        }
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos anchor = below.offset(facing);
            if (isAir(anchor)) {
                continue;
            }
            return new PlaceData(anchor, facing.getOpposite());
        }
        BlockPos[] sides = {new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, -1), new BlockPos(0, 0, 1)};
        for (BlockPos side : sides) {
            BlockPos pos = below.add(side);
            if (!isAir(pos)) {
                continue;
            }
            for (EnumFacing facing : EnumFacing.values()) {
                BlockPos anchor = pos.offset(facing);
                if (!isAir(anchor)) {
                    return new PlaceData(anchor, facing.getOpposite());
                }
            }
        }
        return null;
    }

    private PlaceData fallSearch() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        Vec3 eye = new Vec3(cx, box.minY + mc.thePlayer.getEyeHeight(), cz);
        int baseY = MathHelper.floor_double(box.minY);
        PlaceData best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int down = 1; down <= 3; ++down) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    BlockPos pos = new BlockPos(MathHelper.floor_double(cx) + dx, baseY - down, MathHelper.floor_double(cz) + dz);
                    if (!isAir(pos)) {
                        continue;
                    }
                    AxisAlignedBB blockBox = new AxisAlignedBB(pos, pos.add(1, 1, 1));
                    if (box.intersectsWith(blockBox)) {
                        continue;
                    }
                    if (eye.squareDistanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) > 20.25) {
                        continue;
                    }
                    for (EnumFacing facing : EnumFacing.values()) {
                        BlockPos anchor = pos.offset(facing);
                        if (!canBePlacedOn(anchor) || isContainer(BlockUtils.getBlock(anchor))) {
                            continue;
                        }
                        double distance = eye.squareDistanceTo(new Vec3(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5));
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new PlaceData(anchor, facing.getOpposite());
                        }
                    }
                }
            }
        }
        return best;
    }

    private static boolean isAir(BlockPos pos) {
        if (mc.theWorld == null) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block == Blocks.air || block.getMaterial().isReplaceable();
    }

    private static boolean canBePlacedOn(BlockPos pos) {
        if (mc.theWorld == null) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block.getMaterial().blocksMovement() && block.getMaterial().isSolid() && block != Blocks.air;
    }

    private static boolean isContainer(Block block) {
        return BlockUtils.isContainer(block) || block == Blocks.crafting_table;
    }

    // ------------------------------------------------------------------ //
    // Rotation
    // ------------------------------------------------------------------ //

    private void computeRotation() {
        if (data == null) {
            return;
        }
        switch (rotationIndex()) {
            case 0: // Normal
                rotation = RotationUtils.toBlock(data.blockPos, data.facing);
                break;
            case 1: // Normal 2
                rotation = RotationUtils.toBlock(data.blockPos);
                break;
            case 2: // Normal 3
                rotation = bestRotation(data);
                break;
            case 3: // Smart Center
                rotation = RotationUtils.toVec(smartCenter(data));
                break;
            case 4: // Back
                rotation = new float[]{MathHelper.wrapAngleTo180_float(PlayerUtils.movementAngle() + 180.0f),
                        (float) (75.0 + Math.random() + offGroundTicks * 0.2)};
                break;
            case 5: // Strict
                rotation = RotationUtils.toVec(hitVecOptimized(data));
                break;
            case 6: // God Bridge
                float god = PlayerUtils.movementAngle() + 180.0f;
                if (PlayerUtils.direction().diagonal) {
                    god += 0.0f;
                }
                god = (float) Math.round(god / 45.0f) * 45.0f;
                rotation = pitchSearch(god, data);
                break;
            case 7: // Custom
                rotation = pitchSearch(PlayerUtils.movementAngle() + (float) customYaw.getInput(), data);
                break;
            case 8: // Unfair Pitch
                float[] smart = RotationUtils.toVec(smartCenter(data));
                rotation = new float[]{mc.thePlayer.rotationYaw, 180.0f - smart[1]};
                break;
            case 9: // Hypixel
                float dir = PlayerUtils.movementAngle();
                float faceYaw = RotationUtils.toBlock(data.blockPos, data.facing)[0];
                float plus = MathHelper.wrapAngleTo180_float(faceYaw - dir - 118.0f);
                float minus = MathHelper.wrapAngleTo180_float(faceYaw - dir + 118.0f);
                rotation = new float[]{Math.abs(plus) < Math.abs(minus) ? dir + 118.0f : dir - 118.0f,
                        bestRotation(data)[1]};
                break;
            case 10: // Derp
                derpYaw += 30.0f;
                rotation = new float[]{derpYaw, 85.0f};
                break;
            case 11: // Unfair Smart Center
                float[] target = RotationUtils.toVec(smartCenter(data));
                if (Math.abs(MathHelper.wrapAngleTo180_float(target[0] - mc.thePlayer.rotationYaw)) > 135.0f) {
                    rotation = new float[]{target[0] + 180.0f, 180.0f - target[1]};
                } else {
                    rotation = target;
                }
                break;
            default:
                rotation = RotationUtils.toBlock(data.blockPos, data.facing);
                break;
        }
        if (smoothRotations.isToggled() && modeIndex() == MODE_TELLY) {
            smoothRotation = smoothToward(smoothRotation, rotation);
        }
        if (applyGcd.isToggled()) {
            rotation = gcd(rotation);
        }
        if (rotation != null) {
            rotation[1] = MathHelper.clamp_float(rotation[1], -90.0f, 90.0f);
        }
    }

    private float[] pitchSearch(float yaw, PlaceData placeData) {
        float start = (float) minPitch.getInput();
        float end = (float) maxPitch.getInput();
        float[] fallback = new float[]{yaw, start};
        for (float pitch = start; pitch < end; pitch += 0.5f) {
            if (PlayerUtils.rayTraceBlock(placeData.blockPos, placeData.facing, mc.playerController.getBlockReachDistance(), yaw, pitch) != null) {
                return new float[]{yaw, pitch};
            }
            fallback[1] = pitch;
        }
        return fallback;
    }

    private float[] bestRotation(PlaceData placeData) {
        float[] base = RotationUtils.toBlock(placeData.blockPos, placeData.facing);
        float[] best = base;
        double bestDiff = rotationDifference(base);
        for (Vec3 point : RotationUtils.facePoints(placeData.blockPos, placeData.facing)) {
            float[] candidate = RotationUtils.toVec(point);
            if (PlayerUtils.rayTraceBlock(placeData.blockPos, placeData.facing, mc.playerController.getBlockReachDistance(), candidate[0], candidate[1]) == null) {
                continue;
            }
            double diff = rotationDifference(candidate);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = candidate;
            }
        }
        return best;
    }

    private double rotationDifference(float[] target) {
        float[] current = RotationUtils.currentRotation();
        double dy = Math.abs(MathHelper.wrapAngleTo180_float(target[0] - current[0]));
        double dp = Math.abs(target[1] - current[1]);
        return dy * dy + dp * dp;
    }

    private Vec3 hitVecOptimized(PlaceData placeData) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);
        AxisAlignedBB box = new AxisAlignedBB(placeData.blockPos, placeData.blockPos.add(1, 1, 1));
        double x = MathHelper.clamp_double(eye.xCoord, box.minX + 0.05, box.maxX - 0.05);
        double y = MathHelper.clamp_double(eye.yCoord, box.minY + 0.05, box.maxY - 0.05);
        double z = MathHelper.clamp_double(eye.zCoord, box.minZ + 0.05, box.maxZ - 0.05);
        if (placeData.facing.getAxis() == EnumFacing.Axis.X) {
            x = placeData.facing.getFrontOffsetX() > 0 ? box.maxX : box.minX;
        } else if (placeData.facing.getAxis() == EnumFacing.Axis.Y) {
            y = placeData.facing.getFrontOffsetY() > 0 ? box.maxY : box.minY;
        } else {
            z = placeData.facing.getFrontOffsetZ() > 0 ? box.maxZ : box.minZ;
        }
        return new Vec3(x, y, z);
    }

    private Vec3 facePoint(PlaceData placeData) {
        BlockPos pos = placeData.blockPos;
        return new Vec3(pos.getX() + 0.5 + placeData.facing.getFrontOffsetX() * 0.5,
                pos.getY() + 0.5 + placeData.facing.getFrontOffsetY() * 0.5,
                pos.getZ() + 0.5 + placeData.facing.getFrontOffsetZ() * 0.5);
    }

    private Vec3 smartCenter(PlaceData placeData) {
        AxisAlignedBB box = BlockUtils.getBlock(placeData.blockPos).getSelectedBoundingBox(mc.theWorld, placeData.blockPos);
        double x = (box.minX + box.maxX) / 2.0;
        double y = (box.minY + box.maxY) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;
        switch (placeData.facing) {
            case UP:
                return new Vec3(x, box.maxY, z);
            case DOWN:
                return new Vec3(x, box.minY, z);
            case NORTH:
                return new Vec3(x, y, box.minZ);
            case SOUTH:
                return new Vec3(x, y, box.maxZ);
            case WEST:
                return new Vec3(box.minX, y, z);
            case EAST:
                return new Vec3(box.maxX, y, z);
            default:
                return new Vec3(x, y, z);
        }
    }

    private float[] smoothToward(float[] current, float[] target) {
        float yaw = approach(current[0], target[0], (float) maxYawSpeed.getInput());
        float pitch = approach(current[1], target[1], (float) maxPitchSpeed.getInput());
        return new float[]{yaw, pitch};
    }

    private static float approach(float from, float to, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(to - from);
        if (Math.abs(diff) <= speed) {
            return to;
        }
        return from + Math.signum(diff) * speed;
    }

    private static float[] gcd(float[] rot) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6f + 0.2f;
        double step = (double) (sensitivity * sensitivity * sensitivity) * 1.2;
        float[] current = RotationUtils.currentRotation();
        float yaw = current[0] + (float) ((double) Math.round((double) MathHelper.wrapAngleTo180_float(rot[0] - current[0]) / step) * step);
        float pitch = current[1] + (float) ((double) Math.round((double) (rot[1] - current[1]) / step) * step);
        return new float[]{yaw, pitch};
    }

    public float[] serverRotation() {
        if (!active || data == null || !canPlace) {
            return null;
        }
        float[] out = (smoothRotations.isToggled() && modeIndex() == MODE_TELLY) ? smoothRotation : rotation;
        if (out == null) {
            return null;
        }
        return new float[]{out[0], out[1]};
    }

    // ------------------------------------------------------------------ //
    // Placement / rescue
    // ------------------------------------------------------------------ //

    private boolean placeNow() {
        if (!canPlace || data == null) {
            return false;
        }
        if (addonRayTrace.isToggled()) {
            if (PlayerUtils.rayTraceBlock(data.blockPos, data.facing, mc.playerController.getBlockReachDistance(), rotation[0], rotation[1]) == null) {
                return false;
            }
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
        if (stack == null || stack.stackSize <= 0) {
            return false;
        }
        Vec3 hitVec = facePoint(data);
        MovingObjectPosition hit = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.BLOCK, hitVec, data.facing, data.blockPos);
        if (placeModeIndex() == PLACE_PACKET) {
            PlayerUtils.sendPlace(hit, stack);
        } else {
            PlayerUtils.place(hit, stack);
        }
        if (addonSwing.isToggled()) {
            mc.thePlayer.swingItem();
            mc.getItemRenderer().resetEquippedProgress();
        } else {
            PlayerUtils.swing(true, true);
        }
        blocksPlaced++;
        placed = true;
        previousBlock = data.blockPos;
        underPos = null;
        if (towering() && mc.thePlayer.onGround) {
            PlayerUtils.jump();
        }
        return true;
    }

    private boolean rescueCheck() {
        if (!stuckRescue.isToggled() || mc.thePlayer.onGround || stuckBudget <= 0) {
            return false;
        }
        boolean knockback = knockbackRescue.isToggled() && bigVelocityTicks > 0 && mc.thePlayer.motionY < 0.0;
        if (!knockback && (data == null || mc.thePlayer.motionY >= -0.1)) {
            return false;
        }
        if (landsWithin(2)) {
            return false;
        }
        PlaceData rescue = fallSearch();
        if (rescue == null) {
            return false;
        }
        if (!knockback) {
            Fall fall = new Fall();
            fall.advance();
            fall.advance();
            if (rescue.blockPos.getY() <= fall.y) {
                return false;
            }
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
        if (stack == null) {
            return false;
        }
        MovingObjectPosition hit = new MovingObjectPosition(MovingObjectPosition.MovingObjectType.BLOCK,
                facePoint(rescue), rescue.facing, rescue.blockPos);
        PlayerUtils.sendPlace(hit, stack);
        ++rescueBlocks;
        --stuckBudget;
        rotation = new float[]{RotationUtils.toVec(facePoint(rescue))[0], (float) (80.0 + Math.random())};
        return true;
    }

    private boolean landsWithin(int ticks) {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double mx = mc.thePlayer.motionX;
        double my = mc.thePlayer.motionY;
        double mz = mc.thePlayer.motionZ;
        double dx = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        for (int i = 0; i < ticks; ++i) {
            dx += mx;
            dy += my;
            dz += mz;
            mx *= 0.91;
            mz *= 0.91;
            my = (my - 0.08) * 0.98;
        }
        AxisAlignedBB moved = box.offset(dx, 0.0, dz).addCoord(0.0, dy, 0.0);
        for (AxisAlignedBB other : mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, moved)) {
            if (other.maxY <= box.minY + 0.001) {
                return true;
            }
        }
        return false;
    }

    public void onVelocity(double motionX, double motionZ) {
        if (Math.hypot(motionX, motionZ) >= 1.5) {
            bigVelocityTicks = 60;
        }
    }

    // ------------------------------------------------------------------ //
    // Input (movement addons)
    // ------------------------------------------------------------------ //

    public void postInput() {
        if (!active) {
            return;
        }
        if (addonSprint.isToggled()) {
            mc.thePlayer.setSprinting(true);
        }
        if (addonKeepY.isToggled() && mc.thePlayer.onGround && PlayerUtils.isMoving() && !towering() && !towerMoving()) {
            PlayerUtils.jump();
        }
        if (addonJump.isToggled() && mc.thePlayer.onGround && !mc.gameSettings.keyBindJump.isKeyDown()
                && PlayerUtils.isMoving() && !mc.thePlayer.isSneaking()) {
            if (blocksPlaced >= (int) blocksToJump.getInput()) {
                PlayerUtils.jump();
                blocksPlaced = 0;
            }
        }
        if (addonSneak.isToggled()) {
            sneakAddon();
        }
        if (addonSafeWalk.isToggled() && mc.thePlayer.onGround) {
            mc.thePlayer.movementInput.sneak = true;
        } else if (addonSafeWalkNoData.isToggled() && data == null) {
            mc.thePlayer.movementInput.sneak = true;
        }
        if (addonAdStrafe.isToggled() && PlayerUtils.isMoving() && !PlayerUtils.direction().diagonal
                && mc.currentScreen == null && !mc.gameSettings.keyBindJump.isKeyDown() && mc.thePlayer.onGround) {
            adStrafe();
        }
        if (sprintBoost.isToggled() && mc.thePlayer.onGround && modeIndex() != MODE_TELLY) {
            mc.thePlayer.motionX *= 1.14;
            mc.thePlayer.motionZ *= 1.14;
        }
        if (towerModeIndex() == 1 && towering() && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.motionY = 0.42;
        }
        if (towerMoveIndex() == 1 && towerMoving() && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.motionY = 0.42;
        }
        if (towerMoveIndex() == 2 && towerMoving() && offGroundTicks == 1) {
            mc.thePlayer.motionY += 0.057;
            if (!towerMoveNoSpeed.isToggled()) {
                MoveUtilsBridge.strafe(Math.max(MoveUtilsBridge.speed(), 0.33));
            }
        }
    }

    private void sneakAddon() {
        BlockPos below = new BlockPos(mc.thePlayer).down();
        double distance = 0.5;
        for (EnumFacing facing : EnumFacing.values()) {
            if (facing.getAxis() == EnumFacing.Axis.Y) {
                continue;
            }
            BlockPos neighbor = below.offset(facing);
            if (BlockUtils.isReplaceable(neighbor)) {
                continue;
            }
            double d = facing.getAxis() == EnumFacing.Axis.Z
                    ? Math.abs(neighbor.getZ() + 0.5 - mc.thePlayer.posZ)
                    : Math.abs(neighbor.getX() + 0.5 - mc.thePlayer.posX) - 0.5;
            if (d < distance) {
                distance = d;
            }
        }
        if (mc.thePlayer.onGround && (BlockUtils.isReplaceable(below) || distance < sneakDistance.getInput())
                && blocksPlaced == (int) blocksToSneak.getInput()) {
            mc.thePlayer.movementInput.sneak = true;
        }
        if (blocksPlaced > (int) blocksToSneak.getInput()) {
            blocksPlaced = 0;
        }
    }

    private void adStrafe() {
        BlockPos pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
        EnumFacing facing = horizontalFacing(mc.thePlayer.rotationYaw + 180.0f);
        float strafe = 0.0f;
        if (facing == EnumFacing.EAST) {
            strafe = pos.getZ() + 0.5 > mc.thePlayer.posZ ? 1.0f : -1.0f;
        } else if (facing == EnumFacing.WEST) {
            strafe = pos.getZ() + 0.5 < mc.thePlayer.posZ ? 1.0f : -1.0f;
        } else if (facing == EnumFacing.SOUTH) {
            strafe = pos.getX() + 0.5 < mc.thePlayer.posX ? 1.0f : -1.0f;
        } else {
            strafe = pos.getX() + 0.5 > mc.thePlayer.posX ? 1.0f : -1.0f;
        }
        mc.thePlayer.movementInput.moveStrafe = strafe;
    }

    // ------------------------------------------------------------------ //
    // FOV / render / HUD
    // ------------------------------------------------------------------ //

    private void applyFov() {
        if (!PlayerUtils.isMoving()) {
            return;
        }
        double value = fovValue.getInput() + PlayerUtils.speedLevel() * 0.13;
        if (Math.abs(value - lastFovValue) > 0.001) {
            lastFovValue = value;
            mc.gameSettings.fovSetting = (float) value;
        }
    }

    public void render3d() {
        if (!active || !addonTargetEsp.isToggled() || data == null) {
            return;
        }
        Render3D.block(data.blockPos, RenderUtils.withAlpha(Theme.scheme().primary, 120), true, true);
    }

    public void renderCount() {
        if (!active || counterIndex() == 0) {
            return;
        }
        int count = countBlocks();
        float centerX = RenderUtils.scaledWidth / 2.0f;
        float y = RenderUtils.scaledHeight / 2.0f + 15.0f;
        if (counterIndex() == 1) {
            String text = count + " Blocks";
            mc.fontRendererObj.drawString(text, centerX - mc.fontRendererObj.getStringWidth(text) / 2.0f, y,
                    count < 16 ? 0xFFFF5050 : (count < 32 ? 0xFFFFDC50 : 0xFFFFFFFF), true);
            return;
        }
        ItemStack stack = mc.thePlayer.getHeldItem();
        String label = "Blocks";
        String value = String.valueOf(count);
        float labelWidth = mc.fontRendererObj.getStringWidth(label);
        float valueWidth = mc.fontRendererObj.getStringWidth(value);
        float pad = 5.0f;
        float width = 4.0f + 18.0f + pad + labelWidth + 3.0f + valueWidth + pad;
        float height = 26.0f;
        float x = centerX - width / 2.0f;
        RenderUtils.roundedRect(x, y, x + width, y + height, 6.0f, 0x96000000);
        if (ItemUtils.isScaffoldBlock(stack)) {
            RenderUtils.drawItem(stack, x + 4.0f, y + 5.0f);
        }
        float textY = y + height / 2.0f - 3.0f;
        mc.fontRendererObj.drawString(label, x + 4.0f + 18.0f + pad, textY, 0xFFDCDCDC, true);
        mc.fontRendererObj.drawString(value, x + 4.0f + 18.0f + pad + labelWidth + 3.0f, textY,
                count < 16 ? 0xFFFF5050 : (count < 32 ? 0xFFFFDC50 : 0xFFFFFFFF), true);
    }

    // ------------------------------------------------------------------ //
    // Small bridges (keeps the Moonlight code readable)
    // ------------------------------------------------------------------ //

    private static final class PacketUtilsBridge {
        static void sendSlot(int slot) {
            PlayerUtils.sendSlot(slot);
        }
    }

    private static final class MoveUtilsBridge {
        static void strafe(double speed) {
            xyz.daikirai.util.MoveUtils.strafe(speed);
        }

        static double speed() {
            return xyz.daikirai.util.MoveUtils.speed();
        }
    }

    private static EnumFacing horizontalFacing(float yaw) {
        return EnumFacing.getHorizontal(MathHelper.floor_double((double) (yaw * 4.0f / 360.0f) + 0.5) & 3);
    }

    private int modeIndex() {
        return (int) mode.getInput();
    }

    private int switchIndex() {
        return (int) switchBlock.getInput();
    }

    private int placeModeIndex() {
        return (int) placeMode.getInput();
    }

    private int tellyTimingIndex() {
        return (int) tellyTiming.getInput();
    }

    private int rotationIndex() {
        return (int) rotations.getInput();
    }

    private int keepYModeIndex() {
        return (int) keepYMode.getInput();
    }

    private int towerModeIndex() {
        return (int) tower.getInput();
    }

    private int towerMoveIndex() {
        return (int) towerMove.getInput();
    }

    private int counterIndex() {
        return (int) counter.getInput();
    }

    private static final class PlaceData {
        final BlockPos blockPos;
        final EnumFacing facing;

        PlaceData(BlockPos blockPos, EnumFacing facing) {
            this.blockPos = blockPos;
            this.facing = facing;
        }
    }

    /** Two-tick fall simulation for the rescue check. */
    private static final class Fall {
        double y;
        double motionY;

        Fall() {
            y = mc.thePlayer.posY;
            motionY = mc.thePlayer.motionY;
        }

        void advance() {
            y += motionY;
            motionY = (motionY - 0.08) * 0.98;
        }
    }
}
