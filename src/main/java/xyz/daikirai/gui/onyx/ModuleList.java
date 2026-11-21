package xyz.daikirai.gui.onyx;

import xyz.daikirai.client.Profiles;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Scrollable module list, ported from Onyx's {@code NoModulesMatchContainerComponent} +
 * {@code ContainerComponent} + {@code GuiComponentSub}.
 *
 * <p>Cards are 46px tall with a 12px radius, a 5px gap, a 38x22 switch on the right and the same
 * two-line label layout the original used (name at 32% height, second line at 70%). The list
 * scrolls with a 250ms decelerate animation and draws a 4px scrollbar on the right edge.</p>
 */
public final class ModuleList {

    public static final float CARD_HEIGHT = 46.0F;
    private static final float GAP = 5.0F;
    private static final float SCROLLBAR_WIDTH = 4.0F;

    private final List<Card> cards = new ArrayList<Card>();
    private final Map<Module, Card> byModule = new HashMap<Module, Card>();
    private final Anim scroll = new Anim(0.0F);

    private float x;
    private float y;
    private float w;
    private float h;
    private float scrollTarget;
    private float contentHeight;
    private Module selected;
    private boolean focused;
    private String emptyText = "No modules here yet";
    private Category builtCategory;
    private String builtQuery = "";
    private int builtRevision = -1;

    /** Opens the detail page for a module (right click / keyboard). */
    public interface Opener {
        void open(Module module);
    }

    /** Updates the visible module set; {@code query} filters over name/category. */
    public void setContent(Category category, String query) {
        this.builtCategory = category;
        this.builtQuery = query == null ? "" : query;
        this.builtRevision = Profiles.revision();
        cards.clear();
        byModule.clear();
        String needle = this.builtQuery.trim().toLowerCase(Locale.ROOT);

        List<Module> source = new ArrayList<Module>(ModuleManager.modules);
        List<Module> profileModules = new ArrayList<Module>(Profiles.profiles.values());
        Collections.sort(profileModules, new Comparator<Module>() {
            @Override
            public int compare(Module a, Module b) {
                return String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName()));
            }
        });
        for (Module profile : profileModules) {
            if (!source.contains(profile)) {
                source.add(profile);
            }
        }

        for (Module module : source) {
            boolean profile = module.isProfile();
            if (profile) {
                if (needle.isEmpty() && category != Category.profiles) {
                    continue;
                }
                if (!needle.isEmpty() && !matches(module, needle)) {
                    continue;
                }
            } else if (!needle.isEmpty()) {
                if (!matches(module, needle)) {
                    continue;
                }
            } else if (category != null && module.moduleCategory() != category) {
                continue;
            }
            Card card = new Card(module);
            cards.add(card);
            byModule.put(module, card);
        }

        this.emptyText = !needle.isEmpty() ? "No modules match" : "No modules here yet";
        this.contentHeight = cards.size() * (CARD_HEIGHT + GAP) - GAP;
        this.scrollTarget = 0.0F;
        this.scroll.set(0.0F);
        if (selected != null && !byModule.containsKey(selected)) {
            selected = null;
        }
        if (selected == null && !cards.isEmpty()) {
            selected = cards.get(0).module;
        }
    }

    private static boolean matches(Module module, String needle) {
        if (module.getName() != null && module.getName().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        if (module.getInfo() != null && module.getInfo().toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        return module.moduleCategory() != null
                && module.moduleCategory().name().toLowerCase(Locale.ROOT).contains(needle);
    }

    public void bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int size() {
        return cards.size();
    }

    public Module selected() {
        return selected;
    }

    public float contentHeight() {
        return contentHeight;
    }

    public void focus(boolean focused) {
        this.focused = focused;
    }

    public void update(float delta, float mouseX, float mouseY, boolean interactive) {
        if (builtRevision != Profiles.revision()) {
            setContent(builtCategory, builtQuery);
        }
        this.scroll.to(scrollTarget, 250.0F, Ease.DECELERATE);
        this.scroll.update(delta);

        float visible = Math.max(0.0F, contentHeight - h);
        scrollTarget = Material.clamp(scrollTarget, 0.0F, visible);

        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float cardY = y + i * (CARD_HEIGHT + GAP) - scroll.value();
            boolean hovered = interactive && mouseX >= x && mouseX < x + w - 9.0F
                    && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT
                    && cardY + CARD_HEIGHT > y && cardY < y + h;
            card.update(delta, hovered);
        }
    }

    public void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
        if (cards.isEmpty()) {
            ui.text(Typeface.BODY, emptyText, x + (w - 9.0F) / 2.0F, y + h / 2.0F,
                    scheme.onSurfaceVariant, UI.ALIGN_CENTER);
            return;
        }

        ui.scissorPush(x, y, w - 9.0F, h);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float cardY = y + i * (CARD_HEIGHT + GAP) - scroll.value();
            if (cardY + CARD_HEIGHT < y - 1.0F || cardY > y + h + 1.0F) {
                continue;
            }
            card.draw(ui, scheme, x, cardY, w - 9.0F, CARD_HEIGHT, card.module == selected && focused, mouseX, mouseY);
        }
        ui.scissorPop();

        float visible = contentHeight - h;
        if (visible > 0.5F) {
            float trackHeight = Math.max(24.0F, h * (h / contentHeight));
            float fraction = Material.clamp(scroll.value() / visible, 0.0F, 1.0F);
            float barY = y + (h - trackHeight) * fraction;
            ui.rounded(x + w - 7.0F, barY, SCROLLBAR_WIDTH, trackHeight, SCROLLBAR_WIDTH / 2.0F,
                    Material.withAlpha(scheme.outlineVariant, 0.8F));
        }
    }

    public boolean click(float mouseX, float mouseY, int button, Opener opener) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float cardY = y + i * (CARD_HEIGHT + GAP) - scroll.value();
            if (cardY + CARD_HEIGHT < y || cardY > y + h) {
                continue;
            }
            if (mouseY >= cardY && mouseY < cardY + CARD_HEIGHT && mouseX < x + w - 9.0F) {
                selected = card.module;
                focused = true;
                if (button == 1 || button == 2) {
                    opener.open(card.module);
                    return true;
                }
                return card.click(button);
            }
        }
        return false;
    }

    public void release() {
        for (Card card : cards) {
            card.pressed = false;
        }
    }

    public boolean wheel(float mouseX, float mouseY, double amount) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        float visible = Math.max(0.0F, contentHeight - h);
        if (visible <= 0.0F) {
            return false;
        }
        scrollTarget = Material.clamp(scrollTarget - (float) amount * 32.0F, 0.0F, visible);
        return true;
    }

    /** Moves the keyboard selection by {@code delta} cards, scrolling it into view. */
    public void moveSelection(int delta) {
        if (cards.isEmpty()) {
            return;
        }
        boolean many = Math.abs(delta) > 1;
        int index = selected == null ? -1 : cards.indexOf(byModule.get(selected));
        if (index < 0) {
            index = 0;
        } else {
            index = (index + delta) % cards.size();
            if (index < 0) {
                index += cards.size();
            }
        }
        selected = cards.get(index).module;
        focused = true;
        if (many) {
            scrollTarget = 100000.0F;
        }
        ensureVisible(index);
    }

    public Module openSelected() {
        if (selected != null) {
            focused = true;
        }
        return selected;
    }

    private void ensureVisible(int index) {
        float top = index * (CARD_HEIGHT + GAP);
        float visible = Math.max(0.0F, contentHeight - h);
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (top + CARD_HEIGHT > scrollTarget + h) {
            scrollTarget = Math.min(top + CARD_HEIGHT - h, visible);
        }
        scrollTarget = Material.clamp(scrollTarget, 0.0F, visible);
    }

    private static final class Card {
        final Module module;
        final Anim hover = new Anim(0.0F);
        final Anim toggle = new Anim(0.0F);
        boolean pressed;
        boolean hovered;
        boolean initialised;

        Card(Module module) {
            this.module = module;
        }

        void update(float delta, boolean hovered) {
            this.hovered = hovered;
            this.hover.to(hovered ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            this.hover.update(delta);
            if (!initialised) {
                initialised = true;
                this.toggle.set(module.isEnabled() ? 1.0F : 0.0F);
            }
            this.toggle.to(module.isEnabled() ? 1.0F : 0.0F, 250.0F, Ease.DECELERATE);
            this.toggle.update(delta);
        }

        void draw(UI ui, Scheme scheme, float x, float y, float w, float h, boolean selected, float mouseX, float mouseY) {
            float t = toggle.value();
            int background = Material.mix(scheme.surfaceContainerHigh, scheme.secondaryContainer, t);
            ui.rounded(x, y, w, h, 12.0F, background);
            if (hover.value() > 0.001F) {
                ui.rounded(x, y, w, h, 12.0F,
                        Material.withAlpha(scheme.onSurface, pressed ? 0.1F : 0.08F * hover.value()));
            }
            if (selected) {
                ui.outline(x, y, w, h, 12.0F, 1.5F, Material.withAlpha(scheme.primary, 0.6F));
            }

            int titleColor = t > 0.5F
                    ? Material.mix(scheme.onSurface, scheme.onSecondaryContainer, (t - 0.5F) * 2.0F)
                    : Material.mix(scheme.onSecondaryContainer, scheme.onSurface, (0.5F - t) * 2.0F);
            int subColor = t > 0.5F
                    ? Material.withAlpha(scheme.onSecondaryContainer, 0.72F)
                    : scheme.onSurfaceVariant;

            // Modules that cannot be toggled (the script/profile "Manager" entries, Settings,
            // Command Line, ...) get no switch at all instead of a dead control.
            boolean showSwitch = !module.noToggle;
            float textWidth = w - 12.0F - 10.0F - (showSwitch ? 38.0F + 12.0F : 0.0F);
            String title = ui.truncate(Typeface.HEADLINE, Text.strip(module.getName()), textWidth);
            String sub = subtitle();
            float titleY = !showSwitch && sub.isEmpty() ? y + h / 2.0F : y + h * 0.32F;
            ui.text(Typeface.HEADLINE, title, x + 12.0F, titleY, titleColor, UI.ALIGN_LEFT);
            if (!sub.isEmpty()) {
                ui.text(Typeface.LABEL, ui.truncate(Typeface.LABEL, sub, textWidth), x + 12.0F, y + h * 0.70F,
                        subColor, UI.ALIGN_LEFT);
            }

            if (showSwitch) {
                float switchX = x + w - 38.0F - 12.0F;
                float switchY = y + (h - 22.0F) / 2.0F;
                boolean switchHover = mouseX >= switchX && mouseX < switchX + 38.0F
                        && mouseY >= switchY && mouseY < switchY + 22.0F;
                Widgets.toggle(ui, scheme, switchX, switchY, t, switchHover || pressed, pressed);
            }
        }

        private String subtitle() {
            String info = Text.strip(module.getInfo());
            if (!info.isEmpty()) {
                return info;
            }
            int key = module.getKeycode();
            if (key != 0) {
                String name = org.lwjgl.input.Keyboard.getKeyName(Math.abs(key));
                return name == null ? "" : name;
            }
            return "";
        }

        boolean click(int button) {
            if (button != 0 || module.noToggle) {
                return false;
            }
            pressed = true;
            module.toggle();
            return true;
        }
    }
}
