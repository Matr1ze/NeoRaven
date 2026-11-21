package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.kit.Vines;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class BrambleBloomStyle extends LandingStyleBase {
   private static final float GROW = 0.25F;
   private static final float BLOOM = 0.45F;
   private static final float HOLD = 0.75F;
   private static final float SINK = 1.25F;
   private static final float END = 1.5F;
   private static final float JUMP_GROW = 0.15F;
   private static final float JUMP_WITHER = 0.2F;
   private static final float JUMP_GONE = 0.55F;
   private static final int MAX_RUNNERS = 14;
   private static final int POINTS = 30;
   private static final int MAX_PETALS = 42;
   private static final int NIGHT_RIM = -3549953;
   private static final double THICK = 0.045;
   private static final double ROSE = 0.13;
   private static final double START = 0.06;
   public final ThemeTint tipColor;
   public final ThemeTint roseColor;
   public final SliderSetting runners;
   public final SliderSetting length;
   public final BooleanSetting roses;
   public final SliderSetting thornDensity;
   public final SliderSetting curl;
   private final Vines kit = new Vines(420);
   private final BrambleBloomStyle.Floor floor = new BrambleBloomStyle.Floor();

   public BrambleBloomStyle() {
      super(
         LandingStyle.BRAMBLE_BLOOM,
         1.5F,
         4.3F,
         new ThemeTint()
      );
      this.tipColor = ThemeTint.detail(-1452088);
      this.roseColor = ThemeTint.detail(-5041618);
      this.runners = this.look(this.setting(new SliderSetting("bramblebloom.runners", "Runners", "How many vines burst out.", 3.0, 10.0, 6.0, 1.0)));
      this.length = this.setting(new SliderSetting("bramblebloom.length", "Length", "How far the runners reach.", 1.0, 4.0, 2.0, 0.1).unit(" blocks"));
      this.roses = this.setting(new BooleanSetting("bramblebloom.rosesOn", "Roses", "Roses bloom at the runners' tips.", true));
      this.thornDensity = this.look(
         this.setting(new SliderSetting("bramblebloom.thorns", "Thorn Density", "How thick with thorns the runners are.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.curl = this.setting(new SliderSetting("bramblebloom.curl", "Curl", "How much the runners curl as they go.", 0.0, 100.0, 50.0, 5.0).unit("%"));
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.SOLID.bit();
   }

   @Override
   public void start(LandingFx fx) {
      BrambleBloomStyle.State s = new BrambleBloomStyle.State();
      s.jump = fx.land.jump;
      s.scale = fx.size;
      float k = fx.land.fallScale;
      int n = s.jump ? Math.max(3, Math.round(this.runners.getInt() * 0.6F)) : Math.round(this.runners.getInt() * (0.8F + 0.25F * k));
      s.n = Math.max(3, Math.min(14, n));
      double base = this.length.getFloat() * Math.sqrt(fx.size) * (s.jump ? 0.45 : 1.0);
      double turn = fx.rand(1) * (Math.PI * 2);
      double c = this.curl.getFloat() / 100.0;

      for (int r = 0; r < s.n; r++) {
         double a = turn + (Math.PI * 2) * (r + 0.35 * (fx.rand(10 + r) - 0.5)) / s.n;
         double dir = (r & 1) == 0 ? 1.0 : -1.0;
         double len = base * (0.75 + 0.4 * fx.rand(30 + r));
         double ph = fx.rand(50 + r) * (Math.PI * 2);
         double x = fx.x() + Math.cos(a) * 0.06 * s.scale;
         double z = fx.z() + Math.sin(a) * 0.06 * s.scale;
         double step = len / 29.0;

         for (int j = 0; j < 30; j++) {
            int i = r * 30 + j;
            s.wx[i] = x;
            s.wz[i] = z;
            double u = j / 29.0;
            double th = a + dir * c * (0.45 * u + 2.8 * u * u * u) + 0.22 * Math.sin(u * 7.0 + ph);
            x += Math.cos(th) * step;
            z += Math.sin(th) * step;
         }

         s.delay[r] = 0.04F * fx.rand(70 + r);
         s.rx[r] = Double.NaN;
      }

      fx.data = s;
   }

   private static double grown(LandingFx fx, BrambleBloomStyle.State s, int r) {
      float t = fx.styleTime() - s.delay[r];
      return Easing.outCubic(Easing.clamp01(t / (s.jump ? 0.15F : 0.25F)));
   }

   private static float wither(LandingFx fx, BrambleBloomStyle.State s) {
      float t = fx.styleTime();
      return s.jump ? Easing.smoothstep(0.2F, 0.55F, t) : Easing.smoothstep(0.75F, 1.25F, t);
   }

   private static float sunk(LandingFx fx, BrambleBloomStyle.State s) {
      float t = fx.styleTime();
      return s.jump ? Easing.smoothstep(0.3F, 0.55F, t) : Easing.inCubic(Easing.clamp01((t - 0.75F - 0.1F) / 0.4F));
   }

   private static double bloom(LandingFx fx, BrambleBloomStyle.State s, int r) {
      float t = fx.styleTime() - s.delay[r];
      return Easing.outBack(Easing.clamp01((t - 0.25F) / 0.19999999F), 1.6F);
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      BrambleBloomStyle.State s = (BrambleBloomStyle.State)fx.data;
      if (s != null) {
         float t = fx.styleTime();
         float alpha = fx.alpha();
         if ((!s.jump || !(t >= 0.55F)) && !(alpha <= 0.003F)) {
            Camera cam = Vfx.camera();
            this.floor.hf = fx.ground;
            float w = wither(fx, s);
            float sink = sunk(fx, s);
            boolean vines = sink < 0.999F;
            int points = fx.lod ? 15 : 30;
            if (vines) {
               MeshBatch.begin(Material.CUSTOM, Finish.MATTE, fx.light, 0.1F + 0.55F * fx.night, -3549953, true, true);

               for (int r = 0; r < s.n; r++) {
                  this.runner(fx, f, s, cam, r, points, w, sink, alpha);
               }

               MeshBatch.end();
            }

            MeshBatch.begin(Material.CUSTOM, Finish.SATIN, fx.light, 0.1F + 0.25F * fx.night, -3549953, false, true);
            if (vines) {
               for (int r = 0; r < s.n; r++) {
                  if (!fx.lod) {
                     this.thorns(fx, f, s, cam, r, points, w, alpha);
                  }

                  if (this.roses.get() && !s.jump) {
                     this.rose(fx, f, s, cam, r, w, sink, alpha);
                  }
               }
            }

            if (this.roses.get() && !s.jump && !fx.lod) {
               if (!s.shed && t >= 0.83F) {
                  this.shed(fx, s);
               }

               this.petals(fx, f, s, cam, t, alpha);
            }

            MeshBatch.end();
         }
      }
   }

   private void runner(LandingFx fx, FxFrame f, BrambleBloomStyle.State s, Camera cam, int r, int points, float w, float sink, float alpha) {
      double g = grown(fx, s, r);
      if (g <= 0.01) {
         s.rx[r] = Double.NaN;
      } else {
         int at = r * 30;
         int bark = ColorUtil.lerp(this.color.getColor((float)r / Math.max(1, s.n - 1)), -15856630, 0.6F * w);
         double thick = 0.045 * Math.sqrt(s.scale) * (s.jump ? 0.75 : 1.0) * (1.0 - 0.45 * w);
         boolean mine = fx.land.self && f.firstPerson;

         for (int j = 0; j < points; j++) {
            double u = g * j / (points - 1.0);
            double idx = u * 29.0;
            int j0 = Math.min(28, (int)idx);
            double fr = idx - j0;
            int i0 = at + j0;
            double x = s.wx[i0] + (s.wx[i0 + 1] - s.wx[i0]) * fr;
            double z = s.wz[i0] + (s.wz[i0 + 1] - s.wz[i0]) * fr;
            int i = at + j;
            this.kit.px[i] = f.rx(x);
            this.kit.py[i] = f.ry(fx.y());
            this.kit.pz[i] = f.rz(z);
            double tip = Easing.clamp01((g - u) / 0.08 + (g >= 0.999 ? 0.4 : 0.0));
            double knots = Math.sin(u * 37.0 + r * 3.1) * Math.sin(u * 13.0 + r * 1.7);
            this.kit.rad[i] = thick * (1.0 - 0.55 * u) * (1.0 + 0.15 * knots) * Math.max(0.18, tip);
            int c = ColorUtil.shade(bark, (float)(0.86 + 0.16 * Math.sin(u * 23.0 + r * 2.3)));
            this.kit.col[i] = 0xFF000000 | c & 16777215;
            this.kit.keep[i] = 1.0F;
         }

         double lift = -(thick * 2.3 + 0.012) * sink;
         int n = this.kit.followGround(at, points, this.floor, f.viewX, f.viewY, f.viewZ, lift);
         if (n < 2) {
            s.rx[r] = Double.NaN;
         } else {
            if (mine) {
               for (int j = 0; j < n; j++) {
                  int i = at + j;
                  this.kit.keep[i] = NearCamera.dark(cam, this.kit.px[i], this.kit.py[i], this.kit.pz[i], 1.0F);
               }
            }

            this.kit.groundFrames(at, n);
            this.kit.tube(at, n, false, cam, alpha);
            int e = at + n - 1;
            s.rx[r] = this.kit.px[e] + f.viewX;
            s.ry[r] = this.kit.py[e] + f.viewY;
            s.rz[r] = this.kit.pz[e] + f.viewZ;
            s.rax[r] = this.kit.tx[e];
            s.raz[r] = this.kit.tz[e];
            s.reached[r] = n;
         }
      }
   }

   private void thorns(LandingFx fx, FxFrame f, BrambleBloomStyle.State s, Camera cam, int r, int points, float w, float alpha) {
      float density = this.thornDensity.getFloat() / 100.0F;
      double g = grown(fx, s, r);
      if (!(density <= 0.0F) && !(g <= 0.05) && !Double.isNaN(s.rx[r])) {
         int n = s.reached[r];
         double reach = this.length.getFloat() * Math.sqrt(s.scale) * (s.jump ? 0.45 : 1.0);
         int count = Math.max(1, Math.round(density * 6.0F * (float)reach));
         int tip = this.tipColor.getColor(0.5F);
         double flare = 0.35 + 0.65 * g;

         for (int q = 0; q < count; q++) {
            double u = (q + 0.5 + 0.6 * (hash(r * 31 + q, 3) - 0.5)) / count;
            double atPoint;
            if (!(u < 0.06) && !(u > g - 0.04) && !((atPoint = u / g * (points - 1)) > n - 1.05)) {
               double around = (q % 2 == 0 ? 1 : -1) * (0.35 + 0.9 * hash(r * 13 + q, 7));
               double len = 0.085 * Math.sqrt(s.scale) * flare * (0.8 + 0.4 * hash(r * 17 + q, 23)) * (1.0 - 0.4 * w);
               this.kit.thorn(r * 30, n, atPoint, around, len, tip, w, alpha);
            }
         }
      }
   }

   private void rose(LandingFx fx, FxFrame f, BrambleBloomStyle.State s, Camera cam, int r, float w, float sink, float alpha) {
      float bud = Easing.smoothstep(0.55F, 0.95F, (float)grown(fx, s, r));
      if (!(bud <= 0.01F) && !Double.isNaN(s.rx[r])) {
         double open = Math.max(0.0, bloom(fx, s, r));
         double size = 0.13 * s.scale * (0.85 + 0.3 * hash(r, 41)) * (0.3 + 0.7 * Math.min(1.0, open)) * bud * (1.0 - 0.3 * sink);
         double ax = s.rax[r] * 0.4;
         double ay = 1.0;
         double az = s.raz[r] * 0.4;
         double al = Math.sqrt(ax * ax + ay * ay + az * az);
         int col = ColorUtil.lerp(this.roseColor.getColor(0.5F), -15856630, 0.7F * w) | 0xFF000000;
         double x = f.rx(s.rx[r]);
         double y = f.ry(s.ry[r]) - size * 0.3 * sink;
         double z = f.rz(s.rz[r]);
         float a = alpha;
         if (fx.land.self && f.firstPerson) {
            a = NearCamera.dark(cam, x, y, z, alpha);
         }

         this.kit.rose(fx.seed + r & 7, x, y, z, ax / al, ay / al, az / al, size, Math.min(1.1, open) * (1.0 - 0.35 * w), col, a);
      }
   }

   private void shed(LandingFx fx, BrambleBloomStyle.State s) {
      s.shed = true;

      for (int r = 0; r < s.n && s.petals < 42; r++) {
         if (!Double.isNaN(s.rx[r])) {
            int count = 2 + (hash(r, 61) > 0.5 ? 1 : 0);

            for (int q = 0; q < count && s.petals < 42; q++) {
               double a = (Math.PI * 2) * hash(r * 7 + q, 67);
               double d = (0.08 + 0.22 * hash(r * 7 + q, 71)) * Math.sqrt(s.scale);
               double x = s.rx[r] + Math.cos(a) * d;
               double z = s.rz[r] + Math.sin(a) * d;
               double y = Ground.y(fx.ground, x, z);
               if (Ground.lastValid()) {
                  int i = s.petals++;
                  s.px[i] = x;
                  s.py[i] = y + 0.012;
                  s.pz[i] = z;
                  s.spin[i] = (Math.PI * 2) * hash(r * 7 + q, 73);
                  s.tumble[i] = 0.08 + 0.25 * hash(r * 7 + q, 79);
                  s.psize[i] = 0.13 * s.scale * (0.45 + 0.2 * hash(r * 7 + q, 83));
               }
            }
         }
      }
   }

   private void petals(LandingFx fx, FxFrame f, BrambleBloomStyle.State s, Camera cam, float t, float alpha) {
      if (s.petals != 0) {
         float a = alpha * Easing.smoothstep(0.83F, 0.95F, t) * (1.0F - Easing.smoothstep(1.25F, 1.5F, t));
         if (!(a <= 0.003F)) {
            int petal = ColorUtil.lerp(this.roseColor.getColor(0.5F), -15856630, 0.3F) | 0xFF000000;
            boolean mine = fx.land.self && f.firstPerson;

            for (int i = 0; i < s.petals; i++) {
               double x = f.rx(s.px[i]);
               double y = f.ry(s.py[i]);
               double z = f.rz(s.pz[i]);
               this.kit.tumblingPetal(x, y, z, s.spin[i], s.tumble[i], s.psize[i], petal, mine ? NearCamera.dark(cam, x, y, z, a) : a);
            }
         }
      }
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8) / 1.6777216E7;
   }

   private static final class Floor implements Surface {
      Terrain.HeightField hf;

      private Floor() {
      }

      @Override
      public double at(double x, double y, double z) {
         return this.hf.at(x, z);
      }
   }

   private static final class State {
      int n;
      int petals;
      boolean jump;
      boolean shed;
      double scale;
      final double[] wx = new double[420];
      final double[] wz = new double[420];
      final float[] delay = new float[14];
      final int[] reached = new int[14];
      final double[] rx = new double[14];
      final double[] ry = new double[14];
      final double[] rz = new double[14];
      final double[] rax = new double[14];
      final double[] raz = new double[14];
      final double[] px = new double[42];
      final double[] py = new double[42];
      final double[] pz = new double[42];
      final double[] spin = new double[42];
      final double[] tumble = new double[42];
      final double[] psize = new double[42];

      private State() {
      }
   }
}
