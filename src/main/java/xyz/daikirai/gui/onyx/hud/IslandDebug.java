package xyz.daikirai.gui.onyx.hud;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import xyz.daikirai.hook.Hooks;

/**
 * Movement / landing / combat probe behind {@code Watermark -> Debug}.
 *
 * <p>Every part is switched on its own ({@code Debug: Move} / {@code Debug: Landing} /
 * {@code Debug: Attack} / {@code Debug: Hurt}); {@link #update} returns {@code null} when nothing
 * should be shown, which lets the island fall back to its normal content.</p>
 */
final class IslandDebug {

    static final int ON_GROUND = 0;
    static final int WALK = 1;
    static final int JUMP = 2;
    static final int FALL = 3;
    static final int EVENT = 4;

    private static final long LANDING_HOLD_NS = 500000000L;
    private static final long EVENT_HOLD_NS = 1200000000L;
    private static final int MAX_FALL_TICKS = 400;
    private static final int SCAN_DEPTH = 384;
    private static final int MAX_NAME = 14;
    private static final String[] DIRECTIONS = {
            "Forward", "Forward Left", "Left", "Back Left",
            "Back", "Back Right", "Right", "Forward Right"
    };
    private static final String[] ARROWS = {"↑", "↖", "←", "↙", "↓", "↘", "→", "↗"};

    private boolean airborne;
    private boolean jumped;
    private long landedAt;
    private int predictionTick = -1;
    private int predictedTicks;

    private float lastHealth = -1.0F;
    private long hurtAt;
    private float hurtAmount;
    private int attackSeenTick = -1;
    private long attackAt;
    private String attackName = "";
    private float attackDistance;

    Snapshot update(EntityPlayerSP player, boolean move, boolean landing, boolean attack, boolean hurt) {
        long now = System.nanoTime();
        pollHurt(player, now);
        pollAttack(player, now);
        if (hurt && hurtAt != 0L && now - hurtAt < EVENT_HOLD_NS) {
            return new Snapshot("Hurt", "-" + oneDecimal(hurtAmount));
        }
        if (attack && attackAt != 0L && now - attackAt < EVENT_HOLD_NS) {
            return new Snapshot(attackName.isEmpty() ? "Hit" : "Hit " + attackName,
                    oneDecimal(attackDistance) + "m");
        }
        int direction = direction(player.movementInput.moveForward, player.movementInput.moveStrafe);
        boolean moving = direction >= 0;
        if (player.onGround) {
            if (airborne) {
                landedAt = now;
            }
            airborne = false;
            jumped = false;
            predictionTick = -1;
            predictedTicks = 0;
            boolean justLanded = landedAt != 0L && now - landedAt < LANDING_HOLD_NS;
            if (!justLanded) {
                landedAt = 0L;
            }
            if (landing && (justLanded || !move || !moving)) {
                return new Snapshot(ON_GROUND, direction, 0);
            }
            if (move && moving) {
                return new Snapshot(WALK, direction, 0);
            }
            return null;
        }
        if (!airborne) {
            // A real jump raises motionY through Hooks#onJump; knockback also lifts the player but
            // never calls jump(), so it is reported as a fall instead.
            int sinceJump = player.ticksExisted - Hooks.lastJumpTick;
            jumped = player.motionY > 0.0D && Hooks.lastJumpTick >= 0 && sinceJump >= 0 && sinceJump <= 1;
            predictionTick = -1;
        }
        airborne = true;
        landedAt = 0L;
        if (!landing) {
            return null;
        }
        int tick = player.ticksExisted;
        if (predictionTick != tick) {
            // Recomputed once per game tick: walking off an edge, or falling past one, changes the
            // surface below, and a prediction locked at take-off would keep reporting the jump arc.
            predictionTick = tick;
            predictedTicks = landingTicks(player);
        }
        return new Snapshot(jumped ? JUMP : FALL, direction, predictedTicks);
    }

    void reset() {
        airborne = false;
        jumped = false;
        landedAt = 0L;
        predictionTick = -1;
        predictedTicks = 0;
        lastHealth = -1.0F;
        hurtAt = 0L;
        hurtAmount = 0.0F;
        attackSeenTick = Hooks.lastAttackTargetTick;
        attackAt = 0L;
        attackName = "";
        attackDistance = 0.0F;
    }

    private void pollHurt(EntityPlayerSP player, long now) {
        float health = player.getHealth();
        if (lastHealth >= 0.0F && player.ticksExisted > 1 && health < lastHealth - 0.05F) {
            hurtAmount = lastHealth - health;
            hurtAt = now;
        }
        lastHealth = health;
    }

    private void pollAttack(EntityPlayerSP player, long now) {
        int tick = Hooks.lastAttackTargetTick;
        if (tick < 0 || tick == attackSeenTick) {
            return;
        }
        attackSeenTick = tick;
        attackAt = now;
        net.minecraft.entity.Entity target = Hooks.lastAttackTarget;
        attackDistance = Hooks.lastAttackDistance;
        String name = target == null ? "" : target.getName();
        attackName = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    static int direction(float forward, float strafe) {
        if (Math.abs(forward) < 0.01F && Math.abs(strafe) < 0.01F) {
            return -1;
        }
        // Vanilla MovementInputFromOptions: forward = +W/-S, strafe = +A/-D (left is positive).
        double angle = Math.toDegrees(Math.atan2(strafe, forward));
        return ((int) Math.round(angle / 45.0) % 8 + 8) % 8;
    }

    /**
     * Ticks until the feet reach the first surface below, simulating vanilla 1.8.9 gravity from the
     * player's current height and vertical velocity. {@code -1} means "nothing to land on" (void).
     */
    static int landingTicks(EntityPlayerSP player) {
        double support = supportY(player);
        if (Double.isNaN(support)) {
            return -1;
        }
        double y = player.getEntityBoundingBox().minY;
        double motionY = player.motionY;
        for (int tick = 1; tick <= MAX_FALL_TICKS; tick++) {
            double next = y + motionY;
            motionY = (motionY - 0.08D) * 0.98D;
            if (next <= support + 1.0E-4D) {
                return tick;
            }
            y = next;
        }
        return -1;
    }

    /** Feet level of the first block with a collision box under the player, or NaN for none. */
    static double supportY(EntityPlayerSP player) {
        AxisAlignedBB box = player.getEntityBoundingBox();
        int minX = MathHelper.floor_double(box.minX + 1.0E-7D);
        int maxX = MathHelper.floor_double(box.maxX - 1.0E-7D);
        int minZ = MathHelper.floor_double(box.minZ + 1.0E-7D);
        int maxZ = MathHelper.floor_double(box.maxZ - 1.0E-7D);
        int top = MathHelper.floor_double(box.minY - 1.0E-4D);
        int bottom = Math.max(0, top - SCAN_DEPTH);
        for (int y = top; y >= bottom; y--) {
            double highest = Double.NaN;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!player.worldObj.isBlockLoaded(pos)) {
                        continue;
                    }
                    IBlockState state = player.worldObj.getBlockState(pos);
                    Block block = state.getBlock();
                    AxisAlignedBB collision = block.getCollisionBoundingBox(player.worldObj, pos, state);
                    if (collision == null || collision.maxY > box.minY + 1.0E-4D) {
                        continue;
                    }
                    if (Double.isNaN(highest) || collision.maxY > highest) {
                        highest = collision.maxY;
                    }
                }
            }
            if (!Double.isNaN(highest)) {
                return highest;
            }
        }
        return Double.NaN;
    }

    private static String oneDecimal(double value) {
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }

    static final class Snapshot {
        final int state;
        final int direction;
        final int ticks;
        private final String label;
        private final String badge;
        private final String arrow;

        Snapshot(int state, int direction, int ticks) {
            this.state = state;
            this.direction = direction;
            this.ticks = ticks;
            this.label = state == JUMP ? "Jump" : state == FALL ? "Fall" : "Walk";
            this.arrow = state == WALK && direction >= 0 ? ARROWS[direction] : "";
            if (state == JUMP || state == FALL) {
                this.badge = ticks > 0 ? ticks + "t" : "—";
            } else if (state == ON_GROUND) {
                this.badge = "onGround";
            } else {
                this.badge = direction >= 0 ? DIRECTIONS[direction] : "onGround";
            }
        }

        /** Attack / hurt line: the label and the pill are given by the event itself. */
        Snapshot(String label, String badge) {
            this.state = EVENT;
            this.direction = -1;
            this.ticks = 0;
            this.label = label;
            this.badge = badge;
            this.arrow = "";
        }

        String label() {
            return label;
        }

        String badge() {
            return badge;
        }

        String arrow() {
            return arrow;
        }
    }
}
