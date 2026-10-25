package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import xyz.daikirai.module.render.CustomGuiModule;

import java.nio.IntBuffer;

/** Animates the GUI foreground while keeping its world background full-screen. */
public final class GuiOpenAnimation {
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final Anim OPEN = new Anim(1.0F);
    private static GuiScreen screen;
    private static Framebuffer target;
    private static long lastFrame;
    private static float scale = 1.0F;
    private static boolean targetFailed;
    private static GuiScreen foreground;
    private static float backgroundAlpha = 1.0F;

    private GuiOpenAnimation() {
    }

    private static boolean supported(GuiScreen screen) {
        return screen instanceof GuiContainer || screen instanceof GuiIngameMenu;
    }

    public static void opened(GuiScreen next) {
        screen = next;
        scale = 1.0F;
        lastFrame = 0L;
        boolean animate = CustomGuiModule.animatesScreens() && supported(next);
        OPEN.set(animate ? 0.0F : 1.0F);
        if (animate) OPEN.to(1.0F, CustomGuiModule.openDuration(), Ease.EMPHASIZED);
    }

    public static void reset() {
        screen = null;
        scale = 1.0F;
        lastFrame = 0L;
        OPEN.set(1.0F);
        if (target != null) {
            target.deleteFramebuffer();
            target = null;
        }
        targetFailed = false;
    }

    /** Includes Forge's GUI hooks, and always restores the font scope when drawing throws. */
    public static void drawScreen(GuiScreen gui, int mouseX, int mouseY, float partialTicks) {
        GuiFont.begin();
        try {
            if (screen != gui) opened(gui);
            if (!CustomGuiModule.animatesScreens() || !supported(gui)) {
                scale = 1.0F;
                ForgeHooksClient.drawScreen(gui, mouseX, mouseY, partialTicks);
                return;
            }
            long now = System.nanoTime();
            OPEN.update(lastFrame == 0L ? 16.0F : Math.min((now - lastFrame) / 1000000.0F, 100.0F));
            lastFrame = now;
            float progress = OPEN.value();
            scale = CustomGuiModule.openScale() + (1.0F - CustomGuiModule.openScale()) * progress;
            if (OPEN.isDone()) {
                scale = 1.0F;
                ForgeHooksClient.drawScreen(gui, mouseX, mouseY, partialTicks);
                return;
            }
            int x = mouseX(gui, mouseX);
            int y = mouseY(gui, mouseY);
            boolean fade = CustomGuiModule.openFade() && !targetFailed && OpenGlHelper.isFramebufferEnabled();
            drawBackground(gui, fade ? progress : 1.0F);
            if (fade) {
                drawFaded(gui, x, y, partialTicks, progress);
            } else {
                drawScaled(gui, x, y, partialTicks);
            }
        } finally {
            GuiFont.end();
        }
    }

    private static void drawBackground(GuiScreen gui, float alpha) {
        float previous = backgroundAlpha;
        backgroundAlpha = alpha;
        try {
            gui.drawWorldBackground(0);
        } finally {
            backgroundAlpha = previous;
        }
    }

    /** The background is drawn once before either the scale transform or the offscreen capture. */
    public static boolean skipsBackground(GuiScreen gui) {
        return gui == foreground;
    }

    public static int backgroundColor(int color) {
        return color & 0xFFFFFF | Math.round((color >>> 24) * backgroundAlpha) << 24;
    }

    private static void drawForeground(GuiScreen gui, int mouseX, int mouseY, float partialTicks) {
        GuiScreen previous = foreground;
        foreground = gui;
        try {
            ForgeHooksClient.drawScreen(gui, mouseX, mouseY, partialTicks);
        } finally {
            foreground = previous;
        }
    }

    private static void drawScaled(GuiScreen gui, int mouseX, int mouseY, float partialTicks) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(gui.width / 2.0F, gui.height / 2.0F, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.translate(-gui.width / 2.0F, -gui.height / 2.0F, 0.0F);
            drawForeground(gui, mouseX, mouseY, partialTicks);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static void drawFaded(GuiScreen gui, int mouseX, int mouseY, float partialTicks, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        int previous = GL11.glGetInteger(0x8CA6);
        VIEWPORT.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
        int vx = VIEWPORT.get(0), vy = VIEWPORT.get(1), vw = VIEWPORT.get(2), vh = VIEWPORT.get(3);
        boolean ready = false;
        try {
            try {
                if (target == null) {
                    target = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
                    target.setFramebufferColor(0, 0, 0, 0);
                } else if (target.framebufferWidth != mc.displayWidth || target.framebufferHeight != mc.displayHeight) {
                    target.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
                }
                target.framebufferClear();
                target.bindFramebuffer(true);
                ready = true;
            } catch (Throwable error) {
                targetFailed = true;
                System.err.println("[onyx] GUI animation fell back to scaling: " + error);
                if (target != null) {
                    target.deleteFramebuffer();
                    target = null;
                }
            }
            if (ready) drawForeground(gui, mouseX, mouseY, partialTicks);
        } finally {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previous);
            GlStateManager.viewport(vx, vy, vw, vh);
        }
        if (!ready) {
            // The failed target has already been unbound before trying the direct path.
            drawScaled(gui, mouseX, mouseY, partialTicks);
            return;
        }
        composite(gui.width, gui.height, alpha);
    }

    private static void composite(int width, int height, float alpha) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableAlpha();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            // GUI draws into a transparent target, so its RGB is already premultiplied by alpha.
            GlStateManager.tryBlendFuncSeparate(1, 771, 1, 771);
            GlStateManager.bindTexture(target.framebufferTexture);
            GlStateManager.color(alpha, alpha, alpha, alpha);
            float x = width * (1.0F - scale) / 2.0F;
            float y = height * (1.0F - scale) / 2.0F;
            float u = (float) target.framebufferWidth / target.framebufferTextureWidth;
            float v = (float) target.framebufferHeight / target.framebufferTextureHeight;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, v);
            GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0, 0);
            GL11.glVertex2f(x, height - y);
            GL11.glTexCoord2f(u, 0);
            GL11.glVertex2f(width - x, height - y);
            GL11.glTexCoord2f(u, v);
            GL11.glVertex2f(width - x, y);
            GL11.glEnd();
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.bindTexture(bound);
            GlStateManager.tryBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) GlStateManager.disableBlend();
            if (!texture) GlStateManager.disableTexture2D();
            if (depth) GlStateManager.enableDepth();
            if (alphaTest) GlStateManager.enableAlpha();
            if (lighting) GlStateManager.enableLighting();
            GlStateManager.color(1, 1, 1, 1);
        }
    }

    private static float inputScale(GuiScreen gui) {
        return gui == screen && CustomGuiModule.animatesScreens() && supported(gui) ? Math.max(0.01F, scale) : 1.0F;
    }

    public static int mouseX(GuiScreen gui, int x) {
        return Math.round((x - gui.width / 2.0F) / inputScale(gui) + gui.width / 2.0F);
    }

    public static int mouseY(GuiScreen gui, int y) {
        return Math.round((y - gui.height / 2.0F) / inputScale(gui) + gui.height / 2.0F);
    }

}
