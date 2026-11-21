package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class CircuitLinesStyle extends AmbientStyleBase {
   private static final int MAX_POINTS = 6;
   final SliderSetting traceLength;
   final SliderSetting rise;
   final SliderSetting pulseSpeed;
   final SliderSetting thickness;
   final BooleanSetting nodes;
   private final Rng shape = new Rng();
   private final float[] pu = new float[6];
   private final float[] pv = new float[6];
   private final float[] along = new float[6];
   private int points;
   private final double[] px = new double[6];
   private final double[] py = new double[6];
   private final double[] pz = new double[6];
   private double dirX;
   private double dirZ;
   private final float[] flow = new float[3];
   private double qx;
   private double qy;
   private double qz;

   CircuitLinesStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.CIRCUIT_LINES, spec());
      this.traceLength = this.setting(new SliderSetting("circuits.length", "Length", "How long each trace is.", 1.0, 6.0, 3.0, 0.25).unit(" blocks"));
      this.rise = this.setting(new SliderSetting("circuits.rise", "Rise Speed", "How fast the traces rise.", 0.0, 3.0, 1.0, 0.05).unit("×"));
      this.pulseSpeed = this.setting(new SliderSetting("circuits.pulse", "Pulse Speed", "How fast the data pulses run.", 0.2, 3.0, 1.0, 0.05).unit("×"));
      this.thickness = this.setting(new SliderSetting("circuits.thickness", "Thickness", "How thick the traces are.", 0.3, 3.0, 1.0, 0.05).unit("×"));
      this.nodes = this.setting(new BooleanSetting("circuits.nodes", "Nodes", "Dots at the ends of every trace and at its bends.", true));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.0016F;
      s.nearBias = 1.25F;
      s.opacity = 90.0F;
      s.followWind = 10.0F;
      s.heightMin = 1.0F;
      s.heightMax = 10.0F;
      s.heightLimit = 40.0F;
      s.cap = 300;
      s.densityHelp = "How many traces there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         p.a[i] = this.rng.next() * 6.283F;
         p.b[i] = this.rng.next();
         float lanes = this.rng.next();
         p.d[i] = lanes < 0.4F ? 1.0F : (lanes < 0.75F ? 2.0F : 3.0F);
         p.size[i] = this.rng.range(0.7F, 1.3F);
         p.life[i] = this.rng.range(6.0F, 12.0F);
         p.age[i] = fresh ? this.rng.next() * p.life[i] * 0.6F : 0.0F;
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
         p.x[i] = p.x[i] + this.flow[0] * 0.25F * sp * dt;
         p.z[i] = p.z[i] + this.flow[2] * 0.25F * sp * dt;
         p.y[i] = p.y[i] + 0.28F * this.rise.getFloat() * sp * dt;
         return true;
      }
   }

   private void build(int i) {
      this.shape.seed(this.pool.seed[i], 91);
      float len = this.traceLength.getFloat() * this.pool.size[i] * this.size.getFloat();
      this.dirX = Math.cos(this.pool.a[i]);
      this.dirZ = Math.sin(this.pool.a[i]);
      int bends = this.shape.next() < 0.55F ? 1 : 2;
      float bendLen = len * this.shape.range(0.12F, 0.2F);
      float runs = len - bends * bendLen;
      float u = 0.0F;
      float v = 0.0F;
      float used = 0.0F;
      this.points = 0;
      this.pu[this.points] = u;
      this.pv[this.points] = v;
      this.along[this.points++] = 0.0F;

      for (int k = 0; k <= bends; k++) {
         float run = runs
            * (
               bends == 1
                  ? (k == 0 ? this.shape.range(0.35F, 0.6F) : 1.0F)
                  : (k == 0 ? this.shape.range(0.25F, 0.4F) : (k == 1 ? this.shape.range(0.2F, 0.35F) : 1.0F))
            );
         run = Math.min(run, len - used - (bends - k) * bendLen);
         if (k == bends) {
            run = len - used;
         }

         this.pu[this.points] = u;
         this.pv[this.points] = v += run;
         this.along[this.points++] = used += run;
         if (k < bends) {
            float side = this.shape.next() < 0.5F ? -1.0F : 1.0F;
            this.pu[this.points] = u += side * bendLen;
            this.pv[this.points] = v;
            this.along[this.points++] = used += bendLen;
         }
      }
   }

   private void lane(double bx, double by, double bz, int lane, int lanes, float gap) {
      float off = (lane - (lanes - 1) / 2.0F) * gap;

      for (int k = 0; k < this.points; k++) {
         float nu = 0.0F;
         float nv = 0.0F;
         float n1u = 0.0F;
         float n1v = 0.0F;
         float dv;
         float du;
         float l;
         if (k > 0 && (l = (float)Math.sqrt((du = this.pu[k] - this.pu[k - 1]) * du + (dv = this.pv[k] - this.pv[k - 1]) * dv)) > 1.0E-5F) {
            n1u = -dv / l;
            n1v = du / l;
            nu += n1u;
            nv += n1v;
         }

         if (k + 1 < this.points && (l = (float)Math.sqrt((du = this.pu[k + 1] - this.pu[k]) * du + (dv = this.pv[k + 1] - this.pv[k]) * dv)) > 1.0E-5F) {
            if (n1u == 0.0F && n1v == 0.0F) {
               n1u = -dv / l;
               n1v = du / l;
            }

            nu += -dv / l;
            nv += du / l;
         }

         float nl = (float)Math.sqrt(nu * nu + nv * nv);
         float ou = 0.0F;
         float ov = 0.0F;
         if (nl > 1.0E-5F) {
            float var29;
            float var30;
            float k2 = Math.max(0.5F, (var29 = nu / nl) * n1u + (var30 = nv / nl) * n1v);
            ou = var29 * off / k2;
            ov = var30 * off / k2;
         }

         double u = this.pu[k] + ou;
         double v = this.pv[k] + ov;
         this.px[k] = bx + this.dirX * u;
         this.py[k] = by + v;
         this.pz[k] = bz + this.dirZ * u;
      }
   }

   private void at(float d) {
      for (int k = 1; k < this.points; k++) {
         if (d <= this.along[k] || k == this.points - 1) {
            float seg = this.along[k] - this.along[k - 1];
            float t = seg <= 1.0E-5F ? 0.0F : Easing.clamp01((d - this.along[k - 1]) / seg);
            this.qx = this.px[k - 1] + (this.px[k] - this.px[k - 1]) * t;
            this.qy = this.py[k - 1] + (this.py[k] - this.py[k - 1]) * t;
            this.qz = this.pz[k - 1] + (this.pz[k] - this.pz[k - 1]) * t;
            return;
         }
      }

      this.qx = this.px[0];
      this.qy = this.py[0];
      this.qz = this.pz[0];
   }

   private float alpha(AmbientEnv env, int i, float r) {
      AmbientPool p = this.pool;
      float life = Easing.smoothstep(0.0F, 1.2F, p.age[i]) * (1.0F - Easing.smoothstep(p.life[i] - 1.6F, p.life[i], p.age[i]));
      return this.fogged(this.fade(env, i, r)) * life;
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float w = 0.026F * this.thickness.getFloat() * this.size.getFloat();
      float gap = w * 5.5F;
      float glowK = this.glowK();
      RenderState.additive();

      for (int layer = glowK > 0.0F ? 0 : 1; layer < 3; layer++) {
         for (int i = 0; i < this.pool.count; i++) {
            float len = this.traceLength.getFloat() * this.pool.size[i] * this.size.getFloat();
            float a;
            if (this.place(env, i, len) && !((a = this.alpha(env, i, r)) <= 0.004F)) {
               double bx = this.rx;
               double by = this.ry;
               double bz = this.rz;
               this.build(i);
               int lanes = (int)this.pool.d[i];

               for (int ln = 0; ln < lanes; ln++) {
                  this.lane(bx, by, bz, ln, lanes, gap);
                  if (layer < 2) {
                     this.traces(layer, a, w, glowK);
                  } else {
                     this.pulses(env, i, ln, a, w);
                     if (this.nodes.get()) {
                        this.dots(a, w, glowK, ln == lanes / 2);
                     }
                  }
               }
            }
         }

         Vfx.flush();
      }
   }

   private void traces(int layer, float a, float w, float glowK) {
      int tex = layer == 0 ? Textures.LINE_GLOW : Textures.LINE_CORE;
      float width = layer == 0 ? w * 5.0F : w;
      float la = layer == 0 ? a * 0.3F * glowK : a * 0.95F;
      float total = Math.max(0.01F, this.along[this.points - 1]);

      for (int k = 1; k < this.points; k++) {
         int c0 = this.color.getColor(this.along[k - 1] / total);
         int c1 = this.color.getColor(this.along[k] / total);
         if (layer == 1) {
            c0 = Neon.tint(c0);
            c1 = Neon.tint(c1);
         }

         Vfx.line(
            tex,
            this.px[k - 1],
            this.py[k - 1],
            this.pz[k - 1],
            this.px[k],
            this.py[k],
            this.pz[k],
            width,
            ColorUtil.mulAlpha(c0, la),
            ColorUtil.mulAlpha(c1, la)
         );
      }
   }

   private void pulses(AmbientEnv env, int i, int lane, float a, float w) {
      float total = this.along[this.points - 1];
      if (!(total <= 0.05F)) {
         double t = env.time * this.pulseSpeed.getFloat() * 1.6 / Math.max(0.5, (double)total) + this.pool.b[i] + lane * 0.37;
         double ph = t % 1.0;
         float d = (float)(ph * total);

         float dd;
         for (int s = 0; s < 4 && !((dd = d - s * 0.08F) < 0.0F); s++) {
            this.at(dd);
            int c = Neon.tint(this.color.getColor(dd / total));
            Vfx.sprite(Textures.SPARK, this.qx, this.qy, this.qz, w * (7.0F - s * 1.3F), 0.0F, ColorUtil.mulAlpha(c, a * (1.0F - s * 0.22F)));
         }
      }
   }

   private void dots(float a, float w, float glowK, boolean vias) {
      for (int k = 0; k < this.points; k++) {
         boolean end = k == 0 || k == this.points - 1;
         if (end || vias) {
            int c = this.color.getColor((float)k / (this.points - 1));
            float s = end ? 5.5F : 3.8F;
            Vfx.sprite(Textures.SPARK, this.px[k], this.py[k], this.pz[k], w * s, 0.0F, ColorUtil.mulAlpha(Neon.tint(c), a * 0.9F));
            if (end && glowK > 0.0F) {
               Vfx.sprite(Textures.GLOW, this.px[k], this.py[k], this.pz[k], w * 13.0F, 0.0F, ColorUtil.mulAlpha(c, a * 0.22F * glowK));
            }
         }
      }
   }
}
