package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import xyz.daikirai.gui.onyx.render.Typeface;

import java.nio.FloatBuffer;
import java.util.List;

/**
 * Draws one world-anchored lyric line at a time.
 *
 * <p>An anchor is captured when the active line changes, using the player's yaw at that moment.
 * It is never moved afterwards. The text billboard faces the camera for readability, but its
 * world position remains fixed, so walking toward it changes its distance and can bring the
 * player right up to the line.</p>
 */
final class LyricsWorld {

    private static final long FADE_MS = 180L;
    private static final float BASE_SCALE = 0.018F;

    private static long songId = Long.MIN_VALUE;
    private static int lineIndex = -1;
    private static String lineText = "";
    private static String lineTranslation = "";
    private static long spawnedAt;
    private static double anchorX;
    private static double anchorY;
    private static double anchorZ;

    LyricsWorld() {
    }

    static void reset() {
        songId = Long.MIN_VALUE;
        lineIndex = -1;
        lineText = "";
        lineTranslation = "";
        spawnedAt = 0L;
        anchorX = 0.0D;
        anchorY = 0.0D;
        anchorZ = 0.0D;
    }

    static void render(MusicManager.Lyrics3DSettings l3, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        MusicManager manager = MusicManager.get();
        Song song = manager.current();
        if (l3 == null || song == null || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        List<LyricLine> lines = manager.lyrics();
        long position = manager.positionMs();
        int index = LyricPanel.indexAt(lines, position);
        if (index < 0 || index >= lines.size()) {
            reset();
            return;
        }
        LyricLine line = lines.get(index);
        String text = line.text == null ? "" : line.text.trim();
        if (text.isEmpty()) {
            reset();
            return;
        }
        String translation = "";
        if (l3.translation) {
            String extra = line.translation == null || line.translation.trim().isEmpty()
                    ? (line.romanisation == null ? "" : line.romanisation) : line.translation;
            translation = extra.trim();
        }

        long now = System.currentTimeMillis();
        if (song.id != songId || index != lineIndex) {
            songId = song.id;
            lineIndex = index;
            spawnAnchor(l3, mc.thePlayer, index);
            spawnedAt = now;
        }
        lineText = line.text;
        lineTranslation = translation;
        float fade = spawnedAt == 0L ? 1.0F
                : clamp((now - spawnedAt) / (float) FADE_MS, 0.0F, 1.0F);
        if (fade <= 0.01F) {
            return;
        }

        Entity view = mc.getRenderViewEntity();
        if (view == null) {
            return;
        }
        RenderManager renderManager = mc.getRenderManager();
        Typeface.Style lineStyle = Typeface.HEADLINE;
        Typeface.Style translationStyle = Typeface.LABEL;
        float lineWidth = lineStyle.width(lineText);
        float translationWidth = lineTranslation.isEmpty() ? 0.0F : translationStyle.width(lineTranslation);
        float translationY = lineStyle.font().size() + 2.0F;
        int shadowAlpha = Math.round(fade * 0.55F * 255.0F);
        // The translation fades in over the first 400 ms of the line, under the original text.
        float translationFade = fade * clamp((position - line.timeMs) / 400.0F, 0.0F, 1.0F);
        int translationColor = Math.round(translationFade * 0.82F * 255.0F) << 24 | 0x00D6D8E0;
        int translationShadow = Math.round(translationFade * 0.55F * 255.0F) << 24;

        float distance = clamp(l3.distance, 3.0F, 48.0F);
        float scale = BASE_SCALE * clamp(l3.scale, 0.4F, 2.5F)
                * (1.0F + distance / 90.0F);

        try (State state = new State()) {
            state.prepare();
            GlStateManager.pushMatrix();
            try {
                GlStateManager.translate(anchorX - renderManager.viewerPosX,
                        anchorY - renderManager.viewerPosY, anchorZ - renderManager.viewerPosZ);
                GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
                GlStateManager.scale(-scale, -scale, scale);

                drawStreaming(lineStyle, line, position, -lineWidth / 2.0F, fade, shadowAlpha);
                if (!lineTranslation.isEmpty() && translationFade > 0.01F) {
                    drawShadowed(translationStyle, lineTranslation, -translationWidth / 2.0F, translationY,
                            translationColor, translationShadow);
                }
            } finally {
                GlStateManager.popMatrix();
            }
        }
    }

    /**
     * Streams the line in the same way the player window does: each character appears when it is
     * sung (word timings), or evenly across the start of the line when the song has no word
     * timings, and brightens from a dim ink to full white as it is sung.
     */
    private static void drawStreaming(Typeface.Style style, LyricLine line, long position, float left,
                                      float fade, int shadowAlpha) {
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
        long elapsed = position - line.timeMs;
        long duration = Math.max(400L, line.endMs - line.timeMs);
        long reveal = Math.min(Math.max(250L, text.length() * 55L), (long) (duration * 0.6D));
        float lineSung = line.progress(position);
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            float x = left + style.width(prefix.toString());
            prefix.append(c);
            if (c == ' ') {
                continue;
            }
            int word = wordOf[i];
            long appear;
            float sung;
            if (word >= 0) {
                LyricWord w = line.words.get(word);
                appear = w.startMs - line.timeMs;
                sung = w.progress(position);
            } else {
                appear = text.length() <= 1 ? 0L : (long) (i / (float) (text.length() - 1) * reveal);
                sung = lineSung;
            }
            long t = elapsed - appear;
            if (t < 0L) {
                continue;
            }
            float appearK = clamp(t / 160.0F, 0.0F, 1.0F);
            appearK = appearK * appearK * (3.0F - 2.0F * appearK);
            float a = fade * appearK * (0.45F + 0.55F * clamp(sung, 0.0F, 1.0F));
            if (a <= 0.01F) {
                continue;
            }
            float lift = (1.0F - appearK) * 4.0F;
            String glyph = String.valueOf(c);
            int shadow = Math.round(shadowAlpha * appearK) << 24;
            drawShadowed(style, glyph, x, -lift, Math.round(a * 255.0F) << 24 | 0x00F2F3F7, shadow);
        }
    }

    /**
     * Capture a fixed point at this line's start. Side and height alternate slightly so two
     * consecutive lines never occupy the exact same place when the player stands still.
     */
    private static void spawnAnchor(MusicManager.Lyrics3DSettings l3, Entity player, int index) {
        double yaw = Math.toRadians(player.rotationYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        float distance = clamp(l3.distance, 3.0F, 48.0F);
        float side = l3.offsetX;
        side = (index & 1) == 0 ? side : -side;
        float height = l3.offsetY + ((index % 3) - 1) * 0.35F;
        double px = player.posX;
        double py = player.posY + player.getEyeHeight();
        double pz = player.posZ;
        anchorX = px + forwardX * distance + rightX * side;
        anchorY = py + height;
        anchorZ = pz + forwardZ * distance + rightZ * side;
    }

    private static void drawShadowed(Typeface.Style style, String text, float x, float y,
                                     int color, int shadowColor) {
        drawAt(style, text, x + 1.0F, y + 1.0F, shadowColor);
        drawAt(style, text, x, y, color);
    }

    private static void drawAt(Typeface.Style style, String text, float x, float y, int color) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        style.draw(text, color);
        GlStateManager.popMatrix();
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }

    /**
     * Preserves every global render switch the lyric text touches. The previous implementation
     * unconditionally re-enabled fog, lighting, alpha and depth afterwards, which tinted the whole
     * world whenever the module was active. Both the raw GL state and GlStateManager's cached
     * switches are restored here.
     */
    private static final class State implements AutoCloseable {
        private final int activeUnit;
        private final int activeTexture;
        private final boolean activeTextured;
        private final int program;
        private final int[] units = {OpenGlHelper.defaultTexUnit, OpenGlHelper.lightmapTexUnit};
        private final int[] textures = new int[2];
        private final boolean[] textured = new boolean[2];
        private final boolean blend;
        private final boolean alphaTest;
        private final boolean depthTest;
        private final boolean lighting;
        private final boolean fog;
        private final boolean cull;
        private final boolean colorMaterial;
        private final boolean depthMask;
        private final int depthFunc;
        private final int alphaFunc;
        private final float alphaRef;
        private final int blendSrc;
        private final int blendDst;
        private final int blendSrcAlpha;
        private final int blendDstAlpha;
        private final int shade;
        private final float[] color;

        State() {
            activeUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            activeTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            activeTextured = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            program = GL11.glGetInteger(0x8B8D);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
            fog = GL11.glIsEnabled(GL11.GL_FOG);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            colorMaterial = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            alphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);
            alphaRef = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);
            blendSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            shade = GL11.glGetInteger(GL11.GL_SHADE_MODEL);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buffer);
            color = new float[]{buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3)};
            for (int i = 0; i < units.length; i++) {
                GlStateManager.setActiveTexture(units[i]);
                textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                textured[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            }
            GlStateManager.setActiveTexture(activeUnit);
        }

        void prepare() {
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableFog();
            GlStateManager.disableLighting();
            GlStateManager.disableCull();
            GlStateManager.disableColorMaterial();
            GlStateManager.disableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA,
                    GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }

        @Override
        public void close() {
            if (blend) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (alphaTest) {
                GlStateManager.enableAlpha();
            } else {
                GlStateManager.disableAlpha();
            }
            if (depthTest) {
                GlStateManager.enableDepth();
            } else {
                GlStateManager.disableDepth();
            }
            if (lighting) {
                GlStateManager.enableLighting();
            } else {
                GlStateManager.disableLighting();
            }
            if (fog) {
                GlStateManager.enableFog();
            } else {
                GlStateManager.disableFog();
            }
            if (cull) {
                GlStateManager.enableCull();
            } else {
                GlStateManager.disableCull();
            }
            if (colorMaterial) {
                GlStateManager.enableColorMaterial();
            } else {
                GlStateManager.disableColorMaterial();
            }
            GlStateManager.depthMask(depthMask);
            GlStateManager.depthFunc(depthFunc);
            GlStateManager.alphaFunc(alphaFunc, alphaRef);
            GlStateManager.tryBlendFuncSeparate(blendSrc, blendDst, blendSrcAlpha, blendDstAlpha);
            GlStateManager.shadeModel(shade);
            GlStateManager.resetColor();
            GlStateManager.color(color[0], color[1], color[2], color[3]);
            GL11.glColor4f(color[0], color[1], color[2], color[3]);

            for (int i = 0; i < units.length; i++) {
                GlStateManager.setActiveTexture(units[i]);
                GlStateManager.bindTexture(textures[i]);
                if (textured[i]) {
                    GlStateManager.enableTexture2D();
                } else {
                    GlStateManager.disableTexture2D();
                }
            }
            GlStateManager.setActiveTexture(activeUnit);
            GlStateManager.bindTexture(activeTexture);
            if (activeTextured) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            OpenGlHelper.glUseProgram(program);
        }
    }
}
