package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiCustomizeSkin;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Tablet style rewrite of the vanilla options screen: categories on the left rail, the selected
 * category's settings on the right as a Material 3 Expressive card of switch / chip / slider rows.
 *
 * <p>Every row is bound to {@link GameSettings} through {@code GameSettings.Options}, so values,
 * ranges, snapping and localisation all come from the game itself - the screen only replaces the
 * chrome. Structural screens (key binds, resource packs, skin, language) are one chevron row each
 * and open the originals.</p>
 */
public final class AndroidSettingsScreen extends GuiScreen {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 360.0F;
    private static final float PAD = 14.0F;
    private static final float RAIL_WIDTH = 196.0F;
    private static final float CARD_RADIUS = 24.0F;
    private static final float ROW = 52.0F;
    private static final float SLIDER_ROW = 66.0F;
    /** One rail entry; entries are flush, so this is also the group's pitch. */
    private static final float ITEM_HEIGHT = 52.0F;
    /** Top of the rail's contained list, just under the "Options" title. */
    private static final float RAIL_Y = 50.0F;

    private static final int SWITCH = 0;
    private static final int CYCLE = 1;
    private static final int SLIDER = 2;
    private static final int LINK = 3;
    private static final int ACTION = 4;

    private static final String[] CATEGORY_LABELS = {"Video", "Sound", "Controls", "Chat", "Appearance"};
    private static final String[] CATEGORY_HINTS = {"Graphics, distance, FOV", "Music, blocks, mobs",
            "Sensitivity, key binds", "Visibility, opacity", "Packs, skin, language"};
    private static final String[] CATEGORY_ICONS = {"\ue30b", "\ue050", "\ue323", "\ue0ca", "\ue40a"};

    private static final String ICON_EXTENSION = "\ue87b";
    private static final String ICON_CHEVRON = "\ue5cc";

    private final GuiScreen parent;
    private final UI ui = new UI();
    private final Spring opening = new Spring(0.0F);
    private final Spring scrollSpring = new Spring(0.0F);
    private final Spring[] railHover = new Spring[CATEGORY_LABELS.length];
    private final Spring[] rowHover = new Spring[24];
    private final Anim paneAnim = new Anim(0.0F);

    private float scale = 1.0F;
    private float ox;
    private float oy;
    private float mx;
    private float my;
    private float dt;
    private long lastFrame;

    private float railX;
    private float railY;
    private float cardX;
    private float cardY;
    private float cardWidth;
    private float cardHeight;
    private float doneX;
    private float doneY;
    private float doneWidth;
    private float doneHeight;
    private float modsY;
    private float modsHeight;

    private int category;
    private float scrollTarget;
    private Row dragging;
    private float dragMin;
    private float dragMax;

    private final List<Row> rows = new ArrayList<Row>();

    public AndroidSettingsScreen(GuiScreen parent) {
        this.parent = parent;
        for (int i = 0; i < railHover.length; i++) {
            railHover[i] = new Spring(0.0F);
        }
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0F);
        }
    }

    private GameSettings gs() {
        return mc.gameSettings;
    }

    // ------------------------------------------------------------------ //
    // Layout
    // ------------------------------------------------------------------ //

    @Override
    public void initGui() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;

        railX = PAD;
        railY = PAD;
        cardX = railX + RAIL_WIDTH + 12.0F;
        cardY = 46.0F;
        cardWidth = DESIGN_WIDTH - PAD - cardX;
        // The style button floats over the card's bottom-right padding, never over a row.
        cardHeight = DESIGN_HEIGHT - cardY - PAD;
        doneWidth = 68.0F;
        doneHeight = 30.0F;
        doneX = DESIGN_WIDTH - PAD - doneWidth;
        doneY = 6.0F;
        modsY = DESIGN_HEIGHT - PAD - 26.0F;
        modsHeight = 26.0F;

        buildRows(category);
        lastFrame = 0L;
        opening.set(0.0F);
        opening.target(1.0F);
        scrollSpring.set(scrollTarget);
        paneAnim.set(1.0F);
    }

    private void selectCategory(int index) {
        if (index == category) {
            return;
        }
        category = index;
        buildRows(index);
        scrollTarget = 0.0F;
        paneAnim.set(0.0F);
        paneAnim.to(1.0F, 220.0F, Ease.STANDARD);
    }

    // ------------------------------------------------------------------ //
    // Rows
    // ------------------------------------------------------------------ //

    private static final class Row {
        final int kind;
        final GameSettings.Options option;
        final SoundCategory sound;
        final String label;
        final String hint;
        final Runnable action;

        Row(int kind, GameSettings.Options option, SoundCategory sound, String label, String hint, Runnable action) {
            this.kind = kind;
            this.option = option;
            this.sound = sound;
            this.label = label;
            this.hint = hint;
            this.action = action;
        }
    }

    private Row toggle(GameSettings.Options option) {
        return new Row(SWITCH, option, null, I18n.format(option.getEnumString()), null, null);
    }

    private Row cycle(GameSettings.Options option) {
        return new Row(CYCLE, option, null, I18n.format(option.getEnumString()), null, null);
    }

    private Row slider(GameSettings.Options option) {
        return new Row(SLIDER, option, null, I18n.format(option.getEnumString()), null, null);
    }

    private Row volume(SoundCategory category) {
        String name = I18n.format("soundCategory." + category.getCategoryName());
        return new Row(SLIDER, null, category, name, null, null);
    }

    private Row link(String label, String hint, Runnable action) {
        return new Row(LINK, null, null, label, hint, action);
    }

    private Row action(String label, String hint, Runnable action) {
        return new Row(ACTION, null, null, label, hint, action);
    }

    private void buildRows(int category) {
        rows.clear();
        GameSettings settings = gs();
        switch (category) {
            case 0:
                rows.add(slider(GameSettings.Options.FOV));
                rows.add(slider(GameSettings.Options.GAMMA));
                rows.add(slider(GameSettings.Options.RENDER_DISTANCE));
                rows.add(slider(GameSettings.Options.FRAMERATE_LIMIT));
                rows.add(cycle(GameSettings.Options.GRAPHICS));
                rows.add(cycle(GameSettings.Options.AMBIENT_OCCLUSION));
                rows.add(cycle(GameSettings.Options.RENDER_CLOUDS));
                rows.add(cycle(GameSettings.Options.PARTICLES));
                rows.add(toggle(GameSettings.Options.VIEW_BOBBING));
                rows.add(toggle(GameSettings.Options.ENTITY_SHADOWS));
                rows.add(action("Fullscreen", settings.fullScreen ? "On" : "Off", mc::toggleFullscreen));
                break;
            case 1:
                rows.add(volume(SoundCategory.MASTER));
                rows.add(volume(SoundCategory.MUSIC));
                rows.add(volume(SoundCategory.RECORDS));
                rows.add(volume(SoundCategory.BLOCKS));
                rows.add(volume(SoundCategory.MOBS));
                rows.add(volume(SoundCategory.ANIMALS));
                rows.add(volume(SoundCategory.PLAYERS));
                rows.add(volume(SoundCategory.WEATHER));
                rows.add(volume(SoundCategory.AMBIENT));
                break;
            case 2:
                rows.add(slider(GameSettings.Options.SENSITIVITY));
                rows.add(toggle(GameSettings.Options.INVERT_MOUSE));
                rows.add(toggle(GameSettings.Options.TOUCHSCREEN));
                rows.add(link("Key bindings", "Movement, inventory, chat", () -> mc.displayGuiScreen(new GuiControls(this, gs()))));
                break;
            case 3:
                rows.add(cycle(GameSettings.Options.CHAT_VISIBILITY));
                rows.add(slider(GameSettings.Options.CHAT_OPACITY));
                rows.add(slider(GameSettings.Options.CHAT_SCALE));
                rows.add(slider(GameSettings.Options.CHAT_WIDTH));
                rows.add(slider(GameSettings.Options.CHAT_HEIGHT_FOCUSED));
                rows.add(slider(GameSettings.Options.CHAT_HEIGHT_UNFOCUSED));
                rows.add(toggle(GameSettings.Options.CHAT_COLOR));
                rows.add(toggle(GameSettings.Options.CHAT_LINKS));
                rows.add(toggle(GameSettings.Options.CHAT_LINKS_PROMPT));
                rows.add(toggle(GameSettings.Options.REDUCED_DEBUG_INFO));
                break;
            default:
                rows.add(cycle(GameSettings.Options.GUI_SCALE));
                rows.add(link("Resource packs", "Textures and sounds", () -> mc.displayGuiScreen(new GuiScreenResourcePacks(this))));
                rows.add(link("Skin customization", "Cape, jacket, sleeves", () -> mc.displayGuiScreen(new GuiCustomizeSkin(this))));
                rows.add(link("Language", mc.getLanguageManager().getCurrentLanguage().getLanguageCode(),
                        () -> mc.displayGuiScreen(new GuiLanguage(this, gs(), mc.getLanguageManager()))));
                rows.add(toggle(GameSettings.Options.FORCE_UNICODE_FONT));
                rows.add(toggle(GameSettings.Options.USE_VBO));
                break;
        }
    }

    // ------------------------------------------------------------------ //
    // Values
    // ------------------------------------------------------------------ //

    private boolean enabled(Row row) {
        return row.option != null && gs().getOptionOrdinalValue(row.option);
    }

    private String value(Row row) {
        if (row.kind == ACTION) {
            return row.hint;
        }
        if (row.kind == LINK) {
            return row.hint;
        }
        if (row.sound != null) {
            return Math.round(gs().getSoundLevel(row.sound) * 100.0F) + "%";
        }
        if (row.kind == CYCLE) {
            // getKeyBinding() returns "Label: value"; the row already prints the label.
            String text = gs().getKeyBinding(row.option);
            int separator = text.indexOf(": ");
            return separator > 0 ? text.substring(separator + 2) : text;
        }
        float value = gs().getOptionFloatValue(row.option);
        if (row.option == GameSettings.Options.FRAMERATE_LIMIT) {
            return value >= 260.0F ? I18n.format("options.framerateLimit.max") : Math.round(value) + " fps";
        }
        if (row.option == GameSettings.Options.RENDER_DISTANCE) {
            return Math.round(value) + " chunks";
        }
        if (row.option == GameSettings.Options.FOV) {
            return Math.round(value) + "\u00b0";
        }
        if (row.option.getValueMax() <= 1.001F) {
            return Math.round(value * 100.0F) + "%";
        }
        return String.valueOf(Math.round(value));
    }

    private float fraction(Row row) {
        if (row.sound != null) {
            return Material.clamp(gs().getSoundLevel(row.sound), 0.0F, 1.0F);
        }
        return Material.clamp(row.option.normalizeValue(gs().getOptionFloatValue(row.option)), 0.0F, 1.0F);
    }

    private void clickRow(Row row) {
        if (row.kind == LINK || row.kind == ACTION) {
            row.action.run();
            return;
        }
        if (row.kind == SWITCH) {
            gs().setOptionValue(row.option, 1);
            return;
        }
        if (row.kind == CYCLE) {
            gs().setOptionValue(row.option, 1);
        }
    }

    private void setFraction(Row row, float normalized) {
        float value = Material.clamp(normalized, 0.0F, 1.0F);
        if (row.sound != null) {
            gs().setSoundLevel(row.sound, value);
            return;
        }
        if (row.option == GameSettings.Options.GUI_SCALE) {
            return;
        }
        gs().setOptionFloatValue(row.option, row.option.denormalizeValue(value));
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        opening.update(dt);
        paneAnim.update(dt);
        scrollSpring.target(scrollTarget);
        scrollSpring.update(dt);
        mx = mouseX / scale - ox;
        my = mouseY / scale - oy;
        updateDrag();

        float enter = Material.clamp(opening.value(), 0.0F, 1.0F);
        ui.begin();
        MenuBackground.mouse(mouseX, mouseY, width, height);
        MenuBackground.draw(ui, width, height, true);
        ui.rect(0.0F, 0.0F, width, height, Material.withAlpha(AndroidWallpaper.page(), 0.94F));
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        ui.translate(0.0F, (1.0F - enter) * 26.0F);
        ui.alphaPush(Material.clamp(enter * 1.4F, 0.0F, 1.0F));
        drawRail();
        drawPane();
        ui.alphaPop();
        ui.pop();
        SettingsStyle.drawFab(ui, true, width, height, mouseX, mouseY, dt);
        ui.end();
    }

    /**
     * Left rail as one contained list (Material 3 "contained list"): the five categories sit flush
     * in a single surface, so only the group's outer corners are rounded. The selected row morphs
     * into its own rounded shape on top of the group - the Android 16 settings pattern.
     */
    private void drawRail() {
        ui.textBaseline(Typeface.HEADLINE_LARGE, I18n.format("options.title"), railX + 4.0F, 26.0F,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        float groupHeight = ITEM_HEIGHT * CATEGORY_LABELS.length;
        ui.shadow(railX, RAIL_Y, RAIL_WIDTH, groupHeight, 24.0F, 1);
        ui.rounded(railX, RAIL_Y, RAIL_WIDTH, groupHeight, 24.0F, AndroidWallpaper.card());

        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            float y = RAIL_Y + i * ITEM_HEIGHT;
            boolean selected = i == category;
            boolean hovered = railHit(i, mx, my);
            railHover[i].target(hovered ? 1.0F : 0.0F);
            railHover[i].update(dt);
            if (selected) {
                ui.rounded(railX, y, RAIL_WIDTH, ITEM_HEIGHT, 20.0F, AndroidWallpaper.primaryContainer());
            } else if (railHover[i].value() > 0.01F) {
                ui.rounded(railX, y, RAIL_WIDTH, ITEM_HEIGHT, 20.0F,
                        Material.withAlpha(AndroidWallpaper.ink(), 0.05F * railHover[i].value()));
            }
            int glyph = selected ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.onSurfaceVariant();
            ui.circle(railX + 30.0F, y + ITEM_HEIGHT / 2.0F, 16.0F,
                    selected ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.12F)
                            : AndroidWallpaper.surfaceHigh());
            ui.icon(CATEGORY_ICONS[i], railX + 30.0F, y + ITEM_HEIGHT / 2.0F, 17.0F, glyph);
            ui.text(Typeface.BODY_MEDIUM, CATEGORY_LABELS[i], railX + 54.0F, y + 20.0F,
                    selected ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.ink(), UI.ALIGN_LEFT);
            ui.text(Typeface.CAPTION, CATEGORY_HINTS[i], railX + 54.0F, y + 35.0F,
                    selected ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.7F)
                            : AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        }

        boolean modsHovered = point(mx, my, railX, modsY, RAIL_WIDTH, modsHeight);
        ui.rounded(railX, modsY, RAIL_WIDTH, modsHeight, 14.0F,
                Material.withAlpha(AndroidWallpaper.ink(), modsHovered ? 0.08F : 0.04F));
        ui.icon(ICON_EXTENSION, railX + 22.0F, modsY + modsHeight / 2.0F, 16.0F, AndroidWallpaper.onSurfaceVariant());
        ui.text(Typeface.LABEL, "Mods", railX + 40.0F, modsY + modsHeight / 2.0F,
                AndroidWallpaper.onSurfaceVariant(), UI.ALIGN_LEFT);
    }

    private void drawPane() {
        float fade = Material.clamp(paneAnim.value(), 0.0F, 1.0F);
        ui.textBaseline(Typeface.HEADLINE_LARGE, CATEGORY_LABELS[category], cardX + 4.0F, 26.0F,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        boolean doneHovered = point(mx, my, doneX, doneY, doneWidth, doneHeight);
        ui.rounded(doneX, doneY, doneWidth, doneHeight, 15.0F,
                doneHovered ? AndroidWallpaper.primaryContainer()
                        : Material.mix(AndroidWallpaper.card(), AndroidWallpaper.primaryContainer(), 0.55F));
        ui.text(Typeface.LABEL, "Done", doneX + doneWidth / 2.0F, doneY + doneHeight / 2.0F,
                AndroidWallpaper.onPrimaryContainer(), UI.ALIGN_CENTER);

        ui.shadow(cardX, cardY, cardWidth, cardHeight, CARD_RADIUS, 2);
        ui.rounded(cardX, cardY, cardWidth, cardHeight, CARD_RADIUS, AndroidWallpaper.card());
        ui.scissorPush(cardX, cardY, cardWidth, viewport());
        ui.push();
        ui.translate((1.0F - fade) * 14.0F, 0.0F);
        ui.alphaPush(fade);

        float y = cardY + 6.0F - scrollSpring.value();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float height = row.kind == SLIDER ? SLIDER_ROW : ROW;
            if (fullyVisible(y, height)) {
                drawRow(row, i, y, height);
                float divider = y + height;
                if (i < rows.size() - 1 && divider < cardY + viewport()) {
                    ui.rect(cardX + 20.0F, divider, cardWidth - 40.0F, 1.0F,
                            Material.withAlpha(AndroidWallpaper.ink(), 0.07F));
                }
            }
            y += height;
        }
        ui.alphaPop();
        ui.pop();
        ui.scissorPop();
        drawScrollbar();
    }

    private void drawRow(Row row, int index, float y, float height) {
        boolean hovered = rowHit(y, height, mx, my);
        Spring hover = rowHover[index % rowHover.length];
        hover.target(hovered ? 1.0F : 0.0F);
        hover.update(dt);
        int ink = AndroidWallpaper.ink();
        if (hover.value() > 0.01F && row.kind != SLIDER) {
            ui.rounded(cardX + 8.0F, y + 4.0F, cardWidth - 16.0F, height - 8.0F, 14.0F,
                    Material.withAlpha(ink, 0.04F * hover.value()));
        }
        float left = cardX + 20.0F;
        if (row.kind == SLIDER) {
            ui.textBaseline(Typeface.BODY_MEDIUM, row.label, left, y + 26.0F, ink, UI.ALIGN_LEFT);
            ui.textBaseline(Typeface.LABEL, value(row), cardX + cardWidth - 20.0F, y + 26.0F,
                    AndroidWallpaper.softInk(), UI.ALIGN_RIGHT);
            drawSlider(left, y + 48.0F, cardWidth - 40.0F, fraction(row), hover.value() > 0.5F || row == dragging);
            return;
        }
        ui.textBaseline(Typeface.BODY_MEDIUM, row.label, left, y + 26.0F, ink, UI.ALIGN_LEFT);
        if (row.hint != null && row.kind != ACTION) {
            ui.textBaseline(Typeface.CAPTION, row.hint, left, y + 42.0F, AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        }
        if (row.kind == SWITCH) {
            drawSwitch(cardX + cardWidth - 62.0F, y + height / 2.0F - 13.0F, enabled(row));
        } else if (row.kind == CYCLE) {
            ui.rounded(cardX + cardWidth - 20.0F - chipWidth(row), y + height / 2.0F - 13.0F, chipWidth(row), 26.0F,
                    13.0F, AndroidWallpaper.surfaceHigh());
            ui.text(Typeface.LABEL, value(row), cardX + cardWidth - 20.0F - chipWidth(row) / 2.0F,
                    y + height / 2.0F, AndroidWallpaper.ink(), UI.ALIGN_CENTER);
        } else if (row.kind == LINK) {
            ui.icon(ICON_CHEVRON, cardX + cardWidth - 30.0F, y + height / 2.0F, 16.0F,
                    AndroidWallpaper.outlineVariant());
        } else if (row.kind == ACTION) {
            ui.text(Typeface.LABEL, value(row), cardX + cardWidth - 20.0F, y + height / 2.0F,
                    AndroidWallpaper.primary(), UI.ALIGN_RIGHT);
        }
    }

    private float chipWidth(Row row) {
        return Math.max(52.0F, Typeface.LABEL.width(value(row)) + 28.0F);
    }

    private void drawSwitch(float x, float y, boolean on) {
        float w = 44.0F;
        float h = 26.0F;
        float radius = h / 2.0F;
        ui.rounded(x, y, w, h, radius, on ? AndroidWallpaper.primary() : trackColor());
        if (!on) {
            ui.outline(x, y, w, h, radius, 1.5F, AndroidWallpaper.outlineVariant());
        }
        float knob = on ? 18.0F : 16.0F;
        float cx = on ? x + w - radius : x + radius;
        ui.circle(cx, y + radius, knob / 2.0F, on ? AndroidWallpaper.onPrimary() : AndroidWallpaper.outline());
    }

    /** Inactive slider / switch track: just dark enough to read on the white card. */
    private static int trackColor() {
        return Material.mix(AndroidWallpaper.surfaceHigh(), AndroidWallpaper.outlineVariant(), 0.55F);
    }

    private void drawSlider(float x, float y, float width, float fraction, boolean active) {
        float track = active ? 5.0F : 4.0F;
        ui.rounded(x, y - track / 2.0F, width, track, track / 2.0F, trackColor());
        float filled = Math.max(track, width * fraction);
        ui.rounded(x, y - track / 2.0F, filled, track, track / 2.0F, AndroidWallpaper.primary());
        if (active) {
            ui.circle(x + width * fraction, y, 12.0F,
                    Material.withAlpha(AndroidWallpaper.primary(), 0.16F));
        }
        ui.circle(x + width * fraction, y, active ? 9.0F : 8.0F, AndroidWallpaper.primary());
    }

    private void drawScrollbar() {
        float content = 12.0F;
        for (Row row : rows) {
            content += row.kind == SLIDER ? SLIDER_ROW : ROW;
        }
        if (content <= viewport()) {
            return;
        }
        float trackHeight = viewport() - 24.0F;
        float thumb = Math.max(24.0F, trackHeight * viewport() / content);
        float progress = Material.clamp(scrollSpring.value() / (content - viewport()), 0.0F, 1.0F);
        ui.rounded(cardX + cardWidth - 6.0F, cardY + 12.0F + (trackHeight - thumb) * progress, 3.0F, thumb, 1.5F,
                Material.withAlpha(AndroidWallpaper.ink(), 0.18F));
    }

    // ------------------------------------------------------------------ //
    // Hit testing
    // ------------------------------------------------------------------ //

    private static boolean point(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    private boolean railHit(int index, float x, float y) {
        float top = RAIL_Y + index * ITEM_HEIGHT;
        return point(x, y, railX, top, RAIL_WIDTH, ITEM_HEIGHT);
    }

    /** Rows are only drawn and hit-tested when they fit completely, so no half row is ever cut. */
    /** Height of the card that rows may occupy; the rest is the floating button's padding. */
    private float viewport() {
        return cardHeight - 46.0F;
    }

    private boolean fullyVisible(float y, float height) {
        // Rows are clipped at the viewport, so a partial row may hang into the bottom padding - the
        // standard scrollable-list affordance. It just has to be visible at all.
        return y < cardY + viewport() && y + height > cardY;
    }

    private boolean rowHit(float rowY, float height, float x, float y) {
        return fullyVisible(rowY, height) && point(x, y, cardX, rowY, cardWidth, height);
    }

    /** Row under the pointer, or {@code null}. */
    private Row rowAt(float x, float y) {
        if (!point(x, y, cardX, cardY, cardWidth, cardHeight)) {
            return null;
        }
        float rowY = cardY + 6.0F - scrollSpring.value();
        for (Row row : rows) {
            float height = row.kind == SLIDER ? SLIDER_ROW : ROW;
            if (rowHit(rowY, height, x, y)) {
                return row;
            }
            rowY += height;
        }
        return null;
    }

    private float sliderX() {
        return cardX + 20.0F;
    }

    private float sliderWidth() {
        return cardWidth - 40.0F;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        if (SettingsStyle.clickFab(mouseX, mouseY, width, height)) {
            return;
        }
        float x = mouseX / scale - ox;
        float y = mouseY / scale - oy;
        if (point(x, y, doneX, doneY, doneWidth, doneHeight)) {
            close();
            return;
        }
        if (point(x, y, railX, modsY, RAIL_WIDTH, modsHeight)) {
            mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                    new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
            mc.displayGuiScreen(new GuiModList(this));
            return;
        }
        for (int i = 0; i < CATEGORY_LABELS.length; i++) {
            if (railHit(i, x, y)) {
                mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(
                        new net.minecraft.util.ResourceLocation("gui.button.press"), 1.0F));
                selectCategory(i);
                return;
            }
        }
        Row row = rowAt(x, y);
        if (row == null) {
            return;
        }
        if (row.kind == SLIDER) {
            dragging = row;
            float fraction = (x - sliderX()) / sliderWidth();
            setFraction(row, fraction);
            return;
        }
        clickRow(row);
    }

    private void updateDrag() {
        if (dragging == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            dragging = null;
            return;
        }
        setFraction(dragging, (mx - sliderX()) / sliderWidth());
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        // The wheel value has to be read before the click loop consumes the event buffer.
        int wheel = Mouse.getEventDWheel();
        super.handleMouseInput();
        if (wheel != 0) {
            scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget - wheel * 0.55F));
        }
    }

    private float contentHeight() {
        float content = 12.0F;
        for (Row row : rows) {
            content += row.kind == SLIDER ? SLIDER_ROW : ROW;
        }
        return content;
    }

    private float maxScroll() {
        return Math.max(0.0F, contentHeight() - viewport());
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            close();
            return;
        }
        int step = 0;
        if (keyCode == Keyboard.KEY_DOWN) {
            step = 1;
        } else if (keyCode == Keyboard.KEY_UP) {
            step = -1;
        }
        if (step != 0) {
            scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget + step * 32.0F));
            return;
        }
        int digit = keyCode - Keyboard.KEY_1;
        if (digit >= 0 && digit < CATEGORY_LABELS.length) {
            selectCategory(digit);
        }
    }

    private void close() {
        // Never drop to "no screen" (that leaves a world-less pause menu one keystroke away) and
        // never return to a parent that needs the world once it is gone. In a world the safe
        // fallback is the pause menu: the title screen would strand the client mid-game.
        GuiScreen target = parent;
        if (target == null || (AndroidScreens.needsWorld(target) && mc.theWorld == null)) {
            target = mc.theWorld != null
                    ? new net.minecraft.client.gui.GuiIngameMenu()
                    : new net.minecraft.client.gui.GuiMainMenu();
        }
        mc.displayGuiScreen(target);
    }

    @Override
    public void onGuiClosed() {
        gs().saveOptions();
        if (mc.thePlayer != null) {
            gs().sendSettingsToServer();
        }
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public boolean doesGuiPauseGame() {
        return mc.theWorld == null || super.doesGuiPauseGame();
    }

    // ------------------------------------------------------------------ //
    // Harness hooks (design-space coordinates; used by MenuStyleTest)
    // ------------------------------------------------------------------ //

    public String[] categoryLabels() {
        return CATEGORY_LABELS.clone();
    }

    public int rowCount() {
        return rows.size();
    }

    public float[] categoryCenter(int index) {
        return new float[]{railX + 40.0F, RAIL_Y + index * ITEM_HEIGHT + ITEM_HEIGHT / 2.0F};
    }

    /** Centre of the row at {@code index}, in design space. */
    public float[] rowCenter(int index) {
        float y = cardY + 6.0F - scrollSpring.value();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float height = row.kind == SLIDER ? SLIDER_ROW : ROW;
            if (i == index) {
                return new float[]{cardX + 40.0F, y + height / 2.0F};
            }
            y += height;
        }
        return new float[]{cardX + 40.0F, cardY + 28.0F};
    }

    /** A point on the slider track of {@code index} at the given fraction, in design space. */
    public float[] sliderPoint(int index, float fraction) {
        float y = cardY + 6.0F - scrollSpring.value();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float height = row.kind == SLIDER ? SLIDER_ROW : ROW;
            if (i == index) {
                return new float[]{sliderX() + sliderWidth() * fraction, y + 48.0F};
            }
            y += height;
        }
        return new float[]{sliderX(), cardY + 48.0F};
    }

    public float scroll() {
        return scrollTarget;
    }
}
