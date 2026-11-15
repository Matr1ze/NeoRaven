package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.Language;
import net.minecraft.client.settings.GameSettings;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import java.util.ArrayList;
import java.util.List;

/** Native language names and selection, in the same list layout as the rest of setup. */
public final class AndroidLanguageScreen extends AndroidListScreen {
    private final List<Language> languages = new ArrayList<>();
    public AndroidLanguageScreen(GuiScreen parent) { super(parent); }
    @Override protected String title() { return I18n.format("options.language"); }
    @Override protected String caption() { return I18n.format("options.languageWarning"); }
    @Override protected int rowCount() { return languages.size(); }
    @Override protected float rowHeight() { return 48; }
    @Override protected void onShown() {
        languages.clear();
        languages.addAll(mc.getLanguageManager().getLanguages());
        for (int i = 0; i < languages.size(); i++) {
            if (languages.get(i).getLanguageCode().equals(mc.gameSettings.language)) {
                selected = i;
                scrollTarget = Math.max(0, Math.min(i * rowHeight(), languages.size() * rowHeight() + 8 - cardHeight()));
            }
        }
    }
    @Override protected void drawRow(int index, float x, float y, float w, float h) {
        Language language = languages.get(index);
        boolean active = language.getLanguageCode().equals(mc.gameSettings.language);
        ui.text(Typeface.BODY_MEDIUM, language.toString(), x, y + 18, AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        ui.text(Typeface.CAPTION, language.getLanguageCode(), x, y + 35, AndroidWallpaper.softInk(), UI.ALIGN_LEFT);
        ui.outline(x + w - 24, y + 14, 20, 20, 10, 1.5F, AndroidWallpaper.primary());
        if (active) ui.circle(x + w - 14, y + 24, 5, AndroidWallpaper.primary());
    }
    @Override protected void rowClicked(int index, boolean doubleClick) { activate(index); }
    @Override protected void activate(int index) {
        selected = index;
        ensureVisible(index);
        Language target = languages.get(index);
        if (target.getLanguageCode().equals(mc.gameSettings.language)) return;
        mc.getLanguageManager().setCurrentLanguage(target);
        mc.gameSettings.language = target.getLanguageCode();
        mc.refreshResources();
        mc.fontRendererObj.setUnicodeFlag(mc.getLanguageManager().isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
        mc.fontRendererObj.setBidiFlag(mc.getLanguageManager().isCurrentLanguageBidirectional());
        mc.gameSettings.saveOptions();
    }
    @Override protected void buildActions(List<Action> actions) {
        actions.add(new Action(mc.gameSettings.getKeyBinding(GameSettings.Options.FORCE_UNICODE_FONT), TONAL, () -> {
            mc.gameSettings.setOptionValue(GameSettings.Options.FORCE_UNICODE_FONT, 1);
            mc.gameSettings.saveOptions();
        }));
    }
}
