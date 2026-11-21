package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.trail.PathRecorder;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SpeedLinesStyle extends TrailStyleBase {
   public final SliderSetting lineLength;
   public final SliderSetting spread;
   public final SliderSetting curve;
   public final BooleanSetting onlySprinting;
   private final Rng rng = new Rng();

   public SpeedLinesStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.SPEED_LINES,
         spec()
            .size(0.005, 0.08, 0.02, 0.005)
            .length(0.35)
            .anchor(TrailAnchor.WAIST, true)
            .along(TrailStyleBase.ColorAlong.HEAD_TO_TAIL)
      );
      this.lineLength = this.setting(
         new SliderSetting("speed_lines.lineLength", "Line Length", "How long each line is at walking speed.", 0.3, 3.0, 1.2, 0.1).unit(" blocks")
      );
      this.spread = this.setting(
         new SliderSetting("speed_lines.spread", "Spread", "How far around your body the lines appear.", 0.0, 100.0, 50.0, 1.0).unit("%")
      );
      this.curve = this.setting(new SliderSetting("speed_lines.curve", "Curve", "How much the lines bow around your body.", 0.0, 100.0, 30.0, 1.0).unit("%"));
      this.onlySprinting = this.setting(new BooleanSetting("speed_lines.sprintOnly", "Only While Sprinting", "Lines only while sprinting.", true));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.lineLength);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new SpeedLinesStyle.Lines();
   }

   @Override
   public boolean sprintOnly() {
      return this.onlySprinting.get();
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      if (!p.ghost && !p.reduced && (!this.onlySprinting.get() || (p.state & 2) != 0)) {
         boolean airborne = (p.state & 1) == 0;
         int due = this.piecesDue(p, airborne ? 14.0 : 7.0, 12);
         if (due != 0 && p.path != null) {
            SpeedLinesStyle.Lines lines = this.store(f);
            PathRecorder path = p.path;
            double mx = 0.0;
            double my = 0.0;
            double mz = 0.0;
            int i = path.count() - 1;

            while (
               i >= Math.max(0, path.count() - 8)
                  && !((mx = p.headX - path.x(i)) * mx + (my = p.headY - path.y(i)) * my + (mz = p.headZ - path.z(i)) * mz > 0.09)
            ) {
               i--;
            }

            double ml = Math.sqrt(mx * mx + my * my + mz * mz);
            if (ml < 1.0E-5) {
               double yaw = Math.toRadians(p.pose.bodyYaw);
               mx = -Math.sin(yaw);
               my = 0.0;
               mz = Math.cos(yaw);
            } else {
               mx /= ml;
               my /= ml;
               mz /= ml;
            }

            double sx = -mz;
            double sl = Math.sqrt(sx * sx + mx * mx);
            double sz;
            if (sl < 1.0E-5) {
               sx = 1.0;
               sz = 0.0;
            } else {
               sx /= sl;
               sz = mx / sl;
            }

            double ux = -sz * my;
            double uy = sz * mx - sx * mz;
            double uz = sx * my;
            float sp = this.spread.getFloat() / 100.0F;
            float len = this.lineLength.getFloat() * (0.5F + p.speed / 8.0F) * (1.0F + 0.5F * Math.max(0.0F, this.speedBoost(p.speed)));
            float wid = this.size.getFloat() * p.widthScale;

            for (int ix = 0; ix < due; ix++) {
               if (this.module.piecesFull(f)) {
                  return;
               }

               this.rng.seed((int)(f.now * 7919.0) + ix * 53 + p.entityId * 11);
               double ang = this.rng.next() * Math.PI * 2.0;
               double r = 0.3 + 0.55 * sp * (0.6 + 0.4 * this.rng.next());
               double cx = p.pose.x;
               double cy = p.pose.y + (p.pose.sneaking ? 0.75 : 0.95);
               double cz = p.pose.z;
               double ox = sx * Math.cos(ang) * r + ux * Math.sin(ang) * r * 1.4;
               double oy = uy * Math.sin(ang) * r * 1.4;
               double oz = sz * Math.cos(ang) * r + uz * Math.sin(ang) * r * 1.4;
               double back = this.rng.next() * 0.5;
               double x = cx + ox - mx * back;
               double y = cy + oy - my * back;
               double z = cz + oz - mz * back;
               if (!(f.nearFade(x, y, z) < 0.5F)) {
                  int k = lines.add();
                  lines.x[k] = x;
                  lines.y[k] = y;
                  lines.z[k] = z;
                  lines.dx[k] = (float)mx;
                  lines.dy[k] = (float)my;
                  lines.dz[k] = (float)mz;
                  double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
                  lines.ox[k] = (float)(ol > 1.0E-6 ? ox / ol : 0.0);
                  lines.oy[k] = (float)(ol > 1.0E-6 ? oy / ol : 0.0);
                  lines.oz[k] = (float)(ol > 1.0E-6 ? oz / ol : 0.0);
                  lines.len[k] = len * (0.6F + 0.6F * this.rng.next());
                  lines.born[k] = lines.clock;
                  lines.life[k] = this.lengthNow(p.speed) * (0.7F + 0.6F * this.rng.next());
                  lines.width[k] = wid * (0.6F + 0.8F * this.rng.next());
                  lines.alpha[k] = p.alpha;
                  lines.drift[k] = 2.0F + 2.0F * this.rng.next();
                  lines.speed[k] = p.speed;
                  lines.tint[k] = this.tint;
               }
            }
         }
      }
   }

   @Override
   protected void drawStore(TrailFrame f, TrailStyleBase.Store store) {
      SpeedLinesStyle.Lines l = (SpeedLinesStyle.Lines)store;
      float op = this.opacity.getFloat() / 100.0F;
      float g = this.glow.getFloat() / 100.0F;
      float bow = this.curve.getFloat() / 100.0F;
      RenderState.additive();

      for (int i = 0; i < l.n; i++) {
         float u = Math.min(1.0F, (l.clock - l.born[i]) / Math.max(0.05F, l.life[i]));
         double wx = l.x[i];
         double wy = l.y[i];
         double wz = l.z[i];
         float a = op * l.alpha[i] * Math.min(1.0F, u * 7.0F) * this.fade(u) * f.nearFade(wx, wy, wz) * f.fog(wx, wy, wz);
         if (!(a <= 0.004F)) {
            float shade = f.lightAt(wx, wy, wz);
            int n = 5;

            for (int j = 0; j < n; j++) {
               float t = (float)j / (n - 1);
               double b = Math.sin(t * Math.PI) * bow * 0.12 * l.len[i];
               Vfx.X[j] = wx - l.dx[i] * l.len[i] * t + l.ox[i] * b - f.viewX;
               Vfx.Y[j] = wy - l.dy[i] * l.len[i] * t + l.oy[i] * b - f.viewY;
               Vfx.Z[j] = wz - l.dz[i] * l.len[i] * t + l.oz[i] * b - f.viewZ;
               Vfx.W[j] = l.width[i] * (float)(0.4 + 0.6 * Math.sin(t * Math.PI + 0.15));
               int c = l.tint[i] != 0 ? l.tint[i] : this.colorAt(t, u, l.speed[i], f.colorTime, 0.0F);
               if (shade < 0.999F) {
                  c = ColorUtil.shade(c, shade);
               }

               Vfx.C[j] = ColorUtil.mulAlpha(c, a * (float)Math.sin(t * Math.PI * 0.92 + 0.12));
            }

            TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
            TrailStrip.draw(Textures.LINE_GLOW, 0, n, 2.5F + 3.0F * g, 0.0, 0.35F * (0.4F + g));

            for (int var24 = 0; var24 < n; var24++) {
               int cc = Vfx.C[var24];
               Vfx.C[var24] = cc & 0xFF000000 | ColorUtil.core(cc) & 16777215;
            }

            TrailStrip.draw(Textures.LINE_CORE, 0, n, 1.0F, 0.0, 0.9F);
         }
      }
   }

   static final class Lines implements TrailStyleBase.Store {
      static final int CAP = 240;
      final double[] x = new double[240];
      final double[] y = new double[240];
      final double[] z = new double[240];
      final float[] dx = new float[240];
      final float[] dy = new float[240];
      final float[] dz = new float[240];
      final float[] ox = new float[240];
      final float[] oy = new float[240];
      final float[] oz = new float[240];
      final float[] len = new float[240];
      final float[] born = new float[240];
      final float[] life = new float[240];
      final float[] width = new float[240];
      final float[] alpha = new float[240];
      final float[] drift = new float[240];
      final float[] speed = new float[240];
      final int[] tint = new int[240];
      int n;
      int oldest;
      float clock;

      int add() {
         if (this.n < 240) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 240;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         int w = 0;

         for (int i = 0; i < this.n; i++) {
            if (!(this.clock - this.born[i] >= this.life[i])) {
               this.x[i] = this.x[i] - this.dx[i] * this.drift[i] * dt;
               this.y[i] = this.y[i] - this.dy[i] * this.drift[i] * dt;
               this.z[i] = this.z[i] - this.dz[i] * this.drift[i] * dt;
               if (w != i) {
                  this.x[w] = this.x[i];
                  this.y[w] = this.y[i];
                  this.z[w] = this.z[i];
                  this.dx[w] = this.dx[i];
                  this.dy[w] = this.dy[i];
                  this.dz[w] = this.dz[i];
                  this.ox[w] = this.ox[i];
                  this.oy[w] = this.oy[i];
                  this.oz[w] = this.oz[i];
                  this.len[w] = this.len[i];
                  this.born[w] = this.born[i];
                  this.life[w] = this.life[i];
                  this.width[w] = this.width[i];
                  this.alpha[w] = this.alpha[i];
                  this.drift[w] = this.drift[i];
                  this.speed[w] = this.speed[i];
                  this.tint[w] = this.tint[i];
               }

               w++;
            }
         }

         this.n = w;
         this.oldest = 0;
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
}
