package xyz.daikirai.module.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import xyz.daikirai.client.BotCheck;
import xyz.daikirai.client.Friends;
import xyz.daikirai.client.Hypixel;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationUtils;

/** Flux's target selection and cadence using the client's normal camera and attack path. */
final class KillAura2Flux {
    private final Minecraft mc = Minecraft.getMinecraft();
    final List<Setting> settings = new ArrayList<>();
    private final SliderSetting mode = mode("Flux mode", "Single", "Switch");
    private final SliderSetting priority = mode("Priority", "Angle", "Health", "FOV", "Range", "Armor");
    private final SliderSetting range = slider("Range", 4.2, 1, 8, .1, " m");
    private final SliderSetting aps = slider("APS", 10, 1, 20, 1, "");
    private final SliderSetting switchDelay = slider("Switch Delay", 1000, 100, 2000, 100, " ms");
    private final SliderSetting switchSize = slider("Switch Size", 3, 1, 8, 1, "");
    private final SliderSetting fov = slider("FOV", 360, 10, 360, 10, "°");
    private final ButtonSetting rotateInstantly = flag("Rotate Instantly", true);
    private final SliderSetting rotationSpeed = slider("Rotation Speed", 120, 30, 180, 1, "°");
    private final SliderSetting yawOffset = slider("Yaw Offset", 0, -1, 1, .1, "");
    private final SliderSetting pitchOffset = slider("Pitch Offset", 0, -1, 1, .1, "");
    private final ButtonSetting players = flag("Players", true);
    private final ButtonSetting mobs = flag("Mobs", false);
    private final ButtonSetting invisible = flag("Invisible", true);
    final ButtonSetting autoDisable = flag("Auto Disable", true);

    private final List<EntityLivingBase> candidates = new ArrayList<>();
    private WorldClient world;
    private EntityPlayer owner;
    private EntityLivingBase target;
    private long lastSwitch, nextAttack;

    void reset() {
        world = null;
        owner = null;
        clearTarget();
        nextAttack = 0;
    }

    boolean syncPlayer() {
        boolean changed = owner != null && (owner != mc.thePlayer || world != mc.theWorld);
        if (owner != mc.thePlayer || world != mc.theWorld) {
            reset();
            owner = mc.thePlayer;
            world = mc.theWorld;
        }
        return changed;
    }

    void clearTarget() {
        candidates.clear();
        target = null;
        lastSwitch = 0;
    }

    String modeName() {
        return mode.options[(int) Math.round(mode.getInput())];
    }

    EntityLivingBase tick() {
        long now = System.nanoTime();
        candidates.clear();
        for (Entity entity : world.loadedEntityList) {
            if (entity instanceof EntityLivingBase && valid((EntityLivingBase) entity)) {
                candidates.add((EntityLivingBase) entity);
            }
        }
        if (candidates.isEmpty()) {
            clearTarget();
            return null;
        }
        candidates.sort(comparator().thenComparingInt(Entity::getEntityId));
        if (mode.getInput() == 0) {
            target = candidates.get(0);
        } else {
            int count = Math.min(candidates.size(), (int) switchSize.getInput());
            int index = candidates.indexOf(target);
            if (index < 0 || index >= count) {
                target = candidates.get(0);
                lastSwitch = now;
            } else if (now - lastSwitch >= switchDelay.getInput() * 1_000_000L) {
                target = candidates.get((index + 1) % count);
                lastSwitch = now;
            }
        }
        // The renderer updates the vanilla mouse-over result after camera rotation.
        // Requiring that hit keeps actual attacks within normal reach and line of sight.
        if (now >= nextAttack && mc.objectMouseOver != null && mc.objectMouseOver.entityHit == target
                && mc.objectMouseOver.hitVec != null
                && owner.getPositionEyes(1).distanceTo(mc.objectMouseOver.hitVec) <= PlayerUtils.vanillaReach()
                && !owner.isUsingItem()) {
            PlayerUtils.attack(target, false);
            nextAttack = now + (long) (1_000_000_000L / aps.getInput());
        }
        rotate();
        return target;
    }

    private boolean valid(EntityLivingBase entity) {
        if (entity == owner || !entity.isEntityAlive() || entity.deathTime != 0
                || (!invisible.isToggled() && entity.isInvisible())
                || owner.getDistanceSqToEntity(entity) > range.getInput() * range.getInput()
                || (fov.getInput() < 360 && !PlayerUtils.inFov((float) fov.getInput(), entity))) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (!players.isToggled() || player.isSpectator() || Friends.isFriend(player.getName())
                    || Hypixel.sameTeam(player) || BotCheck.isBot(player)) return false;
        } else if (!mobs.isToggled() || !(entity instanceof EntityCreature || entity instanceof EntitySlime
                || entity instanceof EntityBat || entity instanceof EntitySquid)) {
            return false;
        }
        return RotationUtils.canSee(entity);
    }

    private Comparator<EntityLivingBase> comparator() {
        switch ((int) priority.getInput()) {
            case 1: return Comparator.comparingDouble(EntityLivingBase::getHealth);
            case 2: return Comparator.comparingDouble(this::viewAngle);
            case 3: return Comparator.comparingDouble(owner::getDistanceSqToEntity);
            case 4: return Comparator.comparingInt(EntityLivingBase::getTotalArmorValue);
            default: return Comparator.comparingDouble(this::yawDifference);
        }
    }

    private double yawDifference(EntityLivingBase entity) {
        float[] wanted = RotationUtils.toEntityBox(entity);
        return Math.abs(MathHelper.wrapAngleTo180_float(wanted[0] - owner.rotationYaw));
    }

    private double viewAngle(EntityLivingBase entity) {
        float[] wanted = RotationUtils.toEntityBox(entity);
        double yaw = MathHelper.wrapAngleTo180_float(wanted[0] - owner.rotationYaw);
        return Math.hypot(yaw, wanted[1] - owner.rotationPitch);
    }

    private void rotate() {
        AxisAlignedBB box = target.getEntityBoundingBox();
        double horizontal = MathHelper.clamp_double(.5 + yawOffset.getInput() / 2, .05, .95);
        double vertical = MathHelper.clamp_double(.5 + pitchOffset.getInput() / 2, .05, .95);
        Vec3 point = new Vec3(box.minX + (box.maxX - box.minX) * horizontal,
                box.minY + (box.maxY - box.minY) * vertical, box.minZ + (box.maxZ - box.minZ) * horizontal);
        if (world.rayTraceBlocks(owner.getPositionEyes(1), point) != null) {
            Vec3 visible = RotationUtils.visiblePoint(target);
            if (visible != null) point = visible;
        }
        float[] wanted = RotationUtils.toVec(point);
        float step = rotateInstantly.isToggled() ? 180 : (float) rotationSpeed.getInput();
        owner.rotationYaw += MathHelper.clamp_float(
                MathHelper.wrapAngleTo180_float(wanted[0] - owner.rotationYaw), -step, step);
        owner.rotationPitch += MathHelper.clamp_float(wanted[1] - owner.rotationPitch, -step, step);
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        settings.add(setting);
        return setting;
    }

    private SliderSetting mode(String name, String... options) {
        SliderSetting setting = new SliderSetting(name, "", "", 0, 0, options.length - 1, 1, options, null);
        settings.add(setting);
        return setting;
    }

    private ButtonSetting flag(String name, boolean value) {
        ButtonSetting setting = new ButtonSetting(name, value);
        settings.add(setting);
        return setting;
    }
}
