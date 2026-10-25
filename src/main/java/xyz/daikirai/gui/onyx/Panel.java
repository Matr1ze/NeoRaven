package xyz.daikirai.gui.onyx;

import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The Material You ClickGUI panel, ported from Onyx's {@code UndoneGuiComponent}.
 *
 * <p>640x420 with a 28px radius, a 38px header (client title on the left, active category on the
 * right, search pill in between), a 62px category rail, a 46px card list and a detail page that
 * slides in with the original 300ms/36px emphasized-decelerate transition. Dragging the header
 * moves the panel inside the same bounds the original clamped to.</p>
 */
public final class Panel {

    public static final float WIDTH = 640.0F;
    public static final float HEIGHT = 420.0F;
    public static final float RADIUS = 28.0F;
    /** Header title, as shown in the top-left corner. */
    public static final String TITLE = "NeoRaven";
    private static final float HEADER = 38.0F;
    private static final float PADDING = 10.0F;
    private static final float BODY_HEIGHT = HEIGHT - HEADER - PADDING;

    private final Sidebar sidebar;
    private final ModuleList list = new ModuleList();
    private final SearchField search = new SearchField();
    private final DetailPage detail = new DetailPage();
    private final Toasts toasts = new Toasts();
    private final Anim page = new Anim(0.0F);
    private final Anim backHover = new Anim(0.0F);

    private Category category;
    private float x;
    private float y;
    private float parentWidth;
    private float parentHeight;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean dragging;
    private String lastQuery = "";
    private Module current;
    private boolean themeOpen;
    private float lastMouseX;
    private float lastMouseY;
    private static Category lastCategory;
    private static float offsetX;
    private static float offsetY;

    public Panel() {
        List<Category> categories = new ArrayList<Category>();
        for (Category candidate : Category.values()) {
            for (Module module : ModuleManager.modules) {
                if (!module.isHidden() && !module.isProfile() && module.moduleCategory() == candidate) {
                    categories.add(candidate);
                    break;
                }
            }
        }
        this.category = lastCategory != null && categories.contains(lastCategory)
                ? lastCategory
                : Category.combat;
        if (!categories.contains(this.category) && !categories.isEmpty()) {
            this.category = categories.get(0);
        }
        lastCategory = this.category;
        this.sidebar = new Sidebar(categories, this.category);
        this.list.setContent(this.category, "");
    }

    public SearchField search() {
        return search;
    }

    public Toasts toasts() {
        return toasts;
    }

    public DetailPage detail() {
        return detail;
    }

    public boolean detailOpen() {
        return page.target() >= 0.5F;
    }

    /** Positions the panel inside the screen, honouring a previous drag. */
    public void layout(float parentWidth, float parentHeight) {
        this.parentWidth = parentWidth;
        this.parentHeight = parentHeight;
        if (!dragging) {
            this.x = Math.round(Material.clamp((parentWidth - WIDTH) / 2.0F + offsetX, 96.0F - WIDTH, parentWidth - 96.0F));
            this.y = Math.round(Material.clamp((parentHeight - HEIGHT) / 2.0F + offsetY, 0.0F, parentHeight - HEIGHT));
        }
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public void frame(float delta, float mouseX, float mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
        page.update(delta);
        backHover.update(delta);

        boolean listPage = page.target() < 0.5F;
        search.bounds(searchX(), y + (HEADER - SearchField.HEIGHT) / 2.0F, searchWidth());
        search.update(delta, mouseX, mouseY);
        sidebar.bounds(x + PADDING, y + HEADER, Sidebar.WIDTH, BODY_HEIGHT);
        sidebar.update(delta, mouseX, mouseY, listPage);
        list.bounds(searchX(), y + HEADER, x + WIDTH - PADDING - searchX(), BODY_HEIGHT);
        list.update(delta, mouseX, mouseY, listPage);
        detail.bounds(x + PADDING, y + HEADER, WIDTH - PADDING * 2.0F, BODY_HEIGHT);
        detail.update(delta, mouseX, mouseY, !listPage);
        toasts.update(delta);

        String query = search.text();
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            list.setContent(query.isEmpty() ? category : null, query);
        }

        if (listPage) {
            backHover.to(0.0F, 150.0F, Ease.DECELERATE);
        } else {
            boolean hovered = mouseX >= x + PADDING && mouseX < x + PADDING + 26.0F
                    && mouseY >= y + 6.0F && mouseY < y + 32.0F;
            backHover.to(hovered ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
        }
    }

    private float titleX() {
        return x + PADDING + 6.0F;
    }

    private float titleEnd() {
        return titleX() + Typeface.HEADLINE.width(TITLE);
    }

    private float searchX() {
        return Math.max(x + PADDING + Sidebar.WIDTH + 10.0F, titleEnd() + 12.0F);
    }

    private float searchWidth() {
        float right = x + WIDTH - PADDING - 6.0F - rightLabelWidth() - 12.0F;
        return Math.max(80.0F, right - searchX());
    }

    private float rightLabelWidth() {
        String label = page.target() >= 0.5F ? currentCategoryLabel() : category.name();
        return Typeface.LABEL.width(label);
    }

    private String currentCategoryLabel() {
        if (themeOpen) {
            return "Onyx ClickGUI";
        }
        return current == null ? "" : Icons.label(current.moduleCategory());
    }

    public void draw(UI ui) {
        Scheme scheme = Theme.scheme();
        float fade = page.value();

        ui.shadow(x, y, WIDTH, HEIGHT, RADIUS, 3);
        ui.rounded(x, y, WIDTH, HEIGHT, RADIUS, scheme.surfaceContainer);
        if (Theme.frost() && Blur.ready()) {
            ui.frost(x, y, WIDTH, HEIGHT, RADIUS, scheme.surfaceContainer);
        } else {
            ui.rounded(x, y, WIDTH, HEIGHT, RADIUS,
                    Material.withAlpha(scheme.surfaceContainer, Theme.frost() ? 0.92F : 0.97F));
        }

        drawHeader(ui, scheme, fade);

        ui.scissorPush(x + PADDING, y + HEADER, WIDTH - PADDING * 2.0F, BODY_HEIGHT);
        if (fade < 0.999F) {
            ui.alphaPush(1.0F - fade);
            ui.push();
            ui.translate(-36.0F * fade, 0.0F);
            sidebar.draw(ui, scheme, lastMouseX, lastMouseY);
            ui.scissorPush(searchX(), y + HEADER, x + WIDTH - PADDING - searchX(), BODY_HEIGHT);
            list.draw(ui, scheme, lastMouseX, lastMouseY);
            ui.scissorPop();
            ui.pop();
            ui.alphaPop();
        }
        if (fade > 0.001F) {
            ui.alphaPush(fade);
            ui.push();
            ui.translate(36.0F * (1.0F - fade), 0.0F);
            detail.draw(ui, scheme, lastMouseX, lastMouseY);
            ui.pop();
            ui.alphaPop();
        }
        ui.scissorPop();

        toasts.draw(ui, scheme, x + WIDTH / 2.0F, y + HEIGHT);
    }

    private void drawHeader(UI ui, Scheme scheme, float fade) {
        boolean listPage = page.target() < 0.5F;
        if (listPage) {
            ui.text(Typeface.HEADLINE, TITLE, titleX(), y + HEADER / 2.0F,
                    scheme.onSurface, UI.ALIGN_LEFT);
            String label;
            if (!search.text().isEmpty()) {
                int results = list.size();
                label = results == 1 ? "1 result" : results + " results";
            } else {
                label = Icons.label(category);
            }
            ui.text(Typeface.LABEL, label, x + WIDTH - PADDING - 6.0F, y + HEADER / 2.0F,
                    Material.withAlpha(scheme.onSurfaceVariant, 0.9F), UI.ALIGN_RIGHT);
            search.draw(ui, scheme, lastMouseX, lastMouseY);
        } else {
            float chipX = x + PADDING;
            float chipY = y + 6.0F;
            if (backHover.value() > 0.001F) {
                ui.circle(chipX + 13.0F, chipY + 13.0F, 13.0F,
                        Material.withAlpha(scheme.onSurface, 0.08F * backHover.value()));
            }
            ui.icon(Icons.ARROW_BACK, chipX + 13.0F, chipY + 13.0F, 15.0F, scheme.onSurface);
            String title = themeOpen || current == null ? "Theme" : Text.strip(current.getName());
            ui.text(Typeface.HEADLINE, ui.truncate(Typeface.HEADLINE, title, 340.0F),
                    chipX + 26.0F + 8.0F, y + HEADER / 2.0F, scheme.onSurface, UI.ALIGN_LEFT);
            ui.text(Typeface.LABEL, currentCategoryLabel(), x + WIDTH - PADDING - 6.0F, y + HEADER / 2.0F,
                    Material.withAlpha(scheme.onSurfaceVariant, 0.9F), UI.ALIGN_RIGHT);
        }
        if (fade <= 0.0F) {
            return;
        }
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        boolean listPage = page.target() < 0.5F;
        if (button == 0 && !listPage) {
            boolean overBack = mouseX >= x + PADDING && mouseX < x + PADDING + 26.0F
                    && mouseY >= y + 6.0F && mouseY < y + 32.0F;
            if (overBack) {
                back();
                return true;
            }
        }

        if (listPage) {
            if (search.click(mouseX, mouseY, button)) {
                return true;
            }
            if (sidebar.click(mouseX, mouseY, button)) {
                if (sidebar.themeSelected()) {
                    openTheme();
                } else {
                    themeOpen = false;
                    category = sidebar.selected();
                    lastCategory = category;
                    search.focus(false);
                    list.setContent(category, "");
                    search.clear();
                    lastQuery = "";
                }
                return true;
            }
            if (list.click(mouseX, mouseY, button, this::openDetail)) {
                return true;
            }
        } else if (detail.click(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && mouseY >= y && mouseY < y + HEADER && listPage) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
            return true;
        }
        return false;
    }

    public void mouseDragged(float mouseX, float mouseY, int button) {
        if (drawingDetail()) {
            detail.drag(mouseX, mouseY, button);
            return;
        }
        if (dragging && button == 0) {
            this.x = Math.round(Material.clamp(mouseX - dragOffsetX, 96.0F - WIDTH, parentWidth - 96.0F));
            this.y = Math.round(Material.clamp(mouseY - dragOffsetY, 0.0F, parentHeight - HEIGHT));
            offsetX = this.x - (parentWidth - WIDTH) / 2.0F;
            offsetY = this.y - (parentHeight - HEIGHT) / 2.0F;
        }
    }

    private boolean drawingDetail() {
        return page.target() >= 0.5F;
    }

    public void mouseReleased(int button) {
        dragging = false;
        list.release();
        detail.release();
    }

    public boolean wheel(float mouseX, float mouseY, double amount) {
        if (drawingDetail()) {
            return detail.wheel(mouseX, mouseY, amount);
        }
        if (sidebar.wheel(mouseX, mouseY, amount)) {
            return true;
        }
        return list.wheel(mouseX, mouseY, amount);
    }

    public boolean key(char typed, int key) {
        if (detail.key(typed, key)) {
            return true;
        }
        if (search.focused() && search.key(typed, key)) {
            return true;
        }
        if (key == 1) {
            if (drawingDetail()) {
                back();
                return true;
            }
            return false;
        }
        if (!drawingDetail()) {
            if (key == 200) {
                list.moveSelection(-1);
                return true;
            }
            if (key == 208) {
                list.moveSelection(1);
                return true;
            }
            if (key == 28) {
                Module selected = list.openSelected();
                if (selected != null) {
                    openDetail(selected);
                }
                return true;
            }
            if (key == 57) {
                Module selected = list.openSelected();
                if (selected != null) {
                    selected.toggle();
                    toasts.show(Text.strip(selected.getName()) + (selected.isEnabled() ? " on" : " off"));
                }
                return true;
            }
        }
        return false;
    }

    public void openDetail(Module module) {
        if (module == null) {
            return;
        }
        this.current = module;
        this.themeOpen = false;
        this.search.focus(false);
        this.detail.open(module);
        this.page.to(1.0F, 300.0F, Ease.EMPHASIZED_DECELERATE);
    }

    public void openTheme() {
        this.current = OnyxClickGuiModule.instance;
        if (this.current == null) {
            return;
        }
        this.themeOpen = true;
        this.search.focus(false);
        this.detail.open(OnyxClickGuiModule.instance);
        this.page.to(1.0F, 300.0F, Ease.EMPHASIZED_DECELERATE);
    }

    public void back() {
        this.page.to(0.0F, 300.0F, Ease.EMPHASIZED_DECELERATE);
        this.themeOpen = false;
        this.list.focus(true);
    }

    /** Re-centres the panel (used by the module's "Reset window" button). */
    public void recenter() {
        offsetX = 0.0F;
        offsetY = 0.0F;
        if (parentWidth > 0.0F && parentHeight > 0.0F) {
            this.x = Math.round(Material.clamp((parentWidth - WIDTH) / 2.0F, 96.0F - WIDTH, parentWidth - 96.0F));
            this.y = Math.round(Material.clamp((parentHeight - HEIGHT) / 2.0F, 0.0F, parentHeight - HEIGHT));
        }
    }

    /** Static reset so the module button can recentre an already-open panel. */
    public static void resetOffset() {
        offsetX = 0.0F;
        offsetY = 0.0F;
        if (OnyxClickGui.instance != null) {
            OnyxClickGui.instance.panel().recenter();
        }
    }

    public void toast(String message) {
        toasts.show(message);
    }

    public void toastError(String message) {
        toasts.error(message);
    }
}
