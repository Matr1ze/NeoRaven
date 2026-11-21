package xyz.daikirai.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.EXTBlendMinmax;
import org.lwjgl.util.vector.Matrix4f;
import xyz.daikirai.gui.onyx.render.Shader;

/** Depth-clipped volumetric mist and independently depth-tested world motes. */
public final class FloatingFogRenderer {
    private static final int FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int READ_FRAMEBUFFER = 0x8CA8;
    private static final float CELL_XZ = 8.0f;
    private static final float CELL_Y = 4.5f;
    private static final int NOISE_PERIOD = 128;
    private static final double PARTICLE_CELL = 4.0;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final FloatBuffer matrices = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f camera = new Matrix4f();
    private Shader volume, composite, mote;
    private int depthTexture, fogTexture, fogTarget, width, height;
    private boolean failed;
    private World world;
    private long previousFrame;
    private double drift;

    public void reset() {
        failed = false;
        previousFrame = 0;
    }

    public void dispose() {
        if (fogTarget != 0) OpenGlHelper.glDeleteFramebuffers(fogTarget);
        if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
        if (fogTexture != 0) GL11.glDeleteTextures(fogTexture);
        fogTarget = depthTexture = fogTexture = width = height = 0;
        world = null;
        previousFrame = 0;
        drift = 0;
    }

    public void render(float partial, float density, float range, float speed, int color, boolean particles) {
        if (failed || !OpenGlHelper.shadersSupported || !OpenGlHelper.framebufferSupported) return;
        if (density <= 0 && !particles) return;
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int x = viewport.get(0), y = viewport.get(1), w = viewport.get(2), h = viewport.get(3);
        if (w < 2 || h < 2 || GL11.glGetInteger(GL11.GL_DEPTH_BITS) == 0) return;

        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) matrices.clear());
        projection.load(matrices);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) matrices.clear());
        view.load(matrices);
        if (Matrix4f.invert(view, camera) == null) return;
        Matrix4f.mul(projection, view, inverse);
        if (Matrix4f.invert(inverse, inverse) == null) return;

        RenderManager origin = mc.getRenderManager();
        Vec3 player = WorldRenderState.position(mc.thePlayer, partial);
        if (world != mc.theWorld) {
            world = mc.theWorld;
            previousFrame = 0;
            drift = 0;
        }
        long now = System.nanoTime();
        if (previousFrame != 0) drift += Math.min(.1, (now - previousFrame) * 1e-9) * speed;
        previousFrame = now;

        // Copying depth from a multisample framebuffer isn't supported by CopyTexSubImage2D.
        // World-space particles can still use the original multisampled depth buffer.
        boolean drawFog = density > 0 && GL11.glGetInteger(GL13.GL_SAMPLES) <= 1;
        int scene = GL11.glGetInteger(FRAMEBUFFER_BINDING);
        boolean splitFramebuffer = GLContext.getCapabilities().OpenGL30
                || GLContext.getCapabilities().GL_ARB_framebuffer_object;
        int read = splitFramebuffer ? GL11.glGetInteger(READ_FRAMEBUFFER_BINDING) : scene;
        try (WorldRenderState state = new WorldRenderState(true)) {
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glColorMask(true, true, true, true);
                if (GLContext.getCapabilities().OpenGL14) GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                else if (GLContext.getCapabilities().GL_EXT_blend_minmax) {
                    EXTBlendMinmax.glBlendEquationEXT(GL14.GL_FUNC_ADD);
                }
                if (drawFog) drawVolume(x, y, w, h, scene, origin, player, density, range, color);
                if (particles) drawMotes(origin, player, range, color);
            } finally {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene);
                if (splitFramebuffer && read != scene) OpenGlHelper.glBindFramebuffer(READ_FRAMEBUFFER, read);
                bind(1, texture1);
                bind(0, texture0);
            }
        } catch (RuntimeException error) {
            failed = true;
            dispose();
            System.err.println("[onyx] Floating Fog disabled after render failure: " + error);
        }
    }

    private void drawVolume(int x, int y, int w, int h, int scene, RenderManager origin, Vec3 player,
                            float density, float range, int color) {
        if (volume == null || !volume.valid() || composite == null || !composite.valid()) {
            volume = Shader.load("onyx_floating_fog");
            composite = Shader.load("onyx_floating_fog_composite", "onyx_floating_fog");
            if (!volume.valid() || !composite.valid()) throw new IllegalStateException("Floating Fog shader unavailable");
        }
        ensureTargets(w, h);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene);
        bind(0, depthTexture);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, x, y, w, h);

        int fogWidth = (w + 1) / 2, fogHeight = (h + 1) / 2;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fogTarget);
        GL11.glViewport(0, 0, fogWidth, fogHeight);
        GL11.glDisable(GL11.GL_BLEND);
        volume.use();
        volume.uniform1i("DepthSampler", 0);
        volume.uniform4f("uInverse0", inverse.m00, inverse.m01, inverse.m02, inverse.m03);
        volume.uniform4f("uInverse1", inverse.m10, inverse.m11, inverse.m12, inverse.m13);
        volume.uniform4f("uInverse2", inverse.m20, inverse.m21, inverse.m22, inverse.m23);
        volume.uniform4f("uInverse3", inverse.m30, inverse.m31, inverse.m32, inverse.m33);
        volume.uniform3f("uEye", camera.m30, camera.m31, camera.m32);
        volume.uniform3f("uPlayer", (float) (player.xCoord - origin.viewerPosX),
                (float) (player.yCoord + 1 - origin.viewerPosY), (float) (player.zCoord - origin.viewerPosZ));
        volume.uniform3f("uNoiseOrigin", noiseOrigin(origin.viewerPosX - drift * .78, CELL_XZ),
                noiseOrigin(origin.viewerPosY - drift * .16, CELL_Y),
                noiseOrigin(origin.viewerPosZ + drift * .36, CELL_XZ));
        volume.uniformColor("uColor", color);
        volume.uniform2f("uFog", density, range);
        quad();

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene);
        GL11.glViewport(x, y, w, h);
        bind(1, fogTexture);
        composite.use();
        composite.uniform1i("DepthSampler", 0);
        composite.uniform1i("FogSampler", 1);
        composite.uniform2f("uFogSize", fogWidth, fogHeight);
        composite.uniform2f("uProjection", projection.m22, projection.m32);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        quad();
    }

    private static float noiseOrigin(double position, float cellSize) {
        double cell = position / cellSize;
        return (float) (cell - NOISE_PERIOD * Math.floor(cell / NOISE_PERIOD));
    }

    private void drawMotes(RenderManager origin, Vec3 player, float range, int color) {
        if (mote == null || !mote.valid()) {
            mote = Shader.load("onyx_floating_fog_mote");
            if (!mote.valid()) throw new IllegalStateException("Floating Fog particle shader unavailable");
        }
        double span = Math.min(range * .66, 36);
        double windX = drift * 1.08, windY = drift * .24, windZ = -drift * .5;
        double eyeX = origin.viewerPosX + camera.m30, eyeY = origin.viewerPosY + camera.m31;
        double eyeZ = origin.viewerPosZ + camera.m32;
        int minX = cell(eyeX - windX - span), maxX = cell(eyeX - windX + span);
        int minY = cell(player.yCoord - windY - 13), maxY = cell(player.yCoord - windY + 16);
        int minZ = cell(eyeZ - windZ - span), maxZ = cell(eyeZ - windZ + span);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        mote.use();
        mote.uniformColor("uColor", color);
        GL11.glBegin(GL11.GL_QUADS);
        for (int cy = minY; cy <= maxY; cy++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                for (int cx = minX; cx <= maxX; cx++) {
                    long hash = seed(cx, cy, cz);
                    double phase = fraction(hash, 30) * (2 * Math.PI) + drift * .56;
                    double px = (cx + fraction(hash, 0)) * PARTICLE_CELL + windX + .55 * Math.sin(phase);
                    double py = (cy + fraction(hash, 10)) * PARTICLE_CELL + windY + .35 * Math.cos(phase);
                    double pz = (cz + fraction(hash, 20)) * PARTICLE_CELL + windZ + .55 * Math.cos(phase);
                    double dx = px - eyeX, dy = py - eyeY, dz = pz - eyeZ;
                    double forward = -(view.m02 * dx + view.m12 * dy + view.m22 * dz);
                    double distance2 = dx * dx + dy * dy + dz * dz;
                    if (forward < .55 || distance2 >= span * span) continue;
                    double side = view.m00 * dx + view.m10 * dy + view.m20 * dz;
                    double up = view.m01 * dx + view.m11 * dy + view.m21 * dz;
                    if (Math.abs(side * projection.m00) > forward + .24
                            || Math.abs(up * projection.m11) > forward + .24) continue;
                    double distance = Math.sqrt(distance2);
                    double fade = smooth((distance - .75) / 2.5) * smooth((span - distance) / 10)
                            * smooth((14 - Math.abs(py - player.yCoord - 1)) / 7);
                    float strength = (float) fraction(hash, 40);
                    float alpha = (float) (fade * (.36 + .55 * strength));
                    if (alpha < .01f) continue;
                    double size = .034 + .104 * strength * strength;
                    double tilt = phase * .38;
                    double a = Math.cos(tilt), b = Math.sin(tilt);
                    double rx = size * (camera.m00 * a + camera.m10 * b);
                    double ry = size * (camera.m01 * a + camera.m11 * b);
                    double rz = size * (camera.m02 * a + camera.m12 * b);
                    double ux = size * .60 * (camera.m10 * a - camera.m00 * b);
                    double uy = size * .60 * (camera.m11 * a - camera.m01 * b);
                    double uz = size * .60 * (camera.m12 * a - camera.m02 * b);
                    px -= origin.viewerPosX;
                    py -= origin.viewerPosY;
                    pz -= origin.viewerPosZ;
                    GL11.glColor4f(1, 1, 1, alpha);
                    vertex(0, 0, px - rx - ux, py - ry - uy, pz - rz - uz);
                    vertex(1, 0, px + rx - ux, py + ry - uy, pz + rz - uz);
                    vertex(1, 1, px + rx + ux, py + ry + uy, pz + rz + uz);
                    vertex(0, 1, px - rx + ux, py - ry + uy, pz - rz + uz);
                }
            }
        }
        GL11.glEnd();
    }

    private static int cell(double value) {
        return MathHelper.floor_double(value / PARTICLE_CELL);
    }

    private static long seed(int x, int y, int z) {
        long hash = x * 0x9e3779b97f4a7c15L ^ y * 0x632be59bd9b4e019L ^ z * 0x94d049bb133111ebL;
        hash = (hash ^ (hash >>> 30)) * 0xbf58476d1ce4e5b9L;
        hash = (hash ^ (hash >>> 27)) * 0x94d049bb133111ebL;
        return hash ^ (hash >>> 31);
    }

    private static double fraction(long hash, int shift) {
        return ((hash >>> shift) & 1023) / 1024.0;
    }

    private static double smooth(double x) {
        double t = MathHelper.clamp_double(x, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private void ensureTargets(int w, int h) {
        if (width == w && height == h && fogTarget != 0) return;
        if (fogTarget != 0) OpenGlHelper.glDeleteFramebuffers(fogTarget);
        if (depthTexture != 0) GL11.glDeleteTextures(depthTexture);
        if (fogTexture != 0) GL11.glDeleteTextures(fogTexture);
        depthTexture = texture(w, h, GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT);
        fogTexture = texture((w + 1) / 2, (h + 1) / 2, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        fogTarget = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fogTarget);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, fogTexture, 0);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Floating Fog framebuffer");
        }
        width = w;
        height = h;
    }

    private static int texture(int width, int height, int internal, int format, int type) {
        int id = GL11.glGenTextures();
        bind(0, id);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, width, height, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        return id;
    }

    // Restore the raw bindings before leaving WorldRenderState so the game cache stays intact.
    private static void bind(int unit, int texture) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void quad() {
        GL11.glBegin(GL11.GL_QUADS);
        vertex(0, 0, -1, -1, 0);
        vertex(1, 0, 1, -1, 0);
        vertex(1, 1, 1, 1, 0);
        vertex(0, 1, -1, 1, 0);
        GL11.glEnd();
    }

    private static void vertex(float u, float v, double x, double y, double z) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3d(x, y, z);
    }
}
