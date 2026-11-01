package xyz.daikirai.module.combat;

import xyz.daikirai.client.BotCheck;
import xyz.daikirai.client.Friends;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.DescriptionSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.KeyUtils;
import xyz.daikirai.util.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Mouse;

/**
 * Port of OpenExpo's {@code AutoBlock} ({@code Expo.module.impl.combat.AutoBlock}).
 *
 * <p>Upstream is another fully obfuscated file (method-handle string decryption), so this is a
 * behaviour port: Mode (VANILLA / LEGIT / LAG / LAG_NEW / LAG_LEGIT / *_PRE / NONE), APS-mode,
 * smart unblock (chance + ticks), manual-left-click, require-right-click / KillAura, NoSlow
 * hand-offs, visual blocking (fake sword block pose) and the target filters.</p>
 *
 * <p>The {@code *_PRE} variants act at the same point as their post counterparts here: this client
 * has no packet-order manager, so pre/post motion cannot be distinguished for the right-click
 * packet. The two names are kept so the settings match upstream.</p>
 */
public final class AutoBlock2Module extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static AutoBlock2Module instance;

    private final SliderSetting mode = mode("Mode", 0, "VANILLA", "LEGIT", "LAG", "LAG_NEW", "LAG_LEGIT",
            "LEGIT_PRE", "LAG_PRE", "LAG_NEW_PRE", "LAG_LEGIT_PRE", "NONE");
    private final SliderSetting apsMode = mode("APS-mode", 1, "3APS", "5APS", "7APS", "10APS", "14APS");
    private final SliderSetting smartUnblockChance = slider("Smart-unblock-chance", 100, 0, 100, 1, "%");
    private final ButtonSetting smartUnblock = flag("Smart-unblock", false);
    private final SliderSetting smartUnblockTicks = slider("Smart-unblock-ticks", 8, 0, 15, 1, "t");
    private final ButtonSetting manualLeftClick = flag("Manual-left-click", false);
    private final ButtonSetting requireRightClick = flag("Require-right-click", false);
    private final ButtonSetting requireKillAura = flag("Require-KillAura", true);
    private final ButtonSetting allowNoSlow = flag("Allow-NoSlow", true);
    private final ButtonSetting onlyUnblockWithoutNoSlow = flag("Only-unblock-without-NoSlow", true);
    private final ButtonSetting disableNoSlowInRange = flag("Disable-NoSlow-in-range", true);
    private final SliderSetting noSlowDisableRange = slider("NoSlow-disable-range", 3.5, 0, 8, 0.01, "");
    private final ButtonSetting visualBlocking = flag("Visual-blocking", true);
    private final DescriptionSetting targetHeader = desc("Target settings");
    private final ButtonSetting players = flag("Players", true);
    private final ButtonSetting mobs = flag("Mobs", false);
    private final ButtonSetting animals = flag("Animals", false);
    private final ButtonSetting bosses = flag("Bosses", false);
    private final ButtonSetting friends = flag("Friends", false);
    private final ButtonSetting enemies = flag("Enemies", true);
    private final ButtonSetting teammates = flag("Teammates", false);
    private final ButtonSetting bots = flag("Bots", false);
    private final ButtonSetting silverfishes = flag("Silverfishes", false);
    private final ButtonSetting golems = flag("Golems", false);
    private final SliderSetting fov = slider("FOV", 360, 1, 360, 1, "");
    private final SliderSetting targetRange = slider("Target-range", 5, 1, 8, 0.01, "");

    private boolean blocking;
    private boolean fakeBlocking;
    private boolean pressedByUs;
    private long lastTap;
    private int tapTicks;
    private int smartTicks;
    private long lastSmartRoll;

    public AutoBlock2Module(int key) {
        super("AutoBlock2", Category.combat, key);
        instance = this;
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        registerSetting(setting);
        return setting;
    }

    private SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        registerSetting(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        registerSetting(setting);
        return setting;
    }

    private DescriptionSetting desc(String name) {
        DescriptionSetting setting = new DescriptionSetting(name);
        registerSetting(setting);
        return setting;
    }

    @Override
    public boolean isFakeBlocking() {
        return fakeBlocking;
    }

    @Override
    public String getInfo() {
        return mode.options[(int) mode.getInput()] + " " + apsMode.options[(int) apsMode.getInput()];
    }

    @Override
    public void onEnable() {
        blocking = false;
        fakeBlocking = false;
        pressedByUs = false;
        tapTicks = 0;
        smartTicks = 0;
        lastTap = 0L;
        lastSmartRoll = 0L;
    }

    @Override
    public void onDisable() {
        release();
        fakeBlocking = false;
        blocking = false;
        smartTicks = 0;
    }

    // ------------------------------------------------------------------ //
    // Tick (after motion, like the upstream post update)
    // ------------------------------------------------------------------ //

    @Override
    public void onPostMotion() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            release();
            fakeBlocking = false;
            return;
        }
        int style = (int) mode.getInput();
        boolean sword = PlayerUtils.holdingSword();
        boolean inRange = style != 9 && sword && mc.currentScreen == null && conditions();
        fakeBlocking = visualBlocking.isToggled() && inRange && style != 9;
        if (!inRange) {
            release();
            smartTicks = 0;
            lastSmartRoll = 0L;
            return;
        }
        if (!allowNoSlow.isToggled() && ModuleManager.enabled("NoSlow") != null) {
            // Blocking would stop NoSlow from seeing the use-item state.
            release();
            return;
        }
        if (disableNoSlowInRange.isToggled() && ModuleManager.enabled("NoSlow") != null && nearestTarget() != null
                && mc.thePlayer.getDistanceSqToEntity(nearestTarget()) <= noSlowDisableRange.getInput() * noSlowDisableRange.getInput()) {
            // Blocking inside NoSlow's range would get us slowed; keep it released so NoSlow cannot
            // interfere with the hit.
            release();
            return;
        }
        boolean noSlowEnabled = ModuleManager.enabled("NoSlow") != null;
        if (smartUnblock.isToggled() && (!onlyUnblockWithoutNoSlow.isToggled() || !noSlowEnabled)) {
            if (smartTicks > 0) {
                --smartTicks;
                release();
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastSmartRoll >= 200L && nearestTarget() != null
                    && mc.thePlayer.getDistanceSqToEntity(nearestTarget()) <= 9.0
                    && Math.random() * 100.0 < smartUnblockChance.getInput()) {
                lastSmartRoll = now;
                smartTicks = (int) smartUnblockTicks.getInput();
                release();
                return;
            }
        }
        switch (style) {
            case 0: // VANILLA
            case 2: // LAG
            case 3: // LAG_NEW
            case 6: // LAG_PRE
            case 7: // LAG_NEW_PRE
                hold();
                break;
            case 9: // NONE
                release();
                break;
            default: // LEGIT / LAG_LEGIT / *_PRE variants
                tap();
                break;
        }
    }

    private void hold() {
        blocking = true;
        if (!pressedByUs) {
            pressedByUs = true;
            KeyUtils.press(mc.gameSettings.keyBindUseItem);
        }
    }

    private void tap() {
        long now = System.currentTimeMillis();
        long interval = (long) (1000.0 / aps());
        if (tapTicks > 0) {
            --tapTicks;
            hold();
            return;
        }
        if (now - lastTap >= interval) {
            lastTap = now;
            tapTicks = 1;
            hold();
        } else {
            release();
        }
    }

    private double aps() {
        switch ((int) apsMode.getInput()) {
            case 0:
                return 3.0;
            case 1:
                return 5.0;
            case 2:
                return 7.0;
            case 3:
                return 10.0;
            default:
                return 14.0;
        }
    }

    private void release() {
        blocking = false;
        if (pressedByUs) {
            pressedByUs = false;
            KeyUtils.release(mc.gameSettings.keyBindUseItem);
        }
    }

    // ------------------------------------------------------------------ //
    // Conditions
    // ------------------------------------------------------------------ //

    private boolean conditions() {
        if (requireRightClick.isToggled() && !Mouse.isButtonDown(1)) {
            return false;
        }
        if (manualLeftClick.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }
        if (requireKillAura.isToggled() && auraTarget() == null) {
            return false;
        }
        return nearestTarget() != null;
    }

    private EntityLivingBase auraTarget() {
        for (String name : new String[]{"KillAura", "KillAura2"}) {
            Module module = ModuleManager.enabled(name);
            if (module != null && module.getTarget() != null) {
                return (EntityLivingBase) module.getTarget();
            }
        }
        return null;
    }

    private EntityLivingBase nearestTarget() {
        double range = targetRange.getInput();
        EntityLivingBase best = null;
        double bestDistance = range * range;
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity == mc.thePlayer || entity.isDead || entity.deathTime != 0 || !entity.isEntityAlive()) {
                continue;
            }
            if (fov.getInput() < 360.0 && !PlayerUtils.inFov((float) fov.getInput(), entity)) {
                continue;
            }
            if (!accepted(entity)) {
                continue;
            }
            double distance = mc.thePlayer.getDistanceSqToEntity(entity);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private boolean accepted(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            String name = player.getName().toLowerCase();
            if (Friends.friends.contains(name) || Friends.isFriend(player.getName())) {
                return friends.isToggled();
            }
            if (Friends.enemies.contains(name) || Friends.isEnemy(player.getName())) {
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
        if (entity instanceof EntitySilverfish) {
            return silverfishes.isToggled();
        }
        if (entity instanceof EntityIronGolem) {
            return golems.isToggled();
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
}
