package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Liquid;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ChromeSplashStyle extends LandingStyleBase {
   private static final float RISE = 0.2F;
   private static final float BREAK = 0.4F;
   private static final float MERGED = 0.8F;
   private static final float END = 1.3F;
   private static final float JUMP_END = 0.8F;
   private static final int MAX_DROPLETS = 16;
   private static final int MAX_BALLS = 36;
   private static final int ROWS = 6;
   private static final int MAX_SEGS = 128;
   private static final double CROWN_R = 0.4;
   private static final double CROWN_H = 0.5;
   private static final double FLARE = 0.35;
   private static final double WALL = 0.03;
   private static final double DROP = 0.065;
   private static final double PUDDLE = 0.13;
   private static final double POOL = 0.5;
   private static final double POOL_CELL = 0.17;
   private static final double GRAVITY = 12.0;
   private static final float RIM = 0.22F;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting crownHeight;
   public final SliderSetting droplets;
   public final BooleanSetting puddles;
   public final SliderSetting merge;
   public final ModeSetting<ChromeSplashStyle.Quality> quality;
   private final double[] ringX = new double[128];
   private final double[] ringZ = new double[128];
   private final double[] ringBase = new double[128];

   public ChromeSplashStyle() {
      super(
         LandingStyle.CHROME_SPLASH,
         1.3F,
         2.0F,
         new ThemeTint()
      );
      this.material = this.materialSetting("chromesplash", Material.CHROME, "What the liquid is made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("chromesplash", Finish.METALLIC, this.material);
      this.crownHeight = this.look(
         this.setting(new SliderSetting("chromesplash.crownHeight", "Crown Height", "How tall the crown rises.", 50.0, 150.0, 100.0, 5.0).unit("%"))
      );
      this.droplets = this.look(
         this.setting(new SliderSetting("chromesplash.droplets", "Droplets", "How many droplet-tipped spikes the rim breaks into.", 4.0, 16.0, 10.0, 1.0))
      );
      this.puddles = this.setting(
         new BooleanSetting("chromesplash.puddles", "Puddles", "The droplets fall back into mirror puddles that ripple and shrink away.", true)
      );
      this.merge = this.setting(
         new SliderSetting("chromesplash.merge", "Merge Strength", "How far apart two drops start to flow into one.", 0.0, 100.0, 60.0, 5.0).unit("%")
      );
      this.quality = this.setting(
         new ModeSetting<>(
            "chromesplash.quality",
            "Quality",
            "How fine the liquid's surface is: Low, Medium or High (a little more work each frame).",
            ChromeSplashStyle.Quality.class,
            ChromeSplashStyle.Quality.MEDIUM
         )
      );
   }

   @Override
   public int passes(LandingFx fx) {
      int body = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return fx.lod ? body : body | FxPass.GLOW.bit();
   }

   @Override
   public void start(LandingFx fx) {
      ChromeSplashStyle.State s = new ChromeSplashStyle.State();
      s.jump = fx.land.jump;
      s.scale = fx.size;
      s.crown = 0.5 * this.crownHeight.getFloat() / 100.0 * Math.sqrt(fx.size) * Math.min(1.25F, 0.8F + 0.25F * fx.land.fallScale);
      int n = s.jump ? 3 : Math.round(this.droplets.getInt() * Math.min(1.3F, 0.85F + 0.2F * fx.land.fallScale));
      s.n = Math.max(3, Math.min(16, n));
      s.turn = fx.rand(1) * (Math.PI * 2);
      double r = 0.4 * s.scale * (s.jump ? 0.6 : 1.0);

      for (int i = 0; i < s.n; i++) {
         s.angle[i] = s.turn + (Math.PI * 2) * i / s.n;
         s.breakAt[i] = s.jump ? 0.05F + 0.05F * fx.rand(10 + i) : 0.2F + 0.2F * (0.15F + 0.85F * fx.rand(10 + i));
         s.vOut[i] = (s.jump ? 0.6 : 0.9 + 0.6 * fx.rand(30 + i)) * Math.sqrt(s.scale);
         s.vUp[i] = (s.jump ? 1.3 : 1.1 + 0.6 * fx.rand(50 + i)) * Math.sqrt(s.scale);
         s.size[i] = 0.065 * Math.sqrt(s.scale) * (0.8 + 0.4 * fx.rand(70 + i));
         s.phase[i] = fx.rand(90 + i) * (Math.PI * 2);
         double tipR = r + 0.35 * s.crown;
         double tipH = s.jump ? 0.05 : s.crown * 1.1;
         double ca = Math.cos(s.angle[i]);
         double sa = Math.sin(s.angle[i]);
         s.tipX[i] = fx.x() + ca * tipR;
         s.tipZ[i] = fx.z() + sa * tipR;
         s.tipY[i] = fx.y() + tipH;
         double tFall = flightTime(s.vUp[i], tipH);
         double lx = s.tipX[i] + ca * s.vOut[i] * tFall;
         double lz = s.tipZ[i] + sa * s.vOut[i] * tFall;
         double gy = Ground.y(fx.ground, lx, lz);
         s.landX[i] = lx;
         s.landZ[i] = lz;
         s.landY[i] = Ground.lastValid() ? gy : Double.NaN;
         s.landAt[i] = s.breakAt[i] + (float)tFall;
      }

      fx.data = s;
   }

   private static double flightTime(double vUp, double h) {
      return (vUp + Math.sqrt(vUp * vUp + 24.0 * Math.max(0.0, h))) / 12.0;
   }

   private static double crownUp(float t) {
      return t < 0.2F ? Easing.outBack(Easing.clamp01(t / 0.2F), 1.4F) : 1.0 - Easing.inOutCubic(Easing.clamp01((t - 0.2F - 0.05F) / 0.55F));
   }

   private static double shrink(float t) {
      return 1.0 - Easing.inCubic(Easing.clamp01((t - 0.8F) / 0.49999994F));
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      ChromeSplashStyle.State s = (ChromeSplashStyle.State)fx.data;
      if (s != null) {
         float t = fx.styleTime();
         float alpha = fx.alpha();
         if (!(alpha <= 0.003F) && (!s.jump || !(t >= 0.8F))) {
            if (pass == FxPass.GLOW) {
               this.glints(fx, f, s, t, alpha);
            } else {
               boolean custom = this.material.get() == Material.CUSTOM;
               int tint = custom ? this.color.getColor(0.5F) | 0xFF000000 : -1;
               MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.22F, -1, false, pass == FxPass.SOLID);

               try {
                  if (!s.jump) {
                     this.crown(fx, f, s, t, tint, alpha);
                  }

                  this.liquid(fx, f, s, t, tint, alpha);
               } finally {
                  MeshBatch.end();
               }
            }
         }
      }
   }

   private void crown(LandingFx fx, FxFrame f, ChromeSplashStyle.State s, float t, int tint, float alpha) {
      double up = crownUp(t);
      if (!(up <= 0.01)) {
         int per = fx.lod ? 3 : this.quality.get().perSpike;
         int segs = Math.min(128, s.n * per);
         double r = 0.4 * s.scale * (1.0 + 0.25 * Easing.clamp01((t - 0.2F) / 0.6F));
         double h = s.crown * up;

         for (int k2 = 0; k2 < segs; k2++) {
            double th = s.turn + (Math.PI * 2) * k2 / segs;
            this.ringX[k2] = Math.cos(th);
            this.ringZ[k2] = Math.sin(th);
            double gy = Ground.y(fx.ground, fx.x() + this.ringX[k2] * r, fx.z() + this.ringZ[k2] * r);
            this.ringBase[k2] = Ground.lastValid() ? gy : fx.y();
         }

         int vc = Math.round(255.0F * Easing.clamp01(alpha * ((tint >>> 24) / 255.0F))) << 24 | tint & 16777215;
         int base = MeshBatch.stage(segs * 6 * 2);

         for (int k = 0; k < segs; k++) {
            double th = (Math.PI * 2) * k / segs;
            double c = 0.5 + 0.5 * Math.cos(s.n * th);
            double spike = c * c * c * c * c * c * c * c;
            double top = h * (0.6 + 0.55 * spike);

            for (int j = 0; j < 6; j++) {
               double v = j / 5.0;
               double y = top * v;
               double rr = r + 0.35 * s.crown * up * v * v;
               double slope = 0.7 * s.crown * up * v / Math.max(0.001, top);
               double nl = Math.sqrt(1.0 + slope * slope);
               double nx = this.ringX[k] / nl;
               double ny = -slope / nl;
               double nz = this.ringZ[k] / nl;
               double wall = 0.03 * Math.sqrt(s.scale) * (1.0 - 0.6 * v);
               double wx = fx.x() + this.ringX[k] * rr;
               double wz = fx.z() + this.ringZ[k] * rr;
               double wy = this.ringBase[k] + y;
               int o = base + (k * 6 + j) * 2;
               MeshBatch.vertex(o, f.rx(wx), f.ry(wy), f.rz(wz), nx, ny, nz, vc, 0.0F);
               MeshBatch.vertex(o + 1, f.rx(wx - this.ringX[k] * wall), f.ry(wy), f.rz(wz - this.ringZ[k] * wall), -nx, -ny, -nz, vc, 0.0F);
            }
         }

         for (int var52 = 0; var52 < segs; var52++) {
            int k2 = (var52 + 1) % segs;

            for (int j = 0; j < 5; j++) {
               int a = base + (var52 * 6 + j) * 2;
               int b = base + (k2 * 6 + j) * 2;
               int a2 = a + 2;
               int b2 = b + 2;
               MeshBatch.quad(a, b, b2, a2);
               MeshBatch.quad(a + 1, a2 + 1, b2 + 1, b + 1);
            }

            int a = base + (var52 * 6 + 6 - 1) * 2;
            int b = base + (k2 * 6 + 6 - 1) * 2;
            MeshBatch.quad(a, a + 1, b + 1, b);
         }
      }
   }

   private void liquid(LandingFx fx, FxFrame f, ChromeSplashStyle.State s, float t, int tint, float alpha) {
      Liquid.Field fl = s.drops;
      Liquid.Field pl = s.pool;
      fl.plain = fx.lod;
      pl.plain = fx.lod;
      double reach = 1.5 + 0.9 * this.merge.getFloat() / 100.0;
      fl.begin(reach);
      pl.begin(reach);
      double up = crownUp(t);
      double shrink = shrink(t);
      boolean pud = this.puddles.get();
      double r = 0.4 * s.scale;

      for (int i = 0; i < s.n; i++) {
         double ca = Math.cos(s.angle[i]);
         double sa = Math.sin(s.angle[i]);
         if (t < s.breakAt[i]) {
            if (!s.jump && !(up <= 0.05)) {
               double tipR = r + 0.35 * s.crown * up;
               double tipH = s.crown * up * 1.15;
               double x = fx.x() + ca * tipR;
               double z = fx.z() + sa * tipR;
               double gy2 = Ground.y(fx.ground, x, z);
               double grow = Easing.clamp01((t - 0.120000005F) / 0.120000005F);
               fl.ball(f.rx(x), f.ry(gy2 + tipH + s.size[i] * 0.6), f.rz(z), s.size[i] * grow, 1.0, 0.0, 1.0, 0.0, 0.8);
            }
         } else if (t < s.landAt[i]) {
            double dt = t - s.breakAt[i];
            double x = s.tipX[i] + ca * s.vOut[i] * dt;
            double z = s.tipZ[i] + sa * s.vOut[i] * dt;
            double y = s.tipY[i] + s.vUp[i] * dt - 6.0 * dt * dt;
            double vy = s.vUp[i] - 12.0 * dt;
            fl.ball(f.rx(x), f.ry(y), f.rz(z), s.size[i], 1.0, ca * s.vOut[i], vy, sa * s.vOut[i], 0.8);
         } else if (pud && !Double.isNaN(s.landY[i])) {
            double since = t - s.landAt[i];
            double spread = Easing.outCubic(Easing.clamp01((float)(since / 0.12)));
            double ripple = 1.0 + 0.08 * Math.sin(since * 22.0 + s.phase[i]) * Math.exp(-since * 3.0);
            double pr = 0.13 * Math.sqrt(s.scale) * (s.jump ? 0.6 : 1.0) * (0.5 + 0.5 * spread) * ripple * shrink;
            if (pr > 0.01) {
               pl.ball(f.rx(s.landX[i]), f.ry(s.landY[i] + pr * 0.08), f.rz(s.landZ[i]), pr, 1.0, 0.0, 1.0, 0.0, 4.0);
            }
         }
      }

      if (pud || !s.jump) {
         double pool = s.jump ? 0.0 : Easing.smoothstep(0.2F, 0.8F, t) * shrink;
         if (pool > 0.02) {
            double pr = 0.5 * s.scale * (0.6 + 0.4 * pool) * pool;
            double gy = Ground.y(fx.ground, fx.x(), fx.z());
            if (Ground.lastValid()) {
               pl.ball(f.rx(fx.x()), f.ry(gy + pr * 0.06), f.rz(fx.z()), pr, 1.0, 0.0, 1.0, 0.0, 5.0);
            }
         }
      }

      double ringK = s.jump ? 1.0 - Easing.smoothstep(0.3F, 0.8F, t) : Easing.smoothstep(0.7F, 0.90000004F, t) * shrink;
      if (ringK > 0.02) {
         double rr = s.jump
            ? (0.15 + 0.5 * Easing.outCubic(Easing.clamp01(t / 0.5F))) * s.scale
            : 0.5 * s.scale * (0.9 + 0.5 * Easing.clamp01((t - 0.8F) / 0.49999994F));
         double gy = Ground.y(fx.ground, fx.x(), fx.z());
         pl.ring(f.rx(fx.x()), f.ry(gy + 0.01), f.rz(fx.z()), rr, 0.035 * Math.sqrt(s.scale), ringK, 0.012, Math.cos(t * 9.0), Math.sin(t * 9.0));
      }

      double q = this.quality.get().cell;
      double root = Math.sqrt(s.scale);
      double distSq = f.distanceSqToEye(fx.x(), fx.y(), fx.z());
      if (fl.balls() > 0) {
         fl.mesh(0.065 * root * q, f.rx(fx.x()), f.ry(fx.y()), f.rz(fx.z()), distSq, alpha, tint, null);
      }

      if (pl.balls() > 0 || ringK > 0.02) {
         pl.mesh(0.17 * root * q, f.rx(fx.x()), f.ry(fx.y()), f.rz(fx.z()), distSq, alpha, tint, null);
      }
   }

   private void glints(LandingFx fx, FxFrame f, ChromeSplashStyle.State s, float t, float alpha) {
      if (!s.jump) {
         float peak = Easing.smoothstep(0.14F, 0.2F, t) * (1.0F - Easing.smoothstep(0.4F, 0.5F, t));
         if (!(peak <= 0.01F)) {
            RenderState.additive();
            double up = crownUp(t);
            double r = 0.4 * s.scale + 0.35 * s.crown * up;

            for (int i = 0; i < s.n; i++) {
               if (!(t >= s.breakAt[i] + 0.06F)) {
                  double x = fx.x() + Math.cos(s.angle[i]) * r;
                  double z = fx.z() + Math.sin(s.angle[i]) * r;
                  double y = Ground.y(fx.ground, x, z) + s.crown * up * 1.15 + s.size[i];
                  float tw = 0.6F + 0.4F * (float)Math.sin(t * 40.0 + s.phase[i]);
                  Vfx.sprite(
                     Textures.SPARKLE,
                     f.rx(x),
                     f.ry(y),
                     f.rz(z),
                     0.22 * Math.sqrt(s.scale),
                     (float)s.phase[i],
                     ColorUtil.mulAlpha(-1, alpha * peak * tw * 0.8F)
                  );
               }
            }
         }
      }
   }

   private static String mode(Liquid.Field fl) {
      return fl.lastMode == 0 ? "meshed" : (fl.lastMode == 1 ? "reused" : (fl.lastMode == 2 ? "spheres" : "none"));
   }

   public static enum Quality implements Displayable {
      LOW("Low", "Coarse drops, the least work", 0.9, 4),
      MEDIUM("Medium", "Round drops", 0.65, 6),
      HIGH("High", "The smoothest surface", 0.5, 8);

      private final String displayName;
      private final String description;
      final double cell;
      final int perSpike;

      private Quality(String displayName, String description, double cell, int perSpike) {
         this.displayName = displayName;
         this.description = description;
         this.cell = cell;
         this.perSpike = perSpike;
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

   private static final class State {
      final Liquid.Field drops = new Liquid.Field(18);
      final Liquid.Field pool = new Liquid.Field(36);
      boolean jump;
      int n;
      double scale;
      double crown;
      double turn;
      final double[] angle = new double[16];
      final double[] vOut = new double[16];
      final double[] vUp = new double[16];
      final float[] breakAt = new float[16];
      final float[] landAt = new float[16];
      final double[] landX = new double[16];
      final double[] landY = new double[16];
      final double[] landZ = new double[16];
      final double[] tipX = new double[16];
      final double[] tipY = new double[16];
      final double[] tipZ = new double[16];
      final double[] phase = new double[16];
      final double[] size = new double[16];

      private State() {
      }
   }
}
