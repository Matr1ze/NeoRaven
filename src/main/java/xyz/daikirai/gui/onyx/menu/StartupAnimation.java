package xyz.daikirai.gui.onyx.menu;

import net.minecraftforge.fml.common.ProgressManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;

/** Custom Forge splash drawn on Forge's shared OpenGL render thread. */
public final class StartupAnimation {
    private static final String NAME = "NeoRaven";
    private static final int[] LETTERS = new int[8];
    private static int raven, stageText, glow, brand;
    private static final float[] ADVANCE = new float[8];
    private static float wordWidth;
    private static long started, stageChangedAt;
    private static String lastStage = "";
    private static boolean finished;
    private static Font statusFont;

    private StartupAnimation() { }
    public static boolean completed() { return finished; }

    public static void draw() {
        if (finished || !Display.isCreated()) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix(); GL11.glLoadIdentity();
        int pw = Math.max(1, Display.getWidth()), ph = Math.max(1, Display.getHeight());
        float scale = Math.min(pw / 900F, ph / 600F), w = pw / scale, h = ph / scale;
        GL11.glOrtho(0, w, h, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix(); GL11.glLoadIdentity();
        try {
            GL11.glViewport(0, 0, pw, ph);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_ALPHA_TEST); GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST); GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            if (started == 0) started = System.nanoTime();
            if (raven == 0) initialize();
            float t = (System.nanoTime() - started) / 1.0e9F;
            GL11.glClearColor(0.025F, 0.035F, 0.06F, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            background(w, h);
            atmosphere(w, h, t);
            float cx = w / 2, cy = h * 0.44F;
            GL11.glPushMatrix();
            GL11.glTranslatef(cx, cy, 0);
            float zoom = 1 + 0.08F * ease((t - 4.4F) / 1.8F);
            GL11.glScalef(zoom, zoom, 1);
            GL11.glTranslatef(-cx, -cy, 0);
            float settle = ease((t - 0.9F) / 0.95F);
            float gather = ease((t - 6.4F) / 1.5F);
            float groupLeft = cx - (84 + 26 + wordWidth) / 2;
            float logoX = mix(mix(cx, groupLeft + 42, settle), cx, gather);
            float size = 18 + 66 * ease(t / 0.9F);
            float logoY = cy + 22 * (1 - ease(t / 0.9F));
            texture(glow, logoX - 155, cy - 155, 310, 310, 0.35F + gather * 0.25F);
            rings(logoX, cy, t, gather);
            texture(raven, logoX - size / 2, logoY - size / 2, size, size, ease(t / 0.4F));

            // The first three glyph advances reserve Neo immediately before R.
            // No extra spacing is ever inserted inside Raven.
            float x = groupLeft + 110;
            for (int i = 0; i < NAME.length(); i++) {
                float startAt = i < 3 ? 3.0F + i * 0.22F : 1.8F + (i - 3) * 0.13F;
                float appear = clamp((t - startAt) / 0.65F);
                float gx = x + ADVANCE[i] / 2;
                float gy = cy + (i < 3 ? (i == 1 ? 60 : -60) * (1 - ease(appear)) : 0);
                float orbit = ease((t - 6.4F - i * 0.10F) / 1.0F);
                double angle = -Math.PI / 2 + i * Math.PI / 4 + Math.max(0, t - 6.4F) * 0.16;
                gx = mix(gx, cx + (float) Math.cos(angle) * 151, orbit);
                gy = mix(gy, cy + (float) Math.sin(angle) * 151, orbit);
                float pop = i < 3 ? 1 : 0.45F + 0.55F * overshoot(appear);
                float cell = mix(96 * pop, 72, orbit);
                texture(LETTERS[i], gx - cell / 2, gy - cell / 2, cell, cell, ease(appear));
                x += ADVANCE[i];
            }
            GL11.glPopMatrix();
            drawProgress(w, h, t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }

    private static void initialize() {
        statusFont = new Font("Microsoft YaHei", Font.PLAIN, 18);
        Font letterFont = new Font("SansSerif", Font.BOLD, 84);
        try (InputStream in = StartupAnimation.class.getResourceAsStream("/assets/onyx/font/ttf/googlesansflex_24pt-medium.ttf")) {
            if (in != null) letterFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(84F);
        } catch (Exception ignored) { }
        for (int i = 0; i < NAME.length(); i++) {
            BufferedImage letter = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = letter.createGraphics(); quality(g); g.setFont(letterFont);
            g.setColor(new java.awt.Color(237, 242, 255)); FontMetrics metrics = g.getFontMetrics();
            String value = NAME.substring(i, i + 1);
            g.drawString(value, (128 - metrics.stringWidth(value)) / 2, (128 - metrics.getHeight()) / 2 + metrics.getAscent());
            ADVANCE[i] = metrics.stringWidth(value) * 96F / 128F + 1.5F;
            wordWidth += ADVANCE[i];
            g.dispose(); LETTERS[i] = upload(letter);
        }
        try (InputStream in = StartupAnimation.class.getResourceAsStream("/assets/clickgui/textures/raven.png")) {
            if (in != null) raven = upload(ImageIO.read(in));
        } catch (Exception ignored) { }
        if (raven == 0) {
            BufferedImage fallback = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = fallback.createGraphics(); quality(g); g.setColor(java.awt.Color.WHITE);
            g.fillOval(18, 18, 92, 92); g.dispose(); raven = upload(fallback);
        }
        BufferedImage halo = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            double radius = Math.hypot(x - 127.5, y - 127.5) / 128;
            int alpha = (int) (Math.pow(Math.max(0, 1 - radius), 2.5) * 160);
            halo.setRGB(x, y, (alpha << 24) | 0x398ecb);
        }
        glow = upload(halo);
        BufferedImage badge = new BufferedImage(512, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = badge.createGraphics(); quality(g);
        g.setFont(new Font("SansSerif", Font.PLAIN, 21)); g.setColor(new java.awt.Color(139, 182, 207));
        g.drawString("N E O R A V E N   /   1 . 2", 0, 36); g.dispose(); brand = upload(badge);
        System.out.println("[NeoRaven] Animated Forge splash initialized");
    }

    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    private static void background(float w, float h) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.04F, 0.065F, 0.105F, 1); GL11.glVertex2f(0, 0); GL11.glVertex2f(w, 0);
        GL11.glColor4f(0.015F, 0.022F, 0.04F, 1); GL11.glVertex2f(w, h); GL11.glVertex2f(0, h); GL11.glEnd();
    }
    private static void atmosphere(float w, float h, float t) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1);
        GL11.glColor4f(0.22F, 0.45F, 0.65F, 0.08F);
        GL11.glBegin(GL11.GL_LINES);
        float horizon = h * 0.72F;
        for (int i = -12; i <= 12; i++) {
            GL11.glVertex2f(w / 2 + i * 24, horizon);
            GL11.glVertex2f(w / 2 + i * 115, h);
        }
        for (int i = 0; i < 8; i++) {
            float p = (i + (t * 0.16F) % 1) / 8;
            float y = horizon + p * p * (h - horizon);
            GL11.glVertex2f(0, y); GL11.glVertex2f(w, y);
        }
        GL11.glEnd();
        GL11.glColor4f(0.34F, 0.65F, 0.85F, 0.20F);
        GL11.glBegin(GL11.GL_POINTS);
        for (int i = 0; i < 35; i++) {
            float x = (i * 137.37F + 41) % w;
            float y = (i * 73.73F + 29) % (h * 0.68F);
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
        texture(brand, 36, 22, 280, 35, 0.7F);
        line(36, 65, 96, 65, 0.35F);
        line(w - 36, 36, w - 66, 36, 0.35F);
        line(w - 36, 36, w - 36, 66, 0.35F);
        line(36, h - 36, 66, h - 36, 0.25F);
        line(36, h - 36, 36, h - 66, 0.25F);
    }

    private static void rings(float x, float y, float t, float orbit) {
        float alpha = ease(t / 0.9F);
        arc(x, y, mix(58, 122, orbit), t * 17, 115, alpha * 0.22F);
        arc(x, y, mix(58, 122, orbit), t * 17 + 180, 115, alpha * 0.22F);
        arc(x, y, mix(65, 180, orbit), -t * 10, 60, alpha * 0.45F);
        arc(x, y, mix(65, 180, orbit), -t * 10 + 180, 60, alpha * 0.45F);
        if (orbit > 0.01F) {
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                float r = i % 4 == 0 ? 191 : 187;
                line(x + (float) Math.cos(a) * 184, y + (float) Math.sin(a) * 184,
                        x + (float) Math.cos(a) * r, y + (float) Math.sin(a) * r, orbit * 0.17F);
            }
        }
    }
    private static void arc(float x, float y, float radius, float start, float sweep, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(1.2F);
        GL11.glColor4f(0.35F, 0.73F, 0.93F, alpha); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= 56; i++) {
            double a = Math.toRadians(start + sweep * i / 56);
            GL11.glVertex2f(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
    }
    private static void line(float x, float y, float x2, float y2, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(1);
        GL11.glColor4f(0.35F, 0.73F, 0.93F, alpha); GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x2, y2); GL11.glEnd();
    }

    private static void drawProgress(float w, float h, float t) {
        ProgressManager.ProgressBar bar = null; Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
        while (it.hasNext()) bar = it.next();
        String stage = bar == null ? "正在准备客户端" : bar.getTitle() + "  ·  " + bar.getMessage()
                + "    " + bar.getStep() + " / " + bar.getSteps();
        float progress = bar == null ? -1 : clamp(bar.getStep() / (float) Math.max(1, bar.getSteps()));
        long now = System.nanoTime();
        if (!stage.equals(lastStage) && now - stageChangedAt > 90_000_000L) {
            lastStage = stage; stageChangedAt = now;
            BufferedImage image = new BufferedImage(1024, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics(); quality(g); g.setFont(statusFont); g.setColor(new java.awt.Color(152, 167, 193));
            String label = stage;
            while (g.getFontMetrics().stringWidth(label) > 990 && label.length() > 4) label = label.substring(0, label.length() - 3) + "…";
            g.drawString(label, (1024 - g.getFontMetrics().stringWidth(label)) / 2, 39); g.dispose();
            if (stageText != 0) GL11.glDeleteTextures(stageText); stageText = upload(image);
        }
        float left = w / 2 - 220, y = h - 86;
        rectangle(left, y, 440, 3, 0.13F, 0.18F, 0.26F);
        if (progress >= 0) rectangle(left, y, 440 * progress, 3, 0.48F, 0.66F, 0.95F);
        else rectangle(left + (float) ((Math.sin(t * 1.6) + 1) * 150), y, 100, 3, 0.48F, 0.66F, 0.95F);
        if (progress > 0) texture(glow, left + 440 * progress - 28, y - 26, 56, 56, 0.8F);
        if (stageText != 0) texture(stageText, w / 2 - 360, y + 8, 720, 45, 1);
    }
    private static void rectangle(float x, float y, float w, float h, float r, float g, float b) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(r, g, b, 1); GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y); GL11.glVertex2f(x + w, y); GL11.glVertex2f(x + w, y + h); GL11.glVertex2f(x, y + h); GL11.glEnd();
    }
    private static void texture(int id, float x, float y, float w, float h, float alpha) {
        GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glBindTexture(GL11.GL_TEXTURE_2D, id); GL11.glColor4f(1, 1, 1, alpha); GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y); GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + w, y + h); GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + w, y); GL11.glEnd();
    }
    private static int upload(BufferedImage image) {
        int id = GL11.glGenTextures(); ByteBuffer bytes = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int c = image.getRGB(x, y); bytes.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >> 24));
        }
        bytes.flip(); GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, image.getWidth(), image.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, bytes);
        return id;
    }
    public static void finish() {
        finished = true; for (int texture : LETTERS) if (texture != 0) GL11.glDeleteTextures(texture);
        if (raven != 0) GL11.glDeleteTextures(raven); if (stageText != 0) GL11.glDeleteTextures(stageText);
        if (glow != 0) GL11.glDeleteTextures(glow);
        if (brand != 0) GL11.glDeleteTextures(brand);
        raven = stageText = glow = brand = 0;
    }
    private static float overshoot(float t) { float u = t - 1; return 1 + 2.70158F * u * u * u + 1.70158F * u * u; }
    private static float clamp(float t) { return Math.max(0, Math.min(1, t)); }
    private static float ease(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
