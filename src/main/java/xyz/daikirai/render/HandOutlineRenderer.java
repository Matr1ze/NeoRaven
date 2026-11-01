package xyz.daikirai.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.render.HandOutlineModule;

/** Onyx's half-resolution mask, expansion, smoothing and aurora passes, plus the trail feedback. */
public final class HandOutlineRenderer {
    private static boolean capturing;
    /** 0..5 = the original pipeline, 6..7 = the two trail feedback targets. */
    private final Framebuffer[] targets = new Framebuffer[8];
    private Shader mask, expand, smooth, aurora, blit, trail;
    private boolean failed;
    private boolean trailPrimed;
    private int trailIndex;
    private long started;

    public static boolean capturing() { return capturing; }
    public void reset() { failed = false; started = System.nanoTime(); trailPrimed = false; }
    public void dispose() {
        if (targets[0] == null) return;
        int framebuffer = GL11.glGetInteger(0x8CA6);
        int active = GL11.glGetInteger(0x84E0);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) targets[i].deleteFramebuffer();
            targets[i] = null;
        }
        GlStateManager.bindTexture(texture);
        GlStateManager.setActiveTexture(active);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
    }

    public void render(ItemRenderer itemRenderer, float partial, HandOutlineModule module) {
        Minecraft mc = Minecraft.getMinecraft();
        if (capturing || failed || !OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()
                || mc.displayWidth <= 0 || mc.displayHeight <= 0) return;
        try (HandState state = new HandState()) {
            if (mask == null) {
                mask = Shader.load("onyx_hand_mask");
                expand = Shader.load("onyx_hand_expand", "onyx_post");
                smooth = Shader.load("onyx_hand_smooth", "onyx_post");
                aurora = Shader.load("onyx_hand_aurora", "onyx_post");
                blit = Shader.load("onyx_hand_blit", "onyx_post");
                trail = Shader.load("onyx_hand_trail", "onyx_post");
            }
            if (!mask.valid() || !expand.valid() || !smooth.valid() || !aurora.valid() || !blit.valid()
                    || !trail.valid()) {
                failed = true;
                return;
            }
            int width = Math.max(1, Math.round(mc.displayWidth * .5F));
            int height = Math.max(1, Math.round(mc.displayHeight * .5F));
            for (int i = 0; i < targets.length; i++) {
                Framebuffer target = targets[i];
                if (target == null) {
                    targets[i] = target = new Framebuffer(width, height, i == 0);
                    target.setFramebufferColor(0, 0, 0, 0);
                    target.setFramebufferFilter(GL11.GL_LINEAR);
                    GlStateManager.bindTexture(target.framebufferTexture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
                } else if (target.framebufferWidth != width || target.framebufferHeight != height) {
                    target.createBindFramebuffer(width, height);
                    target.setFramebufferFilter(GL11.GL_LINEAR);
                    GlStateManager.bindTexture(target.framebufferTexture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
                }
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            targets[0].framebufferClear();
            targets[0].bindFramebuffer(true);
            mask.use();
            mask.uniform1i("Sampler0", 0);
            mask.uniform4f("uColor", 1, 1, 1, 1);
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.pushMatrix();
            capturing = true;
            try {
                itemRenderer.renderItemInFirstPerson(partial);
            } finally {
                capturing = false;
                GlStateManager.popMatrix();
            }
            // Postprocessing uses raw GL only; its attribute scope leaves the MC cache untouched.
            postprocess(state.framebuffer, state.viewport, width, height, module);
        } catch (Throwable error) {
            failed = true;
            dispose();
            System.err.println("[onyx] hand outline disabled after render failure: " + error);
        }
    }

    private void postprocess(int scene, int[] viewport, int width, int height, HandOutlineModule m) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPushMatrix(); GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glDisable(GL11.GL_BLEND);
            GL11.glColorMask(true, true, true, true);
            float ratio = (float) width / Minecraft.getMinecraft().displayWidth;
            for (int axis = 0; axis < 2; axis++) {
                bind(targets[axis + 1], width, height);
                expand.use(); expand.uniform1i("Sampler0", 0);
                expand.uniform2f("uSourceSize", width, height);
                expand.uniform2f("uDirection", axis == 0 ? 1 : 0, axis == 0 ? 0 : 1);
                expand.uniform1f("uPadding", (float) m.padding.getInput() * ratio);
                draw(targets[axis].framebufferTexture);
            }
            for (int axis = 0; axis < 2; axis++) {
                bind(targets[axis + 3], width, height);
                smooth.use(); smooth.uniform1i("Sampler0", 0);
                smooth.uniform2f("uSourceSize", width, height);
                smooth.uniform2f("uDirection", axis == 0 ? 1 : 0, axis == 0 ? 0 : 1);
                smooth.uniform1f("uRadius", (float) m.smoothing.getInput() * ratio);
                draw(targets[axis + 2].framebufferTexture);
            }
            bind(targets[5], width, height);
            aurora.use(); aurora.uniform1i("Sampler0", 0);
            aurora.uniform2f("uMaskSize", width, height);
            aurora.uniform2f("uOutSize", viewport[2], viewport[3]);
            color(aurora, "uColorPrimary", m.primary.color(), (float) m.alpha.getInput() / 100);
            color(aurora, "uColorSecondary", m.secondary.color(), (float) m.fill.getInput() / 100);
            aurora.uniform4f("uMotion", (float) ((System.nanoTime() - started) / 1E9 % 3600),
                    (float) m.speed.getInput(), 2, Math.min(8, (float) m.outline.getInput() * ratio));
            aurora.uniform4f("uOptions", (float) m.glow.getInput() / 100, m.outlineOnly.isToggled() ? 1 : 0, 0, 0);
            draw(targets[4].framebufferTexture);
            int source = targets[5].framebufferTexture;
            boolean trail = m.trail.isToggled();
            if (trail) {
                source = accumulate(width, height, m);
            } else {
                // Dropping the trail has to drop its history too, or re-enabling would replay it.
                trailPrimed = false;
            }
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scene);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glEnable(GL11.GL_BLEND);
            if (trail) {
                // The feedback buffer is premultiplied, so it composites additively as-is.
                OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            } else {
                OpenGlHelper.glBlendFunc(770, 771, 1, 771);
            }
            blit.use(); blit.uniform1i("Sampler0", 0);
            draw(source);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix();
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, targets[0].framebufferObject);
            GL11.glPopAttrib();
        }
    }

    /**
     * Motion trail: keeps a decaying copy of last frame's aurora and adds this frame's on top.
     *
     * <p>Both accumulate premultiplied and additively, so the buffer never has to be renormalised:
     * the previous frame is scaled by {@code Trail fade}, this frame contributes {@code (1 - fade) *
     * intensity}, and a still hand settles at exactly {@code intensity} while a moving one smears
     * for roughly {@code 1 / (1 - fade)} frames.</p>
     *
     * @return the texture holding the accumulated trail
     */
    private int accumulate(int width, int height, HandOutlineModule m) {
        int front = 6 + trailIndex;
        int back = 6 + (trailIndex ^ 1);
        // Raw GL here on purpose: Framebuffer.framebufferClear() goes through GlStateManager, and
        // the cache flush that follows it makes every later draw into this target disappear.
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, targets[front].framebufferObject);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1, 1, 1, 1);
        OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        trail.use();
        trail.uniform1i("Sampler0", 0);
        float fade = (float) (m.trailFade.getInput() / 100.0);
        if (trailPrimed) {
            trail.uniform4f("uTint", fade, fade, fade, fade);
            trail.uniform1f("uPremultiply", 0.0F);
            draw(targets[back].framebufferTexture);
        } else {
            trailPrimed = true;
        }
        float gain = Math.max(0.0F, (1.0F - fade) * (float) (m.trailIntensity.getInput() / 100.0));
        trail.uniform4f("uTint", 1.0F, 1.0F, 1.0F, gain);
        trail.uniform1f("uPremultiply", 1.0F);
        draw(targets[5].framebufferTexture);
        GL11.glColor4f(1, 1, 1, 1);
        trailIndex ^= 1;
        return targets[front].framebufferTexture;
    }

    private static void color(Shader shader, String uniform, int rgb, float alpha) {
        shader.uniform4f(uniform, (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha);
    }
    private static void bind(Framebuffer target, int width, int height) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, target.framebufferObject);
        GL11.glViewport(0, 0, width, height);
    }
    private static void draw(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();
    }
}
