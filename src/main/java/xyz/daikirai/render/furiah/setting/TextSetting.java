package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting {
   private final String defaultValue;
   private final int maxLength;
   private String value;

   public TextSetting(String id, String name, String description, String defaultValue, int maxLength) {
      super(id, name, description);
      this.maxLength = maxLength;
      this.value = this.defaultValue = clean(defaultValue, maxLength);
   }

   public String get() {
      return this.value;
   }

   public void set(String text) {
      String v = clean(text, this.maxLength);
      if (!v.equals(this.value)) {
         this.value = v;
         this.fireChanged();
      }
   }

   public int getMaxLength() {
      return this.maxLength;
   }

   public String getDefault() {
      return this.defaultValue;
   }

   public static String clean(String text, int maxLength) {
      if (text == null) {
         return "";
      } else {
         StringBuilder sb = new StringBuilder(Math.min(text.length(), maxLength));

         for (int i = 0; i < text.length() && sb.length() < maxLength; i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != 127 && c != 167) {
               sb.append(c);
            }
         }

         return sb.toString();
      }
   }

   @Override
   public void reset() {
      this.set(this.defaultValue);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive()) {
         this.set(json.getAsString());
      }
   }
}
