package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Toast stack, ported from Onyx's {@code GuiComponentSub2}.
 *
 * <p>Snackbars use a 4px radius (the only non-pill surface in the ClickGUI), sit 12px above the
 * panel bottom, rise 10px while fading in over 300ms and stay for 800ms (1600ms for errors). The
 * background is inverseSurface for information and errorContainer for failures.</p>
 */
public final class Toasts {

    private static final float MAX_WIDTH = 380.0F;
    private static final float PADDING = 16.0F;
    private static final float ICON_SIZE = 16.0F;
    private static final int MAX_TOASTS = 3;

    private final List<Toast> toasts = new ArrayList<Toast>();

    public void show(String message) {
        add(message, false);
    }

    public void error(String message) {
        add(message, true);
    }

    private void add(String message, boolean error) {
        if (message == null || message.isEmpty()) {
            return;
        }
        if (toasts.size() >= MAX_TOASTS) {
            toasts.remove(0);
        }
        toasts.add(new Toast(message, error));
    }

    public boolean isEmpty() {
        return toasts.isEmpty();
    }

    public void update(float delta) {
        Iterator<Toast> iterator = toasts.iterator();
        while (iterator.hasNext()) {
            Toast toast = iterator.next();
            toast.update(delta);
            if (toast.dead()) {
                iterator.remove();
            }
        }
    }

    public void draw(UI ui, Scheme scheme, float centerX, float bottomY) {
        float offset = 0.0F;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            Toast toast = toasts.get(i);
            toast.measure(ui);
            float alpha = toast.alpha();
            if (alpha <= 0.001F) {
                continue;
            }
            float x = centerX - toast.width / 2.0F;
            float y = bottomY - 12.0F - offset - toast.height + 10.0F * (1.0F - toast.enter.value());
            ui.alphaPush(alpha);
            ui.shadow(x, y, toast.width, toast.height, 4.0F, 2);
            ui.rounded(x, y, toast.width, toast.height, 4.0F,
                    toast.error ? scheme.errorContainer : scheme.inverseSurface);
            int color = toast.error ? scheme.onErrorContainer : scheme.inverseOnSurface;
            float textX = x + PADDING;
            if (toast.error) {
                ui.icon(Icons.WARNING, textX + ICON_SIZE / 2.0F, y + toast.height / 2.0F, ICON_SIZE, color);
                textX += ICON_SIZE + 12.0F;
            }
            float lineY = y + toast.height / 2.0F - (toast.lines.size() - 1) * 13.0F / 2.0F;
            for (String line : toast.lines) {
                ui.text(Typeface.BODY, line, textX, lineY, color, UI.ALIGN_LEFT);
                lineY += 13.0F;
            }
            ui.alphaPop();
            offset += toast.height + 8.0F;
        }
    }

    private static final class Toast {
        final String message;
        final boolean error;
        final Anim enter = new Anim(0.0F);
        final Anim exit = new Anim(0.0F);
        final List<String> lines = new ArrayList<String>();
        float width;
        float height;
        float life;
        boolean leaving;

        Toast(String message, boolean error) {
            this.message = message;
            this.error = error;
            this.life = error ? 1600.0F : 800.0F;
            this.enter.to(1.0F, 300.0F, Ease.EMPHASIZED);
        }

        void measure(UI ui) {
            if (width > 0.0F) {
                return;
            }
            float iconWidth = error ? ICON_SIZE + 12.0F : 0.0F;
            int maxLines = 4;
            lines.clear();
            lines.addAll(ui.wrap(Typeface.BODY, message, MAX_WIDTH - PADDING * 2.0F - iconWidth, maxLines));
            float textWidth = 0.0F;
            for (String line : lines) {
                textWidth = Math.max(textWidth, Typeface.BODY.width(line));
            }
            this.width = Math.min(MAX_WIDTH, Math.max(120.0F, textWidth + PADDING * 2.0F + iconWidth));
            this.height = Math.max(34.0F, 21.0F + lines.size() * 13.0F);
        }

        void update(float delta) {
            enter.update(delta);
            exit.update(delta);
            if (leaving) {
                return;
            }
            life -= delta;
            if (life <= 0.0F) {
                leaving = true;
                exit.to(1.0F, 200.0F, Ease.DECELERATE);
            }
        }

        float alpha() {
            return Material.clamp(enter.value() * (1.0F - exit.value()), 0.0F, 1.0F);
        }

        boolean dead() {
            return leaving && exit.isDone();
        }
    }
}
