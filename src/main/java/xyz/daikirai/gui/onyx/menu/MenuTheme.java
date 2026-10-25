package xyz.daikirai.gui.onyx.menu;

import xyz.daikirai.client.ClientConfig;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Locale;

/**
 * Light/dark choice for the rewritten title screens plus the dock's colour override.
 *
 * <p>The Android menus are drawn from a tonal palette that {@link AndroidWallpaper} re-derives when
 * the mode flips; the dock pill follows the wallpaper's own average colour unless the user picks
 * one in the colour sheet ({@code 0} means "follow"). Both are persisted next to the other client
 * files, so the choice survives a restart.</p>
 */
public final class MenuTheme {

    private static final File FILE = new File(ClientConfig.DIR, "menu_theme.txt");

    private static boolean loaded;
    /** First-launch defaults: dark Android menus, wallpaper blur in its Frosted glass mode. */
    private static boolean dark = true;
    private static boolean dockFollows = true;
    private static int dock;
    private static int blurMode = 2;

    private MenuTheme() {
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!FILE.isFile()) {
                return;
            }
            BufferedReader reader = new BufferedReader(new FileReader(FILE));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                    String value = line.substring(eq + 1).trim();
                    if (key.equals("dark")) {
                        dark = value.equals("1");
                    } else if (key.equals("dockfollow")) {
                        dockFollows = value.equals("1");
                    } else if (key.equals("blurmode")) {
                        try {
                            blurMode = Math.max(0, Math.min(2, Integer.parseInt(value)));
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (key.equals("blur")) {
                        // older files only had a boolean: 1 meant the Mica pass
                        blurMode = value.equals("1") ? 1 : 0;
                    } else if (key.equals("dock")) {
                        try {
                            dock = (int) Long.parseLong(value.startsWith("0x") ? value.substring(2) : value, 16);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return;
            }
            FileWriter writer = new FileWriter(FILE, false);
            try {
                writer.write("dark=" + (dark ? 1 : 0) + "\n");
                writer.write("dockFollow=" + (dockFollows ? 1 : 0) + "\n");
                writer.write("blurMode=" + blurMode + "\n");
                writer.write(String.format(Locale.ROOT, "dock=%08X\n", dock));
            } finally {
                writer.close();
            }
        } catch (Exception ignored) {
        }
    }

    /** Whether the Android menus use the black Material scheme. */
    public static boolean dark() {
        load();
        return dark;
    }

    public static void setDark(boolean value) {
        load();
        if (dark == value) {
            return;
        }
        dark = value;
        save();
    }

    public static void toggle() {
        setDark(!dark());
    }

    /** 0 = off, 1 = Mica blur, 2 = the client's own frosted glass. */
    public static int blurMode() {
        load();
        return blurMode;
    }

    public static void setBlurMode(int mode) {
        load();
        int clamped = Math.max(0, Math.min(2, mode));
        if (blurMode == clamped) {
            return;
        }
        blurMode = clamped;
        save();
    }

    /** Cycles off -> Mica -> frosted glass -> off. */
    public static void nextBlurMode() {
        setBlurMode((blurMode() + 1) % 3);
    }

    public static boolean blur() {
        return blurMode() != 0;
    }

    /** The dock colour the user picked, or {@code 0} while it still follows the wallpaper. */
    public static int dockColor() {
        load();
        return dockFollows ? 0 : dock;
    }

    public static boolean dockFollows() {
        load();
        return dockFollows;
    }

    /** {@code argb} with {@code follow} set means "back to following the wallpaper". */
    public static void setDock(int argb, boolean follow) {
        load();
        dockFollows = follow;
        if (!follow) {
            dock = argb | 0xFF000000;
        }
        save();
    }
}
