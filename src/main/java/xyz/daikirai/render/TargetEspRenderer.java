package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;

/**
 * Onyx's TargetESP crystal pass ({@code render.blur.Cls4}): eighteen octahedral shards orbit the
 * target on a slowly turning ring, each one shaded per face and drawn additively, with a bloom
 * sprite behind every shard and a short lunge towards the target whenever it gets hit.
 *
 * <p>The original wrote the shards through {@code Tessellator}/{@code WorldRenderer} with
 * {@code POSITION_COLOR} and billboarded the halos against {@link ActiveRenderInfo}'s camera basis.
 * This port keeps both ideas but draws immediate mode, so it neither depends on the tessellator's
 * translation nor on a vertex format. Everything is additive: the brightest face of a shard plus its
 * halo is what makes the crystal read as a lit gem instead of a flat box.</p>
 */
public final class TargetEspRenderer {

    /** Shards per target, the same 18 the original orbited. */
    private static final int SHARDS = 18;
    /** Degrees between two neighbouring shards; also drives the height pattern. */
    private static final float SHARD_STEP = 20.0F;
    /** Per-face brightness, straight out of the original's {@code FLOAT_ARRAY}. */
    private static final float[] FACE_SHADE = {1.0F, 0.8F, 0.6F, 0.9F, 0.7F, 0.5F, 0.4F, 0.6F};
    /** Octahedron: +Y/-Y poles and the four equator vertices. */
    private static final float[][] CORNER = {
            {0.0F, 1.5F, 0.0F}, {0.0F, -1.5F, 0.0F},
            {1.0F, 0.0F, 0.0F}, {-1.0F, 0.0F, 0.0F},
            {0.0F, 0.0F, 1.0F}, {0.0F, 0.0F, -1.0F}};
    /** Eight triangular faces, each named by its three corners. */
    private static final int[][] FACE = {
            {0, 4, 2}, {0, 3, 4}, {0, 5, 3}, {0, 2, 5},
            {1, 2, 4}, {1, 4, 3}, {1, 3, 5}, {1, 5, 2}};

    /** Duration of one strike wave and of the gap between two shards, in milliseconds. */
    private static final float STRIKE_LIFE = 260.0F;
    private static final float STRIKE_GAP = 80.0F;

    /** Points around one ring, and how many strips the trail curtain is built from. */
    private static final int RING_POINTS = 160;
    private static final int TRAIL_SEGMENTS = 24;
    /** Degrees of the sine scan the trail curtain spans (the original's 130 phase units). */
    private static final float TRAIL_SPAN = 130.0F;

    private static final ResourceLocation BLOOM =
            new ResourceLocation("onyx", "textures/effect/target_bloom.png");

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Per-shard strike timers: negative age = still waiting to start. */
    private final float[] strikeAge = new float[SHARDS];
    private final float[] strikeLife = new float[SHARDS];
    private int strikeCursor;
    private boolean bloomLoaded;
    private boolean failed;

    public void reset() {
        failed = false;
        java.util.Arrays.fill(strikeAge, 0.0F);
        java.util.Arrays.fill(strikeLife, 0.0F);
        strikeCursor = 0;
    }

    /** Advances the strike timers; {@code delta} is the frame time in milliseconds. */
    public void tick(float delta) {
        float step = Math.max(0.0F, delta);
        for (int i = 0; i < SHARDS; i++) {
            if (strikeLife[i] <= 0.0F) {
                continue;
            }
            strikeAge[i] += step;
            if (strikeAge[i] >= strikeLife[i]) {
                strikeLife[i] = 0.0F;
            }
        }
    }

    /** Starts a strike: {@code count} shards lunge inwards, one after another. */
    public void strike(int count, float speed) {
        float inverse = 1.0F / Math.max(speed, 0.1F);
        int shards = Math.max(1, Math.min(SHARDS, count));
        int gap = Math.max(1, SHARDS / shards);
        for (int i = 0; i < shards; i++) {
            int slot = freeSlot((strikeCursor + i * gap) % SHARDS);
            strikeAge[slot] = -i * STRIKE_GAP * inverse;
            strikeLife[slot] = STRIKE_LIFE * inverse;
        }
        strikeCursor = (strikeCursor + 7) % SHARDS;
    }

    private int freeSlot(int from) {
        for (int i = 0; i < SHARDS; i++) {
            int slot = (from + i) % SHARDS;
            if (strikeLife[slot] <= 0.0F) {
                return slot;
            }
        }
        return from;
    }

    /** 0 at rest, 1 at the middle of the lunge: fast rise, slower fall. */
    private float strike(int index) {
        if (strikeLife[index] <= 0.0F) {
            return 0.0F;
        }
        float progress = strikeAge[index] / strikeLife[index];
        if (progress <= 0.0F || progress >= 1.0F) {
            return 0.0F;
        }
        return progress < 0.3F ? smooth(progress / 0.3F) : 1.0F - smooth((progress - 0.3F) / 0.7F);
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    /**
     * Draws the {@code Ring} style: one hoop that scans up and down the target's body, with the
     * curtain of light it drags behind it. Ported from Onyx's {@code render.blur.Cls}.
     *
     * <p>The hoop's height is a sine of the phase, so the analytic history {@code phase - k} is also
     * the height it had {@code k} degrees ago: the trail needs no history buffer, it is the same
     * curve evaluated backwards, split into {@value #TRAIL_SEGMENTS} fading strips.</p>
     *
     * @param phase current scan phase in degrees (0 = mid body, -90 = feet, +90 = head)
     * @param size  hoop radius scale, 1 = the original's default
     * @param glow  halo strength, 1 = 100%
     */
    public void renderRing(EntityLivingBase target, float partial, float fade, float phase,
                           float size, float glow, int rgb) {
        if (failed || fade <= 0.0F) {
            return;
        }
        RenderManager manager = mc.getRenderManager();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial - manager.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial - manager.viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial - manager.viewerPosZ;
        float height = target.height;
        // (width * 0.86 + 0.18) is the original's hoop radius; it hugs the target's own body.
        float radius = (target.width * 0.86F + 0.18F) * size * (0.72F + 0.28F * fade);

        int activeTexture = GL11.glGetInteger(0x84E0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            trail(x, y, z, height, radius, phase, fade, glow, rgb);
            halo(x, y, z, height, phase, radius, fade, glow, rgb);
            hoop(x, y, z, height, phase, radius, fade, glow, rgb);
        } catch (Throwable error) {
            failed = true;
            System.err.println("[onyx] TargetESP ring disabled after a render failure: " + error);
        } finally {
            GlStateManager.bindTexture(texture);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    private void trail(double x, double y, double z, float height, float radius, float phase,
                       float fade, float glow, int rgb) {
        float strength = clamp(fade * (0.28F + 0.32F * glow));
        for (int segment = 0; segment < TRAIL_SEGMENTS; segment++) {
            float top = phase - TRAIL_SPAN * segment / TRAIL_SEGMENTS;
            float bottom = phase - TRAIL_SPAN * (segment + 1) / TRAIL_SEGMENTS;
            float fadeTop = fadeAt(segment);
            float fadeBottom = fadeAt(segment + 1);
            if (fadeTop <= 0.001F && fadeBottom <= 0.001F) {
                continue;
            }
            double topY = y + height * wave(top);
            double bottomY = y + height * wave(bottom);
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= RING_POINTS; i++) {
                double angle = Math.toRadians(i * 360.0D / RING_POINTS);
                double px = x + Math.cos(angle) * radius;
                double pz = z + Math.sin(angle) * radius;
                float ripple = ripple(i, phase);
                color(rgb, strength * fadeTop * ripple);
                GL11.glVertex3d(px, topY, pz);
                color(rgb, strength * fadeBottom * ripple);
                GL11.glVertex3d(px, bottomY, pz);
            }
            GL11.glEnd();
        }
    }

    /** Soft light around the hoop's own plane: two flat bands, the wide one much fainter. */
    private void halo(double x, double y, double z, float height, float phase, float radius,
                      float fade, float glow, int rgb) {
        double ringY = y + height * wave(phase);
        for (int layer = 0; layer < 2; layer++) {
            float inner = layer == 0 ? 0.94F : 0.74F;
            float outer = layer == 0 ? 1.06F : 1.26F;
            float alpha = clamp(fade * glow * (layer == 0 ? 0.16F : 0.07F));
            if (alpha <= 0.001F) {
                continue;
            }
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i <= RING_POINTS; i++) {
                double angle = Math.toRadians(i * 360.0D / RING_POINTS);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                color(rgb, alpha);
                GL11.glVertex3d(x + cos * radius * inner, ringY, z + sin * radius * inner);
                color(rgb, 0.0F);
                GL11.glVertex3d(x + cos * radius * outer, ringY, z + sin * radius * outer);
            }
            GL11.glEnd();
        }
    }

    /** The bright hoop itself, antialiased so the thin line does not crawl while it scans. */
    private void hoop(double x, double y, double z, float height, float phase, float radius,
                      float fade, float glow, int rgb) {
        double ringY = y + height * wave(phase);
        float alpha = clamp(fade * 0.92F);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(1.4F + glow);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= RING_POINTS; i++) {
            double angle = Math.toRadians(i * 360.0D / RING_POINTS);
            color(rgb, alpha * (0.72F + 0.28F * ripple(i, phase)));
            GL11.glVertex3d(x + Math.cos(angle) * radius, ringY, z + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    /** Sine of the scan phase, mapped to 0 at the feet and 1 at the head. */
    private static double wave(float phase) {
        return (Math.sin(Math.toRadians(phase)) + 1.0D) * 0.5D;
    }

    /** Curtain alpha along the trail: (1-t)^3 of the original, scaled to a 0..1 fade. */
    private static float fadeAt(int segment) {
        if (segment >= TRAIL_SEGMENTS) {
            return 0.0F;
        }
        float t = 1.0F - segment / (float) TRAIL_SEGMENTS;
        return t * t * t * 0.55F;
    }

    /** Per-angle shimmer, the original's two-sine brightness ripple around the hoop. */
    private static float ripple(int index, float phase) {
        double slow = Math.sin(Math.toRadians(index * 3.0D + phase * 1.3D));
        double fast = Math.sin(Math.toRadians(index * 7.0D - phase * 0.6D));
        return (float) (0.4D + 0.6D * (0.5D + 0.5D * slow) * (0.65D + 0.35D * fast));
    }

    private static void color(int rgb, float alpha) {
        GL11.glColor4f(channel(rgb, 16), channel(rgb, 8), channel(rgb, 0), clamp(alpha));
    }

    /**
     * Draws the shards and their halos.
     *
     * @param target      entity to mark, already resolved by the module
     * @param partial     render partial ticks
     * @param fade        0..1 fade in / out the module keeps per target
     * @param seconds     free running clock, drives the ring rotation and the aurora of the halos
     * @param radius      ring radius in blocks
     * @param crystal     crystal scale, 1 = the original's shard size
     * @param glow        halo strength, 1 = 100%
     * @param rgb         crystal colour (alpha ignored)
     * @param startOffset extra height for the whole ring, in blocks
     */
    public void render(EntityLivingBase target, float partial, float fade, double seconds,
                       float radius, float crystal, float glow, int rgb, double startOffset) {
        if (failed || fade <= 0.0F) {
            return;
        }
        RenderManager manager = mc.getRenderManager();
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partial - manager.viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partial - manager.viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partial - manager.viewerPosZ;
        double centreY = y + target.height * 0.5D;
        float height = target.height;

        int activeTexture = GL11.glGetInteger(0x84E0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // Everything below is raw GL inside the attribute scope, so Minecraft's cached state is
            // untouched; the lightmap unit has to go or fixed-function would darken the shards.
            OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            shards(x, y, z, centreY, height, seconds, radius, crystal, fade, rgb, startOffset);
            if (glow > 0.001F) {
                halos(x, y, z, centreY, height, seconds, radius, crystal, glow, fade, rgb, startOffset);
            }
        } catch (Throwable error) {
            failed = true;
            System.err.println("[onyx] TargetESP crystals disabled after a render failure: " + error);
        } finally {
            GlStateManager.bindTexture(texture);
            GL11.glPopAttrib();
            GlStateManager.setActiveTexture(activeTexture);
        }
    }

    private void shards(double x, double y, double z, double centreY, float height, double seconds,
                        float radius, float crystal, float fade, int rgb, double startOffset) {
        float spin = (float) (seconds * 120.0D);
        float alpha = fade * 0.92F;
        // Solid gems: normal alpha blending plus back-face culling, so each shard shows the half of
        // its eight faces that actually faces the camera and the per-face shade reads as a facet.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < SHARDS; i++) {
            float strike = strike(i);
            double angle = Math.toRadians(i * SHARD_STEP + spin);
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + startOffset + spread(i, height);
            // A struck shard drives in towards the middle of the target.
            double pull = strike * 0.55D;
            double cx = px + (x - px) * pull;
            double cy = py + (centreY - py) * pull;
            double cz = pz + (z - pz) * pull;

            float dx = (float) (x - px);
            float dy = (float) (centreY - py);
            float dz = (float) (z - pz);
            float length = Math.max(1.0E-4F, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
            float[] up = {dx / length, dy / length, dz / length};
            float[] right = perpendicular(up);
            float[] forward = cross(right, up);

            float scale = 0.1F * crystal * (1.0F + strike * 0.5F);
            float brightness = 1.0F + strike * 0.6F;
            int base = strike <= 0.0F ? rgb : blend(rgb, 0xFFFFFFFF, strike * 0.45F);
            for (int face = 0; face < FACE.length; face++) {
                float shade = FACE_SHADE[face] * brightness;
                GL11.glColor4f(Math.min(1.0F, channel(base, 16) * shade),
                        Math.min(1.0F, channel(base, 8) * shade),
                        Math.min(1.0F, channel(base, 0) * shade), alpha);
                int[] corners = FACE[face];
                for (int corner : corners) {
                    float[] local = CORNER[corner];
                    GL11.glVertex3d(cx + (right[0] * local[0] + up[0] * local[1] + forward[0] * local[2]) * scale,
                            cy + (right[1] * local[0] + up[1] * local[1] + forward[1] * local[2]) * scale,
                            cz + (right[2] * local[0] + up[2] * local[1] + forward[2] * local[2]) * scale);
                }
            }
        }
        GL11.glEnd();
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void halos(double x, double y, double z, double centreY, float height, double seconds,
                       float radius, float crystal, float glow, float fade, int rgb, double startOffset)
            throws IOException {
        bindBloom();
        // Halos stay additive and depth-free, like the original: they read as light thrown off the
        // shard rather than as geometry, and they must not be cut by the target's own body.
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        float rightX = ActiveRenderInfo.getRotationX();
        float upXZ = ActiveRenderInfo.getRotationXZ();
        float rightZ = ActiveRenderInfo.getRotationZ();
        float rightYZ = ActiveRenderInfo.getRotationYZ();
        float rightXY = ActiveRenderInfo.getRotationXY();
        float spin = (float) (seconds * 120.0D);
        float halo = clamp(fade * 0.16F * glow);
        int colour = rgb;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < SHARDS; i++) {
            float strike = strike(i);
            double angle = Math.toRadians(i * SHARD_STEP + spin);
            double px = x + Math.cos(angle) * radius;
            double pz = z + Math.sin(angle) * radius;
            double py = y + startOffset + spread(i, height);
            double pull = strike * 0.55D;
            double cx = px + (x - px) * pull;
            double cy = py + (centreY - py) * pull;
            double cz = pz + (z - pz) * pull;
            float grow = 1.0F + strike * 0.8F;
            float alpha = clamp(halo * (1.0F + strike * 0.7F));
            if (alpha <= 0.001F) {
                continue;
            }
            GL11.glColor4f(channel(colour, 16), channel(colour, 8), channel(colour, 0), alpha);
            quad(cx, cy, cz, 0.55F * crystal * grow, rightX, upXZ, rightZ, rightYZ, rightXY);
            GL11.glColor4f(channel(colour, 16), channel(colour, 8), channel(colour, 0), alpha * 0.6F);
            quad(cx, cy, cz, 0.24F * crystal * grow, rightX, upXZ, rightZ, rightYZ, rightXY);
        }
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    /**
     * One billboard quad. The four corners are the camera's right/up vectors scaled by the half size,
     * exactly the way the original laid its bloom quads out.
     */
    private static void quad(double x, double y, double z, float half,
                             float rightX, float upXZ, float rightZ, float rightYZ, float rightXY) {
        vertex(x - rightX * half - rightYZ * half, y - upXZ * half, z - rightZ * half - rightXY * half, 0.0F, 0.0F);
        vertex(x - rightX * half + rightYZ * half, y + upXZ * half, z - rightZ * half + rightXY * half, 0.0F, 1.0F);
        vertex(x + rightX * half + rightYZ * half, y + upXZ * half, z + rightZ * half + rightXY * half, 1.0F, 1.0F);
        vertex(x + rightX * half - rightYZ * half, y - upXZ * half, z + rightZ * half - rightXY * half, 1.0F, 0.0F);
    }

    private static void vertex(double x, double y, double z, float u, float v) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3d(x, y, z);
    }

    /** Height of shard {@code index} on the target, from the original's {@code 0.1 + h*|sin|} rule. */
    private static double spread(int index, float height) {
        return 0.1D + height * 0.95D * Math.abs(Math.sin(Math.toRadians(index * SHARD_STEP)));
    }

    private void bindBloom() throws IOException {
        if (!bloomLoaded) {
            // Forge's development resource pack only exposes the mod's own namespace, so the sprite
            // is read from the class path and uploaded as a dynamic texture like the jump circles.
            try (InputStream in = TargetEspRenderer.class
                    .getResourceAsStream("/assets/onyx/textures/effect/target_bloom.png")) {
                if (in == null) {
                    throw new IOException("Missing Onyx target bloom texture");
                }
                mc.getTextureManager().loadTexture(BLOOM, new DynamicTexture(ImageIO.read(in)));
            }
            bloomLoaded = true;
        }
        mc.getTextureManager().bindTexture(BLOOM);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
    }

    /** Any unit vector perpendicular to {@code up}, used as the shard's local X axis. */
    private static float[] perpendicular(float[] up) {
        float ax = Math.abs(up[1]) < 0.9F ? 0.0F : 1.0F;
        float ay = Math.abs(up[1]) < 0.9F ? 1.0F : 0.0F;
        float[] right = cross(up, new float[]{ax, ay, 0.0F});
        return normalize(right);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static float[] normalize(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length < 1.0E-4F) {
            return new float[]{1.0F, 0.0F, 0.0F};
        }
        return new float[]{v[0] / length, v[1] / length, v[2] / length};
    }

    private static float channel(int rgb, int shift) {
        return (rgb >> shift & 255) / 255.0F;
    }

    private static int blend(int from, int to, float amount) {
        float t = clamp(amount);
        int r = Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * t);
        int g = Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return r << 16 | g << 8 | b;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
