package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Typeface;

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

    static void render(LyricsModule module, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        MusicManager manager = MusicManager.get();
        Song song = manager.current();
        if (module == null || song == null || mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return;
        }
        List<LyricLine> lines = manager.lyrics();
        int index = LyricPanel.indexAt(lines, manager.positionMs());
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
        String translation = module.showTranslation() && line.translation != null
                ? line.translation.trim() : "";

        long now = System.currentTimeMillis();
        if (song.id != songId || index != lineIndex) {
            songId = song.id;
            lineIndex = index;
            lineText = text;
            lineTranslation = translation;
            spawnAnchor(module, mc.thePlayer, index);
            spawnedAt = now;
        } else {
            lineText = text;
            lineTranslation = translation;
        }
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
        float textLeft = -Math.max(lineWidth, translationWidth) / 2.0F;
        float translationY = lineStyle.font().size() + 2.0F;
        int alpha = Math.round(fade * 255.0F);
        int lineColor = alpha << 24 | 0x00F2F3F7;
        int translationColor = Math.round(fade * 0.82F * 255.0F) << 24 | 0x00D6D8E0;
        int shadowColor = Math.round(fade * 0.55F * 255.0F) << 24;

        float distance = clamp(module.distance(), 5.0F, 120.0F);
        float scale = BASE_SCALE * clamp(module.scale(), 0.5F, 3.0F)
                * (1.0F + distance / 90.0F);

        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(anchorX - renderManager.viewerPosX,
                    anchorY - renderManager.viewerPosY, anchorZ - renderManager.viewerPosZ);
            GlStateManager.rotate(-renderManager.playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(renderManager.playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);

            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableFog();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 771);
            GlStateManager.disableLighting();
            GlStateManager.disableAlpha();

            drawShadowed(lineStyle, lineText, textLeft, 0.0F, lineColor, shadowColor);
            if (!lineTranslation.isEmpty()) {
                drawShadowed(translationStyle, lineTranslation, textLeft, translationY,
                        translationColor, shadowColor);
            }
        } finally {
            GlStateManager.enableAlpha();
            GlStateManager.enableLighting();
            GlStateManager.enableFog();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    /**
     * Capture a fixed point at this line's start. Side and height alternate slightly so two
     * consecutive lines never occupy the exact same place when the player stands still.
     */
    private static void spawnAnchor(LyricsModule module, Entity player, int index) {
        double yaw = Math.toRadians(player.rotationYaw);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = -Math.cos(yaw);
        double rightZ = -Math.sin(yaw);
        float distance = clamp(module.distance(), 5.0F, 120.0F);
        float side = module.sideOffset();
        side = (index & 1) == 0 ? side : -side;
        float height = module.height() + ((index % 3) - 1) * 0.35F;
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
}
