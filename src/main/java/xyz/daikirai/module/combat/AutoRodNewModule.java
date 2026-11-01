package xyz.daikirai.module.combat;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.hook.Hooks;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationSource;
import xyz.daikirai.util.RotationUtils;

/**
 * Port of the Iridium script {@code AutoRodNew} (1.20.1 API, see
 * {@code https://iridium.styles.wtf/assets/1.20.1/types.d.ts}): pick the nearest visible player in
 * range, look at them, switch the hotbar to a rod, cast, wait {@code PullbackTicks} for the bobber
 * (reeling early when it actually catches someone), then hand the hotbar back.
 *
 * <p>Mapping from the script's API to this client: {@code client.registerSlider/Boolean/Color} →
 * the {@code modules.json} entry; {@code client.setRotation(yaw, pitch, speed)} → {@link #getRotations}
 * through {@link RotationManager}; {@code mc.gameMode.useItem} → {@code PlayerControllerMP#sendUseItem};
 * {@code slotManager} → {@link PlayerUtils#setSlot}; {@code mc.player.fishing} →
 * {@code mc.thePlayer.fishEntity}; {@code render.drawBox} → {@link Render3D#filledBox}.</p>
 *
 * <p><b>Anti-cheat spacing (the reason this port is not a straight copy).</b> Three rules keep the
 * client's packets in the order GrimAC expects:</p>
 * <ol>
 *   <li><b>Actions go out after the rotation packet.</b> Everything is issued from
 *       {@link #onPostMotion()}, which {@code Hooks.onUpdateWalkingPlayer} calls right after this
 *       tick's C03 was sent — so the server always reads "look, then use". Issuing the use from
 *       {@code onUpdate} (the old version) put it before that tick's rotation.</li>
 *   <li><b>At most one item action per tick, and never in a tick where something attacked.</b>
 *       {@code Hooks.lastAttackTick} records the last C02; a slot change or a use in the same tick
 *       as an attack is exactly the "aim and hit in one tick" pattern that got flagged.</li>
 *   <li><b>Yield to whoever else owns the rotation.</b> {@link RotationManager} hands the silent
 *       rotation to the first module that asks for it, and AimAssist/KillAura/KillAura2 come before
 *       this module. While another source is driving the rotation this module does nothing at all,
 *       so it can never switch items or cast on top of somebody else's aim.</li>
 * </ol>
 *
 * <p><b>Projectile model</b>, read back from 1.8.9's own {@code EntityFishHook}: the bobber leaves
 * the eye at 1.5 blocks/tick along the look vector and every tick its motion is multiplied by 0.92
 * with <b>no gravity term</b> (the 1.20 script subtracts 0.03; that difference is why the flight
 * model here is solved from first principles instead of copied). Reach: 1.5 / (1 - 0.92) = 18.75.</p>
 */
public final class AutoRodNewModule extends Module {

    public static AutoRodNewModule instance;

    /** {@code handleHookCasting(..., 1.5F, 1.0F)}: launch speed in blocks per tick. */
    private static final double HOOK_SPEED = 1.5D;
    /** Per-tick motion multiplier of the bobber (0.5 while touching ground, ignored here). */
    private static final double HOOK_DRAG = 0.92D;
    /** Maximum reach: the sum of the geometric series 1.5 * (1 + 0.92 + 0.92^2 + ...). */
    private static final double HOOK_REACH = HOOK_SPEED / (1.0D - HOOK_DRAG);
    /** Degrees the sent rotation may be off the solved aim before we deem it "aimed". */
    private static final float AIM_TOLERANCE = 1.5F;
    /** Ticks another module may hold the rotation before this one gives up for a moment. */
    private static final int YIELD_LIMIT = 20;
    /** Combat modules whose fight makes the rod step aside (the request: the built-in one too). */
    private static final String[] BUSY_MODULES = {"KillAura", "KillAura2", "AimAssist"};

    private static final int PHASE_IDLE = 0;
    private static final int PHASE_AIM = 1;
    private static final int PHASE_CAST = 2;
    private static final int PHASE_WAIT = 3;
    private static final int PHASE_RESTORE = 4;

    private SliderSetting range, fov, minTurn, maxTurn, pullback, switchDelay;
    private ButtonSetting silentRotation, keepRotation, hurtCheck, ignoreRunner, render;
    private ColorSetting color;

    private int phase;
    private int phaseStart;
    private int castTick;
    private int cooldownUntil;
    private int switchTick;
    private int yielded;
    private long slotSwitchAt;
    private EntityPlayer target;
    private int rodSlot = -1;
    private int previousSlot = -1;
    private float[] aim;
    private boolean aiming;
    /** Successful casts; the visual probe and the arraylist both read it. */
    public int casts;
    /** Why the module is not casting right now; the script exposed the same string. */
    public String reason = "disabled";

    public AutoRodNewModule(int key) {
        super("AutoRodNew", Category.combat, key);
        instance = this;
    }

    @Override
    public void link() {
        range = slider("Range");
        fov = slider("FOV");
        minTurn = slider("MinTurnSpeed");
        maxTurn = slider("MaxTurnSpeed");
        pullback = slider("PullbackTicks");
        switchDelay = slider("SlotSwitchDelay");
        silentRotation = button("SilentRotation");
        keepRotation = button("KeepRotation");
        hurtCheck = button("TargetHurtTimeCheck");
        ignoreRunner = button("IgnoreRunner");
        render = button("Render");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
    }

    private SliderSetting slider(String name) {
        return (SliderSetting) ModuleManager.setting(this, name);
    }

    private ButtonSetting button(String name) {
        return (ButtonSetting) ModuleManager.setting(this, name);
    }

    @Override
    public void onEnable() {
        casts = 0;
        reset();
    }

    @Override
    public void onDisable() {
        restoreSlot();
        reset();
    }

    private void reset() {
        phase = PHASE_IDLE;
        phaseStart = 0;
        castTick = 0;
        cooldownUntil = 0;
        switchTick = -1;
        yielded = 0;
        slotSwitchAt = 0L;
        target = null;
        rodSlot = -1;
        previousSlot = -1;
        aim = null;
        aiming = false;
    }

    // ------------------------------------------------------------------ //
    // Per-tick bookkeeping (no packets here)
    // ------------------------------------------------------------------ //

    @Override
    public void onUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            reset();
            return;
        }
        int now = mc.thePlayer.ticksExisted;
        if (!safe()) {
            if (phase != PHASE_IDLE) {
                restoreSlot();
            }
            reset();
            return;
        }
        String yielder = yielder();
        if (yielder != null) {
            // Somebody else is turning us (AimAssist, KillAura, Scaffold, ...) or is busy fighting:
            // touch nothing at all, so no item change and no cast can land on top of their aim.
            yielded++;
            reason = "yielding to " + yielder;
            if (yielded > YIELD_LIMIT) {
                abort("yielded too long to " + yielder);
            }
            return;
        }
        yielded = 0;
        if (phase == PHASE_IDLE) {
            idle(now);
            return;
        }
        if (target != null && !valid(target)) {
            abort("target lost");
            return;
        }
        switch (phase) {
            case PHASE_AIM:
                aiming = true;
                solveForTarget();
                if (!silentRotation.isToggled()) {
                    applyVisibleRotation();
                }
                if (now - phaseStart > 20) {
                    abort("aim timeout");
                    return;
                }
                if (now - phaseStart >= 1 && aligned() && selected() == rodSlot) {
                    phase = PHASE_CAST;
                }
                return;
            case PHASE_CAST:
                if (!silentRotation.isToggled()) {
                    applyVisibleRotation();
                }
                solveForTarget();
                if (selected() != rodSlot) {
                    abort("slot changed");
                    return;
                }
                if (mc.thePlayer.fishEntity != null) {
                    abort("manual hook appeared");
                    return;
                }
                if (now - phaseStart > 40) {
                    abort("cast timeout");
                }
                return;
            case PHASE_WAIT:
                if (!silentRotation.isToggled()) {
                    applyVisibleRotation();
                }
                if (keepRotation.isToggled()) {
                    solveForTarget();
                }
                waiting(now);
                return;
            case PHASE_RESTORE:
                // The C09 itself goes out from onPreUpdate, before the tick's movement packet.
                if (now - phaseStart > 20) {
                    abort("restore timeout");
                }
                return;
            default:
                reset();
        }
    }

    /**
     * The only place this module is allowed to send packets, and the reason it is {@code onPreUpdate}
     * rather than a post-tick hook: vanilla sends a use (C08) and a held-item change (C09) <b>before</b>
     * the tick's flying packet (C03). {@code ClickGuiMod} runs {@code onUpdate} at
     * {@code TickEvent.Phase.END} and {@code onPostMotion} after the C03, so either of those made every
     * cast a "post" placement — which GrimAC reads as {@code Post player block placement} /
     * {@code held item change}. {@code Hooks.preUpdate} runs from {@code EntityPlayerSP.onUpdate},
     * i.e. before this tick's C03, which is the vanilla order: C09/C08, then C03.
     *
     * <p>At most one item action per tick, and none in a tick where an attack left the client or
     * another module owns the rotation.</p>
     */
    @Override
    public void onPreUpdate() {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            return;
        }
        if (!safe()) {
            return;
        }
        int now = mc.thePlayer.ticksExisted;
        if (Hooks.lastAttackTick == now) {
            // An attack left the client this tick: a slot change or a use here is the
            // "aim + hit in one tick" pattern. Wait for a clean tick.
            reason = "waiting for a clean tick (attack)";
            return;
        }
        String yielder = yielder();
        if (yielder != null) {
            reason = "yielding to " + yielder;
            return;
        }
        switch (phase) {
            case PHASE_AIM:
                if (now - phaseStart < 1 || !aligned()) {
                    return;
                }
                if (selected() != rodSlot) {
                    // Own tick, own rotation, no attack: the only place a C09 is allowed.
                    switchToRod();
                    switchTick = now;
                    phase = PHASE_CAST;
                    phaseStart = now;
                    reason = "rod selected";
                }
                return;
            case PHASE_CAST:
                if (!aligned() || selected() != rodSlot || mc.thePlayer.fishEntity != null) {
                    return;
                }
                if (switchTick >= 0 && now - switchTick < 2) {
                    reason = "waiting for the slot change to settle";
                    return;
                }
                if (!autoSlotReady()) {
                    return;
                }
                cast();
                return;
            case PHASE_WAIT:
                if (reelReady(now)) {
                    cast();
                }
                return;
            case PHASE_RESTORE:
                restoreSlot();
                cooldownUntil = now + 2;
                reset();
                return;
            default:
        }
    }

    /** The script's {@code safe()}: never fight the player's own input or another macro. */
    private boolean safe() {
        if (mc.currentScreen != null || Overlay.screenOpen()) {
            return false;
        }
        if (!mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator()) {
            return false;
        }
        if (mc.thePlayer.isUsingItem() || mc.thePlayer.isRiding()) {
            return false;
        }
        if (mc.gameSettings.keyBindUseItem.isKeyDown()) {
            return false;
        }
        if (mc.thePlayer.fallDistance >= 3.0F) {
            return false;
        }
        return ModuleManager.enabled("AutoRod") == null
                && ModuleManager.enabled("Scaffold") == null
                && ModuleManager.enabled("Scaffold2") == null;
    }

    /**
     * Name of the module this tick has to be left alone, or null when the tick is ours.
     *
     * <p>Two cases:</p>
     * <ul>
     *   <li>another module owns this tick's silent rotation ({@link RotationManager} serves the
     *       first provider that answers, and AimAssist/KillAura/KillAura2 come before this one);</li>
     *   <li>the combat modules are actually fighting — the built-in {@code KillAura}, {@code KillAura2}
     *       or {@code AimAssist} currently has a target, or an attack left the client within the last
     *       two ticks. Rotating for a cast in that window is what made GrimAC read "aim and hit in
     *       the same tick", so the rod waits until they are quiet.</li>
     * </ul>
     */
    private String yielder() {
        if (RotationManager.rotating && RotationManager.source != RotationSource.AUTORODNEW) {
            return sourceName(RotationManager.source);
        }
        for (String name : BUSY_MODULES) {
            Module module = ModuleManager.enabled(name);
            if (module != null && module.getTarget() != null) {
                return name + " (fighting)";
            }
        }
        if (mc.thePlayer != null && Hooks.lastAttackTick >= 0
                && mc.thePlayer.ticksExisted - Hooks.lastAttackTick <= 2) {
            return "a recent attack";
        }
        return null;
    }

    /** Readable name for the reason string; the enum's own name is all caps. */
    private static String sourceName(RotationSource source) {
        switch (source) {
            case KILLAURA:
                return "KillAura";
            case KILLAURA2:
                return "KillAura2";
            case AIMASSIST:
                return "AimAssist";
            default:
                return source.name();
        }
    }

    private void cast() {
        if (mc.thePlayer.fishEntity != null) {
            return;
        }
        castTick = mc.thePlayer.ticksExisted;
        useItem();
        casts++;
        phase = PHASE_WAIT;
        phaseStart = castTick;
        reason = "hook flying";
    }

    private void abort(String why) {
        reason = why;
        restoreSlot();
        reset();
    }

    // ------------------------------------------------------------------ //
    // Sequence
    // ------------------------------------------------------------------ //

    private void idle(int now) {
        aiming = false;
        aim = null;
        if (now < cooldownUntil) {
            reason = "cooldown";
            return;
        }
        if (mc.thePlayer.fishEntity != null) {
            reason = "manual hook out";
            return;
        }
        int slot = rodSlot();
        if (slot < 0) {
            reason = "no hotbar fishing rod";
            return;
        }
        EntityPlayer candidate = selectTarget();
        if (candidate == null) {
            target = null;
            reason = "no visible player in range";
            return;
        }
        // Waiting for the target's hurt flash to end, like the script's TargetHurtTimeCheck.
        if (hurtCheck.isToggled() && candidate.hurtTime > 0
                && mc.thePlayer.getDistanceSqToEntity(candidate) <= 64.0D) {
            target = candidate;
            reason = "waiting for target hurtTime";
            return;
        }
        float[] solved = solveAim(candidate);
        if (solved == null) {
            target = null;
            reason = "target out of bobber reach";
            return;
        }
        target = candidate;
        rodSlot = slot;
        previousSlot = selected();
        aim = solved;
        aiming = true;
        if (!silentRotation.isToggled()) {
            applyVisibleRotation();
        }
        phase = PHASE_AIM;
        phaseStart = now;
        reason = "aiming";
    }

    private void waiting(int now) {
        if (selected() != rodSlot) {
            abort("slot changed");
            return;
        }
        int limit = Math.max(3, (int) Math.round(pullback.getInput()));
        if (now - castTick >= limit) {
            phase = PHASE_RESTORE;
            phaseStart = now;
            reason = "reeling in";
            return;
        }
        net.minecraft.entity.projectile.EntityFishHook hook = mc.thePlayer.fishEntity;
        if (hook == null) {
            // The bobber is spawned by the server and reaches us a tick or two after the use
            // packet, so a missing hook is only a failure once that grace period is over.
            if (now - castTick >= 5) {
                abort("hook gone");
            }
            return;
        }
        if (hook.caughtEntity != null) {
            phase = PHASE_RESTORE;
            phaseStart = now;
            reason = "caught someone, reeling in";
        }
    }

    /** True when the C08 that pulls the bobber back may be sent this tick. */
    private boolean reelReady(int now) {
        if (phase != PHASE_WAIT) {
            return false;
        }
        net.minecraft.entity.projectile.EntityFishHook hook = mc.thePlayer.fishEntity;
        if (hook == null) {
            return false;
        }
        int limit = Math.max(3, (int) Math.round(pullback.getInput()));
        return now - castTick >= limit || hook.caughtEntity != null;
    }

    /** Switches to the rod and records the tick, so the use waits for the next clean ticks. */
    private void switchToRod() {
        PlayerUtils.setSlot(rodSlot, true);
        slotSwitchAt = System.nanoTime();
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.thePlayer != null && previousSlot != selected()) {
            if (mc.thePlayer.ticksExisted == Hooks.lastAttackTick) {
                // Never move the hotbar in a tick where something attacked.
                return;
            }
            PlayerUtils.setSlot(previousSlot, true);
            slotSwitchAt = System.nanoTime();
        }
        previousSlot = -1;
    }

    private boolean autoSlotReady() {
        long elapsed = (System.nanoTime() - slotSwitchAt) / 1000000L;
        return slotSwitchAt == 0L || elapsed >= Math.max(55L, Math.min(250L, (long) switchDelay.getInput()));
    }

    private int selected() {
        return mc.thePlayer.inventory.currentItem;
    }

    private void useItem() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return;
        }
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
    }

    /** Hotbar slot with a rod, preferring the one already selected. */
    private int rodSlot() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && held.getItem() == Items.fishing_rod) {
            return selected();
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == Items.fishing_rod) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ //
    // Targeting
    // ------------------------------------------------------------------ //

    private EntityPlayer selectTarget() {
        EntityPlayer best = null;
        double bestDistance = range.getInput() * range.getInput();
        for (Object object : mc.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;
            if (!valid(player)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSqToEntity(player);
            if (distance > bestDistance) {
                continue;
            }
            if (!RotationUtils.canSee(player)) {
                continue;
            }
            float fovValue = (float) fov.getInput();
            if (fovValue > 0.0F && fovValue < 360.0F && !PlayerUtils.inFov(fovValue, player)) {
                continue;
            }
            if (ignoreRunner.isToggled() && behindTarget(player)) {
                continue;
            }
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private boolean valid(EntityPlayer player) {
        return player != mc.thePlayer
                && player.isEntityAlive()
                && !player.isSpectator()
                && !player.isInvisible()
                && !PlayerUtils.isFriend(player)
                && !Hypixel.sameTeam(player);
    }

    /** {@code IgnoreRunner}: skip players that are running away (we are behind their facing plane). */
    private boolean behindTarget(EntityPlayer player) {
        double facing = Math.toRadians(player.rotationYaw);
        double dx = mc.thePlayer.posX - player.posX;
        double dz = mc.thePlayer.posZ - player.posZ;
        return dx * -Math.sin(facing) + dz * Math.cos(facing) < -0.000001D;
    }

    // ------------------------------------------------------------------ //
    // Aiming
    // ------------------------------------------------------------------ //

    /** Re-solves the aim for the current target, keeping the previous angles if it is unreachable. */
    private void solveForTarget() {
        if (target == null || !valid(target)) {
            return;
        }
        float[] solved = solveAim(target);
        if (solved != null) {
            aim = solved;
        }
    }

    /**
     * Cast angles for 1.8.9's straight-line, drag-only bobber: the look vector itself is the flight
     * path, so this is a plain "eyes to body centre" solution. The only thing the projectile model
     * contributes is the reach check — beyond {@link #HOOK_REACH} blocks the bobber cannot get there.
     */
    private float[] solveAim(EntityPlayer player) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB box = player.getEntityBoundingBox();
        double x = (box.minX + box.maxX) / 2.0D;
        double y = box.minY + (box.maxY - box.minY) * 0.65D;
        double z = (box.minZ + box.maxZ) / 2.0D;
        double dx = x - eyes.xCoord;
        double dy = y - eyes.yCoord;
        double dz = z - eyes.zCoord;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > HOOK_REACH) {
            return null;
        }
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        return new float[]{yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F)};
    }

    /** True when the rotation that just went out is this module's solved aim. */
    private boolean aligned() {
        if (aim == null) {
            return false;
        }
        float yaw;
        float pitch;
        if (silentRotation.isToggled()) {
            if (!RotationManager.rotating || RotationManager.source != RotationSource.AUTORODNEW) {
                return false;
            }
            yaw = RotationManager.serverYaw;
            pitch = RotationManager.serverPitch;
        } else {
            yaw = mc.thePlayer.rotationYaw;
            pitch = mc.thePlayer.rotationPitch;
        }
        return Math.abs(MathHelper.wrapAngleTo180_float(aim[0] - yaw)) <= AIM_TOLERANCE
                && Math.abs(aim[1] - pitch) <= AIM_TOLERANCE;
    }

    /**
     * {@code client.setRotation(yaw, pitch, speed)}: the silent path. The caller supplies the last
     * sent rotation and this returns the next one, stepped by a randomised degree budget per tick so
     * the turn is not a teleport.
     */
    @Override
    public float[] getRotations(float lastYaw, float lastPitch) {
        if (!isEnabled() || !aiming || aim == null || !silentRotation.isToggled()) {
            return null;
        }
        // Both angles stay relative to what was sent last tick. Returning the solved aim as an
        // absolute value (the old code did when the turn was short) can jump between +179 and -179
        // for the same direction, which GrimAC reads as a 360 degree snap (AimModulo360).
        float dYaw = MathHelper.wrapAngleTo180_float(aim[0] - lastYaw);
        float pitch = MathHelper.clamp_float(aim[1], -90.0F, 90.0F);
        float dPitch = pitch - lastPitch;
        double length = Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        double step = turnSpeed();
        if (length <= step || length < 0.01D) {
            return new float[]{lastYaw + dYaw, pitch};
        }
        double factor = step / length;
        return new float[]{lastYaw + (float) (dYaw * factor), lastPitch + (float) (dPitch * factor)};
    }

    /** Degrees per tick, randomised between the two sliders like the script's turned aim. */
    private double turnSpeed() {
        double low = Math.max(1.0D, Math.min(180.0D, minTurn.getInput()));
        double high = Math.max(1.0D, Math.min(180.0D, maxTurn.getInput()));
        double min = Math.min(low, high);
        double max = Math.max(low, high);
        return min + Math.random() * (max - min);
    }

    /** Visible rotation path when {@code SilentRotation} is off. */
    private void applyVisibleRotation() {
        if (aim == null || silentRotation.isToggled()) {
            return;
        }
        float yaw = mc.thePlayer.rotationYaw;
        float pitch = mc.thePlayer.rotationPitch;
        float dYaw = MathHelper.wrapAngleTo180_float(aim[0] - yaw);
        float dPitch = aim[1] - pitch;
        double length = Math.sqrt(dYaw * dYaw + dPitch * dPitch);
        if (length <= 1.0E-4D) {
            return;
        }
        double factor = Math.min(1.0D, turnSpeed() / length);
        RotationUtils.setRotation(yaw + (float) (dYaw * factor),
                MathHelper.clamp_float(pitch + (float) (dPitch * factor), -90.0F, 90.0F));
    }

    // ------------------------------------------------------------------ //
    // Render
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || !render.isToggled() || target == null || !valid(target)) {
            return;
        }
        AxisAlignedBB box = target.getEntityBoundingBox();
        RenderManager manager = mc.getRenderManager();
        box = box.offset(-manager.viewerPosX, -manager.viewerPosY, -manager.viewerPosZ);
        int argb = color.color();
        float red = ((argb >> 16) & 255) / 255.0F;
        float green = ((argb >> 8) & 255) / 255.0F;
        float blue = (argb & 255) / 255.0F;
        float alpha = ((argb >>> 24) & 255) / 255.0F;
        if (alpha <= 0.0F) {
            alpha = 0.25F;
        }
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        Render3D.filledBox(box, red, green, blue, alpha);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
    }

    @Override
    public String getInfo() {
        return target == null ? null : target.getName();
    }
}
