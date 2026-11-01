package xyz.daikirai.module.combat;

import xyz.daikirai.client.BotCheck;
import xyz.daikirai.client.Friends;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of OpenExpo's {@code Velocity} ({@code Expo.module.impl.combat.Velocity}).
 *
 * <p>Three independent parts, exactly like the upstream module:</p>
 * <ul>
 *     <li><b>Modify-velocity</b> — scale the received knockback (Horizontal/Vertical %, can be
 *     reversed) before it is applied to the player.</li>
 *     <li><b>Delay-velocity</b> — cancel the {@code S12} and re-apply it later (Min/Max delay
 *     ticks), releasing early when the player lands, enters water, holds S, or finishes the
 *     reduce window.</li>
 *     <li><b>Reduce-velocity</b> — after a knockback, clicking within the window attacks the
 *     nearby player and multiplies the player's own motion by 0.6 (the "hit to reduce" trick),
 *     which also lets a held packet out.</li>
 * </ul>
 *
 * <p>Target gating (Players/Mobs/Animals/Bosses/Friends/Enemies/Teammates/Bots + FOV + Chance +
 * Require-moving) only gates Modify-velocity, matching upstream.</p>
 */
public final class Velocity2Module extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static Velocity2Module instance;

    /** Which implementation the settings and hooks belong to. */
    private static final int CLIENT_EXPO = 0;
    private static final int CLIENT_AMUNIX = 1;
    private static final int CLIENT_LEADER = 2;

    private final SliderSetting clientSetting = new SliderSetting("Client", "", "", 2, 0, 2, 1,
            new String[]{"Expo", "Amunix", "Leader"}, null);
    private final List<Setting> expoSettings = new ArrayList<Setting>();
    private final List<Setting> amunixSettings = new ArrayList<Setting>();
    private final List<Setting> leaderSettings = new ArrayList<Setting>();
    private int activeClient = CLIENT_LEADER;
    private Velocity2Amunix amunix;
    private Velocity2Leader leader;

    private final ButtonSetting modifyVelocity = flag("Modify-velocity", false);
    private final ButtonSetting reverseVelocity = flag("Reverse-velocity", false);
    private final SliderSetting horizontal = slider("Horizontal", 100, 0, 100, 1, "%");
    private final SliderSetting vertical = slider("Vertical", 100, 0, 100, 1, "%");
    private final SliderSetting chance = slider("Chance", 100, 0, 100, 1, "%");
    private final ButtonSetting delayVelocity = flag("Delay-velocity", true);
    private final SliderSetting minDelayTicks = slider("Min-delay-ticks", 3, 1, 20, 1, "t");
    private final SliderSetting maxDelayTicks = slider("Max-delay-ticks", 3, 1, 20, 1, "t");
    private final ButtonSetting delayReleaseOnGround = flag("Delay-release-on-ground", true);
    private final ButtonSetting delayReleaseOnReduce = flag("Delay-release-on-reduce", true);
    private final ButtonSetting reduceVelocity = flag("Reduce-velocity", false);
    private final SliderSetting reduceEffectTicks = slider("Reduce-effect-ticks", 10, 0, 20, 1, "t");
    private final ButtonSetting disableWhileHoldingS = flag("Disable-while-holding-S", true);
    private final ButtonSetting requireMoving = flag("Require-moving", false);
    private final SliderSetting fov = slider("FOV", 360, 0, 360, 1, "");
    private final ButtonSetting players = flag("Players", true);
    private final ButtonSetting mobs = flag("Mobs", false);
    private final ButtonSetting animals = flag("Animals", false);
    private final ButtonSetting bosses = flag("Bosses", false);
    private final ButtonSetting friends = flag("Friends", false);
    private final ButtonSetting enemies = flag("Enemies", true);
    private final ButtonSetting teammates = flag("Teammates", false);
    private final ButtonSetting bots = flag("Bots", false);

    private final List<Held> held = new ArrayList<Held>();
    private int reduceTicks;
    private boolean reduced;

    public Velocity2Module(int key) {
        super("Velocity2", Category.combat, key);
        instance = this;
        amunix = new Velocity2Amunix(this, amunixSettings);
        leader = new Velocity2Leader(this, leaderSettings);
        rebuildSettings();
    }

    // ------------------------------------------------------------------ //
    // Client switching (Expo / Amunix)
    // ------------------------------------------------------------------ //

    @Override
    public ArrayList<Setting> getSettings() {
        syncClient();
        return super.getSettings();
    }

    private int clientIndex() {
        return (int) Math.round(clientSetting.getInput());
    }

    private void rebuildSettings() {
        // Keep settings that came from modules.json (the description row) at the end.
        List<Setting> extras = new ArrayList<Setting>();
        for (Setting setting : settings) {
            if (setting != clientSetting && !expoSettings.contains(setting)
                    && !amunixSettings.contains(setting) && !leaderSettings.contains(setting)) {
                extras.add(setting);
            }
        }
        settings.clear();
        settings.add(clientSetting);
        int client = clientIndex();
        if (client == CLIENT_AMUNIX) {
            settings.addAll(amunixSettings);
        } else if (client == CLIENT_LEADER) {
            settings.addAll(leaderSettings);
        } else {
            settings.addAll(expoSettings);
        }
        settings.addAll(extras);
    }

    /** Called from every settings read (GUI) and every tick, so both paths agree. */
    private void syncClient() {
        int wanted = clientIndex();
        if (wanted == activeClient) {
            return;
        }
        clear();
        amunix.reset();
        leader.reset();
        activeClient = wanted;
        rebuildSettings();
    }

    /** Island HUD accessor: the most ticks any held knockback still waits in the queue. */
    public int delayTicks() {
        int max = 0;
        for (Held entry : held) {
            max = Math.max(max, Math.max(0, entry.ticks));
        }
        if (clientIndex() == CLIENT_AMUNIX) {
            max = Math.max(max, amunix.delayRemaining());
        } else if (clientIndex() == CLIENT_LEADER) {
            max = Math.max(max, leader.delayRemaining());
        }
        return max;
    }

    /** Island HUD accessor: how many knockback packets are currently held. */
    public int delayPackets() {
        int packets = held.size();
        if (clientIndex() == CLIENT_AMUNIX && amunix.holding()) {
            ++packets;
        } else if (clientIndex() == CLIENT_LEADER && leader.holding()) {
            ++packets;
        }
        return packets;
    }

    public boolean delaying() {
        if (clientIndex() == CLIENT_AMUNIX) {
            return amunix.holding();
        }
        if (clientIndex() == CLIENT_LEADER) {
            return leader.holding();
        }
        return !held.isEmpty();
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        expoSettings.add(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        expoSettings.add(setting);
        return setting;
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void onDisable() {
        clear();
        if (amunix != null) {
            amunix.reset();
        }
        if (leader != null) {
            leader.reset();
        }
    }

    private void clear() {
        held.clear();
        reduceTicks = 0;
        reduced = false;
    }

    @Override
    public String getInfo() {
        if (clientIndex() == CLIENT_AMUNIX) {
            return amunix.info();
        }
        if (clientIndex() == CLIENT_LEADER) {
            return leader.info();
        }
        if (modifyVelocity.isToggled()) {
            String h = (reverseVelocity.isToggled() ? "§m" : "") + (int) horizontal.getInput() + "%";
            return h + " " + (int) vertical.getInput() + "%";
        }
        if (delayVelocity.isToggled()) {
            return "Delay " + (int) minDelayTicks.getInput() + "-" + (int) maxDelayTicks.getInput();
        }
        if (reduceVelocity.isToggled()) {
            return "Reduce";
        }
        return null;
    }

    @Override
    public void onPostPlayerInput() {
        if (clientIndex() == CLIENT_AMUNIX) {
            amunix.postInput();
        } else if (clientIndex() == CLIENT_LEADER) {
            leader.postInput();
        }
    }

    @Override
    public float[] getRotations(float yaw, float pitch) {
        if (clientIndex() == CLIENT_AMUNIX) {
            return amunix.rotations(yaw, pitch);
        }
        if (clientIndex() == CLIENT_LEADER) {
            return leader.rotations(yaw, pitch);
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    // Knockback packets
    // ------------------------------------------------------------------ //

    @Override
    public boolean onReceivePacket(Packet packet, INetHandler handler) {
        if (mc.thePlayer == null) {
            return true;
        }
        int client = clientIndex();
        if (client == CLIENT_LEADER) {
            if (packet instanceof S19PacketEntityStatus) {
                leader.onStatus((S19PacketEntityStatus) packet);
                return true;
            }
            if (packet instanceof S27PacketExplosion) {
                leader.onExplosion((S27PacketExplosion) packet);
                return true;
            }
        }
        if (!(packet instanceof S12PacketEntityVelocity)) {
            return true;
        }
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
            return true;
        }
        if (client == CLIENT_AMUNIX) {
            return amunix.onVelocity(velocity);
        }
        if (client == CLIENT_LEADER) {
            return leader.onVelocity(velocity);
        }
        if (reduceVelocity.isToggled()) {
            reduceTicks = (int) reduceEffectTicks.getInput();
            reduced = false;
        }
        boolean holdingS = disableWhileHoldingS.isToggled() && GameSettings.isKeyDown(mc.gameSettings.keyBindBack);
        boolean modify = modifyVelocity.isToggled() && !holdingS && (!requireMoving.isToggled() || PlayerUtils.isMoving())
                && hasTarget();
        if (modify && horizontal.getInput() == 0.0 && vertical.getInput() == 0.0) {
            // Upstream cancels the packet outright when both axes are set to zero.
            return false;
        }
        boolean jumpResetConflict = ModuleManager.enabled("Jump Reset") != null && mc.thePlayer.onGround;
        if (delayVelocity.isToggled() && !jumpResetConflict) {
            int delay = (int) Math.round(minDelayTicks.getInput() + Math.random()
                    * Math.max(0.0, maxDelayTicks.getInput() - minDelayTicks.getInput()));
            held.add(new Held(velocity, delay, modify));
            return false;
        }
        if (modify) {
            apply(velocity, true);
            return false;
        }
        return true;
    }

    private void apply(S12PacketEntityVelocity velocity, boolean modify) {
        double scaleH = modify ? horizontal.getInput() / 100.0 : 1.0;
        double scaleV = modify ? vertical.getInput() / 100.0 : 1.0;
        if (modify && reverseVelocity.isToggled()) {
            scaleH = -scaleH;
        }
        mc.thePlayer.motionX = velocity.getMotionX() / 8000.0 * scaleH;
        mc.thePlayer.motionY = velocity.getMotionY() / 8000.0 * scaleV;
        mc.thePlayer.motionZ = velocity.getMotionZ() / 8000.0 * scaleH;
    }

    // ------------------------------------------------------------------ //
    // Tick: release held packets, reduce window
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        syncClient();
        if (mc.thePlayer == null) {
            clear();
            amunix.reset();
            leader.reset();
            return;
        }
        if (clientIndex() == CLIENT_AMUNIX) {
            amunix.tick();
            return;
        }
        if (clientIndex() == CLIENT_LEADER) {
            leader.tick();
            leader.attackWindow();
            leader.airPushWindow();
            return;
        }
        reduceTicks = Math.max(0, reduceTicks - 1);
        if (held.isEmpty()) {
            return;
        }
        boolean holdingS = disableWhileHoldingS.isToggled() && GameSettings.isKeyDown(mc.gameSettings.keyBindBack);
        boolean inLiquid = mc.thePlayer.isInWater() || mc.thePlayer.isInLava();
        for (int i = held.size() - 1; i >= 0; --i) {
            Held entry = held.get(i);
            entry.ticks--;
            boolean release = entry.ticks <= 0
                    || (delayReleaseOnGround.isToggled() && mc.thePlayer.onGround)
                    || (delayReleaseOnReduce.isToggled() && reduced)
                    || holdingS
                    || inLiquid;
            if (!release) {
                continue;
            }
            apply(entry.packet, entry.modify);
            held.remove(i);
        }
    }

    // ------------------------------------------------------------------ //
    // Reduce: click inside the window to cut the knockback
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (clientIndex() == CLIENT_AMUNIX) {
            amunix.onMouse(e);
            return;
        }
        if (clientIndex() == CLIENT_LEADER) {
            leader.onMouse(e);
            return;
        }
        if (!reduceVelocity.isToggled() || reduceTicks <= 0) {
            return;
        }
        if (e.button != 0 || !e.buttonstate || mc.currentScreen != null) {
            return;
        }
        if (!mc.thePlayer.isSprinting()) {
            return;
        }
        EntityLivingBase target = nearestTarget(3.0);
        if (target == null) {
            return;
        }
        // Consume the click: the reduce attack replaces the vanilla one.
        e.setCanceled(true);
        PlayerUtils.attack(target, false);
        mc.thePlayer.motionX *= 0.6;
        mc.thePlayer.motionZ *= 0.6;
        mc.thePlayer.setSprinting(false);
        reduced = true;
    }

    private EntityLivingBase nearestTarget(double range) {
        EntityLivingBase best = null;
        double bestDistance = range * range;
        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer || player.deathTime != 0 || player.isDead) {
                continue;
            }
            if (!accepted(player)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSqToEntity(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ //
    // Target gating (Modify-velocity only, like upstream)
    // ------------------------------------------------------------------ //

    private boolean hasTarget() {
        if (fov.getInput() != 360.0 && fov.getInput() <= 0.0) {
            return false;
        }
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity == mc.thePlayer || entity.isDead || entity.deathTime != 0) {
                continue;
            }
            if (mc.thePlayer.getDistanceSqToEntity(entity) > 100.0) {
                continue;
            }
            if (fov.getInput() < 360.0 && !PlayerUtils.inFov((float) fov.getInput(), entity)) {
                continue;
            }
            if (accepted(entity)) {
                return true;
            }
        }
        return false;
    }

    private boolean accepted(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            String name = player.getName().toLowerCase();
            boolean friend = Friends.friends.contains(name) || Friends.isFriend(player.getName());
            boolean enemy = Friends.enemies.contains(name) || Friends.isEnemy(player.getName());
            if (friend) {
                return friends.isToggled();
            }
            if (enemy) {
                return enemies.isToggled();
            }
            if (Hypixel.sameTeam(player) && !teammates.isToggled()) {
                return false;
            }
            if (BotCheck.isBot(player) && !bots.isToggled()) {
                return false;
            }
            return players.isToggled();
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) {
            return bosses.isToggled();
        }
        if (entity instanceof EntityAnimal) {
            return animals.isToggled();
        }
        if (entity instanceof EntityMob) {
            return mobs.isToggled();
        }
        return false;
    }

    private static final class Held {
        final S12PacketEntityVelocity packet;
        int ticks;
        final boolean modify;

        Held(S12PacketEntityVelocity packet, int ticks, boolean modify) {
            this.packet = packet;
            this.ticks = ticks;
            this.modify = modify;
        }
    }
}
