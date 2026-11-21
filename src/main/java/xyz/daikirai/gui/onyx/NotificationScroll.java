package xyz.daikirai.gui.onyx;

/** Critically damped scrolling in design pixels. Wheel events only change the destination. */
final class NotificationScroll {
    private float position, target, velocity;

    float position() { return position; }
    float target() { return target; }

    void wheel(float steps, float maximum) {
        target = clamp(target - steps * 48, maximum);
    }

    void clampTo(float maximum) {
        target = clamp(target, maximum);
        position = clamp(position, maximum);
        if (position == 0 && velocity < 0 || position == maximum && velocity > 0) velocity = 0;
    }

    void reset() { position = target = velocity = 0; }

    void update(float milliseconds) {
        // Exact spring solution stays consistent across 30/60/144+ fps and rapid wheel reversals.
        float dt = Math.max(0, milliseconds) / 1000;
        float displacement = position - target;
        float impulse = velocity + 18 * displacement;
        float decay = (float) Math.exp(-18 * dt);
        position = target + (displacement + impulse * dt) * decay;
        velocity = (velocity - 18 * impulse * dt) * decay;
        if (Math.abs(position - target) < 0.02F && Math.abs(velocity) < 0.1F) {
            position = target; velocity = 0;
        }
    }

    private static float clamp(float value, float maximum) {
        return Math.max(0, Math.min(value, maximum));
    }
}
