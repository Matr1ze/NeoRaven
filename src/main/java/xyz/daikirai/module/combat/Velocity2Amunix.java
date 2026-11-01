package xyz.daikirai.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationUtils;

import java.util.List;

/**
 * Port of Amunix's {@code Velocity} ({@code missu.amunix.client.features.modules.combat.Velocity}).
 *
 * <p>Upstream splits the module into three modes with a component list:</p>
 * <ul>
 *     <li><b>Vanilla</b> — multiply the incoming knockback by a per-axis factor.</li>
 *     <li><b>Cancel</b> — drop the knockback packet outright.</li>
 *     <li><b>Hypixel</b> — the interesting one: the packet is either let through or buffered, and
 *     the knockback is cut afterwards by the {@code AirPush} / {@code AttackReduce} sprint-reset
 *     attacks plus {@code JumpReset} movement. A small state machine
 *     ({@code handleVelocity} / {@code ticksSinceVelocity} / {@code attackCount}) decides how many
 *     ticks the window stays open.</li>
 * </ul>
 *
 * <p>1.20 → 1.8.9 mapping: {@code EntityVelocityUpdateS2CPacket} → {@code S12PacketEntityVelocity},
 * {@code MoveInputEvent} → {@code onPostPlayerInput} (writes {@code movementInput}),
 * {@code RotationEvent} → {@link Module#getRotations}, {@code PacketLockUtils.attackAndLock} → a
 * one-attack-per-tick guard, {@code ClientData.clientOnGround} → {@code onGround}. The anti-tamper
 * branches ({@code BranchKeys}, {@code bypassProtection}, {@code FakePlayer}) have no equivalent and
 * are dropped; {@code packetManager.freeze} becomes a held packet applied on flush.</p>
 */
final class Velocity2Amunix {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_HYPIXEL = 0;
    private static final int MODE_VANILLA = 1;
    private static final int MODE_CANCEL = 2;
    private static final int TARGET_SMART = 0;

    /** Upstream's attack/sprint-reset reach. */
    private static final double ATTACK_RANGE = 3.0D;
    /** Upstream's "illegal player" search radius for AirPush. */
    private static final double AIR_PUSH_RANGE = 11.0D;

    private final Velocity2Module parent;

    final SliderSetting mode;
    final SliderSetting motionXZ;
    final SliderSetting motionY;
    final ButtonSetting buffer;
    final SliderSetting bufferTicks;
    final ButtonSetting bufferUntilGround;
    final ButtonSetting airPush;
    final ButtonSetting attackReduce;
    final ButtonSetting jumpReset;
    final SliderSetting jumpTarget;

    private boolean handleVelocity;
    private int ticksSinceVelocity = -1;
    private int attackCount = -1;
    private boolean buffering;
    private S12PacketEntityVelocity buffered;
    private int bufferElapsed;
    private float[] jumpRotation;
    private float jumpDifference;
    private boolean needHandleRotation;
    private boolean jumping;
    private boolean wasSprinting;
    private long lastSyntheticAttack;

    Velocity2Amunix(Velocity2Module parent, List<Setting> target) {
        this.parent = parent;
        this.mode = new SliderSetting("Mode", "", "", 0, 0, 2, 1,
                new String[]{"Hypixel", "Vanilla", "Cancel"}, null);
        this.motionXZ = new SliderSetting("Motion XZ", "", "%", 0, 0, 100, 1, null, null);
        this.motionY = new SliderSetting("Motion Y", "", "%", 0, 0, 100, 1, null, null);
        this.buffer = new ButtonSetting("Buffer", true);
        this.bufferTicks = new SliderSetting("Buffer ticks", "", "t", 2, 1, 20, 1, null, null);
        this.bufferUntilGround = new ButtonSetting("Buffer until ground", false);
        this.airPush = new ButtonSetting("AirPush", true);
        this.attackReduce = new ButtonSetting("AttackReduce", true);
        this.jumpReset = new ButtonSetting("JumpReset", true);
        this.jumpTarget = new SliderSetting("JumpReset target", "", "", 0, 0, 1, 1,
                new String[]{"Smart", "Opposite"}, null);
        target.add(this.mode);
        target.add(this.motionXZ);
        target.add(this.motionY);
        target.add(this.buffer);
        target.add(this.bufferTicks);
        target.add(this.bufferUntilGround);
        target.add(this.airPush);
        target.add(this.attackReduce);
        target.add(this.jumpReset);
        target.add(this.jumpTarget);
    }

    // ------------------------------------------------------------------ //
    // Settings helpers
    // ------------------------------------------------------------------ //

    int modeIndex() {
        return (int) Math.round(mode.getInput());
    }

    boolean jumpResetOn() {
        return jumpReset.isToggled();
    }

    /** Island HUD: a knockback packet is being held by the Amunix buffer. */
    boolean holding() {
        return buffering;
    }

    /** Island HUD: how many ticks the held packet still waits. */
    int delayRemaining() {
        if (!buffering) {
            return 0;
        }
        return Math.max(0, (int) Math.round(bufferTicks.getInput()) - bufferElapsed);
    }

    String info() {
        int mode = modeIndex();
        if (mode == MODE_VANILLA) {
            return "Vanilla " + (int) motionXZ.getInput() + "% " + (int) motionY.getInput() + "%";
        }
        if (mode == MODE_CANCEL) {
            return "Cancel";
        }
        return "Hypixel";
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    void reset() {
        handleVelocity = false;
        ticksSinceVelocity = -1;
        attackCount = -1;
        buffering = false;
        buffered = null;
        bufferElapsed = -1;
        jumpRotation = null;
        jumpDifference = 0.0F;
        needHandleRotation = false;
        jumping = false;
    }

    // ------------------------------------------------------------------ //
    // Knockback packet
    // ------------------------------------------------------------------ //

    /** @return whether the packet should be applied by vanilla. */
    boolean onVelocity(S12PacketEntityVelocity packet) {
        if (mc.thePlayer == null) {
            return true;
        }
        int mode = modeIndex();
        double x = packet.getMotionX() / 8000.0D;
        double y = packet.getMotionY() / 8000.0D;
        double z = packet.getMotionZ() / 8000.0D;

        if (mode == MODE_CANCEL) {
            return false;
        }
        if (mode == MODE_VANILLA) {
            // The 1.8.9 packet has no setters, so scale and apply it ourselves (same as Expo).
            double scaleH = motionXZ.getInput() / 100.0D;
            double scaleV = motionY.getInput() / 100.0D;
            mc.thePlayer.motionX = x * scaleH;
            mc.thePlayer.motionY = y * scaleV;
            mc.thePlayer.motionZ = z * scaleH;
            return false;
        }

        // Hypixel: only real knockbacks (not fall damage / weak nudges / climbing / fluids).
        if (y <= 0.0D || Math.hypot(x, z) < 0.1D
                || mc.thePlayer.isOnLadder() || mc.thePlayer.isInWater() || mc.thePlayer.isInLava()) {
            return true;
        }
        attackCount = reduceTicks(packet.getMotionX(), packet.getMotionZ());

        if (buffer.isToggled()) {
            buffered = packet;
            buffering = true;
            bufferElapsed = 0;
            return false;
        }
        beginWindow(x, z);
        return true;
    }

    /** Upstream's curve from knockback strength to how many ticks the window stays open. */
    private static int reduceTicks(int motionX, int motionZ) {
        double knockback = Math.hypot(motionX, motionZ);
        double ticks = 0.000408163D * knockback + 0.7142857D;
        int result = (int) Math.round(ticks);
        return Math.max(1, Math.min(5, result));
    }

    private void beginWindow(double x, double z) {
        handleVelocity = true;
        ticksSinceVelocity = 0;
        if (jumpResetOn()) {
            computeJumpRotation(x, z);
        }
    }

    private void apply(S12PacketEntityVelocity packet) {
        mc.thePlayer.motionX = packet.getMotionX() / 8000.0D;
        mc.thePlayer.motionY = packet.getMotionY() / 8000.0D;
        mc.thePlayer.motionZ = packet.getMotionZ() / 8000.0D;
    }

    // ------------------------------------------------------------------ //
    // Tick: window decay + buffer flush
    // ------------------------------------------------------------------ //

    void tick() {
        if (mc.thePlayer == null) {
            reset();
            return;
        }
        wasSprinting = mc.thePlayer.isSprinting();
        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
            if (ticksSinceVelocity >= 10) {
                ticksSinceVelocity = -1;
                handleVelocity = false;
            }
            if (attackCount >= 0 && ticksSinceVelocity >= attackCount) {
                handleVelocity = false;
            }
        }
        if (!buffering) {
            return;
        }
        bufferElapsed++;
        int max = (int) Math.round(bufferTicks.getInput());
        boolean delayTillGround = bufferUntilGround.isToggled() && !scaffolding();
        boolean sprinting = mc.thePlayer.isSprinting();
        boolean cantSprint = cantSprint();
        boolean ready = delayTillGround
                ? (bufferElapsed >= max || mc.thePlayer.onGround || cantSprint)
                : (bufferElapsed >= max || sprinting || cantSprint);
        if (!ready && !mc.thePlayer.isDead) {
            return;
        }
        S12PacketEntityVelocity packet = buffered;
        buffering = false;
        buffered = null;
        bufferElapsed = -1;
        if (packet == null) {
            return;
        }
        apply(packet);
        beginWindow(packet.getMotionX() / 8000.0D, packet.getMotionZ() / 8000.0D);
    }

    private static boolean scaffolding() {
        Module scaffold = ModuleManager.enabled("Scaffold");
        if (scaffold != null) {
            return true;
        }
        return ModuleManager.enabled("Scaffold2") != null;
    }

    /** Upstream's "cannot sprint" check, mapped onto 1.8.9 movement states. */
    private boolean cantSprint() {
        if (mc.thePlayer == null) {
            return true;
        }
        return mc.thePlayer.isPotionActive(Potion.blindness)
                || mc.thePlayer.isPotionActive(Potion.moveSlowdown)
                || mc.thePlayer.isSneaking()
                || mc.thePlayer.isRiding()
                || (mc.thePlayer.getFoodStats().getFoodLevel() <= 6
                        && !mc.thePlayer.capabilities.allowFlying);
    }

    // ------------------------------------------------------------------ //
    // Click: AttackReduce / AirPush
    // ------------------------------------------------------------------ //

    void onMouse(MouseEvent event) {
        if (mc.thePlayer == null || mc.currentScreen != null) {
            return;
        }
        if (event.button != 0 || !event.buttonstate || !handleVelocity) {
            return;
        }
        if (!mc.thePlayer.isSprinting() && !wasSprinting) {
            return;
        }
        if (!attackLocked()) {
            return;
        }
        EntityPlayer raycast = raycastPlayer(ATTACK_RANGE);
        if (raycast != null) {
            if (!attackReduce.isToggled()) {
                return;
            }
            event.setCanceled(true);
            PlayerUtils.attack(raycast, false);
            mc.thePlayer.swingItem();
            mc.thePlayer.motionX *= 0.6D;
            mc.thePlayer.motionZ *= 0.6D;
            mc.thePlayer.setSprinting(false);
            return;
        }
        if (!airPush.isToggled()) {
            return;
        }
        EntityPlayer far = closestFarPlayer(AIR_PUSH_RANGE);
        if (far == null) {
            return;
        }
        event.setCanceled(true);
        PlayerUtils.attack(far, false);
        mc.thePlayer.swingItem();
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
    }

    /** One synthetic attack per tick, standing in for upstream's {@code PacketLockUtils}. */
    private boolean attackLocked() {
        long now = System.currentTimeMillis();
        if (now - lastSyntheticAttack < 45L) {
            return false;
        }
        lastSyntheticAttack = now;
        return true;
    }

    /** Straight-line entity raycast along the player's current rotation. */
    private EntityPlayer raycastPlayer(double range) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition block = mc.theWorld.rayTraceBlocks(eyes, end, false, false, false);
        double blockDistance = block == null ? Double.MAX_VALUE : eyes.distanceTo(block.hitVec);
        EntityPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        AxisAlignedBB search = mc.thePlayer.getEntityBoundingBox()
                .expand(range, range, range);
        for (Object object : mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer, search)) {
            if (!(object instanceof EntityPlayer) || object == mc.thePlayer) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;
            if (player.isDead || player.deathTime != 0) {
                continue;
            }
            AxisAlignedBB box = player.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D);
            MovingObjectPosition hit = box.calculateIntercept(eyes, end);
            if (hit == null) {
                continue;
            }
            double distance = eyes.distanceTo(hit.hitVec);
            if (distance > range || distance > blockDistance || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    /** Upstream AirPush convention: the nearest player outside 11 blocks (a sprint-reset decoy). */
    private EntityPlayer closestFarPlayer(double minimum) {
        EntityPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance >= minimum && distance < bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ //
    // Movement: JumpReset rotation + input overrides
    // ------------------------------------------------------------------ //

    /** Called from the pre-motion rotation poll; returns {@code null} when nothing is wanted. */
    float[] rotations(float yaw, float pitch) {
        if (!jumpResetOn() || !needHandleRotation || jumpRotation == null || mc.thePlayer == null) {
            return null;
        }
        // Upstream clears the request whether or not it could rotate this tick.
        needHandleRotation = false;
        if (!mc.thePlayer.onGround || Math.abs(jumpDifference) > 45.0F) {
            return null;
        }
        return new float[]{jumpRotation[0], jumpRotation[1]};
    }

    /** Called at the end of {@code updatePlayerMoveState}, i.e. before the player consumes it. */
    void postInput() {
        if (mc.thePlayer == null) {
            return;
        }
        if (handleVelocity && (airPush.isToggled() || attackReduce.isToggled())) {
            PlayerUtils.setForward(1.0F);
        }
        if (!jumpResetOn()) {
            return;
        }
        if (ticksSinceVelocity < 0 || ticksSinceVelocity >= 4 || Math.abs(jumpDifference) > 45.0F) {
            jumping = false;
            return;
        }
        if (mc.thePlayer.onGround) {
            jumping = true;
            PlayerUtils.setJump(true);
        }
        if (!jumping || jumpRotation == null) {
            return;
        }
        float[] movement = movementInput(jumpRotation[0], jumpDifference);
        if (movement[0] != 0.0F) {
            PlayerUtils.setForward(movement[0]);
        }
        if (movement[1] != 0.0F) {
            PlayerUtils.setStrafe(movement[1]);
        }
    }

    /**
     * Upstream {@code getSmartRotation}: look away from the knockback, or at the point on a nearby
     * player whose yaw is closest to that "away" direction.
     */
    private void computeJumpRotation(double velocityX, double velocityZ) {
        double length = Math.hypot(velocityX, velocityZ);
        if (length < 1.0E-4D) {
            jumpRotation = null;
            jumpDifference = 0.0F;
            needHandleRotation = false;
            return;
        }
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double awayX = -velocityX / length;
        double awayZ = -velocityZ / length;
        Vec3 awayPoint = new Vec3(mc.thePlayer.posX + awayX, mc.thePlayer.posY, mc.thePlayer.posZ + awayZ);
        float[] away = RotationUtils.toVec(awayPoint);
        float targetYaw = away[0];
        float[] best = null;
        float bestDifference = Float.MAX_VALUE;

        if (jumpTargetIndex() == TARGET_SMART) {
            for (Object object : mc.theWorld.playerEntities) {
                EntityPlayer player = (EntityPlayer) object;
                if (player == mc.thePlayer || player.isDead || player.deathTime != 0) {
                    continue;
                }
                if (mc.thePlayer.getDistanceToEntity(player) > 6.0D || boxDistance(player) > ATTACK_RANGE) {
                    continue;
                }
                Vec3[] samples = samples(player.getEntityBoundingBox());
                for (Vec3 sample : samples) {
                    double distance = eyes.distanceTo(sample);
                    if (distance > ATTACK_RANGE) {
                        continue;
                    }
                    if (distance > 2.7D && blocked(eyes, sample)) {
                        continue;
                    }
                    float[] rotation = RotationUtils.toVec(sample);
                    float difference = MathHelper.wrapAngleTo180_float(rotation[0] - targetYaw);
                    if (Math.abs(difference) < Math.abs(bestDifference)) {
                        bestDifference = difference;
                        best = rotation;
                    }
                }
            }
        }

        if (best != null) {
            jumpRotation = best;
            jumpDifference = bestDifference;
        } else {
            jumpRotation = new float[]{targetYaw, mc.thePlayer.rotationPitch};
            jumpDifference = 0.0F;
        }
        needHandleRotation = true;
    }

    private int jumpTargetIndex() {
        return (int) Math.round(jumpTarget.getInput());
    }

    /** Box centre plus the eight corners: enough for a "closest yaw" pick, and cheap. */
    private static Vec3[] samples(AxisAlignedBB box) {
        double cx = (box.minX + box.maxX) / 2.0D;
        double cy = (box.minY + box.maxY) / 2.0D;
        double cz = (box.minZ + box.maxZ) / 2.0D;
        return new Vec3[]{
                new Vec3(cx, cy, cz),
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ)
        };
    }

    private boolean blocked(Vec3 from, Vec3 to) {
        return mc.theWorld.rayTraceBlocks(from, to, false, false, false) != null;
    }

    private double boxDistance(Entity other) {
        AxisAlignedBB a = mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB b = other.getEntityBoundingBox();
        double dx = Math.max(0.0D, Math.max(a.minX - b.maxX, b.minX - a.maxX));
        double dy = Math.max(0.0D, Math.max(a.minY - b.maxY, b.minY - a.maxY));
        double dz = Math.max(0.0D, Math.max(a.minZ - b.maxZ, b.minZ - a.maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Upstream {@code computeMovementForward}: convert the rotation delta into WASD input. */
    private static float[] movementInput(float targetYaw, float difference) {
        float yaw = mc.thePlayer == null ? targetYaw : mc.thePlayer.rotationYaw;
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - yaw);
        if (difference > 22.5F) {
            deltaYaw -= 45.0F;
        } else if (difference < -22.5F) {
            deltaYaw += 45.0F;
        }
        double radians = Math.toRadians(deltaYaw);
        double x = Math.sin(radians);
        double z = Math.cos(radians);
        float forward = z > 0.707D ? 1.0F : z < -0.707D ? -1.0F : 0.0F;
        float strafe = x > 0.707D ? -1.0F : x < -0.707D ? 1.0F : 0.0F;
        return new float[]{forward, strafe};
    }
}
