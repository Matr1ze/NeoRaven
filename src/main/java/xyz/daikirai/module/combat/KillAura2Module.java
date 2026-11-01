package xyz.daikirai.module.combat;

import xyz.daikirai.client.BotCheck;
import xyz.daikirai.client.Friends;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.DescriptionSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;
import xyz.daikirai.util.RotationManager;
import xyz.daikirai.util.RotationUtils;
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
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Port of OpenExpo's {@code KillAura} ({@code Expo.module.impl.combat.KillAura}).
 *
 * <p>The upstream file is obfuscated end to end (its helpers/timers carry encrypted-string calls),
 * so this is a behaviour port against its settings:
 * SINGLE/SWITCH target modes, VIEW/DISTANCE/HEALTH/HURT_TIME/ARMOR sorting, SILENT/NO_RENDER/LOCK/
 * MANUAL/NONE rotations with smoothing and an angle step, Min/Max APS, attack/swing ranges, switch
 * delay, target filters (players/mobs/animals/bosses/friends/enemies/teammates/bots/silverfish/
 * golems + FOV), through-wall, screen check, require sword/click, and the target box / head box /
 * ring / reach ring rendering with the three colour modes.</p>
 *
 * <p>Not ported: the upstream Blink-auto-block coupling, AutoBlock hand-off, the per-tick swing
 * animation blend and the Hypixel-only header settings.</p>
 */
public final class KillAura2Module extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static KillAura2Module instance;

    /** Which implementation the settings and behaviour belong to. */
    private static final int CLIENT_EXPO = 0;
    private static final int CLIENT_LEADER = 1;

    private final SliderSetting clientSetting = new SliderSetting("Client", "", "", CLIENT_LEADER, 0, 1, 1,
            new String[]{"Expo", "Leader"}, null);
    private final List<Setting> expoSettings = new ArrayList<Setting>();
    private final List<Setting> leaderSettings = new ArrayList<Setting>();
    private int activeClient = CLIENT_LEADER;

    // Leader-Lite KillAura surface (its own list; the engine below reads whichever client is active).
    private final SliderSetting lMode = leaderMode("Mode", 0, "Single", "Switch");
    private final SliderSetting lSort = leaderMode("Sort", 0, "Distance", "Health", "Hurt Time", "FOV");
    private final SliderSetting lRotations = leaderMode("Rotations", 2, "None", "Legit", "Silent", "Lock View");
    private final SliderSetting lRotationMode = leaderMode("RotationMode", 2, "Normal", "Nearest", "Smart");
    private final SliderSetting lMoveFix = leaderMode("Move Fix", 1, "None", "Silent", "Strict", "Strafe");
    private final SliderSetting lSmoothing = leaderSlider("Smoothing", 0, 0, 100, 1, "%");
    private final SliderSetting lAngleStep = leaderSlider("Angle Step", 90, 0, 180, 1, "°");
    private final SliderSetting lMinCps = leaderSlider("Min Aps", 14, 1, 20, 1, "");
    private final SliderSetting lMaxCps = leaderSlider("Max Aps", 14, 1, 20, 1, "");
    private final SliderSetting lSwitchDelay = leaderSlider("Switch Delay", 1, 0, 20, 1, "t");
    private final SliderSetting lAttackRange = leaderSlider("Attack Range", 3, 0, 8, 0.01, "");
    private final SliderSetting lSwingRange = leaderSlider("Swing Range", 5, 0, 8, 0.01, "");
    private final SliderSetting lFov = leaderSlider("FOV", 360, 1, 360, 1, "°");
    private final ButtonSetting lThroughWalls = leaderFlag("Through Walls", false);
    private final ButtonSetting lRequirePress = leaderFlag("Require Press", false);
    private final ButtonSetting lWeaponsOnly = leaderFlag("Weapons Only", true);
    private final ButtonSetting lAllowTools = leaderFlag("Allow Tools", false);
    private final ButtonSetting lBotCheck = leaderFlag("Bot Check", true);
    private final ButtonSetting lTeams = leaderFlag("Teams", false);
    private final ButtonSetting lPlayers = leaderFlag("Players", true);
    private final ButtonSetting lBosses = leaderFlag("Bosses", true);
    private final ButtonSetting lMobs = leaderFlag("Mobs", false);
    private final ButtonSetting lAnimals = leaderFlag("Animals", false);
    private final ButtonSetting lGolems = leaderFlag("Golems", false);
    private final ButtonSetting lSilverfish = leaderFlag("Silverfish", false);
    /** Shared by both clients: Leader and Expo both showed "multi action" when a block landed. */
    private final ButtonSetting pauseWhileBlocking = sharedFlag("Pause While Blocking", true);

    private final SliderSetting rotationSmoothing = slider("Rotation-smoothing", 0, 0, 100, 1, "%");
    private final SliderSetting showTargetOpacity = slider("Show-target-opacity", 25, 0, 100, 1, "%");
    private final ButtonSetting showReachRing = flag("Show-reach-ring", false);
    private final DescriptionSetting blinkNote = desc("Blink autoblocks only works in Hypixel");
    private final DescriptionSetting targetHeader = desc("Target settings");
    private final ButtonSetting legit = flag("Legit", false);
    private final ButtonSetting requireClick = flag("Require-click", false);
    private final ButtonSetting requireSword = flag("Require-Sword", true);
    private final ButtonSetting screenCheck = flag("Screen-check", true);
    private final ButtonSetting throughWall = flag("Through-wall", false);
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
    private final SliderSetting minAps = slider("Min-APS", 20, 1, 20, 0.1, "");
    private final SliderSetting maxAps = slider("Max-APS", 20, 1, 20, 0.1, "");
    private final SliderSetting attackRange = slider("Attack-range", 3, 0, 8, 0.01, "");
    private final SliderSetting swingRange = slider("Swing-range", 6, 0, 8, 0.01, "");
    private final SliderSetting switchDelay = slider("Switch-delay", 100, 0, 1000, 1, "ms");
    private final SliderSetting angleStep = slider("Angle-step", 90, 0, 180, 1, "°");
    private final SliderSetting mode = mode("Mode", 0, "SINGLE", "SWITCH");
    private final SliderSetting sort = mode("Sort", 0, "VIEW", "DISTANCE", "HEALTH", "HURT_TIME", "ARMOR");
    private final SliderSetting rotation = mode("Rotation", 0, "SILENT", "NO_RENDER", "LOCK", "MANUAL", "NONE");
    private final SliderSetting moveFix = mode("Move-fix", 0, "SILENT", "STRICT", "NONE");
    private final SliderSetting showTarget = mode("Show-target", 0, "BOX", "BOX_WITH_DAMAGE", "HEAD_BOX",
            "HEAD_BOX_WITH_DAMAGE", "RING", "NONE");
    private final SliderSetting showTargetColor = mode("Show-target-color", 0, "THEME", "THEME_CUSTOM", "CUSTOM");
    private final ColorSetting customColor = color("Custom-color", 0xFFFF5555);
    private final ColorSetting showTargetDamageColor = color("Show-target-damage-color", 0xFFFF2020);

    private EntityLivingBase target;
    private List<EntityLivingBase> candidates = new ArrayList<EntityLivingBase>();
    private int switchIndex;
    private long lastAttack;
    private long lastSwitch;
    private float[] pending;
    private float currentYaw;
    private float currentPitch;
    private boolean targetChanged;

    public KillAura2Module(int key) {
        super("KillAura2", Category.combat, key);
        instance = this;
        rebuildSettings();
    }

    // ------------------------------------------------------------------ //
    // Client switching (Expo / Leader)
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
        List<Setting> extras = new ArrayList<Setting>();
        for (Setting setting : settings) {
            if (setting != clientSetting && !expoSettings.contains(setting)
                    && !leaderSettings.contains(setting)) {
                extras.add(setting);
            }
        }
        settings.clear();
        settings.add(clientSetting);
        settings.addAll(clientIndex() == CLIENT_LEADER ? leaderSettings : expoSettings);
        settings.addAll(extras);
    }

    private void syncClient() {
        int wanted = clientIndex();
        if (wanted == activeClient) {
            return;
        }
        activeClient = wanted;
        rebuildSettings();
        target = null;
        pending = null;
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        expoSettings.add(setting);
        return setting;
    }

    private SliderSetting mode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        expoSettings.add(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        expoSettings.add(setting);
        return setting;
    }

    private ColorSetting color(String name, int value) {
        ColorSetting setting = new ColorSetting(name, value, 0.0f);
        expoSettings.add(setting);
        return setting;
    }

    private DescriptionSetting desc(String name) {
        DescriptionSetting setting = new DescriptionSetting(name);
        expoSettings.add(setting);
        return setting;
    }

    private SliderSetting leaderSlider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        leaderSettings.add(setting);
        return setting;
    }

    private SliderSetting leaderMode(String name, int value, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", value, 0, options.length - 1, 1, options, null);
        leaderSettings.add(setting);
        return setting;
    }

    private ButtonSetting leaderFlag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        leaderSettings.add(setting);
        return setting;
    }

    /** A setting that belongs to both client surfaces, so the value does not change with the switch. */
    private ButtonSetting sharedFlag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        expoSettings.add(setting);
        leaderSettings.add(setting);
        return setting;
    }

    // ------------------------------------------------------------------ //
    // Client-aware accessors (the engine reads these instead of the raw settings)
    // ------------------------------------------------------------------ //

    private boolean leader() {
        return clientIndex() == CLIENT_LEADER;
    }

    /** Internal sort code: 0 view, 1 distance, 2 health, 3 hurt time, 4 armour. */
    private int sortCode() {
        if (!leader()) {
            return (int) Math.round(sort.getInput());
        }
        switch ((int) Math.round(lSort.getInput())) {
            case 1:
                return 2;
            case 2:
                return 3;
            case 3:
                return 0;
            default:
                return 1;
        }
    }

    private boolean switchMode() {
        return leader() ? Math.round(lMode.getInput()) == 1 : modeIndex() == 1;
    }

    /** Internal rotation code: 0 silent, 2 lock view, 3 client-only, 4 none. */
    private int rotationCode() {
        if (!leader()) {
            return rotationIndex();
        }
        switch ((int) Math.round(lRotations.getInput())) {
            case 0:
                return 4;
            case 1:
                return 3;
            case 3:
                return 2;
            default:
                return 0;
        }
    }

    /** Internal move-fix code: 0 silent, 1 strict, 2 none. */
    private int moveFixCode() {
        if (!leader()) {
            return (int) Math.round(moveFix.getInput());
        }
        switch ((int) Math.round(lMoveFix.getInput())) {
            case 1:
                return 0;
            case 2:
                return 1;
            default:
                return 2;
        }
    }

    private double smoothingValue() {
        return leader() ? lSmoothing.getInput() : rotationSmoothing.getInput();
    }

    private float angleStepValue() {
        return (float) (leader() ? lAngleStep.getInput() : angleStep.getInput());
    }

    private double attackRangeValue() {
        return leader() ? lAttackRange.getInput() : attackRange.getInput();
    }

    private double swingRangeValue() {
        return leader() ? lSwingRange.getInput() : swingRange.getInput();
    }

    private double minApsValue() {
        return leader() ? lMinCps.getInput() : minAps.getInput();
    }

    private double maxApsValue() {
        return leader() ? lMaxCps.getInput() : maxAps.getInput();
    }

    private long switchDelayValue() {
        // Leader counts in ticks, Expo in milliseconds.
        return leader() ? (long) (lSwitchDelay.getInput() * 50L) : (long) switchDelay.getInput();
    }

    private float fovValue() {
        return (float) (leader() ? lFov.getInput() : fov.getInput());
    }

    private boolean throughWallsValue() {
        return leader() ? lThroughWalls.isToggled() : throughWall.isToggled();
    }

    private boolean requireClickValue() {
        return leader() ? lRequirePress.isToggled() : (legit.isToggled() || requireClick.isToggled());
    }

    private boolean weaponAllowed() {
        if (!leader()) {
            return !requireSword.isToggled() || ItemUtils.isSword(mc.thePlayer.getHeldItem());
        }
        if (!lWeaponsOnly.isToggled()) {
            return true;
        }
        if (ItemUtils.isSword(mc.thePlayer.getHeldItem())) {
            return true;
        }
        return lAllowTools.isToggled() && mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof net.minecraft.item.ItemTool;
    }

    @Override
    public Entity getTarget() {
        return target;
    }

    @Override
    public String getInfo() {
        if (leader()) {
            return "Leader " + lMode.options[(int) Math.round(lMode.getInput())];
        }
        String modeName = mode.options[(int) mode.getInput()];
        return modeName;
    }

    // ------------------------------------------------------------------ //
    // Tick
    // ------------------------------------------------------------------ //

    @Override
    public void onEnable() {
        target = null;
        candidates = new ArrayList<EntityLivingBase>();
        switchIndex = 0;
        lastAttack = 0L;
        lastSwitch = 0L;
        pending = null;
    }

    @Override
    public void onDisable() {
        target = null;
        pending = null;
        RotationManager.killAura2NoMovementFix = false;
    }

    @Override
    public void onPreUpdate() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            target = null;
            pending = null;
            return;
        }
        RotationManager.killAura2NoMovementFix = moveFixCode() == 2;
        if (mc.currentScreen != null && (leader() || screenCheck.isToggled())) {
            target = null;
            pending = null;
            return;
        }
        selectTarget();
        updateRotation();
        attack();
    }

    private int moveFixIndex() {
        return (int) moveFix.getInput();
    }

    private int rotationIndex() {
        return (int) rotation.getInput();
    }

    // ------------------------------------------------------------------ //
    // Target selection
    // ------------------------------------------------------------------ //

    private void selectTarget() {
        EntityLivingBase previous = target;
        candidates = new ArrayList<EntityLivingBase>();
        double range = Math.max(attackRangeValue(), swingRangeValue());
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity == mc.thePlayer || entity.isDead || entity.deathTime != 0 || !entity.isEntityAlive()) {
                continue;
            }
            if (mc.thePlayer.getDistanceSqToEntity(entity) > range * range) {
                continue;
            }
            if (fovValue() < 360.0 && !PlayerUtils.inFov(fovValue(), entity)) {
                continue;
            }
            if (!throughWallsValue() && !RotationUtils.canSee(entity)) {
                continue;
            }
            if (!accepted(entity)) {
                continue;
            }
            candidates.add(entity);
        }
        if (candidates.isEmpty()) {
            target = null;
            pending = null;
            return;
        }
        candidates.sort(comparator());
        if (!switchMode()) {
            target = candidates.get(0);
        } else {
            long now = System.currentTimeMillis();
            if (target == null || !candidates.contains(target) || now - lastSwitch >= switchDelayValue()) {
                switchIndex = target == null ? 0 : (switchIndex + 1) % candidates.size();
                target = candidates.get(switchIndex % candidates.size());
                lastSwitch = now;
            }
        }
        targetChanged = target != previous;
        if (targetChanged) {
            lastAttack = 0L;
        }
    }

    private int modeIndex() {
        return (int) mode.getInput();
    }

    private Comparator<EntityLivingBase> comparator() {
        switch (sortCode()) {
            case 1:
                return Comparator.comparingDouble(e -> mc.thePlayer.getDistanceSqToEntity(e));
            case 2:
                return Comparator.comparingDouble(EntityLivingBase::getHealth);
            case 3:
                return Comparator.<EntityLivingBase>comparingInt(e -> -e.hurtTime);
            case 4:
                return Comparator.comparingInt(EntityLivingBase::getTotalArmorValue);
            default:
                return Comparator.<EntityLivingBase>comparingDouble(this::viewAngle);
        }
    }

    private double viewAngle(EntityLivingBase entity) {
        float[] rotations = RotationUtils.toEntityBox(entity);
        if (rotations == null) {
            return Double.MAX_VALUE;
        }
        double yaw = Math.abs(MathHelper.wrapAngleTo180_float(rotations[0] - mc.thePlayer.rotationYaw));
        double pitch = Math.abs(rotations[1] - mc.thePlayer.rotationPitch);
        return yaw * yaw + pitch * pitch;
    }

    /** Leader's RotationMode: Normal = box centre, Nearest = entity centre, Smart = visible point. */
    private net.minecraft.util.Vec3 leaderAimPoint() {
        int mode = (int) Math.round(lRotationMode.getInput());
        if (mode == 1) {
            return new net.minecraft.util.Vec3(target.posX, target.posY + target.getEyeHeight() * 0.5D, target.posZ);
        }
        if (mode == 2) {
            net.minecraft.util.Vec3 visible = RotationUtils.visiblePoint(target);
            if (visible != null) {
                return visible;
            }
        }
        AxisAlignedBB box = target.getEntityBoundingBox();
        return new net.minecraft.util.Vec3((box.minX + box.maxX) / 2.0D, (box.minY + box.maxY) / 2.0D,
                (box.minZ + box.maxZ) / 2.0D);
    }

    private boolean accepted(EntityLivingBase entity) {
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (!leader()) {
                String name = player.getName().toLowerCase();
                boolean friend = Friends.friends.contains(name) || Friends.isFriend(player.getName());
                boolean enemy = Friends.enemies.contains(name) || Friends.isEnemy(player.getName());
                if (friend) {
                    return friends.isToggled();
                }
                if (enemy) {
                    return enemies.isToggled();
                }
            }
            if (Hypixel.sameTeam(player) && !(leader() ? lTeams.isToggled() : teammates.isToggled())) {
                return false;
            }
            if (BotCheck.isBot(player) && !(leader() ? lBotCheck.isToggled() : bots.isToggled())) {
                return false;
            }
            return leader() ? lPlayers.isToggled() : players.isToggled();
        }
        if (entity instanceof EntitySilverfish) {
            return leader() ? lSilverfish.isToggled() : silverfishes.isToggled();
        }
        if (entity instanceof EntityIronGolem) {
            return leader() ? lGolems.isToggled() : golems.isToggled();
        }
        if (entity instanceof EntityDragon || entity instanceof EntityWither) {
            return leader() ? lBosses.isToggled() : bosses.isToggled();
        }
        if (entity instanceof EntityAnimal) {
            return leader() ? lAnimals.isToggled() : animals.isToggled();
        }
        if (entity instanceof EntityMob) {
            return leader() ? lMobs.isToggled() : mobs.isToggled();
        }
        return false;
    }

    // ------------------------------------------------------------------ //
    // Rotation
    // ------------------------------------------------------------------ //

    private void updateRotation() {
        if (target == null || rotationCode() == 4) {
            pending = null;
            return;
        }
        float[] wanted = leader() ? RotationUtils.toVec(leaderAimPoint()) : RotationUtils.toEntityBox(target);
        if (wanted == null) {
            pending = null;
            return;
        }
        float yaw = wanted[0];
        float pitch = wanted[1];
        if (smoothingValue() <= 0.0) {
            double step = Math.max(1.0, angleStepValue());
            currentYaw = approach(currentYaw, yaw, (float) step);
            currentPitch = approach(currentPitch, pitch, (float) step);
        } else {
            float fraction = (float) (Math.max(5.0, smoothingValue()) / 100.0);
            currentYaw += MathHelper.wrapAngleTo180_float(yaw - currentYaw) * fraction;
            currentPitch += (pitch - currentPitch) * fraction;
        }
        float[] fixed = RotationUtils.fixSensitivity(currentYaw, currentPitch, RotationManager.lastYaw, RotationManager.lastPitch);
        currentYaw = fixed[0];
        currentPitch = fixed[1];
        pending = new float[]{currentYaw, currentPitch};
        if (rotationCode() == 2) {
            mc.thePlayer.rotationYaw = currentYaw;
            mc.thePlayer.rotationPitch = currentPitch;
        }
    }

    private static float approach(float from, float to, float step) {
        float diff = MathHelper.wrapAngleTo180_float(to - from);
        if (Math.abs(diff) <= step) {
            return to;
        }
        return from + Math.signum(diff) * step;
    }

    /**
     * True while the player is blocking, or is about to: the use key only starts a block after the
     * server answers, so the press itself has to count as well (only swords can block in 1.8.9).
     */
    private boolean blocking() {
        if (PlayerUtils.isBlocking()) {
            return true;
        }
        return mc.currentScreen == null && mc.gameSettings.keyBindUseItem.isKeyDown()
                && PlayerUtils.holdingSword();
    }

    @Override
    public float[] getRotations(float yaw, float pitch) {
        if (!isEnabled() || target == null || pending == null || rotationCode() == 4) {
            return null;
        }
        return new float[]{pending[0], pending[1]};
    }

    // ------------------------------------------------------------------ //
    // Attack
    // ------------------------------------------------------------------ //

    private void attack() {
        if (target == null) {
            return;
        }
        if (!weaponAllowed()) {
            return;
        }
        // An attack packet in the same tick as the block's use packet is what the anti-cheat reads
        // as "multi action", so a held right click (or an active block) skips the whole tick.
        if (pauseWhileBlocking.isToggled() && blocking()) {
            return;
        }
        boolean click = Mouse.isButtonDown(0);
        if (requireClickValue() && !click) {
            return;
        }
        long now = System.currentTimeMillis();
        double min = Math.min(minApsValue(), maxApsValue());
        double max = Math.max(minApsValue(), maxApsValue());
        double aps = min + Math.random() * (max - min);
        long interval = (long) (1000.0 / Math.max(0.1, aps));
        if (now - lastAttack < interval) {
            return;
        }
        double distance = mc.thePlayer.getDistanceSqToEntity(target);
        double reach = attackRangeValue();
        if (distance <= reach * reach) {
            PlayerUtils.attack(target, false);
            lastAttack = now;
        } else if (distance <= swingRangeValue() * swingRangeValue()) {
            PlayerUtils.swing(false, true);
            lastAttack = now;
        }
    }

    // ------------------------------------------------------------------ //
    // Render
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (showReachRing.isToggled()) {
            Render3D.ring(mc.thePlayer.posX, mc.thePlayer.getEntityBoundingBox().minY + 0.01, mc.thePlayer.posZ,
                    attackRangeValue(), 90, 1.5, color(255), false);
        }
        if (target == null) {
            return;
        }
        int color = targetColor();
        int style = (int) showTarget.getInput();
        float partial = event.partialTicks;
        if (style == 0 || style == 1) {
            Render3D.entityBox(target, 2, color, partial);
        } else if (style == 2 || style == 3) {
            AxisAlignedBB box = target.getEntityBoundingBox();
            AxisAlignedBB head = new AxisAlignedBB(box.minX, box.maxY - 0.45, box.minZ, box.maxX, box.maxY, box.maxZ);
            net.minecraft.client.renderer.entity.RenderManager rm = mc.getRenderManager();
            head = head.offset(-rm.viewerPosX, -rm.viewerPosY, -rm.viewerPosZ);
            float alpha = (float) (showTargetOpacity.getInput() / 100.0);
            float r = ((color >> 16) & 255) / 255.0f;
            float g = ((color >> 8) & 255) / 255.0f;
            float b = (color & 255) / 255.0f;
            net.minecraft.client.renderer.GlStateManager.enableBlend();
            net.minecraft.client.renderer.GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            net.minecraft.client.renderer.GlStateManager.disableTexture2D();
            net.minecraft.client.renderer.GlStateManager.disableDepth();
            net.minecraft.client.renderer.GlStateManager.depthMask(false);
            Render3D.filledBox(head.expand(0.02, 0.02, 0.02), r, g, b, alpha);
            net.minecraft.client.renderer.GlStateManager.enableTexture2D();
            net.minecraft.client.renderer.GlStateManager.enableDepth();
            net.minecraft.client.renderer.GlStateManager.depthMask(true);
            net.minecraft.client.renderer.GlStateManager.disableBlend();
        } else if (style == 4) {
            AxisAlignedBB box = target.getEntityBoundingBox();
            Render3D.ring((box.minX + box.maxX) / 2.0, box.minY + 0.01, (box.minZ + box.maxZ) / 2.0,
                    Math.max(0.4, (box.maxX - box.minX) * 0.7), 80, 1.5, color, false);
        }
    }

    private int targetColor() {
        int alpha = (int) (showTargetOpacity.getInput() / 100.0 * 255.0);
        if (target.hurtTime > 5 && (showTarget.getInput() == 1 || showTarget.getInput() == 3)) {
            return RenderUtils.withAlpha(showTargetDamageColor.customColor(), alpha);
        }
        if (showTargetColor.getInput() == 2) {
            return RenderUtils.withAlpha(customColor.customColor(), alpha);
        }
        if (showTargetColor.getInput() == 1) {
            return RenderUtils.withAlpha(Theme.scheme().primaryContainer, alpha);
        }
        return RenderUtils.withAlpha(Theme.scheme().primary, alpha);
    }

    private int color(int alpha) {
        return RenderUtils.withAlpha(Theme.scheme().primary, alpha);
    }
}
