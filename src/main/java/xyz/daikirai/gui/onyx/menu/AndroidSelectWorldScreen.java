package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiCreateWorld;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.storage.SaveFormatComparator;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Android-style singleplayer screen: the saved worlds as a Material list of cards. */
public final class AndroidSelectWorldScreen extends AndroidListScreen {

    private final List<SaveFormatComparator> worlds = new ArrayList<SaveFormatComparator>();
    private String loadError;

    public AndroidSelectWorldScreen(GuiScreen parent) {
        super(parent);
    }

    @Override
    protected void onShown() {
        reload();
    }

    private void reload() {
        worlds.clear();
        try {
            worlds.addAll(mc.getSaveLoader().getSaveList());
            loadError = null;
        } catch (Exception error) {
            loadError = error.getMessage() == null ? "Could not read the world list" : error.getMessage();
        }
        selected = -1;
        scrollTarget = 0.0F;
    }

    @Override
    protected String title() {
        return "Singleplayer";
    }

    @Override
    protected String caption() {
        if (loadError != null) {
            return loadError;
        }
        return worlds.size() + (worlds.size() == 1 ? " world" : " worlds");
    }

    @Override
    protected int rowCount() {
        return worlds.size();
    }

    @Override
    protected String emptyText() {
        return "No worlds yet - create one to start";
    }

    @Override
    protected float rowHeight() {
        return 68.0F;
    }

    @Override
    protected void drawRow(int index, float x, float y, float width, float height) {
        SaveFormatComparator world = worlds.get(index);
        boolean active = index == selected;
        float cy = y + height / 2.0F;
        ui.circle(x + 24.0F, cy, 19.0F,
                active ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.14F)
                        : AndroidWallpaper.surfaceHigh());
        ui.icon("\ue80b", x + 24.0F, cy, 19.0F,
                active ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.onSurfaceVariant());
        ui.text(Typeface.BODY_MEDIUM, world.getDisplayName(), x + 56.0F, y + 24.0F,
                active ? AndroidWallpaper.onPrimaryContainer() : AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        ui.text(Typeface.CAPTION, meta(world), x + 56.0F, y + 42.0F,
                active ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.7F)
                        : AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        ui.icon("\ue5cc", x + width - 6.0F, cy, 16.0F,
                active ? Material.withAlpha(AndroidWallpaper.onPrimaryContainer(), 0.6F)
                        : AndroidWallpaper.outlineVariant());
    }

    private String meta(SaveFormatComparator world) {
        StringBuilder text = new StringBuilder(world.getFileName());
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        text.append("  \u00b7  ").append(format.format(new Date(world.getLastTimePlayed())));
        String mode = world.getEnumGameType().getName();
        if (!mode.isEmpty()) {
            text.append("  \u00b7  ").append(Character.toUpperCase(mode.charAt(0))).append(mode.substring(1));
        }
        if (world.isHardcoreModeEnabled()) {
            text.append("  \u00b7  Hardcore");
        }
        if (world.getCheatsEnabled()) {
            text.append("  \u00b7  Cheats");
        }
        return ui.truncate(Typeface.CAPTION, text.toString(), cardWidth() - 130.0F);
    }

    @Override
    protected void activate(int index) {
        play(index);
    }

    private void play(int index) {
        if (index < 0 || index >= worlds.size() || busy) {
            return;
        }
        SaveFormatComparator world = worlds.get(index);
        busy = true;
        mc.displayGuiScreen(null);
        mc.setIngameFocus();
        mc.launchIntegratedServer(world.getFileName(), world.getDisplayName(), (WorldSettings) null);
    }

    @Override
    protected void buildActions(List<Action> actions) {
        actions.add(new Action("Create New World", FILLED, () -> mc.displayGuiScreen(new GuiCreateWorld(this))));
        actions.add(new Action("Re-Create", TEXT, this::recreate, selected >= 0));
        actions.add(new Action("Delete", DANGER, this::askDelete, selected >= 0));
        actions.add(new Action("Play", TONAL, () -> play(selected), selected >= 0));
    }

    private void recreate() {
        if (selected < 0 || selected >= worlds.size()) {
            return;
        }
        SaveFormatComparator world = worlds.get(selected);
        GuiCreateWorld screen = new GuiCreateWorld(this);
        if (mc.getSaveLoader().getWorldInfo(world.getFileName()) != null) {
            screen.recreateFromExistingWorld(mc.getSaveLoader().getWorldInfo(world.getFileName()));
        }
        mc.displayGuiScreen(screen);
    }

    private void askDelete() {
        if (selected < 0 || selected >= worlds.size()) {
            return;
        }
        final SaveFormatComparator world = worlds.get(selected);
        confirm("Delete world?", "\"" + world.getDisplayName() + "\" and its folder will be deleted permanently.",
                "Delete", () -> delete(world));
    }

    private void delete(SaveFormatComparator world) {
        mc.getSaveLoader().deleteWorldDirectory(world.getFileName());
        int keep = selected;
        reload();
        if (keep < worlds.size()) {
            selected = Math.max(0, keep);
        }
    }
}
