package xyz.daikirai.render.furiah.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting<E extends Enum<E>> extends Setting {
   private final Class<E> type;
   private final E[] constants;
   private final E defaultValue;
   private E value;
   private ModeSetting.Style style = ModeSetting.Style.SEGMENTED;

   public ModeSetting(String id, String name, String description, Class<E> type, E defaultValue) {
      super(id, name, description);
      this.type = type;
      this.constants = type.getEnumConstants();
      this.defaultValue = defaultValue;
      this.value = defaultValue;
   }

   public ModeSetting<E> style(ModeSetting.Style style) {
      this.style = style;
      return this;
   }

   public ModeSetting.Style getStyle() {
      return this.style;
   }

   public E get() {
      return this.value;
   }

   public void set(E value) {
      if (value != null && value != this.value) {
         this.value = value;
         this.fireChanged();
      }
   }

   public E[] getValues() {
      return this.constants;
   }

   void setQuietly(Object value) {
      this.value = this.type.cast(value);
   }

   public int getIndex() {
      return this.value.ordinal();
   }

   public void setIndex(int index) {
      this.set(this.constants[Math.floorMod(index, this.constants.length)]);
   }

   public E getDefault() {
      return this.defaultValue;
   }

   public static String displayName(Enum<?> constant) {
      if (constant instanceof Displayable) {
         return ((Displayable)constant).displayName();
      } else {
         String[] words = constant.name().toLowerCase().split("_");
         StringBuilder sb = new StringBuilder();

         for (String word : words) {
            if (!word.isEmpty()) {
               if (sb.length() > 0) {
                  sb.append(' ');
               }

               sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
         }

         return sb.toString();
      }
   }

   public static String description(Enum<?> constant) {
      return constant instanceof Displayable ? ((Displayable)constant).description() : "";
   }

   @Override
   public void reset() {
      this.set(this.defaultValue);
   }

   @Override
   public JsonElement toJson() {
      return new JsonPrimitive(this.value.name());
   }

   @Override
   public void fromJson(JsonElement json) {
      if (json != null && json.isJsonPrimitive()) {
         String name = json.getAsString();

         for (E constant : this.constants) {
            if (constant.name().equalsIgnoreCase(name)) {
               this.set(constant);
               return;
            }
         }
      }
   }

   public Class<E> getType() {
      return this.type;
   }

   public static enum Style {
      SEGMENTED,
      CARDS,
      CHIPS;
   }
}
