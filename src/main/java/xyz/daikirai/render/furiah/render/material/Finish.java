package xyz.daikirai.render.furiah.render.material;

import xyz.daikirai.render.furiah.setting.Displayable;

public enum Finish implements Displayable {
   METALLIC("Metallic", "Polished metal in your color"),
   SATIN("Satin", "A soft sheen"),
   MATTE("Matte", "No shine at all"),
   GLASS("Glass", "See-through, with bright edges"),
   PEARLESCENT("Pearlescent", "A rainbow sheen at the edges"),
   TOON("Toon", "Three flat bands of light and a crisp highlight, like a comic");

   private final String displayName;
   private final String description;

   private Finish(String displayName, String description) {
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
}
