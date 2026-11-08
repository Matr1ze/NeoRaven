package xyz.daikirai.music;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.onyx.render.WavyProgressIndicator;

import java.util.List;

/**
 * The in-window lyric view.
 *
 * <p>Melodify's karaoke view is a spring-scrolled stack of lines where the sung part of the
 * current line is drawn in the highlight colour, feathered by a custom sweep shader
 * ({@code sweepText.frag}). The port keeps the same timing model — each fragment is filled as
 * playback crosses its start/end, the head interpolates inside the current fragment, the active
 * line is the one whose {@code endMs} has not passed — but renders the fill with a scissor clip
 * around the head instead of a shader, so it works with the existing TTF-glyph pipeline and the
 * CJK fallback font.</p>
 */
final class LyricPanel {

    private static final float PITCH_BASE = 46.0F;
    private static final float FADE_LINES = 3.2F;
    /** Pixel height of the soft dissolve at the panel's top and bottom edges. */
    private static final float EDGE_FADE = 30.0F;

    private final MusicScreen screen;
    private final Spring scroll = new Spring(0.0F);
    private final Spring scale = new Spring(1.0F);

    /**
     * Apple Music style focus: every line eases towards "active" (1) or "idle" (0.34) alpha instead
     * of only being dimmed by its distance, and the active line is drawn 5% larger.
     */
    private static final float IDLE_LINE_ALPHA = 0.34F;
    private static final float ACTIVE_LINE_SCALE = 1.05F;
    private float[] lineAlphas = new float[0];

    private int activeIndex = -1;
    private long activeChangedAt;
    private int manualOffset;
    private long manualAt;
    private int cachedScalePercent = -1;
    private Typeface.Style activeStyle;
    private Typeface.Style idleStyle;
    private Typeface.Style translationStyle;

    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;
    private float closeX;
    private float closeY;
    private final java.util.List<float[]> lineHitBoxes = new java.util.ArrayList<float[]>();
    private final java.util.List<Integer> lineHitIndices = new java.util.ArrayList<Integer>();

    LyricPanel(MusicScreen screen) {
        this.screen = screen;
    }

    void reset() {
        activeIndex = -1;
        manualOffset = 0;
        scroll.set(0.0F);
        scale.set(1.0F);
    }

    void nudge(float direction) {
        manualOffset = (int) Material.clamp(manualOffset + direction, -3.0F, 3.0F);
        manualAt = System.currentTimeMillis();
    }

    void tick(float delta) {
        MusicManager manager = MusicManager.get();
        List<LyricLine> lines = manager.lyrics();
        int index = indexAt(lines, manager.positionMs());
        if (index != activeIndex) {
            activeIndex = index;
            activeChangedAt = System.currentTimeMillis();
            manualOffset = 0;
        }
        if (System.currentTimeMillis() - manualAt > 2600L) {
            manualOffset = 0;
        }
        float target = activeIndex < 0 ? 0.0F : activeIndex + manualOffset;
        scroll.target(target);
        scroll.update(delta);
        scale.target(1.0F);
        scale.update(delta);
        updateLineAlphas(lines, delta);
    }

    /** Eases each visible line's focus; rebuilt when the lyric list changes (track switch). */
    private void updateLineAlphas(List<LyricLine> lines, float delta) {
        if (lineAlphas.length != lines.size()) {
            lineAlphas = new float[lines.size()];
            for (int i = 0; i < lineAlphas.length; i++) {
                lineAlphas[i] = i == activeIndex ? 1.0F : IDLE_LINE_ALPHA;
            }
            return;
        }
        float rate = 1.0F - (float) Math.exp(-Math.max(1.0F, delta) / 110.0F);
        for (int i = 0; i < lineAlphas.length; i++) {
            if (Math.abs(i - activeIndex) > 8) {
                continue;
            }
            float wanted = i == activeIndex ? 1.0F : IDLE_LINE_ALPHA;
            lineAlphas[i] += (wanted - lineAlphas[i]) * rate;
        }
    }

    private float lineAlpha(int index) {
        return index >= 0 && index < lineAlphas.length ? lineAlphas[index] : 1.0F;
    }

    /** Line whose end has not passed yet, matching Melodify's {@code findCurrentLine}. */
    static int indexAt(List<LyricLine> lines, long positionMs) {
        if (lines.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < lines.size(); i++) {
            if (positionMs < lines.get(i).endMs) {
                return i;
            }
        }
        return lines.size() - 1;
    }

    // ------------------------------------------------------------------ //
    // Drawing
    // ------------------------------------------------------------------ //

    void draw(UI ui, Scheme scheme, float x, float y, float w, float h) {
        this.panelX = x;
        this.panelY = y;
        this.panelW = w;
        this.panelH = h;
        lineHitBoxes.clear();
        lineHitIndices.clear();

        MusicManager manager = MusicManager.get();
        Song song = manager.current();

        // Same treatment as the rest of the window: frosted glass over the blurred background,
        // falling back to a translucent surface when the frost pipeline is unavailable.
        if (Theme.frost() && Blur.ready()) {
            ui.rounded(x, y, w, h, 22.0F, Material.withAlpha(scheme.surfaceContainer, 0.55F));
            ui.frost(x, y, w, h, 22.0F, scheme.surfaceContainer, 0.82F);
        } else {
            ui.rounded(x, y, w, h, 22.0F, Material.withAlpha(scheme.surfaceContainer, 0.93F));
        }
        // A soft wash of the palette at the top keeps the panel from reading as a black hole.
        ui.gradient4(x, y, w, 120.0F, 22.0F,
                Material.withAlpha(scheme.primary, 0.10F), 0x00000000, 0x00000000,
                Material.withAlpha(scheme.primary, 0.02F));

        ui.text(MusicTypeface.HEADLINE, song == null ? "未在播放" : ui.truncate(MusicTypeface.HEADLINE, song.name, w - 120.0F),
                x + 24.0F, y + 26.0F, scheme.onSurface, UI.ALIGN_LEFT);
        ui.text(MusicTypeface.CAPTION, song == null ? "" : ui.truncate(MusicTypeface.CAPTION, song.artist, w - 120.0F),
                x + 24.0F, y + 42.0F, Material.withAlpha(scheme.onSurfaceVariant, 0.9F), UI.ALIGN_LEFT);

        float closeSize = 32.0F;
        this.closeX = x + w - closeSize - 12.0F;
        this.closeY = y + 10.0F;
        boolean closeHovered = screen.mouseIn(closeX, closeY, closeSize, closeSize);
        if (closeHovered) {
            ui.circle(closeX + closeSize / 2.0F, closeY + closeSize / 2.0F, closeSize / 2.0F,
                    Material.withAlpha(scheme.onSurface, 0.08F));
        }
        ui.icon("\ue5cd", closeX + closeSize / 2.0F, closeY + closeSize / 2.0F, 17.0F,
                Material.withAlpha(scheme.onSurface, 0.82F));

        List<LyricLine> lines = manager.lyrics();
        if (lines.isEmpty()) {
            String message = manager.lyricsLoading() ? "正在加载歌词…" : "暂无歌词";
            ui.text(MusicTypeface.BODY, message, x + w / 2.0F, y + h / 2.0F,
                    Material.withAlpha(scheme.onSurfaceVariant, 0.9F), UI.ALIGN_CENTER);
            if (manager.lyricsLoading()) {
                WavyProgressIndicator.drawCircularIndeterminate(ui, x + w / 2.0F, y + h / 2.0F - 30.0F, 22.0F);
            }
            return;
        }

        prepareStyles(manager.lyricScale());
        float pitch = PITCH_BASE * manager.lyricScale();
        float centerY = y + h * 0.47F;
        long position = manager.positionMs();
        float scrollValue = scroll.value();
        float top = y + 56.0F;
        float bottom = y + h - 8.0F;

        ui.scissorPush(x + 4.0F, top, w - 8.0F, bottom - top);
        for (int i = 0; i < lines.size(); i++) {
            float distance = i - scrollValue;
            if (distance < -FADE_LINES - 0.6F || distance > FADE_LINES + 0.6F) {
                continue;
            }
            LyricLine line = lines.get(i);
            float lineY = centerY + distance * pitch;
            float fade = 1.0F - Math.min(1.0F, Math.abs(distance) / FADE_LINES);
            // Dissolve into the top/bottom edges by pixel distance instead of painting a black
            // gradient over the frosted panel: the lines fade out before the scissor cuts them.
            float edge = Math.min(edgeFade(lineY, top, bottom, EDGE_FADE), fade);
            float alpha = Math.max(0.0F, Math.min(fade, edge));
            if (alpha <= 0.01F) {
                continue;
            }
            boolean active = i == activeIndex;
            float focus = Math.max(0.0F, Math.min(1.0F, lineAlpha(i)));
            alpha *= focus;
            if (alpha <= 0.01F) {
                continue;
            }
            if (active) {
                // Apple Music draws the focused line slightly larger, pivoting on its own centre.
                ui.push();
                ui.scale(ACTIVE_LINE_SCALE, x + w / 2.0F, lineY);
                drawLine(ui, scheme, line, i, lineY, alpha, true, position, x, w);
                ui.pop();
            } else {
                drawLine(ui, scheme, line, i, lineY, alpha, false, position, x, w);
            }
            lineHitBoxes.add(new float[]{x + 12.0F, lineY - pitch * 0.5F, w - 24.0F, pitch});
            lineHitIndices.add(Integer.valueOf(i));
        }
        ui.scissorPop();
    }

    /** Soft ramp so glyphs reach zero alpha right at the panel edge, with no visible cut. */
    private static float edgeFade(float lineY, float top, float bottom, float height) {
        float above = (lineY - top) / height;
        float below = (bottom - lineY) / height;
        float t = Material.clamp(Math.min(above, below), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private void prepareStyles(float lyricScale) {
        int percent = Math.round(lyricScale * 100.0F);
        if (percent == cachedScalePercent && activeStyle != null) {
            return;
        }
        cachedScalePercent = percent;
        float active = 25.0F * lyricScale;
        float idle = 20.0F * lyricScale;
        float translation = 12.0F * lyricScale;
        activeStyle = MusicTypeface.style(active, active * 1.3F, true);
        idleStyle = MusicTypeface.style(idle, idle * 1.3F, false);
        translationStyle = MusicTypeface.style(translation, translation * 1.35F, false);
    }

    private void drawLine(UI ui, Scheme scheme, LyricLine line, int index, float lineY, float alpha,
                          boolean active, long position, float panelLeft, float panelWidth) {
        float layoutWidth = panelWidth - 48.0F;
        Typeface.Style style = active ? activeStyle : idleStyle;
        List<LyricWord> words = line.words;

        // Long lines are wrapped at the panel width; the sweep then runs over the wrapped runs.
        List<String> parts = new java.util.ArrayList<String>();
        List<Integer> partStart = new java.util.ArrayList<Integer>();
        if (style.width(line.text) <= layoutWidth) {
            parts.add(line.text);
            partStart.add(Integer.valueOf(0));
        } else {
            StringBuilder current = new StringBuilder();
            int start = 0;
            for (int w = 0; w < words.size(); w++) {
                LyricWord word = words.get(w);
                String candidate = current.length() == 0 ? word.text : current.toString() + word.text;
                if (style.width(candidate) > layoutWidth && current.length() > 0) {
                    parts.add(current.toString());
                    partStart.add(Integer.valueOf(start));
                    current.setLength(0);
                    start = w;
                    current.append(word.text);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            if (current.length() > 0) {
                parts.add(current.toString());
                partStart.add(Integer.valueOf(start));
            }
        }
        if (parts.isEmpty()) {
            parts.add(line.text);
            partStart.add(Integer.valueOf(0));
        }

        float wrapLineHeight = style.lineHeight * 0.62F;
        float blockHeight = parts.size() * wrapLineHeight;
        float left = panelLeft + 24.0F;

        float focusAlpha = Material.clamp(alpha, 0.0F, 1.0F);
        if (active) {
            float highlight = focusAlpha * 0.06F;
            ui.gradient4(panelLeft + 8.0F, lineY - blockHeight / 2.0F - 8.0F, panelWidth - 16.0F,
                    blockHeight + 16.0F, 12.0F,
                    Material.withAlpha(scheme.primary, highlight), Material.withAlpha(scheme.primary, highlight),
                    Material.withAlpha(scheme.primary, highlight), Material.withAlpha(scheme.primary, highlight));
        }

        // Apple Music keeps the focused line's unsung text dim and its sung part bright; other lines
        // sit at a uniform low alpha (their per-line focus is already folded into alpha).
        float baseAlpha = active ? (Theme.dark() ? 0.42F : 0.72F) : (Theme.dark() ? 0.55F : 0.68F);
        int baseColor = active ? Material.withAlpha(scheme.onSurface, baseAlpha * focusAlpha)
                : Material.withAlpha(scheme.onSurfaceVariant, baseAlpha * focusAlpha);
        int sungColor = active
                ? Material.withAlpha(Material.mix(scheme.primary, scheme.onSurface, 0.25F), focusAlpha)
                : Material.withAlpha(scheme.onSurfaceVariant, baseAlpha * focusAlpha);

        for (int p = 0; p < parts.size(); p++) {
            String text = parts.get(p);
            float lineTop = lineY - blockHeight / 2.0F + p * wrapLineHeight + wrapLineHeight / 2.0F;
            ui.text(style, text, left, lineTop, baseColor, UI.ALIGN_LEFT);
            float sungWidth = sungWidthFor(words, partStart.get(p).intValue(), text, style, position, active);
            if (sungWidth > 0.5F) {
                ui.scissorPush(left - 2.0F, lineTop - wrapLineHeight, sungWidth + 2.0F, wrapLineHeight * 2.0F);
                ui.text(style, text, left, lineTop, sungColor, UI.ALIGN_LEFT);
                ui.scissorPop();
            }
        }

        String translation = line.translation.isEmpty() ? line.romanisation : line.translation;
        if (!translation.isEmpty()) {
            ui.text(translationStyle, ui.truncate(translationStyle, translation, layoutWidth),
                    left, lineY - blockHeight / 2.0F + blockHeight + 10.0F,
                    Material.withAlpha(scheme.onSurfaceVariant, alpha * (active ? 0.85F : 0.55F)), UI.ALIGN_LEFT);
        }
    }

    /**
     * Width of the sung part of one wrapped run: fully sung fragments contribute their measured
     * width, the fragment being sung contributes a linear fraction, everything after is zero.
     */
    private static float sungWidthFor(List<LyricWord> words, int firstWord, String text,
                                      Typeface.Style style, long position, boolean active) {
        if (words.isEmpty()) {
            return 0.0F;
        }
        if (!active) {
            // Passed lines are fully sung; upcoming lines are not.
            return words.get(0).startMs <= position ? style.width(text) : 0.0F;
        }
        float width = 0.0F;
        for (int i = firstWord; i < words.size(); i++) {
            LyricWord word = words.get(i);
            float part = style.width(word.text);
            if (position >= word.endMs) {
                width += part;
                continue;
            }
            if (position > word.startMs) {
                float fraction = (position - word.startMs) / (float) word.duration();
                width += part * Material.clamp(fraction, 0.0F, 1.0F);
            }
            break;
        }
        return width;
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    boolean mouseClicked(float mouseX, float mouseY) {
        if (mouseX >= closeX && mouseX < closeX + 32.0F && mouseY >= closeY && mouseY < closeY + 32.0F) {
            screen.setLyricOpen(false);
            return true;
        }
        List<LyricLine> lines = MusicManager.get().lyrics();
        for (int i = 0; i < lineHitBoxes.size() && i < lineHitIndices.size(); i++) {
            float[] box = lineHitBoxes.get(i);
            if (mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[3]) {
                int index = lineHitIndices.get(i).intValue();
                if (index >= 0 && index < lines.size()) {
                    MusicManager.get().seek(lines.get(index).timeMs);
                    screen.setLyricOpen(true);
                }
                return true;
            }
        }
        return false;
    }
}
