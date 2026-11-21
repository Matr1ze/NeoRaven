package xyz.daikirai.gui.onyx.render;

import net.minecraft.client.renderer.GlStateManager;

/** Animates only ASCII numeric columns; labels, units and punctuation keep their own layout. */
public final class RollingNumber {
    private final DigitRoll roll = new DigitRoll();
    private String context = "";

    public void reset() { roll.clear(); context = ""; }

    public void draw(UI ui, String context, Typeface.Style style, String text, float x, float y, int color, int align) {
        drawAt(ui, context, style, text, x, y, color, align, System.nanoTime() / 1000000L);
    }

    /** Explicit timestamp keeps screenshots, snapshots and numeric tests on the exact same timeline. */
    public void drawAt(UI ui, String context, Typeface.Style style, String text, float x, float y, int color, int align, long now) {
        if (!this.context.equals(context)) { reset(); this.context = context; }
        if (text == null || text.indexOf('\u00a7') >= 0) { reset(); ui.text(style, text, x, y, color, align); return; }
        roll.update(text, now);
        if (!roll.active(now)) { ui.text(style, text, x, y, color, align); return; }
        float width = style.width(text);
        float left = align == UI.ALIGN_CENTER ? x - width / 2 : align == UI.ALIGN_RIGHT ? x - width : x;
        int runIndex = 0, spanStart = 0;
        for (int i = 0; i < text.length(); i++) {
            while (runIndex < roll.runs().size() && i >= roll.runs().get(runIndex).end) runIndex++;
            char c = text.charAt(i);
            DigitRoll.Digit digit = null;
            if (c >= '0' && c <= '9') {
                DigitRoll.Run run = roll.runs().get(runIndex);
                digit = roll.digit(runIndex, run.end - i - 1);
            }
            if (digit != null && digit.active(now)) {
                if (spanStart < i) ui.text(style, text.substring(spanStart, i),
                        left + start(style, text, spanStart), y, color, UI.ALIGN_LEFT);
                drawDigit(ui, style, digit, left + start(style, text, i) + style.width(String.valueOf(c)) / 2, y, color, now);
                spanStart = i + 1;
            }
        }
        if (spanStart < text.length()) ui.text(style, text.substring(spanStart),
                left + start(style, text, spanStart), y, color, UI.ALIGN_LEFT);
        // Retiring leading columns (100 -> 99, 10 -> 9) fade out to the left of their numeric run.
        for (DigitRoll.Digit digit : roll.digits()) {
            if (digit.to != ' ' || !digit.active(now)) continue;
            DigitRoll.Run run = roll.runs().get(digit.run);
            float advance = style.width(String.valueOf(digit.from));
            float start = left + style.width(text.substring(0, run.start));
            drawDigit(ui, style, digit, start - (digit.place - (run.end - run.start) + .5F) * advance, y, color, now);
        }
    }

    private static float start(Typeface.Style style, String text, int index) {
        return index == 0 ? 0 : style.width(text.substring(0, index + 1)) - style.width(text.substring(index, index + 1));
    }

    private void drawDigit(UI ui, Typeface.Style style, DigitRoll.Digit digit, float x, float y, int color, long now) {
        DigitRoll.Frame frame = digit.at(now);
        float reference = style.font().size() * style.scale() / 32;
        paint(ui, style, digit.from, x, y, color, frame.oldAlpha, frame.oldY * reference, frame.scale, frame.blur);
        paint(ui, style, digit.to, x, y, color, frame.newAlpha, frame.newY * reference, frame.scale, frame.blur);
    }

    private void paint(UI ui, Typeface.Style style, char digit, float x, float y, int color, double alpha, double offset, double scale, double blur) {
        if (digit == ' ' || alpha <= .001) return;
        ui.push();
        try {
            ui.translate(0, (float) offset); ui.scale((float) scale, x, y);
            if (blur * style.font().size() * style.scale() / 32 < .08) {
                ui.alphaPush((float) alpha);
                ui.text(style, String.valueOf(digit), x, y, color, UI.ALIGN_CENTER);
                ui.alphaPop();
            } else {
                int a = Math.max(0, Math.min(255, Math.round((color >>> 24) * ui.alpha() * (float) alpha)));
                int ink = color & 0xFFFFFF | a << 24;
                GlStateManager.translate(x - style.width(String.valueOf(digit)) / 2,
                        y + style.ascent() / 2 - 7 * style.scale(), 0);
                GlStateManager.scale(style.scale(), style.scale(), 1);
                style.drawBlurredDigit(digit, ink, (float) blur * style.font().size() / 32);
            }
        } finally { ui.pop(); }
    }
}
