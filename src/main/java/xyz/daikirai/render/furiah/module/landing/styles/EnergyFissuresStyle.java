package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Procedural;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class EnergyFissuresStyle extends LandingStyleBase {
   private static final int MAX_POINTS = 17;
   private static final int BRANCH_POINTS = 9;
   public final SliderSetting cracks;
   public final SliderSetting length;
   private final Rng rng = new Rng();
   private final double[] cx = new double[3];
   private final double[] cy = new double[3];
   private final double[] cz = new double[3];

   public EnergyFissuresStyle() {
      super(LandingStyle.ENERGY_FISSURES, 1.4F, 2.6F, new ThemeTint());
      this.cracks = this.setting(new SliderSetting("fissures.cracks", "Cracks", "How many cracks split out.", 3.0, 10.0, 6.0, 1.0));
      this.length = this.setting(new SliderSetting("fissures.length", "Length", "How long the cracks are.", 0.8, 3.0, 1.6, 0.05).unit(" blocks"));
   }

   @Override
   public void start(LandingFx fx) {
      this.rng.seed(fx.seed);
      float k = fx.land.fallScale;
      int main = Math.max(2, Math.round(this.cracks.getFloat() * (0.8F + 0.35F * k)));
      EnergyFissuresStyle.Web web = new EnergyFissuresStyle.Web();
      web.x = new float[main * 3][];
      web.z = new float[main * 3][];
      web.n = new int[main * 3];
      web.branch = new boolean[main * 3];

      for (int i = 0; i < main; i++) {
         double ang = (Math.PI * 2) * (i + this.rng.signed() * 0.35) / main;
         double len = this.length.get() * fx.size * this.rng.range(0.7F, 1.15F);
         this.cx[0] = fx.x() + Math.cos(ang) * 0.12;
         this.cz[0] = fx.z() + Math.sin(ang) * 0.12;
         this.cx[1] = fx.x() + Math.cos(ang + this.rng.signed() * 0.25) * len * 0.55;
         this.cz[1] = fx.z() + Math.sin(ang + this.rng.signed() * 0.25) * len * 0.55;
         this.cx[2] = fx.x() + Math.cos(ang + this.rng.signed() * 0.3) * len;
         this.cz[2] = fx.z() + Math.sin(ang + this.rng.signed() * 0.3) * len;
         int n = Procedural.jagged(this.rng, 0, this.cx, this.cy, this.cz, 3, 3, 0.2, true);
         store(web, n, false);
         int branches = this.rng.nextInt(3);

         for (int b = 0; b < branches && web.count < web.n.length; b++) {
            int from = 4 + this.rng.nextInt(Math.max(1, n - 8));
            float[] px = web.x[web.count - 1 - b];
            float[] pz = web.z[web.count - 1 - b];
            double bx = px[Math.min(from, px.length - 1)];
            double bz = pz[Math.min(from, pz.length - 1)];
            double bang = ang + (this.rng.next() < 0.5F ? -1 : 1) * this.rng.range(0.5F, 1.0F);
            double blen = len * this.rng.range(0.25F, 0.45F);
            int bn = Procedural.bolt(this.rng, 0, bx, 0.0, bz, bx + Math.cos(bang) * blen, 0.0, bz + Math.sin(bang) * blen, 3, 0.25);
            store(web, bn, true);
         }
      }

      fx.data = web;
      if (!fx.lod) {
         int embers = fx.count(10.0F * (0.8F + 0.4F * k));

         for (int i = 0; i < embers; i++) {
            int c = this.rng.nextInt(web.count);
            int idx = this.rng.nextInt(web.n[c]);
            double ex = web.x[c][idx];
            double ez = web.z[c][idx];
            fx.particles
               .spawn(
                  7,
                  ex,
                  fx.ground.at(ex, ez) + 0.05,
                  ez,
                  this.rng.signed() * 0.6F,
                  this.rng.range(1.6F, 3.2F),
                  this.rng.signed() * 0.6F,
                  fx.seconds(this.rng.range(0.4F, 0.65F)),
                  this.rng.range(0.04F, 0.065F)
               )
               .gravity(8.0F)
               .drag(0.8F)
               .color(this.color, (float)idx / web.n[c])
               .alpha(fx.opacity)
               .delay(fx.life * this.rng.range(0.14F, 0.65F))
               .ground(fx.y() + 0.03, 1, 0.3F);
         }
      }
   }

   private static void store(EnergyFissuresStyle.Web web, int n, boolean branch) {
      int n2 = Math.min(n, 17);
      float[] x = new float[n2];
      float[] z = new float[n2];

      for (int i = 0; i < n2; i++) {
         x[i] = (float)Vfx.X[i];
         z[i] = (float)Vfx.Z[i];
      }

      web.x[web.count] = x;
      web.z[web.count] = z;
      web.n[web.count] = n2;
      web.branch[web.count] = branch;
      web.count++;
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      if (fx.data instanceof EnergyFissuresStyle.Web) {
         EnergyFissuresStyle.Web web = (EnergyFissuresStyle.Web)fx.data;
         float p = fx.progress();
         float spread = Easing.outQuad(Easing.clamp01(p / 0.11F));
         float pulse = (float)Math.exp(-Math.pow((p - 0.16F) / 0.06F, 2.0));
         float cool = Easing.smoothstep(0.22F, 0.9F, p);
         float a = fx.alpha() * (1.0F - Easing.smoothstep(0.62F, 1.0F, p));
         if (!(a <= 0.003F) && !(spread <= 0.0F)) {
            RenderState.decal(true);
            int crater = this.color.getColor(0.0F);
            RenderState.translucent(false);
            Ground.disc(
               f,
               fx.ground,
               Textures.GLOW,
               fx.x(),
               fx.z(),
               0.8 * fx.size,
               0.0,
               alpha(ColorUtil.lerp(crater, ColorUtil.shade(crater, 0.3F), cool), 0.35F * a),
               3,
               20
            );
            RenderState.additive();
            Ground.disc(f, fx.ground, Textures.GLOW, fx.x(), fx.z(), 0.85 * fx.size, 0.0, alpha(crater, (0.25F + 0.4F * pulse) * a * (1.0F - cool)), 3, 20);
            int offset = 0;

            for (int c = 0; c < web.count; c++) {
               int n = web.n[c];
               int shown = Math.max(2, Math.round(n * spread));
               float taperBase = web.branch[c] ? 0.6F : 1.0F;

               for (int i = 0; i < shown; i++) {
                  Vfx.X[offset + i] = web.x[c][i];
                  Vfx.Z[offset + i] = web.z[c][i];
                  float s = (float)i / (n - 1);
                  int col = this.color.getColor(s);
                  col = ColorUtil.lerp(ColorUtil.lerp(col, Neon.tint(col), pulse * 0.6F), ColorUtil.shade(col, 0.3F), cool);
                  Vfx.W[offset + i] = (float)(0.1 * Math.sqrt(fx.size) * taperBase * (1.0F - 0.7F * s) * (1.0F + 0.3F * pulse));
                  Vfx.C[offset + i] = alpha(col, a);
               }

               Ground.toRender(f, fx.ground, offset, shown);
               Neon.addFlat(offset, shown);
               offset += shown;
            }

            Neon.flush(1.0F + pulse, 1.0F, 1.0F - 0.6F * cool);
            RenderState.decal(false);
         }
      }
   }

   private static final class Web {
      int count;
      float[][] x;
      float[][] z;
      int[] n;
      boolean[] branch;

      private Web() {
      }
   }
}
