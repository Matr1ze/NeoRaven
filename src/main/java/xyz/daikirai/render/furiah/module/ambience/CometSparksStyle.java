package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class CometSparksStyle extends AmbientStyleBase {
   private static final int TAIL = 16;
   final SliderSetting tailLength;
   final SliderSetting curve;
   final SliderSetting dots;
   private final float[] flow = new float[3];

   CometSparksStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.COMET_SPARKS, spec());
      this.tailLength = this.setting(new SliderSetting("comets.tail", "Tail Length", "How long the comet tails are.", 0.3, 4.0, 2.2, 0.05).unit(" blocks"));
      this.curve = this.setting(new SliderSetting("comets.curve", "Curve Amount", "How much the sparks curve.", 0.0, 100.0, 50.0, 5.0).unit("%"));
      this.dots = this.setting(
         new SliderSetting("comets.dots", "Drifting Dots", "How many of them are slow, soft drifting dots.", 0.0, 100.0, 40.0, 5.0).unit("%")
      );
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.008F;
      s.nearBias = 1.4F;
      s.opacity = 95.0F;
      s.followWind = 90.0F;
      s.heightMin = 0.5F;
      s.heightMax = 10.0F;
      s.heightLimit = 40.0F;
      s.cap = 800;
      s.tail = 16;
      s.light = true;
      s.defaultColor = -1;
      s.densityHelp = "How many sparks and dots there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         boolean dot = this.rng.next() * 100.0F < this.dots.get();
         p.d[i] = dot ? 1.0F : 0.0F;
         p.a[i] = this.rng.signed() * 0.8F;
         p.b[i] = this.rng.signed() * 1.6F;
         p.c[i] = this.rng.next() * 6.283F;
         p.e[i] = this.rng.next() * 6.283F;
         p.size[i] = dot ? this.rng.range(0.7F, 1.6F) : this.rng.range(0.6F, 1.2F);
         p.life[i] = dot ? this.rng.range(6.0F, 14.0F) : this.rng.range(1.6F, 4.0F);
         p.age[i] = fresh ? this.rng.next() * p.life[i] * 0.7F : 0.0F;
         p.vx[i] = this.rng.range(0.7F, 1.5F);
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      if (p.age[i] >= p.life[i]) {
         return false;
      } else {
         float sp = this.speed.getFloat();
         this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
         double nz;
         double ny;
         double nx;
         if (p.d[i] > 0.5F) {
            float wx = 0.25F * (float)Math.sin(p.age[i] * 0.7F + p.c[i]);
            float wz = 0.25F * (float)Math.cos(p.age[i] * 0.6F + p.e[i]);
            nx = p.x[i] + (this.flow[0] * 0.45F + wx) * sp * dt;
            ny = p.y[i] + (this.flow[1] * 0.4F + 0.12F * (float)Math.sin(p.age[i] * 0.9F + p.c[i])) * sp * dt;
            nz = p.z[i] + (this.flow[2] * 0.45F + wz) * sp * dt;
         } else {
            float hs = Math.max(2.5F, (float)Math.sqrt(this.flow[0] * this.flow[0] + this.flow[2] * this.flow[2]));
            float spd = hs * p.vx[i] * 1.35F;
            float bend = this.curve.getFloat() / 100.0F;
            p.a[i] = p.a[i] + p.b[i] * bend * dt * 1.4F;
            float base = (float)Math.atan2(this.flow[2], this.flow[0]);
            if (this.flow[0] == 0.0F && this.flow[2] == 0.0F) {
               base = (float)Math.atan2(env.wind.dirZ, env.wind.dirX);
            }

            float head = base + p.a[i];
            nx = p.x[i] + Math.cos(head) * spd * sp * dt;
            nz = p.z[i] + Math.sin(head) * spd * sp * dt;
            ny = p.y[i] + (0.9F * bend * (float)Math.sin(p.age[i] * 2.2F + p.c[i]) + this.flow[1] * 0.5F) * sp * dt;
         }

         if (env.solidAt(nx, ny, nz)) {
            return p.d[i] > 0.5F;
         } else {
            p.x[i] = nx;
            p.y[i] = ny;
            p.z[i] = nz;
            return true;
         }
      }
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float glowK = this.glowK();
      float s0 = 0.075F * this.size.getFloat();
      RenderState.additive();
      float len = this.tailLength.getFloat() * this.size.getFloat();

      for (int i = 0; i < this.pool.count; i++) {
         int n;
         float a;
         if (!(this.pool.d[i] > 0.5F) && !((a = this.spark(env, i, r, len)) <= 0.0F) && (n = this.tail(env, i, len)) >= 2) {
            for (int k = 0; k < n; k++) {
               float t = (float)k / (n - 1);
               Vfx.W[k] = s0 * this.pool.size[i] * 1.1F * (1.0F - t * 0.8F);
               Vfx.C[k] = ColorUtil.mulAlpha(this.color.getColor(t), a * 0.8F * (float)Math.pow(1.0F - t, 1.3));
            }

            Vfx.ribbon(Textures.LINE_GLOW, 0, n, false);

            for (int var20 = 2; var20 < n; var20 += 2) {
               float t = (float)var20 / (n - 1);
               Vfx.sprite(
                  Textures.SPARK,
                  Vfx.X[var20],
                  Vfx.Y[var20],
                  Vfx.Z[var20],
                  s0 * this.pool.size[i] * 1.1F * (1.0F - t * 0.6F),
                  0.0F,
                  ColorUtil.mulAlpha(this.color.getColor(t), a * 0.8F * (1.0F - t))
               );
            }
         }
      }

      Vfx.flush();

      for (int var15 = 0; var15 < this.pool.count; var15++) {
         boolean dot = this.pool.d[var15] > 0.5F;
         float a = dot ? this.dot(env, var15, r) : this.spark(env, var15, r, len);
         if (!(a <= 0.0F)) {
            int c = this.color.getColor(0.0F);
            float s = s0 * this.pool.size[var15] * (dot ? 1.6F : 1.0F);
            if (glowK > 0.0F) {
               Vfx.sprite(Textures.GLOW, this.rx, this.ry, this.rz, s * (dot ? 4.5F : 6.5F), 0.0F, ColorUtil.mulAlpha(c, a * (dot ? 0.32F : 0.5F) * glowK));
            }
         }
      }

      Vfx.flush();

      for (int var16 = 0; var16 < this.pool.count; var16++) {
         boolean dot = this.pool.d[var16] > 0.5F;
         float a = dot ? this.dot(env, var16, r) : this.spark(env, var16, r, len);
         if (!(a <= 0.0F)) {
            int c = this.color.getColor(0.0F);
            float s = s0 * this.pool.size[var16] * (dot ? 1.6F : 1.0F);
            Vfx.sprite(Textures.SPARK, this.rx, this.ry, this.rz, s * (dot ? 1.8F : 1.5F), 0.0F, ColorUtil.mulAlpha(dot ? c : Neon.tint(c), a));
         }
      }

      Vfx.flush();
   }

   private float spark(AmbientEnv env, int i, float r, float len) {
      if (!this.place(env, i, len)) {
         return 0.0F;
      } else {
         AmbientPool p = this.pool;
         float life = Easing.smoothstep(0.0F, 0.25F, p.age[i]) * (1.0F - Easing.smoothstep(p.life[i] - 0.5F, p.life[i], p.age[i]));
         float a = this.fogged(this.fade(env, i, r)) * life;
         return a > 0.004F ? a : 0.0F;
      }
   }

   private float dot(AmbientEnv env, int i, float r) {
      if (!this.place(env, i, 0.3F)) {
         return 0.0F;
      } else {
         AmbientPool p = this.pool;
         float life = Easing.smoothstep(0.0F, 1.5F, p.age[i]) * (1.0F - Easing.smoothstep(p.life[i] - 2.0F, p.life[i], p.age[i]));
         float tw = 0.65F + 0.35F * (float)Math.sin(env.time * 2.3 + p.e[i]);
         float a = this.fogged(this.fade(env, i, r)) * life * tw * 0.7F;
         return a > 0.004F ? a : 0.0F;
      }
   }

   private int tail(AmbientEnv env, int i, float maxLen) {
      AmbientPool p = this.pool;
      int n = 0;
      Vfx.X[n] = this.rx;
      Vfx.Y[n] = this.ry;
      Vfx.Z[n] = this.rz;
      n++;
      double used = 0.0;
      double lx = this.rx;
      double ly = this.ry;
      double lz = this.rz;

      for (int k = 1; k < p.filled[i] && n < 17; k++) {
         int h = p.historyIndex(i, k);
         double x = p.hx[h] - env.viewX;
         double dx = x - lx;
         double z;
         double dz;
         double y;
         double dy;
         double seg = Math.sqrt(dx * dx + (dy = (y = p.hy[h] - env.viewY) - ly) * dy + (dz = (z = p.hz[h] - env.viewZ) - lz) * dz);
         if (used + seg >= maxLen) {
            double kk = seg < 1.0E-9 ? 0.0 : (maxLen - used) / seg;
            Vfx.X[n] = lx + dx * kk;
            Vfx.Y[n] = ly + dy * kk;
            Vfx.Z[n] = lz + dz * kk;
            return n + 1;
         }

         used += seg;
         Vfx.X[n] = x;
         Vfx.Y[n] = y;
         Vfx.Z[n] = z;
         lx = x;
         ly = y;
         lz = z;
         n++;
      }

      return n;
   }
}
