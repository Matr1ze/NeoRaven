package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/** Material dialog for adding or editing a multiplayer server entry. */
public final class AndroidServerScreen extends GuiScreen {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 360.0F;
    private static final float PAD = 14.0F;
    private static final float CARD_HEIGHT = 262.0F;

    private final AndroidMultiplayerScreen list;
    private final int index;
    private final UI ui = new UI();
    private final Spring opening = new Spring(0.0F);
    private final GuiTextField nameField;
    private final GuiTextField addressField;

    private float scale = 1.0F;
    private float ox;
    private float oy;
    private float mx;
    private float my;
    private float dt;
    private long lastFrame;
    private String error = "";

    private float cardX;
    private float cardY;
    private float cardWidth;

    /** Baseline of the label above field {@code index}; the box itself starts 12px lower. */
    private float fieldY(int index) {
        return cardY + 82.0F + index * 60.0F;
    }

    public AndroidServerScreen(AndroidMultiplayerScreen list, int index) {
        this.list = list;
        this.index = index;
        this.nameField = new GuiTextField(0, fontRendererObj, 0, 0, 200, 20);
        this.addressField = new GuiTextField(1, fontRendererObj, 0, 0, 200, 20);
        net.minecraft.client.multiplayer.ServerData existing = list == null ? null : list.entry(index);
        if (existing != null) {
            nameField.setText(existing.serverName);
            addressField.setText(existing.serverIP);
        }
        nameField.setFocused(true);
    }

    @Override
    public void initGui() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;
        cardWidth = 360.0F;
        cardX = (DESIGN_WIDTH - cardWidth) / 2.0F;
        cardY = 50.0F;
        lastFrame = 0L;
        opening.set(0.0F);
        opening.target(1.0F);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        opening.update(dt);
        mx = mouseX / scale - ox;
        my = mouseY / scale - oy;
        float enter = Material.clamp(opening.value(), 0.0F, 1.0F);

        ui.begin();
        MenuBackground.mouse(mouseX, mouseY, width, height);
        MenuBackground.draw(ui, width, height, true);
        ui.rect(0.0F, 0.0F, width, height, 0xB3000000);
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        ui.translate(0.0F, (1.0F - enter) * 20.0F);
        ui.alphaPush(Material.clamp(enter * 1.4F, 0.0F, 1.0F));

        float cardHeight = CARD_HEIGHT;
        ui.shadow(cardX, cardY, cardWidth, cardHeight, 28.0F, 4);
        ui.rounded(cardX, cardY, cardWidth, cardHeight, 28.0F, AndroidWallpaper.sheet());
        ui.textBaseline(Typeface.BODY_MEDIUM, index >= 0 ? "Edit server" : "Add server", cardX + 24.0F,
                cardY + 40.0F, AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        ui.textBaseline(Typeface.CAPTION, "Name and address of the server", cardX + 24.0F, cardY + 62.0F,
                AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        drawField("Server name", nameField, cardX + 24.0F, fieldY(0));
        drawField("Server address", addressField, cardX + 24.0F, fieldY(1));
        if (!error.isEmpty()) {
            ui.textBaseline(Typeface.CAPTION, error, cardX + 24.0F, cardY + 200.0F, AndroidWallpaper.errorInk(), UI.ALIGN_LEFT);
        }

        float bw = 84.0F;
        float by = cardY + CARD_HEIGHT - 30.0F - 18.0F;
        float bx = cardX + cardWidth - 18.0F - bw;
        boolean addHovered = point(mx, my, bx, by, bw, 30.0F);
        ui.rounded(bx, by, bw, 30.0F, 15.0F, addHovered ? Material.mix(AndroidWallpaper.primary(), AndroidWallpaper.card(), 0.12F)
                : AndroidWallpaper.primary());
        ui.text(Typeface.LABEL, index >= 0 ? "Save" : "Add", bx + bw / 2.0F, by + 15.0F,
                AndroidWallpaper.onPrimary(), UI.ALIGN_CENTER);
        ui.rounded(bx - 8.0F - bw, by, bw, 30.0F, 15.0F, AndroidWallpaper.surfaceHigh());
        ui.text(Typeface.LABEL, "Cancel", bx - 8.0F - bw / 2.0F, by + 15.0F, AndroidWallpaper.ink(),
                UI.ALIGN_CENTER);
        ui.alphaPop();
        ui.pop();
        ui.end();
    }

    private void drawField(String label, GuiTextField field, float x, float y) {
        ui.textBaseline(Typeface.CAPTION, label, x, y, AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        boolean focused = field.isFocused();
        ui.rounded(x, y + 12.0F, cardWidth - 48.0F, 36.0F, 12.0F,
                focused ? Material.mix(AndroidWallpaper.card(), AndroidWallpaper.primaryContainer(), 0.35F)
                        : AndroidWallpaper.surfaceHigh());
        ui.outline(x, y + 12.0F, cardWidth - 48.0F, 36.0F, 12.0F, focused ? 1.6F : 1.0F,
                focused ? AndroidWallpaper.primary() : AndroidWallpaper.outlineVariant());
        String text = field.getText();
        ui.scissorPush(x + 10.0F, y + 14.0F, cardWidth - 68.0F, 32.0F);
        ui.text(Typeface.BODY, text, x + 12.0F, y + 30.0F, AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caret = ui.width(Typeface.BODY, text);
            ui.rect(x + 12.0F + caret, y + 20.0F, 1.0F, 18.0F, AndroidWallpaper.primary());
        }
        ui.scissorPop();
    }

    private static boolean point(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        float x = mouseX / scale - ox;
        float y = mouseY / scale - oy;
        float bw = 84.0F;
        float by = cardY + CARD_HEIGHT - 48.0F;
        float bx = cardX + cardWidth - 18.0F - bw;
        if (point(x, y, bx, by, bw, 30.0F)) {
            submit();
            return;
        }
        if (point(x, y, bx - 8.0F - bw, by, bw, 30.0F)) {
            mc.displayGuiScreen(list);
            return;
        }
        nameField.setFocused(point(x, y, cardX + 24.0F, fieldY(0) + 12.0F, cardWidth - 48.0F, 36.0F));
        addressField.setFocused(point(x, y, cardX + 24.0F, fieldY(1) + 12.0F, cardWidth - 48.0F, 36.0F));
    }

    private void submit() {
        String address = addressField.getText().trim();
        if (address.isEmpty()) {
            error = "Enter a server address";
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = address;
        }
        if (list != null) {
            list.store(index, name, address);
        }
        mc.displayGuiScreen(list);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(list);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            submit();
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            boolean nameFocused = nameField.isFocused();
            nameField.setFocused(!nameFocused);
            addressField.setFocused(nameFocused);
            return;
        }
        if (nameField.isFocused()) {
            nameField.textboxKeyTyped(typedChar, keyCode);
        } else if (addressField.isFocused()) {
            addressField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        addressField.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
