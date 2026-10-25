package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.GuiModList;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.OnyxClickGui;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * AOSP / Pixel style title screen: a Material You wallpaper, an app dock with four MD3 themed
 * icons, and nothing else - no title text, no buttons, no status bar.
 *
 * <p>The layout is authored in a fixed 640x360 design space that is scaled to fit the window, the
 * same trick the Onyx panels use, so proportions survive every resolution. Every icon is a tonal
 * container (an MD3 role derived from the ClickGUI accent) with its glyph in the matching
 * on-container colour, which is what makes the dock read as themed icons rather than brand art.</p>
 */
public final class AndroidMainMenu extends GuiMainMenu {

    private static final float DESIGN_WIDTH = 640.0F;
    private static final float DESIGN_HEIGHT = 360.0F;

    private static final String ICON_PLAY = "\ue037";
    private static final String ICON_GROUP = "\ue7ef";
    private static final String ICON_ACCOUNT = "\ue853";
    private static final String ICON_SETTINGS = "\ue8b8";
    private static final String ICON_CHEVRON = "\ue5cc";

    private static final String[] DOCK_ICONS = {ICON_PLAY, ICON_GROUP, ICON_ACCOUNT, ICON_SETTINGS};
    private static final String[] DOCK_LABELS = {"Singleplayer", "Multiplayer", "Alt Manager", "Settings"};

    private static final String[] SHEET_LABELS = {"Options", "Mods", "Proxy", "Background", "Quit Game"};
    private static final String[] SHEET_HINTS = {"Video, controls and language", "Forge mod list",
            "SOCKS5 and HTTP tunnel", "Pick the menu wallpaper", "Close the client"};

    private final UI ui = new UI();
    private final Spring[] dockHover = new Spring[DOCK_ICONS.length];
    private final Spring[] rowHover = new Spring[SHEET_LABELS.length];

    private float scale = 1.0F;
    private float ox;
    private float oy;
    private float mx;
    private float my;
    private float dt;
    private long lastFrame;
    private long shownAt;

    private float dockX;
    private float dockY;
    private float dockWidth;
    private float dockHeight;
    private float dockCell;
    private float iconSize;

    private boolean sheetOpen;
    private final Anim sheet = new Anim(0.0F);
    private float sheetX;
    private float sheetY;
    private float sheetWidth;
    private float sheetHeight;
    private float sheetHeader;
    private float rowHeight;

    public AndroidMainMenu() {
        for (int i = 0; i < dockHover.length; i++) {
            dockHover[i] = new Spring(0.0F);
        }
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0F);
        }
    }

    // ------------------------------------------------------------------ //
    // Layout
    // ------------------------------------------------------------------ //

    @Override
    public void initGui() {
        OnyxClickGuiModule.sync();
        MenuStyleSwitch.reset();
        sheetOpen = false;
        sheet.set(0.0F);
        lastFrame = 0L;
        shownAt = System.currentTimeMillis();
        layout();
    }

    private void layout() {
        float fit = Math.max(0.05F, Math.min(width / DESIGN_WIDTH, height / DESIGN_HEIGHT));
        scale = Math.min(Theme.screenScale(fit), 2.6F);
        ox = (width / scale - DESIGN_WIDTH) / 2.0F;
        oy = (height / scale - DESIGN_HEIGHT) / 2.0F;

        dockWidth = 296.0F;
        dockHeight = 62.0F;
        dockX = (DESIGN_WIDTH - dockWidth) / 2.0F;
        dockY = DESIGN_HEIGHT - 26.0F - dockHeight;
        dockCell = dockWidth / DOCK_ICONS.length;
        iconSize = 34.0F;

        rowHeight = 54.0F;
        sheetWidth = 340.0F;
        sheetHeader = 44.0F;
        sheetHeight = sheetHeader + rowHeight * SHEET_LABELS.length + 8.0F;
        sheetX = (DESIGN_WIDTH - sheetWidth) / 2.0F;
        sheetY = DESIGN_HEIGHT - 16.0F - sheetHeight;
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        OnyxClickGuiModule.sync();
        layout();
        long now = System.nanoTime();
        dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        sheet.to(sheetOpen ? 1.0F : 0.0F, 280.0F, Ease.EMPHASIZED_DECELERATE);
        sheet.update(dt);

        mx = mouseX / scale - ox;
        my = mouseY / scale - oy;

        ui.begin();
        MenuBackground.mouse(mouseX, mouseY, width, height);
        MenuBackground.draw(ui, width, height, true);
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);
        drawDock();
        ui.pop();
        drawSheet();
        if (!sheetOpen) {
            drawThemeToggle(mouseX, mouseY);
            MenuStyleSwitch.draw(ui, true, width, height, mouseX, mouseY, dt);
        }
        ui.end();
    }

    /** Dock: a white pill with four themed icons that rise into place one after another. */
    private void drawDock() {
        float age = System.currentTimeMillis() - shownAt;
        float enter = Ease.EMPHASIZED.get(Material.clamp(age / 420.0F, 0.0F, 1.0F));
        float dim = 1.0F - 0.45F * sheet.value();

        ui.alphaPush(enter * dim);
        ui.translate(0.0F, (1.0F - enter) * 26.0F);
        ui.shadow(dockX, dockY, dockWidth, dockHeight, dockHeight / 2.0F, 3);
        ui.rounded(dockX, dockY, dockWidth, dockHeight, dockHeight / 2.0F, AndroidWallpaper.dock());

        int hoveredIndex = -1;
        float strongest = 0.15F;
        for (int i = 0; i < DOCK_ICONS.length; i++) {
            float step = Ease.EMPHASIZED.get(Material.clamp((age - i * 55.0F) / 420.0F, 0.0F, 1.0F));
            float cx = dockX + dockCell * (i + 0.5F);
            float cy = dockY + dockHeight / 2.0F;
            boolean hovered = dockHit(i, mx, my) && !sheetOpen;
            dockHover[i].target(hovered ? 1.0F : 0.0F);
            dockHover[i].update(dt);
            if (dockHover[i].value() > strongest) {
                strongest = dockHover[i].value();
                hoveredIndex = i;
            }
            float d = iconSize * (0.55F + 0.45F * step) * (1.0F + 0.06F * dockHover[i].value());
            ui.alphaPush(step);
            ui.circle(cx, cy, d / 2.0F, container(i));
            ui.icon(DOCK_ICONS[i], cx, cy, d * 0.5F, glyph(i));
            ui.alphaPop();
        }
        ui.alphaPop();

        if (hoveredIndex >= 0 && !sheetOpen) {
            String label = DOCK_LABELS[hoveredIndex];
            float w = Typeface.LABEL.width(label) + 24.0F;
            float h = 24.0F;
            float cx = dockX + dockCell * (hoveredIndex + 0.5F);
            float x = Math.max(10.0F, Math.min(cx - w / 2.0F, DESIGN_WIDTH - 10.0F - w));
            float y = dockY - 12.0F - h;
            ui.alphaPush(strongest * Math.min(1.0F, age / 200.0F));
            ui.rounded(x, y, w, h, h / 2.0F, AndroidWallpaper.tooltip());
            ui.text(Typeface.LABEL, label, x + w / 2.0F, y + h / 2.0F, AndroidWallpaper.tooltipInk(), UI.ALIGN_CENTER);
            ui.alphaPop();
        }
    }

    private static int container(int index) {
        switch (index) {
            case 0:
                return AndroidWallpaper.primaryContainer();
            case 1:
                return AndroidWallpaper.secondaryContainer();
            case 2:
                return AndroidWallpaper.tertiaryContainer();
            default:
                return AndroidWallpaper.neutralContainer();
        }
    }

    private static int glyph(int index) {
        switch (index) {
            case 0:
                return AndroidWallpaper.onPrimaryContainer();
            case 1:
                return AndroidWallpaper.onSecondaryContainer();
            case 2:
                return AndroidWallpaper.onTertiaryContainer();
            default:
                return AndroidWallpaper.onNeutralContainer();
        }
    }

    /** Material bottom sheet with the actions that used to sit around the dock. */
    private void drawSheet() {
        float e = Material.clamp(sheet.value(), 0.0F, 1.0F);
        if (e <= 0.001F) {
            return;
        }
        ui.push();
        ui.scale(scale, 0.0F, 0.0F);
        ui.translate(ox, oy);

        ui.rect(-ox, -oy, DESIGN_WIDTH + ox * 2.0F, DESIGN_HEIGHT + oy * 2.0F,
                Material.withAlpha(0xFF000000, 0.30F * e));

        float slide = (1.0F - e) * 40.0F;
        ui.alphaPush(e);
        ui.shadow(sheetX, sheetY + slide, sheetWidth, sheetHeight, 28.0F, 4);
        ui.rounded(sheetX, sheetY + slide, sheetWidth, sheetHeight, 28.0F, AndroidWallpaper.sheet());
        float handleY = sheetY + slide + 14.0F;
        ui.rounded(DESIGN_WIDTH / 2.0F - 16.0F, handleY, 32.0F, 4.0F, 2.0F,
                Material.withAlpha(AndroidWallpaper.ink(), 0.22F));
        ui.textBaseline(Typeface.BODY_MEDIUM, "Settings", sheetX + 24.0F,
                sheetY + slide + 36.0F, AndroidWallpaper.softInk(), UI.ALIGN_LEFT);
        for (int i = 0; i < SHEET_LABELS.length; i++) {
            float y = sheetY + slide + sheetHeader + i * rowHeight;
            boolean hovered = rowHit(i, mx, my) && sheetOpen;
            rowHover[i].target(hovered ? 1.0F : 0.0F);
            rowHover[i].update(dt);
            if (rowHover[i].value() > 0.01F) {
                ui.rounded(sheetX + 8.0F, y, sheetWidth - 16.0F, rowHeight, 16.0F,
                        Material.withAlpha(AndroidWallpaper.ink(), 0.06F * rowHover[i].value()));
            }
            boolean quit = i == SHEET_LABELS.length - 1;
            int color = quit ? AndroidWallpaper.errorInk() : AndroidWallpaper.ink();
            ui.textBaseline(Typeface.BODY_MEDIUM, SHEET_LABELS[i], sheetX + 24.0F, y + 24.0F, color,
                    UI.ALIGN_LEFT);
            ui.textBaseline(Typeface.CAPTION, SHEET_HINTS[i], sheetX + 24.0F, y + 40.0F,
                    quit ? AndroidWallpaper.errorInk() : AndroidWallpaper.faintInk(), UI.ALIGN_LEFT);
            if (!quit) {
                ui.icon(ICON_CHEVRON, sheetX + sheetWidth - 26.0F, y + rowHeight / 2.0F, 16.0F,
                        AndroidWallpaper.outlineVariant());
            }
        }
        ui.alphaPop();
        ui.pop();
    }

    // ------------------------------------------------------------------ //
    // Interaction
    // ------------------------------------------------------------------ //

    /** Bounds of the light/dark toggle, one step left of the title-screen switcher. */
    private float[] themeBounds() {
        float[] style = MenuStyleSwitch.buttonBounds(width, height);
        float size = style[2];
        float gap = size * 0.28F;
        return new float[]{style[0] - size - gap, style[1], size, size};
    }

    /** Light/dark toggle: a Material FAB drawing a sun or a crescent, no icon font needed. */
    private void drawThemeToggle(int mouseX, int mouseY) {
        float[] b = themeBounds();
        boolean dark = MenuTheme.dark();
        boolean hover = mouseX >= b[0] && mouseX < b[0] + b[2]
                && mouseY >= b[1] && mouseY < b[1] + b[3];
        float cx = b[0] + b[2] / 2.0F;
        float cy = b[1] + b[3] / 2.0F;
        float radius = b[2] / 2.0F;
        ui.shadow(b[0], b[1], b[2], b[3], radius, 3);
        int face = dark ? Material.mix(AndroidWallpaper.sheet(), 0xFFFFFFFF, hover ? 0.10F : 0.03F)
                : Material.mix(AndroidWallpaper.card(), AndroidWallpaper.ink(), hover ? 0.05F : 0.0F);
        ui.circle(cx, cy, radius, face);
        int ink = AndroidWallpaper.ink();
        if (dark) {
            // Sun: a disc with a ring around it.
            ui.circle(cx, cy, radius * 0.34F, ink);
            ui.outline(cx - radius * 0.62F, cy - radius * 0.62F, radius * 1.24F, radius * 1.24F,
                    radius * 0.62F, 1.5F, Material.withAlpha(ink, 0.55F));
        } else {
            // Crescent: a disc with a face-coloured disc biting into it.
            ui.circle(cx, cy, radius * 0.52F, ink);
            ui.circle(cx + radius * 0.30F, cy - radius * 0.26F, radius * 0.44F, face);
        }
    }

    private boolean dockHit(int index, float x, float y) {
        float left = dockX + dockCell * index;
        return x >= left && x < left + dockCell && y >= dockY && y < dockY + dockHeight;
    }

    private boolean rowHit(int index, float x, float y) {
        if (!sheetOpen) {
            return false;
        }
        float rowY = sheetY + sheetHeader + index * rowHeight;
        return x >= sheetX + 8.0F && x < sheetX + sheetWidth - 8.0F
                && y >= rowY && y < rowY + rowHeight;
    }

    private boolean insideSheet(float x, float y) {
        return x >= sheetX && x < sheetX + sheetWidth && y >= sheetY && y < sheetY + sheetHeight;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return;
        }
        float x = mouseX / scale - ox;
        float y = mouseY / scale - oy;

        if (sheetOpen) {
            for (int i = 0; i < SHEET_LABELS.length; i++) {
                if (rowHit(i, x, y)) {
                    click();
                    runSheet(i, mouseX, mouseY);
                    return;
                }
            }
            if (!insideSheet(x, y)) {
                sheetOpen = false;
            }
            return;
        }
        float[] theme = themeBounds();
        if (mouseX >= theme[0] && mouseX < theme[0] + theme[2]
                && mouseY >= theme[1] && mouseY < theme[1] + theme[3]) {
            click();
            MenuTheme.toggle();
            return;
        }
        if (MenuStyleSwitch.click(mouseX, mouseY, width, height)) {
            return;
        }
        for (int i = 0; i < DOCK_ICONS.length; i++) {
            if (dockHit(i, x, y)) {
                click();
                openDock(i, mouseX, mouseY);
                return;
            }
        }
    }

    private void openDock(int index, int mouseX, int mouseY) {
        switch (index) {
            case 0:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case 1:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case 2:
                MenuScreen.enterFrom(mouseX, mouseY, true);
                mc.displayGuiScreen(new AccountsScreen(this));
                break;
            default:
                MenuStyleSwitch.close();
                sheetOpen = true;
                break;
        }
    }

    private void runSheet(int index, int mouseX, int mouseY) {
        sheetOpen = false;
        switch (index) {
            case 0:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case 1:
                mc.displayGuiScreen(new GuiModList(this));
                break;
            case 2:
                MenuScreen.enterFrom(mouseX, mouseY, true);
                mc.displayGuiScreen(new ProxyScreen(this));
                break;
            case 3:
                MenuScreen.enterFrom(mouseX, mouseY, true);
                mc.displayGuiScreen(new BackgroundScreen(this));
                break;
            default:
                mc.shutdown();
                break;
        }
    }

    private void click() {
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (sheetOpen && keyCode == Keyboard.KEY_ESCAPE) {
            sheetOpen = false;
            return;
        }
        if (MenuStyleSwitch.keyTyped(keyCode)) {
            return;
        }
        if (OnyxClickGuiModule.instance != null && keyCode != 0
                && keyCode == OnyxClickGuiModule.instance.getKeycode()) {
            mc.displayGuiScreen(new OnyxClickGui());
        }
    }

    @Override
    public void updateScreen() {
    }

    @Override
    public void onGuiClosed() {
        sheetOpen = false;
        MenuStyleSwitch.close();
    }
}
