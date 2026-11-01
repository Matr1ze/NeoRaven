package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Camera — port of Onyx's {@code CameraModule}.
 *
 * <p>Upstream has three separate effects and this port now has all three that matter for the feel:</p>
 *
 * <ul>
 *   <li><b>Trailing third-person camera.</b> The camera position eases towards the player instead of
 *   being pinned to it, so walking makes the view drift after you (the "action cam" look). The
 *   smoothing is a millisecond time constant — {@code 95%} keeps roughly 380ms of lag — and snaps
 *   after a teleport so it cannot fall behind by more than a few blocks.</li>
 *   <li><b>First-person smoothing</b> (opt-in, default off, like upstream's {@code First person}
 *   toggle): the aim lags your real rotation.</li>
 *   <li><b>Third-person distance</b>: the vanilla {@code thirdPersonDistance} field scaled by the
 *   {@code Distance} slider. Onyx scales the same constant; 1.8.9 keeps the value in that field, so
 *   the port writes it directly instead of patching a literal that does not exist.</li>
 * </ul>
 *
 * <p>The trailing offset is applied by {@code MixinEntityRenderer} at the end of {@code orientCamera},
 * as a camera-space translate. Freelook still wins over the smoothed angles.</p>
 */
public final class CameraModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Vanilla third-person distance, the value {@code Distance} = 100% keeps. */
    private static final float VANILLA_DISTANCE = 4.0F;
    /** Upper bound on the trailing offset, in blocks (the original's 6 block cap). */
    private static final double MAX_OFFSET = 6.0D;
    /** A jump longer than this many blocks is treated as a teleport and snaps the camera. */
    private static final double SNAP_DISTANCE_SQUARED = 64.0D;

    public static CameraModule instance;

    public static SliderSetting smoothing;
    public static ButtonSetting smoothPitch;
    public static ButtonSetting firstPerson;
    public static SliderSetting distance;

    private float smoothYaw;
    private float smoothPitchValue;
    private float lastYaw;
    private float lastPitch;
    private long angleNanos;

    private double trailX;
    private double trailY;
    private double trailZ;
    private double lastX;
    private double lastY;
    private double lastZ;
    private boolean trailReady;
    private long trailNanos;
    private float trailOffsetX;
    private float trailOffsetY;
    private float trailOffsetZ;

    public CameraModule(int key) {
        super("Camera", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        smoothing = (SliderSetting) ModuleManager.setting(this, "Smoothing");
        smoothPitch = (ButtonSetting) ModuleManager.setting(this, "Smooth pitch");
        firstPerson = (ButtonSetting) ModuleManager.setting(this, "First person");
        distance = (SliderSetting) ModuleManager.setting(this, "Distance");
    }

    @Override
    public String getInfo() {
        return smoothing == null ? null : (int) Math.round(smoothing.getInput()) + "%";
    }

    @Override
    public void onEnable() {
        reset();
        applyDistance();
    }

    @Override
    public void onDisable() {
        reset();
        restoreDistance();
    }

    @Override
    public void onUpdate() {
        applyDistance();
    }

    private void reset() {
        if (mc.thePlayer != null) {
            smoothYaw = mc.thePlayer.rotationYaw;
            smoothPitchValue = mc.thePlayer.rotationPitch;
        }
        lastYaw = smoothYaw;
        lastPitch = smoothPitchValue;
        angleNanos = 0L;
        trailReady = false;
        trailNanos = 0L;
        trailOffsetX = trailOffsetY = trailOffsetZ = 0.0F;
    }

    // ------------------------------------------------------------------ //
    // Third-person distance
    // ------------------------------------------------------------------ //

    private void applyDistance() {
        if (distance == null || !isEnabled()) {
            return;
        }
        // Extend Camera owns the same vanilla field; the last one enabled wins, so leave it alone.
        if (ExtendCameraModule.instance != null && ExtendCameraModule.instance.isEnabled()) {
            return;
        }
        setThirdPersonDistance(VANILLA_DISTANCE * (float) (distance.getInput() / 100.0));
    }

    private void restoreDistance() {
        if (ExtendCameraModule.instance != null && ExtendCameraModule.instance.isEnabled()) {
            return;
        }
        setThirdPersonDistance(VANILLA_DISTANCE);
    }

    private static void setThirdPersonDistance(float value) {
        try {
            ReflectionHelper.setPrivateValue(net.minecraft.client.renderer.EntityRenderer.class,
                    mc.entityRenderer, value, "thirdPersonDistance", "field_78490_B");
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ //
    // First-person angle smoothing
    // ------------------------------------------------------------------ //

    /**
     * Camera angle hook used by the {@code orientCamera} redirects. Freelook wins when it is active;
     * otherwise the smoothed angle is returned for the player entity in first person.
     */
    public static float cameraAngle(Entity entity, float value, boolean pitch, boolean previous) {
        float freelook = FreelookModule.cameraAngle(entity, value, pitch, previous);
        if (freelook != value) {
            return freelook;
        }
        CameraModule module = instance;
        if (module == null || !module.isEnabled() || entity != mc.thePlayer || smoothing == null
                || mc.gameSettings.thirdPersonView != 0
                || firstPerson == null || !firstPerson.isToggled()) {
            return value;
        }
        module.step(entity);
        if (pitch) {
            return previous ? module.lastPitch : module.smoothPitchValue;
        }
        return previous ? module.lastYaw : module.smoothYaw;
    }

    /** Frame-rate independent easing towards the real look direction. */
    private void step(Entity entity) {
        long now = System.nanoTime();
        float delta = angleNanos == 0L ? 16.0F : Math.min(100.0F, (now - angleNanos) / 1000000.0F);
        angleNanos = now;
        float rate = (float) easing(delta, smoothing.getInput());
        lastYaw = smoothYaw;
        lastPitch = smoothPitchValue;
        float yawDiff = MathHelper.wrapAngleTo180_float(entity.rotationYaw - smoothYaw);
        smoothYaw += yawDiff * rate;
        if (smoothPitch == null || smoothPitch.isToggled()) {
            smoothPitchValue += (entity.rotationPitch - smoothPitchValue) * rate;
        } else {
            smoothPitchValue = entity.rotationPitch;
        }
    }

    /** Upstream's {@code 1 - exp(-dt / (smoothing% * 400ms))}: bigger % = more lag. */
    private static double easing(float delta, double percent) {
        if (percent <= 0.0D) {
            return 1.0D;
        }
        return 1.0D - Math.exp(-delta / (Math.max(1.0D, percent) / 100.0D * 400.0D));
    }

    // ------------------------------------------------------------------ //
    // Trailing third-person position
    // ------------------------------------------------------------------ //

    /**
     * Applies the trailing offset to the current camera, called at the end of {@code orientCamera}.
     *
     * <p>The offset is a plain world-space translate appended after the camera's rotations, which is
     * exactly "move the lens back by this many blocks": the world behind it keeps its own transform,
     * so every world-space overlay (ESP, tracers, the hand) moves with it.</p>
     */
    public static void applyTrail(float partialTicks) {
        CameraModule module = instance;
        if (module == null || !module.isEnabled() || smoothing == null) {
            return;
        }
        if (mc.gameSettings.thirdPersonView == 0 || mc.gameSettings.debugCamEnable) {
            return;
        }
        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        module.updateTrail(view, partialTicks);
        if (module.trailOffsetX != 0.0F || module.trailOffsetY != 0.0F || module.trailOffsetZ != 0.0F) {
            net.minecraft.client.renderer.GlStateManager.translate(-module.trailOffsetX,
                    -module.trailOffsetY, -module.trailOffsetZ);
        }
    }

    private void updateTrail(Entity view, float partialTicks) {
        double x = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
        double y = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
        double z = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
        long now = System.nanoTime();
        float delta = trailNanos == 0L ? 16.0F : Math.min(100.0F, (now - trailNanos) / 1000000.0F);
        // orientCamera runs twice per frame (world pass and hand pass); both must use the same
        // offset or the hand would be drawn from a different camera than the world behind it.
        if (trailNanos != 0L && delta < 1.0F) {
            return;
        }
        trailNanos = now;
        double rate = easing(delta, smoothing.getInput());
        if (!trailReady) {
            trailReady = true;
            trailX = x;
            trailY = y;
            trailZ = z;
        } else {
            double jumpX = x - lastX;
            double jumpY = y - lastY;
            double jumpZ = z - lastZ;
            if (jumpX * jumpX + jumpY * jumpY + jumpZ * jumpZ > SNAP_DISTANCE_SQUARED) {
                trailX = x;
                trailY = y;
                trailZ = z;
            } else {
                trailX += (x - trailX) * rate;
                trailY += (y - trailY) * rate;
                trailZ += (z - trailZ) * rate;
            }
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        double offsetX = trailX - x;
        double offsetY = trailY - y;
        double offsetZ = trailZ - z;
        double length = Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        if (length > MAX_OFFSET) {
            double scale = MAX_OFFSET / length;
            offsetX *= scale;
            offsetY *= scale;
            offsetZ *= scale;
        }
        trailOffsetX = (float) offsetX;
        trailOffsetY = (float) offsetY;
        trailOffsetZ = (float) offsetZ;
    }
}
