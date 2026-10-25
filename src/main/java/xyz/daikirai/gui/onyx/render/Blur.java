package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Frosted-glass backdrop, ported from Onyx's {@code render.Util3}.
 *
 * <p>The scene (the Minecraft framebuffer texture) is downsampled into two ping-pong targets and
 * run through the shipped {@code box_blur} shader in two passes, which is the exact same blur the
 * Onyx ClickGUI used for its translucent panel. Only the framebuffer-texture path is kept; the
 * desktop-FBO fallback of the original is dropped because 1.8.9 always renders through an FBO.</p>
 */
public final class Blur {

    private static final float[] QUAD = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
    /** GL_FRAMEBUFFER_BINDING. */
    private static final int FRAMEBUFFER_BINDING = 0x8CA6;

    private static Shader shader;
    private static boolean available = true;
    private static boolean prepared;
    private static long preparedAt;

    /**
     * Every HUD element that frosts its background (watermark island, info bar, keybind card,
     * scoreboard) asks for the same frame's backdrop. It must be captured exactly once per frame
     * and before the first element is drawn: a time window would skip whole frames at high frame
     * rates (the backdrop then lags behind the camera and shimmers), and re-capturing mid-frame
     * would bake the already drawn elements into the next one's backdrop.
     *
     * <p>The ticker below flips {@link #newFrame} at the end of every frame; the first caller in
     * the next frame captures and everybody else reuses the texture.</p>
     */
    private static boolean newFrame = true;
    private static Object ticker;
    /** Fallback window used only if {@link #install()} was never called. */
    private static final long FRAME_WINDOW_NANOS = 4_000_000L;

    private static int framebufferA = -1;
    private static int framebufferB = -1;
    private static int textureA = -1;
    private static int textureB = -1;
    private static int width;
    private static int height;

    private Blur() {
    }

    /** Installs the per-frame ticker; called once from the module bootstrap. */
    public static void install() {
        if (ticker != null) {
            return;
        }
        ticker = new Object() {
            @SubscribeEvent(priority = EventPriority.LOWEST)
            public void onRenderTick(TickEvent.RenderTickEvent event) {
                if (event.phase == TickEvent.Phase.END) {
                    newFrame = true;
                }
            }
        };
        MinecraftForge.EVENT_BUS.register(ticker);
    }

    /** Marks the next frame as needing a fresh blur (Onyx's {@code Util3.run4}). */
    public static void request() {
        prepared = false;
        newFrame = true;
    }

    public static boolean ready() {
        return prepared && textureB != -1;
    }

    public static int texture() {
        return textureB;
    }

    /**
     * Captures the scene and runs the box blur. The first caller in a frame does the work and
     * every later caller reuses that texture, so all frosted HUD elements share exactly one
     * capture per frame — taken before the first of them draws anything.
     */
    public static void prepare() {
        if (!available) {
            return;
        }
        // A minimized window must not drive a framebuffer capture: the capture rebinds the scene FBO
        // and queries the binding while the driver has stopped presenting, which on Windows + a
        // fullscreen window either stalls the frame for a second at a time or kills the process
        // outright (0xC0000409, or an AppHang the launcher then force-closes). Nothing is on screen,
        // so the previous frame's texture stays bound and the frost keeps compositing that instead.
        // Only "not visible" is checked: an unfocused window that is still on screen presents
        // normally and must keep its blur live (the game can be watched on a second monitor).
        if (!Display.isVisible()) {
            return;
        }
        long now = System.nanoTime();
        if (ticker != null) {
            if (!newFrame) {
                return;
            }
            newFrame = false;
        } else if (prepared && now - preparedAt < FRAME_WINDOW_NANOS) {
            return;
        }
        if (shader == null) {
            shader = Shader.load("box_blur");
        }
        if (!shader.valid()) {
            available = false;
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            Framebuffer framebuffer = mc.getFramebuffer();
            if (framebuffer == null || framebuffer.framebufferTexture <= 0) {
                available = false;
                return;
            }

            int screenWidth = framebuffer.framebufferWidth;
            int screenHeight = framebuffer.framebufferHeight;
            int targetWidth = Math.max(1, screenWidth / 2);
            int targetHeight = Math.max(1, screenHeight / 2);
            int previousFramebuffer = GL11.glGetInteger(FRAMEBUFFER_BINDING);
            ensureTargets(targetWidth, targetHeight);

            pass(framebuffer.framebufferTexture, framebufferA, screenWidth, screenHeight, 1.0F, 0.0F);
            pass(textureA, framebufferB, targetWidth, targetHeight, 0.0F, 1.0F);
            pass(textureB, framebufferA, targetWidth, targetHeight, 1.0F, 0.0F);
            pass(textureA, framebufferB, targetWidth, targetHeight, 0.0F, 1.0F);

            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, previousFramebuffer);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.enableDepth();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.disableAlpha();
            GlStateManager.disableDepth();
            GlStateManager.shadeModel(7425);
            GlStateManager.disableTexture2D();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.resetColor();
            prepared = true;
            preparedAt = now;
        } catch (Throwable t) {
            available = false;
            prepared = false;
            System.err.println("[onyx] frosted glass unavailable: " + t);
        }
    }

    private static void ensureTargets(int targetWidth, int targetHeight) {
        if (framebufferA != -1 && width == targetWidth && height == targetHeight) {
            return;
        }
        dispose();

        textureA = createTexture(targetWidth, targetHeight);
        textureB = createTexture(targetWidth, targetHeight);
        framebufferA = createFramebuffer(textureA);
        framebufferB = createFramebuffer(textureB);
        if (framebufferA == -1 || framebufferB == -1) {
            throw new IllegalStateException("could not create blur render targets");
        }
        width = targetWidth;
        height = targetHeight;
    }

    private static int createTexture(int w, int h) {
        int texture = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 33071);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 33071);
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int framebuffer = OpenGlHelper.glGenFramebuffers();
        if (framebuffer == -1) {
            return -1;
        }
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
        OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER, OpenGlHelper.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        int status = OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, 0);
        return status == OpenGlHelper.GL_FRAMEBUFFER_COMPLETE ? framebuffer : -1;
    }

    private static void pass(int sourceTexture, int targetFramebuffer, int sourceWidth, int sourceHeight,
                             float dirX, float dirY) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, targetFramebuffer);
        GlStateManager.viewport(0, 0, width, height);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GL11.glOrtho(0.0, 1.0, 0.0, 1.0, -1.0, 1.0);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();

        GlStateManager.bindTexture(sourceTexture);
        shader.use();
        shader.uniform1i("Sampler0", 0);
        shader.uniform2f("uResolution", sourceWidth, sourceHeight);
        shader.uniform2f("uBlurDir", dirX, dirY);
        shader.uniform1f("uRadius", 8.0F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < QUAD.length; i += 2) {
            GL11.glTexCoord2f(QUAD[i], QUAD[i + 1]);
            GL11.glVertex2f(QUAD[i], QUAD[i + 1]);
        }
        GL11.glEnd();
        Shader.unbind();
    }

    private static void dispose() {
        if (framebufferA != -1) {
            OpenGlHelper.glDeleteFramebuffers(framebufferA);
            framebufferA = -1;
        }
        if (framebufferB != -1) {
            OpenGlHelper.glDeleteFramebuffers(framebufferB);
            framebufferB = -1;
        }
        if (textureA != -1) {
            TextureUtil.deleteTexture(textureA);
            textureA = -1;
        }
        if (textureB != -1) {
            TextureUtil.deleteTexture(textureB);
            textureB = -1;
        }
    }
}
