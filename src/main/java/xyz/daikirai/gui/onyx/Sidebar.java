package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Category;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Category rail, ported from Onyx's {@code ConfigsGuiComponent}.
 *
 * <p>Same 50px rows, 46x26 selection pill sliding under a 300ms emphasized-decelerate curve,
 * 16px Material icon and 11px label at a fixed 36px baseline. The rail scrolls when a client has
 * more than six categories, and the pinned "Theme" item at the bottom opens the ClickGUI's own
 * Material settings.</p>
 */
public final class Sidebar {

    public static final float WIDTH = 62.0F;
    private static final float ITEM_HEIGHT = 50.0F;
    private static final float PILL_WIDTH = 46.0F;
    private static final float PILL_HEIGHT = 26.0F;
    private static final float BOTTOM_ITEM_HEIGHT = 50.0F;

    private final List<Category> categories = new ArrayList<Category>();
    private final Map<Category, Item> items = new HashMap<Category, Item>();
    private final Anim pillY = new Anim(0.0F);
    private final Anim scroll = new Anim(0.0F);

    private float x;
    private float y;
    private float w;
    private float h;
    private float scrollTarget;
    private int selectedIndex;
    private Category selected;
    private boolean themeSelected;
    private final Item themeHover = new Item();

    public Sidebar(List<Category> categories, Category selected) {
        this.categories.addAll(categories);
        for (Category category : categories) {
            items.put(category, new Item());
        }
        this.selected = selected;
        this.selectedIndex = Math.max(0, categories.indexOf(selected));
        this.pillY.set(indexY(this.selectedIndex));
    }

    public void bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Category selected() {
        return selected;
    }

    public boolean themeSelected() {
        return themeSelected;
    }

    public void select(Category category) {
        themeSelected = false;
        if (category == null || !categories.contains(category) || category == selected) {
            return;
        }
        this.selected = category;
        int index = categories.indexOf(category);
        this.selectedIndex = index;
        this.pillY.to(indexY(index), 300.0F, Ease.EMPHASIZED_DECELERATE);
        ensureVisible(index);
    }

    public void selectTheme() {
        themeSelected = true;
    }

    private float indexY(int index) {
        return index * ITEM_HEIGHT;
    }

    private float railHeight() {
        return h - BOTTOM_ITEM_HEIGHT;
    }

    /** Only whole items are visible; a sliver of the next row would collide with the Theme item. */
    private float clipHeight() {
        float rail = railHeight();
        float whole = (float) Math.floor(rail / ITEM_HEIGHT) * ITEM_HEIGHT;
        return whole >= ITEM_HEIGHT ? whole : rail;
    }

    private float maxScroll() {
        return Math.max(0.0F, categories.size() * ITEM_HEIGHT - clipHeight());
    }

    private void ensureVisible(int index) {
        float top = indexY(index);
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (top + ITEM_HEIGHT > scrollTarget + clipHeight()) {
            scrollTarget = top + ITEM_HEIGHT - clipHeight();
        }
        scrollTarget = Material.clamp(scrollTarget, 0.0F, maxScroll());
    }

    public void update(float delta, float mouseX, float mouseY, boolean interactive) {
        this.scroll.to(scrollTarget, 250.0F, Ease.DECELERATE);
        this.scroll.update(delta);
        this.pillY.update(delta);

        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            Item item = items.get(category);
            float itemY = y + indexY(i) - scroll.value();
            boolean hovered = interactive
                    && mouseX >= x && mouseX < x + w
                    && mouseY >= Math.max(itemY, y) && mouseY < Math.min(itemY + ITEM_HEIGHT, y + clipHeight());
            item.update(delta, hovered);
        }
        themeHover.update(delta, interactive && isOverTheme(mouseX, mouseY));
    }

    private boolean isOverTheme(float mouseX, float mouseY) {
        float itemY = y + h - BOTTOM_ITEM_HEIGHT;
        return mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + BOTTOM_ITEM_HEIGHT;
    }

    public void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
        ui.scissorPush(x - 10.0F, y, w + 20.0F, clipHeight());
        float pill = pillY.value() - scroll.value() + 2.0F;
        ui.rounded(x + (w - PILL_WIDTH) / 2.0F, y + pill, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0F,
                themeSelected ? Material.withAlpha(scheme.secondaryContainer, 0.0F) : scheme.secondaryContainer);

        for (int i = 0; i < categories.size(); i++) {
            Category category = categories.get(i);
            Item item = items.get(category);
            float itemY = y + indexY(i) - scroll.value();
            if (itemY + ITEM_HEIGHT < y - 1.0F || itemY > y + clipHeight() + 1.0F) {
                continue;
            }
            boolean active = !themeSelected && category == selected;
            float centerX = x + w / 2.0F;
            float pillCenterY = itemY + 2.0F + PILL_HEIGHT / 2.0F;

            if (item.hover.value() > 0.001F && !active) {
                ui.rounded(x + (w - PILL_WIDTH) / 2.0F, itemY + 2.0F, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0F,
                        Material.withAlpha(scheme.onSurface, 0.08F * item.hover.value()));
            }
            int iconColor = active ? scheme.onSecondaryContainer : scheme.onSurfaceVariant;
            ui.icon(Icons.forCategory(category), centerX, pillCenterY, 16.0F, iconColor);
            ui.text(Typeface.LABEL, Icons.label(category),
                    centerX, itemY + 36.0F,
                    active ? scheme.onSurface : Material.withAlpha(scheme.onSurfaceVariant, 0.9F),
                    UI.ALIGN_CENTER);
        }
        ui.scissorPop();

        float themeY = y + h - BOTTOM_ITEM_HEIGHT;
        boolean active = themeSelected;
        if (active) {
            ui.rounded(x + (w - PILL_WIDTH) / 2.0F, themeY + 2.0F, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0F,
                    scheme.secondaryContainer);
        } else if (themeHover.hover.value() > 0.001F) {
            ui.rounded(x + (w - PILL_WIDTH) / 2.0F, themeY + 2.0F, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2.0F,
                    Material.withAlpha(scheme.onSurface, 0.08F * themeHover.hover.value()));
        }
        float centerX = x + w / 2.0F;
        ui.icon(Icons.PALETTE, centerX, themeY + 2.0F + PILL_HEIGHT / 2.0F, 16.0F,
                active ? scheme.onSecondaryContainer : scheme.onSurfaceVariant);
        ui.text(Typeface.LABEL, "Theme", centerX, themeY + 36.0F,
                active ? scheme.onSurface : Material.withAlpha(scheme.onSurfaceVariant, 0.9F), UI.ALIGN_CENTER);
    }

    /** Returns true when the click was consumed. */
    public boolean click(float mouseX, float mouseY, int button) {
        if (button != 0 || mouseX < x || mouseX >= x + w) {
            return false;
        }
        float themeY = y + h - BOTTOM_ITEM_HEIGHT;
        if (mouseY >= themeY && mouseY < themeY + BOTTOM_ITEM_HEIGHT) {
            selectTheme();
            return true;
        }
        if (mouseY < y || mouseY >= y + clipHeight()) {
            return false;
        }
        int index = (int) ((mouseY - y + scroll.value()) / ITEM_HEIGHT);
        if (index < 0 || index >= categories.size()) {
            return false;
        }
        select(categories.get(index));
        return true;
    }

    public boolean wheel(float mouseX, float mouseY, double amount) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + clipHeight()) {
            return false;
        }
        if (maxScroll() <= 0.0F) {
            return false;
        }
        scrollTarget = Material.clamp(scrollTarget - (float) amount * 32.0F, 0.0F, maxScroll());
        return true;
    }

    private static final class Item {
        final Anim hover = new Anim(0.0F);

        void update(float delta, boolean hovered) {
            this.hover.to(hovered ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            this.hover.update(delta);
        }
    }
}
