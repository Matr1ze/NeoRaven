package xyz.daikirai.module.player;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.KeyUtils;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationSource;
import xyz.daikirai.util.WorldUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code keystrokesmod.module.impl.player.BridgeAssist} from raven-bS 16.1.
 *
 * <p>Bridging means walking backwards off the edge you are building; the module holds sneak for you
 * whenever your feet are (about to be) past the block under them, so you stop on the edge instead of
 * falling, then lets go again once you are safely back over solid ground. Everything is optional: it
 * can require the sneak key, a block in hand, looking down, and it can add a sneak window after a
 * jump.</p>
 *
 * <p><b>{@code Pre place}</b> is the part that makes this a legit scaffold rather than just an auto
 * sneak. raven-bS hooks its {@code ClientRotationEvent}, which that client also uses for the
 * placement ray — so looking roughly at the edge is enough and the block still lands on the right
 * face. This client separates the two: {@code RotationManager} only changes the angles that go out in
 * the C03 packet, while vanilla's {@code mc.objectMouseOver} (what a right click places against) is
 * computed from the real view. This port therefore does both halves:</p>
 * <ul>
 *   <li>{@link #getRotations} sends the solved angles silently (a 20% step per tick, capped at 20°
 *       per axis, so the turn stays legal);</li>
 *   <li>{@link #overrideMouseOver} ray-casts at <i>those</i> angles and installs the result as
 *       {@code mc.objectMouseOver}, so holding right click places exactly where the silent aim points
 *       and the block outline shows it. Registered in {@code Hooks.getMouseOver} next to GhostHand /
 *       AimAssist / Reach, which is the hook this client provides for exactly this.</li>
 * </ul>
 *
 * <p>Wiring differences from the original (this client has no {@code PrePlayerInputEvent} /
 * {@code ClientRotationEvent} / {@code SimulatedPlayer}): input is written from
 * {@link #onPostPlayerInput()} (the {@code updatePlayerMoveState} RETURN hook, the only place a module
 * may touch {@code movementInput}), and the edge is measured on the current box offset by the player's
 * own motion instead of raven-bS's full movement simulation.</p>
 */
public class BridgeAssistModule extends Module {

    private static final EnumFacing[] SIDES = {
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST
    };

    public static BridgeAssistModule instance;

    private SliderSetting edgeOffset, unsneakDelay, sneakOnJump;
    private ButtonSetting sneakKeyPressed, holdingBlocks, lookingDown, notMovingForward, prePlace;

    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;
    /** Tick the player was last seen standing on something. */
    private int lastGroundTick = -1;

    /** Solved pre-place aim; refreshed once per tick and shared by the rotation and the placement ray. */
    private float aimYaw;
    private float aimPitch;
    private boolean aimValid;
    private int aimTick = -1;
    /** Last pre-place failure reason, for the visual probe (and for support requests). */
    public String aimDebug = "init";

    public BridgeAssistModule(int key) {
        super("Bridge Assist", Category.player, key);
        instance = this;
    }

    @Override
    public void link() {
        prePlace = button("Pre place");
        edgeOffset = slider("Edge offset");
        unsneakDelay = slider("Unsneak delay");
        sneakOnJump = slider("Sneak on jump");
        sneakKeyPressed = button("Sneak key pressed");
        holdingBlocks = button("Holding blocks");
        lookingDown = button("Looking down");
        notMovingForward = button("Not moving forward");
    }

    private SliderSetting slider(String name) {
        return (SliderSetting) ModuleManager.setting(this, name);
    }

    private ButtonSetting button(String name) {
        return (ButtonSetting) ModuleManager.setting(this, name);
    }

    @Override
    public String getInfo() {
        double offset = edgeOffset.getInput();
        return offset == Math.rint(offset) ? Integer.toString((int) offset)
                : Double.toString(Math.round(offset * 100.0) / 100.0);
    }

    @Override
    public void onEnable() {
        sneakingFromModule = false;
        placed = false;
        forceRelease = false;
        aimValid = false;
        resetUnsneak();
    }

    @Override
    public void onDisable() {
        sneakingFromModule = false;
        forceRelease = false;
        aimValid = false;
        resetUnsneak();
    }

    // ------------------------------------------------------------------ //
    // Sneak logic (raven-bS onPrePlayerInput)
    // ------------------------------------------------------------------ //

    @Override
    public void onPostPlayerInput() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null
                || mc.thePlayer.capabilities.isFlying) {
            aimValid = false;
            return;
        }
        if (prePlace.isToggled() && mc.thePlayer.ticksExisted != aimTick) {
            aimTick = mc.thePlayer.ticksExisted;
            updateAim();
        }
        // raven-bS reads the sneak/jump state from its PrePlayerInputEvent, i.e. the values vanilla
        // just derived from the keys. This hook (updatePlayerMoveState RETURN) has exactly those
        // values in movementInput, so read them before this method starts writing to it.
        boolean manualSneak = mc.thePlayer.movementInput.sneak;
        boolean requireSneak = sneakKeyPressed.isToggled();
        if (manualSneak && !requireSneak) {
            resetUnsneak();
            return;
        }
        float forward = mc.thePlayer.movementInput.moveForward;
        float strafe = mc.thePlayer.movementInput.moveStrafe;
        if (requireSneak && (!manualSneak || (forward == 0.0F && strafe == 0.0F))) {
            if (!manualSneak) {
                resetUnsneak();
            }
            repressSneak();
            return;
        }
        if (notMovingForward.isToggled() && forward > 0.0F) {
            clearSneak();
            return;
        }
        if (lookingDown.isToggled() && mc.thePlayer.rotationPitch < 70.0F) {
            clearSneak();
            return;
        }
        if (holdingBlocks.isToggled() && !holdingBlock()) {
            clearSneak();
            return;
        }
        if (mc.thePlayer.onGround) {
            lastGroundTick = mc.thePlayer.ticksExisted;
        }
        // raven-bS sees the jump key before vanilla applies it, so onGround is still true there.
        // This hook runs after the jump was applied, so an ascending player that was grounded on the
        // previous tick counts as "on ground" for the jump window and the no-support branch.
        boolean ground = mc.thePlayer.onGround
                || (mc.thePlayer.motionY > 0.0D && lastGroundTick >= 0
                && mc.thePlayer.ticksExisted - lastGroundTick <= 1);
        boolean jump = mc.thePlayer.movementInput.jump;
        if (jump && ground && (forward != 0.0F || strafe != 0.0F) && sneakOnJump.getInput() > 0.0D) {
            if (!requireSneak || forceRelease) {
                sneakJumpStartTick = mc.thePlayer.ticksExisted;
                sneakJumpDelayTicks = randomTicks(sneakOnJump.getInput(), 0.0D);
                pressSneak(true);
                return;
            }
        }
        double offset = computeEdgeOffset(predictedBox());
        if (Double.isNaN(offset)) {
            if (jump && (sneakOnJump.getInput() <= 0.0D || (forward == 0.0F && strafe == 0.0F))) {
                if (sneakingFromModule) {
                    tryReleaseSneak(true);
                }
            } else if (ground) {
                pressSneak(true);
            } else if (sneakingFromModule) {
                tryReleaseSneak(true);
            }
            return;
        }
        if (offset > edgeOffset.getInput()) {
            pressSneak(true);
        } else if (sneakingFromModule) {
            tryReleaseSneak(true);
        }
    }

    // ------------------------------------------------------------------ //
    // Pre place: silent aim + the placement ray that follows it
    // ------------------------------------------------------------------ //

    /** {@code ClientRotationEvent}: the silent rotation that goes out with the player's packets. */
    @Override
    public float[] getRotations(float yaw, float pitch) {
        if (!aimValid || !prePlaceActive()) {
            return null;
        }
        float dYaw = MathHelper.wrapAngleTo180_float(aimYaw - yaw);
        float dPitch = aimPitch - pitch;
        float stepYaw = MathHelper.clamp_float(dYaw * 0.2F, -20.0F, 20.0F);
        float stepPitch = MathHelper.clamp_float(dPitch * 0.2F, -20.0F, 20.0F);
        return new float[]{yaw + stepYaw, pitch + stepPitch};
    }

    /**
     * The half raven-bS got for free: make vanilla's block ray use the silent aim, so a right click
     * places against the face the module chose instead of whatever the real view points at.
     */
    @Override
    public boolean overrideMouseOver() {
        if (!aimValid || !prePlaceActive()) {
            return false;
        }
        float yaw;
        float pitch;
        if (RotationManager.rotating && RotationManager.source == RotationSource.BRIDGEASSIST) {
            yaw = RotationManager.serverYaw;
            pitch = RotationManager.serverPitch;
        } else {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        MovingObjectPosition hit = WorldUtils.rayTraceBlocks(
                mc.playerController.getBlockReachDistance(), yaw, pitch);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return false;
        }
        mc.objectMouseOver = hit;
        mc.pointedEntity = null;
        return true;
    }

    private boolean prePlaceActive() {
        return instance != null && instance.isEnabled() && prePlace.isToggled()
                && mc.thePlayer != null && mc.theWorld != null && mc.currentScreen == null
                && !mc.thePlayer.capabilities.isFlying && holdingBlock()
                && (!lookingDown.isToggled() || mc.thePlayer.rotationPitch >= 70.0F)
                && (!notMovingForward.isToggled() || mc.thePlayer.movementInput.moveForward <= 0.0F);
    }

    /** Recomputes the pre-place aim for this tick; a miss clears it so nothing stale is sent. */
    private void updateAim() {
        aimValid = false;
        if (!prePlaceActive()) {
            aimDebug = "inactive";
            return;
        }
        FaceTarget target = findTarget();
        if (target == null) {
            return;
        }
        aimYaw = target.yaw;
        aimPitch = target.pitch;
        aimValid = true;
    }

    /**
     * Picks a free side face of a block under the feet and returns the angles to it.
     *
     * <p>The face is chosen geometrically rather than by ray-casting at the player's own view:
     * raven-bS scanned pitches because its rotation event also drove the placement ray, while this
     * client needs the aim and the placement to agree even when the view is nowhere near the block.
     * The placement itself is built from the face directly (see {@link #placeOnce()}), so it lands in
     * the hole regardless of where the crosshair really points — the silent rotation only has to make
     * it look legitimate.</p>
     */
    private FaceTarget findTarget() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double reach = mc.playerController.getBlockReachDistance();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        int standY = MathHelper.floor_double(box.minY) - 1;
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ);
        List<FaceTarget> targets = new ArrayList<FaceTarget>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos support = new BlockPos(x, standY, z);
                if (BlockUtils.isReplaceable(support)) {
                    continue;
                }
                for (EnumFacing face : SIDES) {
                    BlockPos placeAt = support.offset(face);
                    if (!BlockUtils.isReplaceable(placeAt)) {
                        continue;
                    }
                    targets.add(new FaceTarget(support, face, faceCenter(support, face), placeAt));
                }
            }
        }
        if (targets.isEmpty()) {
            aimDebug = "no targets";
            return null;
        }
        FaceTarget best = null;
        float bestScore = Float.MAX_VALUE;
        for (FaceTarget target : targets) {
            double dx = target.point.xCoord - eyes.xCoord;
            double dy = target.point.yCoord - eyes.yCoord;
            double dz = target.point.zCoord - eyes.zCoord;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > reach || distance < 1.0E-4D) {
                continue;
            }
            target.yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
            target.pitch = MathHelper.clamp_float(
                    (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))), -90.0F, 90.0F);
            target.hit = new MovingObjectPosition(target.point, target.face, target.block);
            // Prefer the face the player is walking away from (that is the side a bridge grows on),
            // then the one closest to the current pitch so the silent turn stays small.
            double behind = 0.0D;
            double motion = mc.thePlayer.motionX * mc.thePlayer.motionX
                    + mc.thePlayer.motionZ * mc.thePlayer.motionZ;
            if (motion > 1.0E-4D) {
                behind = -(target.face.getFrontOffsetX() * -mc.thePlayer.motionX
                        + target.face.getFrontOffsetZ() * -mc.thePlayer.motionZ);
            }
            float score = Math.abs(target.pitch - mc.thePlayer.rotationPitch) + (float) (behind * 20.0D);
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }
        aimDebug = best == null ? "no reachable face" : "ok";
        return best;
    }

    /**
     * One placement against the solved face. Called while right click is held; the vanilla click is
     * then skipped for a few ticks so a single click cannot place twice.
     */
    public boolean placeOnce() {
        FaceTarget target = findTarget();
        if (target == null || target.hit == null) {
            return false;
        }
        aimYaw = target.yaw;
        aimPitch = target.pitch;
        aimValid = true;
        aimTick = mc.thePlayer.ticksExisted;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) {
            return false;
        }
        PlayerUtils.place(target.hit, stack);
        ((xyz.daikirai.mixin.IMinecraft) mc).setRightClickDelayTimer(4);
        placed = true;
        return true;
    }

    /** Centre of one side face of a block, nudged 0.02 into the block so the ray cannot slip past it. */
    private static Vec3 faceCenter(BlockPos pos, EnumFacing face) {
        double x = pos.getX() + 0.5D + face.getFrontOffsetX() * 0.5D;
        double y = pos.getY() + 0.5D + face.getFrontOffsetY() * 0.5D;
        double z = pos.getZ() + 0.5D + face.getFrontOffsetZ() * 0.5D;
        return new Vec3(
                x - face.getFrontOffsetX() * 0.02D,
                y - face.getFrontOffsetY() * 0.02D,
                z - face.getFrontOffsetZ() * 0.02D);
    }

    // ------------------------------------------------------------------ //
    // Sneak helpers
    // ------------------------------------------------------------------ //

    private void pressSneak(boolean resetDelay) {
        PlayerUtils.setSneak(true);
        sneakingFromModule = true;
        if (resetDelay) {
            unsneakStartTick = -1;
        }
        repressSneak();
    }

    private void tryReleaseSneak(boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            unsneakDelayTicks = randomTicks(unsneakDelay.getInput(), 50.0D);
        }
        if (sneakJumpStartTick != -1 && existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(false);
            return;
        }
        if (unsneakStartTick != -1 && existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(false);
            return;
        }
        releaseSneak(resetDelay);
    }

    private void releaseSneak(boolean resetDelay) {
        if (!sneakKeyPressed.isToggled()) {
            PlayerUtils.setSneak(false);
        } else if (sneakingFromModule && isManualSneak() && (placed || !mc.thePlayer.onGround)) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            PlayerUtils.setSneak(false);
            forceRelease = true;
        } else if (forceRelease) {
            PlayerUtils.setSneak(false);
        }
        sneakingFromModule = false;
        placed = false;
        if (resetDelay) {
            resetUnsneak();
        }
    }

    private void repressSneak() {
        if (forceRelease && isManualSneak()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            PlayerUtils.setSneak(true);
        }
        forceRelease = false;
    }

    private void clearSneak() {
        sneakingFromModule = false;
        resetUnsneak();
        if (sneakKeyPressed.isToggled()) {
            repressSneak();
        }
    }

    private void resetUnsneak() {
        unsneakStartTick = -1;
        sneakJumpStartTick = -1;
        sneakJumpDelayTicks = -1;
        unsneakDelayTicks = -1;
    }

    /**
     * raven-bS turns a millisecond setting into whole ticks with random rounding:
     * {@code base = (ms - floor) / 50} plus one tick with the fractional chance.
     */
    private static int randomTicks(double millis, double floor) {
        double raw = (millis - floor) / 50.0D;
        if (raw <= 0.0D) {
            return 0;
        }
        int base = (int) raw;
        return base + (Math.random() < raw - base ? 1 : 0);
    }

    private boolean isManualSneak() {
        return KeyUtils.isDown(mc.gameSettings.keyBindSneak);
    }

    private boolean holdingBlock() {
        ItemStack held = mc.thePlayer.getHeldItem();
        return held != null && held.getItem() instanceof ItemBlock;
    }

    // ------------------------------------------------------------------ //
    // Edge measurement
    // ------------------------------------------------------------------ //

    /** Distance from the feet centre to the closest edge of the block supporting them. */
    private double computeEdgeOffset(AxisAlignedBB box) {
        AxisAlignedBB groundCheck = new AxisAlignedBB(
                box.minX, box.minY - 0.01D, box.minZ,
                box.maxX, box.minY, box.maxZ);
        List<AxisAlignedBB> groundBoxes = mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }
        double feetX = (box.minX + box.maxX) / 2.0D;
        double feetZ = (box.minZ + box.maxZ) / 2.0D;
        double minDistance = Double.MAX_VALUE;
        for (AxisAlignedBB ground : groundBoxes) {
            double closestX = Math.max(ground.minX, Math.min(feetX, ground.maxX));
            double closestZ = Math.max(ground.minZ, Math.min(feetZ, ground.maxZ));
            double distance = Math.max(Math.abs(feetX - closestX), Math.abs(feetZ - closestZ));
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance;
    }

    /** One tick of horizontal travel applied to the current box (this port's {@code SimulatedPlayer}). */
    private AxisAlignedBB predictedBox() {
        return mc.thePlayer.getEntityBoundingBox()
                .offset(mc.thePlayer.motionX, 0.0D, mc.thePlayer.motionZ);
    }

    /**
     * While the use key is held, place from the pre-tick hook (before the tick's C03, the order
     * vanilla uses) so the click lands on the silently aimed face. {@code rightClickDelayTimer} then
     * keeps vanilla's own right click from placing a second block in the same window.
     */
    @Override
    public void onPreUpdate() {
        if (!prePlace.isToggled() || !isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (mc.currentScreen != null || mc.thePlayer.capabilities.isFlying) {
            return;
        }
        if (!KeyUtils.isDown(mc.gameSettings.keyBindUseItem) || !xyz.daikirai.util.PlayerUtils.canRightClick()) {
            return;
        }
        if (!holdingBlock()) {
            return;
        }
        placeOnce();
    }

    @Override
    public boolean onSendPacket(net.minecraft.network.Packet<?> packet) {
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement place = (C08PacketPlayerBlockPlacement) packet;
            if (place.getPlacedBlockDirection() != 255 && sneakingFromModule && sneakKeyPressed.isToggled()) {
                placed = true;
            }
        }
        return true;
    }

    private static final class FaceTarget {
        final BlockPos block;
        final EnumFacing face;
        final Vec3 point;
        final BlockPos placeAt;
        float yaw;
        float pitch;
        MovingObjectPosition hit;

        FaceTarget(BlockPos block, EnumFacing face, Vec3 point, BlockPos placeAt) {
            this.block = block;
            this.face = face;
            this.point = point;
            this.placeAt = placeAt;
        }
    }
}
