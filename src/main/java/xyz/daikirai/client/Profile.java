package xyz.daikirai.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import xyz.daikirai.gui.InfoHudEditor;
import xyz.daikirai.module.HudModule;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.ProfileManagerModule;
import xyz.daikirai.module.ScriptManagerModule;
import xyz.daikirai.module.SettingsModule;
import xyz.daikirai.module.movement.SprintModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

public class Profile {
    public String name;
    public String path;

    public Profile(String name, String path) {
        this.set(name, path);
    }

    public void set(String name, String path) {
        this.name = name;
        this.path = path;
    }

    public boolean load() {
        File file = new File(this.path);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            return false;
        }
        Profiles.current = null;
        Profiles.modified = false;
        ChatCommands.panic();
        try (FileReader r = new FileReader(file)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("modules");
            migrateCustomGui(arr);
            for (JsonElement e : arr) {
                JsonObject o = e.getAsJsonObject();
                String name = string(o, "name");
                for (Module m : new ArrayList<Module>(ModuleManager.modules)) {
                    if (!m.saveName().equals(name)) continue;
                    Boolean enabled = bool(o, "enabled");
                    m.releaseBind();
                    m.setBind(integer(o, "keybind"));
                    m.setHoldBind(Boolean.TRUE.equals(bool(o, "holdBind")));
                    Boolean hidden = bool(o, "hidden");
                    if (hidden != null) {
                        m.hidden = hidden;
                    }
                    if (m == ModuleManager.hud) {
                        Integer x = integerOrNull(o, "posX");
                        Integer y = integerOrNull(o, "posY");
                        if (x != null) HudModule.posX = x;
                        if (y != null) HudModule.posY = y;
                    } else if (m == ModuleManager.settings) {
                        Integer x = integerOrNull(o, "posX");
                        Integer y = integerOrNull(o, "posY");
                        if (x != null) InfoHudEditor.hudX = x;
                        if (y != null) InfoHudEditor.hudY = y;
                    } else if (m == ModuleManager.get("Sprint")) {
                        Integer x = integerOrNull(o, "posX");
                        Integer y = integerOrNull(o, "posY");
                        if (x != null) SprintModule.posX = x;
                        if (y != null) SprintModule.posY = y;
                        String text = string(o, "text");
                        if (!text.isEmpty()) {
                            SprintModule.setText(text);
                        }
                    } else if (m == ModuleManager.get("Name Hider")) {
                        String cname = string(o, "cname");
                        if (!cname.isEmpty()) {
                            ChatCommands.nameHiderRaw = cname;
                            ChatCommands.nameHider = Chat.color(cname);
                        }
                    }
                    for (Setting s : m.getSettings()) {
                        if (s instanceof xyz.daikirai.module.setting.ColorSetting) {
                            loadColor(o.get(s.getName()), (xyz.daikirai.module.setting.ColorSetting) s);
                            continue;
                        }
                        if (m instanceof xyz.daikirai.module.render.JumpCirclesModule
                                && s instanceof ButtonSetting && ((ButtonSetting) s).isMethodButton()) continue;
                        if (s instanceof SliderSetting) {
                            Double d = number(o, s.getName());
                            if (d != null) {
                                SliderSetting slider = (SliderSetting) s;
                                if (!slider.isMode()) {
                                    slider.setValueRaw(d);
                                } else {
                                    slider.setValue(d);
                                }
                            }
                        } else if (s instanceof ButtonSetting) {
                            Boolean b = bool(o, s.getName());
                            if (b != null) {
                                ButtonSetting button = (ButtonSetting) s;
                                button.set(b);
                                m.guiButtonToggled(button);
                            }
                        }
                    }
                    if (m instanceof xyz.daikirai.module.render.JumpCirclesModule) {
                        xyz.daikirai.module.render.JumpCirclesModule circles = (xyz.daikirai.module.render.JumpCirclesModule) m;
                        Integer color = integerOrNull(o, "circleColor");
                        if (!o.has("Color") && color != null) circles.colorSetting().setColor(color);
                        circles.setText(o.has("circleText") ? string(o, "circleText") : circles.text());
                    }
                    m.guiUpdate();
                    if (enabled != null && enabled && !m.isEnabled() && !SettingsModule.disableModulesOnLoad.isToggled()
                            && m != ModuleManager.commandLine && !m.isHoldBind()) {
                        m.enable();
                    }
                    break;
                }
            }
            Profiles.current = this;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int keybind() {
        int n = 0;
        File file = new File(this.path);
        if (file.exists() && file.isFile() && file.canRead()) {
            try (FileReader r = new FileReader(file)) {
                n = integer(new JsonParser().parse(r).getAsJsonObject(), "keybind");
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    public boolean save() {
        try {
            File file = new File(this.path);
            if (!file.exists() || !file.isFile()) {
                file.createNewFile();
            }
            try (JsonWriter w = new JsonWriter(new FileWriter(file, false))) {
                w.setIndent("    ");
                w.beginObject();
                Module self = Profiles.profiles.get(this);
                w.name("keybind").value(self == null ? 0L : (long) self.getKeycode());
                w.name("modules");
                w.beginArray();
                for (Module m : ModuleManager.modules) {
                    if (m.isProfile() || m instanceof ProfileManagerModule || m instanceof ScriptManagerModule) continue;
                    w.beginObject();
                    w.name("name").value(m.saveName());
                    w.name("enabled").value(m.isEnabled());
                    w.name("hidden").value(m.isHidden());
                    w.name("keybind").value((long) m.getKeycode());
                    w.name("holdBind").value(m.isHoldBind());
                    if (m == ModuleManager.hud) {
                        w.name("posX").value((long) HudModule.posX);
                        w.name("posY").value((long) HudModule.posY);
                    } else if (m == ModuleManager.settings) {
                        w.name("posX").value((long) InfoHudEditor.hudX);
                        w.name("posY").value((long) InfoHudEditor.hudY);
                    } else if (m == ModuleManager.get("Sprint")) {
                        w.name("posX").value((long) SprintModule.posX);
                        w.name("posY").value((long) SprintModule.posY);
                        w.name("text").value(SprintModule.storedText());
                    } else if (m == ModuleManager.get("Name Hider")) {
                        w.name("cname").value(ChatCommands.nameHiderRaw);
                    }
                    for (Setting s : m.getSettings()) {
                        if (!s.saved) continue;
                        if (s instanceof xyz.daikirai.module.setting.ColorSetting) {
                            xyz.daikirai.module.setting.ColorSetting color = (xyz.daikirai.module.setting.ColorSetting) s;
                            w.name(s.getName()).beginObject();
                            w.name("value").value(color.customColor());
                            w.name("accent").value(color.followsTheme());
                            w.endObject();
                        } else if (s instanceof SliderSetting) {
                            w.name(s.getName()).value(((SliderSetting) s).getInput());
                        } else if (s instanceof ButtonSetting) {
                            w.name(s.getName()).value(((ButtonSetting) s).isToggled());
                        }
                    }
                    if (m instanceof xyz.daikirai.module.render.JumpCirclesModule) {
                        xyz.daikirai.module.render.JumpCirclesModule circles = (xyz.daikirai.module.render.JumpCirclesModule) m;
                        w.name("circleText").value(circles.text());
                    }
                    w.endObject();
                }
                w.endArray();
                w.endObject().flush();
            }
            Profiles.current = this;
            Profiles.modified = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Translate the former standalone HUD modules in memory; saving uses the new modules.
     *
     * <p>The tab list now belongs to {@code Watermark} (the island draws it), so a legacy
     * {@code Tablist} module is folded into that module rather than into {@code CustomGui}.</p>
     */
    private static void migrateCustomGui(JsonArray modules) {
        JsonObject scoreboard = null;
        JsonObject tablist = null;
        JsonObject watermark = null;
        boolean hasCustomGui = false;
        for (JsonElement element : modules) {
            JsonObject module = element.getAsJsonObject();
            String name = string(module, "name");
            if (name.equals("CustomGui")) {
                hasCustomGui = true;
            } else if (name.equals("Scoreboard")) {
                scoreboard = module;
            } else if (name.equals("Tablist")) {
                tablist = module;
            } else if (name.equals("Watermark")) {
                watermark = module;
            }
        }
        if (tablist != null) {
            boolean tablistEnabled = Boolean.TRUE.equals(bool(tablist, "enabled"));
            if (watermark == null) {
                watermark = new JsonObject();
                watermark.addProperty("name", "Watermark");
                modules.add(watermark);
            }
            watermark.addProperty("Tablist", tablistEnabled);
            if (tablistEnabled) {
                watermark.addProperty("enabled", true);
            }
            copyCustomGuiSettings(watermark, tablist, "Tablist",
                    "Scale", "Frosted glass", "Server text", "Show ping");
        }
        if (hasCustomGui || scoreboard == null) {
            return;
        }
        boolean scoreboardEnabled = Boolean.TRUE.equals(bool(scoreboard, "enabled"));
        JsonObject customGui = new JsonObject();
        customGui.addProperty("name", "CustomGui");
        customGui.addProperty("enabled", scoreboardEnabled);
        customGui.addProperty("Scoreboard", scoreboardEnabled);
        copyCustomGuiSettings(customGui, scoreboard, "Scoreboard",
                "Scale", "Position X", "Position Y", "Frosted glass", "Show scores");
        modules.add(customGui);
    }

    private static void copyCustomGuiSettings(JsonObject target, JsonObject source, String prefix, String... names) {
        if (source == null) {
            return;
        }
        for (String name : names) {
            if (source.has(name)) {
                target.add(prefix + ": " + name, source.get(name));
            }
        }
    }

    private static void loadColor(JsonElement element, xyz.daikirai.module.setting.ColorSetting color) {
        if (element == null || element.isJsonNull()) return;
        try {
            if (element.isJsonObject()) {
                JsonObject value = element.getAsJsonObject();
                Integer custom = integerOrNull(value, "value");
                Boolean accent = bool(value, "accent");
                color.setAppearance(custom == null ? color.customColor() : custom,
                        accent == null ? color.followsTheme() : accent);
            } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                color.setColor(element.getAsInt());
            }
        } catch (RuntimeException ignored) { }
    }

    private static String string(JsonObject o, String k) {
        try {
            return o.get(k).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static Boolean bool(JsonObject o, String k) {
        try {
            return o.get(k).getAsBoolean();
        } catch (Exception e) {
            return null;
        }
    }

    private static int integer(JsonObject o, String k) {
        try {
            return o.get(k).getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Integer integerOrNull(JsonObject o, String k) {
        try {
            return o.get(k).getAsInt();
        } catch (Exception e) {
            return null;
        }
    }

    private static Double number(JsonObject o, String k) {
        try {
            return o.get(k).getAsDouble();
        } catch (Exception e) {
            return null;
        }
    }
}
