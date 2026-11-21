package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Ink;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.stroke.Strokes;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;

public final class BrushstrokeStyle extends TrailStyleBase {
   private static final int MAX_DROPS = 48;
   private static final int NIGHT = -1448744;
   private static final float FLIGHT = 0.22F;
   public final ThemeTint seal;
   public final ModeSetting<BrushstrokeStyle.Orientation> orientation;
   public final SliderSetting dryness;
   public final SliderSetting splatter;
   public final BooleanSetting sealOn;
   public final ModeSetting<Ink.SealDesign> sealDesign;
   private final Strokes.Style brush = new Strokes.Style();
   private final GroundCache ground = new GroundCache();

   public BrushstrokeStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.BRUSHSTROKE,
         spec()
            .size(0.05, 0.6, 0.25, 0.01)
            .taper(25.0)
            .length(1.6)
            .anchor(TrailAnchor.FEET, false)
            .fade(TrailStyleBase.FadeCurve.LATE)
      );
      this.seal = this.signatureColor("seal", "Seal", "The red seal stamped where you stop.", -3660241, -8776672);
      this.doesNotApply(new Setting[]{this.glow});
      this.orientation = this.setting(
         new ModeSetting<>(
               "brushstroke.orientation",
               "Orientation",
               "Flat on the ground, turned to you, or standing along your path.",
               BrushstrokeStyle.Orientation.class,
               BrushstrokeStyle.Orientation.GROUND
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.dryness = this.setting(
         new SliderSetting("brushstroke.dryness", "Dryness", "How soon the brush runs dry into bristle streaks.", 0.0, 100.0, 50.0, 5.0).unit("%")
      );
      this.splatter = this.setting(
         new SliderSetting("brushstroke.splatter", "Splatter on Turns", "How many ink droplets sharp turns fling.", 0.0, 100.0, 40.0, 5.0).unit("%")
      );
      this.sealOn = this.setting(
         new BooleanSetting("brushstroke.sealOn", "Seal When I Stop", "A red seal stamps at the head of the stroke where you stop.", true)
      );
      this.sealDesign = this.setting(
         new ModeSetting<>("brushstroke.sealDesign", "Seal Design", "The seal's crest.", Ink.SealDesign.class, Ink.SealDesign.SUN)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return BrushstrokeStyle.this.sealOn.get();
               }
            })
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.splatter);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new BrushstrokeStyle.Drops();
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      TrailShape s = p.shape;
      boolean mine = p.self && !f.preview && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
      if (s != null) {
         for (int k = 0; k < s.runs; k++) {
            this.stroke(p, f, k, mine);
         }
      }

      if (!p.ghost) {
         if (this.splatter.getFloat() > 0.0F && !p.reduced && this.spawnPass(pass)) {
            this.turns(p, f);
         }

         if (this.sealOn.get()) {
            this.stamp(p, f);
         }
      }
   }

   private void stroke(PlayerTrail p, TrailFrame f, int k, boolean mine) {
      int filled = this.fill(p, f, k, this.size.getFloat(), -1.0F, 0.0F);
      if (filled >= 2) {
         int n = Math.min(filled, 512);
         BrushstrokeStyle.Orientation o = this.orientation.get();
         double last = Double.NaN;

         for (int j = 0; j < n; j++) {
            int i = filled - 1 - j;
            double x = Vfx.X[i];
            double y = Vfx.Y[i];
            double z = Vfx.Z[i];
            if (o == BrushstrokeStyle.Orientation.GROUND) {
               double wx = x + f.viewX;
               double feet = y + f.viewY - 0.06;
               double wz = z + f.viewZ;
               double g = this.ground.surface(f.world, wx, feet, wz, 0.6, 3.0);
               if (Double.isNaN(g)) {
                  g = Double.isNaN(last) ? feet : last;
               }

               last = g;
               y = g - f.viewY + 0.008;
            }

            Strokes.X[j] = x;
            Strokes.Y[j] = y;
            Strokes.Z[j] = z;
            Strokes.PC[j] = Vfx.C[i] | 0xFF000000;
            float a = POINT_ALPHA[i];
            if (mine) {
               a = NearCamera.dark(Vfx.camera(), x, y, z, a);
            }

            Strokes.A[j] = a;
         }

         if (o == BrushstrokeStyle.Orientation.UPRIGHT) {
            for (int var26 = 0; var26 < n; var26++) {
               int a = Math.max(0, var26 - 1);
               int b = Math.min(n - 1, var26 + 1);
               double tx = Strokes.X[b] - Strokes.X[a];
               double tz;
               double tl = Math.sqrt(tx * tx + (tz = Strokes.Z[b] - Strokes.Z[a]) * tz);
               Strokes.NX[var26] = tl < 1.0E-6 ? 1.0 : -tz / tl;
               Strokes.NY[var26] = 0.0;
               Strokes.NZ[var26] = tl < 1.0E-6 ? 0.0 : tx / tl;
            }
         }

         Strokes.Style b = this.brush.reset();
         b.tool = Strokes.Tool.BRUSH;
         b.color = -1;
         b.pointColor = true;
         b.pointAlpha = true;
         b.width = this.size.getFloat() * p.widthScale;
         b.heavyStart = 1.0F;
         b.thinEnd = 0.7F;
         b.dryness = this.dryness.getFloat() / 100.0F;
         b.light = this.matLight;
         b.flat = o != BrushstrokeStyle.Orientation.FACING;
         b.pointNormal = o == BrushstrokeStyle.Orientation.UPRIGHT;
         b.normalX = 0.0;
         b.normalY = 1.0;
         b.normalZ = 0.0;
         if (this.night > 0.01F) {
            b.alpha = 0.16F * this.night;
            b.width *= 1.5;
            b.pointColor = false;
            b.color = -1448744;
            Strokes.draw(n, b);
            b.alpha = 1.0F;
            b.width = this.size.getFloat() * p.widthScale;
            b.pointColor = true;
            b.color = -1;
         }

         Strokes.draw(n, b);
         int from = n * 3 / 5;
         if (n - from >= 2) {
            for (int j2 = from; j2 < n; j2++) {
               int t = j2 - from;
               Strokes.X[t] = Strokes.X[j2];
               Strokes.Y[t] = Strokes.Y[j2];
               Strokes.Z[t] = Strokes.Z[j2];
               Strokes.PC[t] = Strokes.PC[j2];
               Strokes.A[t] = Strokes.A[j2] * 0.5F * t / (n - from);
               Strokes.NX[t] = Strokes.NX[j2];
               Strokes.NY[t] = Strokes.NY[j2];
               Strokes.NZ[t] = Strokes.NZ[j2];
            }

            b.diffuse = 0.55F;
            b.heavyStart = 0.0F;
            b.thinEnd = 0.0F;
            Strokes.draw(n - from, b);
            b.diffuse = 0.0F;
         }
      }
   }

   private void turns(PlayerTrail p, TrailFrame f) {
      TrailShape s = p.shape;
      if (s != null && s.runs != 0 && p.attached) {
         PlayerTrail.StyleState st = p.style;
         int k = s.runs - 1;
         int end = s.runEnd[k] - 1;
         int from = s.runStart[k];
         int b = end;

         while (b > from && s.along[b] < 0.4F) {
            b--;
         }

         int c = b;

         while (c > from && s.along[c] < 0.8F) {
            c--;
         }

         if (c != b && b != end) {
            double h1x = s.x[end] - s.x[b];
            double h1z = s.z[end] - s.z[b];
            double h0x = s.x[b] - s.x[c];
            double h0z = s.z[b] - s.z[c];
            double l1 = Math.sqrt(h1x * h1x + h1z * h1z);
            double l0 = Math.sqrt(h0x * h0x + h0z * h0z);
            if (!(l1 < 0.001) && !(l0 < 0.001)) {
               double cos = (h1x * h0x + h1z * h0z) / (l1 * l0);
               if (!(cos > 0.82) && !(f.now - st.vars[0] < 0.35)) {
                  st.vars[0] = f.now;
                  double cross = h0x * h1z - h0z * h1x;
                  double ox = (cross > 0.0 ? 1 : -1) * h1z / l1;
                  double oz = (cross > 0.0 ? -1 : 1) * h1x / l1;
                  BrushstrokeStyle.Drops d = this.store(f);
                  int count = Math.round(2.0F + 5.0F * this.splatter.getFloat() / 100.0F);
                  float life = this.lengthNow(p.speed) * 0.8F + 0.22F;
                  int col = this.colorAt(0.0F, 0.0F, p.speed, f.colorTime, 0.0F) | 0xFF000000;

                  for (int i = 0; i < count && d.n < 48 && !this.module.piecesFull(f); i++) {
                     int j = d.n++;
                     double spread = (Math.random() - 0.5) * 0.9;
                     double sx = ox * Math.cos(spread) - oz * Math.sin(spread);
                     double sz = ox * Math.sin(spread) + oz * Math.cos(spread);
                     double speed = 1.2 + Math.random() * 1.6;
                     d.x[j] = p.headX;
                     d.y[j] = p.headY + 0.05;
                     d.z[j] = p.headZ;
                     d.vx[j] = sx * speed;
                     d.vz[j] = sz * speed;
                     d.vy[j] = 1.0 + Math.random() * 1.2;
                     double g = this.ground.surface(f.world, p.headX + sx * speed * 0.22F, p.headY + 0.3, p.headZ + sz * speed * 0.22F, 0.6, 3.0);
                     d.ground[j] = Double.isNaN(g) ? p.headY : g + 0.006;
                     d.age[j] = 0.0F;
                     d.life[j] = life;
                     d.size[j] = (float)(this.size.getFloat() * (0.12 + 0.18 * Math.random()));
                     d.turn[j] = (float)(Math.random() * Math.PI * 2.0);
                     d.tint[j] = col;
                  }
               }
            }
         }
      }
   }

   private void stamp(PlayerTrail p, TrailFrame f) {
      PlayerTrail.StyleState st = p.style;
      if (p.growing) {
         st.vars[1] = 0.0;
      } else {
         if (st.vars[1] == 0.0) {
            st.vars[1] = Math.max(1.0E-6, f.now);
            st.vars[2] = p.headX;
            st.vars[3] = p.headZ;
         }

         double z;
         double x;
         double g;
         double y = Double.isNaN(g = this.ground.surface(f.world, x = st.vars[2], p.headY + 0.3, z = st.vars[3], 0.6, 3.0)) ? p.headY : g;
         float since = (float)(f.now - st.vars[1]);
         float press = 1.0F + 0.35F * (1.0F - Easing.outCubic(Easing.clamp01(since / 0.12F)));
         float a = Easing.clamp01(since / 0.05F) * p.alpha * this.opacity.getFloat() / 100.0F * f.nearFade(x, y, z);
         if (!(a <= 0.003F)) {
            double half = Math.max(0.12, this.size.getFloat() * 0.8) * press;
            int red = ColorUtil.shade(this.tint != 0 ? this.tint : this.seal.getColor(0.5F), Math.max(0.45F, this.matLight)) | 0xFF000000;
            RenderState.decal(true);
            Ink.quad(
               Ink.seals(),
               this.sealDesign.get().ordinal(),
               4,
               x - f.viewX,
               y + 0.01 - f.viewY,
               z - f.viewZ,
               1.0,
               0.0,
               0.0,
               0.0,
               0.0,
               1.0,
               half * 2.0,
               half * 2.0,
               (float)(p.entityId * 0.37),
               ColorUtil.mulAlpha(red, a)
            );
            RenderState.decal(false);
         }
      }
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      BrushstrokeStyle.Drops d = (BrushstrokeStyle.Drops)f.store(this.type);
      if (d != null && d.n != 0) {
         RenderState.decal(true);

         for (int i = 0; i < d.n; i++) {
            float k = 1.0F - Easing.smoothstep(0.55F, 1.0F, d.age[i] / d.life[i]);
            boolean flying = d.age[i] < 0.22F;
            double r = d.size[i] * (flying ? 0.6 : 1.0);
            float a = k * f.nearFade(d.x[i], d.y[i], d.z[i]) * f.fog(d.x[i], d.y[i], d.z[i]) * this.opacity.getFloat() / 100.0F;
            if (!(a <= 0.003F)) {
               int col = ColorUtil.mulAlpha(lit(d.tint[i], f, d.x[i], d.y[i], d.z[i]), a);
               Ink.quad(
                  Ink.drops(),
                  flying ? 1 : 0,
                  2,
                  d.x[i] - f.viewX,
                  (flying ? d.y[i] : d.ground[i]) - f.viewY,
                  d.z[i] - f.viewZ,
                  1.0,
                  0.0,
                  0.0,
                  0.0,
                  0.0,
                  1.0,
                  r * 2.0,
                  r * 2.0,
                  d.turn[i],
                  col
               );
            }
         }

         RenderState.decal(false);
      }
   }

   private static final class Drops implements TrailStyleBase.Store {
      final double[] x = new double[48];
      final double[] y = new double[48];
      final double[] z = new double[48];
      final double[] vx = new double[48];
      final double[] vy = new double[48];
      final double[] vz = new double[48];
      final double[] ground = new double[48];
      final float[] age = new float[48];
      final float[] life = new float[48];
      final float[] size = new float[48];
      final float[] turn = new float[48];
      final int[] tint = new int[48];
      int n;

      private Drops() {
      }

      @Override
      public void update(float dt) {
         int i = 0;

         while (i < this.n) {
            this.age[i] = this.age[i] + dt;
            if (this.age[i] >= this.life[i]) {
               int last = --this.n;
               this.copy(last, i);
            } else {
               if (this.age[i] < 0.22F) {
                  this.x[i] = this.x[i] + this.vx[i] * dt;
                  this.z[i] = this.z[i] + this.vz[i] * dt;
                  this.vy[i] = this.vy[i] - 14.0 * dt;
                  this.y[i] = Math.max(this.ground[i], this.y[i] + this.vy[i] * dt);
               }

               i++;
            }
         }
      }

      private void copy(int from, int to) {
         this.x[to] = this.x[from];
         this.y[to] = this.y[from];
         this.z[to] = this.z[from];
         this.vx[to] = this.vx[from];
         this.vy[to] = this.vy[from];
         this.vz[to] = this.vz[from];
         this.ground[to] = this.ground[from];
         this.age[to] = this.age[from];
         this.life[to] = this.life[from];
         this.size[to] = this.size[from];
         this.turn[to] = this.turn[from];
         this.tint[to] = this.tint[from];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
      }
   }

   public static enum Orientation implements Displayable {
      GROUND("On the ground", "Flat on the ground under your path"),
      FACING("Facing camera", "Always turned to you"),
      UPRIGHT("Upright", "A ribbon standing along your path");

      private final String displayName;
      private final String description;

      private Orientation(String displayName, String description) {
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
