package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.kit.Vines;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ThornCrownEffect extends AuraEffect {
   private static final int MAX_VINES = 4;
   private static final int VINE_POINTS = 28;
   private static final int CROWN_POINTS = 44;
   private static final int CROWN_AT = 112;
   private static final int PATH_POINTS = 200;
   private static final int NIGHT_RIM = -3549953;
   private static final int WITHERED = -15856630;
   public final ThemeTint thornColor;
   public final ThemeTint vineColor;
   public final SliderSetting vines;
   public final SliderSetting thornDensity;
   public final SliderSetting roses;
   public final BooleanSetting crown;
   public final SliderSetting height;
   public final SliderSetting sway;
   public final BooleanSetting petals;
   private final Vines kit = new Vines(200);
   private final double[] px;
   private final double[] py;
   private final double[] pz;
   private final double[] tx;
   private final double[] ty;
   private final double[] tz;
   private final double[] nx;
   private final double[] ny;
   private final double[] nz;
   private final double[] bx;
   private final double[] by;
   private final double[] bz;
   private final double[] rad;
   private final int[] col;
   private final float[] keepK;
   private final double[] vineGrown;
   private final int[] strandPoints;
   private final float[] wind;
   private double s;
   private double radial;
   private double rootY;
   private double crownY;
   private double crownR;
   private double grow;
   private double crownGrow;
   private double bloom;
   private double wither;
   private double turn;
   private double swayX;
   private double swayZ;
   private double air;
   private double tiltX;
   private double tiltZ;
   private int vineCount;
   private boolean crownOn;

   public ThornCrownEffect() {
      super(
         EffectType.THORN_CROWN,
         "thorncrown",
         new ThemeTint(),
         true
      );
      this.px = this.kit.px;
      this.py = this.kit.py;
      this.pz = this.kit.pz;
      this.tx = this.kit.tx;
      this.ty = this.kit.ty;
      this.tz = this.kit.tz;
      this.nx = this.kit.nx;
      this.ny = this.kit.ny;
      this.nz = this.kit.nz;
      this.bx = this.kit.bx;
      this.by = this.kit.by;
      this.bz = this.kit.bz;
      this.rad = this.kit.rad;
      this.col = this.kit.col;
      this.keepK = this.kit.keep;
      this.vineGrown = new double[4];
      this.strandPoints = new int[2];
      this.wind = new float[3];
      this.thornColor = ThemeTint.detail(-1452088);
      this.vineColor = new ThemeTint();
      this.vines = this.look(this.setting(new SliderSetting("thorncrown.vines", "Vines", "How many vines climb the body.", 1.0, 4.0, 3.0, 1.0)));
      this.thornDensity = this.look(
         this.setting(new SliderSetting("thorncrown.thornDensity", "Thorn Density", "How thorny the vines and the crown are.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.roses = this.look(this.setting(new SliderSetting("thorncrown.roses", "Roses", "Roses blooming on the crown.", 0.0, 6.0, 4.0, 1.0)));
      this.crown = this.setting(new BooleanSetting("thorncrown.crown", "Crown", "The ring of thorns round the head; off: the vines only.", true));
      this.height = this.setting(
         new SliderSetting("thorncrown.height", "Height", "How high the vines climb, as a share of the target's height.", 50.0, 120.0, 100.0, 5.0).unit("%")
      );
      this.sway = this.look(
         this.setting(new SliderSetting("thorncrown.sway", "Wind Sway", "How much the vines sway in the wind.", 0.0, 100.0, 40.0, 5.0).unit("%"))
      );
      this.petals = this.setting(
         new BooleanSetting("thorncrown.petals", "Falling Petals", "Now and then a petal falls from a rose and drifts on the wind.", true)
      );
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.SOLID) {
         float alpha = this.intensity(c);
         if (alpha <= 0.003F) {
            return;
         }

         this.layout(c);
         this.paths(c);
         Camera cam = c.cam != null ? c.cam : Vfx.camera();
         float rim = 0.1F + 0.55F * c.night;
         MeshBatch.begin(Material.CUSTOM, Finish.MATTE, c.light, rim, -3549953, true, true);

         for (int i = 0; i < this.vineCount; i++) {
            this.tube(c, i * 28, 28, false, cam, alpha);
         }

         if (this.crownOn) {
            for (int k = 0; k < 2; k++) {
               if (this.strandPoints[k] >= 2) {
                  this.tube(c, 112 + k * 44, this.strandPoints[k], this.crownGrow >= 1.0, cam, alpha);
               }
            }
         }

         MeshBatch.end();
         MeshBatch.begin(Material.CUSTOM, Finish.SATIN, c.light, 0.1F + 0.25F * c.night, -3549953, false, true);
         this.thorns(c, alpha);
         if (this.crownOn) {
            this.roses(c, alpha);
         }

         MeshBatch.end();
      } else if (pass == FxPass.GLOW && c.night > 0.01F && this.crown.get()) {
         this.layout(c);
         this.nightHalo(c);
      }
   }

   private void layout(EffectContext c) {
      this.s = this.size(c);
      this.radial = 0.8 + 0.2 * this.s;
      this.vineCount = this.vines.getInt();
      this.crownOn = this.crown.get();
      double t = c.time;
      this.wither = Easing.clamp01(c.outro);
      this.grow = 0.06 + 0.94 * Easing.outCubic(Easing.clamp01(t / 0.5));
      this.crownGrow = Easing.outCubic(Easing.clamp01((t - 0.3) / 0.25));
      this.bloom = Easing.outCubic(Easing.clamp01((t - 0.5) / 0.2));
      this.turn = t * this.speed() * Math.toRadians(6.0);
      this.air = Easing.smoothstep(0.05F, 0.4F, (float)(c.y - c.groundY));
      double ground = c.groundY - 0.04;
      double top = (c.headTop - 0.09) * this.height.getFloat() / 100.0;
      double sink = Easing.inCubic((float)this.wither) * (top + 0.35);
      this.rootY = ground + (c.y - 0.5 - ground) * this.air - sink;
      this.crownY = c.y + top - sink;
      this.crownR = this.radial * 0.38;
      double wx = 1.1 + 0.5 * Math.sin(t * 0.7);
      double wz = 0.6 + 0.4 * Math.sin(t * 0.53 + 1.3);

      double amount = this.sway.getFloat() / 100.0;
      this.swayX = clamp(wx / 5.0, -1.2, 1.2) * 0.17 * amount;
      this.swayZ = clamp(wz / 5.0, -1.2, 1.2) * 0.17 * amount;
      this.tiltX = this.swayX * 0.25;
      this.tiltZ = this.swayZ * 0.25;
   }

   private void paths(EffectContext c) {
      for (int i = 0; i < this.vineCount; i++) {
         this.vinePath(c, i, c.time);
      }

      if (this.crownOn) {
         for (int k = 0; k < 2; k++) {
            this.strandPath(c, k);
         }
      }
   }

   public String layoutForTest(EffectContext c) {
      this.layout(c);
      return String.format(
         Locale.ROOT,
         "roots %.2f and crown %.2f above the feet (the ground %.2f); grown %.2f, crown %.2f, bloom %.2f, wither %.2f",
         this.rootY - c.y,
         this.crownY - c.y,
         c.groundY - c.y,
         this.grow,
         this.crownGrow,
         this.bloom,
         this.wither
      );
   }

   private double vinePhase(int i) {
      return (Math.PI * 2) * i / this.vineCount + 0.6 + 0.35 * hash(i, 11);
   }

   private double vineTopAngle(int i) {
      return this.vinePhase(i) + (Math.PI * 2) * (1.0 + 0.25 * hash(i, 5)) + this.turn;
   }

   private void vinePath(EffectContext c, int i, double t) {
      int at = i * 28;
      int n = 28;
      double phase = this.vinePhase(i);
      double turns = 1.0 + 0.25 * hash(i, 5);
      double gust = 0.75 + 0.25 * Math.sin(t * 1.7 * this.speed() + i * 1.9);
      double wob = 0.02 * this.sway.getFloat() / 100.0 * Math.sin(t * 2.3 * this.speed() + i * 2.7);
      double sxv = this.swayX * gust - this.swayZ * wob * 4.0;
      double szv = this.swayZ * gust + this.swayX * wob * 4.0;
      double thick = this.s * 0.03 * (1.0 - 0.45 * this.wither);
      double g = this.grow;
      this.vineGrown[i] = this.grow;
      int bark0 = this.vineColor.getColor(0.0F);
      int bark1 = this.vineColor.getColor(1.0F);

      for (int j = 0; j < n; j++) {
         double u = g * j / (n - 1.0);
         double kink = Math.sin(u * 53.0 + i * 4.1);
         double y = this.rootY + (this.crownY - this.rootY) * u * (1.25 - 0.25 * u) + 0.02 * Math.sin(u * 23.0 + i * 1.3) + 0.008 * kink;
         double ang = phase + (Math.PI * 2) * turns * u + this.turn * u * u * u;
         double rr = this.radial * (0.36 + 0.13 * Math.exp(-sq((u - 0.6) / 0.28)) + 0.025 * Math.sin(u * 17.0 + i * 2.1) + 0.01 * kink);
         double bend = Math.sin(Math.PI * u);
         int p = at + j;
         this.px[p] = c.x + Math.cos(ang) * rr + sxv * bend;
         this.py[p] = y;
         this.pz[p] = c.z + Math.sin(ang) * rr + szv * bend;
         double tip = Easing.clamp01((g - u) / 0.08);
         if (this.crownOn) {
            tip = Math.max(tip, this.crownGrow);
         }

         double knots = Math.sin(u * 41.0 + i * 3.1) * Math.sin(u * 17.0 + i * 1.7);
         this.rad[p] = thick * (1.0 - 0.4 * u) * (1.0 + 0.2 * knots) * tip;
         int bark = bark0;
         bark = ColorUtil.shade(bark, (float)(0.9 + 0.16 * Math.sin(u * 29.0 + i * 2.3)));
         if (this.wither > 0.0) {
            bark = ColorUtil.lerp(bark, -15856630, (float)(0.6 * this.wither));
         }

         float a = (float)(1.0 - this.air + this.air * Easing.smoothstep(0.0F, 0.5F, (float)(y - this.rootY)));
         this.col[p] = Math.round(255.0F * Easing.clamp01(a)) << 24 | bark & 16777215;
         float k = c.keep.solid(this.px[p], y, this.pz[p]);
         float face = Easing.smoothstep((float)(c.y + c.headTop - 0.52), (float)(c.y + c.headTop - 0.42), (float)y)
            * (1.0F - Easing.smoothstep(0.93F, 0.99F, (float)u));
         if (face > 0.0F) {
            k += (c.keep.face(this.px[p], y, this.pz[p]) - k) * face;
         }

         this.keepK[p] = k;
      }

      this.frames(c, at, n, false);
   }

   private void strandPath(EffectContext c, int k) {
      int at = 112 + k * 44;
      boolean closed = this.crownGrow >= 1.0;
      int n;
      this.strandPoints[k] = n = closed ? 44 : Math.max(0, Math.min(44, (int)Math.ceil(this.crownGrow * 44.0) + 1));
      if (n >= 2) {
         double start = this.vineTopAngle(0);
         double dir = k == 0 ? 1.0 : -1.0;
         double span = closed ? Math.PI * 2 : (Math.PI * 2) * this.crownGrow;
         double thick = this.s * 0.017 * (1.0 - 0.45 * this.wither);
         int bark = this.vineColor.getColor(0.5F);
         if (this.wither > 0.0) {
            bark = ColorUtil.lerp(bark, -15856630, (float)(0.6 * this.wither));
         }

         for (int j = 0; j < n; j++) {
            double f = closed ? (double)j / n : j / (n - 1.0);
            double a = start + dir * span * f;
            double braid = 3.0 * (a - this.turn) + k * Math.PI;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            double rr = this.crownR + 0.022 * this.s * Math.cos(braid);
            int p = at + j;
            this.px[p] = c.x + cos * rr;
            this.pz[p] = c.z + sin * rr;
            this.py[p] = this.crownY + 0.03 * this.s * Math.sin(braid) + this.tiltX * cos + this.tiltZ * sin;
            double tip = closed ? 1.0 : Easing.clamp01((1.0 - f) * n / 3.0);
            this.rad[p] = thick * tip;
            this.col[p] = 0xFF000000 | bark & 16777215;
            this.keepK[p] = c.keep.solid(this.px[p], this.py[p], this.pz[p]);
         }

         this.frames(c, at, n, closed);
      }
   }

   private void frames(EffectContext c, int at, int n, boolean closed) {
      this.kit.frames(at, n, closed, c.x, c.z);
   }

   private void tube(EffectContext c, int at, int n, boolean closed, Camera cam, float alpha) {
      this.kit.tube(at, n, closed, cam, alpha);
   }

   private void thorns(EffectContext c, float alpha) {
      float density = this.thornDensity.getFloat() / 100.0F;
      if (!(density <= 0.0F)) {
         int tipColor = this.thornColor.getColor(0.5F);

         for (int i = 0; i < this.vineCount; i++) {
            int count = Math.round(density * 14.0F);

            for (int q = 0; q < count; q++) {
               double u = (q + 0.5 + 0.7 * (hash(i * 31 + q, 3) - 0.5)) / count;
               if (!(u < 0.05) && !(u > this.vineGrown[i] - 0.04)) {
                  double f = u / this.vineGrown[i] * 27.0;
                  this.thorn(c, i * 28, 28, f, (Math.PI * 2) * hash(i * 17 + q, 7), 1.0, tipColor, alpha);
               }
            }
         }

         if (this.crownOn) {
            int count = Math.round(density * 18.0F);

            for (int qx = 0; qx < count; qx++) {
               int k = qx & 1;
               int n = this.strandPoints[k];
               double f;
               if (n >= 2 && !((f = (qx + 0.5 + 0.6 * (hash(qx, 13) - 0.5)) / count * 44.0) > n - 1.5)) {
                  this.thorn(c, 112 + k * 44, n, f, (hash(qx, 19) - 0.5) * 2.2, 0.8, tipColor, alpha);
               }
            }
         }
      }
   }

   private void thorn(EffectContext c, int at, int n, double f, double around, double scale, int tipColor, float alpha) {
      double len = this.s * 0.055 * scale * (0.8 + 0.4 * hash((int)(f * 7.0) + at, 23)) * (1.0 - 0.4 * this.wither);
      this.kit.thorn(at, n, f, around, len, tipColor, this.wither, alpha);
   }

   private void roses(EffectContext c, float alpha) {
      int count = this.roses.getInt();
      if (count > 0) {
         double t = c.time;
         float bud = Easing.smoothstep(0.42F, 0.6F, (float)t);
         if (!(bud <= 0.0F)) {
            double start = this.vineTopAngle(0);

            for (int r = 0; r < count; r++) {
               double a = start + (Math.PI * 2) * (r + 0.5) / count;
               double cos = Math.cos(a);
               double sin = Math.sin(a);
               double breathe = 1.0 + 0.03 * Math.sin(t * this.speed() * (Math.PI * 2) / 3.2 + r * 1.7);
               double size = this.s * 0.072 * (0.9 + 0.2 * hash(r, 9)) * breathe * bud * (1.0 - 0.35 * this.wither);
               double ox = c.x + cos * (this.crownR + 0.012);
               double oy = this.crownY + 0.035 * this.s + this.tiltX * cos + this.tiltZ * sin;
               double oz = c.z + sin * (this.crownR + 0.012);
               double axx = cos * 0.55;
               double axy = 0.83;
               double axz = sin * 0.55;
               float a0 = alpha * c.keep.solid(ox, oy, oz);
               this.rose(c, r, ox, oy, oz, axx, axy, axz, size, this.bloom * (1.0 - 0.6 * this.wither), a0);
               if (this.petals.get()) {
                  this.fallingPetal(c, r, ox, oy, oz, size, a0);
               }
            }
         }
      }
   }

   private void rose(EffectContext c, int r, double ox, double oy, double oz, double ax, double ay, double az, double size, double open, float alpha) {
      int roseColor = this.color(c, (float)r / Math.max(1, this.roses.getInt() - 1));
      if (this.wither > 0.0) {
         roseColor = ColorUtil.lerp(roseColor, -14022128, (float)(0.55 * this.wither));
      }

      this.kit.rose(r, ox, oy, oz, ax, ay, az, size, open, roseColor, alpha);
   }

   private void fallingPetal(EffectContext c, int r, double ox, double oy, double oz, double size, float alpha) {
      if (!(size <= 1.0E-4)) {
         int roseColor = this.color(c, (float)r / Math.max(1, this.roses.getInt() - 1)) | 0xFF000000;
         if (!(this.wither > 0.0)) {
            if (!(this.bloom < 1.0)) {
               double period = 3.4;
               double fall = 1.6;
               double tau = (c.time * this.speed() + hash(r, 29) * period) % period;
               if (!(tau >= fall)) {
                  double f = tau / fall;
                  double ph = hash(r, 31) * (Math.PI * 2) + Math.floor((c.time * this.speed() + hash(r, 29) * period) / period) * 2.4;
                  double flutter = 0.07 * Math.sin(f * 9.0 + ph);
                  double fx = ox + this.swayX * 1.4 * f + Math.cos(ph) * (0.06 + flutter);
                  double fz = oz + this.swayZ * 1.4 * f + Math.sin(ph) * (0.06 + flutter);
                  double fy = oy - 0.15 * f - 0.9 * f * f;
                  float a = alpha * Easing.smoothstep(0.0F, 0.08F, (float)f) * (1.0F - Easing.smoothstep(0.62F, 1.0F, (float)f));
                  this.tumblingPetal(fx, fy, fz, ph + f * 3.0, 1.2 + f * 5.0, size * 0.8, roseColor, a);
               }
            }
         } else {
            for (int q = 0; q < 3; q++) {
               double f = Easing.clamp01(this.wither * (1.15 - 0.12 * q));
               double ph = r * 1.3 + q * 2.1;
               double fx = ox + Math.cos(ph) * (0.05 + 0.12 * f) + this.swayX * 0.8 * f;
               double fz = oz + Math.sin(ph) * (0.05 + 0.12 * f) + this.swayZ * 0.8 * f;
               double fy = oy - 0.15 * f - 0.55 * f * f;
               float a = alpha * (1.0F - (float)(f * f));
               this.tumblingPetal(fx, fy, fz, ph + f * 5.0, 1.0 + f * 4.0, size * 0.8, roseColor, a);
            }
         }
      }
   }

   private void tumblingPetal(double x, double y, double z, double spin, double tumble, double size, int color, float alpha) {
      this.kit.tumblingPetal(x, y, z, spin, tumble, size, color, alpha);
   }

   private void nightHalo(EffectContext c) {
      float a = this.intensity(c) * c.night * 0.11F * (float)this.crownGrow * (float)(1.0 - this.wither);
      if (!(a <= 0.003F)) {
         int cc = alpha(-3549953, a);
         int n = 10;

         for (int i = 0; i < n; i++) {
            double ang = this.turn + (Math.PI * 2) * i / n;
            Vfx.sprite(Textures.GLOW, c.x + Math.cos(ang) * this.crownR, this.crownY, c.z + Math.sin(ang) * this.crownR, 0.32F * (float)this.s, 0.0F, cc);
         }
      }
   }

   private static double sq(double v) {
      return v * v;
   }

   private static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }
}
