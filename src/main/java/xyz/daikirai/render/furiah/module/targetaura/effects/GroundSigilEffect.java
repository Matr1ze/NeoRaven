package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class GroundSigilEffect extends AuraEffect {
   private static final int RUNES = 12;
   private static final int TICKS = 48;
   public final BooleanSetting runes = this.setting(new BooleanSetting("sigil.runes", "Runes", "Band of runes around the outer ring.", true));
   public final SliderSetting pulse = this.setting(new SliderSetting("sigil.pulse", "Pulse", "How much the brightness breathes.", 0.0, 1.0, 0.5, 0.05));

   public GroundSigilEffect() {
      super(
         EffectType.GROUND_SIGIL,
         "sigil",
         new ThemeTint()
      );
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float a = this.intensity(c) * c.groundAlpha;
      if (!(a <= 0.003F)) {
         float spd = this.speed();
         float grow = Easing.outBack(Easing.clamp01(c.intro / 0.9F), 1.1F);
         double R = 1.05 * sz * grow;
         if (!(R <= 0.001)) {
            double x = c.x;
            double y = c.groundY + 0.015;
            double z = c.z;
            float breathe = 1.0F - this.pulse.getFloat() * 0.35F * (0.5F + 0.5F * (float)Math.sin(c.time * (Math.PI * 2) * 0.5));
            float b = a * breathe;
            double spinUp = (1.0F - Easing.outCubic(c.intro)) * 2.5;
            double outer = c.time * 0.35 * spd + spinUp;
            double inner = -c.time * 0.5 * spd - spinUp;
            RenderState.decal(true);
            Vfx.flatSprite(Textures.GROUND_GLOW, x, y, z, R * 1.12, outer, alpha(this.color(c, 0.6F), 0.28F * b));
            Vfx.flatSprite(Textures.GLOW, x, y, z, R * 0.45, 0.0, alpha(this.color(c, 0.0F), 0.45F * b));
            this.ring(c, Textures.LINE_GLOW, x, y, z, R * 0.985, 0.12 * sz, 0.35F * b, 1.0F, false);
            this.ring(c, Textures.LINE_GLOW, x, y, z, R * 0.72, 0.1 * sz, 0.3F * b, 0.7F, false);
            this.ring(c, Textures.LINE_CORE, x, y, z, R, 0.022 * sz, 0.9F * b, 1.0F, true);
            this.ring(c, Textures.LINE_CORE, x, y, z, R * 0.9, 0.013 * sz, 0.7F * b, 0.95F, true);
            this.ring(c, Textures.LINE_CORE, x, y, z, R * 0.72, 0.016 * sz, 0.85F * b, 0.7F, true);
            this.ring(c, Textures.LINE_CORE, x, y, z, R * 0.34, 0.013 * sz, 0.8F * b, 0.35F, true);
            this.ring(c, Textures.LINE_CORE, x, y, z, R * 0.12, 0.01 * sz, 0.7F * b, 0.1F, true);
            if (this.runes.get()) {
               double rr = R * 0.945;
               double half = R * 0.035;

               for (int k = 0; k < 12; k++) {
                  double ang = outer + k * (Math.PI * 2) / 12.0;
                  double cos = Math.cos(ang);
                  double sin = Math.sin(ang);
                  float u0 = k % 8 / 8.0F;
                  float u1 = u0 + 0.125F;
                  Vfx.flatQuad(
                     Textures.RUNES,
                     x + cos * rr,
                     y,
                     z + sin * rr,
                     -sin * half,
                     cos * half,
                     -cos * half,
                     -sin * half,
                     u0,
                     0.0F,
                     u1,
                     1.0F,
                     alpha(this.color(c, 0.95F), 0.9F * b)
                  );
               }
            } else {
               for (int k = 0; k < 24; k++) {
                  double ang = outer + k * (Math.PI * 2) / 24.0;
                  double cos = Math.cos(ang);
                  double sin = Math.sin(ang);
                  Vfx.flatSprite(Textures.SPARK, x + cos * R * 0.945, y, z + sin * R * 0.945, R * 0.03, 0.0, core(this.color(c, 0.95F), 0.8F * b));
               }
            }

            int tickColor = core(this.color(c, 0.78F), 0.75F * b);

            for (int k = 0; k < 48; k++) {
               double ang = inner + k * (Math.PI * 2) / 48.0;
               double cos = Math.cos(ang);
               double sin = Math.sin(ang);
               double r1 = R * (k % 4 == 0 ? 0.74 : 0.77);
               double r2 = R * 0.85;
               Vfx.flatLine(Textures.LINE_CORE, x + cos * r1, z + sin * r1, x + cos * r2, z + sin * r2, y, 0.011 * sz, tickColor, tickColor);
            }

            double sr = R * 0.72;
            float draw = Easing.clamp01((c.intro - 0.2F) / 0.7F);

            for (int pass = 0; pass < 2; pass++) {
               int tex = pass == 0 ? Textures.LINE_GLOW : Textures.LINE_CORE;
               double width = pass == 0 ? 0.07 * sz : 0.015 * sz;

               for (int k = 0; k < 8; k++) {
                  float line = Easing.outCubic(Easing.clamp01(draw * 1.8F - k * 0.1F));
                  if (!(line <= 0.0F)) {
                     double a1 = inner + k * (Math.PI * 2) / 8.0;
                     double a2 = inner + (k + 3) * (Math.PI * 2) / 8.0;
                     double x1 = x + Math.cos(a1) * sr;
                     double z1 = z + Math.sin(a1) * sr;
                     double x2 = x + Math.cos(a2) * sr;
                     double z2 = z + Math.sin(a2) * sr;
                     x2 = x1 + (x2 - x1) * line;
                     z2 = z1 + (z2 - z1) * line;
                     int col = this.color(c, 0.5F);
                     int lc = pass == 0 ? alpha(col, 0.3F * b) : core(col, 0.85F * b);
                     Vfx.flatLine(tex, x1, z1, x2, z2, y, width, lc, lc);
                  }
               }
            }

            int hex = core(this.color(c, 0.3F), 0.75F * b);

            for (int kx = 0; kx < 6; kx++) {
               double a1 = outer * 1.5 + kx * (Math.PI * 2) / 6.0;
               double a2 = outer * 1.5 + (kx + 1) * (Math.PI * 2) / 6.0;
               double r = R * 0.34;
               Vfx.flatLine(Textures.LINE_CORE, x + Math.cos(a1) * r, z + Math.sin(a1) * r, x + Math.cos(a2) * r, z + Math.sin(a2) * r, y, 0.012 * sz, hex, hex);
            }

            RenderState.decal(false);
         }
      }
   }

   private void ring(EffectContext c, int tex, double x, double y, double z, double radius, double width, float a, float radial, boolean crisp) {
      int col = this.color(c, radial);
      Vfx.flatRing(tex, x, y, z, radius, width, 128, crisp ? core(col, a) : alpha(col, a));
   }
}
