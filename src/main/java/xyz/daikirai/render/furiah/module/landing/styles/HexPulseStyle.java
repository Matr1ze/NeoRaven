package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;

public final class HexPulseStyle extends LandingStyleBase {
   public final BooleanSetting honeycomb;
   public final ModeSetting<HexPulseStyle.Align> align;
   private static final int MAX_HEXES = 7;
   private final double[] hexX = new double[7];
   private final double[] hexZ = new double[7];
   private final double[] hexR = new double[7];
   private final float[] hexA = new float[7];
   private final float[] hexT = new float[7];
   private final float[] hexW = new float[7];
   private int hexes;

   public HexPulseStyle() {
      super(LandingStyle.HEX_PULSE, 1.0F, 2.0F, new ThemeTint());
      this.honeycomb = this.setting(new BooleanSetting("hexpulse.honeycomb", "Honeycomb", "Six smaller hexagons light up around it.", true));
      this.align = this.setting(
         new ModeSetting<>("hexpulse.align", "Align", "Which way the hexagon points.", HexPulseStyle.Align.class, HexPulseStyle.Align.FACING)
      );
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha() * (1.0F - Easing.smoothstep(0.45F, 1.0F, p));
      if (!(a <= 0.003F)) {
         double base = this.align.get() == HexPulseStyle.Align.FACING ? Math.toRadians(fx.land.yaw + 90.0F) : (Math.PI * 2) * fx.rand(1);
         double rot = base + 0.26 * Easing.outCubic(p);
         float pop = Math.max(0.0F, Easing.outBack(Easing.clamp01(p / 0.22F), 1.9F));
         double r = 0.95 * fx.size * pop;
         RenderState.decal(true);
         this.hexes = 0;
         this.keep(fx.x(), fx.z(), r, a, 0.0F, 1.0F);
         if (this.honeycomb.get()) {
            double small = 0.95 * fx.size * 0.42;

            for (int k = 0; k < 6; k++) {
               float q = Easing.clamp01((p - 0.12F - k * 0.045F) / 0.3F);
               if (!(q <= 0.0F)) {
                  float popK = Math.max(0.0F, Easing.outBack(q, 2.2F));
                  float ak = a * Easing.smoothstep(0.0F, 0.3F, q) * (1.0F - 0.35F * Easing.smoothstep(0.4F, 1.0F, q));
                  double dir = base + (Math.PI / 6) + k * Math.PI / 3.0 + 0.26 * Easing.outCubic(p);
                  double dist = 0.95 * fx.size + small * 0.98;
                  this.keep(fx.x() + Math.cos(dir) * dist, fx.z() + Math.sin(dir) * dist, small * popK, ak, (k + 1) / 7.0F, 0.7F);
               }
            }
         }

         RenderState.translucent(false);

         for (int i = 0; i < this.hexes; i++) {
            Ground.disc(
               f,
               fx.ground,
               Textures.HEX_FILL,
               this.hexX[i],
               this.hexZ[i],
               this.hexR[i],
               rot,
               alpha(this.color.getColor(this.hexT[i] + 0.5F), 0.36F * this.hexA[i]),
               3,
               24
            );
         }

         int offset = 0;

         for (int i = 0; i < this.hexes; i++) {
            offset = this.outline(fx, f, i, rot, offset);
         }

         Neon.flush();
         RenderState.decal(false);
      }
   }

   private void keep(double cx, double cz, double r, float a, float t0, float width) {
      if (!(r <= 0.01) && !(a <= 0.003F) && this.hexes < 7) {
         this.hexX[this.hexes] = cx;
         this.hexZ[this.hexes] = cz;
         this.hexR[this.hexes] = r;
         this.hexA[this.hexes] = a;
         this.hexT[this.hexes] = t0;
         this.hexW[this.hexes] = width;
         this.hexes++;
      }
   }

   private int outline(LandingFx fx, FxFrame f, int i, double rot, int offset) {
      int per = 5;
      int n = 6 * per + 1;

      for (int k = 0; k < n; k++) {
         int side = Math.min(5, k / per);
         float along = (float)(k - side * per) / per;
         double a0 = rot + side * Math.PI / 3.0;
         double a1 = rot + (side + 1) * Math.PI / 3.0;
         double x0 = this.hexX[i] + Math.cos(a0) * this.hexR[i];
         double z0 = this.hexZ[i] + Math.sin(a0) * this.hexR[i];
         double x1 = this.hexX[i] + Math.cos(a1) * this.hexR[i];
         double z1 = this.hexZ[i] + Math.sin(a1) * this.hexR[i];
         Vfx.X[offset + k] = x0 + (x1 - x0) * along;
         Vfx.Z[offset + k] = z0 + (z1 - z0) * along;
         Vfx.W[offset + k] = (float)(0.1 * this.hexW[i] * Math.sqrt(fx.size));
         Vfx.C[offset + k] = alpha(this.color.getColor(loop(this.hexT[i] + (side + along) / 6.0F)), this.hexA[i]);
      }

      Ground.toRender(f, fx.ground, offset, n);
      Neon.addFlat(offset, n);
      return offset + n;
   }

   public static enum Align implements Displayable {
      FACING("Facing", "Lined up with the player"),
      RANDOM("Random", "A random angle");

      private final String displayName;
      private final String description;

      private Align(String displayName, String description) {
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
