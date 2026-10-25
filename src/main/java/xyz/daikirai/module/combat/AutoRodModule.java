package xyz.daikirai.module.combat;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MathUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/**
 * Port of OpenSlinky's {@code dev.slinky.module.utility.AutoRodModule}.
 *
 * <p>Throws the fishing rod at the target and puts the hotbar back on the weapon without the player
 * having to do the two-step: the rod goes out, the reel-in is waited out for roughly the time the
 * bobber needs to fly, and the previous (or best weapon) slot is restored. Snowballs and eggs can
 * stand in for the rod when {@code Allow throwables} is on, in which case there is no reel-in to
 * wait for.</p>
 *
 * <p>The phase machine and its waits are a faithful port; the rod throw itself is
 * {@code PlayerControllerMP#sendUseItem}, which syncs the held slot (C09) before the use packet
 * (C08) exactly like the vanilla right click did in the original. Two pieces of Slinky's plumbing
 * have no counterpart here and are omitted: its module action service (this client has no shared
 * action lock, and the state machine already serialises the throw) and its "swallow the use-item
 * release" hook (this port never holds the use key, so there is no release to swallow). The
 * retract estimate uses the target's live position instead of Slinky's lag-compensated server
 * position - the difference is below one tick of target movement.</p>
 *
 * <p>A key press throws once and the module switches itself off afterwards; with a hold binding it
 * keeps throwing while the key is held, which is how the original behaved.</p>
 */
public class AutoRodModule extends Module {

    /** {@code Delay} is in milliseconds; the tick timers below count 50 ms steps. */
    private static final float MS_PER_TICK = 50.0F;

    /** Slinky's phase numbering, kept so the state machine reads the same as the original. */
    private static final int PHASE_IDLE = 0;
    private static final int PHASE_SWITCH = 1;
    private static final int PHASE_THROW = 2;
    private static final int PHASE_RETRACT = 3;
    private static final int PHASE_RESTORE_NEXT = 4;
    private static final int PHASE_FINISH = 5;
    private static final int PHASE_DONE = 6;

    /** Slinky's {@code throwingDelay}: a full swing takes 20 ticks at speed 1. */
    private static final float THROW_TICKS = 20.0F;

    public static AutoRodModule instance;

    private static SliderSetting delay, throwSpeed, retractSpeed;
    private static ButtonSetting randomizeDelays, allowThrowables, requireNotMouseOver,
            alwaysSwitchToWeapon;

    private int phase;
    /** Hotbar slot to go back to once the rod is out. */
    private int previous = -1;
    /** Hotbar slot the rod (or throwable) was found in. */
    private int selected;
    /** Ticks left before the bobber is considered reeled in. */
    private int retract;
    /** Ticks left before the next phase step runs. */
    private float timer;
    /** Whether the item the sequence picked is the rod (throwables have no reel-in). */
    private boolean rod;
    /** The inventory the sequence borrowed, used to put the slot back even after a world change. */
    private InventoryPlayer owner;

    public AutoRodModule(int key) {
        super("AutoRod", Category.combat, key);
        instance = this;
    }

    @Override
    public void link() {
        delay = (SliderSetting) ModuleManager.setting(this, "Delay");
        randomizeDelays = (ButtonSetting) ModuleManager.setting(this, "Randomize delays");
        allowThrowables = (ButtonSetting) ModuleManager.setting(this, "Allow throwables");
        throwSpeed = (SliderSetting) ModuleManager.setting(this, "Throw speed");
        retractSpeed = (SliderSetting) ModuleManager.setting(this, "Retract speed");
        requireNotMouseOver = (ButtonSetting) ModuleManager.setting(this, "Require not mouse over");
        alwaysSwitchToWeapon = (ButtonSetting) ModuleManager.setting(this, "Always switch to weapon");
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void onEnable() {
        phase = PHASE_IDLE;
        previous = -1;
        owner = null;
        timer = 0.0F;
        retract = 0;
        rod = false;
    }

    @Override
    public void onDisable() {
        restore();
        phase = PHASE_IDLE;
        timer = 0.0F;
        retract = 0;
        rod = false;
    }

    // ------------------------------------------------------------------ //
    // Tick
    // ------------------------------------------------------------------ //

    /**
     * Runs before the player's own tick (the hook list in {@code Hooks} already contains "AutoRod"),
     * so the slot change and the use packet go out ahead of that tick's movement packets.
     */
    @Override
    public void onPreUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) {
            // Leaving a world mid-sequence must still hand the hotbar back.
            restore();
            phase = PHASE_IDLE;
            timer = 0.0F;
            retract = 0;
            return;
        }
        if (mc.currentScreen != null) {
            return;
        }
        tick();
    }

    private void tick() {
        InventoryPlayer inventory = mc.thePlayer.inventory;
        // Slinky re-ran the state machine up to three times per tick so a slot change could be
        // followed immediately; only the "the rod moved" branch below asks for the retry.
        for (int pass = 0; pass < 3; pass++) {
            if (blockedByMouseOver()) {
                restore();
                phase = PHASE_DONE;
                finish();
                return;
            }
            if (retract != 0) {
                retract--;
            }
            if (timer > 0.0F) {
                timer--;
                if (timer > 0.0F) {
                    finish();
                    return;
                }
            }

            int current = inventory.currentItem;
            if (phase > PHASE_SWITCH && current != selected) {
                // The hotbar was moved by hand (or by another module); abandon without yanking it back.
                previous = -1;
                owner = null;
                phase = PHASE_DONE;
                finish();
                return;
            }

            if (phase == PHASE_IDLE) {
                int slot = scan(inventory);
                if (slot < 0) {
                    phase = PHASE_DONE;
                    finish();
                    return;
                }
                owner = inventory;
                previous = current;
                selected = slot;
                if (alwaysSwitchToWeapon.isToggled()) {
                    int weapon = bestWeaponSlot(inventory, slot);
                    if (weapon != -1) {
                        previous = weapon;
                    }
                    current = previous;
                }
                phase = selected == current ? PHASE_THROW : PHASE_SWITCH;
                finish();
                return;
            }

            if (phase == PHASE_SWITCH) {
                inventory.currentItem = selected;
                phase = PHASE_THROW;
                timer += delayTicks();
                finish();
                return;
            }

            if (phase == PHASE_THROW) {
                int slot = scan(inventory);
                if (slot < 0) {
                    phase = PHASE_FINISH;
                    return;
                }
                if (slot == current) {
                    throwItem();
                    if (rod) {
                        phase = PHASE_RETRACT;
                        retract = retractTicks(eyePosition(), targetPosition(), retractSpeed());
                        timer += delayTicks();
                        return;
                    }
                    if (!isHoldBind()) {
                        phase = PHASE_FINISH;
                        timer += delayTicks();
                    } else {
                        timer += throwingDelayTicks();
                    }
                    return;
                }
                // A different throwable/rod turned up first; switch to it and retry this tick.
                phase = PHASE_SWITCH;
                selected = slot;
                timer += delayTicks();
                continue;
            }

            if (phase == PHASE_RETRACT) {
                if (retract != 0) {
                    timer += delayTicks();
                    finish();
                    return;
                }
                phase = previous == selected ? PHASE_RESTORE_NEXT : PHASE_FINISH;
                timer += delayTicks();
                finish();
                return;
            }

            if (phase == PHASE_RESTORE_NEXT) {
                // Restoring onto the rod would leave the player holding the rod again, so the
                // original nudges to the slot after the weapon instead.
                inventory.currentItem = (previous + 1) % 9;
                phase = PHASE_FINISH;
                finish();
                return;
            }

            if (phase == PHASE_FINISH) {
                phase = PHASE_DONE;
                restore();
                timer += delayTicks();
                finish();
                return;
            }

            finish();
            return;
        }
        finish();
    }

    /** Slinky's phase-6 tail: a hold binding re-arms, a toggle press ends the module. */
    private void finish() {
        if (phase != PHASE_DONE) {
            return;
        }
        if (isHoldBind()) {
            phase = PHASE_IDLE;
        } else {
            disable();
        }
    }

    private void restore() {
        if (owner != null && previous != -1) {
            owner.currentItem = previous;
        }
        previous = -1;
        owner = null;
    }

    // ------------------------------------------------------------------ //
    // Item selection
    // ------------------------------------------------------------------ //

    /** Hotbar slot holding a rod (or, when allowed, a snowball/egg), or -1. Sets {@link #rod}. */
    private int scan(InventoryPlayer inventory) {
        int candidate = -1;
        rod = false;
        boolean throwables = allowThrowables.isToggled();
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            Item item = stack == null ? null : stack.getItem();
            if (throwables && (item == Items.snowball || item == Items.egg)) {
                return i;
            }
            if (item == Items.fishing_rod) {
                rod = true;
                candidate = i;
                if (!throwables) {
                    return i;
                }
            }
        }
        return candidate;
    }

    /** Highest-damage weapon slot, ignoring the slot the rod lives in. */
    private static int bestWeaponSlot(InventoryPlayer inventory, int avoid) {
        int best = -1;
        double damage = -1.0;
        for (int i = 0; i < InventoryPlayer.getHotbarSize(); i++) {
            if (i == avoid) {
                continue;
            }
            ItemStack stack = inventory.getStackInSlot(i);
            if (!ItemUtils.isWeapon(stack)) {
                continue;
            }
            double candidate = ItemUtils.attackDamage(stack);
            if (candidate > damage) {
                damage = candidate;
                best = i;
            }
        }
        return best;
    }

    /**
     * Sends the throw the same way a right click does: {@code sendUseItem} syncs the held slot
     * (C09) and then sends the use packet (C08) and runs the client-side item logic, which is what
     * spawns the bobber.
     */
    private void throwItem() {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) {
            return;
        }
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, held);
    }

    // ------------------------------------------------------------------ //
    // Timing
    // ------------------------------------------------------------------ //

    private float delayTicks() {
        float millis = (float) delay.getInput();
        if (randomizeDelays.isToggled()) {
            // Jitter the configured delay so the sequence does not look mechanical.
            millis *= 0.75F + 0.5F * MathUtils.RANDOM.nextFloat();
        }
        return millis / MS_PER_TICK;
    }

    private float retractSpeed() {
        float speed = (float) retractSpeed.getInput();
        return speed <= 0.0F ? 1.0F : speed;
    }

    private float throwingDelayTicks() {
        float speed = (float) throwSpeed.getInput();
        if (speed <= 0.0F) {
            speed = 1.0F;
        }
        if (!randomizeDelays.isToggled()) {
            return THROW_TICKS / speed;
        }
        float jitter = 0.5F + 0.5F * MathUtils.RANDOM.nextFloat();
        float result = THROW_TICKS / (speed * jitter);
        if (MathUtils.RANDOM.nextFloat() < 0.01F) {
            // The original gated a rare longer wait in; keep that flavour with a small chance.
            result += 1.0F + MathUtils.RANDOM.nextFloat();
        }
        return result;
    }

    /**
     * Ticks to wait for the bobber: the distance from the eye to the target divided by
     * {@code Retract speed} (blocks per tick), or {@code 10 / speed} when there is no target.
     */
    private static int retractTicks(Vec3 origin, Vec3 target, float speed) {
        float ticks;
        if (target == null) {
            ticks = 10.0F / speed;
        } else {
            Vec3 closest = new Vec3(
                    clamp(target.xCoord - 0.3, target.xCoord + 0.3, origin.xCoord),
                    clamp(target.yCoord, target.yCoord + 1.8, origin.yCoord),
                    clamp(target.zCoord - 0.3, target.zCoord + 0.3, origin.zCoord));
            double dx = origin.xCoord - closest.xCoord;
            double dy = origin.yCoord - closest.yCoord;
            double dz = origin.zCoord - closest.zCoord;
            ticks = (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) / speed);
        }
        return (int) Math.ceil(ticks);
    }

    private static double clamp(double min, double max, double value) {
        return value < min ? min : (value > max ? max : value);
    }

    private Vec3 eyePosition() {
        return new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + 1.62D, mc.thePlayer.posZ);
    }

    /** Aura target when one exists, otherwise whatever the crosshair is on. */
    private Entity target() {
        Module aura = ModuleManager.enabled("KillAura");
        if (aura != null) {
            Entity entity = aura.getTarget();
            if (entity != null) {
                return entity;
            }
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                ? hit.entityHit : null;
    }

    private Vec3 targetPosition() {
        Entity entity = target();
        return entity == null ? null : new Vec3(entity.posX, entity.posY, entity.posZ);
    }

    private boolean blockedByMouseOver() {
        if (!requireNotMouseOver.isToggled()) {
            return false;
        }
        MovingObjectPosition hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY;
    }
}
