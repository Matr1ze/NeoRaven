package xyz.daikirai.render.furiah.render.look;

public enum BodyLook {
   SKIN,
   MATERIAL,
   INK,
   SKETCH,
   GLASS,
   PAPER,
   SHADOW,
   TOON,
   GLITCH;

   public boolean derived() {
      return this != SKIN && this != MATERIAL && this != GLITCH;
   }

   public boolean outlined() {
      return this == INK || this == SKETCH || this == TOON;
   }

   public boolean seeThrough() {
      return this == GLASS;
   }
}
