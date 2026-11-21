package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Comic;
import xyz.daikirai.render.furiah.fx.kit.Halftone;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;

public final class CartoonDustStyle extends TrailStyleBase {
   private static final int MAX_PUFFS = 240;
   private static final float PUFF = 0.42F;
   private static final float SKID = 1.8F;
   private static final float POP_IN = 0.12F;
   private static final float POP_OUT = 0.14F;
   private static final float PUFF_WIND = 0.3F;
   public final ThemeTint outline;
   public final SliderSetting puffSize;
   public final SliderSetting outlineWidth;
   public final BooleanSetting halftone;
   public final BooleanSetting popOut;
   public final BooleanSetting skid;
   public final BooleanSetting actionLines;
   private final Rng rng = new Rng();
   private final GroundCache ground = new GroundCache();
   private int linesShown;
   private int linesCounting;
   private double linesFrame = -1.0;

   public CartoonDustStyle(FuriahTrails module) {
      super(module, TrailStyle.CARTOON_DUST, spec().length(0.8).anchor(TrailAnchor.FEET, true));
      this.outline = this.signatureColor("outline", "Outline", "The puffs' bold outlines and the action lines.", -15658735, -12965302);
      this.doesNotApply(new Setting[]{this.size, this.glow});
      this.puffSize = this.setting(new SliderSetting("cartoon_dust.puffSize", "Puff Size", "How big the dust clouds are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.outlineWidth = this.setting(
         new SliderSetting("cartoon_dust.outlineWidth", "Outline Width", "How bold the outlines are.", 50.0, 200.0, 100.0, 5.0).unit("%")
      );
      this.halftone = this.setting(new BooleanSetting("cartoon_dust.halftone", "Halftone Shading", "Printed halftone dots in the puffs' shade.", true));
      this.popOut = this.setting(new BooleanSetting("cartoon_dust.popOut", "Pop Out", "The puffs pop like bubbles at the end (off: they fade).", true));
      this.skid = this.setting(new BooleanSetting("cartoon_dust.skid", "Skid Clouds", "Sharp turns kick up a bigger cloud.", true));
      this.actionLines = this.setting(new BooleanSetting("cartoon_dust.actionLines", "Action Lines", "Short motion lines behind you while sprinting.", true));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.puffSize);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new CartoonDustStyle.Puffs();
   }

   @Override
   public void update(TrailFrame f) {
      CartoonDustStyle.Puffs d = (CartoonDustStyle.Puffs)f.store(this.type);
      if (d != null && d.n > 0) {
         float[] w = this.breeze(f, f.eyeX, f.eyeY, f.eyeZ, 0.3F);
         d.wx = w[0];
         d.wy = w[1];
         d.wz = w[2];
      }

      super.update(f);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      if (!p.ghost) {
         if (this.actionLines.get() && !p.reduced && p.growing && p.speed > 5.0F) {
            this.lines(p, f);
         }

         if (this.spawnPass(pass)) {
            double yaw = Math.toRadians(p.pose.bodyYaw);
            float fx = (float)(-Math.sin(yaw));
            float fz = (float)Math.cos(yaw);
            int due = this.piecesDue(p, 3.0, 6);
            int extra = this.skid.get() && p.growing ? this.turnSkid(p, f) : 0;
            if (due != 0 || extra != 0) {
               CartoonDustStyle.Puffs d = this.store(f);
               float w0 = 0.42F * this.puffSize.getFloat() / 100.0F * p.widthScale;
               float life = this.lengthNow(p.speed);

               for (int i = 0; i < due + extra && !this.module.piecesFull(f); i++) {
                  boolean big = i >= due;
                  this.rng.seed((int)(f.now * 5231.0) + i * 109 + p.entityId * 23);
                  float side = this.rng.signed();
                  double x = p.headX - fx * 0.25 + fz * side * (big ? 0.35 : 0.18);
                  double z = p.headZ - fz * 0.25 - fx * side * (big ? 0.35 : 0.18);
                  double g = this.ground.surface(f.world, x, p.headY + 0.4, z, 0.6, 2.0);
                  double y = (Double.isNaN(g) ? p.headY : g) + 0.12 + this.rng.next() * 0.1;
                  if (!(f.nearFade(x, y, z) < 0.4F)) {
                     int k = d.add();
                     d.x[k] = x;
                     d.y[k] = y;
                     d.z[k] = z;
                     float back = big ? 0.3F : 0.5F + this.rng.next() * 0.4F;
                     d.vx[k] = -fx * back + fz * side * 0.3F;
                     d.vz[k] = -fz * back - fx * side * 0.3F;
                     d.vy[k] = 0.2F + this.rng.next() * 0.3F;
                     d.born[k] = d.clock;
                     d.life[k] = life * (big ? 1.2F : 0.6F + this.rng.next() * 0.4F);
                     d.size[k] = w0 * (big ? 1.8F : 0.8F + this.rng.next() * 0.4F);
                     d.turn[k] = this.rng.signed() * 0.4F;
                     d.alpha[k] = this.opacity.getFloat() / 100.0F * p.alpha;
                     d.spot[k] = this.rng.next();
                     d.speed[k] = p.speed;
                     d.shape[k] = (int)(this.rng.next() * 1000.0F);
                     d.tint[k] = this.tint;
                     d.far[k] = p.reduced;
                  }
               }
            }
         }
      }
   }

   private int turnSkid(PlayerTrail p, TrailFrame f) {
      PlayerTrail.StyleState st = p.style;
      float yaw = p.pose.bodyYaw;
      if (f.now - st.vars[2] > 0.25) {
         double before = st.vars[1];
         st.vars[1] = yaw;
         st.vars[2] = f.now;
         float turn = Math.abs((float)(((yaw - before) % 360.0 + 540.0) % 360.0 - 180.0));
         if (turn > 55.0F && f.now - st.vars[0] > 0.45 && p.speed > 3.5F) {
            st.vars[0] = f.now;
            return 3;
         }
      }

      return 0;
   }

   private void lines(PlayerTrail p, TrailFrame f) {
      double yaw = Math.toRadians(p.pose.bodyYaw);
      double fx = -Math.sin(yaw);
      double fz;
      double sx = fz = Math.cos(yaw);
      double sz = -fx;
      int ink = this.outlineColor() | 0xFF000000;
      float op = this.opacity.getFloat() / 100.0F * p.alpha;

      for (int i = 0; i < 3; i++) {
         double phase = f.now * 6.5 + i * 2.1;
         double cyc = phase - Math.floor(phase);
         if (!(cyc > 0.55)) {
            float a = op * (float)Math.sin(Math.PI * cyc / 0.55);
            double off = (i - 1) * 0.22;
            double h = 0.25 + i * 0.28;
            double x0 = p.headX - fx * 0.45 + sx * off;
            double y0 = p.headY + h;
            double z0 = p.headZ - fz * 0.45 + sz * off;
            double len = 0.5 + 0.15 * i;
            float near = f.nearFade(x0, y0, z0);
            if (!(a * near <= 0.02F)) {
               for (int k = 0; k < 3; k++) {
                  double t = k / 2.0;
                  Vfx.X[k] = x0 - fx * len * t - f.viewX;
                  Vfx.Y[k] = y0 - f.viewY;
                  Vfx.Z[k] = z0 - fz * len * t - f.viewZ;
                  Vfx.W[k] = (float)(0.035 * (k == 0 ? 1.0 : (k == 1 ? 0.7 : 0.1)));
                  Vfx.C[k] = ColorUtil.mulAlpha(ink, a * near);
               }

               Vfx.ribbon(Textures.LINE_CORE, 3, false);
               if (p.self && !f.preview) {
                  if (f.now != this.linesFrame) {
                     this.linesShown = this.linesCounting;
                     this.linesCounting = 0;
                     this.linesFrame = f.now;
                  }

                  this.linesCounting++;
               }
            }
         }
      }
   }

   private int outlineColor() {
      return this.tint != 0 ? ColorUtil.shade(this.tint, 0.25F) : this.outline.getColor(0.5F);
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      CartoonDustStyle.Puffs d = (CartoonDustStyle.Puffs)f.store(this.type);
      if (d != null && d.n != 0) {
         int i = 0;

         while (i < d.n) {
            double dx = d.x[i] - f.eyeX;
            double dy = d.y[i] - f.eyeY;
            double dz = d.z[i] - f.eyeZ;
            d.dist[i] = dx * dx + dy * dy + dz * dz;

            int j;
            for (j = i; j > 0 && d.dist[d.order[j - 1]] < d.dist[i]; j--) {
               d.order[j] = d.order[j - 1];
            }

            d.order[j] = i++;
         }

         Camera cam = Vfx.camera();
         int ink = this.outline.getColor(0.5F) | 0xFF000000;
         float lineW = this.outlineWidth.getFloat() / 100.0F;
         RenderState.translucent(false);
         this.puffs(f, d, cam, ink, lineW, true);
         boolean dots = this.halftone.get() && Halftone.usable();
         if (dots) {
            Vfx.flush();
            float period = Math.max(3.0F, Minecraft.getMinecraft().displayHeight / 200.0F);
            Halftone.begin(period, ink & 16777215 | -1728053248, 1.0F);
         }

         try {
            this.puffs(f, d, cam, ink, lineW, false);
            Vfx.flush();
         } finally {
            if (dots) {
               Halftone.end();
            }
         }
      }
   }

   private void puffs(TrailFrame f, CartoonDustStyle.Puffs d, Camera cam, int ink, float lineW, boolean far) {
      for (int o = 0; o < d.n; o++) {
         int i = d.order[o];
         if (d.far[i] == far) {
            float age = d.clock - d.born[i];
            float u = Math.min(1.0F, age / Math.max(0.05F, d.life[i]));
            double x = d.x[i];
            double y = d.y[i];
            double z = d.z[i];
            float scale = Easing.outBack(Easing.clamp01(age / 0.12F), 2.6F) * (0.35F + 0.65F * Easing.clamp01(age / 0.12F));
            scale *= 1.0F + 0.3F * u;
            float a = d.alpha[i] * f.nearFade(x, y, z) * f.fog(x, y, z);
            if (this.popOut.get()) {
               float q = Easing.clamp01((u - 0.86F) / 0.14F);
               scale *= q < 0.4F ? 1.0F + 0.35F * (q / 0.4F) : 1.35F * (1.0F - Easing.smoothstep(0.4F, 1.0F, q));
            } else {
               a *= 1.0F - Easing.smoothstep(0.55F, 1.0F, u);
            }

            if (!(a <= 0.01F) && !(scale <= 0.01F)) {
               int fill = lit(d.tint[i] != 0 ? d.tint[i] : this.colorAt(d.spot[i], u, d.speed[i], f.colorTime, 0.0F), f, x, y, z) | 0xFF000000;
               int line = d.tint[i] != 0 ? ColorUtil.shade(d.tint[i], 0.25F) | 0xFF000000 : ink;
               double w = d.size[i] * scale;
               Comic.puff(
                  d.shape[i],
                  x - f.viewX,
                  y - f.viewY,
                  z - f.viewZ,
                  cam.rightX,
                  cam.rightY,
                  cam.rightZ,
                  cam.upX,
                  cam.upY,
                  cam.upZ,
                  w,
                  w * 0.82,
                  d.turn[i],
                  ColorUtil.mulAlpha(fill, a),
                  ColorUtil.mulAlpha(line, a),
                  lineW
               );
            }
         }
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "puff %.0f%%, outline %.0f%%, halftone %s (%s), pop out %s, skid %s, lines %s (%d of yours drawn lately)",
         this.puffSize.getFloat(),
         this.outlineWidth.getFloat(),
         this.halftone.get() ? "on" : "off",
         Halftone.usable() ? "shader" : "printed",
         this.popOut.get() ? "on" : "off",
         this.skid.get() ? "on" : "off",
         this.actionLines.get() ? "on" : "off",
         Math.max(this.linesShown, this.linesCounting)
      );
   }

   static final class Puffs implements TrailStyleBase.Store {
      final double[] x = new double[240];
      final double[] y = new double[240];
      final double[] z = new double[240];
      final float[] vx = new float[240];
      final float[] vy = new float[240];
      final float[] vz = new float[240];
      final float[] born = new float[240];
      final float[] life = new float[240];
      final float[] size = new float[240];
      final float[] turn = new float[240];
      final float[] alpha = new float[240];
      final float[] spot = new float[240];
      final float[] speed = new float[240];
      final int[] shape = new int[240];
      final int[] tint = new int[240];
      final int[] order = new int[240];
      final boolean[] far = new boolean[240];
      final double[] dist = new double[240];
      int n;
      int oldest;
      float clock;
      float wx;
      float wy;
      float wz;

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
         if (!(dt <= 0.0F)) {
            float damp = (float)Math.exp(-3.0 * dt);
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               if (!(this.clock - this.born[i] >= this.life[i])) {
                  this.vx[i] = this.vx[i] * damp;
                  this.vy[i] = this.vy[i] * damp;
                  this.vz[i] = this.vz[i] * damp;
                  this.x[i] = this.x[i] + (this.vx[i] + this.wx) * dt;
                  this.y[i] = this.y[i] + (this.vy[i] + this.wy) * dt;
                  this.z[i] = this.z[i] + (this.vz[i] + this.wz) * dt;
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
         this.size[b] = this.size[a];
         this.turn[b] = this.turn[a];
         this.alpha[b] = this.alpha[a];
         this.spot[b] = this.spot[a];
         this.speed[b] = this.speed[a];
         this.shape[b] = this.shape[a];
         this.tint[b] = this.tint[a];
         this.far[b] = this.far[a];
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
