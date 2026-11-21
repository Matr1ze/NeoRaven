package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Liquid;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.util.MathHelper;

public final class MercuryStyle extends TrailStyleBase {
   static final int MAX_DROPS = 24;
   static final int MAX_PUDDLES = 10;
   static final int MAX_SPLASH = 12;
   private static final double DROP = 0.065;
   private static final double GRAVITY = 12.0;
   private static final double PER_BLOCK = 1.6;
   private static final float PUDDLE_LIFE = 1.0F;
   private static final float SPLASH_LIFE = 0.5F;
   private static final float DROP_LIFE = 3.0F;
   private static final float PUDDLE = 2.6F;
   private static final double PUDDLE_CELL = 2.2;
   private static final float RIM = 0.25F;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting dropSize;
   public final SliderSetting mergeStrength;
   public final BooleanSetting puddles;
   public final ModeSetting<MercuryStyle.Quality> quality;
   private final GroundCache ground = new GroundCache();
   private TrailFrame groundFrame;
   private final Rng rng = new Rng();
   private int lastDrops;
   private int lastPuddles;
   private int lastSplashes;
   private int lastMode = -1;
   private int dropPoints;
   private int dropTris;
   private int floorPoints;
   private int floorTris;
   private double dropCell;
   private double floorCell;
   private double spreadX;
   private double spreadY;
   private double spreadZ;
   private float lastAlpha;

   public MercuryStyle(FuriahTrails module) {
      super(module, TrailStyle.MERCURY, spec().length(1.2).anchor(TrailAnchor.WAIST, false));
      this.material = this.materialSetting(Material.CHROME, "What the drops are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting(Finish.METALLIC, this.material);
      this.doesNotApply(new Setting[]{this.size, this.glow});
      this.dropSize = this.setting(new SliderSetting("mercury.dropSize", "Drop Size", "How big the drops are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.mergeStrength = this.setting(
         new SliderSetting("mercury.mergeStrength", "Merge Strength", "How readily nearby drops flow into one.", 0.0, 100.0, 60.0, 1.0).unit("%")
      );
      this.puddles = this.setting(
         new BooleanSetting("mercury.puddles", "Puddles", "Landing drops splash into small mirror puddles that ripple and shrink away.", true)
      );
      this.quality = this.setting(
         new ModeSetting<>("mercury.quality", "Quality", "How fine the liquid's surface is.", MercuryStyle.Quality.class, MercuryStyle.Quality.MEDIUM)
            .style(ModeSetting.Style.CHIPS)
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.dropSize);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new MercuryStyle.Pools(this);
   }

   @Override
   public int passes() {
      return this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
   }

   @Override
   public void update(TrailFrame f) {
      this.groundFrame = f;
      super.update(f);
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      if (!p.ghost && this.spawnPass(pass)) {
         this.groundFrame = f;
         MercuryStyle.Pools all = this.store(f);
         MercuryStyle.Pool pool = all.of(p);
         pool.seen = all.clock;
         pool.alpha = this.opacity.getFloat() / 100.0F * p.alpha;
         pool.tint = this.tint;
         pool.light = this.matLight;
         pool.reduced = p.reduced;
         pool.self = p.self;
         pool.anchorX = Math.floor(p.headX / 4.0) * 4.0;
         pool.anchorY = Math.floor(p.headY / 4.0) * 4.0;
         pool.anchorZ = Math.floor(p.headZ / 4.0) * 4.0;
         this.drip(p, f, pool);
      }
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      MercuryStyle.Pools all = (MercuryStyle.Pools)f.store(this.type);
      if (all != null) {
         for (int i = 0; i < all.count; i++) {
            this.draw(f, all.list[i], pass);
         }
      }
   }

   private void drip(PlayerTrail p, TrailFrame f, MercuryStyle.Pool pool) {
      int due = this.piecesDue(p, 1.6, 2);
      if (due != 0) {
         double yaw = Math.toRadians(p.pose.bodyYaw);
         double fx = -Math.sin(yaw);
         double fz = Math.cos(yaw);
         double r0 = 0.065 * this.dropSize.getFloat() / 100.0 * p.widthScale;
         pool.merge = this.mergeStrength.getFloat() / 100.0F;
         pool.makesPuddles = this.puddles.get();

         for (int i = 0; i < due && pool.drops < 24 && !this.module.piecesFull(f); i++) {
            this.rng.seed((int)(f.now * 6151.0) + i * 97 + p.entityId * 41);
            double side = this.rng.signed() * 0.2;
            int k = pool.drops++;
            pool.dx[k] = p.headX - fx * 0.15 + fz * side;
            pool.dy[k] = p.headY - 0.05 + this.rng.signed() * 0.08;
            pool.dz[k] = p.headZ - fz * 0.15 - fx * side;
            pool.dvx[k] = -fx * (0.3 + this.rng.next() * 0.4) + fz * this.rng.signed() * 0.25;
            pool.dvy[k] = this.rng.next() * 0.6;
            pool.dvz[k] = -fz * (0.3 + this.rng.next() * 0.4) - fx * this.rng.signed() * 0.25;
            pool.dr[k] = r0 * (0.75 + this.rng.next() * 0.5);
            pool.dage[k] = 0.0F;
         }
      }
   }

   void step(MercuryStyle.Pool p, float dt) {
      float pull = p.merge * 3.0F;
      if (pull > 0.0F) {
         for (int a = 0; a < p.drops; a++) {
            for (int b = a + 1; b < p.drops; b++) {
               double ex = p.dx[b] - p.dx[a];
               double ey = p.dy[b] - p.dy[a];
               double ez = p.dz[b] - p.dz[a];
               double d2 = ex * ex + ey * ey + ez * ez;
               double reach = (p.dr[a] + p.dr[b]) * 2.2;
               if (!(d2 > reach * reach) && !(d2 < 1.0E-8)) {
                  double d = Math.sqrt(d2);
                  double k = pull * (1.0 - d / reach) * dt / d;
                  p.dvx[a] = p.dvx[a] + ex * k;
                  p.dvy[a] = p.dvy[a] + ey * k;
                  p.dvz[a] = p.dvz[a] + ez * k;
                  p.dvx[b] = p.dvx[b] - ex * k;
                  p.dvy[b] = p.dvy[b] - ey * k;
                  p.dvz[b] = p.dvz[b] - ez * k;
               }
            }
         }
      }

      double drag = Math.exp(-0.8 * dt);
      int i = 0;

      while (i < p.drops) {
         p.dage[i] = p.dage[i] + dt;
         p.dvx[i] = p.dvx[i] * drag;
         p.dvz[i] = p.dvz[i] * drag;
         p.dvy[i] = p.dvy[i] - 12.0 * dt;
         p.dx[i] = p.dx[i] + p.dvx[i] * dt;
         p.dy[i] = p.dy[i] + p.dvy[i] * dt;
         p.dz[i] = p.dz[i] + p.dvz[i] * dt;
         double g = this.groundUnder(p.dx[i], p.dy[i] + 0.5, p.dz[i]);
         boolean landed = !Double.isNaN(g) && p.dy[i] - p.dr[i] * 0.6 <= g;
         if (!landed && !(p.dage[i] > 3.0F)) {
            i++;
         } else {
            if (landed) {
               this.splash(p, i, g);
            }

            removeDrop(p, i);
         }
      }

      i = 0;

      while (i < p.puddles) {
         p.page[i] = p.page[i] + dt;
         if (p.page[i] >= 1.0F) {
            int last = --p.puddles;
            p.px[i] = p.px[last];
            p.py[i] = p.py[last];
            p.pz[i] = p.pz[last];
            p.pr[i] = p.pr[last];
            p.page[i] = p.page[last];
            p.pphase[i] = p.pphase[last];
         } else {
            i++;
         }
      }

      i = 0;

      while (i < p.splashes) {
         p.sage[i] = p.sage[i] + dt;
         p.svy[i] = p.svy[i] - 12.0 * dt;
         p.sx[i] = p.sx[i] + p.svx[i] * dt;
         p.sy[i] = p.sy[i] + p.svy[i] * dt;
         p.sz[i] = p.sz[i] + p.svz[i] * dt;
         double g = p.svy[i] < 0.0 ? this.groundUnder(p.sx[i], p.sy[i] + 0.3, p.sz[i]) : Double.NaN;
         if (!(p.sage[i] >= 0.5F) && (Double.isNaN(g) || !(p.sy[i] <= g))) {
            i++;
         } else {
            int last = --p.splashes;
            p.sx[i] = p.sx[last];
            p.sy[i] = p.sy[last];
            p.sz[i] = p.sz[last];
            p.svx[i] = p.svx[last];
            p.svy[i] = p.svy[last];
            p.svz[i] = p.svz[last];
            p.sr[i] = p.sr[last];
            p.sage[i] = p.sage[last];
         }
      }
   }

   private double groundUnder(double x, double y, double z) {
      return this.ground.surface(this.groundFrame == null ? null : this.groundFrame.world, x, y, z, 0.6, 4.0);
   }

   private void splash(MercuryStyle.Pool p, int i, double g) {
      double r = p.dr[i];
      if (p.makesPuddles) {
         int into = -1;

         for (int k = 0; k < p.puddles && into < 0; k++) {
            double ex = p.px[k] - p.dx[i];
            double ez = p.pz[k] - p.dz[i];
            if (ex * ex + ez * ez < p.pr[k] * p.pr[k] * 1.5 && Math.abs(p.py[k] - g) < 0.2) {
               into = k;
            }
         }

         if (into >= 0) {
            p.pr[into] = Math.min(p.pr[into] * 1.15, r * 2.6F * 2.0);
            p.page[into] = Math.min(p.page[into], 0.25F);
         } else if (p.puddles < 10) {
            int var13 = p.puddles++;
            p.px[var13] = p.dx[i];
            p.py[var13] = g;
            p.pz[var13] = p.dz[i];
            p.pr[var13] = r * 2.6F;
            p.page[var13] = 0.0F;
            p.pphase[var13] = (float)(p.dage[i] * 31.0 % 6.283);
         }
      }

      int n = p.self ? 2 + ((int)(p.dage[i] * 97.0) & 1) : 1;

      for (int kx = 0; kx < n && p.splashes < 12; kx++) {
         int s = p.splashes++;
         double a = kx * 2.4 + p.dage[i] * 13.0;
         p.sx[s] = p.dx[i];
         p.sy[s] = g + r * 0.5;
         p.sz[s] = p.dz[i];
         p.svx[s] = MathHelper.cos((float)a) * 0.7;
         p.svy[s] = 1.3 + 0.4 * kx;
         p.svz[s] = MathHelper.sin((float)a) * 0.7;
         p.sr[s] = r * (0.35 + 0.1 * kx);
         p.sage[s] = 0.0F;
      }
   }

   private static void removeDrop(MercuryStyle.Pool p, int i) {
      int last = --p.drops;
      p.dx[i] = p.dx[last];
      p.dy[i] = p.dy[last];
      p.dz[i] = p.dz[last];
      p.dvx[i] = p.dvx[last];
      p.dvy[i] = p.dvy[last];
      p.dvz[i] = p.dvz[last];
      p.dr[i] = p.dr[last];
      p.dage[i] = p.dage[last];
   }

   private void draw(TrailFrame f, MercuryStyle.Pool pool, FxPass pass) {
      boolean mine = pool.self && !f.preview;
      if (mine) {
         this.lastDrops = pool.drops;
         this.lastPuddles = pool.puddles;
         this.lastSplashes = pool.splashes;
      }

      if (pool.size() != 0 && !(pool.alpha <= 0.002F)) {
         Liquid.Field fl = pool.field;
         Liquid.Field floor = pool.floor;
         fl.plain = pool.reduced;
         floor.plain = pool.reduced;
         fl.begin(1.5 + 0.9 * pool.merge);
         floor.begin(1.5 + 0.9 * pool.merge);
         float alpha = pool.alpha;
         double vx = f.viewX;
         double vy = f.viewY;
         double vz = f.viewZ;

         for (int i = 0; i < pool.drops; i++) {
            double grow = Easing.outCubic(Easing.clamp01(pool.dage[i] / 0.12F));
            double speed = Math.sqrt(pool.dvx[i] * pool.dvx[i] + pool.dvy[i] * pool.dvy[i] + pool.dvz[i] * pool.dvz[i]);
            double stretch = 1.0 / (1.0 + Math.min(0.6, speed * 0.08));
            fl.ball(pool.dx[i] - vx, pool.dy[i] - vy, pool.dz[i] - vz, pool.dr[i] * (0.4 + 0.6 * grow), 1.0, pool.dvx[i], pool.dvy[i], pool.dvz[i], stretch);
         }

         for (int var49 = 0; var49 < pool.puddles; var49++) {
            float age = pool.page[var49];
            double spread = Easing.outCubic(Easing.clamp01(age / 0.15F));
            double shrink = 1.0 - Easing.smoothstep(0.45F, 1.0F, age);
            double ripple = 1.0 + 0.09 * MathHelper.sin(age * 22.0F + pool.pphase[var49]) * Math.exp(-age * 3.0);
            double r = pool.pr[var49] * (0.4 + 0.6 * spread) * ripple * shrink;
            if (r > 0.01) {
               floor.ball(pool.px[var49] - vx, pool.py[var49] + r * 0.06 - vy, pool.pz[var49] - vz, r, 1.0, 0.0, 1.0, 0.0, 4.5);
            }
         }

         for (int var50 = 0; var50 < pool.splashes; var50++) {
            fl.ball(
               pool.sx[var50] - vx,
               pool.sy[var50] - vy,
               pool.sz[var50] - vz,
               pool.sr[var50] * (1.0 - 0.5 * pool.sage[var50] / 0.5),
               1.0,
               pool.svx[var50],
               pool.svy[var50],
               pool.svz[var50],
               0.85
            );
         }

         if (fl.balls() != 0 || floor.balls() != 0) {
            Material m = pool.tint != 0 ? Material.CUSTOM : this.material.get();
            int tintCol = pool.tint != 0 ? pool.tint | 0xFF000000 : (m == Material.CUSTOM ? this.color.getColor(0.5F) | 0xFF000000 : -1);
            double ax = pool.anchorX - vx;
            double ay = pool.anchorY - vy;
            double az = pool.anchorZ - vz;
            double ex = pool.anchorX + 2.0 - f.eyeX;
            double ey = pool.anchorY + 2.0 - f.eyeY;
            double ez = pool.anchorZ + 2.0 - f.eyeZ;
            double cell = 0.065
               * this.dropSize.getFloat()
               / 100.0
               * (pool.self ? this.quality.get().cell : Math.max(this.quality.get().cell, MercuryStyle.Quality.LOW.cell));
            MeshBatch.begin(m, pool.tint != 0 ? Finish.METALLIC : this.finish.get(), pool.light, 0.25F, -1, false, pass == FxPass.SOLID);

            try {
               double d2 = ex * ex + ey * ey + ez * ez;
               if (fl.balls() > 0) {
                  fl.mesh(cell, ax, ay, az, d2, alpha, tintCol, null);
               }

               if (floor.balls() > 0) {
                  floor.mesh(cell * 2.2, ax, ay, az, d2, alpha, tintCol, null);
               }
            } finally {
               MeshBatch.end();
            }

            if (mine) {
               this.lastMode = fl.balls() > 0 ? fl.lastMode : floor.lastMode;
               this.dropPoints = fl.balls() > 0 ? fl.lastPoints : 0;
               this.dropCell = fl.balls() > 0 ? fl.lastCell : 0.0;
               this.dropTris = fl.balls() > 0 ? fl.lastTriangles : 0;
               this.floorPoints = floor.balls() > 0 ? floor.lastPoints : 0;
               this.floorCell = floor.balls() > 0 ? floor.lastCell : 0.0;
               this.floorTris = floor.balls() > 0 ? floor.lastTriangles : 0;
               this.lastAlpha = alpha;
               double x0 = Double.MAX_VALUE;
               double y0 = Double.MAX_VALUE;
               double z0 = Double.MAX_VALUE;
               double x1 = -Double.MAX_VALUE;
               double y1 = -Double.MAX_VALUE;
               double z1 = -Double.MAX_VALUE;

               for (int i2 = 0; i2 < pool.drops; i2++) {
                  x0 = Math.min(x0, pool.dx[i2]);
                  x1 = Math.max(x1, pool.dx[i2]);
                  y0 = Math.min(y0, pool.dy[i2]);
                  y1 = Math.max(y1, pool.dy[i2]);
                  z0 = Math.min(z0, pool.dz[i2]);
                  z1 = Math.max(z1, pool.dz[i2]);
               }

               for (int var56 = 0; var56 < pool.splashes; var56++) {
                  x0 = Math.min(x0, pool.sx[var56]);
                  x1 = Math.max(x1, pool.sx[var56]);
                  y0 = Math.min(y0, pool.sy[var56]);
                  y1 = Math.max(y1, pool.sy[var56]);
                  z0 = Math.min(z0, pool.sz[var56]);
                  z1 = Math.max(z1, pool.sz[var56]);
               }

               boolean any = pool.drops + pool.splashes > 0;
               this.spreadX = any ? x1 - x0 : 0.0;
               this.spreadY = any ? y1 - y0 : 0.0;
               this.spreadZ = any ? z1 - z0 : 0.0;
            }
         }
      }
   }

   @Override
   public String stateForTest() {
      String how = this.lastMode == 0 ? "meshed" : (this.lastMode == 1 ? "its last mesh" : (this.lastMode == 2 ? "spheres" : "nothing"));
      return String.format(
         Locale.ROOT,
         "%s, drops %.0f%%, merge %.0f%%, puddles %s, %s quality; %d drop(s), %d puddle(s), %d splash(es), %s (drops: %d points, cell %.3f, %d triangles, spread %.2f x %.2f x %.2f; puddles: %d points, cell %.3f, %d triangles; opacity %.2f)",
         this.material.get().displayName(),
         this.dropSize.getFloat(),
         this.mergeStrength.getFloat(),
         this.puddles.get() ? "on" : "off",
         this.quality.get().displayName(),
         this.lastDrops,
         this.lastPuddles,
         this.lastSplashes,
         how,
         this.dropPoints,
         this.dropCell,
         this.dropTris,
         this.spreadX,
         this.spreadY,
         this.spreadZ,
         this.floorPoints,
         this.floorCell,
         this.floorTris,
         this.lastAlpha
      );
   }

   private static final class Pool {
      final PlayerTrail owner;
      final Liquid.Field field = new Liquid.Field(36);
      final Liquid.Field floor = new Liquid.Field(10);
      double seen;
      int drops;
      int puddles;
      int splashes;
      float alpha = 1.0F;
      float light = 1.0F;
      int tint;
      boolean reduced;
      double anchorX;
      double anchorY;
      double anchorZ;
      final double[] dx = new double[24];
      final double[] dy = new double[24];
      final double[] dz = new double[24];
      final double[] dvx = new double[24];
      final double[] dvy = new double[24];
      final double[] dvz = new double[24];
      final double[] dr = new double[24];
      final float[] dage = new float[24];
      final double[] px = new double[10];
      final double[] py = new double[10];
      final double[] pz = new double[10];
      final double[] pr = new double[10];
      final float[] page = new float[10];
      final float[] pphase = new float[10];
      final double[] sx = new double[12];
      final double[] sy = new double[12];
      final double[] sz = new double[12];
      final double[] svx = new double[12];
      final double[] svy = new double[12];
      final double[] svz = new double[12];
      final double[] sr = new double[12];
      final float[] sage = new float[12];
      float merge = 0.6F;
      boolean makesPuddles = true;
      boolean self;

      Pool(PlayerTrail owner) {
         this.owner = owner;
      }

      int size() {
         return this.drops + this.puddles + this.splashes;
      }
   }

   private static final class Pools implements TrailStyleBase.Store {
      MercuryStyle.Pool[] list = new MercuryStyle.Pool[8];
      int count;
      final MercuryStyle style;
      double clock;

      Pools(MercuryStyle style) {
         this.style = style;
      }

      MercuryStyle.Pool of(PlayerTrail t) {
         for (int i = 0; i < this.count; i++) {
            if (this.list[i].owner == t) {
               return this.list[i];
            }
         }

         if (this.count == this.list.length) {
            this.list = Arrays.copyOf(this.list, this.count * 2);
         }

         MercuryStyle.Pool pool = new MercuryStyle.Pool(t);
         this.list[this.count++] = pool;
         pool.seen = this.clock;
         return pool;
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         int i = 0;

         while (i < this.count) {
            MercuryStyle.Pool p = this.list[i];
            if (this.clock - p.seen > 2.0 && p.size() == 0) {
               this.list[i] = this.list[--this.count];
               this.list[this.count] = null;
            } else {
               if (dt > 0.0F) {
                  this.style.step(p, Math.min(dt, 0.1F));
               }

               i++;
            }
         }
      }

      @Override
      public int size() {
         int n = 0;

         for (int i = 0; i < this.count; i++) {
            n += this.list[i].size();
         }

         return n;
      }

      @Override
      public void clear() {
         Arrays.fill(this.list, 0, this.count, null);
         this.count = 0;
      }
   }

   public static enum Quality implements Displayable {
      LOW("Low", "Coarse drops, the least work", 0.9),
      MEDIUM("Medium", "Round drops", 0.65),
      HIGH("High", "The smoothest surface", 0.5);

      private final String displayName;
      private final String description;
      final double cell;

      private Quality(String displayName, String description, double cell) {
         this.displayName = displayName;
         this.description = description;
         this.cell = cell;
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
