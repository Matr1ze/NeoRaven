package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.gui.onyx.Icons;
import xyz.daikirai.gui.onyx.material.Material;
import xyz.daikirai.gui.onyx.material.Scheme;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.render.Spring;
import xyz.daikirai.gui.onyx.render.UI;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Which options screen "Options" opens: the Material 3 tablet rewrite or the untouched vanilla one.
 *
 * <p>Also owns the bottom-right button that flips between them - it has to exist on the vanilla
 * screen too (through {@code MixinGuiOptions}), otherwise switching to "Minecraft" would strand the
 * user in a screen with no way back.</p>
 */
public enum SettingsStyle {

    ANDROID("Android"),
    MINECRAFT("Minecraft");

    private static final File FILE = new File(ClientConfig.DIR, "settings_style.txt");
    private static final float INSET = 14.0F;
    private static final float BUTTON = 42.0F;

    private static SettingsStyle value;
    private static GuiScreen returnTo;
    private static UI overlayUi;
    private static long lastFrame;
    private static final Spring press = new Spring(0.0F);

    private final String label;

    SettingsStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SettingsStyle current() {
        if (value == null) {
            value = read();
        }
        return value;
    }

    public static void select(SettingsStyle style) {
        if (style == null) {
            return;
        }
        value = style;
        write(style);
    }

    public static SettingsStyle other() {
        return current() == ANDROID ? MINECRAFT : ANDROID;
    }

    /**
     * Replaces a vanilla options request with the Android one. Only the exact {@code GuiOptions}
     * class is replaced: {@code GuiVideoSettings} and friends are their own screens and are opened
     * from inside the rewrite on purpose.
     *
     * <p>The screen that was showing when options opened is remembered for <em>both</em> styles.
     * Opening the vanilla screen and then switching to the Android one used to lose it - the
     * Android screen fell back to the title screen, and since the world was still loaded the
     * player ended up on the main menu while "in" a world, with no way back in.</p>
     */
    public static GuiScreen wrap(GuiScreen requested) {
        if (requested == null || requested.getClass() != GuiOptions.class) {
            return requested;
        }
        GuiScreen previous = Minecraft.getMinecraft().currentScreen;
        if (previous != null && previous.getClass() != GuiOptions.class
                && !(previous instanceof AndroidSettingsScreen)) {
            returnTo = previous;
        }
        if (current() != ANDROID) {
            return requested;
        }
        return new AndroidSettingsScreen(returnTo);
    }

    /** Switches style and reopens the options screen in the other look. */
    private static void toggle() {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen parent = returnTo;
        if (parent == null && mc.currentScreen != null && mc.currentScreen.getClass() != GuiOptions.class
                && !(mc.currentScreen instanceof AndroidSettingsScreen)) {
            parent = mc.currentScreen;
        }
        if (parent == null || (AndroidScreens.needsWorld(parent) && mc.theWorld == null)) {
            // No usable parent: the pause menu in a world, the title screen otherwise. Never the
            // title screen while a world is loaded - that state strands the client.
            parent = mc.theWorld != null
                    ? new net.minecraft.client.gui.GuiIngameMenu()
                    : new net.minecraft.client.gui.GuiMainMenu();
            returnTo = parent;
        }
        select(other());
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
        mc.displayGuiScreen(new GuiOptions(parent, mc.gameSettings));
    }

    // ------------------------------------------------------------------ //
    // Bottom-right button
    // ------------------------------------------------------------------ //

    private static float unit(int height) {
        return Material.clamp(height / 360.0F, 0.72F, 1.45F);
    }

    private static float[] bounds(int width, int height) {
        float u = unit(height);
        float size = BUTTON * u;
        float inset = INSET * u;
        return new float[]{width - inset - size, height - inset - size, size, size};
    }

    /** {@code {x, y, w, h}} of the style button, for the automated harness. */
    public static float[] buttonBounds(int width, int height) {
        return bounds(width, height);
    }

    public static boolean clickFab(float x, float y, int width, int height) {
        float[] button = bounds(width, height);
        if (x >= button[0] && y >= button[1] && x < button[0] + button[2] && y < button[1] + button[3]) {
            toggle();
            return true;
        }
        return false;
    }

    /** Draws the button in logical screen coordinates. */
    public static void drawFab(UI ui, boolean light, int width, int height, float mx, float my, float dt) {
        float[] button = bounds(width, height);
        boolean hovered = mx >= button[0] && my >= button[1] && mx < button[0] + button[2] && my < button[1] + button[3];
        press.target(hovered ? 1.0F : 0.0F);
        press.update(dt);
        Scheme scheme = Theme.scheme();
        int accent = light ? AndroidWallpaper.primary() : scheme.primary;
        int onAccent = light ? AndroidWallpaper.onPrimary() : scheme.onPrimary;
        float cx = button[0] + button[2] / 2.0F;
        float cy = button[1] + button[3] / 2.0F;
        float radius = button[2] / 2.0F;
        ui.shadow(button[0], button[1], button[2], button[3], radius, 3);
        ui.circle(cx, cy, radius * (1.0F + 0.04F * press.value()), accent);
        if (press.value() > 0.01F) {
            ui.circle(cx, cy, radius * (1.0F + 0.04F * press.value()),
                    Material.withAlpha(onAccent, 0.10F * press.value()));
        }
        ui.icon(Icons.PALETTE, cx, cy, button[2] * 0.48F, onAccent);
    }

    // ------------------------------------------------------------------ //
    // Vanilla options hook (MixinGuiOptions)
    // ------------------------------------------------------------------ //

    public static void drawOverVanilla(GuiScreen screen, int mouseX, int mouseY) {
        if (overlayUi == null) {
            overlayUi = new UI();
        }
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 16.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        overlayUi.begin();
        drawFab(overlayUi, false, screen.width, screen.height, mouseX, mouseY, dt);
        overlayUi.end();
    }

    public static boolean clickOverVanilla(GuiScreen screen, int mouseX, int mouseY, int button) {
        return button == 0 && clickFab(mouseX, mouseY, screen.width, screen.height);
    }



    private static SettingsStyle read() {
        try {
            if (FILE.isFile()) {
                BufferedReader reader = new BufferedReader(new FileReader(FILE));
                try {
                    String line = reader.readLine();
                    if (line != null) {
                        String text = line.trim().toLowerCase(Locale.ROOT);
                        for (SettingsStyle style : values()) {
                            if (style.label.toLowerCase(Locale.ROOT).equals(text)) {
                                return style;
                            }
                        }
                    }
                } finally {
                    reader.close();
                }
            }
        } catch (Exception ignored) {
        }
        return ANDROID;
    }

    private static void write(SettingsStyle style) {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            FileWriter writer = new FileWriter(FILE, false);
            try {
                writer.write(style.label + "\n");
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }
}
