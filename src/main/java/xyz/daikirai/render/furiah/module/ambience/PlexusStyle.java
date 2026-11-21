package xyz.daikirai.render.furiah.module.ambience;

import java.util.Arrays;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class PlexusStyle extends AmbientStyleBase {
   final SliderSetting connectDistance;
   final SliderSetting maxConnections;
   final SliderSetting lineThickness;
   final SliderSetting drift;
   private final float[] flow = new float[3];
   private int[] linkA = new int[0];
   private int[] linkB = new int[0];
   private int links;
   private int[] degree = new int[0];
   private int[] cellHead = new int[4096];
   private int[] next = new int[0];
   private long connectedTick = -1L;

   PlexusStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.PLEXUS, spec());
      this.connectDistance = this.setting(
         new SliderSetting("plexus.connect", "Connection Distance", "How close two points must be to be joined.", 0.5, 6.0, 2.6, 0.1).unit(" blocks")
      );
      this.maxConnections = this.setting(new SliderSetting("plexus.maxLinks", "Max Connections", "The most lines one point has.", 1.0, 8.0, 3.0, 1.0));
      this.lineThickness = this.setting(new SliderSetting("plexus.thickness", "Line Thickness", "How thick the lines are.", 0.3, 3.0, 1.0, 0.05).unit("×"));
      this.drift = this.setting(new SliderSetting("plexus.drift", "Drift Speed", "How fast the points drift.", 0.0, 3.0, 1.0, 0.05).unit("×"));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.06F;
      s.opacity = 80.0F;
      s.followWind = 10.0F;
      s.heightMin = 0.5F;
      s.heightMax = 5.0F;
      s.heightLimit = 30.0F;
      s.customDistance = true;
      s.distance = 12;
      s.cap = 400;
      s.densityHelp = "How many points there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         double ang = this.rng.next() * 6.283;
         p.a[i] = (float)Math.cos(ang);
         p.b[i] = (float)Math.sin(ang);
         p.c[i] = this.rng.next() * 6.283F;
         p.size[i] = this.rng.range(0.7F, 1.3F);
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      float sp = this.speed.getFloat() * this.drift.getFloat();
      this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
      float turn = 0.35F * dt * (float)Math.sin(p.age[i] * 0.3F + p.c[i]);
      float ca = (float)Math.cos(turn);
      float sa = (float)Math.sin(turn);
      float nxd = p.a[i] * ca - p.b[i] * sa;
      float nzd = p.a[i] * sa + p.b[i] * ca;
      p.a[i] = nxd;
      p.b[i] = nzd;
      double nx = p.x[i] + (p.a[i] * 0.3F + this.flow[0] * 0.2F) * sp * dt;
      double nz = p.z[i] + (p.b[i] * 0.3F + this.flow[2] * 0.2F) * sp * dt;
      double ny = p.y[i] + 0.12F * (float)Math.sin(p.age[i] * 0.5F + p.c[i] * 2.0F) * sp * dt;
      if (!env.solidAt(nx, ny, nz)) {
         p.x[i] = nx;
         p.y[i] = ny;
         p.z[i] = nz;
      }

      return true;
   }

   private void connect(AmbientEnv env) {
      AmbientPool p = this.pool;
      int n = p.count;
      float dMax = this.connectDistance.getFloat() * this.size.getFloat();
      int maxDeg = this.maxConnections.getInt();
      if (this.degree.length < n) {
         this.degree = new int[p.capacity];
         this.next = new int[p.capacity];
      }

      int maxLinks;
      if (this.linkA.length < (maxLinks = n * maxDeg / 2 + 1)) {
         this.linkA = new int[maxLinks + 64];
         this.linkB = new int[maxLinks + 64];
      }

      Arrays.fill(this.degree, 0, n, 0);
      Arrays.fill(this.cellHead, -1);
      int mask = this.cellHead.length - 1;
      double inv = 1.0 / dMax;
      int i = 0;

      while (i < n) {
         int h = hashCell((int)Math.floor(p.x[i] * inv), (int)Math.floor(p.y[i] * inv), (int)Math.floor(p.z[i] * inv)) & mask;
         this.next[i] = this.cellHead[h];
         this.cellHead[h] = i++;
      }

      this.links = 0;
      double d2Max = (double)dMax * dMax;

      for (int i2 = 0; i2 < n && this.links < maxLinks; i2++) {
         if (this.degree[i2] < maxDeg) {
            int cx = (int)Math.floor(p.x[i2] * inv);
            int cy = (int)Math.floor(p.y[i2] * inv);
            int cz = (int)Math.floor(p.z[i2] * inv);

            for (int ox = -1; ox <= 1 && this.degree[i2] < maxDeg; ox++) {
               for (int oy = -1; oy <= 1 && this.degree[i2] < maxDeg; oy++) {
                  for (int oz = -1; oz <= 1 && this.degree[i2] < maxDeg; oz++) {
                     for (int j = this.cellHead[hashCell(cx + ox, cy + oy, cz + oz) & mask]; j >= 0; j = this.next[j]) {
                        double dz;
                        double dy;
                        double dx;
                        if (j > i2
                           && this.degree[j] < maxDeg
                           && (dx = p.x[j] - p.x[i2]) * dx + (dy = p.y[j] - p.y[i2]) * dy + (dz = p.z[j] - p.z[i2]) * dz < d2Max) {
                           this.linkA[this.links] = i2;
                           this.linkB[this.links] = j;
                           this.links++;
                           this.degree[i2]++;
                           this.degree[j]++;
                           if (this.degree[i2] >= maxDeg || this.links >= maxLinks) {
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static int hashCell(int x, int y, int z) {
      return x * 73856093 ^ y * 19349663 ^ z * 83492791;
   }

   private int heightColor(double y, double ground) {
      float t = Double.isNaN(ground) ? 0.5F : Easing.clamp01((float)((y - ground - this.lowest()) / Math.max(0.5F, this.highest() - this.lowest())));
      return this.color.getColor(t);
   }

   @Override
   void draw(AmbientEnv env) {
      if (this.connectedTick != env.tickCount) {
         this.connectedTick = env.tickCount;
         this.connect(env);
      }

      float r = this.radius(env);
      float dMax = this.connectDistance.getFloat() * this.size.getFloat();
      float w = 0.018F * this.lineThickness.getFloat() * this.size.getFloat();
      float glowK = this.glowK();
      RenderState.additive();

      for (int layer = glowK > 0.0F ? 0 : 1; layer < 2; layer++) {
         for (int k = 0; k < this.links; k++) {
            int i = this.linkA[k];
            int j = this.linkB[k];
            if (i < this.pool.count && j < this.pool.count && this.place(env, j, 0.1F)) {
               double jx = this.rx;
               double jy = this.ry;
               double jz = this.rz;
               float aj = this.fogged(this.fade(env, j, r)) * Easing.smoothstep(1.2F, 3.5F, this.dist);
               double gj = this.pool.ground[j];
               double wj = this.wy;
               if (this.place(env, i, dMax)) {
                  float ai = this.fogged(this.fade(env, i, r)) * Easing.smoothstep(1.2F, 3.5F, this.dist);
                  double dx = jx - this.rx;
                  double dy = jy - this.ry;
                  double dz = jz - this.rz;
                  float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
                  float strength = 1.0F - Easing.clamp01(d / dMax);
                  if (!(strength <= 0.01F) && !(ai + aj <= 0.01F)) {
                     int ci = this.heightColor(this.wy, this.pool.ground[i]);
                     int cj = this.heightColor(wj, gj);
                     if (layer == 0) {
                        Vfx.line(
                           Textures.LINE_GLOW,
                           this.rx,
                           this.ry,
                           this.rz,
                           jx,
                           jy,
                           jz,
                           w * 5.0F,
                           ColorUtil.mulAlpha(ci, ai * strength * 0.3F * glowK),
                           ColorUtil.mulAlpha(cj, aj * strength * 0.3F * glowK)
                        );
                     } else {
                        Vfx.line(
                           Textures.LINE_CORE,
                           this.rx,
                           this.ry,
                           this.rz,
                           jx,
                           jy,
                           jz,
                           w,
                           ColorUtil.mulAlpha(Neon.tint(ci), ai * strength),
                           ColorUtil.mulAlpha(Neon.tint(cj), aj * strength)
                        );
                     }
                  }
               }
            }
         }

         Vfx.flush();
      }

      float s0 = 0.065F * this.size.getFloat();

      for (int i = 0; i < this.pool.count; i++) {
         float a;
         if (this.place(env, i, s0 * 3.0F) && !((a = this.fogged(this.fade(env, i, r)) * Easing.smoothstep(1.2F, 3.5F, this.dist)) <= 0.004F)) {
            int c = this.heightColor(this.wy, this.pool.ground[i]);
            if (glowK > 0.0F) {
               Vfx.sprite(Textures.GLOW, this.rx, this.ry, this.rz, s0 * this.pool.size[i] * 5.0F, 0.0F, ColorUtil.mulAlpha(c, a * 0.3F * glowK));
            }

            Vfx.sprite(Textures.SPARK, this.rx, this.ry, this.rz, s0 * this.pool.size[i] * 1.6F, 0.0F, ColorUtil.mulAlpha(Neon.tint(c), a));
         }
      }

      Vfx.flush();
   }
}
