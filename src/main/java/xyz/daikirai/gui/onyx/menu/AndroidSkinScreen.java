package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EnumPlayerModelParts;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import java.util.List;

public final class AndroidSkinScreen extends AndroidListScreen {
    private final EnumPlayerModelParts[] parts = EnumPlayerModelParts.values();
    public AndroidSkinScreen(GuiScreen parent) { super(parent); }
    @Override protected String title() { return I18n.format("options.skinCustomisation"); }
    @Override protected String caption() { return xyz.daikirai.util.Lang.text("Cape, jacket, sleeves"); }
    @Override protected int rowCount() { return parts.length; }
    @Override protected float rowHeight() { return 48; }
    @Override protected void drawRow(int index, float x, float y, float w, float h) {
        boolean enabled = mc.gameSettings.getModelParts().contains(parts[index]);
        ui.text(Typeface.BODY_MEDIUM, parts[index].func_179326_d().getFormattedText(), x, y + h / 2,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        int track = enabled ? AndroidWallpaper.primary() : AndroidWallpaper.neutralContainer();
        ui.rounded(x + w - 44, y + 12, 44, 24, 12, track);
        ui.circle(x + w - (enabled ? 12 : 32), y + 24, 8,
                enabled ? AndroidWallpaper.onPrimary() : AndroidWallpaper.softInk());
    }
    @Override protected void rowClicked(int index, boolean doubleClick) { activate(index); }
    @Override protected void activate(int index) { mc.gameSettings.switchModelPartEnabled(parts[index]); mc.gameSettings.saveOptions(); }
    @Override protected void buildActions(List<Action> actions) { }
}
