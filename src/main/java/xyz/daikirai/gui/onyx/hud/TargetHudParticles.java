package xyz.daikirai.gui.onyx.hud;

import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Hit particles for the TargetHUD avatar — a port of OpenRise's
 * {@code util.render.particle.Particle} and the emitter in {@code CreidaTargetInfo}.
 *
 * <p>The numbers that decide how it looks are Rise's own:</p>
 *
 * <ul>
 *   <li>a dot is {@code 2 + rand * 3} pixels across (2–5 px);</li>
 *   <li>its colour is the theme's two accents mixed at random, with a <em>random starting
 *   alpha</em> ({@code rand * 255}) — that is why a few dots read bright and the rest read as
 *   faint dust;</li>
 *   <li>the dot starts inside the avatar and is thrown outward;</li>
 *   <li>alpha falls by {@code 1 / 18} per millisecond and anything older than 3000 ms is dropped,
 *   which is what turns repeated hits into a cloud that slowly dissolves instead of a blip;</li>
 *   <li>one hit throws its whole dot count in a single frame (see {@code TargetHud}).</li>
 * </ul>
 *
 * <p>The one number that is <em>not</em> Rise's is the motion curve — see the notes on
 * {@link #FAST_TAU} and {@link #SLOW_TAU}.</p>
 *
 * <p>The look is Rise's. Rise drew the dot as a plain hard circle, then drew it <em>again</em>
 * with {@code alpha * 3} into the {@code BLOOM} layer, which blurred it with a 14 px gaussian and
 * composited additively, so overlapping dots add up and bleach towards white. There is no
 * framebuffer here, so the glow is a vertex-coloured fan whose alpha ramps to exactly zero at the
 * rim — drawn additively, it has no visible edge. It deliberately does <em>not</em> use a texture:
 * a runtime-generated {@code DynamicTexture} came out as an opaque square in game, which was the
 * "big block" artefact. Geometry cannot fail that way.</p>
 */
public final class TargetHudParticles {

    /** {@code ParticleComponent.render}: removed once {@code time > 50 * 3 * 20} ms. */
    private static final float MAX_AGE = 3000.0F;
    /** {@code alpha -= elapsed / 18} per millisecond. */
    private static final float FADE_PER_MS = 1.0F / 18.0F;
    /** Rise had no cap; three seconds of life and dense hits need a ceiling. */
    private static final int MAX = 260;

    /*
     * The burst curve.
     *
     * Rise damped with a flat 0.999 per millisecond, which is a ~1000 ms time constant: the dot
     * keeps ~37% of its speed a full second after the hit, so it reads as a slow even glide rather
     * than the "pumped out" pop we are after. The motion wanted here has three beats — shoved out
     * hard, nearly stopped, then drifting while it fades — which one exponential cannot express.
     *
     * Two exponentials can. A dominant fast term carries the punch, and a weak slow term keeps the
     * dot creeping outward for the rest of its life instead of freezing. Both are closed-form in
     * {@link #update}, so the shape is frame-rate independent.
     */
    /** Launch speed range in GUI pixels per millisecond, before the user's force multiplier. */
    private static final float SPEED_MIN = 0.18F;
    private static final float SPEED_MAX = 0.40F;
    /** How the launch speed splits between the fast and the slow term. */
    private static final float FAST_SHARE = 0.86F;
    private static final float SLOW_SHARE = 0.14F;
    /** Decay constants: ~95 ms is the shove, ~780 ms is the drift that follows it. */
    private static final float FAST_TAU = 95.0F;
    private static final float SLOW_TAU = 780.0F;

    /**
     * Halo radius = {@code size * GLOW_SCALE + GLOW_PAD}. Both numbers are small on purpose:
     * Rise's bloom was a wide 14 px gaussian over a 2–5 px dot, which spreads the dot's energy
     * thin and reads as haze <em>around</em> a crisp speck. A halo that is a big fraction of the
     * dot instead turns every dot into a fuzzy ball, which is what the first version did.
     */
    private static final float GLOW_SCALE = 1.15F;
    private static final float GLOW_PAD = 2.2F;
    /** Peak halo alpha as a fraction of the dot's own alpha. Kept low so overlaps stay subtle. */
    private static final float GLOW_STRENGTH = 0.40F;
    /** Triangles in the glow fan. Small dots do not need more, and it keeps the draw cheap. */
    private static final int SEGMENTS = 14;

    private final List<Particle> particles = new ArrayList<Particle>();
    private final Random random = new Random();

    /**
     * Spawns {@code count} dots at the avatar, exactly like Rise's {@code Particle(position,
     * velocity)} constructor: every dot starts at the same point, so all of the spread comes from
     * the velocity.
     *
     * <p>{@code force} scales the launch speed (the {@code Burst force} setting). Direction is
     * uniform in angle rather than uniform per axis: independent x/y draws cluster on the
     * diagonals and pile dots up near the centre, which kills the "thrown outward" read.</p>
     */
    public void burst(float x, float y, int count, float force, int accent, int accentAlt) {
        for (int i = 0; i < count && particles.size() < MAX; i++) {
            Particle particle = new Particle();
            particle.x = x;
            particle.y = y;
            float angle = random.nextFloat() * (float) (Math.PI * 2.0D);
            float speed = (SPEED_MIN + random.nextFloat() * (SPEED_MAX - SPEED_MIN)) * force;
            float cos = (float) Math.cos(angle) * speed;
            float sin = (float) Math.sin(angle) * speed;
            particle.vxFast = cos * FAST_SHARE;
            particle.vyFast = sin * FAST_SHARE;
            particle.vxSlow = cos * SLOW_SHARE;
            particle.vySlow = sin * SLOW_SHARE;
            particle.size = 2.0F + random.nextFloat() * 2.4F;
            particle.color = TargetHud.mixSrgb(accent, accentAlt, random.nextFloat());
            // Rise started every dot at a random alpha in 0..255. The floor keeps the dim end of
            // that spread from being invisible dust, which made the burst look sparse.
            particle.alpha = 40.0F + (float) (random.nextFloat() * 215.0F);
            particles.add(particle);
        }
    }

    /**
     * One step over {@code deltaMs} milliseconds.
     *
     * <p>Each term integrates to {@code v * tau * (1 - exp(-t / tau))}: the velocity decays
     * exponentially, so that closed form is the exact distance travelled and does not care
     * whether the frame took 5 ms or 40 ms. Stepping the raw velocity once per frame would make
     * the burst travel further on slow frames and flatten the curve into a slide.</p>
     */
    public void update(float deltaMs) {
        if (particles.isEmpty()) {
            return;
        }
        float ms = Material.clamp(deltaMs, 0.0F, 100.0F);
        float fastDecay = (float) Math.exp(-ms / FAST_TAU);
        float slowDecay = (float) Math.exp(-ms / SLOW_TAU);
        float fastTravel = FAST_TAU * (1.0F - fastDecay);
        float slowTravel = SLOW_TAU * (1.0F - slowDecay);
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.x += particle.vxFast * fastTravel + particle.vxSlow * slowTravel;
            particle.y += particle.vyFast * fastTravel + particle.vySlow * slowTravel;
            particle.vxFast *= fastDecay;
            particle.vyFast *= fastDecay;
            particle.vxSlow *= slowDecay;
            particle.vySlow *= slowDecay;
            particle.alpha -= ms * FADE_PER_MS;
            particle.age += ms;
            if (particle.alpha <= 0.0F || particle.age >= MAX_AGE) {
                iterator.remove();
            }
        }
    }

    /**
     * Draws the dots first, then the additive halo pass on top — the order Rise composited its
     * bloom in. Drawing the halo first put a soft disc under a small dot and made every dot read
     * as a blurred ball; over the dot, the halo only bleaches its centre the way real bloom does.
     *
     * <p>The circle pass uses UI's alpha stack directly. The additive pass reads the same
     * combined alpha for its raw GL colors.</p>
     */
    public void render(UI ui) {
        float alphaScale = ui.alpha();
        if (particles.isEmpty() || alphaScale <= 0.0F) {
            return;
        }
        for (Particle particle : particles) {
            float alpha = Material.clamp(particle.alpha / 255.0F, 0.0F, 1.0F);
            if (alpha <= 0.0F) {
                continue;
            }
            ui.circle(particle.x + particle.size / 2.0F, particle.y + particle.size / 2.0F,
                    particle.size / 2.0F, Material.withAlpha(particle.color, alpha));
        }

        beginGlow();
        try {
            for (Particle particle : particles) {
                float alpha = Material.clamp(particle.alpha / 255.0F, 0.0F, 1.0F) * alphaScale;
                if (alpha > 0.0F) {
                    glow(particle.x + particle.size / 2.0F, particle.y + particle.size / 2.0F,
                            particle.size * GLOW_SCALE + GLOW_PAD, particle.color, alpha * GLOW_STRENGTH);
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    public void clear() {
        particles.clear();
    }

    /** Soft additive flush drawn over the avatar. */
    public static void halo(float centerX, float centerY, float radius, int color, float alpha) {
        if (radius <= 0.0F || alpha <= 0.0F) return;
        beginGlow();
        try {
            glow(centerX, centerY, radius, color, alpha);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static void beginGlow() {
        // Restore the caller's actual GL state without writing through GlStateManager's cache.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
    }

    /**
     * One soft dot: a small hot centre, then a two-band skirt that falls off faster than linear at
     * the rim. Three rings is enough to read as a gaussian at this size, and the last band reaches
     * exactly zero, so the halo can never show an edge (which would look like the "translucent
     * circle around a ball" artefact).
     */
    private static void glow(float centerX, float centerY, float radius, int color, float alpha) {
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float inner = radius * 0.30F;
        float middle = radius * 0.66F;
        float innerAlpha = alpha * 0.62F;
        float middleAlpha = alpha * 0.20F;

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glColor4f(r, g, b, alpha);
        GL11.glVertex2f(centerX, centerY);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / SEGMENTS;
            GL11.glColor4f(r, g, b, innerAlpha);
            GL11.glVertex2f(centerX + (float) Math.cos(angle) * inner,
                    centerY + (float) Math.sin(angle) * inner);
        }
        GL11.glEnd();

        ring(centerX, centerY, inner, middle, innerAlpha, middleAlpha, r, g, b);
        ring(centerX, centerY, middle, radius, middleAlpha, 0.0F, r, g, b);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** One band of the halo: {@code from} ring down to the alpha at the {@code to} ring. */
    private static void ring(float centerX, float centerY, float from, float to,
                             float fromAlpha, float toAlpha, float r, float g, float b) {
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= SEGMENTS; i++) {
            double angle = Math.PI * 2.0D * i / SEGMENTS;
            float cs = (float) Math.cos(angle);
            float sn = (float) Math.sin(angle);
            GL11.glColor4f(r, g, b, fromAlpha);
            GL11.glVertex2f(centerX + cs * from, centerY + sn * from);
            GL11.glColor4f(r, g, b, toAlpha);
            GL11.glVertex2f(centerX + cs * to, centerY + sn * to);
        }
        GL11.glEnd();
    }

    private static final class Particle {
        private float x;
        private float y;
        /** Velocity split into the shove (fast) and the drift (slow), in pixels per ms. */
        private float vxFast;
        private float vyFast;
        private float vxSlow;
        private float vySlow;
        /** Dot diameter in GUI pixels (2–5). */
        private float size;
        /** 0–255, starts at a random value and drops by {@code 1 / 18} per millisecond. */
        private float alpha;
        private float age;
        private int color;
    }
}
