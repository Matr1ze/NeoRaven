package xyz.daikirai.music;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.util.ResourceLocation;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.WavyProgressIndicator;
import xyz.daikirai.module.render.WatermarkModule;

import java.util.List;

/** Shared island artwork and geometry, including the endpoint of the player-window morph. */
public final class MusicIsland {
    public static final float MINI_WIDTH = 296.0F;
    public static final float MINI_HEIGHT = 130.0F;
    public static final float PROGRESS_X = 45.0F;
    public static final float PROGRESS_Y = 72.0F;
    public static final float CONTROLS_Y = 104.0F;
    private static long miniUntil;
    private static final Anim lyricFade = new Anim(1.0F);
    private static String lyric = "";
    private static String previousLyric = "";

    private MusicIsland() { }

    public static boolean hasTrack() {
        return MusicManager.get().current() != null;
    }

    /** User setting: the compact card is authored at 296x130 and scaled as a whole. */
    public static float miniSize() {
        return Material.clamp(WatermarkModule.miniSizeFactor(), 0.5F, 2.0F);
    }

    public static float miniWidth() {
        return MINI_WIDTH * miniSize();
    }

    public static float miniHeight() {
        return MINI_HEIGHT * miniSize();
    }

    public static boolean miniVisible() {
        return hasTrack() && (Minecraft.getMinecraft().currentScreen instanceof GuiChat
                || System.nanoTime() < miniUntil);
    }

    /** The three-second hold starts after the returning window has reached the island. */
    public static void returned() {
        miniUntil = hasTrack() ? System.nanoTime() + 3000000000L : 0L;
    }

    public static float progress() {
        MusicManager manager = MusicManager.get();
        return manager.durationMs() <= 0L ? 0.0F
                : Material.clamp(manager.positionMs() / (float) manager.durationMs(), 0.0F, 1.0F);
    }

    public static String currentLyric() {
        MusicManager manager = MusicManager.get();
        List<LyricLine> lines = manager.lyrics();
        long position = manager.positionMs();
        // Seek and track switches can move backwards; binary search does not retain a stale index.
        int low = 0, high = lines.size() - 1, index = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= position) {
                index = mid;
                low = mid + 1;
            } else high = mid - 1;
        }
        if (index >= 0 && !lines.get(index).text.trim().isEmpty()) return lines.get(index).text;
        Song song = manager.current();
        return song == null ? "Music Player" : song.name;
    }

    public static void tick(float delta) {
        CoverCache.tick();
        String next = currentLyric();
        if (!next.equals(lyric)) {
            previousLyric = lyric;
            lyric = next;
            lyricFade.set(0.0F);
            lyricFade.to(1.0F, 240.0F, Ease.STANDARD);
        }
        lyricFade.update(delta);
    }

    /**
     * Draws the compact card only; the caller owns the single continuous rounded surface.
     *
     * <p>The card is authored at 296x130 and scaled as a whole by the user's "Mini size", so every
     * control stays on the same layout and the click map keeps using those design units.</p>
     */
    public static void draw(UI ui, Scheme scheme, float width, float height, float expansion) {
        if (!hasTrack()) return;
        MusicManager manager = MusicManager.get();
        Song song = manager.current();
        float mini = Material.clamp(expansion, 0.0F, 1.0F);
        if (mini < 0.01F) return;
        float size = miniSize();
        float cardWidth = MINI_WIDTH * size;
        float cardHeight = MINI_HEIGHT * size;
        ui.alphaPush(Material.clamp((mini - 0.2F) / 0.8F, 0.0F, 1.0F));
        ui.push();
        ui.translate((width - cardWidth) / 2.0F,
                (height - cardHeight) / 2.0F + (1.0F - mini) * -12.0F);
        ui.scale(size, 0.0F, 0.0F);
        drawCover(ui, scheme, song, 14.0F, 14.0F, 44.0F);
        drawLyric(ui, scheme, 68.0F, 25.0F, MINI_WIDTH - 112.0F, MusicTypeface.CARD_HEADLINE);
        String subtitle = song.artist.isEmpty() ? song.name : song.name + " - " + song.artist;
        ui.text(MusicTypeface.CARD_ARTIST, ui.truncate(MusicTypeface.CARD_ARTIST, subtitle, MINI_WIDTH - 84.0F),
                68.0F, 47.0F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
        ui.rounded(MINI_WIDTH - 38.0F, 15.0F, 24.0F, 24.0F, 7.0F, scheme.primaryContainer);
        ui.icon("\ue405", MINI_WIDTH - 26.0F, 27.0F, 18.0F, scheme.onPrimaryContainer);
        WavyProgressIndicator.drawLinear(ui, PROGRESS_X, PROGRESS_Y - 4.0F, MINI_WIDTH - PROGRESS_X * 2.0F,
                8.0F, progress(), new WavyProgressIndicator.Style(22.0F, 0.85F, 22.0F,
                        scheme.primary, scheme.surfaceContainerHighest));
        ui.text(MusicTypeface.CARD_CLOCK, time(manager.positionMs()), 14.0F, PROGRESS_Y,
                scheme.onSurfaceVariant, UI.ALIGN_LEFT);
        ui.text(MusicTypeface.CARD_CLOCK, time(manager.durationMs()), MINI_WIDTH - 14.0F, PROGRESS_Y,
                scheme.onSurfaceVariant, UI.ALIGN_RIGHT);
        ui.icon(manager.repeat() == MusicManager.Repeat.ONE ? "\ue041" : "\ue040", 30.0F, CONTROLS_Y, 20.0F,
                manager.repeat() == MusicManager.Repeat.OFF ? scheme.onSurfaceVariant : scheme.primary);
        ui.icon("\ue045", MINI_WIDTH / 2.0F - 50.0F, CONTROLS_Y, 23.0F, scheme.onSurface);
        ui.rounded(MINI_WIDTH / 2.0F - 18.0F, CONTROLS_Y - 18.0F, 36.0F, 36.0F, 18.0F,
                Material.withAlpha(scheme.onSurface, 0.10F));
        ui.outline(MINI_WIDTH / 2.0F - 18.0F, CONTROLS_Y - 18.0F, 36.0F, 36.0F, 18.0F, 0.5F,
                Material.withAlpha(scheme.onSurface, 0.12F));
        ui.icon(manager.isPlaying() ? "\ue034" : "\ue037", MINI_WIDTH / 2.0F, CONTROLS_Y,
                28.0F, scheme.onSurface);
        ui.icon("\ue044", MINI_WIDTH / 2.0F + 50.0F, CONTROLS_Y, 23.0F, scheme.onSurface);
        ui.icon("\ue5d0", MINI_WIDTH - 30.0F, CONTROLS_Y, 20.0F, scheme.onSurfaceVariant);
        ui.pop();
        ui.alphaPop();
    }

    private static void drawCover(UI ui, Scheme scheme, Song song, float x, float y, float size) {
        ResourceLocation cover = CoverCache.get(song.cover(96));
        if (cover != null) ui.imageRounded(cover, x, y, size, size, size * 0.24F, -1);
        else {
            ui.rounded(x, y, size, size, size * 0.24F, scheme.primaryContainer);
            ui.icon("\ue405", x + size / 2.0F, y + size / 2.0F, size * 0.55F, scheme.onPrimaryContainer);
        }
    }

    private static void drawLyric(UI ui, Scheme scheme, float x, float y, float maxWidth) {
        drawLyric(ui, scheme, x, y, maxWidth, MusicTypeface.LABEL_TIGHT);
    }

    private static void drawLyric(UI ui, Scheme scheme, float x, float y, float maxWidth, Typeface.Style style) {
        float t = lyricFade.value();
        ui.alphaPush(1.0F - t);
        ui.text(style, ui.truncate(style, previousLyric, maxWidth),
                x, y - t * 5.0F, scheme.onSurface, UI.ALIGN_LEFT);
        ui.alphaPop();
        ui.alphaPush(t);
        ui.text(style, ui.truncate(style, lyric, maxWidth),
                x, y + (1.0F - t) * 5.0F, scheme.onSurface, UI.ALIGN_LEFT);
        ui.alphaPop();
    }

    /** Shares the transport layout with drawing, so chat clicks follow the compact card exactly. */
    public static boolean click(float x, float y) {
        MusicManager manager = MusicManager.get();
        if (x >= 14.0F && x <= 58.0F && y >= 14.0F && y <= 58.0F) {
            Minecraft.getMinecraft().displayGuiScreen(new MusicScreen());
        } else if (y >= PROGRESS_Y - 10.0F && y <= PROGRESS_Y + 10.0F
                && x >= PROGRESS_X && x <= MINI_WIDTH - PROGRESS_X) {
            manager.seek((long) (manager.durationMs() * (x - PROGRESS_X) / (MINI_WIDTH - 2.0F * PROGRESS_X)));
        } else if (Math.abs(y - CONTROLS_Y) <= 20.0F) {
            if (Math.abs(x - 30.0F) < 18.0F) manager.cycleRepeat();
            else if (Math.abs(x - (MINI_WIDTH / 2.0F - 50.0F)) < 20.0F) manager.previous();
            else if (Math.abs(x - MINI_WIDTH / 2.0F) < 22.0F) manager.togglePlay();
            else if (Math.abs(x - (MINI_WIDTH / 2.0F + 50.0F)) < 20.0F) manager.next();
            else if (Math.abs(x - (MINI_WIDTH - 30.0F)) < 18.0F)
                Minecraft.getMinecraft().displayGuiScreen(new MusicScreen());
            else return false;
        } else return false;
        return true;
    }

    public static String time(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        return seconds / 60L + ":" + (seconds % 60L < 10 ? "0" : "") + seconds % 60L;
    }
}
