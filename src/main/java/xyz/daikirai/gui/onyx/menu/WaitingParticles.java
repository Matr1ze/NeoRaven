package xyz.daikirai.gui.onyx.menu;

/** Bounded, allocation-free particle simulation in splash design coordinates. */
final class WaitingParticles {
    static final int COUNT = 112;
    private static final float STEP = 1.0F / 120.0F;
    final float[] x = new float[COUNT], y = new float[COUNT];
    final float[] vx = new float[COUNT], vy = new float[COUNT];
    final float[] radius = new float[COUNT], brightness = new float[COUNT];
    private final float[] speed = new float[COUNT], phase = new float[COUNT];
    private float accumulator, age, lastWidth, lastHeight;
    private boolean active;

    boolean active() { return active; }
    float age() { return age; }
    void reset() { active = false; accumulator = age = 0; }

    void emit(float cx, float cy, float width, float height) {
        reset();
        active = true;
        lastWidth = width;
        lastHeight = height;
        for (int i = 0; i < COUNT; i++) {
            double angle = 2 * Math.PI * (i + random(i, 1) * 0.8F) / COUNT;
            float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
            float offset = 3 + 9 * random(i, 2);
            float launch = 265 + 180 * random(i, 3);
            x[i] = cx + dx * offset;
            y[i] = cy + dy * offset;
            vx[i] = dx * launch;
            vy[i] = dy * launch;
            radius[i] = 0.8F + 1.3F * random(i, 4);
            brightness[i] = 0.30F + 0.42F * random(i, 5);
            speed[i] = 62 + 100 * random(i, 6);
            phase[i] = (float) (Math.PI * 2 * random(i, 7));
        }
    }

    void update(float elapsed, float targetX, float targetY, float width, float height) {
        if (!active || width <= 0 || height <= 0) return;
        if (width != lastWidth || height != lastHeight) {
            float sx = width / lastWidth, sy = height / lastHeight;
            for (int i = 0; i < COUNT; i++) {
                x[i] *= sx; y[i] *= sy; vx[i] *= sx; vy[i] *= sy;
            }
            lastWidth = width; lastHeight = height;
        }
        targetX = Math.max(0, Math.min(width, targetX));
        targetY = Math.max(0, Math.min(height, targetY));
        // Catch up at most 12 substeps after a loading stall. Never teleport through an
        // orbit or spend seconds simulating a hidden/minimized window's missed frames.
        accumulator += Math.max(0, Math.min(0.1F, elapsed));
        while (accumulator + 0.000001F >= STEP) {
            accumulator = Math.max(0, accumulator - STEP);
            age += STEP;
            for (int i = 0; i < COUNT; i++) {
                float dx = targetX - x[i], dy = targetY - y[i];
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float nx, ny;
                if (distance < 0.001F) {
                    nx = (float) Math.cos(phase[i]); ny = (float) Math.sin(phase[i]);
                } else { nx = dx / distance; ny = dy / distance; }
                float tx = -ny, ty = nx;
                float radialSpeed = vx[i] * nx + vy[i] * ny;
                float tangentSpeed = vx[i] * tx + vy[i] * ty;
                // Attraction starts on the very first step, including a centre-positioned
                // cursor. Radial launch velocity still carries the sparks outward first.
                float attraction = Math.min(1250, 95 + distance * 0.85F);
                float radial = attraction - radialSpeed * 0.75F;
                // Two incommensurate waves vary each orbit's speed and radius continuously.
                float wanted = speed[i] * (1 + 0.29F * (float) Math.sin(age * 0.83F + phase[i])
                        + 0.16F * (float) Math.sin(age * 1.37F + phase[i] * 2));
                float turn = (wanted - tangentSpeed) * 1.65F;
                vx[i] += (nx * radial + tx * turn) * STEP;
                vy[i] += (ny * radial + ty * turn) * STEP;
                // Soft viewport containment keeps edge-positioned cursor orbits visible.
                vx[i] += (Math.max(0, 12 - x[i]) - Math.max(0, x[i] - width + 12)) * 12 * STEP;
                vy[i] += (Math.max(0, 12 - y[i]) - Math.max(0, y[i] - height + 12)) * 12 * STEP;
                x[i] += vx[i] * STEP;
                y[i] += vy[i] * STEP;
            }
        }
    }

    private static float random(int index, int salt) {
        int value = (index + 1) * 0x45d9f3b ^ salt * 0x27d4eb2d;
        value ^= value >>> 16;
        value *= 0x45d9f3b;
        value ^= value >>> 16;
        return (value & 0xFFFFFF) / (float) 0x1000000;
    }
}
