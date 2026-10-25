package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * TrueType font renderer with a mip-mapped alpha atlas, ported from Onyx's {@code render.font.Ayg}.
 *
 * <p>Glyphs are rasterised at up to 192px through java.awt, packed into an atlas with the same
 * 8px margin the original used and simply downscaled by a matrix for smaller sizes. Keeping the
 * rasteriser, margin and scale identical is what makes the Material type metrics line up with
 * the reference client.</p>
 */
public final class TtfFont {

    private static final int PADDING = 8;
    private static final int MIP_LEVELS = 4;
    private static final Glyph EMPTY = new Glyph(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    private static final Glyph NO_ATLAS = new Glyph(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    private final Map<Character, Glyph> glyphs = new HashMap<Character, Glyph>();
    private final Map<Character, Float> advances = new HashMap<Character, Float>();
    private final FontRenderContext renderContext = new FontRenderContext(null, true, true);
    private final Font font;
    private final float size;
    private final float scale;
    private final int atlasSize;

    private float ascent;
    private float descent;
    private int texture = -1;
    private int penX;
    private int penY;
    private int rowHeight;
    private boolean full;

    private TtfFont(Font font, float size) {
        this.size = size;
        float raster = Math.min(size * 4.0F, 192.0F);
        LineMetrics metrics = font.deriveFont(100.0F).getLineMetrics("Ayg", renderContext);
        float nominal = metrics.getAscent() + metrics.getDescent();
        float ratio = nominal <= 0.0F ? 1.0F : 100.0F / nominal;
        this.font = font.deriveFont(raster * ratio);
        this.scale = size / raster;

        int pad = (int) raster + 16;
        this.atlasSize = Math.min(2048, Math.max(512, Integer.highestOneBit(pad * 12 - 1) * 2));

        float capHeight = -pixelBounds('H').y * this.scale;
        float descender = (pixelBounds('p').y + pixelBounds('p').height) * this.scale;
        this.ascent = capHeight > 0.0F ? capHeight : size * 0.72F;
        this.descent = descender > 0.0F ? descender : size * 0.21F;
    }

    /** Loads a TTF from the classpath, falling back to a plain dialog font when missing. */
    public static TtfFont load(String resource, float size) {
        try {
            InputStream in = TtfFont.class.getResourceAsStream(resource);
            if (in == null) {
                throw new IOException("Missing font " + resource);
            }
            try {
                return new TtfFont(Font.createFont(Font.TRUETYPE_FONT, in), size);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            System.err.println("[onyx] failed to load font '" + resource + "': " + e);
            return new TtfFont(new Font("SansSerif", Font.PLAIN, 1), size);
        }
    }

    /** Loads a TTF from disk; returns {@code null} when the file cannot be read. */
    public static TtfFont loadFile(java.io.File file, float size) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            return new TtfFont(Font.createFont(Font.TRUETYPE_FONT, in), size);
        } catch (Exception e) {
            System.err.println("[onyx] failed to load system font " + file + ": " + e);
            return null;
        }
    }

    public float size() {
        return size;
    }

    /** Distance from the top of a line to the baseline, in the requested pixel size. */
    public float ascent() {
        return ascent;
    }

    /** Distance from the baseline to the bottom of a line, in the requested pixel size. */
    public float descent() {
        return descent;
    }

    public float lineHeight() {
        return ascent + descent;
    }

    /** Width of a string at this font's pixel size. */
    public float width(String text) {
        float width = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            width += advance(text.charAt(i));
        }
        return width;
    }

    public boolean has(char c) {
        return font.canDisplay(c);
    }

    /** Measures without allocating or uploading a glyph atlas. */
    public float advance(char c) {
        Float advance = advances.get(Character.valueOf(c));
        if (advance == null) {
            advance = Float.valueOf(font.canDisplay(c)
                    ? font.createGlyphVector(renderContext, new char[]{c}).getGlyphMetrics(0).getAdvanceX() * scale
                    : 0.0F);
            advances.put(Character.valueOf(c), advance);
        }
        return advance.floatValue();
    }

    public Glyph glyph(char c) {
        Glyph glyph = glyphs.get(Character.valueOf(c));
        if (glyph == null) {
            glyph = createGlyph(c);
            glyphs.put(Character.valueOf(c), glyph);
        }
        return glyph;
    }

    /** Binds the prepared atlas for world-space glyph quads. */
    public void bind() {
        ensureTexture();
        GlStateManager.bindTexture(texture);
    }

    private Rectangle pixelBounds(char c) {
        return font.createGlyphVector(renderContext, new char[]{c}).getPixelBounds(renderContext, 0.0F, 0.0F);
    }

    private Glyph createGlyph(char c) {
        if (!font.canDisplay(c)) {
            return EMPTY;
        }
        GlyphVector vector = font.createGlyphVector(renderContext, new char[]{c});
        float advance = advance(c);
        Rectangle bounds = vector.getPixelBounds(renderContext, 0.0F, 0.0F);

        if (bounds.width <= 0 || bounds.height <= 0) {
            return new Glyph(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, advance);
        }

        int width = bounds.width + PADDING * 2;
        int height = bounds.height + PADDING * 2;
        if (width > atlasSize || height > atlasSize) {
            return NO_ATLAS;
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setColor(Color.WHITE);
        graphics.setFont(font);
        graphics.drawGlyphVector(vector, PADDING - bounds.x, PADDING - bounds.y);
        graphics.dispose();

        int[] slot = allocate(width, height);
        if (slot == null) {
            return NO_ATLAS;
        }
        upload(image, slot[0], slot[1], width, height);

        return new Glyph(
                (float) slot[0] / atlasSize,
                (float) slot[1] / atlasSize,
                (float) (slot[0] + width) / atlasSize,
                (float) (slot[1] + height) / atlasSize,
                (bounds.x - PADDING) * scale,
                (bounds.y - PADDING) * scale,
                width * scale,
                height * scale,
                advance);
    }

    private static int align(int value) {
        return value + 8 - 1 & -8;
    }

    private int[] allocate(int width, int height) {
        if (full) {
            return null;
        }
        int w = align(width);
        int h = align(height);
        if (penX + w > atlasSize) {
            penX = 0;
            penY += rowHeight;
            rowHeight = 0;
        }
        if (penY + h > atlasSize) {
            full = true;
            System.err.println("[onyx] glyph atlas full at " + atlasSize + "px");
            return null;
        }
        int[] slot = {penX, penY};
        penX += w;
        rowHeight = Math.max(rowHeight, h);
        return slot;
    }

    private void ensureTexture() {
        if (texture != -1) {
            return;
        }
        texture = TextureUtil.glGenTextures();
        GlStateManager.bindTexture(texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12_MAX_LEVEL, MIP_LEVELS - 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        for (int level = 0; level < MIP_LEVELS; level++) {
            int dimension = atlasSize >> level;
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_ALPHA, dimension, dimension, 0,
                    GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, BufferUtils.createByteBuffer(dimension * dimension));
        }
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private static final int GL12_MAX_LEVEL = 0x813D;

    private void upload(BufferedImage image, int x, int y, int width, int height) {
        ensureTexture();

        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        byte[] alpha = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            alpha[i] = (byte) (pixels[i] >>> 24);
        }

        GlStateManager.bindTexture(texture);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        int w = width;
        int h = height;
        for (int level = 0; level < MIP_LEVELS; level++) {
            uploadLevel(level, x >> level, y >> level, w, h, alpha);
            if (level == MIP_LEVELS - 1) {
                break;
            }
            int nextW = Math.max(1, w >> 1);
            int nextH = Math.max(1, h >> 1);
            alpha = downsample(alpha, w, h, nextW, nextH);
            w = nextW;
            h = nextH;
        }
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private void uploadLevel(int level, int x, int y, int width, int height, byte[] data) {
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height);
        buffer.put(data, 0, width * height).flip();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, x, y, width, height,
                GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, buffer);
    }

    private static byte[] downsample(byte[] source, int width, int height, int targetWidth, int targetHeight) {
        byte[] result = new byte[targetWidth * targetHeight];
        for (int y = 0; y < targetHeight; y++) {
            int s0 = Math.min(y * 2, height - 1);
            int s1 = Math.min(y * 2 + 1, height - 1);
            for (int x = 0; x < targetWidth; x++) {
                int c0 = Math.min(x * 2, width - 1);
                int c1 = Math.min(x * 2 + 1, width - 1);
                int sum = (source[s0 * width + c0] & 0xFF)
                        + (source[s0 * width + c1] & 0xFF)
                        + (source[s1 * width + c0] & 0xFF)
                        + (source[s1 * width + c1] & 0xFF);
                result[y * targetWidth + x] = (byte) (sum >> 2);
            }
        }
        return result;
    }

    /** Draws a string at the current matrix origin (baseline at {@code 7 * scale}). */
    public void draw(String text, int color) {
        if (text == null || text.isEmpty() || color >>> 24 == 0) {
            return;
        }
        try {
            for (int i = 0; i < text.length(); i++) {
                glyph(text.charAt(i));
            }
            if (texture == -1) {
                return;
            }
            // Toggle through GlStateManager so a cap left behind by raw glEnable/glDisable calls
            // cannot leave the glyphs untextured (which draws solid blocks instead of letters).
            GlStateManager.disableTexture2D();
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(texture);
            GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F, (color >>> 24) / 255.0F);
            GL11.glBegin(GL11.GL_QUADS);
            float caret = 0.0F;
            for (int i = 0; i < text.length(); i++) {
                Glyph glyph = glyph(text.charAt(i));
                if (!glyph.isEmpty()) {
                    float x0 = caret + glyph.bearingX;
                    float y0 = PADDING - 1 + glyph.bearingY;
                    float x1 = x0 + glyph.width;
                    float y1 = y0 + glyph.height;
                    GL11.glTexCoord2f(glyph.u0, glyph.v0);
                    GL11.glVertex2f(x0, y0);
                    GL11.glTexCoord2f(glyph.u0, glyph.v1);
                    GL11.glVertex2f(x0, y1);
                    GL11.glTexCoord2f(glyph.u1, glyph.v1);
                    GL11.glVertex2f(x1, y1);
                    GL11.glTexCoord2f(glyph.u1, glyph.v0);
                    GL11.glVertex2f(x1, y0);
                }
                caret += glyph.advance;
            }
            GL11.glEnd();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.resetColor();
        } catch (Throwable t) {
            System.err.println("[onyx] font draw failed: " + t);
        }
    }

    /** Draws one GUI-font glyph against an explicit baseline, with Onyx's italic shear. */
    public void drawGlyph(char c, float x, float baseline, float factor, int color, boolean italic) {
        Glyph glyph = glyph(c);
        if (glyph.isEmpty() || texture == -1) {
            return;
        }
        GlStateManager.disableTexture2D();
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture);
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F,
                (color & 255) / 255.0F, (color >>> 24) / 255.0F);
        float x0 = x + glyph.bearingX * factor;
        float y0 = baseline + glyph.bearingY * factor;
        float x1 = x0 + glyph.width * factor;
        float y1 = y0 + glyph.height * factor;
        float topShear = italic ? (baseline - y0) * 0.2F : 0.0F;
        float bottomShear = italic ? (baseline - y1) * 0.2F : 0.0F;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(glyph.u0, glyph.v0);
        GL11.glVertex2f(x0 + topShear, y0);
        GL11.glTexCoord2f(glyph.u0, glyph.v1);
        GL11.glVertex2f(x0 + bottomShear, y1);
        GL11.glTexCoord2f(glyph.u1, glyph.v1);
        GL11.glVertex2f(x1 + bottomShear, y1);
        GL11.glTexCoord2f(glyph.u1, glyph.v0);
        GL11.glVertex2f(x1 + topShear, y0);
        GL11.glEnd();
        // Leave the ambient colour neutral: callers mix glyphs with quads of their own, and a
        // leftover glyph colour is exactly how untinted shapes end up dark.
        GlStateManager.resetColor();
    }

    /** A single atlas entry. */
    public static final class Glyph {
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;
        public final float bearingX;
        public final float bearingY;
        public final float width;
        public final float height;
        public final float advance;

        Glyph(float u0, float v0, float u1, float v1, float bearingX, float bearingY,
              float width, float height, float advance) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.bearingX = bearingX;
            this.bearingY = bearingY;
            this.width = width;
            this.height = height;
            this.advance = advance;
        }

        public boolean isEmpty() {
            return width <= 0.0F || height <= 0.0F;
        }
    }
}
