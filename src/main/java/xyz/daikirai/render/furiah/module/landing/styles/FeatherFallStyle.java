package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class FeatherFallStyle extends LandingStyleBase {
   public final ModeSetting<FeatherFallStyle.Piece> piece;
   public final SliderSetting count;
   private final Rng rng = new Rng();

   public FeatherFallStyle() {
      super(
         LandingStyle.FEATHER_FALL,
         1.8F,
         2.0F,
         new ThemeTint()
      );
      this.piece = this.setting(
         new ModeSetting<>("feathers.piece", "Pieces", "What flutters down.", FeatherFallStyle.Piece.class, FeatherFallStyle.Piece.FEATHERS)
      );
      this.count = this.setting(new SliderSetting("feathers.count", "Count", "How many pieces.", 6.0, 20.0, 11.0, 1.0));
   }

   private int texture() {
      switch ((FeatherFallStyle.Piece)this.piece.get()) {
         case PETALS:
            return Textures.PETAL;
         case LEAVES:
            return Textures.LEAF;
         case SNOWFLAKES:
            return Textures.SNOWFLAKE;
         default:
            return Textures.FEATHER;
      }
   }

   @Override
   public void start(LandingFx fx) {
      this.rng.seed(fx.seed);
      int n = fx.count(this.count.getFloat() * (fx.lod ? 0.5F : 1.0F));
      int tex = this.texture();

      for (int i = 0; i < n; i++) {
         double ang = (Math.PI * 2) * (i + this.rng.next() * 0.8) / n;
         float out = this.rng.range(1.4F, 2.8F) * (float)Math.sqrt(fx.size);
         float s = this.rng.range(0.34F, 0.46F) * (float)Math.sqrt(fx.size);
         double ax = -Math.sin(ang);
         double az = Math.cos(ang);
         fx.particles
            .spawn(
               9,
               fx.x() + Math.cos(ang) * 0.2,
               fx.y() + this.rng.range(0.15F, 0.45F),
               fx.z() + Math.sin(ang) * 0.2,
               (float)Math.cos(ang) * out,
               this.rng.range(2.2F, 3.6F),
               (float)Math.sin(ang) * out,
               fx.seconds(this.rng.range(1.6F, 1.9F)),
               s
            )
            .gravity(2.6F)
            .drag(2.9F)
            .axis((float)ax, 0.0F, (float)az)
            .spin(this.rng.next() * 6.28F, this.rng.signed() * 2.0F)
            .sway(this.rng.range(0.35F, 0.6F), this.rng.next() * 6.28F)
            .texture(tex)
            .color(this.color, this.rng.next())
            .alpha(0.95F * fx.opacity)
            .fade(0.02F, 0.72F)
            .flags(4)
            .ground(fx.y() + 0.02, 0, 0.0F);
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
   }

   public static enum Piece implements Displayable {
      FEATHERS("Feathers", "Soft feathers"),
      PETALS("Petals", "Blossom petals"),
      LEAVES("Leaves", "Leaves"),
      SNOWFLAKES("Snowflakes", "Snowflakes");

      private final String displayName;
      private final String description;

      private Piece(String displayName, String description) {
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
}
