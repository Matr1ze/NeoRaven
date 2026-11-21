package xyz.daikirai.gui.samsara;

import org.lwjgl.input.Keyboard;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.client.Profile;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Blur;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import xyz.daikirai.gui.samsara.SamsaraClickGui.Palette;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Local profile controls for the Samsara columns. */
final class SamsaraProfilesPane {
    private static final float PAD = 9.0F;
    private static final float HEADER_H = 27.0F;
    private static final float ROW_H = 21.0F;
    private static final float BUTTON_H = 19.0F;
    private static final float FOOTER_H = 49.0F;
    private static final Comparator<Profile> BY_NAME =
            Comparator.comparing((Profile profile) -> profile.name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(profile -> profile.path);

    private final List<Profile> profiles = new ArrayList<>();
    private int revision = -1;
    private Profile selected;
    private boolean focused;
    private String status = "";
    private long statusUntil;
    private float x, y, w, h;
    private float listY, listH, buttonY;
    private float scroll;

    void bounds(float x, float y, float w, float maxHeight) {
        refresh();
        this.x = x;
        this.y = y;
        this.w = w;
        float requestedList = Math.max(2, Math.min(4, profiles.size())) * ROW_H;
        this.listH = Math.min(requestedList, Math.max(0, maxHeight - HEADER_H - FOOTER_H));
        this.h = listH >= ROW_H / 2.0F && w >= 58.0F ? HEADER_H + listH + FOOTER_H : 0;
        this.listY = y + HEADER_H;
        this.buttonY = listY + listH + 8;
        this.scroll = Material.clamp(scroll, 0, maxScroll());
    }

    void draw(UI ui, float mouseX, float mouseY, Palette palette) {
        if (h <= 0) return;
        if (Blur.ready()) ui.frost(x, y, w, h, 10.0F, palette.body | 0xFF000000, 0.76F);
        else ui.rounded(x, y, w, h, 10.0F, palette.body);

        ui.text(Typeface.LABEL, ui.truncate(Typeface.LABEL, "Profiles", w - newButtonWidth() - 2 * PAD - 6),
                x + PAD, y + 13.0F, palette.text, UI.ALIGN_LEFT);
        drawButton(ui, newButtonX(), y + 5.0F, newButtonWidth(), "New", true, true,
                mouseX, mouseY, palette);
        ui.rect(x + PAD, listY - 1.0F, w - 2 * PAD, 1.0F, Material.withAlpha(palette.scrollbar, 0.34F));

        ui.scissorPush(x + PAD, listY, w - 2.0F * PAD, listH);
        try {
            drawRows(ui, mouseX, mouseY, palette);
        } finally {
            ui.scissorPop();
        }
        if (maxScroll() > 0) {
            float thumb = Math.max(9.0F, listH * listH / (profiles.size() * ROW_H));
            float top = listY + (listH - thumb) * scroll / maxScroll();
            ui.rounded(x + w - PAD - 2.0F, top, 2.0F, thumb, 1.0F, Material.withAlpha(palette.accent, 0.5F));
        }

        float actionWidth = (w - 2 * PAD - 5.0F) / 2.0F;
        drawButton(ui, x + PAD, buttonY, actionWidth, "Load", selected != null, false,
                mouseX, mouseY, palette);
        drawButton(ui, x + w - PAD - actionWidth, buttonY, actionWidth, "Save", canSave(), false,
                mouseX, mouseY, palette);
        if (System.currentTimeMillis() < statusUntil) {
            ui.text(Typeface.MICRO, ui.truncate(Typeface.MICRO, status, w - 2 * PAD),
                    x + PAD, buttonY + BUTTON_H + 9.0F, palette.muted, UI.ALIGN_LEFT);
        }
    }

    private void drawRows(UI ui, float mouseX, float mouseY, Palette palette) {
        if (profiles.isEmpty()) {
            ui.text(Typeface.CAPTION, ui.truncate(Typeface.CAPTION, "No saved profiles", w - 2 * PAD - 8),
                    x + PAD + 4.0F, listY + ROW_H / 2.0F, palette.muted, UI.ALIGN_LEFT);
            return;
        }
        float rowX = x + PAD;
        float rowWidth = w - 2 * PAD - (maxScroll() > 0 ? 5.0F : 0.0F);
        int first = (int) (scroll / ROW_H);
        for (int i = first; i < profiles.size(); i++) {
            float rowY = listY + i * ROW_H - scroll;
            if (rowY >= listY + listH) break;
            Profile profile = profiles.get(i);
            boolean active = profile == Profiles.current;
            boolean over = inside(mouseX, mouseY, rowX, rowY, rowWidth, ROW_H)
                    && inside(mouseX, mouseY, rowX, listY, rowWidth, listH);
            if (profile == selected || over) {
                ui.rounded(rowX, rowY + 1.0F, rowWidth, ROW_H - 2.0F, 5.0F,
                        Material.withAlpha(palette.accent, profile == selected ? 0.28F : 0.12F));
            }
            if (active) ui.icon(Icons.CHECK, rowX + rowWidth - 8.0F, rowY + ROW_H / 2.0F, 10.0F, palette.accent);
            String name = ui.truncate(Typeface.CAPTION, profile.name, rowWidth - (active ? 23.0F : 10.0F));
            ui.text(Typeface.CAPTION, name, rowX + 5.0F, rowY + ROW_H / 2.0F, palette.text, UI.ALIGN_LEFT);
        }
    }

    private void drawButton(UI ui, float left, float top, float width, String label, boolean enabled,
                            boolean addIcon, float mouseX, float mouseY, Palette palette) {
        boolean hover = enabled && inside(mouseX, mouseY, left, top, width, BUTTON_H);
        ui.rounded(left, top, width, BUTTON_H, 6.0F,
                Material.withAlpha(palette.accent, hover ? 0.33F : enabled ? 0.22F : 0.08F));
        float iconWidth = addIcon && width >= 35.0F ? 10.0F : 0.0F;
        int color = enabled ? palette.text : Material.withAlpha(palette.muted, 0.55F);
        if (iconWidth > 0) ui.icon(Icons.ADD, left + 8.0F, top + BUTTON_H / 2.0F, 10.0F, palette.accent);
        ui.text(Typeface.MICRO, ui.truncate(Typeface.MICRO, label, width - iconWidth - 4.0F),
                left + (width + iconWidth) / 2.0F, top + BUTTON_H / 2.0F, color, UI.ALIGN_CENTER);
    }

    boolean click(float mouseX, float mouseY, int button) {
        if (!inside(mouseX, mouseY, x, y, w, h)) {
            focused = false;
            return false;
        }
        if (button != 0) return true;
        if (inside(mouseX, mouseY, newButtonX(), y + 5.0F, newButtonWidth(), BUTTON_H)) {
            create();
        } else if (inside(mouseX, mouseY, x + PAD, listY, w - 2 * PAD, listH)) {
            int index = (int) ((mouseY - listY + scroll) / ROW_H);
            if (index < profiles.size()) {
                selected = profiles.get(index);
                focused = true;
                statusUntil = 0;
            }
        } else if (inside(mouseX, mouseY, x + PAD, buttonY, w - 2 * PAD, BUTTON_H)) {
            if (mouseX < x + w / 2.0F - 2.5F) load();
            else if (mouseX >= x + w / 2.0F + 2.5F) save();
        }
        return true;
    }

    boolean wheel(float mouseX, float mouseY, int wheel) {
        if (!inside(mouseX, mouseY, x, y, w, h)) return false;
        scroll = Material.clamp(scroll - Integer.signum(wheel) * ROW_H, 0, maxScroll());
        return true;
    }

    boolean keyTyped(char typed, int key) {
        if (!focused || selected == null || h <= 0) return false;
        if (key == Keyboard.KEY_ESCAPE) {
            focused = false;
            return true;
        }
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            load();
            return true;
        }
        if (key == Keyboard.KEY_UP || key == Keyboard.KEY_DOWN) {
            int index = profiles.indexOf(selected) + (key == Keyboard.KEY_UP ? -1 : 1);
            selected = profiles.get(Material.clamp(index, 0, profiles.size() - 1));
            showSelected();
            return true;
        }
        return false;
    }

    void onClose() {
        focused = false;
    }

    private void refresh() {
        if (revision == Profiles.revision()) return;
        revision = Profiles.revision();
        profiles.clear();
        profiles.addAll(Profiles.profiles.keySet());
        profiles.sort(BY_NAME);
        if (!profiles.contains(selected)) selected = profiles.contains(Profiles.current) ? Profiles.current : null;
    }

    private void load() {
        if (selected == null) return;
        boolean loaded = selected.load();
        setStatus(loaded ? "Profile loaded" : "Could not load");
    }

    private boolean canSave() {
        return selected != null && selected == Profiles.current;
    }

    private void save() {
        if (!canSave()) return;
        boolean saved = selected.save();
        setStatus(saved ? "Profile saved" : "Could not save");
    }

    private void create() {
        String name = Profiles.freeName();
        if (name.isEmpty()) {
            setStatus("No free profile slot");
            return;
        }
        Path file = Paths.get(ClientConfig.DIR, "profiles", name + Profiles.EXT);
        try {
            Files.createDirectories(file.getParent());
            // Reserve the generated name without replacing a profile that was never registered.
            Files.createFile(file);
            if (!Profiles.create(name)) {
                Files.deleteIfExists(file);
                setStatus("Could not create");
                return;
            }
        } catch (FileAlreadyExistsException exception) {
            setStatus("No free profile slot");
            return;
        } catch (IOException exception) {
            setStatus("Could not create");
            return;
        }
        refresh();
        selected = Profiles.current;
        focused = true;
        showSelected();
        setStatus("Created " + name);
    }

    private void showSelected() {
        float top = profiles.indexOf(selected) * ROW_H;
        float rowBottom = top + ROW_H;
        if (top < scroll) scroll = top;
        else if (rowBottom > scroll + listH) scroll = rowBottom - listH;
        scroll = Material.clamp(scroll, 0, maxScroll());
    }

    private void setStatus(String message) {
        status = message;
        statusUntil = System.currentTimeMillis() + 3000L;
    }

    private float newButtonWidth() {
        return Math.min(42.0F, w * 0.36F);
    }

    private float newButtonX() {
        return x + w - PAD - newButtonWidth();
    }

    private float maxScroll() {
        return Math.max(0, profiles.size() * ROW_H - listH);
    }

    private static boolean inside(float px, float py, float left, float top, float width, float height) {
        return px >= left && px < left + width && py >= top && py < top + height;
    }
}
