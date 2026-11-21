package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Clockwork;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
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
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class CogsStyle extends TrailStyleBase {
   private static final int MAX_CLUSTERS = 96;
   private static final int PER = 3;
   private static final float GEAR = 0.15F;
   private static final float THICK = 0.28F;
   private static final double FULL_WITHIN = 5.0;
   private static final float ROLL = 0.45F;
   private static final float FADE = 0.5F;
   private static final int[] TEETH = new int[]{10, 12, 14, 8};
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final ThemeTint glowColor;
   public final SliderSetting gearSize;
   public final BooleanSetting clusters;
   public final BooleanSetting settle;
   public final BooleanSetting glints;
   public final ModeSetting<CogsStyle.Motion> motion;
   private final Rng rng = new Rng();
   private final GroundCache ground = new GroundCache();

   public CogsStyle(FuriahTrails module) {
      super(module, TrailStyle.COGS, spec().length(1.8).anchor(TrailAnchor.BACK, false));
      this.material = this.materialSetting(Material.BRASS, "What the gears are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting(Finish.METALLIC, this.material);
      this.glowColor = this.signatureColor("glowColor", "Glow", "The glints on the teeth.", -6237, -15254);
      this.doesNotApply(new Setting[]{this.size, this.glow});
      this.gearSize = this.setting(new SliderSetting("cogs.gearSize", "Gear Size", "How big the gears are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.clusters = this.setting(new BooleanSetting("cogs.clusters", "Clusters", "Two or three gears meshing together (off: single gears).", true));
      this.motion = this.setting(
         new ModeSetting<>("cogs.motion", "Motion", "Ticking round like clockwork, or turning evenly.", CogsStyle.Motion.class, CogsStyle.Motion.CLOCKWORK)
            .style(ModeSetting.Style.CHIPS)
      );
      this.settle = this.setting(
         new BooleanSetting("cogs.settle", "Settle on the Ground", "Gears that reach the ground roll a little and tick to a stop.", true)
      );
      this.glints = this.setting(new BooleanSetting("cogs.glints", "Glints", "Glints on the teeth as they turn.", true));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.gearSize);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new CogsStyle.Clusters();
   }

   @Override
   public int passes() {
      int body = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return this.glints.get() ? body | FxPass.GLOW.bit() : body;
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      if (!p.ghost && this.spawnPass(pass)) {
         int due = this.piecesDue(p, 0.55, 2);
         if (due != 0) {
            CogsStyle.Clusters d = this.store(f);
            double yaw = Math.toRadians(p.pose.bodyYaw);
            float fx = (float)(-Math.sin(yaw));
            float fz = (float)Math.cos(yaw);
            float life = this.lengthNow(p.speed);

            for (int i = 0; i < due && !this.module.piecesFull(f); i++) {
               this.rng.seed((int)(f.now * 4441.0) + i * 157 + p.entityId * 29);
               double x = p.headX - fx * 0.25 + fz * this.rng.signed() * 0.2;
               double y = p.headY + this.rng.signed() * 0.1;
               double z = p.headZ - fz * 0.25 - fx * this.rng.signed() * 0.2;
               if (!(f.nearFade(x, y, z) < 0.35F)) {
                  int k = d.add();
                  d.x[k] = x;
                  d.y[k] = y;
                  d.z[k] = z;
                  float back = 0.8F + this.rng.next() * 0.4F;
                  d.vx[k] = -fx * back + fz * this.rng.signed() * 0.25F;
                  d.vz[k] = -fz * back - fx * this.rng.signed() * 0.25F;
                  d.vy[k] = 0.5F + this.rng.next() * 0.4F;
                  d.born[k] = d.clock;
                  d.life[k] = life * (0.85F + this.rng.next() * 0.3F) + (this.settle.get() ? 0.95F : 0.0F);
                  d.alpha[k] = this.opacity.getFloat() / 100.0F * p.alpha;
                  double tilt = this.rng.signed() * 0.5;
                  double swing = this.rng.signed() * 0.4;
                  double axx = fz * Math.cos(swing) + fx * Math.sin(swing);
                  double axz = -fx * Math.cos(swing) + fz * Math.sin(swing);
                  double axy = Math.sin(tilt);
                  double l = Math.sqrt(axx * axx * Math.cos(tilt) * Math.cos(tilt) + axy * axy + axz * axz * Math.cos(tilt) * Math.cos(tilt));
                  d.ax[k] = (float)(axx * Math.cos(tilt) / l);
                  d.ay[k] = (float)(axy / l);
                  d.az[k] = (float)(axz * Math.cos(tilt) / l);
                  d.tumble[k] = this.rng.signed() * 0.8F;
                  d.landed[k] = -1.0F;
                  d.settles[k] = this.settle.get();
                  double g = this.ground.surface(f.world, x, y, z, 0.5, 6.0);
                  d.groundY[k] = Double.isNaN(g) ? Float.NaN : (float)g;
                  d.radius[k] = 0.15F * this.gearSize.getFloat() / 100.0F * (0.85F + this.rng.next() * 0.3F) * p.widthScale;
                  d.phase[k] = this.rng.next() * 6.283F;
                  d.spin[k] = (this.rng.next() < 0.5F ? 1.0F : -1.0F) * (0.9F + this.rng.next() * 0.5F);
                  d.count[k] = !p.reduced && this.clusters.get() ? 2 + (this.rng.next() < 0.45F ? 1 : 0) : 1;
                  d.tint[k] = this.tint;
                  d.seed[k] = (int)(this.rng.next() * 10000.0F);
                  d.far[k] = p.reduced;
                  d.self[k] = p.self;
               }
            }
         }
      }
   }

   private float alphaOf(CogsStyle.Clusters d, int i, TrailFrame f) {
      float age = d.clock - d.born[i];
      float a = d.alpha[i] * Easing.clamp01(age / 0.1F) * (1.0F - Easing.smoothstep(d.life[i] - 0.5F, d.life[i], age));
      return a * f.nearFade(d.x[i], d.y[i], d.z[i]) * f.fog(d.x[i], d.y[i], d.z[i]);
   }

   private double driverTurn(CogsStyle.Clusters d, int i) {
      float age = d.clock - d.born[i];
      boolean smooth = this.motion.get() == CogsStyle.Motion.SMOOTH;
      if (d.landed[i] >= 0.0F) {
         float since = d.clock - d.landed[i];
         float stop = 0.71999997F;
         float slowed = since >= stop ? stop * 0.5F : since - since * since / (2.0F * stop);
         age = d.landed[i] - d.born[i] + slowed;
      }

      return d.phase[i] + d.spin[i] * Clockwork.ratchet(age, 0.22, Math.PI / 6, smooth) * 1.0;
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      CogsStyle.Clusters d = (CogsStyle.Clusters)f.store(this.type);
      if (d != null && d.n != 0) {
         if (pass == FxPass.GLOW) {
            this.sparkle(f, d);
         } else {
            Material mat = this.material.get();
            this.batch(f, d, mat, pass, false);
            this.batch(f, d, Material.CUSTOM, pass, true);
         }
      }
   }

   private void batch(TrailFrame f, CogsStyle.Clusters d, Material m, FxPass pass, boolean tinted) {
      boolean any = false;

      for (int i = 0; i < d.n && !any; i++) {
         any = d.tint[i] != 0 == tinted;
      }

      if (any) {
         MeshBatch.begin(m, tinted ? Finish.METALLIC : this.finish.get(), this.matLight, 0.2F, -1, true, pass == FxPass.SOLID);

         try {
            for (int var13 = 0; var13 < d.n; var13++) {
               float a;
               if (d.tint[var13] != 0 == tinted && !((a = this.alphaOf(d, var13, f)) <= 0.01F)) {
                  int col = tinted ? d.tint[var13] | 0xFF000000 : (m == Material.CUSTOM ? this.color.getColor(0.5F) | 0xFF000000 : -1);
                  this.cluster(f, d, var13, ColorUtil.mulAlpha(col, a));
               }
            }
         } finally {
            MeshBatch.end();
         }
      }
   }

   private void cluster(TrailFrame f, CogsStyle.Clusters d, int i, int col) {
      double ax = d.ax[i];
      double ay = d.ay[i];
      double az = d.az[i];
      if (d.landed[i] >= 0.0F) {
         float lie = Easing.outCubic(Easing.clamp01((d.clock - d.landed[i]) / 0.45F));
         ay = ay * (1.0F - lie) + lie;
         double var60;
         double var62;
         double l = Math.sqrt((var60 = ax * (1.0F - lie)) * var60 + ay * ay + (var62 = az * (1.0F - lie)) * var62);
         ax = var60 / l;
         ay /= l;
         az = var62 / l;
      }

      double ez;
      double ey;
      double ex;
      if (Math.abs(ay) < 0.9) {
         ex = -az;
         ey = 0.0;
         ez = ax;
      } else {
         ex = 1.0;
         ey = 0.0;
         ez = 0.0;
      }

      double el = Math.sqrt(ex * ex + ey * ey + ez * ez);
      double var57;
      double var58;
      double gx = ay * (var57 = ez / el) - az * (var58 = ey / el);
      double var59;
      double gy = az * (var59 = ex / el) - ax * var57;
      double gz = ax * var58 - ay * var59;
      double cx = d.x[i] - f.viewX;
      double cy = d.y[i] - f.viewY;
      double cz = d.z[i] - f.viewZ;
      boolean full = d.self[i] && !d.far[i] && cx * cx + cy * cy + cz * cz < 25.0;
      double turn = this.driverTurn(d, i);
      int teeth0 = TEETH[d.seed[i] % 3];
      double r0 = d.radius[i];
      double m = Clockwork.module(r0, teeth0);
      this.gear(cx, cy, cz, ax, ay, az, r0, turn, teeth0, full, col, d.seed[i]);

      for (int k = 1; k < d.count[i]; k++) {
         int teeth = TEETH[(d.seed[i] / 3 + k) % 4];
         double r = Clockwork.tipRadius(m, teeth);
         double dist = Clockwork.meshDistance(m, teeth0, teeth);
         double ang = d.seed[i] % 628 / 100.0 + (k - 1) * 2.3;
         double ox = (var59 * Math.cos(ang) + gx * Math.sin(ang)) * dist;
         double oy = (var58 * Math.cos(ang) + gy * Math.sin(ang)) * dist;
         double oz = (var57 * Math.cos(ang) + gz * Math.sin(ang)) * dist;
         double towards = Clockwork.angleOf(ax, ay, az, ox, oy, oz);
         double t = Clockwork.meshed(turn, teeth0, teeth, towards);
         this.gear(cx + ox, cy + oy, cz + oz, ax, ay, az, r, t, teeth, full, col, d.seed[i] + k);
      }
   }

   private void gear(double x, double y, double z, double ax, double ay, double az, double r, double turn, int teeth, boolean full, int col, int seed) {
      if (full) {
         Clockwork.gear(x, y, z, ax, ay, az, r, r * 0.28F, turn, teeth, teeth >= 12 ? 4 : 3, col, 0.0F);
      } else {
         Clockwork.kiteGear(x, y, z, ax, ay, az, r, r * 0.28F, turn, col);
      }
   }

   private void sparkle(TrailFrame f, CogsStyle.Clusters d) {
      RenderState.additive();
      double time = f.now;

      for (int i = 0; i < d.n; i++) {
         float a;
         double run;
         double cyc;
         if (!d.far[i]
            && !((cyc = (run = time * 1.7 + d.seed[i] % 97 * 0.13) - Math.floor(run)) > 0.18)
            && !((a = this.alphaOf(d, i, f) * (float)Math.sin(Math.PI * cyc / 0.18)) <= 0.02F)) {
            double r = d.radius[i];
            double turn = this.driverTurn(d, i) + d.seed[i] % 7;
            double ax = d.ax[i];
            double ay = d.ay[i];
            double az = d.az[i];
            double ex = Math.abs(ay) < 0.9 ? -az : 1.0;
            double ez = Math.abs(ay) < 0.9 ? ax : 0.0;
            double el = Math.sqrt(ex * ex + ez * ez);
            double var45;
            double gx = ay * (var45 = ez / el);
            double var44;
            double gy = az * (var44 = ex / el) - ax * var45;
            double gz = -ay * var44;
            double c = Math.cos(turn) * r * 0.9;
            double s = Math.sin(turn) * r * 0.9;
            double x = d.x[i] - f.viewX + var44 * c + gx * s;
            double y = d.y[i] - f.viewY + gy * s;
            double z = d.z[i] - f.viewZ + var45 * c + gz * s;
            int col = this.slotColor(this.glowColor, 0.5F, f.colorTime);
            Vfx.sprite(Textures.SPARKLE, x, y, z, r * 1.1, (float)(time * 2.0 + i), ColorUtil.mulAlpha(col, Math.min(1.0F, a)));
         }
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%s, gear %.0f%%, clusters %s, %s, settle %s, glints %s",
         this.material.get().displayName(),
         this.gearSize.getFloat(),
         this.clusters.get() ? "on" : "off",
         this.motion.get().displayName(),
         this.settle.get() ? "on" : "off",
         this.glints.get() ? "on" : "off"
      );
   }

   static final class Clusters implements TrailStyleBase.Store {
      final double[] x = new double[96];
      final double[] y = new double[96];
      final double[] z = new double[96];
      final float[] vx = new float[96];
      final float[] vy = new float[96];
      final float[] vz = new float[96];
      final float[] born = new float[96];
      final float[] life = new float[96];
      final float[] alpha = new float[96];
      final float[] ax = new float[96];
      final float[] ay = new float[96];
      final float[] az = new float[96];
      final float[] tumble = new float[96];
      final float[] landed = new float[96];
      final float[] groundY = new float[96];
      final float[] radius = new float[96];
      final float[] phase = new float[96];
      final float[] spin = new float[96];
      final int[] count = new int[96];
      final int[] tint = new int[96];
      final int[] seed = new int[96];
      final boolean[] far = new boolean[96];
      final boolean[] self = new boolean[96];
      final boolean[] settles = new boolean[96];
      int n;
      int oldest;
      float clock;

      int add() {
         if (this.n < 96) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 96;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         if (!(dt <= 0.0F)) {
            float damp = (float)Math.exp(-1.4 * dt);
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               if (!(this.clock - this.born[i] >= this.life[i])) {
                  if (this.landed[i] < 0.0F) {
                     this.vx[i] = this.vx[i] * damp;
                     this.vz[i] = this.vz[i] * damp;
                     this.vy[i] = this.vy[i] - 4.5F * dt;
                     this.x[i] = this.x[i] + this.vx[i] * dt;
                     this.y[i] = this.y[i] + this.vy[i] * dt;
                     this.z[i] = this.z[i] + this.vz[i] * dt;
                     if (this.settles[i] && !Float.isNaN(this.groundY[i]) && this.y[i] - this.radius[i] * 0.6 <= this.groundY[i]) {
                        this.y[i] = this.groundY[i] + this.radius[i] * 0.6F;
                        this.landed[i] = this.clock;
                     }
                  } else {
                     float k = 1.0F - Easing.clamp01((this.clock - this.landed[i]) / 0.45F);
                     this.x[i] = this.x[i] + this.vx[i] * dt * k;
                     this.z[i] = this.z[i] + this.vz[i] * dt * k;
                  }

                  if (w != i) {
                     this.copy(i, w);
                  }

                  w++;
               }
            }

            this.n = w;
            this.oldest = 0;
         }
      }

      private void copy(int a, int b) {
         this.x[b] = this.x[a];
         this.y[b] = this.y[a];
         this.z[b] = this.z[a];
         this.vx[b] = this.vx[a];
         this.vy[b] = this.vy[a];
         this.vz[b] = this.vz[a];
         this.born[b] = this.born[a];
         this.life[b] = this.life[a];
         this.alpha[b] = this.alpha[a];
         this.ax[b] = this.ax[a];
         this.ay[b] = this.ay[a];
         this.az[b] = this.az[a];
         this.tumble[b] = this.tumble[a];
         this.landed[b] = this.landed[a];
         this.groundY[b] = this.groundY[a];
         this.radius[b] = this.radius[a];
         this.phase[b] = this.phase[a];
         this.spin[b] = this.spin[a];
         this.count[b] = this.count[a];
         this.tint[b] = this.tint[a];
         this.seed[b] = this.seed[a];
         this.far[b] = this.far[a];
         this.self[b] = this.self[a];
         this.settles[b] = this.settles[a];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
         this.oldest = 0;
      }
   }

   public static enum Motion implements Displayable {
      CLOCKWORK("Clockwork", "Ticking round a tooth at a time"),
      SMOOTH("Smooth", "Turning evenly");

      private final String displayName;
      private final String description;

      private Motion(String displayName, String description) {
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
