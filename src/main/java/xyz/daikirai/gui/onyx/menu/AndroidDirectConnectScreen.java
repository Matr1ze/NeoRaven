package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * Material dialog for joining a server without saving it: one address field, Connect and Cancel.
 *
 * <p>Minecraft's own "Direct Connect" is the same one-off join - the address goes straight into a
 * {@link GuiConnecting} and never touches {@code servers.dat}, so a quick hop to a friend's IP does
 * not leave a permanent entry in the list. A missing port is left to
 * {@code ServerAddress.fromString}, which falls back to 25565 exactly like the vanilla field.</p>
 */
public final class AndroidDirectConnectScreen extends GuiScreen {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 360.0F;
    private static final float CARD_HEIGHT = 196.0F;

    private final GuiScreen parent;
    private final UI ui = new UI();
    private final Spring opening = new Spring(0.0F);
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

    /**
     * @param parent  screen Cancel / Esc returns to (the server list)
     * @param initial address to prefill, e.g. the selected server's IP; may be empty
     */
    public AndroidDirectConnectScreen(GuiScreen parent, String initial) {
        this.parent = parent;
        this.addressField = new GuiTextField(0, fontRendererObj, 0, 0, 220, 20);
        if (initial != null && !initial.trim().isEmpty()) {
            addressField.setText(initial.trim());
        }
        addressField.setFocused(true);
    }

    @Override
    public void initGui() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;
        cardWidth = 360.0F;
        cardX = (DESIGN_WIDTH - cardWidth) / 2.0F;
        cardY = (DESIGN_HEIGHT - CARD_HEIGHT) / 2.0F + 6.0F;
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

        ui.shadow(cardX, cardY, cardWidth, CARD_HEIGHT, 28.0F, 4);
        ui.rounded(cardX, cardY, cardWidth, CARD_HEIGHT, 28.0F, AndroidWallpaper.sheet());
        ui.textBaseline(Typeface.BODY_MEDIUM, "Direct connect", cardX + 24.0F, cardY + 40.0F,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        ui.textBaseline(Typeface.CAPTION, "Server address, host or host:port", cardX + 24.0F, cardY + 62.0F,
                AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        drawField();

        float bw = 84.0F;
        float by = cardY + CARD_HEIGHT - 30.0F - 18.0F;
        float bx = cardX + cardWidth - 18.0F - bw;
        boolean connectHovered = point(mx, my, bx, by, bw, 30.0F);
        ui.rounded(bx, by, bw, 30.0F, 15.0F, connectHovered
                ? Material.mix(AndroidWallpaper.primary(), AndroidWallpaper.card(), 0.12F)
                : AndroidWallpaper.primary());
        ui.text(Typeface.LABEL, "Connect", bx + bw / 2.0F, by + 15.0F, AndroidWallpaper.onPrimary(),
                UI.ALIGN_CENTER);
        ui.rounded(bx - 8.0F - bw, by, bw, 30.0F, 15.0F, AndroidWallpaper.surfaceHigh());
        ui.text(Typeface.LABEL, "Cancel", bx - 8.0F - bw / 2.0F, by + 15.0F, AndroidWallpaper.ink(),
                UI.ALIGN_CENTER);
        ui.alphaPop();
        ui.pop();
        ui.end();
    }

    private void drawField() {
        float x = cardX + 24.0F;
        float y = cardY + 82.0F;
        boolean focused = addressField.isFocused();
        ui.rounded(x, y + 12.0F, cardWidth - 48.0F, 36.0F, 12.0F,
                focused ? Material.mix(AndroidWallpaper.card(), AndroidWallpaper.primaryContainer(), 0.35F)
                        : AndroidWallpaper.surfaceHigh());
        ui.outline(x, y + 12.0F, cardWidth - 48.0F, 36.0F, 12.0F, focused ? 1.6F : 1.0F,
                focused ? AndroidWallpaper.primary() : AndroidWallpaper.outlineVariant());
        String text = addressField.getText();
        ui.scissorPush(x + 10.0F, y + 14.0F, cardWidth - 68.0F, 32.0F);
        ui.text(Typeface.BODY, text, x + 12.0F, y + 30.0F, AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caret = ui.width(Typeface.BODY, text);
            ui.rect(x + 12.0F + caret, y + 20.0F, 1.0F, 18.0F, AndroidWallpaper.primary());
        }
        ui.scissorPop();
        if (!error.isEmpty()) {
            ui.textBaseline(Typeface.CAPTION, error, cardX + 24.0F, cardY + 148.0F,
                    AndroidWallpaper.errorInk(), UI.ALIGN_LEFT);
        }
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
            connect();
            return;
        }
        if (point(x, y, bx - 8.0F - bw, by, bw, 30.0F)) {
            back();
            return;
        }
        addressField.setFocused(point(x, y, cardX + 24.0F, cardY + 94.0F, cardWidth - 48.0F, 36.0F));
    }

    private void connect() {
        String address = addressField.getText().trim();
        if (address.isEmpty()) {
            error = "Enter a server address";
            return;
        }
        if (address.indexOf(' ') >= 0) {
            error = "Server addresses cannot contain spaces";
            return;
        }
        ServerData data = new ServerData("Direct Connect", address, false);
        mc.displayGuiScreen(new GuiConnecting(parent, mc, data));
    }

    private void back() {
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            back();
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN) {
            connect();
            return;
        }
        addressField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        addressField.updateCursorCounter();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
