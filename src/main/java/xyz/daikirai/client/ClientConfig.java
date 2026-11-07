package xyz.daikirai.client;

import xyz.daikirai.gui.CategoryComponent;
import xyz.daikirai.gui.ClickGui;
import xyz.daikirai.module.GuiModule;
import net.minecraft.client.Minecraft;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class ClientConfig {
    public static final String DIR = Minecraft.getMinecraft().mcDataDir + File.separator + "keystrokes";
    public static String defaultProfile;
    private static float onyxPanelX;
    private static float onyxPanelY;
    private static boolean onyxPanelXLoaded;
    private static boolean onyxPanelYLoaded;

    private ClientConfig() {
    }

    private static void write(FileWriter w, String key, Object value) throws Exception {
        w.write(key + ": " + value + "\n");
    }

    public static void save() {
        try {
            File file = new File(DIR, "config.txt");
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            FileWriter w = new FileWriter(file, false);
            write(w, "default_profile", defaultProfile == null ? "" : defaultProfile);
            write(w, "language", xyz.daikirai.util.Lang.locale());
            write(w, "save_categories", GuiModule.saveCategoryPositions.isToggled());
            if (ClickGui.categories != null) {
                for (CategoryComponent c : ClickGui.categories) {
                    write(w, "category-" + c.categoryName.name(), c.getX() + "," + c.getY());
                }
            }
            write(w, "friends", String.join(",", Friends.friends));
            write(w, "enemies", String.join(",", Friends.enemies));
            write(w, "onyx_panel_x", xyz.daikirai.gui.onyx.Panel.savedOffsetX());
            write(w, "onyx_panel_y", xyz.daikirai.gui.onyx.Panel.savedOffsetY());
            w.close();
        } catch (Exception ignored) {
        }
    }

    public static void load() {
        Friends.friends.clear();
        Friends.enemies.clear();
        onyxPanelX = 0.0F;
        onyxPanelY = 0.0F;
        onyxPanelXLoaded = false;
        onyxPanelYLoaded = false;
        File file = new File(DIR, "config.txt");
        boolean loaded = false;
        if (file.exists()) {
            try {
                BufferedReader r = new BufferedReader(new FileReader(file));
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.contains(": ")) continue;
                    String[] kv = line.split(": ");
                    if (kv.length <= 1) continue;
                    String key = kv[0], value = kv[1];
                    if (key.equals("default_profile")) {
                        defaultProfile = value;
                    } else if (key.equals("language")) {
                        xyz.daikirai.util.Lang.setLocale(value);
                    } else if (key.equals("save_categories")) {
                        GuiModule.saveCategoryPositions.setEnabled(value.equals("true"));
                        GuiModule.saveCategoryPositions.updateLabel();
                    } else if (key.startsWith("category-") && GuiModule.saveCategoryPositions.isToggled()) {
                        String name = key.substring("category-".length());
                        String[] xy = value.split(",");
                        if (xy.length == 2) {
                            try {
                                float x = Float.parseFloat(xy[0]);
                                float y = Float.parseFloat(xy[1]);
                                for (CategoryComponent c : ClickGui.categories) {
                                    if (c.categoryName.name().equals(name)) {
                                        c.setX(x);
                                        c.setY(y);
                                        break;
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    } else if (key.startsWith("friends")) {
                        for (String s : value.split(",")) {
                            if (!s.isEmpty()) Friends.friends.add(s);
                        }
                    } else if (key.startsWith("enemies")) {
                        for (String s : value.split(",")) {
                            if (!s.isEmpty()) Friends.enemies.add(s);
                        }
                    } else if (key.equals("onyx_panel_x")) {
                        try {
                            onyxPanelX = Float.parseFloat(value);
                            onyxPanelXLoaded = true;
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (key.equals("onyx_panel_y")) {
                        try {
                            onyxPanelY = Float.parseFloat(value);
                            onyxPanelYLoaded = true;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                r.close();
                loaded = true;
            } catch (Exception ignored) {
            }
        }
        if (!loaded) {
            save();
        }
        if (!onyxPanelXLoaded || !onyxPanelYLoaded) {
            xyz.daikirai.gui.onyx.Panel.restoreOffset(0.0F, 0.0F);
        } else {
            xyz.daikirai.gui.onyx.Panel.restoreOffset(onyxPanelX, onyxPanelY);
        }
        xyz.daikirai.gui.onyx.OnyxClickGuiModule.refreshLanguageSetting();
        Profiles.loadAll();
        xyz.daikirai.module.minigames.SpeedBuildersModule.loadTimes();
    }
}
