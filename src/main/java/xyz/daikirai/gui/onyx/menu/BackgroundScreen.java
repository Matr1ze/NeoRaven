package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.module.setting.ColorSetting;

/**
 * "Menu background" picker: a live preview of the hovered backdrop (drawn by {@link MenuBackground})
 * above a radio list of the wallpapers, plus two appearance switches underneath - the dock colour,
 * which opens the Material colour sheet ({@link ColorEditor}), and the dark mode of the Android
 * menus ({@link MenuTheme}).
 */
final class BackgroundScreen extends MenuScreen {

    private static final float LEFT = 22.0F;
    private static final float WIDTH = 516.0F;
    private static final float PREVIEW_Y = 52.0F;
    private static final float PREVIEW_H = 150.0F;
    private static final float ROWS_Y = PREVIEW_Y + PREVIEW_H + 14.0F;
    private static final float ROW_H = 40.0F;
    private static final float ROW_GAP = 6.0F;
    private static final float ACTION_H = 36.0F;

    /** Backs the colour sheet so the pick can be persisted by {@link MenuTheme}. */
    private final ColorSetting dockColor = new ColorSetting("Dock colour", 0xFFFFFFFF);

    BackgroundScreen(GuiScreen parent) {
        super(parent, 560.0F, 536.0F);
        dockColor.setAppearance(MenuTheme.dockColor() == 0 ? 0xFFFFFFFF : MenuTheme.dockColor(),
                MenuTheme.dockFollows());
    }

    private static float rowY(int index) {
        return ROWS_Y + index * (ROW_H + ROW_GAP);
    }

    private static float actionY(int index) {
        return rowY(MenuBackground.styles().length) + 8.0F + index * (ACTION_H + ROW_GAP);
    }

    private static boolean rowHit(int index, float mx, float my) {
        return mx >= LEFT && mx < LEFT + WIDTH && my >= rowY(index) && my < rowY(index) + ROW_H;
    }

    private static boolean actionHit(int index, float mx, float my) {
        return mx >= LEFT && mx < LEFT + WIDTH && my >= actionY(index) && my < actionY(index) + ACTION_H;
    }

    @Override
    void drawContent(Scheme s) {
        title("Menu background");
        // The colour sheet writes straight into the setting; mirror it into the saved theme.
        int wanted = dockColor.followsTheme() ? 0 : dockColor.color();
        if (wanted != MenuTheme.dockColor() || dockColor.followsTheme() != MenuTheme.dockFollows()) {
            MenuTheme.setDock(dockColor.color(), dockColor.followsTheme());
        }
        MenuBackground.Style[] styles = MenuBackground.styles();
        MenuBackground.Style hovered = MenuBackground.current();
        for (int i = 0; i < styles.length; i++) {
            if (rowHit(i, mx, my)) {
                hovered = styles[i];
            }
        }
        ui.scissorPush(LEFT, PREVIEW_Y, WIDTH, PREVIEW_H);
        MenuBackground.drawInto(ui, hovered, LEFT, PREVIEW_Y, WIDTH, PREVIEW_H, false,
                System.currentTimeMillis());
        ui.scissorPop();
        ui.outline(LEFT, PREVIEW_Y, WIDTH, PREVIEW_H, 18.0F, 1.0F, s.outlineVariant);
        for (int i = 0; i < styles.length; i++) {
            drawRow(i, styles[i], s);
        }
        drawActions(s);
    }

    private void drawRow(int index, MenuBackground.Style style, Scheme s) {
        float y = rowY(index);
        boolean selected = MenuBackground.current() == style;
        boolean over = rowHit(index, mx, my);
        int surface = selected
                ? Material.mix(s.secondaryContainer, s.primary, 0.12F)
                : Material.mix(s.surfaceContainerHighest, 0xFFFFFFFF, over ? 0.08F : 0.0F);
        ui.rounded(LEFT, y, WIDTH, ROW_H, 14.0F, surface);
        if (over) {
            ui.outline(LEFT, y, WIDTH, ROW_H, 14.0F, 1.0F, Material.withAlpha(s.primary, 0.55F));
        }
        ui.textBaseline(Typeface.BODY_MEDIUM, MenuBackground.label(style), LEFT + 18.0F,
                y + ROW_H / 2.0F, s.onSurface, UI.ALIGN_LEFT);
        ui.icon(selected ? Icons.CHECK : Icons.CHEVRON_RIGHT, LEFT + WIDTH - 22.0F,
                y + ROW_H / 2.0F, 16.0F, selected ? s.primary : s.outlineVariant);
    }

    private void drawActions(Scheme s) {
        // Dock colour: the swatch shows the pinned colour, or the wallpaper-following tone.
        float y = actionY(0);
        boolean over = actionHit(0, mx, my);
        ui.rounded(LEFT, y, WIDTH, ACTION_H, 14.0F,
                Material.mix(s.surfaceContainerHighest, 0xFFFFFFFF, over ? 0.08F : 0.0F));
        ui.textBaseline(Typeface.BODY_MEDIUM, "Dock colour", LEFT + 18.0F, y + ACTION_H / 2.0F,
                s.onSurface, UI.ALIGN_LEFT);
        ui.textBaseline(Typeface.CAPTION,
                dockColor.followsTheme() ? "Follows the wallpaper" : "Custom colour",
                LEFT + 150.0F, y + ACTION_H / 2.0F, s.onSurfaceVariant, UI.ALIGN_LEFT);
        int swatch = dockColor.followsTheme() ? AndroidWallpaper.dock() : dockColor.color();
        ui.rounded(LEFT + WIDTH - 76.0F, y + ACTION_H / 2.0F - 11.0F, 40.0F, 22.0F, 11.0F, swatch);
        ui.outline(LEFT + WIDTH - 76.0F, y + ACTION_H / 2.0F - 11.0F, 40.0F, 22.0F, 11.0F,
                1.0F, s.outlineVariant);
        ui.icon(Icons.PALETTE, LEFT + WIDTH - 22.0F, y + ACTION_H / 2.0F, 16.0F, s.outlineVariant);

        // Dark mode switch.
        float dy = actionY(1);
        boolean dark = MenuTheme.dark();
        boolean darkOver = actionHit(1, mx, my);
        ui.rounded(LEFT, dy, WIDTH, ACTION_H, 14.0F,
                Material.mix(s.surfaceContainerHighest, 0xFFFFFFFF, darkOver ? 0.08F : 0.0F));
        ui.textBaseline(Typeface.BODY_MEDIUM, "Dark mode", LEFT + 18.0F, dy + ACTION_H / 2.0F,
                s.onSurface, UI.ALIGN_LEFT);
        ui.textBaseline(Typeface.CAPTION, "Black theme for the menus", LEFT + 150.0F,
                dy + ACTION_H / 2.0F, s.onSurfaceVariant, UI.ALIGN_LEFT);
        switchTrack(s, dy, dark);

        // Background blur: off / Mica / the client's frosted glass, cycling on click.
        float by = actionY(2);
        int mode = MenuTheme.blurMode();
        boolean blurOver = actionHit(2, mx, my);
        ui.rounded(LEFT, by, WIDTH, ACTION_H, 14.0F,
                Material.mix(s.surfaceContainerHighest, 0xFFFFFFFF, blurOver ? 0.08F : 0.0F));
        ui.textBaseline(Typeface.BODY_MEDIUM, "Background blur", LEFT + 18.0F,
                by + ACTION_H / 2.0F, s.onSurface, UI.ALIGN_LEFT);
        String state;
        if (!MenuBackground.current().wallpaper()) {
            state = "Only affects wallpapers";
        } else if (mode == 1) {
            state = "Mica";
        } else if (mode == 2) {
            state = "Frosted glass";
        } else {
            state = "Off";
        }
        ui.textBaseline(Typeface.CAPTION, state, LEFT + 150.0F, by + ACTION_H / 2.0F,
                s.onSurfaceVariant, UI.ALIGN_LEFT);
        switchTrack(s, by, mode != 0);
        if (mode != 0) {
            ui.icon(Icons.EXPAND_MORE, LEFT + WIDTH - 78.0F, by + ACTION_H / 2.0F, 14.0F,
                    s.outlineVariant);
        }
    }

    private void switchTrack(Scheme s, float rowY, boolean on) {
        float trackW = 46.0F;
        float trackH = 24.0F;
        float trackX = LEFT + WIDTH - 22.0F - trackW;
        float trackY = rowY + (ACTION_H - trackH) / 2.0F;
        ui.rounded(trackX, trackY, trackW, trackH, trackH / 2.0F,
                on ? s.primary : s.surfaceContainerHighest);
        ui.outline(trackX, trackY, trackW, trackH, trackH / 2.0F, 1.0F, s.outlineVariant);
        ui.circle(trackX + (on ? trackW - trackH / 2.0F - 2.0F : trackH / 2.0F + 2.0F),
                trackY + trackH / 2.0F, trackH / 2.0F - 5.0F, on ? s.onPrimary : s.outline);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            MenuBackground.Style[] styles = MenuBackground.styles();
            for (int i = 0; i < styles.length; i++) {
                if (rowHit(i, mx, my)) {
                    MenuBackground.select(styles[i]);
                    click();
                    return;
                }
            }
            if (actionHit(0, mx, my)) {
                click();
                mc.displayGuiScreen(new ColorEditor(this, dockColor));
                return;
            }
            if (actionHit(1, mx, my)) {
                click();
                MenuTheme.toggle();
                return;
            }
            if (actionHit(2, mx, my)) {
                click();
                MenuTheme.nextBlurMode();
                return;
            }
        }
        try {
            super.mouseClicked(mouseX, mouseY, button);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(
                net.minecraft.client.audio.PositionedSoundRecord.create(
                        new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
    }
}
