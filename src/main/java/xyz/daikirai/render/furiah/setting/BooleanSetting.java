package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting {
   private final boolean defaultValue;
   private boolean value;
   private boolean sectionSwitch;

   public BooleanSetting(String id, String name, String description, boolean defaultValue) {
      super(id, name, description);
      this.defaultValue = defaultValue;
      this.value = defaultValue;
   }

   public boolean get() {
      return this.value;
   }

   public void set(boolean value) {
      if (this.value != value) {
         this.value = value;
         this.fireChanged();
      }
   }

   public void toggle() {
      this.set(!this.value);
   }

   void setQuietly(boolean value) {
      this.value = value;
   }

   public boolean getDefault() {
      return this.defaultValue;
   }

   public BooleanSetting sectionSwitch() {
      this.sectionSwitch = true;
      return this;
   }

   public boolean isSectionSwitch() {
      return this.sectionSwitch;
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
      if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
         this.set(json.getAsBoolean());
      }
   }
}
