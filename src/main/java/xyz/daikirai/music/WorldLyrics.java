package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.TtfFont;
import xyz.daikirai.gui.onyx.render.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * "3D lyrics" ported from DangoMusic ({@code LyricsModule}): the line the built-in player is
 * singing floats in the world as a billboard, close to the crosshair.
 *
 * <p>Timing comes from the player's own lyric sheet - {@link MusicManager#lyrics()} and
 * {@link MusicManager#positionMs()} - so this is the same karaoke data the lyric window uses. The
 * settings live on {@link MusicManager#lyrics3d} and are edited from the player window.</p>
 */
public final class WorldLyrics {

    private static final String FONT_RESOURCE = xyz.daikirai.gui.onyx.render.Typeface.familyPath();
    private static final float FONT_SIZE = 32.0F;
    private static final float LINE_HEIGHT = 40.0F;
    private static final float MAX_WIDTH = 460.0F;
    private static final float TRANSLATION_SCALE = 0.72F;
    private static final long FADE_NS = 400000000L;
    private static final int WIGGLE_FPS = 8;
    /** How far below eye level the line may hang, so it can never spawn inside the ground. */
    private static final double GROUND_MARGIN = 0.75D;
    /** Blocks per font pixel at {@code Distance = 12}; scaled with the distance so the apparent size stays put. */
    private static final float BASE_SCALE = 0.021F;

    private static Typeface.Style style;
    private static Typeface.Style translationStyle;
    private static boolean stylesTried;
    private static final Random RANDOM = new Random();

    private static Floating active;
    private static final List<Floating> fading = new ArrayList<Floating>();
    private static List<LyricLine> lyricsRef;
    private static int lastIndex = -1;
    private static long lastFrameNanos;

    private WorldLyrics() {
    }

    /** Drops every floating line; called when 3D lyrics are switched off. */
    public static void reset() {
        active = null;
        fading.clear();
        lyricsRef = null;
        lastIndex = -1;
        lastFrameNanos = 0L;
    }

    public static boolean isAnimating() {
        return active != null || !fading.isEmpty();
    }

    /** Called from the world render pass (after the terrain, before the HUD). */
    public static void render(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        MusicManager manager = MusicManager.get();
        MusicManager.Lyrics3DSettings l3 = manager.lyrics3d;
        if (!l3.enabled || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        long now = System.nanoTime();
        float delta = lastFrameNanos == 0L ? 16.0F : Math.min((now - lastFrameNanos) / 1000000.0F, 100.0F);
        lastFrameNanos = now;

        List<LyricLine> lines = manager.lyrics();
        if (lines != lyricsRef) {
            lyricsRef = lines;
            lastIndex = -1;
            fading.clear();
            if (active != null) {
                active.fadeStart = now;
                fading.add(active);
                active = null;
            }
        }
        long position = manager.positionMs();
        boolean playing = manager.isPlaying() && manager.current() != null;

        if (!playing || lines.isEmpty()) {
            if (active != null) {
                active.fadeStart = now;
                fading.add(active);
                active = null;
            }
            lastIndex = -1;
        } else {
            int index = LyricPanel.indexAt(lines, position);
            if (index != lastIndex) {
                lastIndex = index;
                if (active != null) {
                    active.fadeStart = now;
                    fading.add(active);
                }
                active = null;
                if (index >= 0) {
                    LyricLine line = lines.get(index);
                    if (!line.text.trim().isEmpty()) {
                        active = new Floating(line, l3);
                    }
                }
            }
        }
        if (active == null && fading.isEmpty()) {
            return;
        }

        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        Vec3 eye = eye(view, partialTicks);
        Vec3 look = view.getLook(partialTicks);
        if (active != null) {
            follow(active, l3, eye, look, delta);
        }

        Scheme scheme = Theme.scheme();
        float rainbowPhase = (float) ((now / 1000000000.0) * 0.08 % 1.0);

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        // World post-processing (Fog Blur) and the hand pass leave the driver and the caches in
        // different places: GL_FOG can still be switched on, the lightmap unit can hold a blur
        // texture instead of the lightmap, and the last raw glBindTexture leaves GlStateManager
        // believing another texture is bound (so the font bind below would be skipped and the
        // glyphs would sample that leftover texture - the line reads as black). Normalise all
        // three here; the line is full-bright by design, so the lightmap is forced to 240,240.
        GlStateManager.disableFog();
        mc.entityRenderer.enableLightmap();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.bindTexture(0);
        try {
            for (int i = fading.size() - 1; i >= 0; i--) {
                Floating f = fading.get(i);
                float t = (now - f.fadeStart) / (float) FADE_NS;
                if (t >= 1.0F) {
                    fading.remove(i);
                    continue;
                }
                draw(f, l3, scheme, position, (1.0F - t) * (1.0F - t), t * 0.45F, rainbowPhase);
            }
            if (active != null) {
                draw(active, l3, scheme, position, 1.0F, 0.0F, rainbowPhase);
            }
        } finally {
            // Hand the lightmap back the way Minecraft leaves it after the world pass.
            mc.entityRenderer.disableLightmap();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.enableLighting();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    // ------------------------------------------------------------------ //
    // Placement
    // ------------------------------------------------------------------ //

    private static Vec3 eye(Entity view, float partialTicks) {
        return new Vec3(
                view.prevPosX + (view.posX - view.prevPosX) * partialTicks,
                view.prevPosY + (view.posY - view.prevPosY) * partialTicks + view.getEyeHeight(),
                view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks);
    }

    /** Keeps the line at its random angular offset from the view, and never lets it drift away. */
    private static void follow(Floating f, MusicManager.Lyrics3DSettings l3, Vec3 eye, Vec3 look, float delta) {
        double yawOffset = Math.toRadians(f.spreadYaw);
        double pitchOffset = Math.toRadians(f.spreadPitch);
        double dx = look.xCoord * Math.cos(yawOffset) + look.zCoord * Math.sin(yawOffset);
        double dz = -look.xCoord * Math.sin(yawOffset) + look.zCoord * Math.cos(yawOffset);
        double dy = look.yCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double elevation = Math.atan2(dy, horizontal) + pitchOffset;
        double cosPitch = Math.cos(elevation);
        double sinPitch = Math.sin(elevation);
        double hx = horizontal > 1.0E-6 ? dx / horizontal : 0.0;
        double hz = horizontal > 1.0E-6 ? dz / horizontal : 1.0;
        // Screen-space nudge: offsetX runs along the camera's right vector, offsetY along its up
        // vector, both measured in blocks at the configured distance.
        double lx = look.xCoord;
        double ly = look.yCoord;
        double lz = look.zCoord;
        double horizontal2 = Math.sqrt(lx * lx + lz * lz);
        double rightX;
        double rightZ;
        if (horizontal2 < 1.0E-6D) {
            rightX = 1.0D;
            rightZ = 0.0D;
        } else {
            // screen right = look x worldUp (looking south, +X is on your left)
            rightX = -lz / horizontal2;
            rightZ = lx / horizontal2;
        }
        double upX = -ly * lx / Math.max(1.0E-6D, horizontal2);
        double upZ = -ly * lz / Math.max(1.0E-6D, horizontal2);
        double distance = l3.distance;
        double offsetX = l3.offsetX;
        double offsetY = l3.offsetY;
        double targetX = eye.xCoord + hx * cosPitch * distance + rightX * offsetX + upX * offsetY;
        double targetY = eye.yCoord + sinPitch * distance + horizontal2 * offsetY;
        double targetZ = eye.zCoord + hz * cosPitch * distance + rightZ * offsetX + upZ * offsetY;
        // "Above ground" (Dango's rule): looking down used to drop the line a whole distance below
        // the eyes, i.e. inside the terrain, where it was invisible. Keep it at most a little below
        // eye level instead.
        double floor = eye.yCoord - GROUND_MARGIN;
        if (targetY < floor) {
            targetY = floor;
        }
        if (!f.placed) {
            f.x = targetX;
            f.y = targetY;
            f.z = targetZ;
            f.placed = true;
            return;
        }
        // "Follow view" off means the line stays where it appeared; it is not dragged back into the
        // view (the pull-back felt jumpy and is gone again).
        if (!l3.followView) {
            return;
        }
        float k = 1.0F - (float) Math.exp(-delta / 90.0F);
        f.x += (targetX - f.x) * k;
        f.y += (targetY - f.y) * k;
        f.z += (targetZ - f.z) * k;
    }

    private static void draw(Floating f, MusicManager.Lyrics3DSettings l3, Scheme scheme, long position,
                             float alpha, float lift, float rainbowPhase) {
        Minecraft mc = Minecraft.getMinecraft();
        // viewerPos is the *feet* of the view entity (Render3D#updateCameraPos adds the eye height
        // itself); the world matrix origin is the camera at eye level. Using the feet here both
        // pushed the line ~1.6 blocks too high and made the occlusion ray start inside the ground.
        // RenderManager#viewerPos is the view entity's *feet*; the world matrix origin is the eye.
        // Using the feet put the line ~1.6 blocks high and made the occlusion ray start inside the
        // block underfoot, which collapsed the distance and shrank the text to an invisible speck.
        Entity view = mc.getRenderViewEntity();
        float eyeHeight = view == null ? 1.62F : view.getEyeHeight();
        double camX = mc.getRenderManager().viewerPosX;
        double camY = mc.getRenderManager().viewerPosY + eyeHeight;
        double camZ = mc.getRenderManager().viewerPosZ;
        double px = f.x;
        double py = f.y + lift;
        double pz = f.z;
        double dx = px - camX;
        double dy = py - camY;
        double dz = pz - camZ;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        // No occlusion pull-in any more: the line keeps its configured distance even if a block is
        // in the way.
        float pixels = BASE_SCALE * l3.scale * (l3.distance / 12.0F);
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) dx, (float) dy, (float) dz);
        GlStateManager.rotate(-yaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(pitch, 1.0F, 0.0F, 0.0F);
        GlStateManager.scale(-pixels, -pixels, pixels);
        try {
            // A soft black drop shadow keeps the line readable against bright skies and snow.
            GlStateManager.pushMatrix();
            GlStateManager.translate(1.6F, 1.6F, 0.0F);
            drawGlyphs(f, l3, scheme, position, alpha * 0.45F, rainbowPhase, 1.0F, true);
            GlStateManager.popMatrix();
            if (l3.glow && alpha > 0.02F) {
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
                drawGlyphs(f, l3, scheme, position, alpha * 0.22F, rainbowPhase, 1.12F, false);
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            }
            drawGlyphs(f, l3, scheme, position, alpha, rainbowPhase, 1.0F, false);
            if (l3.translation) {
                drawTranslation(f, scheme, alpha, position);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    private static void drawGlyphs(Floating f, MusicManager.Lyrics3DSettings l3, Scheme scheme, long position,
                                   float alpha, float rainbowPhase, float spread, boolean shadow) {
        Typeface.Style style = style();
        long elapsed = position - f.line.timeMs;
        long frame = System.currentTimeMillis() * WIGGLE_FPS / 1000L;
        float amount = l3.wiggle ? 1.0F : 0.0F;
        for (Glyph g : f.glyphs) {
            long appear;
            float sung;
            if (g.word >= 0) {
                LyricWord word = f.line.words.get(g.word);
                appear = word.startMs - f.line.timeMs;
                sung = word.progress(position);
            } else {
                appear = g.appearMs;
                sung = f.line.progress(position);
            }
            long t = elapsed - appear;
            if (t < 0L) {
                continue;
            }
            float appearK = Material.clamp(t / 160.0F, 0.0F, 1.0F);
            appearK = appearK * appearK * (3.0F - 2.0F * appearK);
            float pop = l3.wordPop && g.word >= 0
                    ? 1.0F + 0.55F * (1.0F - Material.clamp(t / 160.0F, 0.0F, 1.0F)) : 1.0F;
            float a = appearK * alpha;
            if (a <= 0.02F) {
                continue;
            }
            float jitterX = 0.0F;
            float jitterY = 0.0F;
            if (amount > 0.001F) {
                jitterX = hash(g.index, frame, 2) * 0.8F * amount;
                jitterY = hash(g.index, frame, 3) * 1.6F * amount;
            }
            float lift = (1.0F - appearK) * 5.0F;
            int color = shadow ? Material.withAlpha(0x000000, a)
                    : color(scheme, g, f, sung, a, rainbowPhase);
            float centerX = g.x + g.width * 0.5F;
            float centerY = g.y + LINE_HEIGHT * 0.5F;
            GlStateManager.pushMatrix();
            GlStateManager.translate(centerX + jitterX, centerY + jitterY - lift, 0.0F);
            if (spread != 1.0F) {
                GlStateManager.scale(spread, spread, 1.0F);
            }
            GlStateManager.scale(pop, pop, 1.0F);
            GlStateManager.translate(-centerX, -centerY, 0.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translate(g.x, g.y, 0.0F);
            GlStateManager.scale(style.scale(), style.scale(), 1.0F);
            style.draw(g.text, color);
            GlStateManager.popMatrix();
            GlStateManager.popMatrix();
        }
    }

    private static void drawTranslation(Floating f, Scheme scheme, float alpha, long position) {
        if (f.translation.isEmpty()) {
            return;
        }
        Typeface.Style style = translationStyle();
        long elapsed = position - f.line.timeMs;
        float appear = Material.clamp(elapsed / 400.0F, 0.0F, 1.0F);
        appear = appear * appear * (3.0F - 2.0F * appear);
        float a = alpha * appear;
        if (a <= 0.02F) {
            return;
        }
        // Same reason as the main line: a bright ink over the world, not the palette's dark one.
        int base = Material.withAlpha(0xFFFFFFFF, a * 0.75F);
        float y = f.rowsHeight * 0.5F + 8.0F;
        for (String row : f.translation) {
            float width = style.width(row);
            GlStateManager.pushMatrix();
            GlStateManager.translate(-width * 0.5F, y, 0.0F);
            GlStateManager.scale(style.scale(), style.scale(), 1.0F);
            style.draw(row, base);
            GlStateManager.popMatrix();
            y += LINE_HEIGHT * TRANSLATION_SCALE;
        }
    }

    private static int color(Scheme scheme, Glyph g, Floating f, float sung, float alpha, float rainbowPhase) {
        if (f.rainbow) {
            return rainbow(g.index, f.glyphs.size(), alpha, rainbowPhase);
        }
        // The line floats over the world, not on a Material surface, so it keeps a bright ink in
        // both palettes: the light palette's onSurface is near-black, and over the fog's pale wash
        // (or any bright sky) the "theme-aware" ink read as a black line.
        int ink = 0xFFFFFFFF;
        int rgb = Material.mix(ink, Material.mix(scheme.primary, ink, 0.25F),
                Material.clamp(sung, 0.0F, 1.0F));
        float a = alpha * (0.34F + 0.66F * Material.clamp(sung, 0.0F, 1.0F));
        return Material.withAlpha(rgb, a);
    }

    private static int rainbow(int index, int total, float alpha, float phase) {
        float u = total <= 1 ? 0.5F : index / (float) (total - 1);
        float hue = (u * 0.6F + phase) % 1.0F;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.55F, 1.0F) & 0xFFFFFF;
        return Material.withAlpha(rgb, alpha);
    }

    /** Deterministic jitter in [-1, 1] for one glyph on one wiggle frame. */
    private static float hash(int index, long frame, int salt) {
        long h = index * 0x9E3779B97F4A7C15L ^ frame * 0xBF58476D1CE4E5B9L ^ salt * 0x94D049BB133111EBL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return ((int) (h & 0xFFFF) / 32768.0F) - 1.0F;
    }

    private static Typeface.Style style() {
        if (!stylesTried) {
            stylesTried = true;
            TtfFont font = TtfFont.load(FONT_RESOURCE, FONT_SIZE, 1.0F, 4096);
            if (font != null) {
                style = Typeface.fromFont(font, FONT_SIZE, LINE_HEIGHT);
            }
        }
        return style != null ? style : MusicTypeface.HEADLINE;
    }

    private static Typeface.Style translationStyle() {
        if (translationStyle == null) {
            translationStyle = Typeface.fromFont(style().font(), FONT_SIZE * TRANSLATION_SCALE,
                    LINE_HEIGHT * TRANSLATION_SCALE);
        }
        return translationStyle;
    }

    // ------------------------------------------------------------------ //
    // One floating line
    // ------------------------------------------------------------------ //

    private static final class Glyph {
        final String text;
        final float x;
        final float y;
        final float width;
        final int word;
        final int index;
        final long appearMs;

        Glyph(char c, float x, float y, float width, int word, int index, long appearMs) {
            this.text = String.valueOf(c);
            this.x = x;
            this.y = y;
            this.width = width;
            this.word = word;
            this.index = index;
            this.appearMs = appearMs;
        }
    }

    private static final class Floating {
        final LyricLine line;
        final List<Glyph> glyphs = new ArrayList<Glyph>();
        final List<String> translation = new ArrayList<String>();
        final float spreadYaw;
        final float spreadPitch;
        final float rowsHeight;
        final boolean rainbow;
        long fadeStart;
        double x;
        double y;
        double z;
        boolean placed;

        Floating(LyricLine line, MusicManager.Lyrics3DSettings l3) {
            this.line = line;
            float spread = l3.spread;
            this.spreadYaw = (RANDOM.nextFloat() * 2.0F - 1.0F) * spread;
            this.spreadPitch = (RANDOM.nextFloat() * 2.0F - 1.0F) * spread * 0.35F;
            this.rainbow = l3.rainbow;
            this.rowsHeight = layout();
            if (l3.translation) {
                String extra = line.translation.isEmpty() ? line.romanisation : line.translation;
                if (!extra.isEmpty()) {
                    translation.addAll(wrap(extra, MAX_WIDTH * 1.15F));
                }
            }
        }

        /** Lays the line out as wrapped rows and returns the block height in font pixels. */
        private float layout() {
            Typeface.Style style = style();
            String text = line.text;
            int[] wordOf = new int[text.length()];
            java.util.Arrays.fill(wordOf, -1);
            int cursor = 0;
            for (int w = 0; w < line.words.size() && cursor < text.length(); w++) {
                String word = line.words.get(w).text;
                while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                    cursor++;
                }
                for (int k = 0; k < word.length() && cursor < text.length(); k++) {
                    wordOf[cursor++] = w;
                }
            }
            List<String> rows = wrap(text, MAX_WIDTH);
            List<Integer> starts = new ArrayList<Integer>();
            int start = 0;
            for (String row : rows) {
                starts.add(Integer.valueOf(start));
                start += row.length();
            }
            int total = 0;
            for (String row : rows) {
                total += row.length();
            }
            long duration = Math.max(400L, line.endMs - line.timeMs);
            long reveal = Math.min(Math.max(250L, total * 55L), (long) (duration * 0.6D));
            float top = -(rows.size() * LINE_HEIGHT) / 2.0F;
            int index = 0;
            for (int r = 0; r < rows.size(); r++) {
                String row = rows.get(r);
                float width = style.width(row);
                float x = -width * 0.5F;
                float y = top + r * LINE_HEIGHT;
                int offset = starts.get(r).intValue();
                for (int i = 0; i < row.length(); i++) {
                    char c = row.charAt(i);
                    float advance = style.width(String.valueOf(c));
                    if (c != ' ') {
                        int global = offset + i;
                        int word = global < wordOf.length ? wordOf[global] : -1;
                        long appear = word >= 0 ? line.words.get(word).startMs - line.timeMs
                                : (total <= 1 ? 0L : (long) ((index / (float) Math.max(1, total - 1)) * reveal));
                        glyphs.add(new Glyph(c, x, y, advance, word, index, appear));
                    }
                    x += advance;
                    index++;
                }
            }
            return rows.size() * LINE_HEIGHT;
        }
    }

    /** Wraps at {@code maxWidth} pixels; Latin breaks on spaces, CJK anywhere (Dango's rule). */
    private static List<String> wrap(String text, float maxWidth) {
        Typeface.Style style = style();
        List<String> rows = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            if (style.width(current.toString()) > maxWidth && current.length() > 1) {
                int space = current.lastIndexOf(" ");
                int cut = space > 0 ? space : current.length() - 1;
                rows.add(current.substring(0, cut));
                String rest = current.substring(cut);
                current.setLength(0);
                current.append(rest);
                if (rows.size() >= 3) {
                    break;
                }
            }
        }
        if (current.length() > 0) {
            rows.add(current.toString());
        }
        return rows;
    }
}
