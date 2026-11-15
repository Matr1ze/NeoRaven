package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;
import java.util.Arrays;
import java.util.List;

/** Key assignment uses vanilla key codes, including mouse buttons and unbound Escape. */
public final class AndroidControlsScreen extends AndroidListScreen {
    private KeyBinding[] keys = new KeyBinding[0];
    private KeyBinding listening;
    public AndroidControlsScreen(GuiScreen parent) { super(parent); }
    @Override protected void onShown() { keys = mc.gameSettings.keyBindings.clone(); Arrays.sort(keys); listening = null; }
    @Override protected String title() { return I18n.format("controls.title"); }
    @Override protected String caption() {
        return listening == null ? text("Select a row · Click its key to change", "选择一行 · 点击右侧按键修改")
                : text("Press a key or mouse button · Esc to unbind", "按下键盘或鼠标按键 · Esc 解除绑定");
    }
    private static String text(String en, String zh) { return xyz.daikirai.util.Lang.chinese() ? zh : en; }
    @Override protected int rowCount() { return keys.length; }
    @Override protected float rowHeight() { return 48; }
    private boolean conflict(KeyBinding key) {
        if (key.getKeyCode() == 0) return false;
        for (KeyBinding other : keys) if (other != key && key.getKeyCode() == other.getKeyCode()) return true;
        return false;
    }
    @Override protected void drawRow(int index, float x, float y, float w, float h) {
        KeyBinding key = keys[index];
        ui.text(Typeface.BODY_MEDIUM, I18n.format(key.getKeyDescription()), x, y + 18, AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        ui.text(Typeface.CAPTION, I18n.format(key.getKeyCategory()), x, y + 35, AndroidWallpaper.softInk(), UI.ALIGN_LEFT);
        boolean conflict = conflict(key);
        ui.rounded(x + w - 132, y + 8, 132, 32, 16, conflict ? AndroidWallpaper.errorContainer() : AndroidWallpaper.primaryContainer());
        ui.text(Typeface.LABEL, listening == key ? "…" : GameSettings.getKeyDisplayString(key.getKeyCode()),
                x + w - 66, y + 24, conflict ? AndroidWallpaper.onErrorContainer() : AndroidWallpaper.onPrimaryContainer(), UI.ALIGN_CENTER);
    }
    @Override protected void activate(int index) { listening = keys[index]; }
    @Override protected void rowClicked(int index, boolean doubleClick) {
        if (doubleClick || mx >= cardX() + cardWidth() - 152) activate(index);
    }
    private void assign(int code) {
        listening.setKeyCode(code);
        listening = null;
        KeyBinding.resetKeyBindingArrayAndHash();
        mc.gameSettings.saveOptions();
    }
    @Override protected void keyTyped(char c, int key) {
        if (listening != null) { assign(key == Keyboard.KEY_ESCAPE ? 0 : key == 0 ? c + 256 : key); return; }
        super.keyTyped(c, key);
    }
    @Override protected void mouseClicked(int x, int y, int button) {
        if (listening != null) { assign(button - 100); return; }
        super.mouseClicked(x, y, button);
    }
    @Override public void onGuiClosed() { listening = null; mc.gameSettings.saveOptions(); }
    @Override protected void buildActions(List<Action> actions) {
        actions.add(new Action(I18n.format("controls.reset"), TONAL, () -> {
            keys[selected].setKeyCode(keys[selected].getKeyCodeDefault());
            KeyBinding.resetKeyBindingArrayAndHash(); mc.gameSettings.saveOptions();
        }, selected >= 0 && selected < keys.length && keys[selected].getKeyCode() != keys[selected].getKeyCodeDefault()));
        actions.add(new Action(I18n.format("controls.resetAll"), TONAL, () -> {
            for (KeyBinding key : keys) key.setKeyCode(key.getKeyCodeDefault());
            KeyBinding.resetKeyBindingArrayAndHash(); mc.gameSettings.saveOptions();
        }));
    }
}
