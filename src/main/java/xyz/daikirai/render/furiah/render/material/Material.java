package xyz.daikirai.render.furiah.render.material;

import xyz.daikirai.render.furiah.setting.Displayable;

public enum Material implements Displayable {
   GOLD("Gold", "Warm polished gold"),
   ROSE_GOLD("Rose gold", "Gold with a pink blush"),
   SILVER("Silver", "Bright polished silver"),
   CHROME("Chrome", "A mirror of the sky and the ground"),
   BRASS("Brass", "Antique yellow brass"),
   BRONZE("Bronze", "Deep reddish-brown bronze"),
   IRON("Iron", "Dark brushed iron"),
   OBSIDIAN("Obsidian", "Glossy volcanic black"),
   PEARL("Pearl", "Soft white with a rainbow sheen"),
   JADE("Jade", "Milky green stone"),
   CUSTOM("Custom", "Your color, with a finish");

   private final String displayName;
   private final String description;

   private Material(String displayName, String description) {
      this.displayName = displayName;
      this.description = description;
   }

   @Override
   public String displayName() {
      return this.displayName;
   }

   @Override
   public String description() {
      return this.description;
   }

   public boolean translucent(Finish finish) {
      return this == CUSTOM && finish == Finish.GLASS;
   }

   public int swatch() {
      switch (this) {
         case GOLD:
            return -1525686;
         case ROSE_GOLD:
            return -1596527;
         case SILVER:
            return -2762532;
         case CHROME:
            return -3879720;
         case BRASS:
            return -3562674;
         case BRONZE:
            return -5740230;
         case IRON:
            return -9539210;
         case OBSIDIAN:
            return -15067616;
         case PEARL:
            return -1185566;
         case JADE:
            return -11557256;
         default:
            return -1;
      }
   }
}
