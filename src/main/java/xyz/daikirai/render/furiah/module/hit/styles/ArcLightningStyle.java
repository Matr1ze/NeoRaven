package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Procedural;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ArcLightningStyle extends HitStyleBase {
   private static final float FLICKER_SECONDS = 0.04F;
   private static final int CONTROLS = 5;
   private static final int MAX_TIPS = 16;
   public final SliderSetting arcs;
   public final SliderSetting branches;
   private final Rng rng = new Rng();
   private final double[] cx = new double[5];
   private final double[] cy = new double[5];
   private final double[] cz = new double[5];
   private final double[] tipX = new double[16];
   private final double[] tipY = new double[16];
   private final double[] tipZ = new double[16];
   private int tips;

   public ArcLightningStyle() {
      super(HitStyle.ARC_LIGHTNING, 0.4F, new ThemeTint());
      this.arcs = this.setting(new SliderSetting("lightning.arcs", "Arcs", "How many arcs crawl over the body.", 2.0, 4.0, 3.0, 1.0));
      this.branches = this.setting(new SliderSetting("lightning.branches", "Branches", "How much the arcs fork.", 0.0, 2.0, 1.0, 0.1).unit("x"));
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha() * (1.0F - Easing.smoothstep(0.5F, 1.0F, p)) * Easing.smoothstep(0.0F, 0.03F, p);
      if (!(a <= 0.003F)) {
         Basis b = fx.basis;
         int bucket = (int)(fx.age / (0.04F * fx.timeScale));
         double bodyX = fx.cx();
         double bodyZ = fx.cz();
         double ix = fx.ix();
         double iy = fx.iy();
         double iz = fx.iz();
         double hugR = fx.hit.width * 0.5 + 0.12;
         double startAngle = Math.atan2(iz - bodyZ, ix - bodyX);
         int n = this.arcs.getInt() + (fx.crit ? 1 : 0);
         float size = fx.size;
         float crawl = Easing.outCubic(Easing.clamp01(p / 0.3F));
         int c0 = this.color.getColor(0.0F);
         Vfx.sprite(Textures.GLOW, f.rx(ix), f.ry(iy), f.rz(iz), 0.8 * size, 0.0F, alpha(c0, 0.45F * a));
         int offset = 0;
         this.tips = 0;

         for (int k = 0; k < n; k++) {
            this.rng.seed(fx.seed + k * 977, bucket);
            int count;
            if (k == 0) {
               double exitDist = hugR * 1.1;
               this.cx[0] = ix;
               this.cy[0] = iy;
               this.cz[0] = iz;
               this.cx[1] = bodyX + b.fx * exitDist * 0.3;
               this.cy[1] = iy + b.fy * 0.2;
               this.cz[1] = bodyZ + b.fz * exitDist * 0.3;
               double out = exitDist + (0.7 + 0.5 * fx.rand(3)) * size * crawl;
               this.cx[2] = bodyX + b.fx * out;
               this.cy[2] = iy + b.fy * out;
               this.cz[2] = bodyZ + b.fz * out;
               count = 3;
            } else {
               double side = (k % 2 == 0 ? 1 : -1) * fx.hit.side;
               double sweep = side * Math.toRadians(110.0F + 90.0F * fx.rand(k * 7 + 1)) * crawl;
               double rise = fx.srand(k * 7 + 2) * 0.8 * fx.hit.height * 0.5 * crawl;

               for (int i2 = 0; i2 < 5; i2++) {
                  double t = i2 / 4.0;
                  double ang = startAngle + sweep * t;
                  double r = hugR * (1.0 + 0.12 * Math.sin(t * Math.PI));
                  this.cx[i2] = bodyX + Math.cos(ang) * r;
                  this.cz[i2] = bodyZ + Math.sin(ang) * r;
                  this.cy[i2] = Math.max(fx.anchor.feetY() + 0.1, Math.min(fx.anchor.feetY() + fx.hit.height, iy + rise * t));
               }

               this.cx[0] = ix;
               this.cy[0] = iy;
               this.cz[0] = iz;
               count = 5;
            }

            int points = Procedural.jagged(this.rng, offset, this.cx, this.cy, this.cz, count, 3, 0.22, false);
            float flicker = 0.75F + 0.25F * this.rng.next();
            int forks = Math.round(this.branches.getFloat() * (1 + this.rng.nextInt(2)));
            int branchOffset = offset + points;

            for (int j = 0; j < forks && points > 4; j++) {
               int from = offset + 2 + this.rng.nextInt(points - 3);
               double bx = Vfx.X[from];
               double by = Vfx.Y[from];
               double bz = Vfx.Z[from];
               double len = (0.2 + 0.25 * this.rng.next()) * size;
               double dx = this.rng.signed();
               double dy = this.rng.signed() * 0.6;
               double dz = this.rng.signed();
               double dl = Math.max(1.0E-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
               int bp = Procedural.bolt(this.rng, branchOffset, bx, by, bz, bx + dx / dl * len, by + dy / dl * len, bz + dz / dl * len, 2, 0.3);
               this.bolt(f, branchOffset, bp, 0.045 * size, a * flicker * 0.8F, (float)(from - offset) / points);
               if (this.tips < 16) {
                  int tip = branchOffset + bp - 1;
                  this.tipX[this.tips] = Vfx.X[tip];
                  this.tipY[this.tips] = Vfx.Y[tip];
                  this.tipZ[this.tips] = Vfx.Z[tip];
                  this.tips++;
               }

               branchOffset += bp;
            }

            this.bolt(f, offset, points, 0.07 * size, a * flicker, 0.0F);
            offset = branchOffset;
         }

         Neon.flush();
         int c = this.color.getColor(1.0F);

         for (int i = 0; i < this.tips; i++) {
            Vfx.sprite(Textures.GLOW, this.tipX[i], this.tipY[i], this.tipZ[i], 0.14 * size, 0.0F, alpha(c, 0.55F * a));
         }

         for (int var53 = 0; var53 < this.tips; var53++) {
            Vfx.sprite(Textures.SPARK, this.tipX[var53], this.tipY[var53], this.tipZ[var53], 0.06 * size, 0.0F, alpha(Neon.tint(c), a));
         }
      }
   }

   private void bolt(FxFrame f, int offset, int n, double width, float a, float t0) {
      if (n >= 2) {
         for (int i = offset; i < offset + n; i++) {
            float s = (float)(i - offset) / (n - 1);
            Vfx.X[i] = f.rx(Vfx.X[i]);
            Vfx.Y[i] = f.ry(Vfx.Y[i]);
            Vfx.Z[i] = f.rz(Vfx.Z[i]);
            Vfx.W[i] = (float)(width * (1.0F - 0.6F * s));
            Vfx.C[i] = alpha(this.color.getColor(t0 + (1.0F - t0) * s), a);
         }

         Neon.add(offset, n, false);
      }
   }
}
