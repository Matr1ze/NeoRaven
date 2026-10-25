package xyz.daikirai.render;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.render.FogBlurModule;

/** Onyx's half/quarter-resolution blur, composited using the scene's depth. */
public final class FogBlurRenderer {
    private final Minecraft mc = Minecraft.getMinecraft();
    private Shader down, up, composite;
    private int half = -1, quarter = -1, depth = -1, halfFbo = -1, quarterFbo = -1;
    private int width, height;
    private boolean failed;

    public void reset() { failed = false; }

    public void dispose() {
        if (halfFbo != -1) OpenGlHelper.glDeleteFramebuffers(halfFbo);
        if (quarterFbo != -1) OpenGlHelper.glDeleteFramebuffers(quarterFbo);
        for (int texture : new int[]{half, quarter, depth}) if (texture != -1) GL11.glDeleteTextures(texture);
        half = quarter = depth = halfFbo = quarterFbo = -1;
        width = height = 0;
    }

    public void render(FogBlurModule settings) {
        Framebuffer scene = mc.getFramebuffer();
        if (failed || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled() || scene == null) return;
        int previousFbo = GL11.glGetInteger(0x8CA6);
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        try (WorldRenderState state = new WorldRenderState(true)) {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            try {
                if (down == null) {
                    down = Shader.load("onyx_fog_down");
                    up = Shader.load("onyx_fog_up");
                    composite = Shader.load("onyx_fog_composite");
                }
                if (!down.valid() || !up.valid() || !composite.valid()) { failed = true; return; }
                ensure(scene.framebufferWidth, scene.framebufferHeight);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFbo);
                bind(0, depth);
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
                GL11.glLoadIdentity();
                GL11.glOrtho(0, 1, 0, 1, -1, 1);
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glLoadIdentity();
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                int hw = Math.max(1, width / 2), hh = Math.max(1, height / 2);
                int qw = Math.max(1, hw / 2), qh = Math.max(1, hh / 2);
                pass(down, scene.framebufferTexture, halfFbo, hw, hh, width, height);
                pass(down, half, quarterFbo, qw, qh, hw, hh);
                pass(up, quarter, halfFbo, hw, hh, qw, qh);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFbo);
                GL11.glViewport(0, 0, width, height);
                composite.use();
                composite.uniform1i("BlurSampler", 1);
                composite.uniform1i("DepthSampler", 2);
                composite.uniform4f("uCamera", .05F, Math.max(1.05F, mc.gameSettings.renderDistanceChunks * 64F), 1, 0);
                composite.uniform4f("uFog", (float) settings.distance.getInput(), (float) settings.fade.getInput(),
                        (float) settings.tint.getInput(), settings.clientColor.isToggled() ? 1 : 0);
                composite.uniformColor("uColor1", Theme.scheme().primary);
                composite.uniformColor("uColor2", Theme.scheme().primary);
                composite.uniformColor("uColor3", Theme.scheme().tertiary);
                composite.uniformColor("uColor4", Theme.scheme().tertiary);
                bind(1, half);
                bind(2, depth);
                OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                quad();
            } finally {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFbo);
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
                GL11.glMatrixMode(matrix);
            }
        } catch (RuntimeException error) {
            failed = true;
            dispose();
            System.err.println("[onyx] Fog Blur disabled after render failure: " + error);
        }
    }

    private void ensure(int w, int h) {
        if (width == w && height == h && half != -1) return;
        dispose();
        half = texture(Math.max(1, w / 2), Math.max(1, h / 2), false);
        quarter = texture(Math.max(1, w / 4), Math.max(1, h / 4), false);
        depth = texture(w, h, true);
        halfFbo = framebuffer(half);
        quarterFbo = framebuffer(quarter);
        width = w;
        height = h;
    }

    private static int texture(int width, int height, boolean depth) {
        int texture = GL11.glGenTextures();
        bind(0, texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, depth ? 33190 : 32856, width, height, 0,
                depth ? GL11.GL_DEPTH_COMPONENT : GL11.GL_RGBA, depth ? GL11.GL_FLOAT : GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        return texture;
    }

    private static int framebuffer(int texture) {
        int fbo = OpenGlHelper.glGenFramebuffers();
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, texture, 0);
        if (OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER) != OpenGlHelper.GL_FRAMEBUFFER_COMPLETE) {
            OpenGlHelper.glDeleteFramebuffers(fbo);
            throw new IllegalStateException("Incomplete fog framebuffer");
        }
        return fbo;
    }

    private static void bind(int unit, int texture) {
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static void pass(Shader shader, int texture, int fbo, int w, int h, int sourceW, int sourceH) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, w, h);
        bind(0, texture);
        shader.use();
        shader.uniform1i("Sampler0", 0);
        shader.uniform2f("uSourceSize", sourceW, sourceH);
        quad();
    }

    private static void quad() {
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }
}
