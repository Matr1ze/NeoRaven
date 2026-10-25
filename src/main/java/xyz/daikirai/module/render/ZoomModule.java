package xyz.daikirai.module.render;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Smooth hold-to-zoom camera, ported from OpenOnyx's {@code ZoomModule}.
 *
 * <p>Onyx kept the module switched on and gave it a separate "Zoom Key" setting: the camera eased
 * towards {@code FOV / Distance} while that key was held and eased back on release. This client
 * already has per-module binds with a hold mode, so the port uses that instead of a second key:
 * the module is created in hold mode and its own bind (default {@code V}) acts as the zoom key.
 * Holding it zooms, letting go eases back — and a plain toggle from the ClickGUI simply stays
 * zoomed, which is what "enabled" reads as.</p>
 *
 * <p>The fade is advanced once per frame from {@code EntityRenderer.setupCameraTransform}, so it
 * keeps running while the module is disabled (that is what makes the release animate instead of
 * snapping), and it is time based: a stalled frame cannot overshoot and a second camera setup in
 * the same frame (the name-plate projection does one) advances it by nothing.</p>
 */
public final class ZoomModule extends Module {

    public static ZoomModule instance;

    /** Epsilon for the fade and for "close enough to the target". */
    private static final float EPSILON = 0.0005F;

    private final SliderSetting distance = slider("Distance", 4.0, 1.5, 12.0, 0.5, "x");
    private final SliderSetting animation = slider("Animation", 180.0, 0.0, 500.0, 10.0, " ms");

    /** Eased fade, 0 = normal FOV, 1 = fully zoomed. */
    private float current;
    private long lastFrame;

    public ZoomModule(int key) {
        super("Zoom", Category.render, key);
        instance = this;
        // Hold to zoom: the bind owns the enabled state, so releasing the key reports "disabled"
        // and the fade below runs the camera back out.
        setHoldBind(true);
    }

    private SliderSetting slider(String name, double value, double min, double max, double step, String suffix) {
        SliderSetting setting = new SliderSetting(name, "", suffix, value, min, max, step, null, null);
        registerSetting(setting);
        return setting;
    }

    public float distance() {
        return (float) Math.max(1.0D, distance.getInput());
    }

    // ------------------------------------------------------------------ //
    // Frame + FOV
    // ------------------------------------------------------------------ //

    /** Advances the fade by the time since the previous frame. */
    public void tick() {
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F);
        lastFrame = now;
        tick(delta);
    }

    /** Advances the fade by an explicit amount of milliseconds (the harness drives it this way). */
    public void tick(float deltaMillis) {
        float target = isEnabled() ? 1.0F : 0.0F;
        float duration = (float) animation.getInput();
        if (duration <= 0.0F) {
            current = target;
            return;
        }
        // Ease-out cubic on the step fraction, the same curve Onyx's getFloat36 used.
        float step = Math.min(1.0F, Math.max(0.0F, deltaMillis) / duration);
        float eased = 1.0F - (1.0F - step) * (1.0F - step) * (1.0F - step);
        current += (target - current) * eased;
        if (Math.abs(target - current) < EPSILON) {
            current = target;
        }
    }

    /** Squeezes {@code fov} towards {@code fov / distance} by the current fade. */
    public float apply(float fov) {
        if (current <= EPSILON) {
            return fov;
        }
        float eased = current * current * (3.0F - 2.0F * current);
        float zoomed = fov / distance();
        return fov + (zoomed - fov) * eased;
    }

    /** How far the zoom is currently applied, between 0 and 1 (used by the dev harness). */
    public float value() {
        return current;
    }

    @Override
    public void onEnable() {
        // Start the fade from wherever it is; a fresh press after a long pause must not jump.
        lastFrame = 0L;
    }

    // ------------------------------------------------------------------ //
    // Hooks used by MixinEntityRenderer
    // ------------------------------------------------------------------ //

    public static void tickStatic() {
        ZoomModule module = instance;
        if (module != null) {
            module.tick();
        }
    }

    public static float applyStatic(float fov) {
        ZoomModule module = instance;
        return module == null ? fov : module.apply(fov);
    }
}
