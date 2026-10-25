package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;

/**
 * Material 3 Expressive loading indicator.
 *
 * <p>Per the M3 Expressive spec the indeterminate loader is a <em>filled shape that morphs</em>
 * between the Material forms while it rotates - circle, nine-sided "cookie", rounded square - rather
 * than the older growing/shrinking arc. The forms are drawn through {@code m3_loading.fsh}, a signed
 * distance field, so every intermediate shape (and the rotation) stays perfectly anti-aliased without
 * tessellating a polygon in Java.</p>
 *
 * <p>Timing follows the reference implementation: one shape-to-shape morph per {@value #MORPH_MS} ms
 * with the emphasized easing, one full turn per {@value #SPIN_MS} ms, and a small breathing pulse
 * tied to the morph so the shape settles instead of snapping.</p>
 *
 * <p>Draw between {@link UI#begin()} and {@link UI#end()}; the indicator is stateless, so any GUI or
 * HUD callback can call it.</p>
 */
public final class M3LoadingIndicator {

    /** Duration of one shape transition. */
    public static final float MORPH_MS = 900.0F;
    /** Duration of one full rotation, i.e. two morphs. */
    public static final float SPIN_MS = 1800.0F;

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final long START = System.nanoTime();
    private static Shader shader;
    private static boolean shaderFailed;

    /**
     * The three forms, as {sides, apothem, corner rounding, circle blend} fractions of the radius.
     *
     * <p>Each form keeps {@code apothem + rounding = 1}, so all three share the same bounding box and
     * the morph never changes the visual mass. The rounded square's corners reach out to
     * {@code apothem * sqrt(2) + rounding = 1.29R}, which is why the quad is drawn at 1.4R.</p>
     */
    private static final float[][] FORMS = {
            // Circle: a polygon whose corners are fully blended away.
            {9.0F, 0.68F, 0.32F, 1.0F},
            // Cookie: the nine-sided soft burst M3 uses as the "expressive" mid form. The corners are
            // wider than the facets, so it reads as a circle with nine soft lobes.
            {9.0F, 0.68F, 0.32F, 0.0F},
            // Rounded square: four sides with generous corners.
            {4.0F, 0.72F, 0.28F, 0.0F},
    };

    /** Quad half-size relative to the indicator radius; covers the square form's diagonal plus AA. */
    private static final float QUAD = 1.4F;

    private M3LoadingIndicator() {
    }

    /** Draws the indicator with the current theme's primary colour. */
    public static void draw(UI ui, float centerX, float centerY, float size) {
        draw(ui, centerX, centerY, size, Theme.scheme().primary);
    }

    /**
     * Draws the indicator.
     *
     * @param size outer diameter in GUI units (M3's default is 48dp)
     */
    public static void draw(UI ui, float centerX, float centerY, float size, int color) {
        if (ui == null || size <= 0.0F || Float.isNaN(size) || Float.isInfinite(size)) {
            return;
        }
        Shader shader = shader();
        if (shader == null || !shader.valid()) {
            // No shader: a plain disc is still a better placeholder than nothing.
            ui.circle(centerX, centerY, size * 0.34F, color);
            return;
        }

        float milliseconds = (System.nanoTime() - START) / 1.0E6F;
        float cycles = milliseconds / MORPH_MS;
        int index = (int) Math.floor(cycles);
        float eased = Ease.EMPHASIZED.get(cycles - index);
        float[] from = FORMS[Math.floorMod(index, FORMS.length)];
        float[] to = FORMS[Math.floorMod(index + 1, FORMS.length)];

        float radius = size * 0.5F;
        float circle = lerp(from[3], to[3], eased);
        float rotation = milliseconds / SPIN_MS * TAU;
        // Breathe with the morph: the shape pulls in slightly while it changes form, then settles.
        float scale = 1.0F - 0.05F * (1.0F - eased);

        int ink = Material.withAlpha(color, Material.alpha(color) / 255.0F * ui.alpha());
        float half = radius * QUAD;
        float r = Material.red(ink) / 255.0F;
        float g = Material.green(ink) / 255.0F;
        float b = Material.blue(ink) / 255.0F;
        float a = Material.alpha(ink) / 255.0F;

        shader.use();
        shader.uniform1f("uPixel", pixel());
        shader.uniform1f("uSidesA", from[0]);
        shader.uniform1f("uApothemA", from[1] * radius);
        shader.uniform1f("uRoundA", from[2] * radius);
        shader.uniform1f("uSidesB", to[0]);
        shader.uniform1f("uApothemB", to[1] * radius);
        shader.uniform1f("uRoundB", to[2] * radius);
        shader.uniform1f("uMorph", eased);
        shader.uniform1f("uCircle", circle);
        shader.uniform1f("uRadius", radius);
        shader.uniform1f("uRotation", rotation);
        shader.uniform1f("uScale", scale);

        GlStateManager.disableTexture2D();
        GL11.glBegin(GL11.GL_QUADS);
        vertex(centerX - half, centerY - half, centerX, centerY, r, g, b, a);
        vertex(centerX - half, centerY + half, centerX, centerY, r, g, b, a);
        vertex(centerX + half, centerY + half, centerX, centerY, r, g, b, a);
        vertex(centerX + half, centerY - half, centerX, centerY, r, g, b, a);
        GL11.glEnd();
        Shader.unbind();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
    }

    private static void vertex(float x, float y, float centerX, float centerY,
                               float r, float g, float b, float a) {
        GL11.glColor4f(r, g, b, a);
        GL11.glTexCoord2f(x - centerX, y - centerY);
        GL11.glVertex2f(x, y);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /** One screen pixel expressed in GUI units, for the minimum edge feather. */
    private static float pixel() {
        Minecraft mc = Minecraft.getMinecraft();
        int factor = mc == null ? 1 : new ScaledResolution(mc).getScaleFactor();
        return 1.0F / Math.max(1, factor);
    }

    /**
     * Whether the morph shader compiled. The dev harness asserts this so a silent fallback (a plain
     * disc) cannot pass as the indicator.
     */
    public static boolean ready() {
        Shader current = shader();
        return current != null && current.valid();
    }

    private static Shader shader() {
        if (shader == null && !shaderFailed) {
            shader = Shader.load("m3_loading", "onyx_shape");
            shaderFailed = !shader.valid();
        }
        return shader;
    }
}
