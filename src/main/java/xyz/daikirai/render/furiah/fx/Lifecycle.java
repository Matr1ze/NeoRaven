package xyz.daikirai.render.furiah.fx;

public enum Lifecycle {
   FADE("FADE"),
   ANIMATION("ANIM");

   private final String tag;

   private Lifecycle(String tag) {
      this.tag = tag;
   }

   public String tag() {
      return this.tag;
   }
}
