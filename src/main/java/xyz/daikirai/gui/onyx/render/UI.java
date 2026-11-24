package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.util.glu.GLU;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Immediate-mode GUI renderer, ported from Onyx's {@code render.Sampler0}.
 *
 * <p>Everything the ClickGUI draws goes through here: SDF rounded rectangles (through the shipped
 * {@code onyx_shape} shader, so anti-aliasing and stroke widths match the original exactly),
 * shadows, scissor stacks, the frosted-glass panel, Material icons and the Google Sans type
 * scale. Colors are multiplied by a stack-based alpha, which is how the original faded whole
 * groups in and out during page transitions.</p>
 */
public final class UI {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    /** Base tint coverage for the ClickGUI's glass panels. */
    public static final float DEFAULT_TINT_COVERAGE = 0.88F;

    private static final FloatBuffer PROJECTION_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MODELVIEW_BUFFER = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer SCREEN_BUFFER = BufferUtils.createFloatBuffer(3);

    private static Shader shapeShader;
    private static Shader shadowShader;
    private static Shader frostShader;
    private static Shader imageShader;
    private static boolean shapeFailed;
    private static boolean shadowFailed;
    private static boolean frostFailed;
    private static boolean imageFailed;

    private final Deque<int[]> scissors = new ArrayDeque<int[]>();
    private final Deque<Float> savedAlphas = new ArrayDeque<Float>();
    private final Deque<Float> savedPixels = new ArrayDeque<Float>();

    private float alpha = 1.0F;
    private float pixel = 1.0F;
    /** Extra alpha for surface fills only (translucent ClickGUI); text and icons ignore it. */
    private float surfaceAlpha = 1.0F;
    private boolean hudReturnTransform;

    public UI() {
        if (shapeShader == null && !shapeFailed) {
            shapeShader = Shader.load("onyx_shape");
            shapeFailed = !shapeShader.valid();
        }
        if (shadowShader == null && !shadowFailed) {
            shadowShader = Shader.load("onyx_shadow", "onyx_shape");
            shadowFailed = !shadowShader.valid();
        }
        if (frostShader == null && !frostFailed) {
            frostShader = Shader.load("onyx_frost");
            frostFailed = !frostShader.valid();
        }
        if (imageShader == null && !imageFailed) {
            imageShader = Shader.load("onyx_image");
            imageFailed = !imageShader.valid();
        }
    }

    // ------------------------------------------------------------------ //
    // Frame / state handling
    // ------------------------------------------------------------------ //

    public void begin() {
        Minecraft mc = Minecraft.getMinecraft();
        this.pixel = 1.0F / Math.max(1, new ScaledResolution(mc).getScaleFactor());
        this.alpha = 1.0F;
        this.savedAlphas.clear();
        this.savedPixels.clear();

        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        // GlStateManager caches the GL caps it has switched, while several world renderers poke
        // glEnable/glDisable directly. Toggling through the cache forces both back into agreement,
        // so a leaked GL_TEXTURE_2D or GL_BLEND from the world pass cannot reach the GUI.
        GlStateManager.enableTexture2D();
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GL11.glTexEnvi(8960, 8704, 8448);
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.shadeModel(7425);
        float returning = xyz.daikirai.gui.onyx.hud.HudReturn.elapsed();
        if (returning >= 0) {
            float displacement = xyz.daikirai.gui.onyx.hud.HudReturn.displacement(returning);
            ScaledResolution resolution = new ScaledResolution(mc);
            float cx = resolution.getScaledWidth() / 2.0F, cy = resolution.getScaledHeight() / 2.0F;
            GlStateManager.pushMatrix(); hudReturnTransform = true;
            GlStateManager.translate(cx, cy + 16 * displacement, 0);
            float size = 1 - 0.10F * displacement;
            GlStateManager.scale(size, size, 1);
            GlStateManager.translate(-cx, -cy, 0);
            alpha = Math.min(1, 0.25F + returning / 0.16F);
        }
    }

    public void end() {
        if (hudReturnTransform) { GlStateManager.popMatrix(); hudReturnTransform = false; }
        while (!scissors.isEmpty()) {
            scissorPop();
        }
        Shader.unbind();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
        GlStateManager.shadeModel(7424);
        // Restore with a toggle so the caches and the driver agree again for whoever draws next
        // (chat, tooltips, the vanilla bitmap font).
        GlStateManager.disableTexture2D();
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.disableAlpha();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.disableBlend();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        this.alpha = 1.0F;
        this.pixel = 1.0F;
    }

    public float alpha() {
        return alpha;
    }

    /** Multiplies the current alpha and remembers the previous value. */
    public void alphaPush(float factor) {
        savedAlphas.push(Float.valueOf(alpha));
        this.alpha = Material.clamp(alpha * factor, 0.0F, 1.0F);
    }

    /** Restores the alpha saved by the matching {@link #alphaPush(float)}. */
    public void alphaPop() {
        if (!savedAlphas.isEmpty()) {
            this.alpha = savedAlphas.pop().floatValue();
        }
    }

    public void push() {
        savedPixels.push(Float.valueOf(pixel));
        GlStateManager.pushMatrix();
    }

    public void pop() {
        GlStateManager.popMatrix();
        if (!savedPixels.isEmpty()) {
            this.pixel = savedPixels.pop().floatValue();
        }
    }

    public void translate(float x, float y) {
        GlStateManager.translate(x, y, 0.0F);
    }

    /** Draws one perspective-projected album sheet in GUI coordinates. */
    public void albumQuad(ResourceLocation texture, float x1, float y1, float x2, float y2,
                          float x3, float y3, float x4, float y4, float alpha) {
        if (texture == null || alpha <= 0.001F) return;
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, Material.clamp(alpha, 0.0F, 1.0F));
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(1.0F, 0.0F); GL11.glVertex2f(x1, y1);
        GL11.glTexCoord2f(1.0F, 1.0F); GL11.glVertex2f(x2, y2);
        GL11.glTexCoord2f(0.0F, 1.0F); GL11.glVertex2f(x3, y3);
        GL11.glTexCoord2f(0.0F, 0.0F); GL11.glVertex2f(x4, y4);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableTexture2D();
    }

    /** Scales everything drawn afterwards around a point, keeping AA pixel size constant. */
    public void scale(float factor, float centerX, float centerY) {
        GlStateManager.translate(centerX, centerY, 0.0F);
        GlStateManager.scale(factor, factor, 1.0F);
        GlStateManager.translate(-centerX, -centerY, 0.0F);
        if (factor > 0.0F) {
            this.pixel /= factor;
        }
    }

    // ------------------------------------------------------------------ //
    // Scissor stack (logical coordinates, converted to window pixels)
    // ------------------------------------------------------------------ //

    public void scissorPush(float x, float y, float w, float h) {
        int[] rect = windowRect(x, y, x + w, y + h);
        int[] parent = scissors.peek();
        if (parent != null) {
            rect = intersect(rect, parent);
        }
        scissors.push(rect);
        applyScissor(rect);
    }

    public void scissorPop() {
        if (!scissors.isEmpty()) {
            scissors.pop();
            int[] parent = scissors.peek();
            if (parent == null) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            } else {
                applyScissor(parent);
            }
        }
    }

    private static void applyScissor(int[] rect) {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(rect[0], rect[1], rect[2], rect[3]);
    }

    private static int[] intersect(int[] a, int[] b) {
        int x = Math.max(a[0], b[0]);
        int y = Math.max(a[1], b[1]);
        int right = Math.min(a[0] + a[2], b[0] + b[2]);
        int top = Math.min(a[1] + a[3], b[1] + b[3]);
        return new int[]{x, y, Math.max(0, right - x), Math.max(0, top - y)};
    }

    private static int[] windowRect(float x0, float y0, float x1, float y1) {
        float[] a = project(x0, y0);
        float[] b = project(x1, y1);
        int x = Math.round(Math.min(a[0], b[0]));
        int y = Math.round(Math.min(a[1], b[1]));
        int right = Math.round(Math.max(a[0], b[0]));
        int top = Math.round(Math.max(a[1], b[1]));
        return new int[]{x, y, Math.max(0, right - x), Math.max(0, top - y)};
    }

    private static float[] project(float x, float y) {
        MODELVIEW_BUFFER.clear();
        PROJECTION_BUFFER.clear();
        VIEWPORT_BUFFER.clear();
        SCREEN_BUFFER.clear();
        GL11.glGetFloat(2982, MODELVIEW_BUFFER);
        GL11.glGetFloat(2983, PROJECTION_BUFFER);
        GL11.glGetInteger(2978, VIEWPORT_BUFFER);
        GLU.gluProject(x, y, 0.0F, MODELVIEW_BUFFER, PROJECTION_BUFFER, VIEWPORT_BUFFER, SCREEN_BUFFER);
        return new float[]{SCREEN_BUFFER.get(0), SCREEN_BUFFER.get(1)};
    }

    // ------------------------------------------------------------------ //
    // Shapes
    // ------------------------------------------------------------------ //

    public void rect(float x, float y, float w, float h, int color) {
        shape(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, color, color, color, color);
    }

    public void rounded(float x, float y, float w, float h, float radius, int color) {
        float r = Theme.radius(radius, w, h);
        shape(x, y, w, h, r, r, r, r, 0.0F, 0.0F, color, color, color, color);
    }

    public void roundedCorners(float x, float y, float w, float h,
                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                               int color) {
        roundedCorners(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft, color, false, false);
    }

    /** Fills the same bounds with no vertical feathering at joined row edges. */
    public void roundedCorners(float x, float y, float w, float h,
                               float topLeft, float topRight, float bottomRight, float bottomLeft,
                               int color, boolean joinTop, boolean joinBottom) {
        shape(x, y, w, h,
                Theme.radius(topLeft, w, h), Theme.radius(topRight, w, h),
                Theme.radius(bottomRight, w, h), Theme.radius(bottomLeft, w, h),
                0.0F, 0.0F, color, color, color, color, joinTop, joinBottom);
    }

    /** Stroked rounded rectangle (thickness inside the bounds), like Material's outline. */
    public void outline(float x, float y, float w, float h, float radius, float thickness, int color) {
        if (thickness <= 0.0F) {
            return;
        }
        float r = Theme.radius(radius, w, h);
        shape(x, y, w, h, r, r, r, r, -thickness, 0.75F, color, color, color, color);
    }

    /** Draws a texture quad with a tint (used by the island logo). */
    public void image(ResourceLocation texture, float x, float y, float w, float h,
                      float u0, float v0, float u1, float v1, int color) {
        image(texture, x, y, w, h, u0, v0, u1, v1, color, true);
    }

    /** Keeps pixel-art skin faces crisp while preserving the shared world's texture filters. */
    public void imageNearest(ResourceLocation texture, float x, float y, float w, float h,
                             float u0, float v0, float u1, float v1, int color) {
        image(texture, x, y, w, h, u0, v0, u1, v1, color, false);
    }

    private void image(ResourceLocation texture, float x, float y, float w, float h,
                       float u0, float v0, float u1, float v1, int color, boolean smooth) {
        int c = applyAlpha(color);
        if (c >>> 24 == 0) {
            return;
        }
        // Bind on unit 0 explicitly: the frosted-glass passes switch the active unit, and a
        // stale unit makes the shader sample whatever texture was left there (covers from the
        // previous row), which reads as flickering artwork.
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        int previousMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int previousMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        int filter = smooth ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glColor4f((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F,
                (c & 0xFF) / 255.0F, (c >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(u0, v0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(u0, v1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(u1, v1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(u1, v0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMinFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMagFilter);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
        GlStateManager.disableTexture2D();
    }

    /**
     * Draws a texture with rounded corners. Album art is the reason this exists: a plain
     * {@link #image} quad keeps square corners and visibly pokes out of a rounded card.
     *
     * <p>The mask runs through {@code onyx_image}, the same SDF the shapes use, so the corner
     * keeps the panel's anti-aliasing at any scale. When the program cannot be built the call
     * falls back to the plain textured quad rather than drawing nothing.</p>
     */
    public void imageRounded(ResourceLocation texture, float x, float y, float w, float h,
                             float radius, int color) {
        if (texture == null || w <= 0.0F || h <= 0.0F) {
            return;
        }
        int c = applyAlpha(color);
        if (c >>> 24 == 0) {
            return;
        }
        Shader shader = imageShader;
        if (shader == null || !shader.valid()) {
            image(texture, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F, color);
            return;
        }
        float r = Theme.radius(radius, w, h);
        if (r <= 0.01F) {
            image(texture, x, y, w, h, 0.0F, 0.0F, 1.0F, 1.0F, color);
            return;
        }
        // Bind on unit 0 explicitly: the frosted-glass passes switch the active unit, and a
        // stale unit makes the shader sample whatever texture was left there (covers from the
        // previous row), which reads as flickering artwork.
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableTexture2D();
        int previousMinFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int previousMagFilter = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        shader.use();
        shader.uniform1i("Sampler0", 0);
        shader.uniform2f("uHalfExtents", w / 2.0F, h / 2.0F);
        shader.uniform4f("uCornerRadii", r, r, r, r);
        shader.uniform1f("uPixel", pixel);
        GL11.glColor4f((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F,
                (c & 0xFF) / 255.0F, (c >>> 24) / 255.0F);

        float centerX = x + w / 2.0F;
        float centerY = y + h / 2.0F;
        GL11.glBegin(GL11.GL_QUADS);
        imageVertex(x, y, centerX, centerY, 0.0F, 0.0F);
        imageVertex(x, y + h, centerX, centerY, 0.0F, 1.0F);
        imageVertex(x + w, y + h, centerX, centerY, 1.0F, 1.0F);
        imageVertex(x + w, y, centerX, centerY, 1.0F, 0.0F);
        GL11.glEnd();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMinFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMagFilter);


        Shader.unbind();
        GlStateManager.disableTexture2D();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
    }

    private static void imageVertex(float x, float y, float centerX, float centerY, float u, float v) {
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, x - centerX, y - centerY);
        GL11.glTexCoord2f(u, v);
        GL11.glVertex2f(x, y);
    }

    /** Vertical two-colour gradient (used for the title-screen backdrop). */
    public void gradient(float x, float y, float w, float h, int top, int bottom) {
        shape(x, y, w, h, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, top, bottom, bottom, top);
    }

    /**
     * Four-corner gradient confined to a rounded rectangle (the colour picker's HSV plane and its
     * hue ramp). It goes through the same SDF pipeline as {@link #rounded}, so the edge keeps the
     * anti-aliasing every other surface in the GUI has; a raw {@code glBegin} quad would give the
     * plane a stair-stepped corner.
     */
    public void gradient4(float x, float y, float w, float h, float radius,
                          int topLeft, int bottomLeft, int bottomRight, int topRight) {
        float r = Theme.radius(radius, w, h);
        shape(x, y, w, h, r, r, r, r, 0.0F, 0.0F, topLeft, bottomLeft, bottomRight, topRight);
    }

    public void circle(float centerX, float centerY, float radius, int color) {
        rounded(centerX - radius, centerY - radius, radius * 2.0F, radius * 2.0F, radius, color);
    }

    /** Soft outer shadow, with a transparent interior for glass surfaces. */
    public void shadow(float x, float y, float w, float h, float radius, int elevation) {
        float spread = Theme.shadowForLevel(elevation);
        if (spread <= 0.0F || w <= 0.0F || h <= 0.0F
                || shadowShader == null || !shadowShader.valid()) {
            return;
        }
        int color = applySurfaceAlpha(Material.scaleAlpha(Theme.scheme().shadow, 0.16F));
        if (color >>> 24 == 0) {
            return;
        }
        float sigma = 2.0F + spread * 0.75F;
        float offset = spread * 0.35F;
        // Four standard deviations leave less than a pixel's visible alpha at the quad edge.
        float padding = sigma * 4.0F + offset + pixel * 2.0F;
        float centerX = x + w / 2.0F;
        float centerY = y + h / 2.0F;
        GlStateManager.disableTexture2D();
        shadowShader.use();
        shadowShader.uniform2f("uHalfExtents", w / 2.0F, h / 2.0F);
        shadowShader.uniform1f("uRadius", Theme.radius(radius, w, h));
        shadowShader.uniform1f("uSigma", sigma);
        shadowShader.uniform1f("uOffset", offset);
        shadowShader.uniform1f("uPixel", pixel);
        GL11.glBegin(GL11.GL_QUADS);
        vertex(x - padding, y - padding, centerX, centerY, color);
        vertex(x - padding, y + h + padding, centerX, centerY, color);
        vertex(x + w + padding, y + h + padding, centerX, centerY, color);
        vertex(x + w + padding, y - padding, centerX, centerY, color);
        GL11.glEnd();
        Shader.unbind();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
    }

    /** Fills a rounded rectangle with the (already blurred) backdrop, tinted by {@code tint}. */
    public void frost(float x, float y, float w, float h, float radius, int tint) {
        frost(x, y, w, h, radius, tint, DEFAULT_TINT_COVERAGE);
    }

    /**
     * Fills a rounded rectangle with the (already blurred) backdrop.
     *
     * @param coverage base tint strength, with the glass shader adapting it to scene brightness
     */
    public void frost(float x, float y, float w, float h, float radius, int tint, float coverage) {
        frostCorners(x, y, w, h, radius, radius, radius, radius, tint, coverage);
    }

    /** Frosted fill with one radius per corner (top-left, top-right, bottom-right, bottom-left). */
    public void frostCorners(float x, float y, float w, float h,
                             float topLeft, float topRight, float bottomRight, float bottomLeft,
                             int tint, float coverage) {
        frostCorners(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft, tint, coverage, false, false);
    }

    /** Frosted fill with no vertical feathering at joined row edges. */
    public void frostCorners(float x, float y, float w, float h,
                             float topLeft, float topRight, float bottomRight, float bottomLeft,
                             int tint, float coverage, boolean joinTop, boolean joinBottom) {
        if (!Blur.ready()) {
            return;
        }
        if (!glass(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
                tint, coverage, joinTop, joinBottom, Blur.texture(), 0.0F)) {
            roundedCorners(x, y, w, h, topLeft, topRight, bottomRight, bottomLeft,
                    tint, joinTop, joinBottom);
        }
    }

    /** Draws the selected glass material from a screen-aligned backdrop. */
    public boolean glass(float x, float y, float w, float h, float radius, int texture,
                         float offset, int tint, float coverage) {
        return glass(x, y, w, h, radius, radius, radius, radius,
                tint, coverage, false, false, texture, offset);
    }

    private boolean glass(float x, float y, float w, float h,
                          float topLeft, float topRight, float bottomRight, float bottomLeft,
                          int tint, float coverage, boolean joinTop, boolean joinBottom,
                          int texture, float offset) {
        if (!frostShader.valid() || texture <= 0) {
            return false;
        }
        int color = applyAlpha(tint);
        if (w <= 0.0F || h <= 0.0F || Material.alpha(color) == 0) {
            return true;
        }
        float tl = Theme.radius(topLeft, w, h);
        float tr = Theme.radius(topRight, w, h);
        float br = Theme.radius(bottomRight, w, h);
        float bl = Theme.radius(bottomLeft, w, h);
        Minecraft mc = Minecraft.getMinecraft();
        float topExtension = joinedEdgeExtension(joinTop);
        float bottomExtension = joinedEdgeExtension(joinBottom);

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(texture);
        GlStateManager.enableTexture2D();
        frostShader.use();
        frostShader.uniform1i("Sampler0", 0);
        frostShader.uniform2f("uHalfExtents", w / 2.0F, (h + topExtension + bottomExtension) / 2.0F);
        frostShader.uniform4f("uCornerRadii", tl, tr, br, bl);
        frostShader.uniform1f("uPixel", pixel);
        frostShader.uniform2f("uResolution", mc.displayWidth, mc.displayHeight);
        frostShader.uniform1f("uBackdropOffset", offset);
        frostShader.uniform1f("uTint", Material.clamp(coverage, 0.0F, 1.0F));
        frostShader.uniform1i("uLiquidGlass", Theme.liquidGlass() ? 1 : 0);
        frostShader.uniform2f("uJoinedEdges", joinTop ? 1.0F : 0.0F, joinBottom ? 1.0F : 0.0F);

        GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F);
        float centerX = x + w / 2.0F;
        float centerY = y + (h + bottomExtension - topExtension) / 2.0F;
        GL11.glBegin(GL11.GL_QUADS);
        localVertex(x, y, centerX, centerY);
        localVertex(x, y + h, centerX, centerY);
        localVertex(x + w, y + h, centerX, centerY);
        localVertex(x + w, y, centerX, centerY);
        GL11.glEnd();

        Shader.unbind();
        GlStateManager.disableTexture2D();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
        return true;
    }

    private static void localVertex(float x, float y, float centerX, float centerY) {
        GL11.glTexCoord2f(x - centerX, y - centerY);
        GL11.glVertex2f(x, y);
    }

    private void shape(float x, float y, float w, float h,
                       float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                       float radiusBottomLeft, float softness, float expansion,
                       int colorTopLeft, int colorBottomLeft, int colorBottomRight, int colorTopRight) {
        shape(x, y, w, h, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                softness, expansion, colorTopLeft, colorBottomLeft, colorBottomRight, colorTopRight, false, false);
    }

    private void shape(float x, float y, float w, float h,
                       float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                       float radiusBottomLeft, float softness, float expansion,
                       int colorTopLeft, int colorBottomLeft, int colorBottomRight, int colorTopRight,
                       boolean joinTop, boolean joinBottom) {
        if (w <= 0.0F || h <= 0.0F) {
            return;
        }
        Shader shader = shapeShader;
        if (shader == null || !shader.valid()) {
            fallbackQuad(x, y, w, h, colorTopLeft);
            return;
        }

        int c0 = applySurfaceAlpha(colorTopLeft);
        int c1 = applySurfaceAlpha(colorBottomLeft);
        int c2 = applySurfaceAlpha(colorBottomRight);
        int c3 = applySurfaceAlpha(colorTopRight);
        if ((c0 | c1 | c2 | c3) == 0) {
            return;
        }

        float topExtension = joinedEdgeExtension(joinTop);
        float bottomExtension = joinedEdgeExtension(joinBottom);
        float centerX = x + w / 2.0F;
        float centerY = y + (h + bottomExtension - topExtension) / 2.0F;

        GlStateManager.disableTexture2D();
        shader.use();
        shader.uniform2f("uHalfExtents", w / 2.0F, (h + topExtension + bottomExtension) / 2.0F);
        shader.uniform4f("uCornerRadii", radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft);
        shader.uniform1f("uSoftness", softness);
        shader.uniform1f("uPixel", pixel);

        float x0 = x - expansion;
        float y0 = y - expansion;
        float x1 = x + w + expansion;
        float y1 = y + h + expansion;

        GL11.glBegin(GL11.GL_QUADS);
        vertex(x0, y0, centerX, centerY, c0);
        vertex(x0, y1, centerX, centerY, c1);
        vertex(x1, y1, centerX, centerY, c2);
        vertex(x1, y0, centerX, centerY, c3);
        GL11.glEnd();

        Shader.unbind();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
    }

    private float joinedEdgeExtension(boolean joined) {
        // Move the shader's edge beyond its feather while keeping the quad at the row bounds.
        return joined ? 2.0F * pixel : 0.0F;
    }

    private static void vertex(float x, float y, float centerX, float centerY, int color) {
        GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F);
        GL11.glTexCoord2f(x - centerX, y - centerY);
        GL11.glVertex2f(x, y);
    }

    private void fallbackQuad(float x, float y, float w, float h, int color) {
        int c = applySurfaceAlpha(color);
        if (c >>> 24 == 0) {
            return;
        }
        GlStateManager.disableTexture2D();
        GL11.glColor4f((c >> 16 & 0xFF) / 255.0F, (c >> 8 & 0xFF) / 255.0F,
                (c & 0xFF) / 255.0F, (c >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ //
    // Text and icons
    // ------------------------------------------------------------------ //

    /** Draws a string with the given alignment, vertically centred on {@code y}. */
    public void text(Typeface.Style style, String text, float x, float y, int color, int align) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.indexOf('\u00a7') >= 0) {
            legacy(style, text, x, y, color, align, false);
            return;
        }
        float width = style.width(text);
        if (align == ALIGN_CENTER) {
            x -= width / 2.0F;
        } else if (align == ALIGN_RIGHT) {
            x -= width;
        }
        draw(style, text, x, y + style.ascent() / 2.0F - 7.0F * style.scale(), color);
    }

    /** Draws a string with its top at {@code y} (matches Onyx's leading-anchored text). */
    public void textTop(Typeface.Style style, String text, float x, float y, int color, int align) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (text.indexOf('\u00a7') >= 0) {
            legacy(style, text, x, y, color, align, true);
            return;
        }
        float width = style.width(text);
        if (align == ALIGN_CENTER) {
            x -= width / 2.0F;
        } else if (align == ALIGN_RIGHT) {
            x -= width;
        }
        draw(style, text, x, y + style.ascent() - 7.0F * style.scale(), color);
    }

    /**
     * Draws a string that still carries Minecraft {@code §} formatting.
     *
     * <p>Settings hand their display value around with the codes baked in (a slider sitting at its
     * minimum says {@code §cDisabled}), and server strings keep theirs too. The typefaces have no
     * colour support, so the codes are split into runs: the visible glyphs are measured without
     * them and each run keeps its own colour. Without this the code characters themselves were
     * rasterised - the "§" fell through to the CJK fallback and showed up as a stray ideograph.</p>
     */
    private void legacy(Typeface.Style style, String text, float x, float y, int color, int align,
                        boolean top) {
        java.util.List<xyz.daikirai.gui.onyx.LegacyText.Run> runs =
                xyz.daikirai.gui.onyx.LegacyText.parse(text, color);
        if (runs.isEmpty()) {
            return;
        }
        float total = 0.0F;
        for (xyz.daikirai.gui.onyx.LegacyText.Run run : runs) {
            total += style.width(run.text);
        }
        float cursor = align == ALIGN_CENTER ? x - total / 2.0F
                : align == ALIGN_RIGHT ? x - total : x;
        float shift = top ? style.ascent() / 2.0F : 0.0F;
        for (xyz.daikirai.gui.onyx.LegacyText.Run run : runs) {
            float runWidth = style.width(run.text);
            if (top) {
                textTop(style, run.text, cursor, y, run.color, ALIGN_LEFT);
            } else {
                text(style, run.text, cursor, y, run.color, ALIGN_LEFT);
            }
            if (run.strikethrough) {
                rect(cursor, y - 0.5F + shift, runWidth, 1.0F, run.color);
            }
            if (run.underline) {
                rect(cursor, y + style.ascent() * 0.5F + 1.0F + shift, runWidth, 1.0F, run.color);
            }
            cursor += runWidth;
        }
    }

    /**
     * Draws a string on an explicit baseline. The type scale's own vertical maths assumes a
     * 14px line box, which is meaningless for the 42px wallpaper clock, so oversized text is
     * positioned by its baseline instead.
     */
    public void textBaseline(Typeface.Style style, String text, float x, float baseline, int color, int align) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float width = style.width(text);
        if (align == ALIGN_CENTER) {
            x -= width / 2.0F;
        } else if (align == ALIGN_RIGHT) {
            x -= width;
        }
        draw(style, text, x, baseline, color);
    }

    private void draw(Typeface.Style style, String text, float x, float baseline, int color) {
        int c = applyAlpha(color);
        if (c >>> 24 == 0) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, baseline, 0.0F);
        float scale = style.scale();
        GlStateManager.scale(scale, scale, 1.0F);
        style.draw(text, c);
        GlStateManager.popMatrix();
    }

    /** Draws a Material Icons glyph centred on {@code x}, baseline-relative to {@code y}. */
    public void icon(String codepoint, float x, float y, float size, int color) {
        TtfFont icons = Typeface.ICONS;
        float scale = size / 16.0F;
        float width = icons.width(codepoint) * scale;
        int c = applyAlpha(color);
        if (c >>> 24 == 0) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate(x - width / 2.0F, y + scale, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        icons.draw(codepoint, c);
        GlStateManager.popMatrix();
    }

    public float width(Typeface.Style style, String text) {
        return style.width(text);
    }

    /** Shortens text with an ellipsis so it fits into {@code maxWidth}. */
    public String truncate(Typeface.Style style, String text, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.indexOf('\u00a7') >= 0) {
            // Keep the colour codes intact and measure only the visible glyphs.
            return xyz.daikirai.gui.onyx.LegacyText.truncate(style, text, maxWidth);
        }
        // A hair of tolerance: layout code arrives here with budgets built from the same widths, so
        // floating point rounding used to ellipsise text that fits exactly (Chinese labels in the
        // island lost their last glyph this way).
        if (style.width(text) <= maxWidth + 0.05F) {
            return text;
        }
        String ellipsis = "...";
        maxWidth -= style.width(ellipsis);
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (style.width(text.substring(0, mid)) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low) + ellipsis;
    }

    /** Wraps text into at most {@code maxLines} lines of {@code maxWidth}. */
    public java.util.List<String> wrap(Typeface.Style style, String text, float maxWidth, int maxLines) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (style.width(candidate) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                if (lines.size() == maxLines) {
                    current.setLength(0);
                    break;
                }
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (lines.size() < maxLines && current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private int applyAlpha(int color) {
        return Material.withAlpha(color, Material.alpha(color) / 255.0F * alpha);
    }

    /**
     * Alpha for filled surfaces (rounded rects, circles, shadows). Kept apart from
     * {@link #applyAlpha(int)} so a translucent panel fades while its labels stay readable.
     *
     * @param value 1 for opaque surfaces, lower for the translucent "glass" look
     */
    public void surfaceAlpha(float value) {
        this.surfaceAlpha = Material.clamp(value, 0.0F, 1.0F);
    }

    private int applySurfaceAlpha(int color) {
        int c = applyAlpha(color);
        if (surfaceAlpha >= 0.999F) {
            return c;
        }
        return Material.withAlpha(c, Material.alpha(c) / 255.0F * surfaceAlpha);
    }

    /** Convenience for drawing the current scheme's shadow colour. */
    public Scheme scheme() {
        return Theme.scheme();
    }
}
