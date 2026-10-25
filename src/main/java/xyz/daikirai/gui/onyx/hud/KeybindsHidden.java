package xyz.daikirai.gui.onyx.hud;

import xyz.daikirai.client.ClientConfig;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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
 * <p>The list is stored as lowercase module names in {@code keystrokes/keybinds-hidden.txt}, so it
 * survives restarts and re-ordering without touching the profile format.</p>
 */
public final class KeybindsHidden {

    private static final String FILE_NAME = "keybinds-hidden.txt";
    private static final Set<String> HIDDEN = new LinkedHashSet<String>();
    private static boolean loaded;

    private KeybindsHidden() {
    }

    /** Whether the Keybinds card should skip this module. */
    public static synchronized boolean isHidden(Module module) {
        return module != null && isHidden(module.getName());
    }

    public static synchronized boolean isHidden(String moduleName) {
        load();
        return moduleName != null && HIDDEN.contains(moduleName.toLowerCase(Locale.ROOT));
    }

    /** Adds the module; returns {@code true} when it was not hidden before. */
    public static synchronized boolean hide(Module module) {
        load();
        if (module == null) {
            return false;
        }
        boolean changed = HIDDEN.add(module.getName().toLowerCase(Locale.ROOT));
        if (changed) {
            save();
        }
        return changed;
    }

    /** Removes the module; returns {@code true} when it was hidden before. */
    public static synchronized boolean show(Module module) {
        load();
        if (module == null) {
            return false;
        }
        boolean changed = HIDDEN.remove(module.getName().toLowerCase(Locale.ROOT));
        if (changed) {
            save();
        }
        return changed;
    }

    /** Flips the module; returns the new hidden state. */
    public static synchronized boolean toggle(Module module) {
        if (module == null) {
            return false;
        }
        if (show(module)) {
            return false;
        }
        hide(module);
        return true;
    }

    /** Unhides everything; returns how many entries were removed. */
    public static synchronized int clear() {
        load();
        int count = HIDDEN.size();
        if (count > 0) {
            HIDDEN.clear();
            save();
        }
        return count;
    }

    public static synchronized int size() {
        load();
        return HIDDEN.size();
    }

    /** Hidden module names in their current spelling (unknown/stale names are kept verbatim). */
    public static synchronized List<String> names() {
        load();
        List<String> names = new ArrayList<String>(HIDDEN.size());
        for (String key : HIDDEN) {
            Module module = ModuleManager.getIgnoreCase(key);
            names.add(module == null ? key : module.getName());
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    // ------------------------------------------------------------------ //
    // Persistence
    // ------------------------------------------------------------------ //

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = file();
        if (!file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    HIDDEN.add(line.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            System.err.println("[onyx] could not read " + FILE_NAME + ": " + e);
        }
    }

    private static void save() {
        File file = file();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("could not create " + parent);
            }
            List<String> sorted = new ArrayList<String>(HIDDEN);
            Collections.sort(sorted);
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write("# modules hidden from the Keybinds HUD (.hidekey)\n");
                for (String name : sorted) {
                    writer.write(name);
                    writer.write('\n');
                }
            }
        } catch (Exception e) {
            System.err.println("[onyx] could not write " + FILE_NAME + ": " + e);
        }
    }

    private static File file() {
        return new File(ClientConfig.DIR, FILE_NAME);
    }
}
