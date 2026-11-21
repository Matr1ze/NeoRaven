package xyz.daikirai.render.furiah.setting;


public class EnumSetSetting<E extends Enum<E>> extends StringListSetting implements EnumChoices {
   private final E[] values;
   private final boolean atLeastOne;
   private int cachedVersion = -1;
   private final boolean[] cached;

   @SafeVarargs
   public EnumSetSetting(String id, String name, String description, Class<E> type, boolean atLeastOne, E... defaults) {
      super(id, name, description, names(defaults));
      this.values = type.getEnumConstants();
      this.atLeastOne = atLeastOne;
      this.cached = new boolean[this.values.length];
   }

   private static String[] names(Enum<?>[] defaults) {
      String[] out = new String[defaults.length];

      for (int i = 0; i < defaults.length; i++) {
         out[i] = defaults[i].name();
      }

      return out;
   }

   public E[] values() {
      return this.values;
   }

   @Override
   public Enum<?>[] choices() {
      return this.values;
   }

   public boolean includes(E value) {
      if (this.cachedVersion != this.version()) {
         this.cachedVersion = this.version();

         for (int i = 0; i < this.values.length; i++) {
            this.cached[i] = this.contains(this.values[i].name());
         }
      }

      return this.cached[value.ordinal()];
   }

   public int count() {
      int n = 0;

      for (E v : this.values) {
         n += this.includes(v) ? 1 : 0;
      }

      return n;
   }

   public boolean toggle(E value) {
      if (this.includes(value)) {
         if (this.atLeastOne && this.count() <= 1) {
            return false;
         }

         this.remove(value.name());
      } else {
         this.add(value.name());
      }

      return true;
   }

   @Override
   public boolean showInMenu() {
      return true;
   }

}
