package xyz.daikirai.gui.onyx.menu;

/** Stage-weighted estimate: Forge has no single work counter for the whole startup. */
public final class StartupProgress {
    private static final float[] BOUNDS = {0.0F, 0.12F, 0.32F, 0.68F, 0.84F, 0.94F, 0.985F};
    private volatile int requestedPhase;
    private int phase = -1;
    private long phaseStarted;
    private float greatest;

    public void advance(String state) {
        int next;
        if ("CONSTRUCTING".equals(state)) next = 1;
        else if ("PREINITIALIZATION".equals(state)) next = 2;
        else if ("INITIALIZATION".equals(state)) next = 3;
        else if ("POSTINITIALIZATION".equals(state)) next = 4;
        else if ("AVAILABLE".equals(state)) next = 5;
        else return;
        requestedPhase = Math.max(requestedPhase, next);
    }

    public float sample(float localProgress, long now) {
        int next = requestedPhase;
        if (phase != next) { phase = next; phaseStarted = now; }
        float elapsed = Math.max(0L, now - phaseStarted) / 1.0e9F;
        // Local completion covers only part of this phase. A slow approach keeps the line
        // advancing during uncounted work, but cannot cross a real lifecycle boundary.
        float activity = Math.max(Math.max(0, Math.min(1, localProgress)) * 0.70F,
                0.97F * (1.0F - (float) Math.exp(-elapsed / 14.0F)));
        float target = BOUNDS[phase] + (BOUNDS[phase + 1] - BOUNDS[phase]) * activity;
        greatest = Math.max(greatest, target);
        return greatest;
    }
}
