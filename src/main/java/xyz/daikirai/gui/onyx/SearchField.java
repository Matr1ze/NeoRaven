package xyz.daikirai.gui.onyx;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * Search pill in the panel header, ported from Onyx's {@code SearchModulesGuiComponent}.
 *
 * <p>26px tall, fully rounded, surfaceContainerHigh track, 1.5px primary outline that fades in on
 * focus, 16px search glyph at x+15 and the text baseline at x+28. The clear affordance sits in a
 * 22px circular state layer on the right.</p>
 */
public final class SearchField {

    public static final float HEIGHT = 26.0F;

    private String text = "";
    private boolean focused;
    private long caretStamp = System.currentTimeMillis();

    private float x;
    private float y;
    private float w;

    private final Anim focus = new Anim(0.0F);
    private final Anim hover = new Anim(0.0F);

    public void bounds(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
    }

    public String text() {
        return text;
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean focused) {
        this.focused = focused;
        this.caretStamp = System.currentTimeMillis();
    }

    public void clear() {
        this.text = "";
        this.caretStamp = System.currentTimeMillis();
    }

    public void update(float delta, float mouseX, float mouseY) {
        focus.to(focused ? 1.0F : 0.0F, 200.0F, Ease.DECELERATE);
        focus.update(delta);
        hover.to(contains(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
        hover.update(delta);
    }

    private boolean contains(float mouseX, float mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEIGHT;
    }

    public void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
        float centerY = y + HEIGHT / 2.0F;
        ui.rounded(x, y, w, HEIGHT, HEIGHT / 2.0F, scheme.surfaceContainerHigh);

        if (hover.value() > 0.001F && !focused) {
            ui.rounded(x, y, w, HEIGHT, HEIGHT / 2.0F,
                    Material.withAlpha(scheme.onSurface, 0.04F * hover.value()));
        }
        if (focus.value() > 0.01F) {
            ui.outline(x, y, w, HEIGHT, HEIGHT / 2.0F, 1.5F,
                    Material.withAlpha(scheme.primary, focus.value()));
        }

        ui.icon(Icons.SEARCH, x + 15.0F, centerY, 16.0F, scheme.onSurfaceVariant);
        float textStart = x + 28.0F;
        float clearSpace = text.isEmpty() ? 6.0F : 24.0F;
        float maxText = w - 28.0F - clearSpace;

        if (text.isEmpty() && !focused) {
            ui.text(Typeface.BODY_MEDIUM, "Search modules", textStart, centerY,
                    Material.withAlpha(scheme.onSurfaceVariant, 0.7F), UI.ALIGN_LEFT);
        } else {
            String visible = ui.truncate(Typeface.BODY_MEDIUM, text.isEmpty() ? "" : text, maxText);
            ui.text(Typeface.BODY_MEDIUM, visible, textStart, centerY, scheme.onSurface, UI.ALIGN_LEFT);
            if (focused && (System.currentTimeMillis() - caretStamp) / 500L % 2L == 0L) {
                float caretX = textStart + Typeface.BODY_MEDIUM.width(visible) + 1.0F;
                ui.rect(caretX, centerY - 7.0F, 1.0F, 14.0F, scheme.primary);
            }
        }

        if (!text.isEmpty()) {
            boolean overClear = mouseX >= x + w - 26.0F && mouseX < x + w - 4.0F
                    && mouseY >= y + 2.0F && mouseY < y + HEIGHT - 2.0F;
            if (overClear) {
                ui.circle(x + w - 15.0F, centerY, 11.0F, Material.withAlpha(scheme.onSurface, 0.08F));
            }
            ui.icon(Icons.CLOSE, x + w - 15.0F, centerY, 16.0F, scheme.onSurfaceVariant);
        }
    }

    public boolean click(float mouseX, float mouseY, int button) {
        if (button != 0 || !contains(mouseX, mouseY)) {
            return false;
        }
        if (!text.isEmpty() && mouseX >= x + w - 26.0F) {
            clear();
            focus(true);
            return true;
        }
        focus(true);
        return true;
    }

    /** Handles typing; returns true when the key was consumed. */
    public boolean key(char typed, int key) {
        if (!focused) {
            return false;
        }
        this.caretStamp = System.currentTimeMillis();
        if (key == 1) {
            if (!text.isEmpty()) {
                clear();
            } else {
                focus(false);
            }
            return true;
        }
        if (key == 14) {
            if (!text.isEmpty()) {
                this.text = text.substring(0, text.length() - 1);
            }
            return true;
        }
        if (key == 47 && GuiScreen.isCtrlKeyDown()) {
            String clipboard = GuiScreen.getClipboardString();
            if (clipboard != null) {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < clipboard.length() && builder.length() < 48; i++) {
                    char c = clipboard.charAt(i);
                    if (c >= ' ' && c != 127) {
                        builder.append(c);
                    }
                }
                if (builder.length() > 0) {
                    this.text = (text + builder).length() > 48 ? (text + builder).substring(0, 48) : text + builder;
                }
                return true;
            }
            return true;
        }
        if (typed >= ' ' && typed != 127 && text.length() < 48) {
            this.text = text + typed;
            return true;
        }
        return false;
    }
}
