package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.gui.onyx.render.UI;

/**
 * The bottom right title-screen switcher: a Material floating action button that opens a menu with
 * the three title screens (Minecraft / Onyx / Android).
 *
 * <p>It draws and hit-tests in logical screen coordinates on every title screen, so the same
 * component serves the dark Onyx menu, the light Android menu and the vanilla one (through
 * {@code MixinGuiMainMenu}). Only the button is permanent - the mode names live in the popup, so
 * the wallpaper itself stays free of text.</p>
 */
public final class MenuStyleSwitch {

    private static final float INSET = 14.0F;
    private static final float BUTTON = 42.0F;
    private static final float POPUP_WIDTH = 176.0F;
    private static final float POPUP_HEADER = 28.0F;
    private static final float ROW_HEIGHT = 34.0F;
    private static final float POPUP_PADDING = 6.0F;

    /**
     * Scale factor for the component. The title screens are drawn in a design space that shrinks
     * with the window, so a fixed pixel button would grow relative to the dock on small screens;
     * tying the size to the logical height keeps the same visual weight everywhere.
     */
    private static float unit(int height) {
        return Material.clamp(height / 360.0F, 0.72F, 1.45F);
    }

    private static boolean open;
    private static UI overlayUi;
    private static long lastFrame;
    private static final Spring reveal = new Spring(0.0F);
    private static final Spring press = new Spring(0.0F);
    private static final Spring[] rowHover = new Spring[MenuStyle.values().length];

    static {
        for (int i = 0; i < rowHover.length; i++) {
            rowHover[i] = new Spring(0.0F);
        }
    }

    private MenuStyleSwitch() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void open() {
        open = true;
    }

    public static void close() {
        open = false;
    }

    /** Drops the transient animation state; called when a title screen is created. */
    public static void reset() {
        open = false;
        reveal.set(0.0F);
        press.set(0.0F);
        for (Spring spring : rowHover) {
            spring.set(0.0F);
        }
    }

    // ------------------------------------------------------------------ //
    // Geometry (logical screen units)
    // ------------------------------------------------------------------ //

    /** {@code {x, y, w, h}} of the circular button. */
    public static float[] buttonBounds(int width, int height) {
        float u = unit(height);
        float size = BUTTON * u;
        float inset = INSET * u;
        return new float[]{width - inset - size, height - inset - size, size, size};
    }

    public static float[] popupBounds(int width, int height) {
        float u = unit(height);
        float[] button = buttonBounds(width, height);
        float w = POPUP_WIDTH * u;
        float h = (POPUP_HEADER + ROW_HEIGHT * MenuStyle.values().length + POPUP_PADDING) * u;
        return new float[]{Math.max(8.0F, button[0] + button[2] - w), button[1] - 10.0F * u - h, w, h};
    }

    public static float[] rowBounds(int width, int height, int index) {
        float u = unit(height);
        float[] popup = popupBounds(width, height);
        return new float[]{popup[0] + POPUP_PADDING * u,
                popup[1] + POPUP_HEADER * u + index * ROW_HEIGHT * u,
                popup[2] - POPUP_PADDING * 2.0F * u, ROW_HEIGHT * u};
    }

    private static boolean inside(float[] rect, float x, float y) {
        return x >= rect[0] && y >= rect[1] && x < rect[0] + rect[2] && y < rect[1] + rect[3];
    }

    // ------------------------------------------------------------------ //
    // Interaction
    // ------------------------------------------------------------------ //

    /**
     * Handles a click in logical screen coordinates.
     *
     * @return whether the click belonged to the switcher (the caller must then stop handling it)
     */
    public static boolean click(float x, float y, int width, int height) {
        float[] button = buttonBounds(width, height);
        if (open) {
            for (int i = 0; i < MenuStyle.values().length; i++) {
                if (inside(rowBounds(width, height, i), x, y)) {
                    choose(i);
                    return true;
                }
            }
            boolean insidePopup = inside(popupBounds(width, height), x, y);
            boolean onButton = inside(button, x, y);
            open = false;
            // Clicking the button again toggles the menu shut instead of reopening it.
            if (insidePopup || onButton) {
                return true;
            }
        } else if (inside(button, x, y)) {
            open = true;
            return true;
        }
        return false;
    }

    private static void choose(int index) {
        MenuStyle target = MenuStyle.values()[index];
        open = false;
        reset();
        MenuStyle.select(target);
        Minecraft mc = Minecraft.getMinecraft();
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        mc.displayGuiScreen(target.screen());
    }

    /** Closes the popup on Escape; returns whether it consumed the key. */
    public static boolean keyTyped(int key) {
        if (open && key == Keyboard.KEY_ESCAPE) {
            open = false;
            return true;
        }
        return false;
    }

    /**
     * Vanilla title screen entry point (called from {@code MixinGuiMainMenu}).
     *
     * <p>Lives here instead of in the mixin so the {@code width}/{@code height} reads happen in a
     * normally compiled class, where the build remaps them, and the mixin needs no added fields.</p>
     */
    public static void drawOverVanilla(GuiScreen screen, int mouseX, int mouseY) {
        if (overlayUi == null) {
            overlayUi = new UI();
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        overlayUi.begin();
        draw(overlayUi, false, screen.width, screen.height, mouseX, mouseY, dt);
        overlayUi.end();
    }

    /** Click entry point for the vanilla title screen. */
    public static boolean clickOverVanilla(GuiScreen screen, int mouseX, int mouseY, int button) {
        return button == 0 && click(mouseX, mouseY, screen.width, screen.height);
    }

    // ------------------------------------------------------------------ //
    // Rendering
    // ------------------------------------------------------------------ //

    /**
     * Draws the button (and its popup) in logical screen coordinates.
     *
     * @param light whether the surface under the component is light (the Android wallpaper), which
     *              flips the popup from the dark Material surface to a white one
     */
    public static void draw(UI ui, boolean light, int width, int height, float mx, float my, float dt) {
        Scheme scheme = Theme.scheme();
        float[] button = buttonBounds(width, height);

        int surface = light ? 0xFFFFFFFF : scheme.surfaceContainerHigh;
        int ink = light ? 0xFF1B1B1F : scheme.onSurface;
        int soft = light ? 0xFF5A5860 : scheme.onSurfaceVariant;
        int accent = light ? AndroidWallpaper.primary() : scheme.primary;
        int onAccent = light ? AndroidWallpaper.onPrimary() : scheme.onPrimary;

        boolean hovered = inside(button, mx, my);
        press.target(hovered || open ? 1.0F : 0.0F);
        press.update(dt);
        reveal.target(open ? 1.0F : 0.0F);
        reveal.update(dt);

        float cx = button[0] + button[2] / 2.0F;
        float cy = button[1] + button[3] / 2.0F;
        float radius = button[2] / 2.0F;
        // Raised, slightly larger while hovered: the MD3 extended FAB states, without the bounce.
        ui.shadow(button[0], button[1], button[2], button[3], radius, open ? 4 : 3);
        ui.circle(cx, cy, radius * (1.0F + 0.04F * press.value()), accent);
        if (press.value() > 0.01F) {
            ui.circle(cx, cy, radius * (1.0F + 0.04F * press.value()),
                    Material.withAlpha(onAccent, 0.10F * press.value()));
        }
        ui.icon(Icons.PALETTE, cx, cy, button[2] * 0.48F, onAccent);

        if (reveal.value() < 0.01F) {
            return;
        }

        // Popup: Material elevated surface with radio rows, growing out of the button.
        float u = unit(height);
        float[] popup = popupBounds(width, height);
        float progress = Material.clamp(reveal.value(), 0.0F, 1.0F);
        float lift = (1.0F - progress) * 10.0F * u;
        ui.alphaPush(progress);
        ui.shadow(popup[0], popup[1] + lift, popup[2], popup[3], 16.0F * u, 3);
        ui.rounded(popup[0], popup[1] + lift, popup[2], popup[3], 16.0F * u, surface);
        ui.outline(popup[0], popup[1] + lift, popup[2], popup[3], 16.0F * u, 1.0F,
                light ? 0x14000000 : Material.withAlpha(scheme.outlineVariant, 0.5F));
        ui.text(Typeface.CAPTION, "Menu style", popup[0] + 16.0F * u, popup[1] + lift + 17.0F * u, soft,
                UI.ALIGN_LEFT);
        for (int i = 0; i < MenuStyle.values().length; i++) {
            float[] row = rowBounds(width, height, i);
            row[1] += lift;
            boolean rowHovered = open && inside(row, mx, my);
            rowHover[i].target(rowHovered ? 1.0F : 0.0F);
            rowHover[i].update(dt);
            if (rowHover[i].value() > 0.01F) {
                ui.rounded(row[0], row[1], row[2], row[3], 12.0F * u,
                        Material.withAlpha(ink, 0.07F * rowHover[i].value()));
            }
            boolean selected = MenuStyle.values()[i] == MenuStyle.current();
            ui.text(Typeface.BODY_MEDIUM, MenuStyle.values()[i].label(), row[0] + 16.0F * u,
                    row[1] + row[3] / 2.0F, selected ? accent : ink, UI.ALIGN_LEFT);
            float dotRadius = 7.0F * u;
            float dotX = row[0] + row[2] - 18.0F * u;
            float dotY = row[1] + row[3] / 2.0F;
            if (selected) {
                ui.circle(dotX, dotY, dotRadius, accent);
                ui.circle(dotX, dotY, 3.0F * u, surface);
            } else {
                ui.outline(dotX - dotRadius, dotY - dotRadius, dotRadius * 2.0F, dotRadius * 2.0F, dotRadius,
                        1.5F, light ? 0xFF8B8890 : scheme.outline);
            }
        }
        ui.alphaPop();
    }
}
