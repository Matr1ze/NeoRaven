/*
 * Native texture/state handling adapted from the bundled Apache-2.0 M3 wavy renderer.
 * Copyright (C) 2020, 2024 The Android Open Source Project
 * Copyright 2026 M3 Wavy Minecraft adaptation contributors
 * SPDX-License-Identifier: Apache-2.0
 * Geometry and animation follow the user-provided m3-shape-morph.html.
 */
package xyz.daikirai.gui.onyx.render;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import xyz.daikirai.gui.onyx.material.Material;

/** Seven morphing outlines, arc-length segment chase and the HTML's 225-degree rotation whip. */
public final class M3ShapeMorphProgress implements AutoCloseable {
    private final ShapeMorphGeometry geometry = new ShapeMorphGeometry();
    private final Path2D.Double track = new Path2D.Double(), active = new Path2D.Double();
    private final BufferedImage glowMask = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB_PRE);
    private final BufferedImage glow = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB_PRE);
    private final Graphics2D glowGraphics = glowMask.createGraphics();
    private final int[] maskPixels = ((DataBufferInt)glowMask.getRaster().getDataBuffer()).getData();
    private final int[] glowPixels = ((DataBufferInt)glow.getRaster().getDataBuffer()).getData();
    private final float[] blurScratch = new float[96 * 96], kernel = gaussian();
    private final BasicStroke stroke = new BasicStroke(5.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private long lastNanos, lastUploadNanos = Long.MIN_VALUE;
    private int color, texture, texturePixels, uploads;
    private boolean dirty = true;
    private BufferedImage image;
    private Graphics2D graphics;
    private int[] pixels;
    private ByteBuffer upload;

    /** Draw inside UI.begin/end. Size is the path diameter; a transparent margin includes the glow. */
    public void draw(UI ui, float centerX, float centerY, float size, int primary) {
        long now = System.nanoTime();
        if (lastNanos == 0 || now - lastNanos > 500000000L) { geometry.reset(); dirty = true; }
        else geometry.advance((now - lastNanos) / 1e9);
        lastNanos = now;
        int ink = Material.withAlpha(primary, Material.alpha(primary) / 255.0F * ui.alpha());
        if (ink != color) { color = ink; dirty = true; }
        float extent = size * 1.5F;
        float x = centerX - extent / 2, y = centerY - extent / 2;
        size = extent;
        int side = Math.min(2048, Math.max(1, (int)Math.ceil(size * new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor() * 2)));
        Shader.unbind();
        int previousActive = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            // Disable other fixed-function texture units (including Minecraft lightmap).
            int units = GL11.glGetInteger(GL13.GL_MAX_TEXTURE_UNITS);
            for (int unit = 1; unit < units; unit++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            // Raster is PREMULTIPLIED; ONE avoids dark/bright antialias fringes.
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE);

            if (texture == 0) {
                texture = GL11.glGenTextures();
                dirty = true;
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (image == null || texturePixels != side) allocate(side);

            if (dirty || lastUploadNanos == Long.MIN_VALUE || now - lastUploadNanos >= 16_666_667L) {
                raster();
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, side, side,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
                lastUploadNanos = now;
                dirty = false;
            }
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + size);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + size, y + size);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + size, y);
            GL11.glEnd();
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL13.glActiveTexture(previousActive);
        }
    }

    private void allocate(int side) {
        if (graphics != null) graphics.dispose();
        texturePixels = side;
        lastUploadNanos = Long.MIN_VALUE;
        image = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB_PRE);
        graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        upload = BufferUtils.createByteBuffer(side * side * 4);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, side, side, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        dirty = true;
    }


    private void raster() {
        geometry.track(track, 48, 48, 32); geometry.active(active, 48, 48, 32);
        glowGraphics.setComposite(AlphaComposite.Clear); glowGraphics.fillRect(0, 0, 96, 96);
        glowGraphics.setComposite(AlphaComposite.SrcOver);
        glowGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        glowGraphics.setStroke(stroke); glowGraphics.setColor(Color.WHITE); glowGraphics.draw(active);
        // Blur only a small design-resolution glow mask; the outline itself is rasterized at 2x
        // physical resolution. This avoids a large per-frame convolution on high-DPI displays.
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
            float alpha = 0;
            for (int k = -6; k <= 6; k++) if (x + k >= 0 && x + k < 96)
                alpha += (maskPixels[y * 96 + x + k] >>> 24) * kernel[k + 6];
            blurScratch[y * 96 + x] = alpha;
        }
        int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
            float alpha = 0;
            for (int k = -6; k <= 6; k++) if (y + k >= 0 && y + k < 96)
                alpha += blurScratch[(y + k) * 96 + x] * kernel[k + 6];
            int a = Math.round(alpha * .38F * (color >>> 24) / 255);
            glowPixels[y * 96 + x] = (a << 24) | ((red * a / 255) << 16) | ((green * a / 255) << 8) | (blue * a / 255);
        }
        graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(0, 0, texturePixels, texturePixels);
        graphics.setComposite(AlphaComposite.SrcOver);
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.scale(texturePixels / 96.0, texturePixels / 96.0);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setStroke(stroke);
            g.setColor(new Color((color & 0xFFFFFF) | (Math.round((color >>> 24) * .13F) << 24), true));
            g.draw(track); g.drawImage(glow, 0, 0, null);
            g.setColor(new Color(color, true)); g.draw(active);
        } finally { g.dispose(); }
        upload.clear();
        for (int p : pixels) upload.put((byte)(p >>> 16)).put((byte)(p >>> 8)).put((byte)p).put((byte)(p >>> 24));
        upload.flip(); uploads++;
    }
    private static float[] gaussian() {
        float[] weights = new float[13]; float sum = 0;
        for (int i = -6; i <= 6; i++) { weights[i + 6] = (float)Math.exp(-i * i / 8.0); sum += weights[i + 6]; }
        for (int i = 0; i < weights.length; i++) weights[i] /= sum;
        return weights;
    }
    /** Release texture while the GL context is current. Safe to call again. */
    @Override public void close() {
        if (texture != 0) {
            GL11.glDeleteTextures(texture);
            texture = 0;
        }
        if (graphics != null) graphics.dispose();
        graphics = null;
        image = null;
        pixels = null;
        upload = null;
        texturePixels = 0;
        lastNanos = 0;
        dirty = true;
    }
}
