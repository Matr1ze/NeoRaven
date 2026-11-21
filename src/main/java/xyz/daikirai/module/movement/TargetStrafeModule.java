package xyz.daikirai.module.movement;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;
import xyz.daikirai.util.MoveDirection;
import xyz.daikirai.util.PlayerUtils;
import xyz.daikirai.util.RotationUtils;

public final class TargetStrafeModule extends Module {
    private SliderSetting range, dots, thickness;
    private ButtonSetting holdJump, requireSpeedOrFlight, autoThirdPerson, circle, glow, behind;
    private EntityLivingBase target;
    private float yaw;
    private MoveDirection inputDirection;
    private boolean left, collided, ownsCamera, manualCamera;

    public TargetStrafeModule(int key) {
        super("TargetStrafe", Category.movement, key);
    }

    @Override public void link() {
        range = (SliderSetting) ModuleManager.setting(this, "Range");
        dots = (SliderSetting) ModuleManager.setting(this, "Dots");
        thickness = (SliderSetting) ModuleManager.setting(this, "Thickness");
        holdJump = (ButtonSetting) ModuleManager.setting(this, "Hold Jump");
        requireSpeedOrFlight = (ButtonSetting) ModuleManager.setting(this, "Require Speed or Flight");
        autoThirdPerson = (ButtonSetting) ModuleManager.setting(this, "Auto third person camera");
        circle = (ButtonSetting) ModuleManager.setting(this, "Circle");
        glow = (ButtonSetting) ModuleManager.setting(this, "Glow");
        behind = (ButtonSetting) ModuleManager.setting(this, "Behind");
    }

    @Override public void onEnable() {
        left = collided = false;
    }

    @Override public void onDisable() {
        clearTarget();
    }

    @Override public void onUpdate() {
        if (!eligible() || target != null && !valid(target)) clearTarget();
        else updateCamera();
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) clearTarget();
    }

    private void clearTarget() {
        target = null;
        collided = false;
        updateCamera();
    }

    private boolean eligible() {
        return mc.theWorld != null && mc.thePlayer != null && mc.thePlayer.isEntityAlive()
                && !mc.thePlayer.isSpectator() && mc.thePlayer.ridingEntity == null
                && mc.getRenderViewEntity() == mc.thePlayer && mc.currentScreen == null
                && ModuleManager.enabled("Scaffold") == null && ModuleManager.enabled("Scaffold2") == null
                && (!holdJump.isToggled() || mc.gameSettings.keyBindJump.isKeyDown())
                && (!requireSpeedOrFlight.isToggled() || ModuleManager.enabled("Speed") != null
                    || flying());
    }

    private boolean flying() {
        return mc.thePlayer.capabilities.isFlying || ModuleManager.enabled("Fly") != null;
    }

    private boolean valid(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer
                || entity.worldObj != mc.theWorld || !entity.isEntityAlive()) return false;
        double limit = range.getInput() + 6;
        return mc.thePlayer.getDistanceSqToEntity(entity) <= limit * limit;
    }

    private EntityLivingBase findTarget() {
        Module aura2 = ModuleManager.enabled("KillAura2");
        Entity primary = aura2 == null ? null : aura2.getTarget();
        if (valid(primary)) return (EntityLivingBase) primary;
        Module aura = ModuleManager.enabled("KillAura");
        Entity fallback = aura == null ? null : aura.getTarget();
        return valid(fallback) ? (EntityLivingBase) fallback : null;
    }

    @Override public void onPostPlayerInput() {
        target = eligible() ? findTarget() : null;
        if (target == null) {
            clearTarget();
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        boolean leftKey = mc.gameSettings.keyBindLeft.isKeyDown();
        boolean rightKey = mc.gameSettings.keyBindRight.isKeyDown();
        if (leftKey != rightKey) left = leftKey;
        else if (player.isCollidedHorizontally && !collided) left = !left;
        collided = player.isCollidedHorizontally;
        yaw = orbitYaw(player, target);
        if (player.onGround && !flying() && !groundAhead(player, yaw)) {
            if (!behind.isToggled()) {
                left = !left;
                yaw = orbitYaw(player, target);
            }
            if (!groundAhead(player, yaw)) {
                clearTarget();
                return;
            }
        }
        float forward = player.movementInput.moveForward;
        float strafe = player.movementInput.moveStrafe;
        inputDirection = PlayerUtils.direction(forward, strafe);
        player.movementInput.moveForward = (float) Math.min(1, Math.hypot(forward, strafe));
        player.movementInput.moveStrafe = 0;
        updateCamera();
    }

    private float orbitYaw(EntityPlayerSP player, EntityLivingBase center) {
        float bearing = RotationUtils.fromDelta(center.posX - player.posX, 0, center.posZ - player.posZ)[0];
        double orbit = Math.toRadians(behind.isToggled() ? center.rotationYaw + 180 : bearing + (left ? -135 : 135));
        double x = center.posX - Math.sin(orbit) * range.getInput();
        double z = center.posZ + Math.cos(orbit) * range.getInput();
        return RotationUtils.fromDelta(x - player.posX, 0, z - player.posZ)[0];
    }

    private boolean groundAhead(EntityPlayerSP player, float direction) {
        double radians = Math.toRadians(direction);
        double x = player.posX - Math.sin(radians), z = player.posZ + Math.cos(radians);
        return mc.theWorld.rayTraceBlocks(new Vec3(x, player.posY + .1, z),
                new Vec3(x, player.posY - 3, z)) != null;
    }

    @Override public Float getMovementYaw() {
        if (!eligible() || !valid(target)) return null;
        return yaw;
    }

    public static TargetStrafeModule activeOrbit() {
        TargetStrafeModule module = (TargetStrafeModule) ModuleManager.enabled("TargetStrafe");
        return module != null && module.getMovementYaw() != null ? module : null;
    }

    public float orbitDirection() {
        return yaw;
    }

    MoveDirection inputDirection() {
        return inputDirection;
    }

    private void updateCamera() {
        if (target == null || !autoThirdPerson.isToggled()) {
            if (ownsCamera && mc.gameSettings.thirdPersonView == 1) mc.gameSettings.thirdPersonView = 0;
            ownsCamera = manualCamera = false;
        } else if (ownsCamera && mc.gameSettings.thirdPersonView != 1) {
            ownsCamera = false;
            manualCamera = true;
        } else if (!ownsCamera && !manualCamera && mc.gameSettings.thirdPersonView == 0) {
            mc.gameSettings.thirdPersonView = 1;
            ownsCamera = true;
        }
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if (!circle.isToggled() || !eligible() || !valid(target)) return;
        Vec3 center = WorldRenderState.position(target, event.partialTicks);
        RenderManager origin = mc.getRenderManager();
        double x = center.xCoord - origin.viewerPosX;
        double y = center.yCoord - origin.viewerPosY + .025;
        double z = center.zCoord - origin.viewerPosZ;
        int color = Theme.scheme().primary;
        float size = (float) thickness.getInput();
        try (WorldRenderState state = new WorldRenderState(false)) {
            GL11.glEnable(GL11.GL_POINT_SMOOTH);
            if (glow.isToggled()) {
                drawDots(x, y, z, size + 4, color, .14);
                drawDots(x, y, z, size + 2, color, .30);
            }
            drawDots(x, y, z, size, color, .94);
        }
    }

    private void drawDots(double x, double y, double z, float size, int color, double alpha) {
        double radius = range.getInput();
        int step = Math.max(1, (int) dots.getInput());
        GL11.glPointSize(size);
        WorldRenderState.color(color, alpha);
        GL11.glBegin(GL11.GL_POINTS);
        for (int i = 0; i < 360; i += step) {
            double angle = Math.toRadians(i);
            GL11.glVertex3d(x + Math.sin(angle) * radius, y, z + Math.cos(angle) * radius);
        }
        GL11.glEnd();
    }
}
