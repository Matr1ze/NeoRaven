package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StringListSetting extends Setting {
   private final List<String> defaults;
   private final List<String> values = new ArrayList<>();
   private int version;

   public StringListSetting(String id, String name, String description, String... defaults) {
      super(id, name, description);
      this.defaults = Collections.unmodifiableList(new ArrayList<>(Arrays.asList(defaults)));
      this.values.addAll(this.defaults);
   }

   public List<String> get() {
      return Collections.unmodifiableList(this.values);
   }

   public List<String> getDefaults() {
      return this.defaults;
   }

   public boolean contains(String value) {
      return this.indexOf(value) >= 0;
   }

   public int indexOf(String value) {
      for (int i = 0; i < this.values.size(); i++) {
         if (this.values.get(i).equalsIgnoreCase(value)) {
            return i;
         }
      }

      return -1;
   }

   public void add(String value) {
      if (value != null && !value.trim().isEmpty() && !this.contains(value.trim())) {
         this.values.add(value.trim());
         this.changed();
      }
   }

   public void remove(String value) {
      int i = this.indexOf(value);
      if (i >= 0) {
         this.values.remove(i);
         this.changed();
      }
   }

   public void set(List<String> list) {
      ArrayList<String> clean = new ArrayList<>();

      for (String s : list) {
         if (s != null && !s.trim().isEmpty()) {
            boolean dup = false;

            for (String c : clean) {
               if (c.equalsIgnoreCase(s.trim())) {
                  dup = true;
                  break;
               }
            }

            if (!dup) {
               clean.add(s.trim());
            }
         }
      }

      if (!clean.equals(this.values)) {
         this.values.clear();
         this.values.addAll(clean);
         this.changed();
      }
   }

   public void move(int from, int to) {
      if (from >= 0 && from < this.values.size() && to >= 0 && to < this.values.size() && from != to) {
         this.values.add(to, this.values.remove(from));
         this.changed();
      }
   }

   public int version() {
      return this.version;
   }

   private void changed() {
      this.version++;
      this.fireChanged();
   }

   @Override
   public boolean showInMenu() {
      return false;
   }

   @Override
   public void reset() {
      this.set(this.defaults);
   }

   @Override
   public JsonElement toJson() {
      JsonArray array = new JsonArray();

      for (String s : this.values) {
         array.add(new JsonPrimitive(s));
      }

      return array;
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonArray()) {
         ArrayList<String> list = new ArrayList<>();

         for (JsonElement e : json.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
               list.add(e.getAsString());
            }
         }

         this.set(list);
      }
   }

   public static String appKey(String name) {
      String s = name.trim().toLowerCase(Locale.ROOT);
      return s.endsWith(".exe") ? s.substring(0, s.length() - 4) : s;
   }
}
