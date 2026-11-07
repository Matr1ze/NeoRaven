package xyz.daikirai.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Lang {
    public static final String ENGLISH = "en";
    public static final String CHINESE = "zh";
    private static final Map<String, String> CHINESE_TEXT = new HashMap<String, String>();
    private static final Map<String, String> ENGLISH_TEXT = new HashMap<String, String>();
    private static String locale = CHINESE;
    private static int revision;

    static {
        read("en_us.json", ENGLISH_TEXT);
        read("zh_cn.json", CHINESE_TEXT);
    }

    private Lang() {
    }

    private static void read(String file, Map<String, String> target) {
        InputStream stream = null;
        try {
            stream = Lang.class.getResourceAsStream("/assets/clickgui/lang/" + file);
            if (stream == null) return;
            JsonObject json = new JsonParser()
                    .parse(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                target.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Exception ignored) {
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String get(String fallback) {
        if (fallback == null) return "";
        Map<String, String> table = CHINESE.equals(locale) ? CHINESE_TEXT : ENGLISH_TEXT;
        String value = table.get(fallback);
        return value == null ? fallback : value;
    }

    public static String module(String name) {
        return get(name);
    }

    public static String setting(String name) {
        return get(name);
    }

    public static String category(String name) {
        return get(name);
    }

    public static String text(String fallback) {
        return get(fallback);
    }

    /** Translation keyed by the vanilla translation key; English keeps the vanilla text. */
    public static String vanilla(String key, String fallback) {
        if (CHINESE.equals(locale)) {
            String value = CHINESE_TEXT.get(key);
            if (value != null) return value;
        }
        return fallback;
    }

    public static String locale() {
        return locale;
    }

    public static boolean chinese() {
        return CHINESE.equals(locale);
    }

    public static int revision() {
        return revision;
    }

    public static void setLocale(String value) {
        String next = ENGLISH.equalsIgnoreCase(value) ? ENGLISH : CHINESE;
        if (!next.equals(locale)) {
            locale = next;
            revision++;
        }
    }
}
