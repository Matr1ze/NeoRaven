package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.Widgets;
import xyz.daikirai.gui.onyx.hud.AndroidClock;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.setting.ColorSetting;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Background settings shared by the title screens, with a live wallpaper preview. */
final class BackgroundScreen extends MenuScreen {

    private static final float PANEL_HEIGHT = 442.0F;
    private static final float LEFT = 22.0F;
    private static final float WIDTH = 516.0F;
    private static final float PREVIEW_Y = 52.0F;
    private static final float PREVIEW_H = 150.0F;
    private static final float ROWS_Y = PREVIEW_Y + PREVIEW_H + 14.0F;
    private static final float ROW_H = 36.0F;
    private static final float ROW_GAP = 6.0F;
    private static final float MENU_W = 208.0F;
    private static final float MENU_X = LEFT + WIDTH - MENU_W;
    private static final float DOCK_Y = rowY(1);
    private static final float DARK_Y = rowY(2);

    private static final String[] BLUR_OPTIONS = {"Off", "Mica", "Frosted glass"};
    private static final String[] CLOCK_OPTIONS = {"Digital", "Analog"};

    private final ColorSetting dockColor = new ColorSetting("Dock colour", 0xFFFFFFFF);
    private final List<MenuBackground.Style> backgrounds = Arrays.asList(MenuBackground.styles());

    private final Dropdown wallpaper = new Dropdown("Background image", rowY(0), backgrounds.size(),
            index -> MenuBackground.label(backgrounds.get(index)),
            () -> backgrounds.indexOf(MenuBackground.current()), index -> MenuBackground.select(backgrounds.get(index)));
    private final Dropdown blur = new Dropdown("Background blur", rowY(3), BLUR_OPTIONS.length,
            index -> BLUR_OPTIONS[index], MenuTheme::blurMode, MenuTheme::setBlurMode);
    private final Dropdown clock = new Dropdown("Clock face", rowY(4), CLOCK_OPTIONS.length,
            index -> CLOCK_OPTIONS[index], MenuTheme::clockStyle, MenuTheme::setClockStyle);
    private final Dropdown[] dropdowns = {wallpaper, blur, clock};
    private Dropdown openDropdown;

    BackgroundScreen(GuiScreen parent) {
        super(parent, 560.0F, PANEL_HEIGHT);
        dockColor.setAppearance(MenuTheme.dockColor() == 0 ? 0xFFFFFFFF : MenuTheme.dockColor(),
                MenuTheme.dockFollows());
    }

    private static float rowY(int index) {
        return ROWS_Y + index * (ROW_H + ROW_GAP);
    }

    private static boolean rowHit(float y, float mx, float my) {
        return mx >= LEFT && mx < LEFT + WIDTH && my >= y && my < y + ROW_H;
    }

    @Override
    void drawContent(Scheme s) {
        title("Menu background");
        // The colour editor writes into the setting; mirror its saved value into the menu theme.
        int wanted = dockColor.followsTheme() ? 0 : dockColor.color();
        if (wanted != MenuTheme.dockColor() || dockColor.followsTheme() != MenuTheme.dockFollows()) {
            MenuTheme.setDock(dockColor.color(), dockColor.followsTheme());
        }

        MenuBackground.Style current = MenuBackground.current();
        ui.scissorPush(LEFT, PREVIEW_Y, WIDTH, PREVIEW_H);
        MenuBackground.drawInto(ui, previewStyle(), LEFT, PREVIEW_Y, WIDTH, PREVIEW_H, false,
                System.currentTimeMillis());
        ui.scissorPop();
        ui.outline(LEFT, PREVIEW_Y, WIDTH, PREVIEW_H, 18.0F, 1.0F, s.outlineVariant);

        drawDropdown(s, wallpaper, MenuBackground.label(current));
        drawDock(s);
        drawDarkMode(s);
        drawDropdown(s, blur, blur.optionLabel.apply(MenuTheme.blurMode()));
        if (!current.wallpaper()) {
            caption(s, blur.y, "Only affects wallpapers");
        }
        drawDropdown(s, clock, clock.optionLabel.apply(MenuTheme.clockStyle()));
        drawClockGlyph(s, LEFT + WIDTH - 116.0F, clock.y + ROW_H / 2.0F, 11.0F,
                MenuTheme.clockStyle() == MenuTheme.CLOCK_ANALOG);
    }

    private MenuBackground.Style previewStyle() {
        int hovered = openDropdown == wallpaper ? wallpaper.optionAt(mx, my) : -1;
        return hovered >= 0 ? backgrounds.get(hovered) : MenuBackground.current();
    }

    @Override
    void drawOverlay(Scheme s) {
        if (openDropdown == null) {
            return;
        }
        Dropdown dropdown = openDropdown;
        String[] options = new String[dropdown.count];
        for (int i = 0; i < options.length; i++) {
            options[i] = dropdown.optionLabel.apply(i);
        }
        Widgets.menu(ui, s, MENU_X, dropdown.popupY(), MENU_W, options, dropdown.selected.getAsInt(),
                dropdown.optionAt(mx, my), 1.0F);
    }

    private void drawRow(Scheme s, float y, String label) {
        boolean hovered = rowHit(y, mx, my) && (openDropdown == null || !openDropdown.popupHit(mx, my));
        ui.rounded(LEFT, y, WIDTH, ROW_H, 14.0F,
                Material.mix(s.surfaceContainerHighest, s.onSurface, hovered ? 0.08F : 0.0F));
        ui.text(Typeface.BODY_MEDIUM, label, LEFT + 18.0F, y + ROW_H / 2.0F, s.onSurface, UI.ALIGN_LEFT);
    }

    private void drawDropdown(Scheme s, Dropdown dropdown, String summary) {
        drawRow(s, dropdown.y, dropdown.label);
        float centerY = dropdown.y + ROW_H / 2.0F;
        ui.text(Typeface.BODY_MEDIUM, summary, LEFT + WIDTH - 44.0F, centerY, s.primary, UI.ALIGN_RIGHT);
        ui.icon(Icons.EXPAND_MORE, LEFT + WIDTH - 22.0F, centerY, 16.0F,
                openDropdown == dropdown ? s.primary : s.onSurfaceVariant);
    }

    private void drawDock(Scheme s) {
        drawRow(s, DOCK_Y, "Dock colour");
        caption(s, DOCK_Y, dockColor.followsTheme() ? "Follows the wallpaper" : "Custom colour");
        float centerY = DOCK_Y + ROW_H / 2.0F;
        int swatch = dockColor.followsTheme() ? AndroidWallpaper.dock() : dockColor.color();
        ui.rounded(LEFT + WIDTH - 76.0F, centerY - 11.0F, 40.0F, 22.0F, 11.0F, swatch);
        ui.outline(LEFT + WIDTH - 76.0F, centerY - 11.0F, 40.0F, 22.0F, 11.0F, 1.0F, s.outlineVariant);
        ui.icon(Icons.PALETTE, LEFT + WIDTH - 22.0F, centerY, 16.0F, s.outlineVariant);
    }

    private void drawDarkMode(Scheme s) {
        drawRow(s, DARK_Y, "Dark mode");
        caption(s, DARK_Y, "Shared across client screens and HUDs");
        float trackX = LEFT + WIDTH - 22.0F - Widgets.SWITCH_WIDTH;
        Widgets.toggle(ui, s, trackX, DARK_Y + (ROW_H - Widgets.SWITCH_HEIGHT) / 2.0F,
                MenuTheme.dark() ? 1.0F : 0.0F, rowHit(DARK_Y, mx, my) && openDropdown == null, false);
    }

    private void caption(Scheme s, float y, String caption) {
        ui.text(Typeface.CAPTION, caption, LEFT + 150.0F, y + ROW_H / 2.0F, s.onSurfaceVariant, UI.ALIGN_LEFT);
    }

    private void drawClockGlyph(Scheme s, float cx, float cy, float radius, boolean analog) {
        if (analog) {
            AndroidClock.glyph(ui, cx, cy, radius, s);
        } else {
            ui.rounded(cx - radius * 0.72F, cy - radius * 0.6F, radius * 0.46F, radius * 1.1F,
                    radius * 0.16F, s.onSurface);
            ui.rounded(cx - radius * 0.10F, cy - radius * 0.6F, radius * 0.46F, radius * 1.1F,
                    radius * 0.16F, s.onSurface);
            ui.circle(cx + radius * 0.56F, cy - radius * 0.28F, radius * 0.12F, s.onSurface);
            ui.circle(cx + radius * 0.56F, cy + radius * 0.32F, radius * 0.12F, s.onSurface);
        }
    }

    @Override
    boolean clickContent(float x, float y) {
        if (openDropdown != null) {
            Dropdown dropdown = openDropdown;
            openDropdown = null;
            if (dropdown.popupHit(x, y)) {
                int option = dropdown.optionAt(x, y);
                if (option >= 0) {
                    dropdown.select.accept(option);
                    click();
                }
                return true;
            }
            if (rowHit(dropdown.y, x, y)) {
                click();
                return true;
            }
        }
        for (Dropdown dropdown : dropdowns) {
            if (rowHit(dropdown.y, x, y)) {
                openDropdown = dropdown;
                click();
                return true;
            }
        }
        if (rowHit(DOCK_Y, x, y)) {
            click();
            mc.displayGuiScreen(new ColorEditor(this, dockColor));
            return true;
        }
        if (rowHit(DARK_Y, x, y)) {
            click();
            MenuTheme.toggle();
            return true;
        }
        return false;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE && openDropdown != null) {
            openDropdown = null;
            return;
        }
        super.keyTyped(c, key);
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    private static final class Dropdown {
        final String label;
        final float y;
        final int count;
        final IntFunction<String> optionLabel;
        final IntSupplier selected;
        final IntConsumer select;

        Dropdown(String label, float y, int count, IntFunction<String> optionLabel,
                 IntSupplier selected, IntConsumer select) {
            this.label = label;
            this.y = y;
            this.count = count;
            this.optionLabel = optionLabel;
            this.selected = selected;
            this.select = select;
        }

        float popupY() {
            float below = y + ROW_H + 4.0F;
            float height = Widgets.menuFullHeight(count);
            return below + height <= PANEL_HEIGHT - 12.0F ? below : y - height - 4.0F;
        }

        boolean popupHit(float x, float y) {
            float top = popupY();
            return x >= MENU_X && x < MENU_X + MENU_W && y >= top && y < top + Widgets.menuFullHeight(count);
        }

        int optionAt(float x, float y) {
            return popupHit(x, y) ? Widgets.menuOptionAt(y - popupY(), count) : -1;
        }
    }
}
