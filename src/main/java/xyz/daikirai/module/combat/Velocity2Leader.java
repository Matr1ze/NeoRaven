package xyz.daikirai.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
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
 * Port of Leader-Lite's {@code Velocity} ({@code leader.module.modules.combat.Velocity}).
 *
 * <p>Four modes, in upstream order:</p>
 * <ul>
 *     <li><b>Vanilla</b> — scale the knockback (Horizontal/Vertical, 0% keeps the current motion),
 *     separately for explosions, gated by Chance and the hurt-status ("Fake check") packet.</li>
 *     <li><b>Prediction</b> — hold the knockback until it is worth releasing (delay ticks / on
 *     ground / rising-to-falling), then cut it by attacking while sprinting (Reduce), optionally
 *     with an AirPush decoy attack; the reduce window is sized from the knockback strength.</li>
 *     <li><b>GrimReduce</b> — same delay/release rules, then keep attacking for N ticks.</li>
 *     <li><b>SkipTick</b> — hold the knockback for a few ticks before letting it through, with
 *     optional "delay first until on ground" and a range gate.</li>
 * </ul>
 *
 * <p>1.8.9 mapping: {@code KnockbackEvent} → the {@code S12} receive hook (Leader mutates the packet,
 * this client cancels it and writes {@code motionX/Y/Z} — the 1.8.9 packet has no setters),
 * {@code UpdateEvent} PRE/POST → {@code onTick} / {@code getRotations} + {@code onPostPlayerInput}.
 * Not ported: {@code Stuck}/skip-freeze, {@code TimerSpeedLease}, {@code delayManager} (the held
 * packet here is the delay), {@code KillAura.velocityCanReduce}/{@code prepareVelocityAttackWindow}
 * (reduce modes 1/2 collapse into "release when sprinting") and {@code SkipTickPacketPolicy} —
 * SkipTick is therefore a delay+rotate+jump approximation, see the handoff.</p>
 */
final class Velocity2Leader {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int MODE_VANILLA = 0;
    private static final int MODE_PREDICTION = 1;
    private static final int MODE_GRIM = 2;
    private static final int MODE_SKIP = 3;

    private static final int REDUCE_ATTACK = 0;
    private static final int REDUCE_SPRINTING = 1;

    final SliderSetting mode;
    final SliderSetting chance;
    final SliderSetting horizontal;
    final SliderSetting vertical;
    final SliderSetting explosionHorizontal;
    final SliderSetting explosionVertical;
    final ButtonSetting fakeCheck;
    final ButtonSetting jump;
    final ButtonSetting delay;
    final SliderSetting delayTicks;
    final ButtonSetting airBuffer;
    final ButtonSetting reduce;
    final SliderSetting reduceMode;
    final ButtonSetting onlySprinting;
    final SliderSetting attackTimes;
    final ButtonSetting airPush;
    final ButtonSetting keepSprint;
    final ButtonSetting rotate;
    final SliderSetting rotateTicks;
    final ButtonSetting autoMove;
    final SliderSetting grimTicks;
    final SliderSetting skipTicks;
    final ButtonSetting delayFirst;
    final ButtonSetting rangeCheck;
    final SliderSetting range;

    // Vanilla
    private int chanceCounter;
    private boolean pendingExplosion;
    private boolean allowNext = true;

    // Prediction / GrimReduce
    private boolean hasReceivedVelocity;
    private int ticksSinceVelocity = -1;
    private int reduceTick;
    private int airPushLeft;
    private boolean delayFlag;
    private S12PacketEntityVelocity held;
    private int heldTicks;
    private boolean jumpFlag;
    private boolean grimActive;
    private int grimTick;

    // Rotate
    private int rotateTickCounter;
    private float[] targetRotation;
    private double knockbackX;
    private double knockbackZ;

    // SkipTick
    private boolean skipHeld;
    private S12PacketEntityVelocity skipPacket;
    private int skipElapsed;
    private boolean skipWaitingGround;

    private long lastAttack;

    Velocity2Leader(Velocity2Module parent, List<Setting> target) {
        this.mode = mode("Mode", 0, "Vanilla", "Prediction", "GrimReduce", "SkipTick");
        this.chance = slider("Chance", 100, 0, 100, 1, "%");
        this.horizontal = slider("Horizontal", 100, 0, 100, 1, "%");
        this.vertical = slider("Vertical", 100, 0, 100, 1, "%");
        this.explosionHorizontal = slider("Explosions horizontal", 100, 0, 100, 1, "%");
        this.explosionVertical = slider("Explosions vertical", 100, 0, 100, 1, "%");
        this.fakeCheck = flag("Fake check", true);
        this.jump = flag("Jump", true);
        this.delay = flag("Delay", false);
        this.delayTicks = slider("Delay ticks", 1, 1, 10, 1, "t");
        this.airBuffer = flag("Delay till on ground", true);
        this.reduce = flag("Reduce", true);
        this.reduceMode = mode("Reduce mode", 0, "Attack", "ReleaseWhenSprinting");
        this.onlySprinting = flag("Only sprinting", true);
        this.attackTimes = slider("Attack times", 1, 1, 5, 1, "");
        this.airPush = flag("AirPush", false);
        this.keepSprint = flag("Keep sprint", false);
        this.rotate = flag("Rotate", false);
        this.rotateTicks = slider("Rotate ticks", 3, 1, 12, 1, "t");
        this.autoMove = flag("Auto move", false);
        this.grimTicks = slider("Grim reduce ticks", 4, 1, 10, 1, "t");
        this.skipTicks = slider("Skip tick", 3, 1, 5, 1, "t");
        this.delayFirst = flag("Delay first", false);
        this.rangeCheck = flag("Range check", true);
        this.range = slider("Range", 3.0, 0.0, 6.0, 0.1, "");
        for (Setting setting : new Setting[]{this.mode, this.chance, this.horizontal, this.vertical,
                this.explosionHorizontal, this.explosionVertical, this.fakeCheck, this.jump, this.delay,
                this.delayTicks, this.airBuffer, this.reduce, this.reduceMode, this.onlySprinting,
                this.attackTimes, this.airPush, this.keepSprint, this.rotate, this.rotateTicks, this.autoMove,
                this.grimTicks, this.skipTicks, this.delayFirst, this.rangeCheck, this.range}) {
            target.add(setting);
        }
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        return new SliderSetting(name, "", suffix, value, min, max, step, null, null);
    }

    private SliderSetting mode(String name, int value, String... options) {
        return new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
    }

    private ButtonSetting flag(String name, boolean value) {
        return new ButtonSetting(name, value);
    }

    // ------------------------------------------------------------------ //
    // Settings helpers
    // ------------------------------------------------------------------ //

    int modeIndex() {
        return (int) Math.round(mode.getInput());
    }

    private int reduceModeIndex() {
        return (int) Math.round(reduceMode.getInput());
    }

    String info() {
        int mode = modeIndex();
        if (mode == MODE_VANILLA) {
            return (int) horizontal.getInput() + "% " + (int) vertical.getInput() + "%";
        }
        return mode == MODE_PREDICTION ? "Prediction" : mode == MODE_GRIM ? "GrimReduce" : "SkipTick";
    }

    void reset() {
        chanceCounter = 0;
        pendingExplosion = false;
        allowNext = true;
        hasReceivedVelocity = false;
        ticksSinceVelocity = -1;
        reduceTick = 0;
        airPushLeft = 0;
        delayFlag = false;
        held = null;
        heldTicks = 0;
        jumpFlag = false;
        grimActive = false;
        grimTick = 0;
        rotateTickCounter = 0;
        targetRotation = null;
        knockbackX = 0.0D;
        knockbackZ = 0.0D;
        skipHeld = false;
        skipPacket = null;
        skipElapsed = 0;
        skipWaitingGround = false;
    }

    /** Upstream's reduce-window curve (1..10 ticks). */
    private static int reduceTicks(int motionX, int motionZ) {
        double knockback = Math.hypot(motionX, motionZ);
        int ticks = (int) Math.round(0.000643153527D * knockback + 2.9419087136D);
        return Math.max(1, Math.min(10, ticks));
    }

    // ------------------------------------------------------------------ //
    // Packets
    // ------------------------------------------------------------------ //

    /** @return whether the packet should be applied by vanilla. */
    boolean onVelocity(S12PacketEntityVelocity packet) {
        if (mc.thePlayer == null) {
            return true;
        }
        int mode = modeIndex();
        if (mode == MODE_VANILLA) {
            return vanilla(packet);
        }
        if (mode == MODE_SKIP) {
            return skip(packet);
        }
        return prediction(packet);
    }

    private boolean vanilla(S12PacketEntityVelocity packet) {
        if (!allowNext || !fakeCheck.isToggled()) {
            allowNext = true;
            if (pendingExplosion) {
                pendingExplosion = false;
                scale(packet, explosionHorizontal.getInput(), explosionVertical.getInput());
                return false;
            }
            return true;
        }
        chanceCounter = chanceCounter % 100 + (int) Math.round(chance.getInput());
        if (chanceCounter < 100) {
            return true;
        }
        chanceCounter = 0;
        scale(packet, horizontal.getInput(), vertical.getInput());
        return false;
    }

    /** 0% means "keep the motion the player already has", like upstream's {@code event.setX(motionX)}. */
    private void scale(S12PacketEntityVelocity packet, double horizontalPercent, double verticalPercent) {
        double scaleH = horizontalPercent / 100.0D;
        double scaleV = verticalPercent / 100.0D;
        mc.thePlayer.motionX = horizontalPercent > 0.0D
                ? packet.getMotionX() / 8000.0D * scaleH : mc.thePlayer.motionX;
        mc.thePlayer.motionZ = horizontalPercent > 0.0D
                ? packet.getMotionZ() / 8000.0D * scaleH : mc.thePlayer.motionZ;
        mc.thePlayer.motionY = verticalPercent > 0.0D
                ? packet.getMotionY() / 8000.0D * scaleV : mc.thePlayer.motionY;
        captureRotation(packet);
    }

    private boolean prediction(S12PacketEntityVelocity packet) {
        airPushLeft = reduceTicks(packet.getMotionX(), packet.getMotionZ());
        captureRotation(packet);
        boolean hold = (airBuffer.isToggled() && !mc.thePlayer.onGround)
                || (delay.isToggled() && !mc.thePlayer.onGround);
        if (hold && !isInLiquidOrWeb()) {
            held = packet;
            heldTicks = 0;
            delayFlag = true;
            return false;
        }
        hasReceivedVelocity = true;
        ticksSinceVelocity = 0;
        jumpFlag = jump.isToggled() && mc.thePlayer.onGround;
        return true;
    }

    private boolean skip(S12PacketEntityVelocity packet) {
        if (skipHeld) {
            return true;
        }
        if (rangeCheck.isToggled() && !targetInRange((float) range.getInput())) {
            return true;
        }
        skipPacket = packet;
        skipHeld = true;
        skipElapsed = 0;
        skipWaitingGround = delayFirst.isToggled() && !mc.thePlayer.onGround;
        return false;
    }

    // ------------------------------------------------------------------ //
    // Status / explosion packets
    // ------------------------------------------------------------------ //

    void onStatus(S19PacketEntityStatus packet) {
        Entity entity = packet.getEntity(mc.theWorld);
        if (entity != null && entity == mc.thePlayer && packet.getOpCode() == 2) {
            allowNext = false;
        }
    }

    void onExplosion(S27PacketExplosion packet) {
        if (modeIndex() != MODE_VANILLA) {
            return;
        }
        // 1.8.9 exposes the explosion knockback indirectly; treat any non-zero motion as "pending".
        pendingExplosion = true;
    }

    // ------------------------------------------------------------------ //
    // Tick
    // ------------------------------------------------------------------ //

    void tick() {
        if (mc.thePlayer == null) {
            reset();
            return;
        }
        int mode = modeIndex();
        if (mode == MODE_SKIP) {
            tickSkip();
            return;
        }
        if (ticksSinceVelocity >= 0) {
            ticksSinceVelocity++;
            if (ticksSinceVelocity >= 10) {
                ticksSinceVelocity = -1;
                hasReceivedVelocity = false;
                reduceTick = 0;
            }
        }
        if (delayFlag) {
            releaseHeld();
        }
        if (mode == MODE_GRIM && grimActive) {
            attackTarget(true);
            grimTick++;
            if (grimTick >= (int) Math.round(grimTicks.getInput())) {
                grimActive = false;
                grimTick = 0;
            }
        }
        if (mode == MODE_PREDICTION && hasReceivedVelocity && ticksSinceVelocity < 0) {
            // Upstream keeps the window until an attack lands; expire it with the tick counter so
            // a miss does not leave the module attacking forever.
            hasReceivedVelocity = false;
            reduceTick = 0;
            rotateTickCounter = 0;
            targetRotation = null;
        }
    }

    private void tickSkip() {
        if (!skipHeld || skipPacket == null) {
            return;
        }
        skipElapsed++;
        if (skipWaitingGround) {
            if (!mc.thePlayer.onGround) {
                return;
            }
            skipWaitingGround = false;
        }
        if (skipElapsed < (int) Math.round(skipTicks.getInput())) {
            return;
        }
        S12PacketEntityVelocity packet = skipPacket;
        skipHeld = false;
        skipPacket = null;
        apply(packet);
        jumpFlag = jump.isToggled();
        captureRotation(packet);
    }

    private void releaseHeld() {
        if (held == null) {
            delayFlag = false;
            return;
        }
        heldTicks++;
        if (!shouldRelease()) {
            return;
        }
        S12PacketEntityVelocity packet = held;
        held = null;
        delayFlag = false;
        apply(packet);
        ticksSinceVelocity = 0;
        hasReceivedVelocity = true;
        reduceTick = 0;
        jumpFlag = jump.isToggled();
        if (modeIndex() == MODE_GRIM) {
            grimActive = true;
            grimTick = 0;
        }
    }

    /** Upstream {@code shouldReleasePredictionDelay}, minus the KillAura-specific branches. */
    private boolean shouldRelease() {
        boolean onGround = mc.thePlayer.onGround;
        if (airBuffer.isToggled()) {
            return onGround || isInLiquidOrWeb();
        }
        if (!delay.isToggled()) {
            return onGround;
        }
        return isInLiquidOrWeb() || heldTicks >= (int) Math.round(delayTicks.getInput()) || onGround;
    }

    private void apply(S12PacketEntityVelocity packet) {
        mc.thePlayer.motionX = packet.getMotionX() / 8000.0D;
        mc.thePlayer.motionY = packet.getMotionY() / 8000.0D;
        mc.thePlayer.motionZ = packet.getMotionZ() / 8000.0D;
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava()
                || xyz.daikirai.util.BlockUtils.getBlock(
                        new net.minecraft.util.BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))
                        == net.minecraft.init.Blocks.web;
    }

    // ------------------------------------------------------------------ //
    // Attacks
    // ------------------------------------------------------------------ //

    /** @return whether an attack was sent. */
    private boolean attackTarget(boolean respectKeepSprint) {
        EntityPlayer target = raycastPlayer(3.0D);
        if (target == null) {
            return false;
        }
        if (onlySprinting.isToggled() && !mc.thePlayer.isSprinting()) {
            return false;
        }
        if (System.currentTimeMillis() - lastAttack < 45L) {
            return false;
        }
        lastAttack = System.currentTimeMillis();
        PlayerUtils.attack(target, false);
        hitSlowDown(respectKeepSprint);
        return true;
    }

    private void hitSlowDown(boolean respectKeepSprint) {
        if (!mc.thePlayer.isSprinting()) {
            return;
        }
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        if (!respectKeepSprint || !keepSprint.isToggled()) {
            mc.thePlayer.setSprinting(false);
        }
    }

    private EntityPlayer raycastPlayer(double reach) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition block = mc.theWorld.rayTraceBlocks(eyes, end, false, false, false);
        double blockDistance = block == null ? Double.MAX_VALUE : eyes.distanceTo(block.hitVec);
        EntityPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Object object : mc.theWorld.getEntitiesWithinAABBExcludingEntity(mc.thePlayer,
                mc.thePlayer.getEntityBoundingBox().expand(reach, reach, reach))) {
            if (!(object instanceof EntityPlayer) || object == mc.thePlayer) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;
            if (player.isDead || player.deathTime != 0) {
                continue;
            }
            MovingObjectPosition hit = player.getEntityBoundingBox().expand(0.1D, 0.1D, 0.1D)
                    .calculateIntercept(eyes, end);
            if (hit == null) {
                continue;
            }
            double distance = eyes.distanceTo(hit.hitVec);
            if (distance > reach || distance > blockDistance || distance >= bestDistance) {
                continue;
            }
            bestDistance = distance;
            best = player;
        }
        return best;
    }

    private EntityPlayer airPushTarget(double nearRange) {
        EntityPlayer farthest = null;
        double farthestDistance = 11.0D;
        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance <= nearRange) {
                return null;
            }
            if (distance < farthestDistance) {
                continue;
            }
            farthestDistance = distance;
            farthest = player;
        }
        return farthest;
    }

    /** Click handler kept for parity with upstream's "Reduce" click (hit slow-down only). */
    void onMouse(MouseEvent event) {
        if (modeIndex() != MODE_PREDICTION || !reduce.isToggled() || !hasReceivedVelocity) {
            return;
        }
        if (event.button != 0 || !event.buttonstate || mc.currentScreen != null) {
            return;
        }
        if (raycastPlayer(3.0D) != null) {
            hitSlowDown(true);
        }
    }

    /** Runs on the attack window: Prediction/Reduce attacks while the window is open. */
    void attackWindow() {
        if (modeIndex() != MODE_PREDICTION || !reduce.isToggled() || !hasReceivedVelocity) {
            return;
        }
        if (reduceTick >= (int) Math.round(attackTimes.getInput())) {
            reduceTick = 0;
            hasReceivedVelocity = false;
            return;
        }
        boolean sprinting = mc.thePlayer.isSprinting() || !onlySprinting.isToggled();
        if (!sprinting) {
            return;
        }
        if (reduceModeIndex() == REDUCE_SPRINTING && !mc.thePlayer.isSprinting()) {
            return;
        }
        if (attackTarget(false)) {
            reduceTick++;
        }
    }

    /** AirPush: attack a far player so the server sees a sprint hit without a close target. */
    void airPushWindow() {
        if (modeIndex() != MODE_PREDICTION || !reduce.isToggled() || !airPush.isToggled()
                || !hasReceivedVelocity || airPushLeft <= 0) {
            return;
        }
        if (raycastPlayer(3.0D) != null) {
            return;
        }
        EntityPlayer target = airPushTarget(3.0D);
        if (target == null) {
            return;
        }
        airPushLeft--;
        PlayerUtils.attack(target, false);
        hitSlowDown(true);
    }

    // ------------------------------------------------------------------ //
    // Rotation / movement
    // ------------------------------------------------------------------ //

    private void captureRotation(S12PacketEntityVelocity packet) {
        if (!rotate.isToggled() || packet.getMotionY() <= 0) {
            return;
        }
        knockbackX = packet.getMotionX() / 8000.0D;
        knockbackZ = packet.getMotionZ() / 8000.0D;
        if (Math.abs(knockbackX) > 0.01D || Math.abs(knockbackZ) > 0.01D) {
            rotateTickCounter = 1;
            targetRotation = RotationUtils.toVec(new Vec3(
                    mc.thePlayer.posX - knockbackX, mc.thePlayer.posY, mc.thePlayer.posZ - knockbackZ));
        }
    }

    float[] rotations(float yaw, float pitch) {
        int mode = modeIndex();
        if (mode != MODE_PREDICTION && mode != MODE_SKIP) {
            return null;
        }
        if (!rotate.isToggled() || targetRotation == null) {
            return null;
        }
        if (rotateTickCounter <= 0 || rotateTickCounter > (int) Math.round(rotateTicks.getInput())) {
            return null;
        }
        if (scaffolding()) {
            return null;
        }
        if (mode == MODE_PREDICTION) {
            rotateTickCounter++;
        }
        return new float[]{targetRotation[0], targetRotation[1]};
    }

    private static boolean scaffolding() {
        Module scaffold = ModuleManager.enabled("Scaffold");
        return scaffold != null || ModuleManager.enabled("Scaffold2") != null;
    }

    void postInput() {
        if (modeIndex() == MODE_SKIP || !autoMove.isToggled() || targetRotation == null) {
            if (jumpFlag && mc.thePlayer.onGround) {
                PlayerUtils.setJump(true);
                jumpFlag = false;
            }
            return;
        }
        if (rotateTickCounter > 0 && rotateTickCounter <= (int) Math.round(rotateTicks.getInput())) {
            PlayerUtils.setForward(1.0F);
        }
        if (jumpFlag && mc.thePlayer.onGround && mc.thePlayer.isSprinting()) {
            PlayerUtils.setJump(true);
        }
        jumpFlag = false;
    }

    /** Upstream's "target in range" gate for SkipTick: any valid player within the configured range. */
    private boolean targetInRange(float reach) {
        if (!Float.isFinite(reach) || reach < 0.0F) {
            return false;
        }
        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer || player.isDead || player.deathTime != 0) {
                continue;
            }
            if (mc.thePlayer.getDistanceToEntity(player) <= reach) {
                return true;
            }
        }
        return false;
    }

    /** Island HUD: a knockback packet is held by the delay/skip stage. */
    boolean holding() {
        return delayFlag || skipHeld;
    }

    int delayRemaining() {
        if (skipHeld) {
            return Math.max(0, (int) Math.round(skipTicks.getInput()) - skipElapsed);
        }
        if (delayFlag) {
            return Math.max(0, (int) Math.round(delayTicks.getInput()) - heldTicks);
        }
        return 0;
    }
}
