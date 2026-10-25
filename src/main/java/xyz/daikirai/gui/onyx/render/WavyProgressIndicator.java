package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;

/**
 * Material 3 Expressive style wavy progress indicators for Minecraft's immediate-mode GUI.
 *
 * <p>Call these methods between {@link UI#begin()} and {@link UI#end()}. Progress is a fraction
 * from {@code 0} to {@code 1}; the wave grows in as progress starts, is strongest through the
 * middle, and settles into a straight fill near completion. The methods are stateless, so they
 * can be drawn from any GUI or HUD render callback.</p>
 */
public final class WavyProgressIndicator {

    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float DEFAULT_WAVELENGTH = 24.0F;
    private static final float DEFAULT_AMPLITUDE = 1.0F;
    private static final float DEFAULT_SPEED = DEFAULT_WAVELENGTH;

    private static Shader shader;
    private static boolean shaderFailed;

    private WavyProgressIndicator() {
    }

    /** Wave settings and colors. Amplitude is a 0..1 fraction; speed is measured in GUI px/sec. */
    public static final class Style {
        public final float wavelength;
        public final float amplitude;
        public final float speed;
        public final int indicatorColor;
        public final int trackColor;

        public Style(float wavelength, float amplitude, float speed,
                     int indicatorColor, int trackColor) {
            this.wavelength = Float.isNaN(wavelength) || Float.isInfinite(wavelength)
                    ? DEFAULT_WAVELENGTH : Math.max(4.0F, wavelength);
            this.amplitude = Float.isNaN(amplitude) || Float.isInfinite(amplitude)
                    ? DEFAULT_AMPLITUDE : Material.clamp(amplitude, 0.0F, 1.0F);
            this.speed = Float.isNaN(speed) || Float.isInfinite(speed) ? DEFAULT_SPEED : speed;
            this.indicatorColor = indicatorColor;
            this.trackColor = trackColor;
        }

        /** Uses the current Onyx Material palette. */
        public static Style material() {
            return new Style(DEFAULT_WAVELENGTH, DEFAULT_AMPLITUDE, DEFAULT_SPEED,
                    Theme.scheme().primary, Theme.scheme().primaryContainer);
        }
    }

    /** Draws a determinate horizontal indicator inside the supplied bounds. */
    public static void drawLinear(UI ui, float x, float y, float width, float height,
                                  float progress) {
        drawLinear(ui, x, y, width, height, progress, Style.material());
    }

    /** Draws a determinate horizontal indicator with custom wave settings and colors. */
    public static void drawLinear(UI ui, float x, float y, float width, float height,
                                  float progress, Style style) {
        if (!valid(ui, width, height)) {
            return;
        }
        style = style == null ? Style.material() : style;
        float value = unit(progress);
        float stroke = linearStroke(height);
        float amplitude = style.amplitude * Math.max(0.0F, (height - stroke) * 0.5F);
        float trackY = y + (height - stroke) * 0.5F;
        if (value <= 0.0F) {
            ui.rounded(x, trackY, width, stroke, stroke * 0.5F, style.trackColor);
            return;
        }
        if (value >= 1.0F) {
            boolean drawn = drawWave(ui, x, y, width, height, false, 0.0F, 1.0F,
                    0.0F, stroke, 0.0F, style, phase(style));
            if (!drawn) {
                ui.rounded(x, trackY, width, stroke, stroke * 0.5F, style.indicatorColor);
            }
            return;
        }

        float waveAmplitude = amplitude * determinateAmplitude(value);
        float wavePhase = phase(style);
        boolean drawn = drawWave(ui, x, y, width, height, false, 0.0F, value,
                0.0F, stroke, waveAmplitude, style, wavePhase);
        float trackStartX;
        if (!drawn) {
            float fillWidth = Math.max(0.0F, width * value);
            ui.rounded(x, trackY, fillWidth, stroke,
                    stroke * 0.5F, style.indicatorColor);
            trackStartX = x + fillWidth;
        } else {
            // The wavy cap's rightmost point is where the straight, unplayed track begins.
            trackStartX = x + stroke + value * (width - stroke);
        }
        float remaining = x + width - trackStartX;
        if (remaining > 0.0F) {
            ui.rounded(trackStartX, trackY, remaining, stroke,
                    stroke * 0.5F, style.trackColor);
        }
    }

    /** Draws an animated, indeterminate horizontal indicator. */
    public static void drawLinearIndeterminate(UI ui, float x, float y,
                                               float width, float height) {
        drawLinearIndeterminate(ui, x, y, width, height, Style.material());
    }

    /** Draws an animated, indeterminate horizontal indicator with custom settings. */
    public static void drawLinearIndeterminate(UI ui, float x, float y,
                                               float width, float height, Style style) {
        if (!valid(ui, width, height)) {
            return;
        }
        style = style == null ? Style.material() : style;
        float stroke = linearStroke(height);
        float amplitude = style.amplitude * Math.max(0.0F, (height - stroke) * 0.5F);
        float trackY = y + (height - stroke) * 0.5F;

        float t = cycle((float) (seconds() * 0.34F), 2.0F);
        float travel = t < 1.0F ? t : 2.0F - t;
        travel = travel * travel * (3.0F - 2.0F * travel);
        float segment = 0.18F + 0.24F * (0.5F + 0.5F * (float) Math.sin(seconds() * 1.8F));
        float start = travel * (1.0F - segment);
        ui.rounded(x, trackY, width, stroke, stroke * 0.5F, style.trackColor);
        boolean drawn = drawWave(ui, x, y, width, height, false, start, start + segment,
                0.0F, stroke, amplitude, style, phase(style));
        if (!drawn) {
            float fillWidth = width * segment;
            ui.rounded(x + width * start, trackY,
                    fillWidth, stroke, stroke * 0.5F, style.indicatorColor);
        }
    }

    /** Draws a determinate circular indicator. The diameter includes the complete wavy track. */
    public static void drawCircular(UI ui, float centerX, float centerY, float diameter,
                                    float progress) {
        float stroke = Math.min(4.0F, Math.max(1.0F, diameter * 0.12F));
        drawCircular(ui, centerX, centerY, diameter, stroke, progress, Style.material());
    }

    /** Draws a determinate circular indicator with custom stroke, wave settings, and colors. */
    public static void drawCircular(UI ui, float centerX, float centerY, float diameter,
                                    float stroke, float progress, Style style) {
        if (!valid(ui, diameter, diameter) || stroke <= 0.0F) {
            return;
        }
        style = style == null ? Style.material() : style;
        float value = unit(progress);
        stroke = Math.min(stroke, diameter * 0.25F);
        float outer = diameter * 0.5F;
        float maxAmplitude = Math.min(Math.max(0.0F, (outer - stroke) * 0.5F), diameter * 0.10F);
        float amplitude = style.amplitude * maxAmplitude * determinateAmplitude(value);
        float baseRadius = Math.max(stroke, outer - stroke * 0.5F - maxAmplitude);

        float trackRadius = baseRadius + stroke * 0.5F;
        ui.outline(centerX - trackRadius, centerY - trackRadius, trackRadius * 2.0F,
                trackRadius * 2.0F, trackRadius, stroke, style.trackColor);
        if (value <= 0.0F) {
            return;
        }
        if (value >= 1.0F) {
            ui.outline(centerX - trackRadius, centerY - trackRadius,
                    trackRadius * 2.0F, trackRadius * 2.0F,
                    trackRadius, stroke, style.indicatorColor);
            return;
        }

        boolean drawn = drawWave(ui, centerX - outer, centerY - outer, diameter, diameter,
                true, 0.0F, value, baseRadius, stroke, amplitude, style, phase(style));
        if (!drawn) {
            drawFallbackArc(ui, centerX, centerY, baseRadius, stroke, 0.0F, value,
                    style.indicatorColor);
        }
    }

    /** Draws an animated, indeterminate circular indicator. */
    public static void drawCircularIndeterminate(UI ui, float centerX, float centerY,
                                                 float diameter) {
        float stroke = Math.min(4.0F, Math.max(1.0F, diameter * 0.12F));
        drawCircularIndeterminate(ui, centerX, centerY, diameter, stroke, Style.material());
    }

    /** Draws an animated, indeterminate circular indicator with custom settings. */
    public static void drawCircularIndeterminate(UI ui, float centerX, float centerY,
                                                 float diameter, float stroke, Style style) {
        if (!valid(ui, diameter, diameter) || stroke <= 0.0F) {
            return;
        }
        style = style == null ? Style.material() : style;
        stroke = Math.min(stroke, diameter * 0.25F);
        float outer = diameter * 0.5F;
        float maxAmplitude = Math.min(Math.max(0.0F, (outer - stroke) * 0.5F), diameter * 0.10F);
        float amplitude = style.amplitude * maxAmplitude;
        float baseRadius = Math.max(stroke, outer - stroke * 0.5F - maxAmplitude);

        float trackRadius = baseRadius + stroke * 0.5F;
        ui.outline(centerX - trackRadius, centerY - trackRadius, trackRadius * 2.0F,
                trackRadius * 2.0F, trackRadius, stroke, style.trackColor);
        double time = seconds();
        float start = (float) ((time * 0.18F) % 1.0F);
        float sweep = 0.20F + 0.16F * (0.5F + 0.5F * (float) Math.sin(time * 1.7F));
        boolean drawn = drawWave(ui, centerX - outer, centerY - outer, diameter, diameter,
                true, start, sweep, baseRadius, stroke, amplitude, style, phase(style));
        if (!drawn) {
            drawFallbackArc(ui, centerX, centerY, baseRadius, stroke, start, sweep,
                    style.indicatorColor);
        }
    }

    private static boolean drawWave(UI ui, float x, float y, float width, float height,
                                   boolean circular, float start, float end, float baseRadius,
                                   float stroke, float amplitude, Style style, float phase) {
        if (ui.alpha() <= 0.0F || (!circular && end <= start) || (circular && end <= 0.0F)) {
            return false;
        }
        Shader wave = shader();
        if (wave == null || !wave.valid()) {
            return false;
        }

        float alpha = Material.alpha(style.indicatorColor) / 255.0F * ui.alpha();
        wave.use();
        wave.uniform2f("uHalfExtents", width * 0.5F, height * 0.5F);
        wave.uniform1i("uCircular", circular ? 1 : 0);
        wave.uniform1f("uStart", start);
        wave.uniform1f("uEnd", end);
        wave.uniform1f("uBaseRadius", baseRadius);
        wave.uniform1f("uThickness", stroke);
        wave.uniform1f("uAmplitude", amplitude);
        wave.uniform1f("uWavelength", style.wavelength);
        wave.uniform1f("uPhase", phase);

        int color = style.indicatorColor;
        GlStateManager.disableTexture2D();
        GL11.glColor4f(Material.red(color) / 255.0F, Material.green(color) / 255.0F,
                Material.blue(color) / 255.0F, alpha);
        float centerX = x + width * 0.5F;
        float centerY = y + height * 0.5F;
        GL11.glBegin(GL11.GL_QUADS);
        vertex(x, y, centerX, centerY);
        vertex(x, y + height, centerX, centerY);
        vertex(x + width, y + height, centerX, centerY);
        vertex(x + width, y, centerX, centerY);
        GL11.glEnd();
        Shader.unbind();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
        return true;
    }

    private static void vertex(float x, float y, float centerX, float centerY) {
        GL11.glTexCoord2f(x - centerX, y - centerY);
        GL11.glVertex2f(x, y);
    }

    private static void drawFallbackArc(UI ui, float centerX, float centerY, float radius,
                                        float stroke, float start, float sweep, int color) {
        int segments = Math.max(8, (int) Math.ceil(Math.abs(sweep) * Math.PI * 2.0 * radius / 2.0));
        float a0 = start * TAU - (float) (Math.PI * 0.5);
        float span = sweep * TAU;
        int alphaColor = Material.scaleAlpha(color, ui.alpha());
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = a0 + span * t;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            setColor(alphaColor);
            GL11.glVertex2f(centerX + cos * (radius + stroke * 0.5F),
                    centerY + sin * (radius + stroke * 0.5F));
            setColor(alphaColor);
            GL11.glVertex2f(centerX + cos * (radius - stroke * 0.5F),
                    centerY + sin * (radius - stroke * 0.5F));
        }
        GL11.glEnd();
        resetColor();
    }

    private static void setColor(int color) {
        GL11.glColor4f(Material.red(color) / 255.0F, Material.green(color) / 255.0F,
                Material.blue(color) / 255.0F, Material.alpha(color) / 255.0F);
    }

    private static void resetColor() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.resetColor();
    }

    private static float linearStroke(float height) {
        return Math.min(height, Math.max(1.0F, Math.min(4.0F, height * 0.36F)));
    }

    private static float determinateAmplitude(float progress) {
        if (progress <= 0.1F || progress >= 0.95F) {
            return 0.0F;
        }
        if (progress < 0.14F) {
            return (progress - 0.1F) / 0.04F;
        }
        if (progress > 0.9F) {
            return (0.95F - progress) / 0.05F;
        }
        return 1.0F;
    }

    private static float unit(float value) {
        return Float.isNaN(value) ? 0.0F : Material.clamp(value, 0.0F, 1.0F);
    }

    private static float phase(Style style) {
        double value = seconds() * style.speed / style.wavelength * TAU;
        return (float) (value % TAU);
    }

    private static double seconds() {
        return System.nanoTime() / 1.0E9;
    }

    private static float cycle(float value, float length) {
        float result = value % length;
        return result < 0.0F ? result + length : result;
    }

    private static boolean valid(UI ui, float width, float height) {
        return ui != null && width > 0.0F && height > 0.0F
                && !Float.isNaN(width) && !Float.isNaN(height)
                && !Float.isInfinite(width) && !Float.isInfinite(height);
    }

    private static Shader shader() {
        if (shader == null && !shaderFailed) {
            shader = Shader.load("onyx_wavy_progress", "onyx_shape");
            shaderFailed = !shader.valid();
        }
        return shader;
    }
}
