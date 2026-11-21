package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class WindStyle extends AmbientStyleBase {
   private static final int TAIL = 34;
   final SliderSetting length;
   final SliderSetting thickness;
   final SliderSetting curlChance;
   final SliderSetting loopSize;
   final BooleanSetting hugTerrain;
   private final float[] flow = new float[3];
   private int blocked;
   private static final int MAX_POINTS = 72;
   private float[] cacheX = new float[0];
   private float[] cacheY = new float[0];
   private float[] cacheZ = new float[0];
   private int[] cacheN = new int[0];
   private float[] cacheA = new float[0];
   private float[] cacheFog = new float[0];
   private static final double[] SX = new double[36];
   private static final double[] SY = new double[36];
   private static final double[] SZ = new double[36];

   WindStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.WIND, spec());
      this.length = this.setting(new SliderSetting("wind.length", "Length", "How long each streak's tail is.", 2.0, 6.0, 4.0, 0.25).unit(" blocks"));
      this.thickness = this.setting(new SliderSetting("wind.thickness", "Thickness", "How thick the streaks are.", 0.3, 3.0, 1.0, 0.05).unit("×"));
      this.curlChance = this.setting(
         new SliderSetting("wind.curl", "Curl Chance", "How many streaks curl into a loop on their way.", 0.0, 100.0, 30.0, 5.0).unit("%")
      );
      this.loopSize = this.setting(new SliderSetting("wind.loop", "Loop Size", "How big the loops are.", 0.3, 2.5, 0.9, 0.05).unit(" blocks"));
      this.hugTerrain = this.setting(
         new BooleanSetting(
            "wind.hug",
            "Hug Terrain",
            "Streaks follow the ground's height as they travel (off: they keep their height and rise over what is in the way).",
            true
         )
      );
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.005F;
      s.nearBias = 1.3F;
      s.opacity = 75.0F;
      s.followWind = 100.0F;
      s.heightMin = 0.5F;
      s.heightMax = 6.0F;
      s.heightLimit = 30.0F;
      s.cap = 400;
      s.tail = 34;
      s.onByDefault = true;
      s.collides = null;
      s.densityHelp = "How many streaks there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         this.setUp(env, i);
         return true;
      }
   }

   private void setUp(AmbientEnv env, int i) {
      AmbientPool p = this.pool;
      p.a[i] = this.rng.next() * 6.283F;
      p.size[i] = this.rng.range(5.0F, 15.0F);
      p.b[i] = this.rng.next() * 100.0F < this.curlChance.get() ? this.rng.range(0.25F, 0.7F) : -1.0F;
      p.c[i] = -1.0F;
      p.d[i] = Double.isNaN(p.ground[i]) ? 2.0F : (float)(p.y[i] - p.ground[i]);
      p.e[i] = 0.0F;
      p.life[i] = this.rng.range(0.8F, 1.25F);
      p.age[i] = 0.0F;
      p.flags[i] = p.flags[i] & 1;
      p.vz[i] = 0.0F;
      p.vy[i] = 0.0F;
      p.vx[i] = 0.0F;
   }

   @Override
   protected void beforeUpdate(AmbientEnv env, float r) {
      if (env.wind.gustStarted && this.pool.count >= 6 && !(this.presence < 0.5F)) {
         int n = Math.min(this.pool.count / 3, 4 + this.rng.nextInt(4));
         double ang = this.rng.signed() * 0.9;
         double along = -r * this.rng.range(0.15F, 0.45F);
         double cx = env.camX + env.wind.dirX * along + -env.wind.dirZ * r * 0.35 * Math.sin(ang);
         double cz = env.camZ + env.wind.dirZ * along + env.wind.dirX * r * 0.35 * Math.sin(ang);
         float spacing = this.rng.range(0.6F, 1.1F);
         double g0 = env.outdoorsOnly ? env.groundAt(cx, cz) : env.floorBelow(cx, env.camY + 2.0, cz, 16);
         if (!Double.isNaN(g0)) {
            float h0 = this.lowest() + (this.highest() - this.lowest()) * this.rng.range(0.2F, 0.7F);

            for (int k = 0; k < n; k++) {
               int oldest = -1;
               float best = -1.0F;

               for (int j = 0; j < this.pool.count; j++) {
                  if ((this.pool.flags[j] & 1) == 0 && (this.pool.flags[j] & 2) == 0 && this.pool.age[j] > best) {
                     best = this.pool.age[j];
                     oldest = j;
                  }
               }

               if (oldest < 0) {
                  break;
               }

               double side = (k - (n - 1) / 2.0) * spacing;
               double x = cx - env.wind.dirZ * side - env.wind.dirX * k * 0.4;
               double z = cz + env.wind.dirX * side - env.wind.dirZ * k * 0.4;
               double g = env.outdoorsOnly ? env.groundAt(x, z) : g0;
               double y;
               if (!Double.isNaN(g) && !env.solidAt(x, y = g + h0 + k * 0.12, z)) {
                  this.pool.teleport(oldest, x, y, z);
                  this.pool.ground[oldest] = g;
                  this.pool.fadeIn[oldest] = 0.0F;
                  this.setUp(env, oldest);
                  this.pool.b[oldest] = -1.0F;
                  this.pool.size[oldest] = this.rng.range(10.0F, 16.0F);
                  this.pool.flags[oldest] = this.pool.flags[oldest] | 2;
               }
            }

            int jx = 0;

            while (jx < this.pool.count) {
               int n3 = jx++;
               this.pool.flags[n3] = this.pool.flags[n3] & -3;
            }
         }
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      float sp = this.speed.getFloat();
      if (p.e[i] > 0.0F) {
         p.e[i] = p.e[i] + dt / 0.45F;
         double ex = p.x[i] + p.vx[i] * dt * 0.4F;
         double ey = p.y[i] + p.vy[i] * dt * 0.4F;
         double ez = p.z[i] + p.vz[i] * dt * 0.4F;
         if (!env.solidAt(ex, ey, ez)) {
            p.x[i] = ex;
            p.y[i] = ey;
            p.z[i] = ez;
         }

         return p.e[i] < 1.0F;
      } else {
         this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
         float vx = this.flow[0];
         float vy = this.flow[1] * 0.5F;
         float vz = this.flow[2];
         float hs = (float)Math.sqrt(vx * vx + vz * vz);
         float min = 2.2F;
         if (hs < min) {
            vx += env.wind.dirX * (min - hs);
            vz += env.wind.dirZ * (min - hs);
            hs = min;
         }

         float mul = p.life[i] * sp * (0.85F + 0.3F * env.wind.gustAmount());
         vx *= mul;
         vy *= mul;
         hs *= mul;
         float sway = 0.28F * (float)Math.sin(p.age[i] * 0.9F * sp + p.a[i]);
         float cos = (float)Math.cos(sway);
         float sin = (float)Math.sin(sway);
         float var43;
         float nvx = vx * cos - (var43 = vz * mul) * sin;
         float nvz = vx * sin + var43 * cos;
         vx = nvx;
         vz = nvz;
         vy += 0.35F * (float)Math.sin(p.age[i] * 1.3F * sp + p.a[i] * 2.1F);
         float traveled = p.age[i] * hs;
         if (p.b[i] >= 0.0F && p.c[i] < 0.0F && traveled >= p.b[i] * p.size[i]) {
            p.c[i] = 0.0F;
         }

         if (p.c[i] >= 0.0F) {
            float radius = this.loopSize.getFloat() * this.size.getFloat();
            float loopTime = (float)((Math.PI * 2) * radius / Math.max(0.5F, hs));
            p.c[i] = p.c[i] + dt / Math.max(0.25F, loopTime);
            if (p.c[i] >= 1.0F) {
               p.c[i] = -2.0F;
            } else {
               float t = p.c[i];
               float turn = (float)((Math.PI * 2) * (t * t * (3.0F - 2.0F * t)));
               float dirX = nvx / hs;
               float dirZ = nvz / hs;
               float c = (float)Math.cos(turn);
               float s = (float)Math.sin(turn);
               vx = dirX * hs * c;
               vz = dirZ * hs * c;
               vy = vy * 0.3F + hs * s;
            }
         }

         double nx = p.x[i] + vx * dt;
         double nz = p.z[i] + vz * dt;
         double ny = p.y[i] + vy * dt;
         if (this.hugTerrain.get() && p.c[i] < 0.0F) {
            double g = env.outdoorsOnly ? env.groundAt(nx, nz) : p.ground[i];
            if (!Double.isNaN(g)) {
               double ahead = g;
               if (env.outdoorsOnly && hs > 0.01F) {
                  double ux = vx / hs;
                  double uz = vz / hs;

                  for (int k = 1; k <= 5; k++) {
                     double ga = env.groundAt(nx + ux * k, nz + uz * k);
                     if (!Double.isNaN(ga) && ga > ahead && ga - g <= 4.0) {
                        ahead = ga;
                     }
                  }
               }

               double want = ahead + p.d[i];
               double dy = (want - ny) * Math.min(1.0, dt * (want > ny ? 5.0 : 2.5));
               ny += Math.max(-hs * dt * 0.5, Math.min(hs * dt * 0.85, dy));
               p.ground[i] = g;
            }
         }

         if (env.solidAt(nx, ny, nz)) {
            if (env.solidAt(nx, ny + 1.0, nz)) {
               p.e[i] = 0.001F;
               this.blocked++;
               return true;
            }

            ny = Math.floor(ny) + 1.05;
            p.d[i] = p.d[i] + 0.5F;
         }

         p.vx[i] = vx;
         p.vy[i] = (float)((ny - p.y[i]) / dt);
         p.vz[i] = vz;
         p.x[i] = nx;
         p.y[i] = ny;
         p.z[i] = nz;
         if (traveled >= p.size[i]) {
            p.e[i] = 0.001F;
         }

         return true;
      }
   }

   @Override
   void draw(AmbientEnv env) {
      float glowK = this.glowK();
      float r = this.radius(env);
      int n = this.pool.count;
      if (this.cacheN.length < this.pool.capacity) {
         int cap = this.pool.capacity;
         this.cacheX = new float[cap * 72];
         this.cacheY = new float[cap * 72];
         this.cacheZ = new float[cap * 72];
         this.cacheN = new int[cap];
         this.cacheA = new float[cap];
         this.cacheFog = new float[cap];
      }

      float len = this.length.getFloat() * this.size.getFloat();

      for (int i = 0; i < n; i++) {
         this.cacheN[i] = 0;
         int pts;
         float a;
         if (this.place(env, i, len)
            && !((a = this.fade(env, i, r) * (1.0F - Easing.smoothstep(0.0F, 1.0F, this.pool.e[i]))) <= 0.003F)
            && !(this.fogK >= 1.0F)
            && (pts = this.points(env, i, len, this.dist < 12.0F ? 0 : (this.dist < 22.0F ? 1 : 2))) >= 2) {
            int o = i * 72;

            for (int k = 0; k < pts; k++) {
               this.cacheX[o + k] = (float)Vfx.X[k];
               this.cacheY[o + k] = (float)Vfx.Y[k];
               this.cacheZ[o + k] = (float)Vfx.Z[k];
            }

            this.cacheN[i] = pts;
            this.cacheA[i] = a;
            this.cacheFog[i] = this.fogK;
         }
      }

      if (glowK > 0.0F) {
         RenderState.additive();

         for (int var11 = 0; var11 < n; var11++) {
            this.streak(env, var11, 0, glowK);
         }

         Vfx.flush();
      }

      RenderState.translucent(false);

      for (int var12 = 0; var12 < n; var12++) {
         this.streak(env, var12, 1, glowK);
      }

      Vfx.flush();
      RenderState.additive();

      for (int var13 = 0; var13 < n; var13++) {
         this.streak(env, var13, 2, glowK);
      }

      Vfx.flush();
   }

   private void streak(AmbientEnv env, int i, int layer, float glowK) {
      int n = this.cacheN[i];
      if (n >= 2) {
         float a = this.cacheA[i];
         this.fogK = this.cacheFog[i];
         int o = i * 72;

         for (int k = 0; k < n; k++) {
            Vfx.X[k] = this.cacheX[o + k];
            Vfx.Y[k] = this.cacheY[o + k];
            Vfx.Z[k] = this.cacheZ[o + k];
         }

         float w0 = 0.1F * this.thickness.getFloat() * this.size.getFloat();
         int tex;
         float alphaK;
         float widthK;
         switch (layer) {
            case 0:
               widthK = 4.0F;
               alphaK = 0.3F * glowK;
               tex = Textures.LINE_GLOW;
               break;
            case 1:
               widthK = 1.0F;
               alphaK = 0.75F;
               tex = Textures.LINE_GLOW;
               break;
            default:
               widthK = 0.34F;
               alphaK = 1.0F;
               tex = Textures.LINE_CORE;
         }

         for (int k = 0; k < n; k++) {
            float t = (float)k / (n - 1);
            float taper = (float)Math.pow(1.0F - t, 0.75) * Easing.smoothstep(0.0F, 0.06F, t + 0.02F);
            Vfx.W[k] = w0 * widthK * Math.max(0.05F, taper);
            int c = this.color.getColor(t);
            if (layer == 2) {
               c = Neon.tint(c);
            }

            float fa = a * alphaK * (float)Math.pow(1.0F - t, 1.4);
            if (layer == 1) {
               c = this.fogColor(env, this.lit(env, c));
            } else {
               fa = this.fogged(fa);
            }

            Vfx.C[k] = ColorUtil.mulAlpha(c, Math.min(1.0F, fa));
         }

         Vfx.ribbon(tex, 0, n, false);
      }
   }

   private int points(AmbientEnv env, int i, float maxLen, int detail) {
      AmbientPool p = this.pool;
      int filled = p.filled[i];
      if (filled < 1) {
         return 0;
      } else {
         int samples = 0;
         SX[samples] = this.rx;
         SY[samples] = this.ry;
         SZ[samples] = this.rz;
         samples++;
         int stride = detail >= 2 ? 2 : 1;

         for (int k = 1; k < filled && samples < SX.length; k += stride) {
            int h = p.historyIndex(i, k);
            SX[samples] = p.hx[h] - env.viewX;
            SY[samples] = p.hy[h] - env.viewY;
            SZ[samples] = p.hz[h] - env.viewZ;
            samples++;
         }

         int n = 0;
         double used = 0.0;
         double lx = SX[0];
         double ly = SY[0];
         double lz = SZ[0];
         Vfx.X[n] = lx;
         Vfx.Y[n] = ly;
         Vfx.Z[n] = lz;
         n++;

         for (int s = 0; s + 1 < samples && n < 69; s++) {
            int s0 = Math.max(0, s - 1);
            int s2 = s + 1;
            int s3 = Math.min(samples - 1, s + 2);
            double gx = SX[s2] - SX[s];
            double gy = SY[s2] - SY[s];
            double gz = SZ[s2] - SZ[s];
            double gap = gx * gx + gy * gy + gz * gz;
            int subs = detail > 0 ? 1 : (gap > 0.12249999999999998 ? 3 : (gap > 0.0225 ? 2 : 1));

            for (int sub = 1; sub <= subs; sub++) {
               float t = (float)sub / subs;
               double x = cr(SX[s0], SX[s], SX[s2], SX[s3], t);
               double dx = x - lx;
               double z;
               double dz;
               double y;
               double dy;
               double seg = Math.sqrt(
                  dx * dx + (dy = (y = cr(SY[s0], SY[s], SY[s2], SY[s3], t)) - ly) * dy + (dz = (z = cr(SZ[s0], SZ[s], SZ[s2], SZ[s3], t)) - lz) * dz
               );
               if (used + seg >= maxLen) {
                  double k = seg < 1.0E-9 ? 0.0 : (maxLen - used) / seg;
                  Vfx.X[n] = lx + dx * k;
                  Vfx.Y[n] = ly + dy * k;
                  Vfx.Z[n] = lz + dz * k;
                  return n + 1;
               }

               used += seg;
               Vfx.X[n] = x;
               Vfx.Y[n] = y;
               Vfx.Z[n] = z;
               n++;
               lx = x;
               ly = y;
               lz = z;
            }
         }

         return n;
      }
   }

   private static double cr(double p0, double p1, double p2, double p3, float t) {
      float t2 = t * t;
      float t3 = t2 * t;
      return 0.5 * (2.0 * p1 + (-p0 + p2) * t + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2 + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
   }

   int blockedForTest() {
      return this.blocked;
   }
}
