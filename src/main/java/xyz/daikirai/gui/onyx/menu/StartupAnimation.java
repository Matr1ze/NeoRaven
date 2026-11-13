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

    private static int raven, halo;
    private static float wordWidth, imageSize = 128, shownProgress, offX, offY;
    private static long started;
    private static final StartupProgress PROGRESS = new StartupProgress();
    private static boolean finished, completing;

    // Fourier series of the outline, sorted by amplitude: frequency and complex coefficient.
    private static int[] freq = new int[0];
    private static float[] re = new float[0], im = new float[0];
    private static float[] pathX = new float[0], pathY = new float[0];

    private StartupAnimation() { }
    public static boolean completed() { return finished; }
    public static void loadingStage(String state) { PROGRESS.advance(state); }

    /** Run after Minecraft creates the display, before Forge starts its splash worker. */
    public static void prepare() {
        if (raven == 0 && Display.isCreated()) initialize();
    }

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
            // Build fonts, textures and the Fourier series before starting the visible clock.
            // Otherwise the first frame is timed before this expensive one-time work and the
            // trace appears to hitch or skip its opening segment.
            if (raven == 0) initialize();
            if (started == 0) started = System.nanoTime();
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

            float idle = smooth((t - 5.0F) / 0.8F);
            float groupWidth = SMALL + GAP + wordWidth;
            float size = mix(BIG * mix(0.6F, 1, intro), SMALL, settle);
            size *= 1 + 0.018F * (float) Math.sin(t * 2.2) * idle; // breathing once loaded in
            float boxX = mix(cx, cx - groupWidth / 2 + SMALL / 2, settle);
            float radius = mix(size / 2, size * 0.3F, settle);

            // A soft white light behind the container; the only "colour" on the page.
            float light = 0.10F + 0.05F * (float) Math.sin(t * 1.6) * idle + 0.25F * pulse(t, 3.35F, 0.9F);
            texture(halo, boxX - size * 1.6F, cy - size * 1.6F, size * 3.2F, size * 3.2F, light * intro);
            ripples(boxX, cy, size, t);
            roundRect(boxX - size / 2, cy - size / 2, size, size, radius, 0.12F, intro);
            roundOutline(boxX - size / 2, cy - size / 2, size, size, radius, 0.30F, intro * 0.55F);

            float unit = size * 0.70F / imageSize;
            float epicycleAlpha = intro * (1 - reveal);
            if (traced && epicycleAlpha > 0.01F) epicycles(boxX, cy, unit, trace, epicycleAlpha);
            if (traced) burst(cx, cy, BIG * 0.70F / imageSize, t);
            if (reveal > 0.01F) {
                float s = imageSize * unit * mix(0.92F, 1, reveal);
                float k = s / imageSize;
                texture(raven, boxX - s / 2 - offX * k, cy - s / 2 - offY * k, s, s, reveal);
            }
            brackets(boxX, cy, size, t, idle);

            // Wordmark rises in letter by letter once the container settles.
            float wordLeft = cx - groupWidth / 2 + SMALL + GAP;
            float x = wordLeft;
            for (int i = 0; i < NAME.length(); i++) {
                float d = emphasized((t - 3.95F - i * 0.06F) / 0.75F);
                float gy = cy - 8 + 22 * (1 - d);
                float cell = 96 * mix(1.18F, 1, d);
                texture(LETTERS[i], x + ADVANCE[i] / 2 - cell / 2, gy - cell / 2, cell, cell, d);
                x += ADVANCE[i];
            }
            underline(wordLeft, cy + 34, t);
            drawProgress(w, h, t);
        } finally {
            GL11.glMatrixMode(GL11.GL_MODELVIEW); GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION); GL11.glPopMatrix(); GL11.glPopAttrib();
        }
    }

    // ------------------------------------------------------------------ setup

    private static void initialize() {
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
        // The raven does not sit in the middle of its square image; centre on the visible shape.
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i]) continue;
            int mx = i % w, my = i / w;
            minX = Math.min(minX, mx); maxX = Math.max(maxX, mx);
            minY = Math.min(minY, my); maxY = Math.max(maxY, my);
        }
        offX = (minX + maxX + 1) / 2F - w / 2F;
        offY = (minY + maxY + 1) / 2F - h / 2F;

        // Moore-neighbour boundary trace, clockwise on screen.
        int[] dx = {-1, -1, 0, 1, 1, 1, 0, -1}, dy = {0, -1, -1, -1, 0, 1, 1, 1};
        int sx = start % w, sy = start / w, x = sx, y = sy, dir = 0;
        List<float[]> outline = new ArrayList<>();
        for (int step = 0; step < w * h * 4; step++) {
            outline.add(new float[]{x + 0.5F - w / 2F - offX, y + 0.5F - h / 2F - offY});
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
        // A brighter, wider comet tail right behind the pen.
        if (progress < 1 && end > 2) {
            GL11.glLineWidth(5F);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int p = Math.max(0, end - 48); p <= end; p++) {
                float k = 1 - (end - p) / 48F;
                GL11.glColor4f(1, 1, 1, alpha * 0.16F * k);
                GL11.glVertex2f(cx + pathX[p] * unit, cy + pathY[p] * unit);
            }
            GL11.glVertex2f(x, y);
            GL11.glEnd();
        }
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
        // Two huge dotted orbits turning in opposite directions, very faint.
        float ocx = w / 2, ocy = h * 0.45F;
        GL11.glPointSize(1.6F);
        GL11.glBegin(GL11.GL_POINTS);
        for (int ring = 0; ring < 2; ring++) {
            float r = ring == 0 ? 236 : 318, spin = (ring == 0 ? 0.06F : -0.04F) * t;
            int n = ring == 0 ? 96 : 132;
            for (int i = 0; i < n; i++) {
                double a = spin + i * Math.PI * 2 / n;
                GL11.glColor4f(1, 1, 1, (i % 6 == 0 ? 0.10F : 0.045F) * fade);
                GL11.glVertex2f(ocx + (float) Math.cos(a) * r, ocy + (float) Math.sin(a) * r);
            }
        }
        GL11.glEnd();
    }

    /** Material ripple rings: three on the reveal, then a soft one every few seconds. */
    private static void ripples(float x, float y, float size, float t) {
        for (int i = 0; i < 3; i++) {
            float k = (t - 3.35F - i * 0.16F) / 1.3F;
            if (k > 0 && k < 1) circle(x, y, size / 2 + emphasized(k) * 240, 1.3F, (1 - k) * 0.35F);
        }
        if (t > 5.6F) {
            float k = ((t - 5.6F) % 3.4F) / 1.8F;
            if (k < 1) circle(x, y, size * 0.55F + emphasized(k) * 90, 1, (1 - k) * 0.18F);
        }
    }

    /** White sparks thrown outward from the traced outline as the solid raven appears. */
    private static void burst(float cx, float cy, float unit, float t) {
        float k = (t - 3.3F) / 1.5F;
        if (k <= 0 || k >= 1 || pathX.length == 0) return;
        float e = 1 - (1 - k) * (1 - k) * (1 - k);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPointSize(2.2F);
        GL11.glBegin(GL11.GL_POINTS);
        for (int i = 0; i < 140; i++) {
            int p = i * PATH / 140;
            float sx = pathX[p] * unit, sy = pathY[p] * unit;
            double angle = Math.atan2(sy, sx) + (rnd(i, 2) - 0.5F) * 0.7F;
            float d = (28 + 110 * rnd(i, 1)) * e;
            GL11.glColor4f(1, 1, 1, (1 - k) * (0.4F + 0.6F * rnd(i, 3)));
            GL11.glVertex2f(cx + sx + (float) Math.cos(angle) * d, cy + sy + (float) Math.sin(angle) * d);
        }
        GL11.glEnd();
    }

    /**
     * Viewfinder corner brackets around the square container. They breathe in and out, and a
     * thin light line scans down the container now and then.
     */
    private static void brackets(float x, float y, float size, float t, float idle) {
        if (idle <= 0.01F) return;
        float half = size / 2 + 9 + 2.5F * (float) Math.sin(t * 2.2), len = 13 * idle;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(1.6F);
        GL11.glColor4f(1, 1, 1, 0.55F * idle);
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                GL11.glVertex2f(x + sx * half, y + sy * (half - len));
                GL11.glVertex2f(x + sx * half, y + sy * half);
                GL11.glVertex2f(x + sx * (half - len), y + sy * half);
                GL11.glEnd();
            }
        }
        float s = ((t - 5.4F) % 4.0F) / 1.4F;
        if (t > 5.4F && s < 1) {
            float inner = size / 2 - 6, ly = y - inner + 2 * inner * standard(s);
            float a = (float) Math.sin(Math.PI * s) * idle;
            GL11.glLineWidth(1);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(1, 1, 1, 0); GL11.glVertex2f(x - inner, ly);
            GL11.glColor4f(1, 1, 1, 0.35F * a); GL11.glVertex2f(x, ly);
            GL11.glVertex2f(x, ly);
            GL11.glColor4f(1, 1, 1, 0); GL11.glVertex2f(x + inner, ly);
            GL11.glEnd();
        }
    }

    /** A line grows under the wordmark, then a light dot sweeps along it now and then. */
    private static void underline(float left, float y, float t) {
        float e = emphasized((t - 4.6F) / 0.8F);
        if (e <= 0.01F) return;
        roundRect(left + 2, y, Math.max(2, (wordWidth - 4) * e), 2, 1, 0.85F, e * 0.45F);
        if (t > 5.4F) {
            float s = ((t - 5.4F) % 2.6F) / 1.2F;
            if (s < 1) {
                float hx = left + 2 + (wordWidth - 4) * standard(s), a = (float) Math.sin(Math.PI * s);
                texture(halo, hx - 14, y - 13, 28, 28, a * 0.5F);
                disc(hx, y + 1, 2.2F, a);
            }
        }
    }

    /** 0 → 1 → 0 bump starting at {@code at}, lasting {@code length} seconds. */
    private static float pulse(float t, float at, float length) {
        float k = (t - at) / length;
        return k <= 0 || k >= 1 ? 0 : (float) Math.sin(Math.PI * k);
    }

    /** Stable pseudo-random value in [0, 1) for particle {@code i}. */
    private static float rnd(int i, int salt) {
        long h = i * 0x9E3779B97F4A7C15L ^ salt * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Material 3 linear progress: rounded track, 4 px gap, active indicator and stop dot. */
    private static void drawProgress(float w, float h, float t) {
        ProgressManager.ProgressBar root = null, bar = null;
        Iterator<ProgressManager.ProgressBar> it = ProgressManager.barIterator();
        while (it.hasNext()) {
            bar = it.next();
            if (root == null) root = bar;
        }
        float local = 0.0F;
        if (root != null) {
            float nested = bar == root ? 0.0F
                    : clamp(bar.getStep() / (float) Math.max(1, bar.getSteps()));
            local = clamp((root.getStep() + nested) / Math.max(1, root.getSteps()));
        }
        float target = completing ? 1.0F : PROGRESS.sample(local, System.nanoTime());
        float appear = completing ? 1.0F : emphasized((t - 0.3F) / 0.8F);
        if (appear <= 0.01F) return;
        float width = 360, height = 4, left = w / 2 - width / 2, y = h - 88 + 8 * (1 - appear), gap = 4;
        {
            // Never lower the target when Forge switches to another sub-bar.
            target = Math.max(target, shownProgress);
            shownProgress = completing ? 1.0F : shownProgress + (target - shownProgress) * 0.12F;
            float active = width * shownProgress;
            if (active > 0.5F) roundRect(left, y, Math.max(height, active), height, height / 2, 0.95F, appear);
            float trackX = left + active + gap;
            if (trackX < left + width) roundRect(trackX, y, left + width - trackX, height, height / 2, 0.22F, appear);
            disc(left + width - height / 2, y + height / 2, height / 2, appear * 0.95F);
        }
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
        GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glLineWidth(width); GL11.glColor4f(1, 1, 1, alpha);
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
        // Forge has joined its worker and restored the main GL context here. Present the
        // completed bar once before handing the window to the title screen.
        if (!finished && started != 0 && Display.isCreated()) {
            completing = true;
            try { draw(); Display.update(); }
            finally { completing = false; }
        }
        finished = true; for (int texture : LETTERS) if (texture != 0) GL11.glDeleteTextures(texture);
        Arrays.fill(LETTERS, 0);
        for (int texture : new int[]{raven, halo}) if (texture != 0) GL11.glDeleteTextures(texture);
        raven = halo = 0;
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
