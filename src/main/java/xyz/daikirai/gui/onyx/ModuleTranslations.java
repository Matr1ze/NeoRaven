package xyz.daikirai.gui.onyx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Chinese module names, descriptions, category labels and search matching. */
final class ModuleTranslations {

    static final class Entry {
        final String zh;
        final String desc;

        Entry(String zh, String desc) {
            this.zh = zh == null ? "" : zh;
            this.desc = desc == null ? "" : desc;
        }
    }

    private static final String RESOURCE = "/assets/clickgui/modules_zh_cn.json";
    private static final Map<String, Entry> ENTRIES = new HashMap<String, Entry>();
    private static boolean loaded;

    private ModuleTranslations() {
    }

    static synchronized Entry get(Module module) {
        if (module == null) {
            return null;
        }
        load();
        Category category = module.moduleCategory();
        return ENTRIES.get(key(category == null ? "" : category.name(), module.getName()));
    }

    static String name(Module module) {
        Entry entry = get(module);
        return entry == null || entry.zh.isEmpty() ? Text.strip(module.getName()) : entry.zh;
    }

    static String description(Module module) {
        Entry entry = get(module);
        return entry == null ? "" : entry.desc;
    }

    static String category(Category category) {
        if (category == null) {
            return "";
        }
        switch (category) {
            case combat:
                return "战斗";
            case movement:
                return "移动";
            case player:
                return "玩家";
            case world:
                return "世界";
            case render:
                return "渲染";
            case network:
                return "网络";
            case minigames:
                return "小游戏";
            case other:
                return "其他";
            case client:
                return "客户端";
            case scripts:
                return "脚本";
            case profiles:
                return "配置";
            default:
                return category.name();
        }
    }

    /** Extends the GUI search to the Chinese name and description. */
    static synchronized boolean matches(Module module, String needle) {
        if (module == null || needle == null || needle.isEmpty()) {
            return false;
        }
        Entry entry = get(module);
        String query = needle.toLowerCase(Locale.ROOT);
        return entry != null
                && (entry.zh.toLowerCase(Locale.ROOT).contains(query)
                || entry.desc.toLowerCase(Locale.ROOT).contains(query));
    }

    private static String key(String category, String name) {
        return category.toLowerCase(Locale.ROOT) + ":" + Text.strip(name).trim().toLowerCase(Locale.ROOT);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        InputStream stream = ModuleTranslations.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonArray array = new JsonParser().parse(reader).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject object = element.getAsJsonObject();
                String name = string(object, "name");
                String category = string(object, "category");
                if (name.isEmpty() || category.isEmpty()) {
                    continue;
                }
                ENTRIES.put(key(category, name),
                        new Entry(string(object, "zh"), string(object, "desc")));
            }
        } catch (Exception e) {
            System.out.println("[onyx] could not load Chinese module translations: " + e);
        }
    }

    private static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
