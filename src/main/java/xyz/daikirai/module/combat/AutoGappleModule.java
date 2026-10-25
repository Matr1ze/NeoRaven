package xyz.daikirai.module.combat;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.mixin.IEntityPlayerSP;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.PacketUtils;
import xyz.daikirai.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Port of Moonlight's {@code AutoGapple} (OldGrimModule/Gapple) for this client.
 *
 * <p>The module eats a golden apple out of the hotbar without holding right click: while it lags,
 * every outgoing packet except a small whitelist is queued, so the server sees the player frozen
 * and then receives the held movement packets, a slot change to the apple and the use-item packet
 * in one burst. The queue is flushed either when the configured number of held {@code C03}s is
 * reached ({@link #PACKET_TARGET}) or on the "use count" schedule.</p>
 *
 * <p>The original drew its own bar in the middle of the screen; that bar is intentionally not
 * ported. Progress is exposed through {@link #progress()} / {@link #moving()} and the Dynamic
 * Island renders it with a Material 3 wavy progress indicator instead.</p>
 */
public class AutoGappleModule extends Module {
    /** {@code Mode}: the health-gated Moonlight behaviour. */
    public static final int MODE_MOONLIGHT = 0;
    /** {@code Mode}: SilenceFix's manual "eat whenever the module is on" behaviour. */
    public static final int MODE_SILENCE = 1;

    /** {@code After eat}: put the previous slot back. */
    public static final int AFTER_EAT_RESTORE = 0;
    /** {@code After eat}: always switch to {@code After eat: slot} after an apple. */
    public static final int AFTER_EAT_FIXED = 1;

    /** Bounds and default of "Hold packets": the withheld C03 count the island gauge runs to. */
    public static final int MIN_PACKETS = 3;
    public static final int MAX_PACKETS = 40;
    public static final int DEFAULT_PACKETS = 32;
    /** Non-motion packets are released this often so keepalives/transactions never stall. */
    private static final int KEEPALIVE_TICKS = 5;
    /** "Smart mode" waits at most this many ticks for our hit to land before using anyway. */
    private static final int SMART_WAIT_LIMIT = 10;
    /**
     * Ticks the use packet must stay untouched after an eat: the server finishes the apple after
     * the 32-tick use duration, and a second {@code C08} before that resets {@code itemInUseCount},
     * so the apple would never actually be consumed.
     */
    private static final int EAT_COOLDOWN = 34;

    public static AutoGappleModule instance;

    private static SliderSetting mode, delay, health, holdPackets, afterEat, afterEatSlot;
    private static ButtonSetting smartMode, stopMove, alwaysAttack, autoClose, lagInAir, invalidC03;

    /** 0..1 fraction of held movement packets, read by the island gauge. */
    private static float progress;
    /** Number of held movement packets (the gauge's counter). */
    private static int heldMoves;
    /** True while the module is holding packets for an apple. */
    private static boolean eating;
    /** Dev harness seam: pins the gauge state and keeps the tick logic out of the way. */
    private static boolean previewing;

    private final Deque<Packet<?>> held = new ArrayDeque<Packet<?>>();
    private long lastUse;
    private int slot = -1;
    /** Slot to return to once the selected apple finished its 32-tick use. */
    private int restoreSlot = -1;
    /** Slot the apple was actually eaten from (detects a manual hotbar change mid-eat). */
    private int eatSlot = -1;
    private boolean needSkip;
    private int smartWait;
    private int eatCooldown;
    private float lastYaw;
    private float lastPitch;

    public AutoGappleModule(int key) {
        super("AutoGapple", Category.combat, key);
        instance = this;
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    public void link() {
        mode = (SliderSetting) ModuleManager.setting(this, "Mode");
        delay = (SliderSetting) ModuleManager.setting(this, "Delay");
        health = (SliderSetting) ModuleManager.setting(this, "Health");
        holdPackets = (SliderSetting) ModuleManager.setting(this, "Hold packets");
        afterEat = (SliderSetting) ModuleManager.setting(this, "After eat");
        afterEatSlot = (SliderSetting) ModuleManager.setting(this, "After eat: slot");
        smartMode = (ButtonSetting) ModuleManager.setting(this, "Smart mode");
        stopMove = (ButtonSetting) ModuleManager.setting(this, "Stop move when eating");
        alwaysAttack = (ButtonSetting) ModuleManager.setting(this, "Always attack");
        autoClose = (ButtonSetting) ModuleManager.setting(this, "Close when no golden apple");
        lagInAir = (ButtonSetting) ModuleManager.setting(this, "Lag when in air");
        invalidC03 = (ButtonSetting) ModuleManager.setting(this, "Send invalid C03");
    }

    // ------------------------------------------------------------------ //
    // Island / HUD accessors
    // ------------------------------------------------------------------ //

    public static boolean isEating() {
        return eating;
    }

    public static float progress() {
        return progress;
    }

    public static int moving() {
        return heldMoves;
    }

    /** {@code Mode}: 0 = Moonlight (health gated), 1 = SilenceFix (manual, always eating). */
    public static int mode() {
        return mode == null ? MODE_MOONLIGHT : (int) Math.round(mode.getInput());
    }

    public static boolean silenceMode() {
        return mode() == MODE_SILENCE;
    }

    /** Withheld C03 count the module waits for and the island gauge fills to. */
    public static int targetPackets() {
        if (holdPackets == null) {
            return DEFAULT_PACKETS;
        }
        int value = (int) Math.round(holdPackets.getInput());
        return Math.max(MIN_PACKETS, Math.min(MAX_PACKETS, value));
    }

    /** KillAura keeps attacking while the apple is being eaten when "Always attack" is on. */
    public static boolean keepsAttacking() {
        return instance != null && instance.isEnabled() && eating && alwaysAttack != null
                && alwaysAttack.isToggled();
    }

    /**
     * Dev harness seam: pins the progress the island reads without a live eat, so the gauge can be
     * captured deterministically. While previewing, the tick and packet logic stay out of the way.
     */
    public static void preview(float value, int moves) {
        previewing = true;
        eating = true;
        heldMoves = Math.max(0, moves);
        progress = value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    /** Dev harness seam: ends {@link #preview(float, int)}. */
    public static void clearPreview() {
        previewing = false;
        eating = false;
        heldMoves = 0;
        progress = 0.0F;
    }

    @Override
    public String getInfo() {
        return eating ? Math.round(progress * 100.0F) + "%" : null;
    }

    // ------------------------------------------------------------------ //
    // Module lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void onEnable() {
        reset();
        lastUse = System.currentTimeMillis();
    }

    @Override
    public void onDisable() {
        eatCooldown = 0;
        restoreSlot();
        eating = false;
        release();
        reset();
    }

    private void reset() {
        held.clear();
        heldMoves = 0;
        progress = 0.0F;
        eating = false;
        previewing = false;
        needSkip = false;
        smartWait = 0;
        eatCooldown = 0;
        slot = -1;
        restoreSlot = -1;
        eatSlot = -1;
        lastYaw = 0.0F;
        lastPitch = 0.0F;
    }

    @SubscribeEvent
    public void onJoinWorld(EntityJoinWorldEvent e) {
        if (e.entity == mc().thePlayer) {
            eatCooldown = 0;
            restoreSlot();
            eating = false;
            release();
            reset();
            lastUse = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------ //
    // Per tick (motion pre / post)
    // ------------------------------------------------------------------ //

    @Override
    public void onPreMotion(MotionEvent e) {
        if (previewing) {
            return;
        }
        Minecraft mc = mc();
        if (mc.thePlayer == null || !mc.thePlayer.isEntityAlive()) {
            stopEating(true);
            return;
        }
        if (eatCooldown > 0) {
            --eatCooldown;
            if (eatCooldown == 0) {
                restoreSlot();
            }
        }
        boolean ready = mc.playerController != null
                && mc.playerController.getCurrentGameType().isSurvivalOrAdventure()
                && (silenceMode() || passed(delayMs()));
        if (!ready) {
            stopEating(false);
            return;
        }
        slot = appleSlot();
        // SilenceFix eats for as long as the module is on; Moonlight only below "Health".
        boolean wanted = slot != -1 && (silenceMode() || mc.thePlayer.getHealth() < (float) health.getInput());
        if (!wanted) {
            stopEating(false);
            return;
        }
        eating = true;
        // The motion queue accumulates until "Hold packets" is reached; the scheduled pass only
        // drains the non-motion packets, so the withheld C03s (and the gauge) keep climbing.
        if (heldMoves >= targetPackets() && eatCooldown == 0) {
            if (!deferForTarget()) {
                eat();
            }
        } else if (mc.thePlayer.ticksExisted % KEEPALIVE_TICKS == 0) {
            releaseNonMotion();
        }
    }

    /**
     * Leaves the eating state. While an apple is still being used the queue and the selected slot
     * are kept until the use finishes, otherwise aborting would waste the apple.
     */
    private void stopEating(boolean force) {
        if (eatCooldown > 0 && !force) {
            eating = true;
            return;
        }
        eatCooldown = 0;
        if (force) {
            restoreSlot();
        }
        if (eating) {
            eating = false;
            release();
        } else {
            clearProgress();
        }
    }

    @Override
    public void onPostPlayerInput() {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.playerController == null || !eating) {
            needSkip = false;
            return;
        }
        boolean lock = stopMove.isToggled();
        if (!lock) {
            if (invalidC03.isToggled()) {
                lock = lagInAir.isToggled();
            } else {
                lock = lagInAir.isToggled() && !needSkip && !mc.thePlayer.onGround
                        && ((IEntityPlayerSP) mc.thePlayer).getPositionUpdateTicks() < 19;
            }
        }
        if (silenceMode()) {
            // SilenceFix holds the player still for the whole eat and cancels the movement math
            // while the position updates are stale (its onMoveMath: positionUpdateTicks < 19).
            lock = true;
        }
        needSkip = false;
        if (!lock) {
            return;
        }
        // "Stop move when eating" mirrors the original's zeroed move input; the in-air variant
        // cancels the tick's movement instead of the original's MoveMath event.
        mc.thePlayer.movementInput.moveForward = 0.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.setSprinting(false);
    }

    // ------------------------------------------------------------------ //
    // Packets
    // ------------------------------------------------------------------ //

    @Override
    public boolean onSendPacket(Packet<?> packet) {
        if (previewing) {
            return true;
        }
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.getNetHandler() == null || mc.playerController == null
                || !mc.playerController.getCurrentGameType().isSurvivalOrAdventure()) {
            return true;
        }
        if (!eating) {
            return true;
        }
        if (silenceMode() && mc.isSingleplayer()) {
            // SilenceFix bails out of the lag in singleplayer. Keep the gauge moving by counting the
            // motion packets that go out normally, so the eat still happens on schedule.
            if (packet instanceof C03PacketPlayer) {
                ++heldMoves;
                updateProgress();
            }
            return true;
        }
        if (packet instanceof C00Handshake || packet instanceof C00PacketLoginStart
                || packet instanceof C00PacketServerQuery || packet instanceof C01PacketPing
                || packet instanceof C01PacketEncryptionResponse || packet instanceof C01PacketChatMessage) {
            return true;
        }
        if (packet instanceof C09PacketHeldItemChange || packet instanceof C0EPacketClickWindow
                || packet instanceof C16PacketClientStatus || packet instanceof C0DPacketCloseWindow) {
            return true;
        }
        if (silenceMode() && packet instanceof C08PacketPlayerBlockPlacement) {
            // SilenceFix lets block placements through instead of holding them.
            return true;
        }
        if (packet instanceof C03PacketPlayer) {
            C03PacketPlayer c03 = (C03PacketPlayer) packet;
            ++heldMoves;
            if (c03.getRotating()) {
                lastYaw = c03.getYaw();
                lastPitch = c03.getPitch();
            }
        } else if (invalidC03.isToggled()) {
            // With the invalid-C03 mode only the motion packet matters; everything else is dropped.
            return true;
        }
        held.addLast(packet);
        updateProgress();
        return false;
    }

    @Override
    public boolean onReceivePacket(Packet packet, INetHandler handler) {
        if (packet instanceof S12PacketEntityVelocity && mc().thePlayer != null
                && ((S12PacketEntityVelocity) packet).getEntityID() == mc().thePlayer.getEntityId()) {
            needSkip = true;
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    // Eating
    // ------------------------------------------------------------------ //

    private void eat() {
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.getNetHandler() == null) {
            return;
        }
        int current = mc.thePlayer.inventory.currentItem;
        eatSlot = slot;
        // Where the hotbar has to end up after this apple: the previous slot, or the slot the user
        // configured for every eat ("After eat: Fixed slot").
        int after = afterEatTarget(current);
        if (slot != current) {
            // The apple has to stay selected until the server finished the 32-tick use: the vanilla
            // slot change clears itemInUse, so switching straight back (like the original did) makes
            // the apple never get consumed. Restore in restoreSlot() once the cooldown expires.
            restoreSlot = after;
            PlayerUtils.setSlot(slot, true);
        } else if (after != current) {
            // The apple was already selected, so only the exit switch is pending.
            restoreSlot = after;
        }
        PacketUtils.sendNoEvent(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(slot)));
        release();
        smartWait = 0;
        eatCooldown = EAT_COOLDOWN;
        lastUse = System.currentTimeMillis();
    }

    /** Hotbar slot the module switches to once an apple finished; 0-based inventory index. */
    private int afterEatTarget(int current) {
        if (!afterEatFixed()) {
            return current;
        }
        int chosen = afterEatSlot == null ? 1 : (int) Math.round(afterEatSlot.getInput());
        return Math.max(0, Math.min(8, chosen - 1));
    }

    /**
     * Returns the hotbar to the configured slot (or to the slot the player was holding before).
     *
     * <p>With "After eat: Fixed slot" the switch is forced: HvH setups park the hotbar on the sword
     * or on the slot the armor-break module keeps selecting, and another module changing the slot
     * while the apple was in use must not cancel the return.</p>
     */
    private void restoreSlot() {
        int target = restoreSlot;
        int apple = eatSlot;
        restoreSlot = -1;
        eatSlot = -1;
        if (target < 0) {
            return;
        }
        Minecraft mc = mc();
        if (mc.thePlayer == null || mc.playerController == null) {
            return;
        }
        if (!afterEatFixed() && apple >= 0 && mc.thePlayer.inventory.currentItem != apple) {
            // Restore mode only undoes our own switch: if the player (or another module) moved the
            // hotbar by hand, do not yank it back.
            return;
        }
        PlayerUtils.setSlot(target, true);
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem != null && heldItem.getItem() instanceof ItemSword) {
            PacketUtils.sendNoEvent(new C08PacketPlayerBlockPlacement(heldItem));
        }
        // "Delay" is measured from the moment the hotbar is back on the requested slot, so the sword
        // (or the armor-break slot) is really held for that long before the next apple starts.
        lastUse = System.currentTimeMillis();
    }

    private static boolean afterEatFixed() {
        return afterEat != null && (int) Math.round(afterEat.getInput()) == AFTER_EAT_FIXED;
    }

    /**
     * "Smart mode": once the queue is full the use packet waits (bounded) for the hit we are in the
     * middle of to land, so the apple goes down right after the trade instead of during it.
     */
    private boolean deferForTarget() {
        if (!smartMode.isToggled() || silenceMode()) {
            smartWait = 0;
            return false;
        }
        EntityLivingBase target = auraTarget();
        if (target == null || target.hurtTime == 0) {
            smartWait = 0;
            return false;
        }
        return ++smartWait < SMART_WAIT_LIMIT;
    }

    private int appleSlot() {
        Minecraft mc = mc();
        if (mc.thePlayer == null) {
            return -1;
        }
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemAppleGold) {
                return i;
            }
        }
        if (silenceMode() || autoClose.isToggled()) {
            this.disable();
        }
        return -1;
    }

    private static EntityLivingBase auraTarget() {
        if (KillAuraModule.target == null) {
            return null;
        }
        return KillAuraModule.target.entity instanceof EntityLivingBase
                ? (EntityLivingBase) KillAuraModule.target.entity : null;
    }

    // ------------------------------------------------------------------ //
    // Queue handling
    // ------------------------------------------------------------------ //

    /**
     * Drains everything except the withheld motion packets. Keepalives, transactions and actions
     * keep flowing so a long hold cannot stall the connection, while the C03s stay queued for the
     * burst that precedes the use packet.
     */
    private void releaseNonMotion() {
        if (held.isEmpty()) {
            return;
        }
        Deque<Packet<?>> moves = new ArrayDeque<Packet<?>>();
        while (!held.isEmpty()) {
            Packet<?> packet = held.pollFirst();
            if (packet instanceof C03PacketPlayer) {
                moves.addLast(packet);
                continue;
            }
            PacketUtils.sendNoEvent(packet);
        }
        held.addAll(moves);
    }

    /**
     * Drains the queue. With "Send invalid C03" the held motion packets are replaced by one look
     * packet carrying the last rotation and then plain on-ground packets, exactly like the original.
     */
    private void release() {
        Minecraft mc = mc();
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            held.clear();
            heldMoves = 0;
            progress = 0.0F;
            return;
        }
        int count = 0;
        while (!held.isEmpty()) {
            Packet<?> packet = held.pollFirst();
            if (packet instanceof C01PacketChatMessage || packet instanceof C07PacketPlayerDigging
                    || packet instanceof C0DPacketCloseWindow || packet instanceof C0EPacketClickWindow) {
                continue;
            }
            if (invalidC03.isToggled()) {
                if (!(packet instanceof C03PacketPlayer)) {
                    continue;
                }
                if (count == 0) {
                    PacketUtils.sendNoEvent(new C03PacketPlayer.C05PacketPlayerLook(lastYaw, lastPitch,
                            mc.thePlayer.onGround));
                } else {
                    PacketUtils.sendNoEvent(new C03PacketPlayer(mc.thePlayer.onGround));
                }
                ++count;
                continue;
            }
            PacketUtils.sendNoEvent(packet);
        }
        heldMoves = 0;
        progress = 0.0F;
    }

    private void clearProgress() {
        heldMoves = 0;
        progress = 0.0F;
    }

    private void updateProgress() {
        float value = heldMoves / (float) Math.max(1, targetPackets());
        progress = value < 0.0F ? 0.0F : Math.min(value, 1.0F);
    }

    private boolean passed(long ms) {
        return System.currentTimeMillis() - lastUse >= ms;
    }

    private long delayMs() {
        return delay == null ? 50L : (long) delay.getInput();
    }
}
