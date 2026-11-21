package xyz.daikirai.gui.onyx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.gui.onyx.render.UI;

/** Cached wallpaper textures for the notification shade and its glass cards. */
final class NotificationBackdrop {
    private static Shader blur;
    private Framebuffer background, horizontal, blurred;
    private boolean ready;
    private int updates;

    static void warmup() {
        if (blur == null) blur = Shader.load("box_blur");
    }

    boolean ready() { return ready; }

    boolean resize() {
        if (!Display.isVisible()) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (background != null && background.framebufferWidth == mc.displayWidth
                && background.framebufferHeight == mc.displayHeight) return false;
        dispose(); warmup();
        if (!OpenGlHelper.isFramebufferEnabled()) return true;
        background = target(mc.displayWidth, mc.displayHeight);
        horizontal = target(Math.max(1, mc.displayWidth / 2), Math.max(1, mc.displayHeight / 2));
        blurred = target(horizontal.framebufferWidth, horizontal.framebufferHeight);
        mc.getFramebuffer().bindFramebuffer(true);
        return true;
    }

    private Framebuffer target(int width, int height) {
        Framebuffer result = new Framebuffer(width, height, false);
        result.setFramebufferFilter(GL11.GL_LINEAR);
        GlStateManager.bindTexture(result.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GlStateManager.bindTexture(0);
        return result;
    }

    void capture(Runnable draw) {
        if (background == null || !Display.isVisible()) return;
        Minecraft mc = Minecraft.getMinecraft();
        try {
            background.bindFramebuffer(true);
            mc.entityRenderer.setupOverlayRendering();
            draw.run();
            if (blur.valid()) {
                pass(background, horizontal, 1, 0);
                pass(horizontal, blurred, 0, 1);
                for (int round = 0; round < 2; round++) {
                    pass(blurred, horizontal, 1, 0);
                    pass(horizontal, blurred, 0, 1);
                }
            }
            updates++; ready = true;
        } finally {
            mc.getFramebuffer().bindFramebuffer(true);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private void pass(Framebuffer source, Framebuffer target, float dx, float dy) {
        target.bindFramebuffer(true);
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.loadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.loadIdentity();
        GlStateManager.disableBlend(); GlStateManager.disableAlpha(); GlStateManager.disableDepth();
        boolean culling = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D(); GlStateManager.bindTexture(source.framebufferTexture);
        blur.use(); blur.uniform1i("Sampler0", 0);
        blur.uniform2f("uResolution", source.framebufferWidth, source.framebufferHeight);
        blur.uniform2f("uBlurDir", dx, dy); blur.uniform1f("uRadius", Blur.samplingRadius());
        GlStateManager.color(1, 1, 1, 1);
        quad(0, 0, 1, 1, false);
        Shader.unbind();
        if (culling) GlStateManager.enableCull();
    }

    void draw(UI ui, float width, float height, int tint, float offset) {
        if (!ready) return;
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D(); GlStateManager.bindTexture(background.framebufferTexture);
        GlStateManager.color(1, 1, 1, 1);
        // Menu wallpapers can leave their final scrim alpha in the target; the shade itself is opaque.
        GlStateManager.disableBlend();
        quad(0, 0, width, height, true);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.disableTexture2D();
        glass(ui, 0, 0, width, height, 0, tint, offset);
    }

    boolean glass(UI ui, float x, float y, float width, float height, float radius, int tint, float offset) {
        return ready && blur.valid()
                && ui.glass(x, y, width, height, radius, blurred.framebufferTexture, offset, tint, 0.55F);
    }

    private static void quad(float x, float y, float w, float h, boolean flip) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, flip ? 1 : 0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, flip ? 0 : 1); GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1, flip ? 0 : 1); GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1, flip ? 1 : 0); GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    void dispose() {
        if (background != null) background.deleteFramebuffer();
        if (horizontal != null) horizontal.deleteFramebuffer();
        if (blurred != null) blurred.deleteFramebuffer();
        background = horizontal = blurred = null; ready = false;
    }
}
