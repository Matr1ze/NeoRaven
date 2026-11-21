package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Liquid;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class LiquidChromeEffect extends AuraEffect {
   private static final int MAX_DROPS = 6;
   private static final int INTRO_DROPLETS = 4;
   private static final int OUTRO_DROPLETS = 5;
   private static final int MAX_BALLS = 36;
   private static final double SPAN = 1.72;
   private static final double HALF = 1.05;
   private static final double BOTTOM = -0.65;
   private static final double TOP = 2.25;
   private static final float RIM = 0.24F;
   private static final double RING_EVERY = 6.0;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting drops;
   public final SliderSetting merge;
   public final SliderSetting wobble;
   public final BooleanSetting ringMoments;
   public final ModeSetting<LiquidChromeEffect.Quality> quality;
   private final Liquid.Field liquid = new Liquid.Field(36);
   private double ringY;
   private double ringRadius;
   private double ringRho;
   private double ringWeight;
   private double ripple;
   private double rippleCos;
   private double rippleSin;
   private double reachRatio;
   private final double[] orbitAt = new double[3];
   private final double[] orbitDir = new double[3];

   public LiquidChromeEffect() {
      super(
         EffectType.LIQUID_CHROME,
         "liquidchrome",
         new ThemeTint(),
         true
      );
      this.material = this.materialSetting("liquidchrome", Material.CHROME, "What the drops are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("liquidchrome", Finish.METALLIC, this.material);
      this.drops = this.look(this.setting(new SliderSetting("liquidchrome.drops", "Drops", "How many drops float round the target.", 2.0, 6.0, 4.0, 1.0)));
      this.merge = this.look(
         this.setting(
            new SliderSetting("liquidchrome.merge", "Merge Strength", "How far apart two drops start to flow into one.", 0.0, 100.0, 60.0, 5.0).unit("%")
         )
      );
      this.wobble = this.look(
         this.setting(new SliderSetting("liquidchrome.wobble", "Wobble", "How much the drops' surfaces wobble.", 0.0, 100.0, 40.0, 5.0).unit("%"))
      );
      this.ringMoments = this.setting(
         new BooleanSetting(
            "liquidchrome.rings", "Ring Moments", "Every few seconds the drops gather into a rippling chrome ring round the waist, then break apart.", true
         )
      );
      this.quality = this.setting(
         new ModeSetting<>(
            "liquidchrome.quality",
            "Quality",
            "How fine the drops' surface is: Low, Medium or High (a little more work each frame).",
            LiquidChromeEffect.Quality.class,
            LiquidChromeEffect.Quality.MEDIUM
         )
      );
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      boolean glass = this.material.get().translucent(this.finish.get());
      if (pass == FxPass.SOLID && !glass || pass == FxPass.TRANSLUCENT && glass) {
         float alpha = this.intensity(c);
         if (alpha <= 0.003F) {
            return;
         }

         this.balls(c);
         if (this.liquid.balls() == 0 && this.ringWeight <= 0.0) {
            return;
         }

         Material m = this.material.get();
         MeshBatch.begin(m, this.finish.get(), c.light, 0.24F, -1, true, !glass);
         double s = Math.max(0.75, (double)this.size(c));
         double h = 1.72 * s / this.quality.get().cells;
         int half = (int)Math.ceil(1.05 * s / h);
         int below = (int)Math.ceil(0.65 / h);
         int above = (int)Math.ceil(2.25 * s / h);
         this.liquid.bounds(c.x - half * h, c.y - below * h, c.z - half * h, c.x + half * h, c.y + above * h, c.z + half * h);
         Camera cam = c.cam != null ? c.cam : Vfx.camera();
         this.liquid.mesh(h, c.x, c.y, c.z, cam.distanceSq(c.x, c.y + 1.0, c.z), alpha, m == Material.CUSTOM ? this.color(c, 0.5F) : -1, c.keep);
         MeshBatch.end();
      }
   }

   private void balls(EffectContext c) {
      double s = this.size(c);
      double t = c.time;
      double spd = this.speed();
      int n = this.drops.getInt();
      this.reachRatio = 1.5 + 0.9 * this.merge.getFloat() / 100.0;
      this.liquid.begin(this.reachRatio);
      double cy = c.y + 1.0;
      double orbit = 0.66 * (0.7 + 0.3 * s);
      double wob = this.wobble.getFloat() / 100.0;
      float out = c.outro;
      double phase = t % 6.0;
      double gather = 0.0;
      double ringIn = 0.0;
      if (this.ringMoments.get() && t > 3.0) {
         gather = Easing.smoothstep(4.0F, 4.6F, (float)phase) * (1.0F - Easing.smoothstep(5.3F, 6.0F, (float)phase));
         ringIn = Easing.smoothstep(4.35F, 4.7F, (float)phase) * (1.0F - Easing.smoothstep(5.15F, 5.6F, (float)phase));
      }

      this.ringY = c.y + 0.95;
      this.ringRadius = 0.58 * (0.8 + 0.2 * s);
      this.ringRho = 0.085 * s;
      this.ringWeight = ringIn * (1.0 - Easing.smoothstep(0.0F, 0.35F, out));
      double rippleAt = t * 6.0 * spd;
      this.rippleCos = Math.cos(rippleAt);
      this.rippleSin = Math.sin(rippleAt);
      this.ripple = 0.03 * s * (0.5 + wob);
      float grown = 0.3F + 0.7F * Easing.smoothstep(0.1F, 0.4F, (float)t);
      float rush = Easing.inQuad(Easing.clamp01(t / 0.4));
      float splash = Easing.smoothstep(0.0F, 0.35F, out);
      if (this.ringWeight > 0.0) {
         this.liquid.ring(c.x, this.ringY, c.z, this.ringRadius, this.ringRho, this.ringWeight, this.ripple, this.rippleCos, this.rippleSin);
      }

      for (int i = 0; i < n; i++) {
         this.orbit(i, n, t, orbit, spd);
         double x = c.x + this.orbitAt[0];
         double y = cy + this.orbitAt[1];
         double z = c.z + this.orbitAt[2];
         if (gather > 0.0) {
            double ra = (Math.PI * 2) * i / n + t * 0.8 * spd;
            x += (c.x + Math.cos(ra) * this.ringRadius - x) * gather;
            y += (this.ringY - y) * gather;
            z += (c.z + Math.sin(ra) * this.ringRadius - z) * gather;
         }

         double r = 0.15 * s * (0.92 + 0.16 * hash(i, 7)) * grown * (1.0 - 0.45 * ringIn) * (1.0 - splash);
         double squash = 1.0 - wob * (0.14 + 0.1 * Math.sin(t * 5.3 * spd + i * 1.7));
         if (r > 0.001) {
            this.add(x, y, z, r, 1.0, this.orbitDir[0], this.orbitDir[1], this.orbitDir[2], squash);
         }

         if (t < 0.45) {
            for (int q = 0; q < 4; q++) {
               double a = (Math.PI * 2) * hash(i * 7 + q, 11);
               double e = (hash(i * 7 + q, 13) - 0.5) * 1.6;
               double far = 0.9 * s * (1.0 - rush);
               this.add(x + Math.cos(a) * Math.cos(e) * far, y + Math.sin(e) * far, z + Math.sin(a) * Math.cos(e) * far, 0.07 * s, 1.0, 0.0, 1.0, 0.0, 1.0);
            }
         }

         if (out > 0.0F) {
            for (int q = 0; q < 5; q++) {
               double a = (Math.PI * 2) * (q + hash(i * 5 + q, 17) * 0.6) / 5.0;
               double up = 0.25 + 0.5 * hash(i * 5 + q, 19);
               double fly = 0.35 * s * out;
               double dr = 0.08 * s * (0.8 + 0.4 * hash(i * 5 + q, 23)) * (1.0 - Easing.smoothstep(0.45F, 1.0F, out)) * splash;
               if (dr > 0.001) {
                  this.add(x + Math.cos(a) * fly, y + up * fly - 1.1 * out * out, z + Math.sin(a) * fly, dr, 1.0, 0.0, 1.0, 0.0, 1.0);
               }
            }
         }
      }
   }

   private void orbit(int i, int n, double t, double rr, double spd) {
      int pairs = (n + 1) / 2;
      int p = i >> 1;
      int m = i & 1;
      double tilt = Math.toRadians(28.0F + 18.0F * hash(p, 3));
      double dirA = (Math.PI * 2) * p / pairs + 0.4;
      double ux = Math.cos(dirA);
      double uz = Math.sin(dirA);
      double vx = -uz * Math.cos(tilt);
      double vy = Math.sin(tilt);
      double vz = ux * Math.cos(tilt);
      double w = (1.0 + 0.15 * p) * spd * (m == 0 ? 1.0 : -0.92);
      double th = w * t + m * Math.PI + p * 1.1;
      double ct = Math.cos(th);
      double st = Math.sin(th);
      double r = rr * (m == 0 ? 1.035 : 0.965);
      this.orbitAt[0] = (ux * ct + vx * st) * r;
      this.orbitAt[1] = vy * st * r + 0.05 * Math.sin(t * 1.3 * spd + i);
      this.orbitAt[2] = (uz * ct + vz * st) * r;
      this.orbitDir[0] = -ux * st + vx * ct;
      this.orbitDir[1] = vy * ct;
      this.orbitDir[2] = -uz * st + vz * ct;
   }

   public double meetForTest(int n, double apart) {
      double rr = 0.66;
      double near = 0.15 * apart;
      double[] p = new double[3 * n];
      double before = Double.MAX_VALUE;

      for (double t = 1.5; t < 15.0; t += 0.01) {
         for (int i = 0; i < n; i++) {
            this.orbit(i, n, t, rr, 1.0);
            p[3 * i] = this.orbitAt[0];
            p[3 * i + 1] = this.orbitAt[1];
            p[3 * i + 2] = this.orbitAt[2];
         }

         double d = Double.MAX_VALUE;

         for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
               double dx = p[3 * i] - p[3 * j];
               double dy = p[3 * i + 1] - p[3 * j + 1];
               double dz = p[3 * i + 2] - p[3 * j + 2];
               d = Math.min(d, Math.sqrt(dx * dx + dy * dy + dz * dz));
            }
         }

         if (d <= near && before > near && t > 1.5) {
            return t;
         }

         before = d;
      }

      return -1.0;
   }

   private void add(double x, double y, double z, double r, double weight, double sx, double sy, double sz, double squash) {
      this.liquid.ball(x, y, z, r, weight, sx, sy, sz, squash);
   }

   public String stateForTest() {
      String how = this.liquid.lastMode == 0 ? "meshed" : (this.liquid.lastMode == 1 ? "its last mesh" : (this.liquid.lastMode == 2 ? "spheres" : "nothing"));
      return String.format(
         Locale.ROOT,
         "%s; %d balls, ring %.2f, grid %d points, drawn: %s (%d vertices, %d triangles)",
         Liquid.stateForTest(),
         this.liquid.balls(),
         this.ringWeight,
         this.liquid.lastPoints,
         how,
         this.liquid.lastVertices,
         this.liquid.lastTriangles
      );
   }

   public static enum Quality implements Displayable {
      LOW("Low", "16 cells across", 16),
      MEDIUM("Medium", "24 cells across", 24),
      HIGH("High", "32 cells across", 32);

      private final String displayName;
      private final String description;
      final int cells;

      private Quality(String displayName, String description, int cells) {
         this.displayName = displayName;
         this.description = description;
         this.cells = cells;
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
