package xyz.daikirai.gui.onyx;

import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.DescriptionSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Module detail page, ported from Onyx's settings container
 * ({@code ContainerComponent} + {@code Cls2} + {@code SettingComponent}*).
 *
 * <p>Rows are laid out in a 460px column centred in the body, each one separated by the same
 * faded 1px outline the original used. The first row is always the module bind, followed by the
 * module's own settings mapped onto Material controls: sliders with a 4px track and 14px thumb,
 * dropdowns for mode settings, switches for toggles, text rows for actions and a wrapped
 * description block.</p>
 */
public final class DetailPage {

    private static final float COLUMN_WIDTH = 460.0F;
    private static final float DIVIDER_ALPHA = 0.6F;

    private final List<Row> rows = new ArrayList<Row>();
    private final Anim scroll = new Anim(0.0F);

    private Module module;
    private float x;
    private float y;
    private float w;
    private float h;
    private float contentHeight;
    private float scrollTarget;
    private float columnX;
    private boolean binding;
    private int menuIndex = -1;
    private int menuHover = -1;
    private Row activeRow;

    public Module module() {
        return module;
    }

    public boolean binding() {
        return binding;
    }

    public void bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.columnX = x + (w - Math.min(w - 12.0F, COLUMN_WIDTH)) / 2.0F;
    }

    public void open(Module module) {
        this.module = module;
        this.rows.clear();
        this.scroll.set(0.0F);
        this.scrollTarget = 0.0F;
        this.binding = false;
        this.menuIndex = -1;
        this.activeRow = null;
        if (module == null) {
            return;
        }
        rows.add(new BindRow());
        for (Setting setting : module.getSettings()) {
            if (setting instanceof SliderSetting) {
                SliderSetting slider = (SliderSetting) setting;
                rows.add(slider.isMode() ? (Row) new ModeRow(slider) : new SliderRow(slider));
            } else if (setting instanceof ButtonSetting) {
                ButtonSetting button = (ButtonSetting) setting;
                rows.add(button.isMethodButton() ? (Row) new ButtonRow(button, module) : new ToggleRow(button, module));
            } else if (setting instanceof DescriptionSetting) {
                rows.add(new DescriptionRow((DescriptionSetting) setting));
            }
        }
        layoutRows(0.0F);
    }

    public void close() {
        this.module = null;
        this.rows.clear();
        this.binding = false;
        this.menuIndex = -1;
        this.activeRow = null;
    }

    private void layoutRows(float top) {
        float y = top;
        float width = Math.min(w - 12.0F, COLUMN_WIDTH);
        for (Row row : rows) {
            row.layout(columnX, y, width);
            y += row.height;
        }
        this.contentHeight = y - top;
    }

    public void update(float delta, float mouseX, float mouseY, boolean interactive) {
        float visible = Math.max(0.0F, contentHeight - h);
        scrollTarget = Material.clamp(scrollTarget, 0.0F, visible);
        scroll.to(scrollTarget, 250.0F, Ease.DECELERATE);
        scroll.update(delta);

        float offset = y - scroll.value();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.layout(columnX, offset, Math.min(w - 12.0F, COLUMN_WIDTH));
            offset += row.height;
            row.update(delta, mouseX, mouseY, interactive);
        }

        if (menuIndex >= 0 && menuIndex < rows.size() && rows.get(menuIndex) instanceof ModeRow) {
            ModeRow mode = (ModeRow) rows.get(menuIndex);
            this.menuHover = menuIndexAt(mode, mouseX, mouseY);
        } else {
            this.menuHover = -1;
        }
    }

    public void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
        if (module == null) {
            return;
        }
        ui.scissorPush(x, y, w, h);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.y + row.height < y - 1.0F || row.y > y + h + 1.0F) {
                continue;
            }
            row.draw(ui, scheme, mouseX, mouseY);
            if (i < rows.size() - 1) {
                ui.rect(row.x, row.y + row.height - 0.5F, row.w, 1.0F,
                        Material.withAlpha(scheme.outlineVariant, DIVIDER_ALPHA));
            }
        }

        if (module.getSettings().isEmpty()) {
            ui.text(Typeface.BODY, "This module has no settings",
                    x + w / 2.0F, y + h / 2.0F, scheme.onSurfaceVariant, UI.ALIGN_CENTER);
        }
        ui.scissorPop();

        if (menuIndex >= 0 && menuIndex < rows.size() && rows.get(menuIndex) instanceof ModeRow) {
            ModeRow mode = (ModeRow) rows.get(menuIndex);
            mode.drawMenu(ui, scheme, menuHover);
        }

        float visible = contentHeight - h;
        if (visible > 0.5F) {
            float trackHeight = Math.max(24.0F, h * (h / contentHeight));
            float fraction = Material.clamp(scroll.value() / visible, 0.0F, 1.0F);
            ui.rounded(x + w - 4.0F, y + (h - trackHeight) * fraction, 4.0F, trackHeight, 2.0F,
                    Material.withAlpha(scheme.outlineVariant, 0.8F));
        }
    }

    public boolean click(float mouseX, float mouseY, int button) {
        if (module == null) {
            return false;
        }
        if (binding && button >= 0 && button < 16) {
            module.setBind(button - 100);
            binding = false;
            return true;
        }
        if (menuIndex >= 0 && menuIndex < rows.size() && rows.get(menuIndex) instanceof ModeRow) {
            ModeRow mode = (ModeRow) rows.get(menuIndex);
            int index = menuIndexAt(mode, mouseX, mouseY);
            if (index >= 0) {
                mode.select(index);
                this.menuIndex = -1;
                return true;
            }
            this.menuIndex = -1;
        }
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (mouseY >= row.y && mouseY < row.y + row.height && row.click(mouseX, mouseY, button)) {
                if (row instanceof ModeRow) {
                    this.menuIndex = menuIndex == i ? -1 : i;
                }
                this.activeRow = row;
                return true;
            }
        }
        return false;
    }

    public void drag(float mouseX, float mouseY, int button) {
        if (activeRow != null && button == 0) {
            activeRow.drag(mouseX, mouseY);
        }
    }

    public void release() {
        if (activeRow != null) {
            activeRow.release();
            activeRow = null;
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

    /** Captures the next key while the bind row is listening; returns true when consumed. */
    public boolean key(char typed, int key) {
        if (!binding || module == null) {
            return false;
        }
        if (key == 1) {
            module.setBind(0);
            binding = false;
            return true;
        }
        if (key > 0 && key < 256) {
            module.setBind(key);
            binding = false;
            return true;
        }
        return true;
    }

    public boolean isBinding() {
        return binding;
    }

    private int menuIndexAt(ModeRow mode, float mouseX, float mouseY) {
        float menuX = mode.menuX();
        float menuY = mode.menuY();
        float menuW = mode.menuWidth();
        float rowHeight = 32.0F;
        if (mouseX < menuX || mouseX >= menuX + menuW) {
            return -1;
        }
        int index = (int) ((mouseY - menuY - 4.0F) / rowHeight);
        if (mouseY < menuY + 4.0F || index < 0 || index >= mode.count()) {
            return -1;
        }
        return index;
    }

    // ------------------------------------------------------------------ //
    // Rows
    // ------------------------------------------------------------------ //

    private abstract static class Row {
        float x;
        float y;
        float w;
        float height;

        void layout(float x, float y, float w) {
            this.x = x;
            this.y = y;
            this.w = w;
        }

        void update(float delta, float mouseX, float mouseY, boolean interactive) {
        }

        abstract void draw(UI ui, Scheme scheme, float mouseX, float mouseY);

        boolean click(float mouseX, float mouseY, int button) {
            return false;
        }

        void drag(float mouseX, float mouseY) {
        }

        void release() {
        }

        boolean hovered(float mouseX, float mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + height;
        }
    }

    private final class BindRow extends Row {
        private final Anim hover = new Anim(0.0F);

        BindRow() {
            this.height = 34.0F;
        }

        @Override
        void update(float delta, float mouseX, float mouseY, boolean interactive) {
            hover.to(interactive && hovered(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            hover.update(delta);
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            Widgets.stateLayer(ui, scheme, x, y + 2.0F, w, height - 4.0F, 8.0F, hover.value(), 0.0F, scheme.onSurface);
            ui.text(Typeface.LABEL, "Bind", x + 8.0F, y + height / 2.0F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
            String label;
            int color;
            if (binding) {
                label = "Press a key...";
                color = scheme.primary;
            } else {
                label = keyName(module == null ? 0 : module.getKeycode());
                color = scheme.onSurface;
            }
            float modesX = x + w - 132.0F;
            ui.text(Typeface.BODY_MEDIUM, label, modesX - 12.0F, y + height / 2.0F, color, UI.ALIGN_RIGHT);
            ui.rounded(modesX, y + 5, 124, 24, 6, scheme.surfaceContainerHigh);
            boolean hold = module.isHoldBind();
            ui.rounded(modesX + (hold ? 62 : 0), y + 5, 62, 24, 6, scheme.primaryContainer);
            ui.text(Typeface.LABEL, "Toggle", modesX + 31, y + height / 2, hold ? scheme.onSurfaceVariant : scheme.onPrimaryContainer, UI.ALIGN_CENTER);
            ui.text(Typeface.LABEL, "Hold", modesX + 93, y + height / 2, hold ? scheme.onPrimaryContainer : scheme.onSurfaceVariant, UI.ALIGN_CENTER);
        }

        @Override
        boolean click(float mouseX, float mouseY, int button) {
            if (button != 0 || !hovered(mouseX, mouseY)) {
                return false;
            }
            if (mouseX >= x + w - 132) {
                module.setHoldBind(mouseX >= x + w - 70);
                binding = false;
            } else binding = !binding;
            return true;
        }

        private String keyName(int key) {
            if (key == 0) {
                return "None";
            }
            if (key < 0) return "Mouse " + (key + 101);
            String name = Keyboard.getKeyName(Math.abs(key));
            return name == null ? "None" : name;
        }
    }

    private final class SliderRow extends Row {
        private final SliderSetting setting;
        private final Anim hover = new Anim(0.0F);
        private final Anim active = new Anim(0.0F);
        private final Anim visual = new Anim(0.0F);
        private boolean dragging;

        SliderRow(SliderSetting setting) {
            this.setting = setting;
            this.height = 44.0F;
            this.visual.set(fraction());
        }

        private float fraction() {
            double range = setting.getMax() - setting.getMin();
            return range <= 0.0D ? 0.0F : (float) ((setting.getVisualInput() - setting.getMin()) / range);
        }

        @Override
        void update(float delta, float mouseX, float mouseY, boolean interactive) {
            hover.to(interactive && hovered(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            active.to(dragging ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            hover.update(delta);
            active.update(delta);
            visual.to(fraction(), dragging ? 0.0F : 120.0F, Ease.DECELERATE);
            visual.update(delta);
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            float trackX = x + 8.0F;
            float trackW = w - 16.0F;
            ui.text(Typeface.LABEL, setting.getName(), x + 8.0F, y + 10.0F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
            ui.text(Typeface.LABEL, setting.getDisplay(false), x + w - 8.0F, y + 10.0F, scheme.primary, UI.ALIGN_RIGHT);
            Widgets.slider(ui, scheme, trackX, y + 30.0F, trackW, visual.value(),
                    hover.value() > 0.5F, dragging);
        }

        @Override
        boolean click(float mouseX, float mouseY, int button) {
            if (button != 0 || !hovered(mouseX, mouseY)) {
                return false;
            }
            dragging = true;
            apply(mouseX);
            return true;
        }

        @Override
        void drag(float mouseX, float mouseY) {
            if (dragging) {
                apply(mouseX);
            }
        }

        private void apply(float mouseX) {
            float trackX = x + 8.0F;
            float trackW = w - 16.0F;
            float t = Material.clamp((mouseX - trackX) / trackW, 0.0F, 1.0F);
            double value = setting.getMin() + t * (setting.getMax() - setting.getMin());
            setting.setValue(value);
            setting.setVisualInput(value);
        }

        @Override
        void release() {
            dragging = false;
        }
    }

    private final class ModeRow extends Row {
        private final SliderSetting setting;
        private final Anim hover = new Anim(0.0F);

        ModeRow(SliderSetting setting) {
            this.setting = setting;
            this.height = 34.0F;
        }

        @Override
        void update(float delta, float mouseX, float mouseY, boolean interactive) {
            hover.to(interactive && hovered(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            hover.update(delta);
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            Widgets.stateLayer(ui, scheme, x, y + 2.0F, w, height - 4.0F, 8.0F, hover.value(), 0.0F, scheme.onSurface);
            ui.text(Typeface.LABEL, setting.getName(), x + 8.0F, y + height / 2.0F, scheme.onSurfaceVariant, UI.ALIGN_LEFT);
            String display = setting.getDisplay(false);
            float textWidth = Typeface.BODY_MEDIUM.width(display);
            ui.text(Typeface.BODY_MEDIUM, display, x + w - 30.0F, y + height / 2.0F, scheme.primary, UI.ALIGN_RIGHT);
            ui.icon(Icons.EXPAND_MORE, x + w - 14.0F, y + height / 2.0F, 16.0F,
                    textWidth > 0.0F ? scheme.onSurfaceVariant : scheme.onSurfaceVariant);
        }

        @Override
        boolean click(float mouseX, float mouseY, int button) {
            return button == 0 && hovered(mouseX, mouseY);
        }

        int count() {
            return setting.options == null ? 0 : setting.options.length;
        }

        void select(int index) {
            setting.setValue(index);
            setting.setVisualInput(index);
        }

        float menuWidth() {
            return 180.0F;
        }

        float menuX() {
            return x + w - menuWidth();
        }

        float menuY() {
            float height = count() * 32.0F + 8.0F;
            float below = y + this.height + 4.0F;
            float limit = DetailPage.this.y + DetailPage.this.h;
            return below + height > limit ? y - height - 4.0F : below;
        }

        void drawMenu(UI ui, Scheme scheme, int hovered) {
            Widgets.menu(ui, scheme, menuX(), menuY(), menuWidth(), setting.options,
                    (int) setting.getInput(), hovered, 1.0F);
        }
    }

    private final class ToggleRow extends Row {
        private final ButtonSetting setting;
        private final Module owner;
        private final Anim hover = new Anim(0.0F);
        private final Anim toggle = new Anim(0.0F);
        private boolean pressed;
        private boolean initialised;

        ToggleRow(ButtonSetting setting, Module owner) {
            this.setting = setting;
            this.owner = owner;
            this.height = 34.0F;
        }

        @Override
        void update(float delta, float mouseX, float mouseY, boolean interactive) {
            hover.to(interactive && hovered(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            hover.update(delta);
            if (!initialised) {
                initialised = true;
                toggle.set(setting.isToggled() ? 1.0F : 0.0F);
            }
            toggle.to(setting.isToggled() ? 1.0F : 0.0F, 250.0F, Ease.DECELERATE);
            toggle.update(delta);
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            Widgets.stateLayer(ui, scheme, x, y + 2.0F, w, height - 4.0F, 8.0F, hover.value(), pressed ? 1.0F : 0.0F,
                    scheme.onSurface);
            ui.text(Typeface.LABEL, setting.getName(), x + 8.0F, y + height / 2.0F, scheme.onSurface, UI.ALIGN_LEFT);
            Widgets.toggle(ui, scheme, x + w - 46.0F, y + (height - 22.0F) / 2.0F, toggle.value(),
                    hover.value() > 0.5F, pressed);
        }

        @Override
        boolean click(float mouseX, float mouseY, int button) {
            if (button != 0 || !hovered(mouseX, mouseY)) {
                return false;
            }
            pressed = true;
            setting.toggle();
            owner.guiButtonToggled(setting);
            return true;
        }

        @Override
        void release() {
            pressed = false;
        }
    }

    private final class ButtonRow extends Row {
        private final ButtonSetting setting;
        private final Module owner;
        private final Anim hover = new Anim(0.0F);
        private boolean pressed;

        ButtonRow(ButtonSetting setting, Module owner) {
            this.setting = setting;
            this.owner = owner;
            this.height = 34.0F;
        }

        @Override
        void update(float delta, float mouseX, float mouseY, boolean interactive) {
            hover.to(interactive && hovered(mouseX, mouseY) ? 1.0F : 0.0F, 150.0F, Ease.DECELERATE);
            hover.update(delta);
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            Widgets.stateLayer(ui, scheme, x, y + 2.0F, w, height - 4.0F, 8.0F, hover.value(), pressed ? 1.0F : 0.0F,
                    scheme.primary);
            ui.text(Typeface.LABEL, setting.getName(), x + 8.0F, y + height / 2.0F, scheme.primary, UI.ALIGN_LEFT);
            ui.icon(Icons.CHEVRON_RIGHT, x + w - 14.0F, y + height / 2.0F, 16.0F, scheme.primary);
            if (setting instanceof xyz.daikirai.module.setting.ColorSetting)
                ui.rounded(x + w - 48, y + 9, 18, 16, 3, ((xyz.daikirai.module.setting.ColorSetting) setting).color());
        }

        @Override
        boolean click(float mouseX, float mouseY, int button) {
            if (button != 0 || !hovered(mouseX, mouseY)) {
                return false;
            }
            pressed = true;
            setting.toggle();
            owner.guiButtonToggled(setting);
            return true;
        }

        @Override
        void release() {
            pressed = false;
        }
    }

    private final class DescriptionRow extends Row {
        private final DescriptionSetting setting;
        private final List<String> lines = new ArrayList<String>();

        private float measuredWidth = -1.0F;

        DescriptionRow(DescriptionSetting setting) {
            this.setting = setting;
            this.height = 30.0F;
        }

        @Override
        void layout(float x, float y, float w) {
            super.layout(x, y, w);
            if (measuredWidth == w && !lines.isEmpty()) {
                return;
            }
            measuredWidth = w;
            this.lines.clear();
            this.lines.addAll(rowsFor(w));
            this.height = Math.max(26.0F, lines.size() * 16.0F + 12.0F);
        }

        private List<String> rowsFor(float width) {
            List<String> result = new ArrayList<String>();
            StringBuilder current = new StringBuilder();
            for (String word : setting.getDesc().split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (Typeface.BODY.width(candidate) > width - 16.0F && current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                } else {
                    current.setLength(0);
                    current.append(candidate);
                }
            }
            if (current.length() > 0) {
                result.add(current.toString());
            }
            return result;
        }

        @Override
        void draw(UI ui, Scheme scheme, float mouseX, float mouseY) {
            float lineY = y + 10.0F;
            for (String line : lines) {
                ui.textTop(Typeface.BODY, line, x + 8.0F, lineY, Material.withAlpha(scheme.onSurfaceVariant, 0.8F),
                        UI.ALIGN_LEFT);
                lineY += 16.0F;
            }
        }
    }
}
