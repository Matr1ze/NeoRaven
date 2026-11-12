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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Monochrome Material 3 Forge splash, drawn on Forge's shared OpenGL render thread.
 *
 * <p>Timeline: a tonal container grows in, Fourier epicycles trace the raven's outline, the
 * filled raven replaces the outline, then the container morphs from a circle into a rounded
 * square and slides left while the wordmark rises in. A Material 3 linear progress indicator
 * runs along the bottom the whole time.
 */
public final class StartupAnimation {
    private static final String NAME = "NeoRaven";
    private static final int[] LETTERS = new int[8];
    private static final float[] ADVANCE = new float[8];
    /** Epicycles kept (largest first) and points sampled along the traced outline. */
    private static final int TERMS = 110, SAMPLES = 512, PATH = 720;
    private static final float BIG = 210, SMALL = 116, GAP = 30;

    private static int raven, stageText, brand, chip, halo;
    private static float wordWidth, chipWidth, imageSize = 128, shownProgress;
    private static long started, stageChangedAt;
    private static String lastStage = "";
    private static boolean finished;
    private static Font statusFont;

    // Fourier series of the outline, sorted by amplitude: frequency and complex coefficient.
    private static int[] freq = new int[0];
    private static float[] re = new float[0], im = new float[0];
    private static float[] pathX = new float[0], pathY = new float[0];

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
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            if (started == 0) started = System.nanoTime();
            if (raven == 0) initialize();
            float t = (System.nanoTime() - started) / 1.0e9F;
            GL11.glClearColor(0.035F, 0.035F, 0.04F, 1);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            background(w, h, t);

            float cx = w / 2, cy = h * 0.45F;
            float intro = emphasized(t / 0.7F);
            float trace = smooth((t - 0.35F) / 3.0F);
            float reveal = standard((t - 3.35F) / 0.6F);
            float settle = emphasized((t - 3.7F) / 1.0F);
            boolean traced = freq.length > 0;
            if (!traced) reveal = intro;

            float groupWidth = SMALL + GAP + wordWidth;
            float size = mix(BIG * mix(0.6F, 1, intro), SMALL, settle);
            float boxX = mix(cx, cx - groupWidth / 2 + SMALL / 2, settle);
            float radius = mix(size / 2, size * 0.3F, settle);

            // A soft white light behind the container; the only "colour" on the page.
            texture(halo, boxX - size * 1.6F, cy - size * 1.6F, size * 3.2F, size * 3.2F, 0.10F * intro);
            roundRect(boxX - size / 2, cy - size / 2, size, size, radius, 0.12F, intro);
            roundOutline(boxX - size / 2, cy - size / 2, size, size, radius, 0.30F, intro * 0.55F);

            float unit = size * 0.70F / imageSize;
            float epicycleAlpha = intro * (1 - reveal);
            if (traced && epicycleAlpha > 0.01F) epicycles(boxX, cy, unit, trace, epicycleAlpha);
            if (reveal > 0.01F) {
                float s = imageSize * unit * mix(0.92F, 1, reveal);
                texture(raven, boxX - s / 2, cy - s / 2, s, s, reveal);
            }

            // Wordmark rises in letter by letter once the container settles.
            float x = cx - groupWidth / 2 + SMALL + GAP;
            float last = 0;
            for (int i = 0; i < NAME.length(); i++) {
                float d = emphasized((t - 3.95F - i * 0.06F) / 0.75F);
                float gy = cy - 8 + 22 * (1 - d);
                texture(LETTERS[i], x + ADVANCE[i] / 2 - 48, gy - 48, 96, 96, d);
                x += ADVANCE[i];
                last = d;
            }
            if (chip != 0 && last > 0.01F) {
                float chipX = cx - groupWidth / 2 + SMALL + GAP + 4, chipY = cy + 36 + 10 * (1 - last);
                roundOutline(chipX, chipY, chipWidth + 24, 26, 13, 0.45F, last);
                texture(chip, chipX + 12, chipY + 1, 256, 24, last);
            }
            drawProgress(w, h, t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }

    // ------------------------------------------------------------------ setup

    private static void initialize() {
        statusFont = new Font("Microsoft YaHei", Font.PLAIN, 18);
        Font letterFont = new Font("SansSerif", Font.BOLD, 84);
        try (InputStream in = StartupAnimation.class.getResourceAsStream("/assets/onyx/font/ttf/googlesansflex_24pt-medium.ttf")) {
            if (in != null) letterFont = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(84F);
        } catch (Exception ignored) { }
        wordWidth = 0;
        for (int i = 0; i < NAME.length(); i++) {
            BufferedImage letter = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = letter.createGraphics(); quality(g); g.setFont(letterFont);
            g.setColor(new java.awt.Color(242, 242, 242)); FontMetrics metrics = g.getFontMetrics();
            String value = NAME.substring(i, i + 1);
            g.drawString(value, (128 - metrics.stringWidth(value)) / 2, (128 - metrics.getHeight()) / 2 + metrics.getAscent());
            ADVANCE[i] = metrics.stringWidth(value) * 96F / 128F + 1.5F;
            wordWidth += ADVANCE[i];
            g.dispose(); LETTERS[i] = upload(letter);
        }
        BufferedImage ravenImage = null;
        try (InputStream in = StartupAnimation.class.getResourceAsStream("/assets/clickgui/textures/raven.png")) {
            if (in != null) ravenImage = ImageIO.read(in);
        } catch (Exception ignored) { }
        if (ravenImage == null) {
            ravenImage = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = ravenImage.createGraphics(); quality(g); g.setColor(java.awt.Color.WHITE);
            g.fillOval(18, 18, 92, 92); g.dispose();
        }
        imageSize = Math.max(ravenImage.getWidth(), ravenImage.getHeight());
        raven = upload(ravenImage);
        try {
            buildSeries(ravenImage);
        } catch (Exception e) {
            freq = new int[0];
        }

        BufferedImage light = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) {
            double r = Math.hypot(x - 127.5, y - 127.5) / 128;
            int alpha = (int) (Math.pow(Math.max(0, 1 - r), 2.2) * 255);
            light.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
        }
        halo = upload(light);

        BufferedImage badge = new BufferedImage(512, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = badge.createGraphics(); quality(g);
        g.setFont(new Font("SansSerif", Font.PLAIN, 21)); g.setColor(new java.awt.Color(150, 150, 155));
        g.drawString("N E O R A V E N", 0, 36); g.dispose(); brand = upload(badge);

        BufferedImage pill = new BufferedImage(512, 48, BufferedImage.TYPE_INT_ARGB);
        g = pill.createGraphics(); quality(g);
        Font chipFont = new Font("SansSerif", Font.PLAIN, 26);
        g.setFont(chipFont); g.setColor(new java.awt.Color(200, 200, 204));
        String chipText = "Client  ·  1.2";
        chipWidth = g.getFontMetrics().stringWidth(chipText) / 2F;
        g.drawString(chipText, 0, 34); g.dispose(); chip = upload(pill);
        System.out.println("[NeoRaven] Animated Forge splash initialized (" + freq.length + " epicycles)");
    }

    /**
     * Traces the outer outline of the largest opaque shape in the logo, resamples it evenly, and
     * keeps the {@link #TERMS} strongest Fourier terms. Coordinates are in image pixels around
     * the image centre, so the epicycle drawing lines up with the filled texture.
     */
    private static void buildSeries(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        boolean[] mask = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) mask[y * w + x] = (image.getRGB(x, y) >>> 24) > 100;

        // Keep only the largest 4-connected piece, so one clean outline gets traced.
        int[] label = new int[w * h], stack = new int[w * h];
        int next = 1, best = 0, bestSize = 0;
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i] || label[i] != 0) continue;
            int top = 0, count = 0; stack[top++] = i; label[i] = next;
            while (top > 0) {
                int p = stack[--top]; count++;
                int px = p % w, py = p / w;
                int[] around = {px > 0 ? p - 1 : -1, px < w - 1 ? p + 1 : -1, py > 0 ? p - w : -1, py < h - 1 ? p + w : -1};
                for (int q : around) if (q >= 0 && mask[q] && label[q] == 0) { label[q] = next; stack[top++] = q; }
            }
            if (count > bestSize) { bestSize = count; best = next; }
            next++;
        }
        int start = -1;
        for (int i = 0; i < mask.length; i++) {
            mask[i] = label[i] == best && best != 0;
            if (mask[i] && start < 0) start = i;
        }
        if (start < 0) return;

        // Moore-neighbour boundary trace, clockwise on screen.
        int[] dx = {-1, -1, 0, 1, 1, 1, 0, -1}, dy = {0, -1, -1, -1, 0, 1, 1, 1};
        int sx = start % w, sy = start / w, x = sx, y = sy, dir = 0;
        List<float[]> outline = new ArrayList<>();
        for (int step = 0; step < w * h * 4; step++) {
            outline.add(new float[]{x + 0.5F - w / 2F, y + 0.5F - h / 2F});
            boolean moved = false;
            for (int k = 0; k < 8; k++) {
                int d = (dir + k) % 8, nx = x + dx[d], ny = y + dy[d];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && mask[ny * w + nx]) {
                    x = nx; y = ny; dir = (d + 5) % 8; moved = true; break;
                }
            }
            if (!moved || (x == sx && y == sy && outline.size() > 2)) break;
        }
        if (outline.size() < 16) return;

        // Even spacing by arc length, so the pen moves at a steady speed.
        int n = outline.size();
        float[] length = new float[n + 1];
        for (int i = 0; i < n; i++) {
            float[] a = outline.get(i), b = outline.get((i + 1) % n);
            length[i + 1] = length[i] + (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
        }
        float total = length[n];
        float[] zr = new float[SAMPLES], zi = new float[SAMPLES];
        int seg = 0;
        for (int s = 0; s < SAMPLES; s++) {
            float target = total * s / SAMPLES;
            while (seg < n - 1 && length[seg + 1] < target) seg++;
            float span = Math.max(1e-4F, length[seg + 1] - length[seg]);
            float u = (target - length[seg]) / span;
            float[] a = outline.get(seg), b = outline.get((seg + 1) % n);
            zr[s] = mix(a[0], b[0], u); zi[s] = mix(a[1], b[1], u);
        }

        // Discrete Fourier transform: c_k = (1/N) * sum z_n * e^(-i*2*pi*k*n/N).
        final float[] cr = new float[SAMPLES], ci = new float[SAMPLES];
        for (int k = 0; k < SAMPLES; k++) {
            double sr = 0, si = 0;
            for (int s = 0; s < SAMPLES; s++) {
                double a = -2 * Math.PI * k * s / SAMPLES, c = Math.cos(a), sn = Math.sin(a);
                sr += zr[s] * c - zi[s] * sn; si += zr[s] * sn + zi[s] * c;
            }
            cr[k] = (float) (sr / SAMPLES); ci[k] = (float) (si / SAMPLES);
        }
        Integer[] order = new Integer[SAMPLES];
        for (int k = 0; k < SAMPLES; k++) order[k] = k;
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return Float.compare(cr[b] * cr[b] + ci[b] * ci[b], cr[a] * cr[a] + ci[a] * ci[a]);
            }
        });
        int terms = Math.min(TERMS, SAMPLES);
        int[] f = new int[terms]; float[] r = new float[terms], i2 = new float[terms];
        for (int k = 0; k < terms; k++) {
            int idx = order[k];
            f[k] = idx <= SAMPLES / 2 ? idx : idx - SAMPLES; r[k] = cr[idx]; i2[k] = ci[idx];
        }
        float[] px = new float[PATH + 1], py = new float[PATH + 1];
        for (int p = 0; p <= PATH; p++) {
            double theta = 2 * Math.PI * p / PATH, sxv = 0, syv = 0;
            for (int k = 0; k < terms; k++) {
                double a = f[k] * theta, c = Math.cos(a), sn = Math.sin(a);
                sxv += r[k] * c - i2[k] * sn; syv += r[k] * sn + i2[k] * c;
            }
            px[p] = (float) sxv; py[p] = (float) syv;
        }
        freq = f; re = r; im = i2; pathX = px; pathY = py;
    }

    // ------------------------------------------------------------------ pieces

    /** Rotating circles chained tip to tip; the last tip is the pen drawing the outline. */
    private static void epicycles(float cx, float cy, float unit, float progress, float alpha) {
        double theta = 2 * Math.PI * progress;
        float x = cx, y = cy;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        for (int k = 0; k < freq.length; k++) {
            double a = freq[k] * theta, c = Math.cos(a), s = Math.sin(a);
            float nx = x + unit * (float) (re[k] * c - im[k] * s);
            float ny = y + unit * (float) (re[k] * s + im[k] * c);
            float radius = unit * (float) Math.hypot(re[k], im[k]);
            if (freq[k] != 0 && radius > 0.6F) {
                // Larger circles read brighter; the tiny tail ones fade into texture.
                float weight = Math.min(1, radius / 18F);
                circle(x, y, radius, 1, alpha * (0.06F + 0.16F * weight));
                GL11.glLineWidth(1);
                GL11.glColor4f(1, 1, 1, alpha * (0.18F + 0.30F * weight));
                GL11.glBegin(GL11.GL_LINES); GL11.glVertex2f(x, y); GL11.glVertex2f(nx, ny); GL11.glEnd();
            }
            x = nx; y = ny;
        }
        int end = Math.min(PATH, (int) (progress * PATH));
        GL11.glLineWidth(2.2F);
        GL11.glColor4f(1, 1, 1, alpha * 0.95F);
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int p = 0; p <= end; p++) GL11.glVertex2f(cx + pathX[p] * unit, cy + pathY[p] * unit);
        if (progress < 1) GL11.glVertex2f(x, y);
        GL11.glEnd();
        if (progress < 1) {
            disc(x, y, 6, alpha * 0.18F);
            disc(x, y, 2.6F, alpha);
        }
    }

    private static void background(float w, float h, float t) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(0.065F, 0.065F, 0.07F, 1); GL11.glVertex2f(0, 0); GL11.glVertex2f(w, 0);
        GL11.glColor4f(0.025F, 0.025F, 0.028F, 1); GL11.glVertex2f(w, h); GL11.glVertex2f(0, h); GL11.glEnd();
        // A quiet dot grid that drifts upward, like Material's motion backdrops.
        float fade = smooth(t / 1.2F);
        float drift = (t * 6) % 32;
        GL11.glPointSize(1.5F);
        GL11.glBegin(GL11.GL_POINTS);
        for (float y = -drift + 16; y < h; y += 32) {
            for (float x = 16; x < w; x += 32) {
                float dx = (x - w / 2) / (w / 2), dy = (y - h * 0.45F) / (h / 2);
                float a = 0.07F * (1 - Math.min(1, (float) Math.sqrt(dx * dx + dy * dy))) * fade;
                if (a <= 0.003F) continue;
                GL11.glColor4f(1, 1, 1, a); GL11.glVertex2f(x, y);
            }
        }
        GL11.glEnd();
        texture(brand, 36, 22, 280, 35, 0.75F * fade);
    }

    /** Material 3 linear progress: rounded track, 4 px gap, active indicator and stop dot. */
    private static void drawProgress(float w, float h, float t) {
        ProgressManager.ProgressBar bar = null; Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
        while (it.hasNext()) bar = it.next();
        String stage = bar == null ? "正在准备客户端" : bar.getTitle() + "  ·  " + bar.getMessage()
                + "    " + bar.getStep() + " / " + bar.getSteps();
        float target = bar == null ? -1 : clamp(bar.getStep() / (float) Math.max(1, bar.getSteps()));
        long now = System.nanoTime();
        if (!stage.equals(lastStage) && now - stageChangedAt > 90_000_000L) {
            lastStage = stage; stageChangedAt = now;
            BufferedImage image = new BufferedImage(1024, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics(); quality(g); g.setFont(statusFont); g.setColor(new java.awt.Color(150, 150, 155));
            String label = stage;
            while (g.getFontMetrics().stringWidth(label) > 990 && label.length() > 4) label = label.substring(0, label.length() - 3) + "…";
            g.drawString(label, (1024 - g.getFontMetrics().stringWidth(label)) / 2, 39); g.dispose();
            if (stageText != 0) GL11.glDeleteTextures(stageText); stageText = upload(image);
        }
        float appear = emphasized((t - 0.3F) / 0.8F);
        if (appear <= 0.01F) return;
        float width = 360, height = 4, left = w / 2 - width / 2, y = h - 88 + 8 * (1 - appear), gap = 4;
        if (target >= 0) {
            shownProgress += (target - shownProgress) * 0.12F;
            float active = width * shownProgress;
            if (active > 0.5F) roundRect(left, y, Math.max(height, active), height, height / 2, 0.95F, appear);
            float trackX = left + active + gap;
            if (trackX < left + width) roundRect(trackX, y, left + width - trackX, height, height / 2, 0.22F, appear);
            disc(left + width - height / 2, y + height / 2, height / 2, appear * 0.95F);
        } else {
            // Indeterminate: a segment that sweeps across with an eased head and tail.
            float cycle = (t * 0.55F) % 1;
            float head = standard(cycle * 1.3F), tail = standard(cycle * 1.3F - 0.3F);
            float a = left + width * tail, b = left + width * head;
            if (a > left + gap) roundRect(left, y, a - left - gap, height, height / 2, 0.22F, appear);
            if (b - a > 0.5F) roundRect(a, y, Math.max(height, b - a), height, height / 2, 0.95F, appear);
            if (b + gap < left + width) roundRect(b + gap, y, left + width - b - gap, height, height / 2, 0.22F, appear);
        }
        if (stageText != 0) texture(stageText, w / 2 - 360, y + 10, 720, 45, appear);
    }

    // ------------------------------------------------------------------ drawing helpers

    private static void roundRect(float x, float y, float w, float h, float r, float grey, float alpha) {
        r = Math.min(r, Math.min(w, h) / 2);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(grey, grey, grey, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2, y + h / 2);
        roundCorners(x, y, w, h, r);
        GL11.glVertex2f(x + w - r, y);
        GL11.glEnd();
    }
    private static void roundOutline(float x, float y, float w, float h, float r, float grey, float alpha) {
        r = Math.min(r, Math.min(w, h) / 2);
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(1); GL11.glColor4f(grey, grey, grey, alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        roundCorners(x, y, w, h, r);
        GL11.glEnd();
    }
    private static void roundCorners(float x, float y, float w, float h, float r) {
        corner(x + w - r, y + r, r, -90, 0);
        corner(x + w - r, y + h - r, r, 0, 90);
        corner(x + r, y + h - r, r, 90, 180);
        corner(x + r, y + r, r, 180, 270);
    }
    private static void corner(float cx, float cy, float r, float from, float to) {
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(from + (to - from) * i / 10);
            GL11.glVertex2f(cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r);
        }
    }
    private static void circle(float x, float y, float radius, float width, float alpha) {
        GL11.glLineWidth(width); GL11.glColor4f(1, 1, 1, alpha);
        int segments = Math.max(16, Math.min(64, (int) (radius * 1.5F)));
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2 / segments;
            GL11.glVertex2f(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
    }
    private static void disc(float x, float y, float radius, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glColor4f(1, 1, 1, alpha);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN); GL11.glVertex2f(x, y);
        for (int i = 0; i <= 24; i++) {
            double a = i * Math.PI * 2 / 24;
            GL11.glVertex2f(x + (float) Math.cos(a) * radius, y + (float) Math.sin(a) * radius);
        }
        GL11.glEnd();
    }
    private static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    private static void texture(int id, float x, float y, float w, float h, float alpha) {
        if (id == 0 || alpha <= 0.003F) return;
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
        Arrays.fill(LETTERS, 0);
        for (int texture : new int[]{raven, stageText, brand, chip, halo}) if (texture != 0) GL11.glDeleteTextures(texture);
        raven = stageText = brand = chip = halo = 0;
    }

    // ------------------------------------------------------------------ easing

    /** Material 3 "emphasized decelerate" curve. */
    private static float emphasized(float t) { return bezier(t, 0.05F, 0.7F, 0.1F, 1.0F); }
    /** Material 3 "standard" curve. */
    private static float standard(float t) { return bezier(t, 0.2F, 0.0F, 0.0F, 1.0F); }
    private static float smooth(float t) { t = clamp(t); return t * t * (3 - 2 * t); }
    /** CSS-style cubic-bezier(x1, y1, x2, y2) evaluated at time t. */
    private static float bezier(float t, float x1, float y1, float x2, float y2) {
        t = clamp(t);
        if (t <= 0 || t >= 1) return t;
        float lo = 0, hi = 1, u = t;
        for (int i = 0; i < 22; i++) {
            u = (lo + hi) / 2;
            float x = cubic(u, x1, x2);
            if (x < t) lo = u; else hi = u;
        }
        return cubic(u, y1, y2);
    }
    private static float cubic(float u, float p1, float p2) {
        float v = 1 - u;
        return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
    }
    private static float clamp(float t) { return Math.max(0, Math.min(1, t)); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
