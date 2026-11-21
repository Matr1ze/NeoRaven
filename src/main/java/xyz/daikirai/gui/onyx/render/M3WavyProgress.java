/*
 * Copyright (C) 2020, 2024 The Android Open Source Project
 * Copyright 2026 M3 Wavy Minecraft adaptation contributors
 * SPDX-License-Identifier: Apache-2.0
 * See NOTICE and LICENSE for upstream attribution.
 */
package xyz.daikirai.gui.onyx.render;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/**
 * Material 3 Expressive Wavy circular progress for Minecraft 1.8.9 / LWJGL 2.
 * Modified for NeoRaven: package name, unchanged setter guards and 60 Hz upload scheduling.
 * Call draw and close on the Minecraft render thread, outside Tessellator.begin/draw.
 * All setters also belong on that thread. Coordinates are Minecraft GUI units.
 * Uses Java2D antialiasing and a reusable premultiplied-alpha GL texture.
 */
public final class M3WavyProgress implements AutoCloseable {
    public static final float DEFAULT_SIZE = 48;
    private final M3WavyShape shape = new M3WavyShape();
    private boolean indeterminate = true;
    private boolean motionEnabled = true;
    private int primary = 0xFF6750A4;
    private int secondaryContainer = 0xFFE8DEF8;
    private double progress, target, velocity;
    private double amplitudePlayhead;
    private boolean amplitudeOn;
    private long lastNanos = System.nanoTime(), epochNanos = lastNanos;
    private long lastUploadNanos = Long.MIN_VALUE;
    private boolean dirty = true;
    private int texture, texturePixels;
    private BufferedImage image;
    private Graphics2D graphics;
    private int[] pixels;
    private ByteBuffer upload;

    /** Supply your theme's colorPrimary and colorSecondaryContainer as ARGB. */
    public void setColors(int primaryArgb, int secondaryContainerArgb) {
        if (primary == primaryArgb && secondaryContainer == secondaryContainerArgb) return;
        primary = primaryArgb;
        secondaryContainer = secondaryContainerArgb;
        dirty = true;
    }

    /** Stroke width in the component's 48-unit design space; all geometry stays inside its bounds. */
    public void setThickness(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Thickness must be finite");
        if (shape.setThickness(value)) dirty = true;
    }

    public void setIndeterminate(boolean value) {
        advance(System.nanoTime());
        if (indeterminate != value) {
            indeterminate = value;
            epochNanos = System.nanoTime();
            dirty = true;
        }
    }

    public boolean isIndeterminate() { return indeterminate; }

    /** Set 0..1; switches to determinate. Default: critically damped spring. */
    public void setProgress(float value) { setProgress(value, true); }

    /** Pass animated=false for an immediate update. NaN/infinity are rejected. */
    public void setProgress(float value, boolean animated) {
        if (Float.isNaN(value) || Float.isInfinite(value)) throw new IllegalArgumentException("Progress must be finite");
        if (!indeterminate && target == M3WavyShape.clamp(value) && animated) return;
        advance(System.nanoTime());
        indeterminate = false;
        target = M3WavyShape.clamp(value);
        if (!animated || !motionEnabled) {
            progress = target;
            velocity = 0;
            amplitudeOn = progress >= 0.1 && progress <= 0.9;
            amplitudePlayhead = amplitudeOn ? 1 : 0;
        }
        dirty = true;
    }

    /** Optional application-level reduced-motion setting. */
    public void setMotionEnabled(boolean enabled) {
        advance(System.nanoTime());
        motionEnabled = enabled;
        epochNanos = System.nanoTime();
        if (!enabled) {
            progress = target;
            velocity = 0;
            amplitudeOn = progress >= 0.1 && progress <= 0.9;
            amplitudePlayhead = amplitudeOn ? 1 : 0;
        }
        dirty = true;
    }

    public void draw(float x, float y, int guiScaleFactor) {
        draw(x, y, DEFAULT_SIZE, guiScaleFactor);
    }

    /**
     * size uniformly scales the entire 48dp component, including stroke/gap.
     * guiScaleFactor = new ScaledResolution(mc).getScaleFactor().
     * The caller must have the usual GuiScreen orthographic matrices installed.
     */
    public void draw(float x, float y, float size, int guiScaleFactor) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(size)
                || size <= 0 || guiScaleFactor < 1) throw new IllegalArgumentException("Invalid GUI bounds/scale");
        long now = System.nanoTime();
        advance(now);
        // 2x physical-resolution supersampling; cap prevents accidental huge allocations.
        int side = (int) Math.ceil(size * (double) guiScaleFactor * 2);
        if (side < 1 || side > 2048) throw new IllegalArgumentException("Raster side must be 1..2048 pixels");

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
            boolean moving = motionEnabled && (indeterminate || Math.abs(progress - target) > 1e-6
                    || Math.abs(velocity) > 1e-6 || amplitudePlayhead > 0 && amplitudePlayhead < 1);
            if ((dirty || moving) && (lastUploadNanos == Long.MIN_VALUE || now - lastUploadNanos >= 16_666_667L)) {
                raster(now);
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

    private void raster(long now) {
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, texturePixels, texturePixels);
        graphics.setComposite(AlphaComposite.SrcOver);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.scale(texturePixels / M3WavyShape.SIZE, texturePixels / M3WavyShape.SIZE);
            double seconds = motionEnabled ? (now - epochNanos) / 1e9 : 0;
            double amplitude = amplitudeOn ? M3WavyShape.standard(amplitudePlayhead)
                    : M3WavyShape.accelerate(amplitudePlayhead);
            shape.paint(g, seconds, progress, indeterminate, amplitude, primary, secondaryContainer);
        } finally {
            g.dispose();
        }
        upload.clear();
        for (int p : pixels) {
            upload.put((byte) (p >>> 16)).put((byte) (p >>> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        upload.flip();
    }

    private void advance(long now) {
        double dt = Math.max(0, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!motionEnabled || indeterminate) return;
        double before = progress;
        double oldAmplitude = amplitudePlayhead;
        // Exact solution: mass=1, stiffness=50, damping ratio=1; Android VERY_LOW/NO_BOUNCY.
        double omega = Math.sqrt(50);
        double offset = progress - target;
        double c = velocity + omega * offset;
        double decay = Math.exp(-omega * dt);
        progress = target + (offset + c * dt) * decay;
        velocity = (velocity - omega * c * dt) * decay;
        if (Math.abs(progress - target) < 1e-7 && Math.abs(velocity) < 1e-7) {
            progress = target;
            velocity = 0;
        }
        progress = M3WavyShape.clamp(progress);
        amplitudeOn = progress >= 0.1 && progress <= 0.9;
        amplitudePlayhead = M3WavyShape.clamp(amplitudePlayhead + (amplitudeOn ? 1 : -1) * dt / 0.5);
        if (before != progress || oldAmplitude != amplitudePlayhead) dirty = true;
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
        dirty = true;
    }
}
