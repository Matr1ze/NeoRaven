package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.kit.Glitch;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Liquid;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
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

public final class QuicksilverStyle extends HitStyleBase {
   private static final float ARRIVE = 0.06666666F;
   private static final float SPREAD = 0.19999999F;
   private static final float RIPPLE = 0.45F;
   private static final float GATHER = 0.55F;
   private static final float SNAP = 0.6166667F;
   private static final float EVAPORATE = 0.82F;
   private static final float GATHER_CRIT = 0.52F;
   private static final float SPIKE = 0.66F;
   private static final float SECONDS = 0.6F;
   private static final int MAX_DROPLETS = 20;
   private static final int MAX_BALLS = 24;
   private static final int POOL = 8;
   private static final double DISC = 0.3;
   private static final double THICK = 0.022;
   private static final int RINGS = 12;
   private static final int SEGS = 40;
   private static final double WAVES = Math.PI * 6;
   private static final double WAVE_SPEED = Math.PI * 14;
   private static final double REGION = 0.56;
   private static final double BLOB = 0.14;
   private static final double DROPLET = 0.066;
   private static final double BIG_DROP = 0.18;
   private static final double BREAK = 0.12;
   private static final double SPEED = 2.6;
   private static final double SPILL = 3.0;
   private static final double DRAG = 1.6;
   private static final double GRAVITY = 7.0;
   private static final double MEET = 0.12;
   private static final double SWING = 0.3;
   private static final float RIM = 0.22F;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting droplets;
   public final SliderSetting size;
   public final SliderSetting merge;
   public final BooleanSetting disc;
   public final ModeSetting<QuicksilverStyle.Quality> quality;
   private final ArrayDeque<QuicksilverStyle.State> pool = new ArrayDeque<>();
   private double cx;
   private double cy;
   private double cz;
   private double sc;
   private Basis b;
   private double bodyR;
   private double bodyU;
   private double halfW;
   private double halfH;
   private final double[] segCos = new double[40];
   private final double[] segSin = new double[40];
   private final double[] edge = new double[40];
   private final double[] edgeSlope = new double[40];
   private final double[] shiftSin = new double[40];
   private final double[] shiftCos = new double[40];
   private final double[] shiftSlope = new double[40];
   private final double[] ringRho = new double[13];
   private final double[] lens = new double[13];
   private final double[] lensSlope = new double[13];
   private final double[] crownH = new double[13];
   private final double[] crownSlope = new double[13];
   private final double[] env = new double[13];
   private final double[] envSlope = new double[13];
   private final double[] waveSin = new double[13];
   private final double[] waveCos = new double[13];
   private final double[] at;

   public QuicksilverStyle() {
      super(
         HitStyle.QUICKSILVER,
         0.6F,
         new ThemeTint()
      );

      for (int i = 0; i <= 12; i++) {
         this.ringRho[i] = 1.0 - Math.pow(1.0 - i / 12.0, 1.6);
      }

      this.at = new double[3];
      this.material = this.materialSetting("quicksilver", Material.CHROME, "What the liquid is made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("quicksilver", Finish.METALLIC, this.material);
      this.droplets = this.look(
         this.setting(new SliderSetting("quicksilver.droplets", "Droplets", "How many droplets the splash breaks into.", 3.0, 10.0, 6.0, 1.0))
      );
      this.size = this.look(
         this.setting(new SliderSetting("quicksilver.size", "Size", "How big the splash and its droplets are.", 50.0, 150.0, 100.0, 5.0).unit("%"))
      );
      this.merge = this.setting(
         new SliderSetting("quicksilver.merge", "Merge Strength", "How far apart two droplets start to flow into one.", 0.0, 100.0, 60.0, 5.0).unit("%")
      );
      this.disc = this.setting(new BooleanSetting("quicksilver.disc", "Disc", "The drop spreads into a thin, rippling mirror disc before it breaks up.", true));
      this.quality = this.setting(
         new ModeSetting<>(
            "quicksilver.quality",
            "Quality",
            "How fine the liquid's surface is: Low, Medium or High (a little more work each frame).",
            QuicksilverStyle.Quality.class,
            QuicksilverStyle.Quality.MEDIUM
         )
      );
   }

   @Override
   public void start(HitFx fx) {
      QuicksilverStyle.State s = this.pool.poll();
      if (s == null) {
         s = new QuicksilverStyle.State();
      }

      int n;
      s.droplets = n = Math.min(20, fx.count(this.droplets.getInt(), true));
      int pairs = (n + 1) / 2;
      double turn = hash(fx.seed, 1);

      for (int i = 0; i < n; i++) {
         int id = fx.seed + i * 37;
         int pair = i >> 1;
         int pid = fx.seed + pair * 53;
         double k = (pair + 0.25 + 0.5 * hash(pid, 2) + turn) / pairs;
         k -= Math.floor(k);
         s.way[i] = Math.toRadians(-55.0 + 290.0 * k);
         s.swing[i] = (i ^ 1) >= n ? 0 : ((i & 1) == 0 ? 1 : -1);
         s.cone[i] = Math.toRadians(12.0 + 18.0 * hash(pid, 3));
         s.speed[i] = 0.85 + 0.3 * hash(pid, 4);
         s.size[i] = 0.85 + 0.3 * hash(id, 5);
         s.lift[i] = 0.6 * hash(pid, 6) - 0.1;
         s.along[i] = hash(id, 7);
      }

      s.phase1 = hash(fx.seed, 8) * (Math.PI * 2);
      s.phase2 = hash(fx.seed, 9) * (Math.PI * 2);
      fx.data = s;
   }

   @Override
   public void finish(HitFx fx) {
      if (fx.data instanceof QuicksilverStyle.State && this.pool.size() < 8) {
         this.pool.push((QuicksilverStyle.State)fx.data);
      }

      fx.data = null;
   }

   @Override
   public int passes(HitFx fx) {
      int body = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return fx.lod ? body : body | FxPass.GLOW.bit();
   }

   private void setUp(HitFx fx, FxFrame f) {
      this.b = fx.basis;
      this.sc = scale(fx) * this.size.getFloat() / 100.0;
      this.cx = f.rx(fx.ix()) - this.b.fx * 0.07;
      this.cy = f.ry(fx.iy()) - this.b.fy * 0.07;
      this.cz = f.rz(fx.iz()) - this.b.fz * 0.07;
      double dx = fx.cx() - fx.ix();
      double dy = fx.cy() - fx.iy();
      double dz = fx.cz() - fx.iz();
      this.bodyR = dx * this.b.rx + dy * this.b.ry + dz * this.b.rz;
      this.bodyU = dx * this.b.ux + dy * this.b.uy + dz * this.b.uz;
      this.halfW = fx.hit.width * 0.71 + 0.05;
      this.halfH = fx.hit.height * 0.5 + 0.05;
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      QuicksilverStyle.State s = (QuicksilverStyle.State)fx.data;
      if (s != null) {
         float p = fx.progress();
         float alpha = fx.alpha();
         if (!(alpha <= 0.003F)) {
            this.setUp(fx, f);
            if (pass == FxPass.GLOW) {
               this.glints(fx, s, p, alpha);
            } else {
               boolean custom = this.material.get() == Material.CUSTOM;
               int tint = custom ? this.color.getColor(0.5F) : -1;
               float a = custom ? alpha * (tint >>> 24) / 255.0F : alpha;
               MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.22F, -1, true, pass == FxPass.SOLID);
               double r;
               if (this.disc.get() && (r = this.discRadius(p, fx.crit)) > 0.004 * this.sc) {
                  this.disc(fx, s, p, r, a, tint);
               }

               this.balls(fx, s, p);
               if (s.field.balls() > 0) {
                  double h = 0.56 * this.sc / this.quality.get().cells;
                  s.field.mesh(h, this.cx, this.cy, this.cz, f.distanceSqToEye(fx.ix(), fx.iy(), fx.iz()), a, tint, fx.keep);
               }

               MeshBatch.end();
            }
         }
      }
   }

   private double discRadius(float p, boolean crit) {
      double r = 0.3 * this.sc;
      if (p < 0.06666666F) {
         return 0.0;
      } else if (p < 0.19999999F) {
         return r * Easing.outBack(Easing.clamp01((p - 0.06666666F) / 0.13333333F), 1.4F);
      } else if (p < 0.45F) {
         return r;
      } else {
         float g = crit ? 0.52F : 0.55F;
         return r * (1.0 - Easing.inCubic(Easing.clamp01((p - 0.45F) / (g - 0.45F))));
      }
   }

   private void disc(HitFx fx, QuicksilverStyle.State s, float p, double radius, float alpha, int tint) {
      float g = fx.crit ? 0.52F : 0.55F;
      float spread = Easing.clamp01((p - 0.06666666F) / 0.13333333F);
      float gather = Easing.clamp01((p - 0.45F) / (g - 0.45F));
      double tau = p * 0.6F;
      double thick = 0.022 * this.sc * (1.0 + 1.6 * (1.0 - spread) + 1.4 * gather);
      double thickBack = thick * 0.45;
      double crown = 0.05 * this.sc * Easing.smoothstep(0.3F, 1.0F, spread) * (1.0F - Easing.smoothstep(0.19999999F, 0.325F, p));
      double ripple = 0.013 * this.sc * Easing.smoothstep(0.096666664F, 0.19999999F, p) * (1.0F - Easing.smoothstep(0.45F, g, p));
      double wobble = 0.03 + 0.07 * (1.0F - Easing.smoothstep(0.19999999F, 0.45F, p)) + 0.05 * gather;

      for (int j = 0; j < 40; j++) {
         double th = (Math.PI * 2) * j / 40.0;
         double a5 = 5.0 * th + s.phase1 + 9.0 * tau;
         double a3 = 3.0 * th - s.phase2 - 6.0 * tau;
         this.segCos[j] = Math.cos(th);
         this.segSin[j] = Math.sin(th);
         this.edge[j] = radius * (1.0 + wobble * Math.sin(a5) + 0.5 * wobble * Math.sin(a3));
         this.edgeSlope[j] = radius * wobble * (5.0 * Math.cos(a5) - 1.5 * Math.cos(a3));
         double shift = 0.6 * Math.sin(3.0 * th + s.phase1);
         this.shiftSin[j] = Math.sin(shift);
         this.shiftCos[j] = Math.cos(shift);
         this.shiftSlope[j] = 1.8 * Math.cos(3.0 * th + s.phase1);
      }

      for (int i = 0; i <= 12; i++) {
         double rho = this.ringRho[i];
         double e;
         this.lens[i] = e = Math.sqrt(Math.max(1.0E-6, 1.0 - rho * rho));
         this.lensSlope[i] = -rho / e;
         double c = rho - 0.8;
         double bump;
         this.crownH[i] = bump = crown * Math.exp(-c * c / 0.016);
         this.crownSlope[i] = bump * (-2.0 * c / 0.016);
         double sq = Math.sqrt(rho);
         this.env[i] = sq * (1.0 - rho * rho);
         this.envSlope[i] = rho > 1.0E-6 ? (1.0 - rho * rho) / (2.0 * sq) - 2.0 * rho * sq : 0.0;
         double wave = (Math.PI * 6) * rho - (Math.PI * 14) * tau;
         this.waveSin[i] = Math.sin(wave);
         this.waveCos[i] = Math.cos(wave);
      }

      for (int side = 0; side < 2; side++) {
         boolean front = side == 0;
         int base = MeshBatch.stage(481);
         double h0 = front ? thick + this.crownH[0] : -thickBack;
         this.discVertex(fx, base, 0, 0, h0, 0.0, 0.0, 0, front, alpha, tint);

         for (int i = 1; i <= 12; i++) {
            for (int j = 0; j < 40; j++) {
               double ht;
               double hr;
               double h;
               if (front) {
                  double sw = this.waveSin[i] * this.shiftCos[j] + this.waveCos[i] * this.shiftSin[j];
                  double cw = this.waveCos[i] * this.shiftCos[j] - this.waveSin[i] * this.shiftSin[j];
                  h = this.lens[i] * (thick + this.crownH[i]) + ripple * sw * this.env[i];
                  hr = this.lensSlope[i] * (thick + this.crownH[i])
                     + this.lens[i] * this.crownSlope[i]
                     + ripple * ((Math.PI * 6) * cw * this.env[i] + sw * this.envSlope[i]);
                  ht = ripple * cw * this.shiftSlope[j] * this.env[i];
               } else {
                  h = -this.lens[i] * thickBack;
                  hr = -this.lensSlope[i] * thickBack;
                  ht = 0.0;
               }

               this.discVertex(fx, base + 1 + (i - 1) * 40 + j, i, j, h, hr, ht, 1, front, alpha, tint);
            }
         }

         for (int j = 0; j < 40; j++) {
            int j2 = (j + 1) % 40;
            MeshBatch.tri(base, base + 1 + j, base + 1 + j2);

            for (int i = 1; i < 12; i++) {
               int a0 = base + 1 + (i - 1) * 40;
               MeshBatch.quad(a0 + j, a0 + j2, a0 + 40 + j2, a0 + 40 + j);
            }
         }
      }
   }

   private void discVertex(HitFx fx, int v, int i, int j, double h, double hr, double ht, int away, boolean front, float alpha, int tint) {
      double ox = -this.b.fx;
      double oy = -this.b.fy;
      double oz = -this.b.fz;
      double nz;
      double ny;
      double nx;
      double z;
      double y;
      double x;
      if (away == 0) {
         x = this.cx + ox * h;
         y = this.cy + oy * h;
         z = this.cz + oz * h;
         double sgn = front ? 1.0 : -1.0;
         nx = ox * sgn;
         ny = oy * sgn;
         nz = oz * sgn;
      } else {
         double rho = this.ringRho[i];
         double e = this.edge[j];
         double c = this.segCos[j];
         double s = this.segSin[j];
         double rx = this.b.rx * c + this.b.ux * s;
         double ry = this.b.ry * c + this.b.uy * s;
         double rz = this.b.rz * c + this.b.uz * s;
         double tx = -this.b.rx * s + this.b.ux * c;
         double ty = -this.b.ry * s + this.b.uy * c;
         double tz = -this.b.rz * s + this.b.uz * c;
         double rr = e * rho;
         x = this.cx + rx * rr + ox * h;
         y = this.cy + ry * rr + oy * h;
         z = this.cz + rz * rr + oz * h;
         double nr = -hr * e;
         double nt = hr * this.edgeSlope[j] - e * ht / rho;
         double no = e * e;
         if (!front) {
            nr = -nr;
            nt = -nt;
            no = -no;
         }

         double l;
         if ((l = Math.sqrt((nx = rx * nr + tx * nt + ox * no) * nx + (ny = ry * nr + ty * nt + oy * no) * ny + (nz = rz * nr + tz * nt + oz * no) * nz))
            < 1.0E-12) {
            nx = rx;
            ny = ry;
            nz = rz;
         } else {
            nx /= l;
            ny /= l;
            nz /= l;
         }
      }

      float k = alpha * fx.keep.cover(x, y, z);
      MeshBatch.vertex(v, x, y, z, nx, ny, nz, Math.round(255.0F * Easing.clamp01(k)) << 24 | tint & 16777215, 0.06F);
   }

   private void balls(HitFx fx, QuicksilverStyle.State s, float p) {
      Liquid.Field fl = s.field;
      fl.begin(1.5 + 0.9 * this.merge.getFloat() / 100.0);
      double ox = -this.b.fx;
      double oy = -this.b.fy;
      double oz = -this.b.fz;
      boolean withDisc = this.disc.get();
      float g = fx.crit ? 0.52F : 0.55F;
      if (p < 0.06666666F) {
         double d = 0.5 * this.sc * (1.0 - p / 0.06666666F);
         fl.ball(this.cx + ox * d, this.cy + oy * d, this.cz + oz * d, 0.1 * this.sc, 1.0, ox, oy, oz, 0.6);
      } else {
         float q;
         double r;
         if (withDisc && p < 0.19999999F && (r = 0.1 * this.sc * (1.0 - Easing.outQuad(q = (p - 0.06666666F) / 0.13333333F))) > 0.015 * this.sc) {
            fl.ball(this.cx, this.cy, this.cz, r, 1.0, ox, oy, oz, 1.0 + 2.5 * q);
         }
      }

      double big = (fx.crit ? 0.18 : 0.14) * this.sc;
      double blob = 0.0;
      double squash = 1.0;
      if (!withDisc && p >= 0.06666666F) {
         float q2 = Easing.clamp01((p - 0.06666666F) / 0.13333333F);
         float flat = q2 * (1.0F - Easing.smoothstep(0.45F, g, p));
         blob = 0.14 * this.sc * (0.75 + 0.25 * q2) + (big - 0.14 * this.sc) * Easing.smoothstep(0.45F, g, p);
         squash = 1.0 + 1.6 * flat + 0.35 * flat * Math.sin(p * 0.6F * 9.0 * (Math.PI * 2) + s.phase1);
      } else if (withDisc && p >= 0.45F) {
         blob = big * Easing.smoothstep(0.45F + 0.3F * (g - 0.45F), g, p);
      }

      if (fx.crit) {
         this.spike(fx, fl, p, blob, squash);
      } else if (blob > 0.0 && (blob = blob * (1.0 - Easing.smoothstep(g, 0.6166667F, p))) > 0.004 * this.sc) {
         fl.ball(this.cx, this.cy, this.cz, blob, 1.0, ox, oy, oz, squash);
      }

      for (int i = 0; i < s.droplets; i++) {
         double r2 = this.droplet(fx, s, i, p);
         if (r2 > 0.004 * this.sc) {
            fl.ball(this.at[0], this.at[1], this.at[2], r2, 1.0, 0.0, 1.0, 0.0, 1.0);
         }
      }
   }

   private void spike(HitFx fx, Liquid.Field fl, float p, double blob, double squash) {
      double ox = -this.b.fx;
      double oy = -this.b.fy;
      double oz = -this.b.fz;
      if (p < 0.52F) {
         if (blob > 0.004 * this.sc) {
            fl.ball(this.cx, this.cy, this.cz, blob, 1.0, ox, oy, oz, squash);
         }
      } else {
         float q = Easing.clamp01((p - 0.52F) / 0.14000005F);
         float gone = Easing.clamp01((p - 0.66F) / 0.05F);
         if (!(gone >= 1.0F)) {
            float e = Easing.inOutCubic(q);
            double k = 1.0 - 0.7 * e;
            double r = 0.18 * this.sc * Math.cbrt(k) * (1.0 - gone);
            double shift = 0.2 * this.sc * e;
            double x = this.cx + this.b.fx * shift;
            double y = this.cy + this.b.fy * shift;
            double z = this.cz + this.b.fz * shift;
            fl.ball(x, y, z, r, 1.0, this.b.fx, this.b.fy, this.b.fz, k);
            double tip = 0.05 * this.sc * e * (1.0 - gone);
            if (tip > 0.004 * this.sc) {
               double ahead = r / k * 0.9;
               fl.ball(x + this.b.fx * ahead, y + this.b.fy * ahead, z + this.b.fz * ahead, tip, 1.0, this.b.fx, this.b.fy, this.b.fz, 0.45);
            }
         }
      }
   }

   private double droplet(HitFx fx, QuicksilverStyle.State s, int i, float p) {
      boolean crit = fx.crit;
      float release = crit ? 0.66F : 0.6166667F;
      float from = crit ? 0.66F : 0.55F;
      if (p < from) {
         return 0.0;
      } else {
         double r = 0.066 * this.sc * s.size[i] * (1.0 - Easing.smoothstep(0.82F, 0.98F, p));
         double tf = Math.max(0.0, (double)((p - release) * 0.6F));
         double way = s.way[i] + s.swing[i] * 0.3 * Math.sin(Math.PI * tf / 0.12);
         double dr = Math.cos(way);
         double du = Math.sin(way);
         double ux = this.b.rx * dr + this.b.ux * du;
         double uy = this.b.ry * dr + this.b.uy * du;
         double uz = this.b.rz * dr + this.b.uz * du;
         double sx = this.cx;
         double sy = this.cy;
         double sz = this.cz;
         double out;
         if (crit) {
            double along = 0.2 * this.sc + 0.18 * this.sc / 0.3 * 0.9 * s.along[i];
            sx += this.b.fx * along;
            sy += this.b.fy * along;
            sz += this.b.fz * along;
            out = 0.03 * this.sc;
            r *= Easing.smoothstep(0.66F, 0.70000005F, p);
         } else if (p < 0.6166667F) {
            float q = (p - 0.55F) / 0.06666666F;
            out = 0.12 * this.sc * Easing.outCubic(q);
            r *= Easing.smoothstep(0.0F, 0.5F, q);
         } else {
            out = 0.12 * this.sc;
         }

         double travel = (1.0 - Math.exp(-1.6 * tf)) / 1.6 * s.speed[i] * this.sc * (crit ? 1.25 : 1.0);
         double cone = s.cone[i];
         double lateral = out + (2.6 * Math.sin(cone) + 3.0) * travel;
         double depth = 2.6 * Math.cos(cone) * travel;
         double er = (dr * lateral - this.bodyR) / this.halfW;
         double eu = (du * lateral - this.bodyU) / this.halfH;
         float clear = Easing.smoothstep(1.0F, 1.35F, (float)Math.sqrt(er * er + eu * eu));
         depth = Math.min(depth, 0.05) + Math.max(0.0, depth - 0.05) * clear;
         double fall = (s.lift[i] * tf - 3.5 * tf * tf) * this.sc;
         this.at[0] = sx + ux * lateral + this.b.fx * depth;
         this.at[1] = sy + uy * lateral + this.b.fy * depth + fall;
         this.at[2] = sz + uz * lateral + this.b.fz * depth;
         return r;
      }
   }

   private void glints(HitFx fx, QuicksilverStyle.State s, float p, float alpha) {
      boolean calm = Glitch.reduceFlashing();
      int col = this.material.get() == Material.CUSTOM ? this.color.getColor(0.5F) : -1;
      if (p >= 0.06666666F && p < 0.16666666F) {
         float k = 1.0F - (p - 0.06666666F) / 0.1F;
         Vfx.sprite(Textures.GLOW, this.cx, this.cy, this.cz, 0.45 * this.sc, 0.0F, alpha(col, (calm ? 0.15F : 0.4F) * k * alpha));
         if (!calm) {
            Vfx.sprite(Textures.FLARE, this.cx, this.cy, this.cz, 0.35 * this.sc, 0.6F, core(col, 0.5F * k * alpha));
         }
      }

      if (p > 0.78F) {
         float k = Easing.smoothstep(0.78F, 0.88F, p) * (1.0F - Easing.smoothstep(0.9F, 1.0F, p));

         for (int i = 0; i < s.droplets; i++) {
            if (!(this.droplet(fx, s, i, p) <= 0.0) || !(p < 0.98F)) {
               float tw = 0.6F + 0.4F * (float)Math.sin(p * 60.0 + i * 1.7);
               Vfx.sprite(
                  Textures.SPARK, this.at[0], this.at[1], this.at[2], 0.07 * this.sc, (float)(p * 8.0 + i), core(col, (calm ? 0.3F : 0.6F) * k * tw * alpha)
               );
            }
         }
      }
   }

   private static String phase(float p, boolean crit) {
      if (p < 0.06666666F) {
         return "landing";
      } else if (p < 0.19999999F) {
         return "spreading";
      } else if (p < 0.45F) {
         return "rippling";
      } else if (crit) {
         return p < 0.52F ? "gathering" : (p < 0.66F ? "spiking" : (p < 0.82F ? "flying" : "evaporating"));
      } else {
         return p < 0.55F ? "gathering" : (p < 0.6166667F ? "breaking up" : (p < 0.82F ? "flying" : "evaporating"));
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      QuicksilverStyle.State s = (QuicksilverStyle.State)fx.data;
      if (s == null) {
         return null;
      } else {
         float p = fx.progress();
         this.sc = scale(fx) * this.size.getFloat() / 100.0;
         Liquid.Field fl = s.field;
         String how = fl.lastMode == 0 ? "meshed" : (fl.lastMode == 1 ? "its last mesh" : (fl.lastMode == 2 ? "spheres" : "nothing"));
         return String.format(
            Locale.ROOT,
            "%s, %s, %s; disc %.3f, %d droplets, %d balls, grid %d points, drawn: %s (%d vertices, %d triangles)",
            this.material.get().displayName(),
            this.quality.get().displayName(),
            phase(p, fx.crit),
            this.disc.get() ? this.discRadius(p, fx.crit) : 0.0,
            s.droplets,
            fl.balls(),
            fl.lastPoints,
            how,
            fl.lastVertices,
            fl.lastTriangles
         );
      }
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   public static enum Quality implements Displayable {
      LOW("Low", "12 cells across the splash", 12),
      MEDIUM("Medium", "16 cells across the splash", 16),
      HIGH("High", "24 cells across the splash", 24);

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

   private static final class State {
      final Liquid.Field field = new Liquid.Field(24);
      int droplets;
      final double[] way = new double[20];
      final double[] cone = new double[20];
      final double[] speed = new double[20];
      final double[] size = new double[20];
      final double[] lift = new double[20];
      final double[] along = new double[20];
      final int[] swing = new int[20];
      double phase1;
      double phase2;

      State() {
         this.field.cover = true;
      }
   }
}
