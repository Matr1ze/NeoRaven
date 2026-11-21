package xyz.daikirai.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.Chunk;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.vector.Matrix4f;
import xyz.daikirai.gui.onyx.render.Shader;

/** Traces the visible scene at half resolution and composites onto depth-matched wet terrain. */
public final class RainReflectionRenderer {
    private static final int EXPOSURE_SIZE = 112;
    private static final int EXPOSURE_REFRESH_TICKS = 10;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f camera = new Matrix4f();
    private final ByteBuffer exposure = BufferUtils.createByteBuffer(EXPOSURE_SIZE * EXPOSURE_SIZE * 2);
    private final BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
    private World exposureWorld;
    private long exposureTick;
    private int exposureX, exposureZ;
    private boolean exposureDirty, hasWetColumns;

    private Shader trace, composite;
    private int depth, wetColumns, reflection, reflectionTarget, width, height;
    private boolean failed;

    public void reset() {
        failed = false;
    }

    public void dispose() {
        disposeTargets();
        exposureWorld = null;
    }

    public void render(float partialTicks, float rain) {
        if (failed || mc.gameSettings.anaglyph || !OpenGlHelper.isFramebufferEnabled()
                || !GLContext.getCapabilities().OpenGL20) return;
        Framebuffer scene = mc.getFramebuffer();
        if (scene == null || scene.framebufferTexture <= 0 || scene.framebufferObject <= 0
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != scene.framebufferObject
                || GL11.glGetInteger(GL13.GL_SAMPLES) > 1 || GL11.glGetInteger(GL11.GL_DEPTH_BITS) == 0) return;
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        int w = viewport.get(2), h = viewport.get(3);
        if (viewport.get(0) != 0 || viewport.get(1) != 0 || w < 2 || h < 2
                || w != scene.framebufferTextureWidth || h != scene.framebufferTextureHeight) return;

        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) matrix.clear());
        projection.load(matrix);
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) matrix.clear());
        view.load(matrix);
        if (Matrix4f.invert(view, camera) == null) return;

        RenderManager origin = mc.getRenderManager();
        double eyeX = origin.viewerPosX + camera.m30;
        double eyeZ = origin.viewerPosZ + camera.m32;
        updateExposure(mc.theWorld, eyeX, eyeZ);
        if (!hasWetColumns) return;

        boolean splitFramebuffer = GLContext.getCapabilities().OpenGL30
                || GLContext.getCapabilities().GL_ARB_framebuffer_object;
        int previousRead = splitFramebuffer ? GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING) : scene.framebufferObject;
        try (WorldRenderState state = new WorldRenderState(true)) {
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + 2);
            int texture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            try {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glDisable(GL11.GL_STENCIL_TEST);
                GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
                GL11.glColorMask(true, true, true, true);
                GL14.glBlendEquation(GL14.GL_FUNC_ADD);
                draw(scene, w, h, rain, partialTicks, eyeX, origin.viewerPosY + camera.m31, eyeZ);
            } finally {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene.framebufferObject);
                if (splitFramebuffer && previousRead != scene.framebufferObject) {
                    OpenGlHelper.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                }
                bind(2, texture2);
                bind(1, texture1);
                bind(0, texture0);
            }
        } catch (RuntimeException error) {
            failed = true;
            dispose();
            System.err.println("[onyx] Rain Reflections disabled after render failure: " + error);
        }
    }

    private void updateExposure(World world, double eyeX, double eyeZ) {
        int minX = (MathHelper.floor_double(eyeX) & ~15) - 48;
        int minZ = (MathHelper.floor_double(eyeZ) & ~15) - 48;
        long tick = world.getTotalWorldTime();
        if (world == exposureWorld && minX == exposureX && minZ == exposureZ
                && tick >= exposureTick && tick - exposureTick < EXPOSURE_REFRESH_TICKS) return;
        exposureWorld = world;
        exposureTick = tick;
        exposureX = minX;
        exposureZ = minZ;
        hasWetColumns = false;
        exposureDirty = true;
        WorldChunkManager biomes = world.getWorldChunkManager();
        for (int cz = 0; cz < EXPOSURE_SIZE; cz += 16) {
            for (int cx = 0; cx < EXPOSURE_SIZE; cx += 16) {
                Chunk chunk = world.getChunkFromChunkCoords((minX + cx) >> 4, (minZ + cz) >> 4);
                for (int z = cz; z < cz + 16; z++) {
                    for (int x = cx; x < cx + 16; x++) {
                        int offset = (z * EXPOSURE_SIZE + x) * 2;
                        int ground = chunk.isEmpty() ? -1 : exposedGround(chunk, biomes, minX + x, minZ + z);
                        exposure.put(offset, (byte) Math.max(0, ground));
                        exposure.put(offset + 1, (byte) (ground < 0 ? 0 : 255));
                        hasWetColumns |= ground >= 0;
                    }
                }
            }
        }
    }

    private int exposedGround(Chunk chunk, WorldChunkManager biomes, int x, int z) {
        column.set(x, 0, z);
        BiomeGenBase biome = chunk.getBiome(column, biomes);
        if (!biome.canRain()) return -1;
        int top = chunk.getPrecipitationHeight(column).getY();
        if (top < 1 || top > 256) return -1;
        column.set(x, top - 1, z);
        Material material = chunk.getBlock(column).getMaterial();
        if (!material.isSolid() || material.isLiquid() || material == Material.leaves
                || biomes.getTemperatureAtHeight(biome.getFloatTemperature(column), top) < .15F) return -1;
        return top - 1;
    }

    private void draw(Framebuffer scene, int w, int h, float rain, float partial,
                      double eyeX, double eyeY, double eyeZ) {
        if (trace == null) trace = Shader.load("onyx_rain_reflection", "onyx_floating_fog");
        if (composite == null) composite = Shader.load("onyx_rain_reflection_composite", "onyx_floating_fog");
        if (!trace.valid() || !composite.valid()) throw new IllegalStateException("Rain Reflections shader unavailable");
        ensureTargets(w, h);
        if (exposureDirty) {
            bind(2, wetColumns);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, EXPOSURE_SIZE, EXPOSURE_SIZE,
                    GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE, exposure);
            exposureDirty = false;
        }

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene.framebufferObject);
        bind(0, depth);
        GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
        int halfW = (w + 1) / 2, halfH = (h + 1) / 2;
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, reflectionTarget);
        GL11.glViewport(0, 0, halfW, halfH);
        GL11.glDisable(GL11.GL_BLEND);
        bind(1, scene.framebufferTexture);
        bind(2, wetColumns);
        trace.use();
        trace.uniform1i("DepthSampler", 0);
        trace.uniform1i("SceneSampler", 1);
        trace.uniform1i("ExposureSampler", 2);
        trace.uniform2f("uPixel", 1.0F / w, 1.0F / h);
        trace.uniform2f("uDepthProjection", projection.m22, projection.m32);
        trace.uniform4f("uFrustum", projection.m00, projection.m11, projection.m20, projection.m21);
        trace.uniform3f("uWorldX", camera.m00, camera.m10, camera.m20);
        trace.uniform3f("uWorldY", camera.m01, camera.m11, camera.m21);
        trace.uniform3f("uWorldZ", camera.m02, camera.m12, camera.m22);
        trace.uniform4f("uExposure", (float) (eyeX - exposureX), (float) eyeY, (float) (eyeZ - exposureZ), EXPOSURE_SIZE);
        trace.uniform2f("uNoiseOrigin", wrap(eyeX), wrap(eyeZ));
        Vec3 sky = mc.theWorld.getSkyColor(mc.getRenderViewEntity(), partial);
        trace.uniform3f("uSky", (float) sky.xCoord, (float) sky.yCoord, (float) sky.zCoord);
        trace.uniform2f("uRain", rain, ((mc.theWorld.getTotalWorldTime() % 1200) + partial) / 1200.0F);
        quad();

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene.framebufferObject);
        GL11.glViewport(0, 0, w, h);
        bind(1, reflection);
        composite.use();
        composite.uniform1i("DepthSampler", 0);
        composite.uniform1i("ReflectionSampler", 1);
        composite.uniform2f("uDepthProjection", projection.m22, projection.m32);
        composite.uniform2f("uPixel", 1.0F / w, 1.0F / h);
        composite.uniform2f("uReflectionSize", halfW, halfH);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
        quad();
    }

    private void ensureTargets(int w, int h) {
        if (width == w && height == h && reflectionTarget > 0) return;
        disposeTargets();
        depth = texture(w, h, GL14.GL_DEPTH_COMPONENT24, GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT);
        wetColumns = texture(EXPOSURE_SIZE, EXPOSURE_SIZE, GL11.GL_LUMINANCE8_ALPHA8,
                GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE);
        exposureDirty = true;
        reflection = texture((w + 1) / 2, (h + 1) / 2, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
        reflectionTarget = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, reflectionTarget);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, reflection, 0);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Incomplete Rain Reflections framebuffer");
        }
        width = w;
        height = h;
    }

    private void disposeTargets() {
        if (reflectionTarget > 0) OpenGlHelper.glDeleteFramebuffers(reflectionTarget);
        if (reflection > 0) GL11.glDeleteTextures(reflection);
        if (depth > 0) GL11.glDeleteTextures(depth);
        if (wetColumns > 0) GL11.glDeleteTextures(wetColumns);
        reflectionTarget = reflection = depth = wetColumns = width = height = 0;
    }

    private static int texture(int w, int h, int internal, int format, int type) {
        int texture = GL11.glGenTextures();
        bind(0, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static void bind(int unit, int texture) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static float wrap(double position) {
        return (float) (position - Math.floor(position / 1024.0) * 1024.0);
    }

    private static void quad() {
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(2, 0); GL11.glVertex2f(3, -1);
        GL11.glTexCoord2f(0, 2); GL11.glVertex2f(-1, 3);
        GL11.glEnd();
    }
}
