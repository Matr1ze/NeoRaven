package xyz.daikirai.gui.onyx.menu;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import xyz.daikirai.client.ClientConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Which title screen the client shows.
 *
 * <p>Three looks share the same entry point: the untouched vanilla {@code GuiMainMenu}, the Onyx
 * menu the port shipped with, and the Android/AOSP-style menu. The choice is persisted next to the
 * other client files so a restart keeps it, and the bottom right switcher cycles it from inside any
 * of the three screens.</p>
 */
public enum MenuStyle {

    MINECRAFT("Minecraft"),
    ONYX("Onyx"),
    ANDROID("Android");

    private static final File FILE = new File(ClientConfig.DIR, "menu_style.txt");

    private final String label;
    private static MenuStyle current;

    MenuStyle(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static MenuStyle current() {
        if (current == null) {
            current = read();
        }
        return current;
    }

    /** Selects a style and remembers it for the next launch. */
    public static void select(MenuStyle style) {
        if (style == null) {
            return;
        }
        current = style;
        write(style);
    }

    /** A fresh screen of this style, ready for {@code Minecraft#displayGuiScreen}. */
    public GuiScreen screen() {
        switch (this) {
            case ANDROID:
                return new AndroidMainMenu();
            case ONYX:
                return new MainMenu();
            default:
                return new GuiMainMenu();
        }
    }

    /**
     * Maps a vanilla title screen request onto the configured style. Any other screen (including
     * the Android/Onyx menus, which extend {@code GuiMainMenu}) is returned untouched so this can
     * never wrap itself.
     */
    public static GuiScreen wrap(GuiScreen requested) {
        if (requested == null || requested.getClass() != GuiMainMenu.class) {
            return requested;
        }
        MenuStyle style = current();
        return style == MINECRAFT ? requested : style.screen();
    }

    private static MenuStyle read() {
        try {
            if (FILE.isFile()) {
                BufferedReader reader = new BufferedReader(new FileReader(FILE));
                try {
                    String line = reader.readLine();
                    if (line != null) {
                        String value = line.trim().toLowerCase(Locale.ROOT);
                        for (MenuStyle style : values()) {
                            if (style.label.toLowerCase(Locale.ROOT).equals(value)) {
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

    private static void write(MenuStyle style) {
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
