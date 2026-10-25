package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.render.SkyboxModule;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Custom sky dome, ported from OpenOnyx's {@code render.Util6}.
 *
 * <p>Onyx drops a triangle dome of radius 16 around the camera and paints it with the
 * {@code onyx_sky} shader, keyed on the vertex direction so the pattern is world locked, then lets
 * {@code RenderGlobal.renderSky} return early. This port does the same from a mixin at the head of
 * that method.</p>
 *
 * <p>Two things differ from the original, both for correctness on 1.8.9:</p>
 * <ul>
 *   <li>The camera translation is stripped from the modelview instead of inheriting it. At the
 *       head of {@code renderSky} the matrix already holds the camera transform, so a dome centred
 *       on the origin would sit at the world origin rather than around the camera; zeroing the
 *       translation keeps the rotation (the pattern stays world locked) and makes the dome
 *       follow the camera.</li>
 *   <li>Every GL and FBO entry point is wrapped so a driver that refuses the shader or the
 *       offscreen target disables the module instead of breaking the frame.</li>
 * </ul>
 */
public final class SkyboxRenderer {

    private static final float RADIUS = 16.0F;
    private static final int RINGS = 24;
    private static final int SEGMENTS = 48;
    /** GL_FRAMEBUFFER_BINDING. */
    private static final int FRAMEBUFFER_BINDING = 0x8CA6;

    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);

    private static Shader skyShader;
    private static Shader blitShader;
    private static boolean unavailable;
    private static int dome = -1;
    private static final long START = System.nanoTime();

    private static int framebuffer = -1;
    private static int texture = -1;
    private static int width;
    private static int height;

    /** Frames the custom sky was actually drawn in (used by the dev harness). */
    private static int frames;

    private SkyboxRenderer() {
    }

    public static int frames() {
        return frames;
    }

    public static void reset() {
        frames = 0;
    }

    public static boolean available() {
        return skyShader != null && skyShader.valid() && !unavailable;
    }

    private static float elapsed() {
        return (System.nanoTime() - START) / 1.0E9F;
    }

    /**
     * Draws the custom sky if the module asks for one.
     *
     * @return {@code true} when the vanilla sky must be skipped
     */
    public static boolean render(float partialTicks) {
        SkyboxModule module = SkyboxModule.instance;
        if (module == null || module.isEnabled() == false) {
            return false;
        }
        if (unavailable) {
            return false;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        // The Nether draws no sky of its own, so there is nothing to replace there.
        if (mc.theWorld.provider.getDimensionId() == -1) {
            return false;
        }
        if (skyShader == null) {
            skyShader = Shader.load("onyx_sky");
        }
        if (!skyShader.valid()) {
            unavailable = true;
            return false;
        }
        try {
            Framebuffer target = mc.getFramebuffer();
            float resolution = module.resolution();
            if (resolution < 0.999F && target != null
                    && OpenGlHelper.isFramebufferEnabled() && ensureTarget(target, resolution)) {
                renderScaled(module, target);
            } else {
                drawDome(module);
            }
            frames++;
            return true;
        } catch (Throwable t) {
            unavailable = true;
            release();
            System.err.println("[onyx] skybox unavailable: " + t);
            return false;
        }
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private static void drawDome(SkyboxModule module) {
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);

        GL11.glPushMatrix();
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        MODELVIEW.rewind();
        MODELVIEW.put(12, 0.0F).put(13, 0.0F).put(14, 0.0F);
        MODELVIEW.rewind();
        GL11.glLoadMatrix(MODELVIEW);

        int color = module.color();
        skyShader.use();
        skyShader.uniform4f("uColorModulator",
                (color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F,
                elapsed());
        skyShader.uniform3f("uParams", module.octaves(), module.preset(), module.animationSpeed());
        skyShader.uniform4f("uKnobs", module.scale(), module.intensity(),
                module.strikeInterval(), module.strikeChance());
        skyShader.uniform1f("uStrikeGlow", module.strikeGlow());
        skyShader.uniform4f("uFogColor", 0.0F, 0.0F, 0.0F, 0.0F);

        if (dome == -1) {
            buildDome();
        }
        GL11.glCallList(dome);

        Shader.unbind();
        GL11.glPopMatrix();

        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.enableFog();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.resetColor();
    }

    private static void buildDome() {
        dome = net.minecraft.client.renderer.GLAllocation.generateDisplayLists(1);
        GL11.glNewList(dome, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int ring = 0; ring < RINGS; ring++) {
            float theta0 = (float) (Math.PI * ring / RINGS - Math.PI / 2.0);
            float theta1 = (float) (Math.PI * (ring + 1) / RINGS - Math.PI / 2.0);
            float y0 = (float) Math.sin(theta0);
            float r0 = (float) Math.cos(theta0);
            float y1 = (float) Math.sin(theta1);
            float r1 = (float) Math.cos(theta1);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float phi0 = (float) (Math.PI * 2.0 * segment / SEGMENTS);
                float phi1 = (float) (Math.PI * 2.0 * (segment + 1) / SEGMENTS);
                float c0 = (float) Math.cos(phi0);
                float s0 = (float) Math.sin(phi0);
                float c1 = (float) Math.cos(phi1);
                float s1 = (float) Math.sin(phi1);

                vertex(r0 * c0, y0, r0 * s0);
                vertex(r1 * c0, y1, r1 * s0);
                vertex(r1 * c1, y1, r1 * s1);

                vertex(r0 * c0, y0, r0 * s0);
                vertex(r1 * c1, y1, r1 * s1);
                vertex(r0 * c1, y0, r0 * s1);
            }
        }
        GL11.glEnd();
        GL11.glEndList();
    }

    private static void vertex(float x, float y, float z) {
        GL11.glVertex3f(x * RADIUS, y * RADIUS, z * RADIUS);
    }

    // ------------------------------------------------------------------ //
    // Quarter-resolution path
    // ------------------------------------------------------------------ //

    private static void renderScaled(SkyboxModule module, Framebuffer target) {
        int previous = GL11.glGetInteger(FRAMEBUFFER_BINDING);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        GlStateManager.viewport(0, 0, width, height);
        drawDome(module);

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target.framebufferObject);
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);
        blitSky();

        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        GlStateManager.viewport(0, 0, target.framebufferWidth, target.framebufferHeight);
    }

    private static void blitSky() {
        if (blitShader == null) {
            blitShader = Shader.load("onyx_hand_blit", "onyx_post");
        }
        if (!blitShader.valid()) {
            return;
        }
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(texture);

        blitShader.use();
        blitShader.uniform1i("Sampler0", 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(0.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(1.0F, 0.0F);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(1.0F, 1.0F);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(0.0F, 1.0F);
        GL11.glEnd();
        Shader.unbind();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.resetColor();
    }

    // ------------------------------------------------------------------ //
    // Offscreen target
    // ------------------------------------------------------------------ //

    private static boolean ensureTarget(Framebuffer target, float resolution) {
        int targetWidth = Math.max(1, Math.round(target.framebufferWidth * resolution));
        int targetHeight = Math.max(1, Math.round(target.framebufferHeight * resolution));
        if (framebuffer != -1 && width == targetWidth && height == targetHeight) {
            return true;
        }
        release();

        int previous = GL11.glGetInteger(FRAMEBUFFER_BINDING);
        try {
            texture = TextureUtil.glGenTextures();
            GlStateManager.bindTexture(texture);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, targetWidth, targetHeight, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);

            int handle = OpenGlHelper.glGenFramebuffers();
            if (handle == -1) {
                release();
                return false;
            }
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, handle);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,
                    OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
            int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
            if (status != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
                OpenGlHelper.glDeleteFramebuffers(handle);
                release();
                return false;
            }
            framebuffer = handle;
            width = targetWidth;
            height = targetHeight;
            return true;
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
        }
    }

    private static void release() {
        if (framebuffer != -1) {
            OpenGlHelper.glDeleteFramebuffers(framebuffer);
            framebuffer = -1;
        }
        if (texture != -1) {
            TextureUtil.deleteTexture(texture);
            texture = -1;
        }
        width = 0;
        height = 0;
    }
}
