package xyz.daikirai.module.player;

import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
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
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Rise ({@code com.alan.clients.module.impl.player.Scaffold}) port used by
 * {@link Scaffold2Module} when the Client setting is {@code Rise}.
 *
 * <p>Core port: the {@code Mode} family (Normal/Godbridge/Breesily/Snap/Telly/Eagle), the silent
 * yaw/pitch rotation with a per-tick speed clamp, {@code Ray Cast} Off/Normal/Strict, the Grim
 * rotation mode (look packet → place → restore), {@code Same Y}, place delay, movement correction,
 * safe walk, the sneak rhythm, tower and the target-block render.</p>
 *
 * <p>Not ported: Rise's server-specific strategy classes (Watchdog/Verus/Matrix/Vulcan/MMC
 * downstairs, 10 sprint and 10 tower strategies), {@code Timer}, {@code Downwards (Press Sneak)},
 * the watchdog rotation boost/prediction paths and {@code Visual Back Rotations} camera tricks —
 * they depend on Rise's components and packet-order manager.</p>
 */
public final class Scaffold2Rise {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_NORMAL = 0;
    private static final int MODE_GODBRIDGE = 1;
    private static final int MODE_BREESILY = 2;
    private static final int MODE_SNAP = 3;
    private static final int MODE_TELLY = 4;
    private static final int MODE_EAGLE = 5;

    private static final int SAME_Y_OFF = 0;
    private static final int SAME_Y_ON = 1;
    private static final int SAME_Y_AUTO_JUMP = 2;

    private static final int RAY_OFF = 0;
    private static final int RAY_NORMAL = 1;
    private static final int RAY_STRICT = 2;

    private final Scaffold2Module host;
    private final List<Setting> settings = new ArrayList<Setting>();

    private final SliderSetting mode = mode("Mode", MODE_TELLY, "Normal", "Godbridge", "Breesily", "Snap", "Telly", "Eagle");
    private final SliderSetting rotationMode = mode("Rotation Mode", 0, "Normal", "Grim");
    private final SliderSetting rayCast = mode("Ray Cast", RAY_STRICT, "Off", "Normal", "Strict");
    private final SliderSetting sprint = mode("Sprint", 1, "Disabled", "Normal", "Legit");
    private final SliderSetting tower = mode("Tower", 0, "Disabled", "Normal", "Vanilla");
    private final SliderSetting sameY = mode("Same Y", SAME_Y_OFF, "Off", "On", "Auto Jump");
    private final SliderSetting rotationSpeedMin = slider("Rotation Speed (min)", 5, 1, 10, 1, "");
    private final SliderSetting rotationSpeedMax = slider("Rotation Speed (max)", 10, 1, 10, 1, "");
    private final SliderSetting placeDelayMin = slider("Place Delay (min)", 0, 0, 5, 1, "");
    private final SliderSetting placeDelayMax = slider("Place Delay (max)", 0, 0, 5, 1, "");
    private final SliderSetting expand = slider("Expand", 0, 0, 4, 1, "");
    private final ButtonSetting movementCorrection = flag("Movement Correction", true);
    private final ButtonSetting safeWalk = flag("Safe Walk", true);
    private final ButtonSetting sneak = flag("Sneak", false);
    private final SliderSetting startSneaking = slider("Start Sneaking", 0, 0, 5, 1, "");
    private final SliderSetting stopSneaking = slider("Stop Sneaking", 0, 0, 5, 1, "");
    private final SliderSetting sneakEvery = slider("Sneak every x blocks", 1, 1, 10, 1, "");
    private final SliderSetting sneakingSpeed = slider("Sneaking Speed", 0.2, 0.2, 1, 0.05, "x");
    private final ButtonSetting tellyOnlyOnRightClick = flag("Telly Only on Right Click", false);
    private final ButtonSetting render = flag("Render", true);
    private final SliderSetting yawOffset = mode("Yaw Offset", 0, "0", "45", "-45");
    private final ButtonSetting upSideDown = flag("Up Side Down", false);
    private final ButtonSetting bypassRaycastWhenFalling = flag("Bypass Raycast When Falling", false);
    private final ButtonSetting advanced = flag("Advanced", false);

    private boolean active;
    private int blockSlot = -1;
    private int oldSlot = -1;
    private double startY;
    private int tellyTicks;
    private int offGroundTicks;
    private int onGroundTicks;
    private int blocksPlaced;
    private int placeCooldown;
    private int sneakTicks;
    private int blocksUntilSneak;
    private BlockPos targetBlock;
    private PlaceData data;
    private float targetYaw;
    private float targetPitch = 90.0f;
    private float currentYaw;
    private float currentPitch = 90.0f;
    private float randomYawOffset;
    private float randomPitchOffset;
    private boolean canPlace;
    private boolean placedThisTick;
    private int renderTicks;

    public Scaffold2Rise(Scaffold2Module host) {
        this.host = host;
        last = this;
    }

    private static Scaffold2Rise last;

    public static String instanceState() {
        return last == null ? "none" : last.debugState();
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

    public boolean isActive() {
        return active;
    }

    public boolean movementFixDisabled() {
        return !movementCorrection.isToggled();
    }

    public int modeIndex() {
        return (int) mode.getInput();
    }

    private int sameYIndex() {
        return (int) sameY.getInput();
    }

    private int rayIndex() {
        return (int) rayCast.getInput();
    }

    private int towerIndex() {
        return (int) tower.getInput();
    }

    private int yawOffsetIndex() {
        return (int) yawOffset.getInput();
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    public void onEnable() {
        if (mc.thePlayer == null) {
            return;
        }
        active = false;
        blockSlot = -1;
        oldSlot = mc.thePlayer.inventory.currentItem;
        startY = Math.floor(mc.thePlayer.posY);
        tellyTicks = 0;
        offGroundTicks = 0;
        onGroundTicks = 0;
        blocksPlaced = 0;
        placeCooldown = 0;
        sneakTicks = -1;
        blocksUntilSneak = 0;
        targetBlock = null;
        data = null;
        targetYaw = mc.thePlayer.rotationYaw - 180.0f + yawOffsetDegrees();
        targetPitch = 90.0f;
        currentYaw = targetYaw;
        currentPitch = targetPitch;
        randomYawOffset = (float) ((Math.random() - 0.5) * 3.0);
        randomPitchOffset = (float) ((Math.random() - 0.5) * 3.0);
        canPlace = true;
        placedThisTick = false;
    }

    public void onDisable() {
        if (mc.thePlayer != null) {
            mc.thePlayer.movementInput.sneak = false;
            if (oldSlot != -1 && mc.thePlayer.inventory.currentItem != oldSlot) {
                PlayerUtils.setSlot(oldSlot, true);
            }
        }
        oldSlot = -1;
        active = false;
        data = null;
        targetBlock = null;
        blocksPlaced = 0;
        sneakTicks = -1;
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
        blockSlot = slot;
        if (mc.thePlayer.inventory.currentItem != blockSlot) {
            PlayerUtils.setSlot(blockSlot, true);
        }
        if (mc.thePlayer.onGround) {
            startY = Math.floor(mc.thePlayer.posY);
        }

        applySprint();
        applySameY();
        computeTarget();
        computeCanPlace();
        updateRotation();
        place();
        applySneak();
        if (placeCooldown > 0) {
            --placeCooldown;
        }
        ++renderTicks;
    }

    private void begin() {
        active = true;
        oldSlot = mc.thePlayer.inventory.currentItem;
        startY = Math.floor(mc.thePlayer.posY);
        currentYaw = targetYaw = mc.thePlayer.rotationYaw - 180.0f + yawOffsetDegrees();
        currentPitch = targetPitch = 90.0f;
        blocksPlaced = 0;
        placeCooldown = 0;
        sneakTicks = -1;
        blocksUntilSneak = 0;
    }

    private void stop() {
        active = false;
        if (mc.thePlayer != null) {
            mc.thePlayer.movementInput.sneak = false;
            if (oldSlot != -1 && mc.thePlayer.inventory.currentItem != oldSlot) {
                PlayerUtils.setSlot(oldSlot, true);
            }
        }
        oldSlot = -1;
        data = null;
        targetBlock = null;
        canPlace = false;
        blocksPlaced = 0;
    }

    private float yawOffsetDegrees() {
        String name = yawOffset.options == null ? "0" : yawOffset.options[yawOffsetIndex()];
        return Float.parseFloat(name);
    }

    // ------------------------------------------------------------------ //
    // Target / modes
    // ------------------------------------------------------------------ //

    private void computeTarget() {
        double baseY = mc.thePlayer.getEntityBoundingBox().minY;
        boolean keepLayer = sameYIndex() != SAME_Y_OFF || modeIndex() != MODE_NORMAL;
        if (keepLayer && !mc.gameSettings.keyBindJump.isKeyDown() && !towering()) {
            baseY = startY;
        }
        targetBlock = new BlockPos(mc.thePlayer.posX, baseY, mc.thePlayer.posZ).down();
        data = risePlaceData(targetBlock);
        if (data == null) {
            data = PlaceData.INVALID;
        }
    }

    private void computeCanPlace() {
        canPlace = true;
        if (!data.getValid()) {
            canPlace = false;
            return;
        }
        switch (modeIndex()) {
            case MODE_TELLY:
                canPlace = tellyReady();
                break;
            case MODE_SNAP:
                canPlace = !mc.thePlayer.onGround || mc.thePlayer.motionY < -0.1;
                break;
            case MODE_EAGLE:
                canPlace = !mc.thePlayer.isSneaking();
                break;
            default:
                break;
        }
        if (tellyOnlyOnRightClick.isToggled() && modeIndex() == MODE_TELLY && !mc.gameSettings.keyBindUseItem.isKeyDown()) {
            canPlace = false;
        }
    }

    private boolean tellyReady() {
        if (mc.thePlayer.onGround) {
            return false;
        }
        if (modeIndex() != MODE_TELLY) {
            return true;
        }
        if (offGroundTicks >= 1 + tellyTicks) {
            return true;
        }
        return mc.thePlayer.getEntityBoundingBox().minY < startY - 0.5;
    }

    private void applySameY() {
        if (sameYIndex() == SAME_Y_AUTO_JUMP && mc.thePlayer.onGround && PlayerUtils.isMoving()
                && !mc.gameSettings.keyBindJump.isKeyDown() && !towering()) {
            PlayerUtils.jump();
        }
        if (modeIndex() == MODE_TELLY && mc.thePlayer.onGround && PlayerUtils.isMoving()
                && !mc.gameSettings.keyBindJump.isKeyDown()) {
            tellyTicks = MathUtils.randomInt(0, 1);
            PlayerUtils.jump();
        }
    }

    private void applySprint() {
        int index = (int) sprint.getInput();
        if (index == 0) {
            mc.thePlayer.setSprinting(false);
        } else if (index == 1) {
            mc.thePlayer.setSprinting(true);
        } else if (index == 2) {
            // Legit: only sprint while actually moving on ground, like Rise's LegitSprint.
            mc.thePlayer.setSprinting(mc.thePlayer.onGround && PlayerUtils.isMoving());
        }
    }

    private boolean towering() {
        return towerIndex() != 0 && PlayerUtils.jumpKeyDown() && !PlayerUtils.isMoving();
    }

    // ------------------------------------------------------------------ //
    // Placement search
    // ------------------------------------------------------------------ //

    private static final EnumFacing[] RISE_FACINGS = {EnumFacing.EAST, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP};
    private static final BlockPos[] RISE_OFFSETS = {
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1), new BlockPos(0, -1, 0)};

    private PlaceData risePlaceData(BlockPos target) {
        if (target == null || mc.theWorld == null) {
            return PlaceData.INVALID;
        }
        PlaceData best = null;
        double bestScore = -Double.MAX_VALUE;
        Vec3 player = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        for (int i = 0; i < RISE_OFFSETS.length; ++i) {
            BlockPos anchor = target.add(RISE_OFFSETS[i]);
            if (!isSolid(anchor)) {
                continue;
            }
            EnumFacing facing = RISE_FACINGS[i];
            // Prefer the anchor whose face points at the player: that is the block behind the
            // player's movement direction, which is what the backward Telly yaw expects to hit.
            Vec3 normal = new Vec3(facing.getFrontOffsetX(), facing.getFrontOffsetY(), facing.getFrontOffsetZ());
            Vec3 toPlayer = new Vec3(player.xCoord - (anchor.getX() + 0.5),
                    player.yCoord - (anchor.getY() + 0.5), player.zCoord - (anchor.getZ() + 0.5));
            double length = toPlayer.lengthVector();
            double score = length < 1.0E-6 ? 0.0 : normal.dotProduct(toPlayer) / length;
            if (score > bestScore) {
                bestScore = score;
                best = new PlaceData(anchor, facing);
            }
        }
        if (best != null) {
            return best;
        }
        for (int i = 0; i < RISE_OFFSETS.length; ++i) {
            BlockPos near = target.add(RISE_OFFSETS[i]);
            for (int j = 0; j < RISE_OFFSETS.length; ++j) {
                BlockPos anchor = near.add(RISE_OFFSETS[j]);
                if (isSolid(anchor)) {
                    return new PlaceData(anchor, RISE_FACINGS[j]);
                }
            }
        }
        return PlaceData.INVALID;
    }

    private static boolean isSolid(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        return block.getMaterial().isSolid() && block != Blocks.air
                && !block.getMaterial().isReplaceable()
                && !BlockUtils.isInteractable(block);
    }

    // ------------------------------------------------------------------ //
    // Rotation
    // ------------------------------------------------------------------ //

    private void updateRotation() {
        if (!data.getValid()) {
            return;
        }
        float yaw;
        float pitch;
        float[] face = RotationUtils.toBlock(data.blockPos, data.facing);
        if (!PlayerUtils.isMoving()) {
            yaw = face[0];
            pitch = face[1];
        } else if (modeIndex() == MODE_GODBRIDGE) {
            yaw = Math.round((PlayerUtils.movementAngle() + 180.0f) / 45.0f) * 45.0f;
            pitch = (float) MathUtils.randomBetween(74.0, 82.0);
        } else if (modeIndex() == MODE_TELLY) {
            yaw = PlayerUtils.movementAngle() + 180.0f + randomYawOffset;
            if (offGroundTicks <= 1) {
                pitch = 90.0f;
            } else if (mc.thePlayer.motionY > 0.0) {
                pitch = (float) MathUtils.randomBetween(82.0, 88.0);
            } else {
                pitch = (float) MathUtils.randomBetween(72.0, 80.0);
            }
        } else {
            yaw = PlayerUtils.movementAngle() + 180.0f + yawOffsetDegrees() + randomYawOffset;
            pitch = 90.0f;
        }
        if (upSideDown.isToggled()) {
            pitch = -pitch;
        }
        if (!hitsFace(yaw, pitch)) {
            float searched = findPitch(yaw);
            if (!Float.isNaN(searched)) {
                pitch = searched;
            } else {
                yaw = face[0];
                pitch = face[1];
            }
        }
        targetYaw = yaw;
        targetPitch = pitch;
        float speed = (float) MathUtils.randomBetween(rotationSpeedMin.getInput(), rotationSpeedMax.getInput()) * 9.0f;
        currentYaw = approach(currentYaw, targetYaw, speed);
        currentPitch = approach(currentPitch, targetPitch, speed);
        currentPitch = MathHelper.clamp_float(currentPitch, -90.0f, 90.0f);
    }

    private float findPitch(float yaw) {
        float pitch = 90.0f;
        while (pitch >= 40.0f) {
            if (hitsFace(yaw, pitch)) {
                return pitch;
            }
            pitch -= 2.5f;
        }
        return Float.NaN;
    }

    private boolean hitsFace(float yaw, float pitch) {
        return data.getValid() && PlayerUtils.rayTraceBlock(data.blockPos, data.facing,
                mc.playerController.getBlockReachDistance() + expandReach(), yaw, pitch) != null;
    }

    private double expandReach() {
        return advanced.isToggled() ? expand.getInput() * 0.25 : 0.0;
    }

    private static float approach(float from, float to, float speed) {
        float diff = MathHelper.wrapAngleTo180_float(to - from);
        if (Math.abs(diff) <= speed) {
            return to;
        }
        return from + Math.signum(diff) * speed;
    }

    public float[] serverRotation() {
        if (!active || !data.getValid() || !canPlace) {
            return null;
        }
        return new float[]{currentYaw, currentPitch};
    }

    // ------------------------------------------------------------------ //
    // Place
    // ------------------------------------------------------------------ //

    private void place() {
        if (!canPlace || !data.getValid() || placeCooldown > 0 || mc.playerController == null) {
            return;
        }
        int ray = rayIndex();
        boolean falling = !mc.thePlayer.onGround && mc.thePlayer.motionY < -0.1;
        boolean strict = ray == RAY_STRICT && !(bypassRaycastWhenFalling.isToggled() && falling);
        double reach = mc.playerController.getBlockReachDistance() + expandReach();
        MovingObjectPosition hit = null;
        if (strict || ray == RAY_NORMAL) {
            hit = PlayerUtils.rayTraceBlock(data.blockPos, data.facing, reach, currentYaw, currentPitch);
            if (hit == null) {
                // The smoothing lags a tick behind; fall back to the exact target angle that was
                // already verified to hit this face, so a strict raycast cannot stall placement.
                hit = PlayerUtils.rayTraceBlock(data.blockPos, data.facing, reach, targetYaw, targetPitch);
            }
            if (hit == null) {
                return;
            }
        }
        Vec3 facePoint = facePoint(data);
        MovingObjectPosition placement = hit != null ? hit
                : new MovingObjectPosition(MovingObjectPosition.MovingObjectType.BLOCK, facePoint, data.facing, data.blockPos);
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(blockSlot);
        if (stack == null || stack.stackSize <= 0) {
            return;
        }
        if (rotationModeIndex() == 1) {
            // Rise's Grim path: send the look, place, then restore the player's own rotation.
            PacketUtils.sendNoEvent(new C03PacketPlayer.C05PacketPlayerLook(currentYaw, currentPitch, mc.thePlayer.onGround));
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, stack, data.blockPos, data.facing, facePoint)) {
                PlayerUtils.swing(true, true);
            }
            PacketUtils.sendNoEvent(new C03PacketPlayer.C05PacketPlayerLook(
                    mc.thePlayer.rotationYaw + (float) (Math.random() * 0.03),
                    MathHelper.clamp_float(mc.thePlayer.rotationPitch - (float) Math.random(), -90.0f, 90.0f),
                    mc.thePlayer.onGround));
        } else {
            PlayerUtils.place(placement, stack);
            PlayerUtils.swing(false, true);
        }
        ++blocksPlaced;
        placeCooldown = MathUtils.randomInt((int) placeDelayMin.getInput(), (int) placeDelayMax.getInput());
        if (towering() && mc.thePlayer.onGround) {
            PlayerUtils.jump();
        }
    }

    private int rotationModeIndex() {
        return (int) rotationMode.getInput();
    }

    private Vec3 facePoint(PlaceData placeData) {
        BlockPos pos = placeData.blockPos;
        return new Vec3(pos.getX() + 0.5 + placeData.facing.getFrontOffsetX() * 0.5,
                pos.getY() + 0.5 + placeData.facing.getFrontOffsetY() * 0.5,
                pos.getZ() + 0.5 + placeData.facing.getFrontOffsetZ() * 0.5);
    }

    // ------------------------------------------------------------------ //
    // Sneak / tower / misc
    // ------------------------------------------------------------------ //

    private void applySneak() {
        if (sneak.isToggled()) {
            if (sneakTicks >= 0) {
                if (sneakTicks >= (int) stopSneaking.getInput()) {
                    mc.thePlayer.movementInput.sneak = false;
                    sneakTicks = -1;
                } else {
                    ++sneakTicks;
                }
            } else if (blocksPlaced >= blocksUntilSneak + (int) sneakEvery.getInput()) {
                int delay = -(sneakTicks + 1);
                if (delay < (int) startSneaking.getInput()) {
                    sneakTicks = -(delay + 1);
                } else {
                    blocksUntilSneak = blocksPlaced;
                    sneakTicks = 0;
                    mc.thePlayer.movementInput.sneak = true;
                }
            }
            if (mc.thePlayer.movementInput.sneak && PlayerUtils.isMoving()) {
                mc.thePlayer.movementInput.moveForward *= (float) sneakingSpeed.getInput();
                mc.thePlayer.movementInput.moveStrafe *= (float) sneakingSpeed.getInput();
            }
        }
        if (safeWalk.isToggled() && mc.thePlayer.onGround && PlayerUtils.nearEdge(0.25)
                && !mc.gameSettings.keyBindSneak.isKeyDown()) {
            mc.thePlayer.movementInput.sneak = true;
        }
    }

    public void postInput() {
        if (!active) {
            return;
        }
        if (towerIndex() == 2 && towering() && !mc.thePlayer.isPotionActive(net.minecraft.potion.Potion.jump)) {
            mc.thePlayer.motionY = 0.42;
        }
    }

    public void render3d() {
        if (!active || !render.isToggled() || !data.getValid()) {
            return;
        }
        Render3D.block(data.blockPos, RenderUtils.withAlpha(Theme.scheme().primary, 120), true, true);
    }

    // ------------------------------------------------------------------ //
    // Slots
    // ------------------------------------------------------------------ //

    private int findBlockSlot() {
        if (mc.thePlayer == null) {
            return -1;
        }
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(mc.thePlayer.inventory.currentItem);
        if (ItemUtils.isScaffoldBlock(held)) {
            return mc.thePlayer.inventory.currentItem;
        }
        for (int slot = 0; slot <= 8; ++slot) {
            if (ItemUtils.isScaffoldBlock(mc.thePlayer.inventory.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    public String debugState() {
        if (mc.thePlayer == null) {
            return "no-player";
        }
        return "active=" + active + " slot=" + blockSlot + " can=" + canPlace
                + " data=" + (data == null || !data.getValid() ? "null" : data.blockPos + "/" + data.facing)
                + " rot=" + (int) currentYaw + "/" + (int) currentPitch
                + " yaw=" + (int) mc.thePlayer.rotationYaw + " srv=" + (int) RotationManager.serverYaw
                + " rotating=" + RotationManager.rotating + " noFix=" + RotationManager.scaffold2NoMovementFix
                + " moveAngle=" + (int) PlayerUtils.movementAngle()
                + " placed=" + blocksPlaced + " startY=" + startY
                + " y=" + mc.thePlayer.posY + " z=" + mc.thePlayer.posZ
                + " fwd=" + mc.thePlayer.movementInput.moveForward + " jump=" + mc.thePlayer.movementInput.jump
                + " sneak=" + mc.thePlayer.movementInput.sneak;
    }

    private static final class PlaceData {
        static final PlaceData INVALID = new PlaceData(null, null);
        final BlockPos blockPos;
        final EnumFacing facing;

        PlaceData(BlockPos blockPos, EnumFacing facing) {
            this.blockPos = blockPos;
            this.facing = facing;
        }

        boolean getValid() {
            return blockPos != null && facing != null;
        }
    }
}
