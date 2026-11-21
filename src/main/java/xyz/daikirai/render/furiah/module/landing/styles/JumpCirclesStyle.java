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
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class JumpCirclesStyle extends LandingStyleBase {
   private static final int SEGMENTS = 96;
   private static final int DASHES = 26;
   public final SliderSetting rings = this.setting(new SliderSetting("circles.rings", "Rings", "How many rings, 80 ms apart.", 1.0, 3.0, 2.0, 1.0));
   public final SliderSetting radius = this.setting(
      new SliderSetting("circles.radius", "Radius", "How far the rings reach.", 0.5, 3.0, 1.5, 0.05).unit(" blocks")
   );
   public final SliderSetting thickness = this.setting(
      new SliderSetting("circles.thickness", "Thickness", "Width of the rings.", 0.5, 2.0, 1.0, 0.05).unit("x")
   );
   public final ModeSetting<JumpCirclesStyle.Line> line = this.setting(
      new ModeSetting<>("circles.line", "Line", "How the rings are drawn.", JumpCirclesStyle.Line.class, JumpCirclesStyle.Line.SOLID)
   );
   private int offset;

   public JumpCirclesStyle() {
      super(LandingStyle.JUMP_CIRCLES, 1.2F, 1.6F, new ThemeTint());
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float fadeAll = 1.0F - Easing.smoothstep(0.5F, 1.0F, p);
      int n = this.rings.getInt();
      double maxR = this.radius.get() * fx.size;
      float th = this.thickness.getFloat();
      float stagger = 0.08F / fx.style.baseSeconds();
      RenderState.decal(true);
      this.offset = 0;

      for (int k = 0; k < n; k++) {
         float q = Easing.clamp01((p - k * stagger) / 0.55F);
         if (!(q <= 0.0F)) {
            double r = Math.max(0.04, maxR * Easing.outQuint(q) * (1.0 - 0.12 * k));
            float a = fx.alpha() * fadeAll * Easing.smoothstep(0.0F, 0.05F, q) * (1.0F - 0.18F * k);
            if (!(a <= 0.003F)) {
               float shift = k * 0.21F;
               if (k == 0) {
                  RenderState.translucent(false);
                  Ground.disc(f, fx.ground, Textures.RADIAL_FILL, fx.x(), fx.z(), r, 0.0, alpha(this.color.getColor(0.5F), 0.3F * a), 4, 32);
                  RenderState.additive();
               }

               double w = 0.115 * th * Math.sqrt(fx.size);
               switch ((JumpCirclesStyle.Line)this.line.get()) {
                  case DASHED:
                     for (int d = 0; d < 26; d++) {
                        double a0 = (Math.PI * 2) * d / 26.0 + p * 0.4;
                        double span = 0.14016336454477538;
                        int c = alpha(this.color.getColor(loop(d / 26.0F + shift)), a);

                        for (int i = 0; i <= 4; i++) {
                           double ang = a0 + span * i / 4.0;
                           Vfx.X[this.offset + i] = fx.x() + Math.cos(ang) * r;
                           Vfx.Z[this.offset + i] = fx.z() + Math.sin(ang) * r;
                           Vfx.W[this.offset + i] = (float)w;
                           Vfx.C[this.offset + i] = c;
                        }

                        Ground.toRender(f, fx.ground, this.offset, 5);
                        Neon.addFlat(this.offset, 5);
                        this.offset += 5;
                     }
                     break;
                  case DOUBLE:
                     this.ring(fx, f, r, w * 0.8, a, shift);
                     this.ring(fx, f, r * 0.84, w * 0.6, a * 0.75F, shift + 0.3F);
                     break;
                  default:
                     this.ring(fx, f, r, w, a, shift);
               }
            }
         }
      }

      Neon.flush();
      RenderState.decal(false);
   }

   private void ring(LandingFx fx, FxFrame f, double r, double w, float a, float shift) {
      int count = Ground.ringPoints(f, fx.ground, this.offset, fx.x(), fx.z(), r, w, 96, this.color, 0, a, shift);
      Neon.addFlat(this.offset, count);
      this.offset += count;
   }

   public static enum Line implements Displayable {
      SOLID("Solid", "One unbroken ring"),
      DASHED("Dashed", "A ring of dashes"),
      DOUBLE("Double", "Two rings side by side");

      private final String displayName;
      private final String description;

      private Line(String displayName, String description) {
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
