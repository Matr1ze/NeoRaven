package xyz.daikirai.gui.onyx.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.client.Profile;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The {@code .hidekey} list: modules the user removed from the Keybinds HUD.
 *
 * <p>This is deliberately independent from Raven's own {@code .hide} command, which flips
 * {@link Module#hidden} and removes the module from the HUD element list. A module can be hidden
 * from one list while staying visible in the other, and profiles never sync the two.</p>
 *
 * <p>Each profile stores lowercase module names. Client defaults also cover sessions with
 * no selected profile and seed profiles created before the list was part of their JSON.</p>
 */
public final class KeybindsHidden {

    public static final String CONFIG_KEY = "hiddenKeybinds";
    private static final String LEGACY_FILE = "keybinds-hidden.txt";
    private static Set<String> defaults;
    private static Set<String> hidden;

    private KeybindsHidden() {
    }

    /** Whether the Keybinds card should skip this module. */
    public static synchronized boolean isHidden(Module module) {
        return module != null && isHidden(module.getName());
    }

    public static synchronized boolean isHidden(String moduleName) {
        return moduleName != null && entries().contains(normalize(moduleName));
    }

    /** Adds the module; returns {@code true} when it was not hidden before. */
    public static synchronized boolean hide(Module module) throws IOException {
        if (module == null || isHidden(module)) {
            return false;
        }
        Set<String> updated = new LinkedHashSet<>(entries());
        updated.add(normalize(module.getName()));
        store(updated);
        return true;
    }

    /** Removes the module; returns {@code true} when it was hidden before. */
    public static synchronized boolean show(Module module) throws IOException {
        if (!isHidden(module)) {
            return false;
        }
        Set<String> updated = new LinkedHashSet<>(entries());
        updated.remove(normalize(module.getName()));
        store(updated);
        return true;
    }

    /** Flips the module; returns the new hidden state. */
    public static synchronized boolean toggle(Module module) throws IOException {
        return !show(module) && hide(module);
    }

    /** Unhides everything; returns how many entries were removed. */
    public static synchronized int clear() throws IOException {
        int count = entries().size();
        if (count > 0) {
            store(Collections.<String>emptySet());
        }
        return count;
    }

    public static synchronized int size() {
        return entries().size();
    }

    /** Hidden module names in their current spelling (unknown/stale names are kept verbatim). */
    public static synchronized List<String> names() {
        List<String> names = new ArrayList<String>(entries().size());
        for (String key : entries()) {
            Module module = ModuleManager.getIgnoreCase(key);
            names.add(module == null ? key : module.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** An explicitly empty array overrides the client defaults. */
    public static synchronized void useProfile(JsonElement saved) {
        hidden = saved != null && saved.isJsonArray() ? decode(saved.getAsJsonArray()) : defaults();
    }

    public static synchronized JsonArray toJson() {
        return encode(entries());
    }

    private static Set<String> entries() {
        if (hidden == null) {
            useProfile(null);
        }
        return hidden;
    }

    private static Set<String> defaults() {
        if (defaults != null) return defaults;
        defaults = new LinkedHashSet<>();
        File config = clientFile();
        if (config.exists()) {
            try (BufferedReader reader = Files.newBufferedReader(config.toPath(), StandardCharsets.UTF_8)) {
                JsonElement saved = new JsonParser().parse(reader).getAsJsonObject().get(CONFIG_KEY);
                if (saved != null && saved.isJsonArray()) {
                    defaults = decode(saved.getAsJsonArray());
                    return defaults;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("[onyx] could not read the client hidden keybinds: " + e);
                return defaults;
            }
        }
        File legacy = new File(ClientConfig.DIR, LEGACY_FILE);
        if (!legacy.isFile()) return defaults;
        Set<String> migrated = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(legacy.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    migrated.add(normalize(line));
                }
            }
        } catch (IOException e) {
            System.err.println("[onyx] could not read " + LEGACY_FILE + ": " + e);
            return defaults;
        }
        defaults = migrated;
        try {
            Profile.writeHiddenKeybinds(config, encode(defaults), true);
        } catch (IOException e) {
            System.err.println("[onyx] could not migrate " + LEGACY_FILE + ": " + e);
        }
        return defaults;
    }

    private static Set<String> decode(JsonArray saved) {
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement item : saved) {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                String name = normalize(item.getAsString());
                if (!name.isEmpty()) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private static JsonArray encode(Set<String> names) {
        List<String> sorted = new ArrayList<>(names);
        Collections.sort(sorted);
        JsonArray result = new JsonArray();
        for (String name : sorted) result.add(new JsonPrimitive(name));
        return result;
    }

    private static void store(Set<String> updated) throws IOException {
        Profile active = Profiles.current;
        File file = active == null ? clientFile() : new File(active.path);
        Profile.writeHiddenKeybinds(file, encode(updated), active == null);
        hidden = updated;
        if (active == null) defaults = updated;
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static File clientFile() {
        return new File(ClientConfig.DIR, "client.json");
    }
}
