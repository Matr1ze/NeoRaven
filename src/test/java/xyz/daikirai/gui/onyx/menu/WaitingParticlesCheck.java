package xyz.daikirai.gui.onyx.menu;

/** Physics regression checks without Minecraft, input devices or a GL context. */
public final class WaitingParticlesCheck {
    public static void main(String[] args) {
        WaitingParticles p = new WaitingParticles();
        p.emit(450, 300, 900, 600);
        float initial = distance(p, 450, 300);
        for (int i = 0; i < 30; i++) p.update(1 / 120F, 450, 300, 900, 600);
        float outbound = distance(p, 450, 300);
        require(outbound > initial + 40, "Centre cursor must not erase launch inertia");
        float peak = outbound;
        double winding = 0;
        float minRadius = Float.MAX_VALUE, maxRadius = 0;
        double previous = Math.atan2(p.y[0] - 300, p.x[0] - 450);
        for (int i = 0; i < 2400; i++) {
            p.update(1 / 120F, 450, 300, 900, 600);
            peak = Math.max(peak, distance(p, 450, 300));
            double angle = Math.atan2(p.y[0] - 300, p.x[0] - 450);
            double delta = Math.atan2(Math.sin(angle - previous), Math.cos(angle - previous));
            if (i > 1200) {
                winding += delta;
                float radius = (float) Math.hypot(p.x[0] - 450, p.y[0] - 300);
                minRadius = Math.min(minRadius, radius); maxRadius = Math.max(maxRadius, radius);
            }
            previous = angle;
        }
        require(distance(p, 450, 300) < peak * 0.8F, "Attraction must bring the burst back");
        require(Math.abs(winding) > Math.PI * 2, "Particles must keep orbiting");
        require(maxRadius - minRadius > 10, "Orbit radius must vary");
        WaitingParticles a = simulate(30), b = simulate(144);
        for (int i = 0; i < WaitingParticles.COUNT; i++) {
            require(Math.hypot(a.x[i] - b.x[i], a.y[i] - b.y[i]) < 0.1, "Motion must be frame-rate independent");
        }
        // Move the cursor to the other side and verify the cloud follows instead of staying
        // on a precomputed emitter-centred circle.
        for (int i = 0; i < 1800; i++) p.update(1 / 120F, 720, 220, 900, 600);
        require(distance(p, 720, 220) < distance(p, 450, 300), "Cloud must follow the new cursor");
        for (int i = 0; i < 18000; i++) {
            boolean resized = i >= 9000;
            float w = resized ? 600 : 900, h = resized ? 900 : 600;
            p.update(i % 240 == 0 ? 5 : 1 / 60F, i % 1200 < 600 ? 0 : w, h, w, h);
            for (int j = 0; j < WaitingParticles.COUNT; j++) {
                require(Float.isFinite(p.x[j]) && Float.isFinite(p.y[j]), "Position must stay finite");
                require(p.x[j] > -250 && p.x[j] < w + 250 && p.y[j] > -250 && p.y[j] < h + 250,
                        "Particles must remain bounded near viewport");
            }
        }
        System.out.println("PASS: outward inertia, capture, irregular orbit, moving cursor, 30/144 FPS, long stalls and resize");
        System.out.println("Mean launch distance=" + outbound + ", peak=" + peak + "; orbit radius range=" + minRadius + ".." + maxRadius);
    }

    private static WaitingParticles simulate(int fps) {
        WaitingParticles p = new WaitingParticles(); p.emit(450, 300, 900, 600);
        for (int i = 0; i < fps * 8; i++) p.update(1F / fps, 600, 200, 900, 600);
        return p;
    }
    private static float distance(WaitingParticles p, float x, float y) {
        float total = 0;
        for (int i = 0; i < WaitingParticles.COUNT; i++) total += Math.hypot(p.x[i] - x, p.y[i] - y);
        return total / WaitingParticles.COUNT;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
