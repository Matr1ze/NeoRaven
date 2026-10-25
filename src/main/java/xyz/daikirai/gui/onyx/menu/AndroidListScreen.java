package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared chrome for the Android-style list screens (singleplayer worlds, multiplayer servers):
 * a page surface, a title block, one rounded list card with a scrollable stack of rows, a footer
 * row of buttons, and a Material confirm dialog.
 *
 * <p>The rows themselves are drawn by the subclass; everything around them (hit testing, hover,
 * selection, scrolling, the entrance spring) lives here so both screens behave identically.</p>
 */
public abstract class AndroidListScreen extends GuiScreen {

    protected static final float DESIGN_WIDTH = 640.0F;
    protected static final float DESIGN_HEIGHT = 360.0F;
    protected static final float PAD = 14.0F;

    /** Footer button styles. */
    protected static final int FILLED = 0;
    protected static final int TONAL = 1;
    protected static final int TEXT = 2;
    protected static final int DANGER = 3;

    protected final GuiScreen parent;
    protected final UI ui = new UI();
    private final Spring opening = new Spring(0.0F);
    private final Spring scrollSpring = new Spring(0.0F);
    private final Spring[] hover = new Spring[10];
    private final List<Action> actions = new ArrayList<Action>();

    protected float scale = 1.0F;
    protected float ox;
    protected float oy;
    protected float mx;
    protected float my;
    protected float dt;
    protected float scrollTarget;
    protected int selected = -1;
    protected boolean busy;

    private long lastFrame;
    private long lastClickAt;
    private int lastClickRow = -1;

    private float cardX;
    private float cardY;
    private float cardWidth;
    private float cardHeight;
    private float doneX;
    private float doneY;
    private float doneWidth;
    private float doneHeight;
    private float footerY;

    private String confirmTitle;
    private String confirmMessage;
    private String confirmLabel;
    private Runnable confirmAction;

    protected AndroidListScreen(GuiScreen parent) {
        this.parent = parent;
        for (int i = 0; i < hover.length; i++) {
            hover[i] = new Spring(0.0F);
        }
    }

    /** Footer button model. */
    protected static final class Action {
        final String label;
        final int style;
        final Runnable run;
        final boolean enabled;

        Action(String label, int style, Runnable run) {
            this(label, style, run, true);
        }

        Action(String label, int style, Runnable run, boolean enabled) {
            this.label = label;
            this.style = style;
            this.run = run;
            this.enabled = enabled;
        }
    }

    // ------------------------------------------------------------------ //
    // Subclass contract
    // ------------------------------------------------------------------ //

    protected abstract String title();

    /** Small line under the title, e.g. "4 worlds". */
    protected abstract String caption();

    protected abstract int rowCount();

    protected abstract float rowHeight();

    /** Draws one row's content; the base has already painted selection / hover. */
    protected abstract void drawRow(int index, float x, float y, float width, float height);

    /** Double click / Enter on a row. */
    protected abstract void activate(int index);

    /** Rebuilds the footer buttons; called every frame before drawing. */
    protected abstract void buildActions(List<Action> actions);

    /** Called when the screen is shown, after the layout exists. */
    protected void onShown() {
    }

    /** Centred message when there is nothing to list. */
    protected String emptyText() {
        return "";
    }

    /**
     * Leaves this screen without ever landing on a screen that needs a world that is no longer
     * there.
     *
     * <p>The parent is whatever was showing when the list was opened. That can be the disconnect
     * screen, another list, or the pause menu - and after a disconnect the pause menu is a trap:
     * its "Save and Quit" dereferences {@code mc.theWorld}, which is already null, so the client
     * crashes when the button is pressed. Anything that is not a plain title screen is therefore
     * replaced by the title screen once the world is gone.</p>
     */
    protected void close() {
        mc.displayGuiScreen(parentOrTitle());
    }

    /** The screen ESC / Done should return to. Never null, never world-dependent. */
    protected GuiScreen parentOrTitle() {
        GuiScreen target = parent;
        if (target == null || (AndroidScreens.needsWorld(target) && mc.theWorld == null)) {
            // In a world the pause menu is the safe way back; the title screen would strand the
            // client with a world still loaded.
            target = mc.theWorld != null
                    ? new net.minecraft.client.gui.GuiIngameMenu()
                    : new net.minecraft.client.gui.GuiMainMenu();
        }
        return target;
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void initGui() {
        layout();
        busy = false;
        selected = -1;
        scrollTarget = 0.0F;
        lastFrame = 0L;
        opening.set(0.0F);
        opening.target(1.0F);
        onShown();
    }

    private void layout() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;

        doneWidth = 68.0F;
        doneHeight = 30.0F;
        doneX = DESIGN_WIDTH - PAD - doneWidth;
        doneY = 10.0F;

        cardX = PAD;
        cardY = 62.0F;
        cardWidth = DESIGN_WIDTH - PAD * 2.0F;
        footerY = DESIGN_HEIGHT - PAD - 30.0F;
        cardHeight = footerY - 12.0F - cardY;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        opening.update(dt);
        scrollSpring.target(scrollTarget);
        scrollSpring.update(dt);
        mx = mouseX / scale - ox;
        my = mouseY / scale - oy;

        actions.clear();
        buildActions(actions);

        float enter = Material.clamp(opening.value(), 0.0F, 1.0F);
        ui.begin();
        MenuBackground.mouse(mouseX, mouseY, width, height);
        MenuBackground.draw(ui, width, height, true);
        ui.rect(0.0F, 0.0F, width, height, Material.withAlpha(AndroidWallpaper.page(), 0.94F));
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        ui.translate(0.0F, (1.0F - enter) * 24.0F);
        ui.alphaPush(Material.clamp(enter * 1.4F, 0.0F, 1.0F));
        drawHeader();
        drawCard();
        drawFooter();
        ui.alphaPop();
        ui.pop();
        ui.end();

        if (confirmTitle != null) {
            drawConfirm();
        }
    }

    private void drawHeader() {
        int ink = AndroidWallpaper.ink();
        ui.textBaseline(Typeface.HEADLINE_LARGE, title(), PAD, 30.0F, ink, UI.ALIGN_LEFT);
        ui.textBaseline(Typeface.CAPTION, caption(), PAD, 48.0F, AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
        boolean hovered = point(mx, my, doneX, doneY, doneWidth, doneHeight);
        ui.rounded(doneX, doneY, doneWidth, doneHeight, 15.0F,
                hovered ? AndroidWallpaper.primaryContainer()
                        : Material.mix(AndroidWallpaper.card(), AndroidWallpaper.primaryContainer(), 0.55F));
        ui.text(Typeface.LABEL, I18n.format("gui.done"), doneX + doneWidth / 2.0F, doneY + doneHeight / 2.0F,
                AndroidWallpaper.onPrimaryContainer(), UI.ALIGN_CENTER);
    }

    private void drawCard() {
        ui.shadow(cardX, cardY, cardWidth, cardHeight, 24.0F, 2);
        ui.rounded(cardX, cardY, cardWidth, cardHeight, 24.0F, AndroidWallpaper.card());
        ui.scissorPush(cardX, cardY, cardWidth, cardHeight);
        float height = rowHeight();
        float y = cardY + 4.0F - scrollSpring.value();
        for (int i = 0; i < rowCount(); i++) {
            if (y + height > cardY && y < cardY + cardHeight) {
                int style = i % hover.length;
                boolean rowHovered = rowHit(i, mx, my);
                hover[style].target(rowHovered ? 1.0F : 0.0F);
                hover[style].update(dt);
                if (i == selected) {
                    ui.rounded(cardX + 4.0F, y + 2.0F, cardWidth - 8.0F, height - 4.0F, 18.0F,
                            AndroidWallpaper.primaryContainer());
                } else if (hover[style].value() > 0.01F) {
                    ui.rounded(cardX + 4.0F, y + 2.0F, cardWidth - 8.0F, height - 4.0F, 18.0F,
                            Material.withAlpha(AndroidWallpaper.ink(), 0.045F * hover[style].value()));
                }
                drawRow(i, cardX + 20.0F, y, cardWidth - 40.0F, height);
            }
            y += height;
        }
        ui.scissorPop();
        if (rowCount() == 0 && !emptyText().isEmpty()) {
            ui.icon("\ue5cc", cardX + cardWidth / 2.0F, cardY + cardHeight / 2.0F - 16.0F, 26.0F,
                    AndroidWallpaper.outlineVariant());
            ui.text(Typeface.BODY, emptyText(), cardX + cardWidth / 2.0F, cardY + cardHeight / 2.0F + 14.0F,
                    AndroidWallpaper.faintInk(), UI.ALIGN_CENTER);
        }
        drawScrollbar();
    }

    private void drawScrollbar() {
        float content = rowCount() * rowHeight() + 8.0F;
        if (content <= cardHeight) {
            return;
        }
        float track = cardHeight - 24.0F;
        float thumb = Math.max(24.0F, track * cardHeight / content);
        float progress = Material.clamp(scrollSpring.value() / (content - cardHeight), 0.0F, 1.0F);
        ui.rounded(cardX + cardWidth - 7.0F, cardY + 12.0F + (track - thumb) * progress, 3.0F, thumb, 1.5F,
                Material.withAlpha(AndroidWallpaper.ink(), 0.18F));
    }

    private void drawFooter() {
        float right = DESIGN_WIDTH - PAD;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action action = actions.get(i);
            float width = Math.max(64.0F, Typeface.LABEL.width(action.label) + 32.0F);
            float x = right - width;
            boolean hovered = action.enabled && point(mx, my, x, footerY, width, 30.0F);
            drawButton(action, x, footerY, width, 30.0F, hovered);
            right = x - 8.0F;
        }
    }

    private void drawButton(Action action, float x, float y, float w, float h, boolean hovered) {
        int ink = AndroidWallpaper.ink();
        if (!action.enabled) {
            ui.alphaPush(0.38F);
        }
        switch (action.style) {
            case FILLED:
                ui.shadow(x, y, w, h, 15.0F, hovered ? 2 : 1);
                ui.rounded(x, y, w, h, 15.0F, hovered ? Material.mix(AndroidWallpaper.primary(), AndroidWallpaper.card(), 0.12F)
                        : AndroidWallpaper.primary());
                ui.text(Typeface.LABEL, action.label, x + w / 2.0F, y + h / 2.0F, AndroidWallpaper.onPrimary(),
                        UI.ALIGN_CENTER);
                break;
            case DANGER:
                ui.rounded(x, y, w, h, 15.0F, hovered ? Material.mix(AndroidWallpaper.errorContainer(), AndroidWallpaper.errorInk(), 0.10F) : AndroidWallpaper.errorContainer());
                ui.text(Typeface.LABEL, action.label, x + w / 2.0F, y + h / 2.0F, AndroidWallpaper.onErrorContainer(), UI.ALIGN_CENTER);
                break;
            case TEXT:
                if (hovered) {
                    ui.rounded(x, y, w, h, 15.0F, Material.withAlpha(AndroidWallpaper.primary(), 0.08F));
                }
                ui.text(Typeface.LABEL, action.label, x + w / 2.0F, y + h / 2.0F, AndroidWallpaper.primary(),
                        UI.ALIGN_CENTER);
                break;
            default:
                ui.rounded(x, y, w, h, 15.0F, hovered ? Material.mix(AndroidWallpaper.primaryContainer(), ink, 0.06F)
                        : AndroidWallpaper.primaryContainer());
                ui.text(Typeface.LABEL, action.label, x + w / 2.0F, y + h / 2.0F,
                        AndroidWallpaper.onPrimaryContainer(), UI.ALIGN_CENTER);
                break;
        }
        if (!action.enabled) {
            ui.alphaPop();
        }
    }

    // ------------------------------------------------------------------ //
    // Confirm dialog
    // ------------------------------------------------------------------ //

    protected void confirm(String title, String message, String label, Runnable action) {
        confirmTitle = title;
        confirmMessage = message;
        confirmLabel = label;
        confirmAction = action;
    }

    private float[] confirmBounds() {
        float w = 320.0F;
        float h = 156.0F;
        return new float[]{(DESIGN_WIDTH - w) / 2.0F, (DESIGN_HEIGHT - h) / 2.0F, w, h};
    }

    private void drawConfirm() {
        ui.begin();
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        ui.rect(-ox, -oy, DESIGN_WIDTH + ox * 2.0F, DESIGN_HEIGHT + oy * 2.0F, 0x99000000);
        float[] box = confirmBounds();
        ui.shadow(box[0], box[1], box[2], box[3], 28.0F, 4);
        ui.rounded(box[0], box[1], box[2], box[3], 28.0F, AndroidWallpaper.sheet());
        ui.textBaseline(Typeface.BODY_MEDIUM, confirmTitle, box[0] + 24.0F, box[1] + 40.0F,
                AndroidWallpaper.ink(), UI.ALIGN_LEFT);
        java.util.List<String> lines = ui.wrap(Typeface.BODY, confirmMessage, box[2] - 48.0F, 2);
        for (int i = 0; i < lines.size(); i++) {
            ui.textBaseline(Typeface.BODY, lines.get(i), box[0] + 24.0F, box[1] + 64.0F + i * 18.0F,
                    AndroidWallpaper.softInk(), UI.ALIGN_LEFT);
        }
        float cancelW = 76.0F;
        float confirmW = 92.0F;
        float y = box[1] + box[3] - 30.0F - 20.0F;
        float x = box[0] + box[2] - 20.0F - confirmW;
        ui.rounded(x, y, confirmW, 30.0F, 15.0F, AndroidWallpaper.errorContainer());
        ui.text(Typeface.LABEL, confirmLabel, x + confirmW / 2.0F, y + 15.0F, AndroidWallpaper.onErrorContainer(), UI.ALIGN_CENTER);
        ui.rounded(x - 8.0F - cancelW, y, cancelW, 30.0F, 15.0F, AndroidWallpaper.surfaceHigh());
        ui.text(Typeface.LABEL, I18n.format("gui.cancel"), x - 8.0F - cancelW / 2.0F, y + 15.0F,
                AndroidWallpaper.ink(), UI.ALIGN_CENTER);
        ui.pop();
        ui.end();
    }

    // ------------------------------------------------------------------ //
    // Hit testing
    // ------------------------------------------------------------------ //

    protected static boolean point(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && y >= ry && x < rx + rw && y < ry + rh;
    }

    protected float cardX() {
        return cardX;
    }

    protected float cardY() {
        return cardY;
    }

    protected float cardWidth() {
        return cardWidth;
    }

    protected float cardHeight() {
        return cardHeight;
    }

    protected float scroll() {
        return scrollSpring.value();
    }

    private int rowAt(float x, float y) {
        if (!point(x, y, cardX, cardY, cardWidth, cardHeight)) {
            return -1;
        }
        float height = rowHeight();
        float top = cardY + 4.0F - scrollSpring.value();
        int index = (int) ((y - top) / height);
        if (index < 0 || index >= rowCount()) {
            return -1;
        }
        float rowTop = top + index * height;
        return y >= rowTop && y < rowTop + height ? index : -1;
    }

    private boolean rowHit(int index, float x, float y) {
        return rowAt(x, y) == index;
    }

    private int actionAt(float x, float y) {
        if (confirmTitle != null) {
            return -1;
        }
        float right = DESIGN_WIDTH - PAD;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action action = actions.get(i);
            float width = Math.max(64.0F, Typeface.LABEL.width(action.label) + 32.0F);
            float left = right - width;
            if (action.enabled && point(x, y, left, footerY, width, 30.0F)) {
                return i;
            }
            right = left - 8.0F;
        }
        return -1;
    }

    // ------------------------------------------------------------------ //
    // Input
    // ------------------------------------------------------------------ //

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0 || busy) {
            return;
        }
        float x = mouseX / scale - ox;
        float y = mouseY / scale - oy;

        if (confirmTitle != null) {
            float[] box = confirmBounds();
            float cancelW = 76.0F;
            float confirmW = 92.0F;
            float rowY = box[1] + box[3] - 50.0F;
            float confirmX = box[0] + box[2] - 20.0F - confirmW;
            if (point(x, y, confirmX, rowY, confirmW, 30.0F)) {
                Runnable action = confirmAction;
                dismissConfirm();
                if (action != null) {
                    click();
                    action.run();
                }
                return;
            }
            if (point(x, y, confirmX - 8.0F - cancelW, rowY, cancelW, 30.0F) || !point(x, y, box[0], box[1], box[2], box[3])) {
                dismissConfirm();
            }
            return;
        }

        if (point(x, y, doneX, doneY, doneWidth, doneHeight)) {
            click();
            close();
            return;
        }
        int action = actionAt(x, y);
        if (action >= 0) {
            click();
            actions.get(action).run.run();
            return;
        }
        int row = rowAt(x, y);
        if (row >= 0) {
            long now = System.currentTimeMillis();
            boolean again = row == lastClickRow && now - lastClickAt < 420L;
            lastClickRow = row;
            lastClickAt = now;
            selected = row;
            click();
            if (again) {
                activate(row);
            }
        }
    }

    private void dismissConfirm() {
        confirmTitle = null;
        confirmMessage = null;
        confirmLabel = null;
        confirmAction = null;
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        int wheel = Mouse.getEventDWheel();
        super.handleMouseInput();
        if (wheel != 0 && confirmTitle == null) {
            scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget - wheel * 0.6F));
        }
    }

    private float maxScroll() {
        return Math.max(0.0F, rowCount() * rowHeight() + 8.0F - cardHeight);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (confirmTitle != null) {
                dismissConfirm();
            } else {
                close();
            }
            return;
        }
        if (confirmTitle != null) {
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN && selected >= 0 && selected < rowCount()) {
            activate(selected);
            return;
        }
        if (keyCode == Keyboard.KEY_DOWN) {
            selected = Math.min(rowCount() - 1, Math.max(0, selected + 1));
            ensureVisible(selected);
        } else if (keyCode == Keyboard.KEY_UP) {
            selected = Math.max(0, selected - 1);
            ensureVisible(selected);
        }
    }

    private void ensureVisible(int index) {
        if (index < 0) {
            return;
        }
        float top = index * rowHeight();
        float bottom = top + rowHeight();
        if (top < scrollTarget) {
            scrollTarget = top;
        } else if (bottom > scrollTarget + cardHeight - 8.0F) {
            scrollTarget = bottom - cardHeight + 8.0F;
        }
        scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget));
    }

    protected void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    protected static Minecraft minecraft() {
        return Minecraft.getMinecraft();
    }

    // ------------------------------------------------------------------ //
    // Harness hooks
    // ------------------------------------------------------------------ //

    /** Design-space centre of a row, for the automated harness. */
    public float[] rowCenter(int index) {
        return new float[]{cardX + 60.0F, cardY + 4.0F - scrollSpring.value() + index * rowHeight() + rowHeight() / 2.0F};
    }

    /** Design-space centre of a footer button, for the automated harness. */
    public float[] actionCenter(String label) {
        float right = DESIGN_WIDTH - PAD;
        for (int i = actions.size() - 1; i >= 0; i--) {
            Action action = actions.get(i);
            float width = Math.max(64.0F, Typeface.LABEL.width(action.label) + 32.0F);
            float left = right - width;
            if (action.label.equals(label)) {
                return new float[]{left + width / 2.0F, footerY + 15.0F};
            }
            right = left - 8.0F;
        }
        return null;
    }

    public int rowCountPublic() {
        return rowCount();
    }

    public int selectedRow() {
        return selected;
    }

    public boolean dialogOpen() {
        return confirmTitle != null;
    }

    public float[] confirmButtonCenter() {
        float[] box = confirmBounds();
        float confirmW = 92.0F;
        return new float[]{box[0] + box[2] - 20.0F - confirmW / 2.0F, box[1] + box[3] - 50.0F + 15.0F};
    }
}
