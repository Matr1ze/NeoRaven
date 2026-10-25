package xyz.daikirai.gui.onyx.render;

/** Float animation with a duration and easing, ported from Onyx's {@code render.misc.Cls}. */
public final class Anim {

    private float value;
    private float target;
    private float start;
    private float duration;
    private float elapsed;
    private Ease ease = Ease.DECELERATE;

    public Anim(float initial) {
        this.value = initial;
        this.target = initial;
    }

    public Anim to(float newTarget, float durationMs, Ease easing) {
        if (newTarget == this.target) {
            return this;
        }
        this.start = value();
        this.target = newTarget;
        this.duration = Math.max(durationMs, 0.0F);
        this.ease = easing == null ? Ease.LINEAR : easing;
        this.elapsed = 0.0F;
        return this;
    }

    public float target() {
        return target;
    }

    public void set(float v) {
        this.value = v;
        this.target = v;
        this.duration = 0.0F;
        this.elapsed = 0.0F;
    }

    public void update(float deltaMs) {
        if (!isDone()) {
            this.elapsed = Math.min(this.elapsed + deltaMs, this.duration);
        }
    }

    public boolean isDone() {
        return duration <= 0.0F || elapsed >= duration;
    }

    public float value() {
        if (duration <= 0.0F) {
            return target;
        }
        return start + (target - start) * ease.get(elapsed / duration);
    }

    /** The animation's raw progress in 0..1 (used for things like drag fades). */
    public float progress() {
        return duration <= 0.0F ? 1.0F : Math.min(elapsed / duration, 1.0F);
    }
}
