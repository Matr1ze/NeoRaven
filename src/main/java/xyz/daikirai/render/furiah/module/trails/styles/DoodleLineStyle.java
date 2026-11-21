package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Sketch;
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
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.stroke.Strokes;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;

public final class DoodleLineStyle extends TrailStyleBase {
   private static final double WOBBLE = 0.1;
   private static final double DOODLE = 0.4;
   private static final double[] BASE_X = new double[512];
   private static final double[] BASE_Y = new double[512];
   private static final double[] BASE_Z = new double[512];
   private static final float ERASE_FROM = 0.8F;
   private static final int NIGHT = -1512203;
   public final ThemeTint highlights;
   public final ModeSetting<DoodleLineStyle.Orientation> orientation;
   public final ModeSetting<Sketch.Tool> tool;
   public final ModeSetting<DoodleLineStyle.Doodles> doodles;
   public final SliderSetting doodleSpacing;
   public final SliderSetting wobble;
   public final SliderSetting boil;
   private final Strokes.Style style = new Strokes.Style();
   private final Sketch.Plane plane = new Sketch.Plane();
   private final GroundCache ground = new GroundCache();
   private Sketch.Tool lastTool;
   private static final double[] AT = new double[512];
   private int drawnPoints;
   private int drawnDoodles;
   private int lastSeed;
   private boolean counting;
   private static final double[] NORMAL = new double[3];
   private static double PX;
   private static double PY;
   private static double PZ;
   private static double PTX;
   private static double PTZ;
   private static float PA_AGE;

   public DoodleLineStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.DOODLE_LINE,
         spec()
            .size(0.03, 0.2, 0.09, 0.005)
            .taper(30.0)
            .length(1.4)
            .anchor(TrailAnchor.WAIST, false)
      );
      this.highlights = this.signatureColor("highlightsColor", "Highlights", "The doodles along the line.", -14020, -37986);
      this.doesNotApply(new Setting[]{this.glow});
      this.orientation = this.setting(
         new ModeSetting<>(
               "doodle_line.orientation",
               "Orientation",
               "Facing you, or drawn on the ground like chalk on pavement.",
               DoodleLineStyle.Orientation.class,
               DoodleLineStyle.Orientation.FACING
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.tool = this.setting(
         new ModeSetting<>(
               "doodle_line.tool",
               "Tool",
               "Pencil, marker or chalk. With Lines Static and still the tool's color, switching the tool switches the color too.",
               Sketch.Tool.class,
               Sketch.Tool.PENCIL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.doodles = this.setting(
         new ModeSetting<>(
               "doodle_line.doodles", "Doodles", "What is drawn along the line every few blocks.", DoodleLineStyle.Doodles.class, DoodleLineStyle.Doodles.MIXED
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.doodleSpacing = this.setting(
         new SliderSetting("doodle_line.doodleSpacing", "Doodle Spacing", "How far apart the doodles are.", 1.0, 6.0, 3.0, 0.5).unit(" blocks")
      );
      this.wobble = this.setting(new SliderSetting("doodle_line.wobble", "Wobble", "How shaky the hand is.", 0.0, 100.0, 50.0, 1.0).unit("%"));
      this.boil = this.setting(
         new SliderSetting("doodle_line.boil", "Boil", "How many times a second the line is redrawn with a new wobble.", 0.0, 12.0, 8.0, 1.0).unit("/s")
      );
      this.lastTool = this.tool.get();
      this.tool.addListener(new Runnable() {
         @Override
         public void run() {
            Sketch.Tool now = DoodleLineStyle.this.tool.get();
            Sketch.Tool before = DoodleLineStyle.this.lastTool;
            DoodleLineStyle.this.lastTool = now;
         }
      });
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.wobble);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      boolean bl = this.counting = p.self && !p.ghost && !f.preview;
      if (this.counting) {
         this.drawnDoodles = 0;
         this.drawnPoints = 0;
      }

      TrailShape s = p.shape;
      if (p.shape != null && s.runs != 0) {
         boolean pencil = this.tool.get() == Sketch.Tool.PENCIL;
         boolean mine = pencil && p.self && !f.preview && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
         double head = traveledAtHead(p);
         int seed = !p.reduced && !(this.boil.getFloat() <= 0.0F) ? (int)Math.floor(f.now * this.boil.getFloat()) : 0;
         if (this.counting) {
            this.lastSeed = seed;
         }

         for (int k = 0; k < s.runs; k++) {
            this.line(p, f, k, head, seed, pencil, mine);
         }

         if (!p.reduced && this.doodles.get() != DoodleLineStyle.Doodles.NONE) {
            this.doodlesAlong(p, f, head, seed, mine);
         }
      }
   }

   private void line(PlayerTrail p, TrailFrame f, int k, double head, int seed, boolean pencil, boolean mine) {
      int filled = this.fill(p, f, k, this.size.getFloat(), 0.0F, 0.0F);
      if (filled >= 2) {
         int n = Math.min(filled, 512);
         TrailShape s = p.shape;
         int from = s.runStart[k];
         float total = Math.max(0.001F, s.length);
         boolean grounded = this.orientation.get() == DoodleLineStyle.Orientation.GROUND;
         double last = Double.NaN;

         for (int j = 0; j < n; j++) {
            int i = filled - 1 - j;
            double x = Vfx.X[i];
            double y = Vfx.Y[i];
            double z = Vfx.Z[i];
            if (grounded) {
               double wx = x + f.viewX;
               double feet = y + f.viewY - 0.06;
               double wz = z + f.viewZ;
               double g = this.ground.surface(f.world, wx, feet, wz, 1.6, 3.0);
               if (Double.isNaN(g)) {
                  g = Double.isNaN(last) ? feet : last;
               }

               last = g;
               y = g - f.viewY + 0.01;
            }

            Strokes.X[j] = x;
            Strokes.Y[j] = y;
            Strokes.Z[j] = z;
            AT[j] = head - s.along[from + i];
            float u = Math.min(1.0F, s.along[from + i] / total);
            float a = POINT_ALPHA[i] * (1.0F - Easing.smoothstep(0.8F, 1.0F, u));
            if (mine) {
               a = NearCamera.dark(Vfx.camera(), x, y, z, a);
            }

            Strokes.A[j] = a;
            Strokes.PC[j] = Vfx.C[i] | 0xFF000000;
         }

         double amount = this.wobble.getFloat() / 100.0 * 0.1;
         double nx = grounded ? 0.0 : f.cam.forwardX;
         double ny = grounded ? 1.0 : f.cam.forwardY;
         double nz = grounded ? 0.0 : f.cam.forwardZ;
         System.arraycopy(Strokes.X, 0, BASE_X, 0, n);
         System.arraycopy(Strokes.Y, 0, BASE_Y, 0, n);
         System.arraycopy(Strokes.Z, 0, BASE_Z, 0, n);
         Strokes.wobbleAt(n, amount, seed * 2, nx, ny, nz, AT);
         Sketch.Tool tl = this.tool.get();
         this.style.reset();
         this.style.tool = tl.stroke();
         this.style.color = -1;
         this.style.pointColor = true;
         this.style.pointAlpha = true;
         this.style.width = this.size.getFloat() * p.widthScale;
         this.style.heavyStart = 0.3F;
         this.style.thinEnd = this.taper == null ? 0.3F : this.taper.getFloat() / 100.0F;
         this.style.flat = grounded;
         this.style.normalX = 0.0;
         this.style.normalY = 1.0;
         this.style.normalZ = 0.0;
         this.style.tile = 0.5;
         this.style.light = this.matLight;
         if (pencil && this.night > 0.01F) {
            this.style.pointColor = false;
            this.style.color = -1512203;
            this.style.alpha = 0.16F * this.night;
            this.style.width *= 2.2;
            Strokes.draw(n, this.style);
            this.style.pointColor = true;
            this.style.color = -1;
            this.style.alpha = 1.0F;
            this.style.width = this.size.getFloat() * p.widthScale;
         }

         Strokes.draw(n, this.style);
         System.arraycopy(BASE_X, 0, Strokes.X, 0, n);
         System.arraycopy(BASE_Y, 0, Strokes.Y, 0, n);
         System.arraycopy(BASE_Z, 0, Strokes.Z, 0, n);
         Strokes.wobbleAt(n, amount, seed * 2 + 1, nx, ny, nz, AT);
         this.style.alpha = 0.6F;
         this.style.width = this.size.getFloat() * 0.8 * p.widthScale;
         Strokes.draw(n, this.style);
         if (this.counting) {
            this.drawnPoints += n;
         }

         this.smear(p, f, n, grounded, total, s, from, filled);
      }
   }

   private void smear(PlayerTrail p, TrailFrame f, int n, boolean grounded, float total, TrailShape s, int from, int filled) {
      float want = 0.9F * total;
      int j = -1;

      for (int q = 0; q < n; q++) {
         if (s.along[from + filled - 1 - q] >= want) {
            j = q;
            break;
         }
      }

      if (j >= 1 && j < n - 1) {
         double tx = Strokes.X[j + 1] - Strokes.X[j - 1];
         double ty = Strokes.Y[j + 1] - Strokes.Y[j - 1];
         double tz = Strokes.Z[j + 1] - Strokes.Z[j - 1];
         double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
         if (!(tl < 1.0E-6)) {
            double nz = grounded ? 0.0 : f.cam.forwardZ;
            double ny;
            double var40;
            double var41;
            double ux = (var40 = ty / tl) * nz - (var41 = tz / tl) * (ny = grounded ? 1.0 : f.cam.forwardY);
            double uz;
            double nx;
            double uy;
            double ul;
            if (!(
               (ul = Math.sqrt(ux * ux + (uy = var41 * (nx = grounded ? 0.0 : f.cam.forwardX) - (tx = tx / tl) * nz) * uy + (uz = tx * ny - var40 * nx) * uz))
                  < 1.0E-6
            )) {
               this.plane.ox = Strokes.X[j];
               this.plane.oy = Strokes.Y[j];
               this.plane.oz = Strokes.Z[j];
               this.plane.rx = tx;
               this.plane.ry = var40;
               this.plane.rz = var41;
               this.plane.ux = ux / ul;
               this.plane.uy = uy / ul;
               this.plane.uz = uz / ul;
               float a = Strokes.A[Math.max(0, j - 2)];
               int lines = Strokes.PC[j];
               double w = this.size.getFloat() * p.widthScale;
               int m = Sketch.smear(this.plane, 0.0, w * 2.2, w * 1.2);
               this.style.reset();
               this.style.tool = Strokes.Tool.CHALK;
               this.style.color = Sketch.smearColor(lines);
               this.style.width = w * 2.6;
               this.style.alpha = 0.35F * Math.min(1.0F, a * 2.0F + 0.3F) * p.alpha;
               this.style.light = this.matLight;
               this.style.flat = grounded;
               this.style.normalX = 0.0;
               this.style.normalY = 1.0;
               this.style.normalZ = 0.0;
               Strokes.draw(m, this.style);
            }
         }
      }
   }

   private void doodlesAlong(PlayerTrail p, TrailFrame f, double head, int seed, boolean mine) {
      TrailShape s = p.shape;
      float total = Math.max(0.001F, s.length);
      double gap = this.doodleSpacing.getFloat();
      boolean grounded = this.orientation.get() == DoodleLineStyle.Orientation.GROUND;
      double tail = head - s.length;
      long q0 = (long)Math.floor(tail / gap);
      long q1 = (long)Math.floor(head / gap);
      int high = this.slotColor(this.highlights, 0.5F, f.colorTime) | 0xFF000000;
      Sketch.Tool tl = this.tool.get();
      double amount = this.wobble.getFloat() / 100.0 * 0.03;

      for (long q = q0; q <= q1; q++) {
         int qi = (int)q;
         double at = (q + 0.4 * (Rng.hash(qi, 3) - 0.5)) * gap;
         double along = head - at;
         float drawOn;
         float u;
         float a;
         if (!(along < 0.25)
            && !(along > total)
            && pointAt(s, along)
            && !(
               (
                     a = (1.0F - Easing.smoothstep(0.75F, 0.97F, u = (float)(along / total)))
                        * p.alpha
                        * this.opacity.getFloat()
                        / 100.0F
                        * this.fade(PA_AGE / Math.max(0.05F, p.maxAge))
                        * f.nearFade(PX, PY, PZ)
                        * f.fog(PX, PY, PZ)
                  )
                  <= 0.01F
            )
            && !((drawOn = Easing.outCubic(Easing.clamp01(PA_AGE / 0.35F))) <= 0.01F)) {
            double side = (qi & 1) == 0 ? 1.0 : -1.0;
            double r = 0.4 * (0.85 + 0.3 * Rng.hash(qi, 5)) * Math.sqrt(this.size.getFloat() / 0.09);
            double x = PX - f.viewX;
            double y = PY - f.viewY;
            double z = PZ - f.viewZ;
            if (grounded) {
               double g = this.ground.surface(f.world, PX, PY - 0.06, PZ, 1.6, 3.0);
               y = (Double.isNaN(g) ? PY - 0.06 : g) - f.viewY + 0.012;
               double sx = -PTZ;
               double sz = PTX;
               this.plane.ox = x + sx * side * (r + 0.15);
               this.plane.oy = y;
               this.plane.oz = z + sz * side * (r + 0.15);
               this.plane.rx = PTX;
               this.plane.ry = 0.0;
               this.plane.rz = PTZ;
               this.plane.ux = sx;
               this.plane.uy = 0.0;
               this.plane.uz = sz;
            } else {
               this.plane.ox = x + f.cam.upX * side * (r + 0.12);
               this.plane.oy = y + f.cam.upY * side * (r + 0.12);
               this.plane.oz = z + f.cam.upZ * side * (r + 0.12);
               this.plane.rx = f.cam.rightX;
               this.plane.ry = f.cam.rightY;
               this.plane.rz = f.cam.rightZ;
               this.plane.ux = f.cam.upX;
               this.plane.uy = f.cam.upY;
               this.plane.uz = f.cam.upZ;
            }

            if (mine) {
               a = NearCamera.dark(Vfx.camera(), this.plane.ox, this.plane.oy, this.plane.oz, a);
            }

            int n;
            switch (this.kind(qi)) {
               case STARS:
                  n = Sketch.star(this.plane, 0.0, 0.0, r);
                  break;
               case SPIRALS:
                  n = Sketch.swirl(this.plane, 0.0, 0.0, r, 2.2, (qi & 2) == 0 ? 1 : -1, Rng.hash(qi, 7) * Math.PI * 2.0);
                  break;
               case LOOPS:
                  n = Sketch.loops(this.plane, 0.0, 0.0, r * 2.4, r * 0.9, 3);
                  break;
               default:
                  n = Sketch.heart(this.plane, 0.0, 0.0, r);
            }

            double[] nrm = NORMAL;
            this.plane.normal(nrm);
            Strokes.wobble(n, amount, seed * 31 + qi, nrm[0], nrm[1], nrm[2]);
            this.style.reset();
            this.style.tool = tl.stroke();
            this.style.color = high;
            this.style.width = this.size.getFloat() * 0.9 * p.widthScale;
            this.style.alpha = a;
            this.style.progress = drawOn;
            this.style.heavyStart = 0.2F;
            this.style.thinEnd = 0.3F;
            this.style.tile = 0.5;
            this.style.light = this.matLight;
            this.style.flat = grounded;
            this.style.normalX = 0.0;
            this.style.normalY = 1.0;
            this.style.normalZ = 0.0;
            Strokes.draw(n, this.style);
            if (this.counting) {
               this.drawnDoodles++;
            }
         }
      }
   }

   private DoodleLineStyle.Doodles kind(int qi) {
      DoodleLineStyle.Doodles d = this.doodles.get();
      if (d != DoodleLineStyle.Doodles.MIXED) {
         return d;
      } else {
         int k = qi + (int)(Rng.hash(qi, 11) * 2.0F) & 3;
         return k == 0
            ? DoodleLineStyle.Doodles.STARS
            : (k == 1 ? DoodleLineStyle.Doodles.SPIRALS : (k == 2 ? DoodleLineStyle.Doodles.LOOPS : DoodleLineStyle.Doodles.HEARTS));
      }
   }

   private static boolean pointAt(TrailShape s, double along) {
      for (int k = s.runs - 1; k >= 0; k--) {
         int b = s.runEnd[k];
         int a = s.runStart[k];
         if (b - a >= 2 && !(along < s.along[b - 1]) && !(along > s.along[a])) {
            for (int i = b - 1; i > a; i--) {
               double a0 = s.along[i];
               double a1 = s.along[i - 1];
               if (along >= a0 && along <= a1) {
                  double t = a1 - a0 < 1.0E-9 ? 0.0 : (along - a0) / (a1 - a0);
                  PX = s.x[i] + (s.x[i - 1] - s.x[i]) * t;
                  PY = s.y[i] + (s.y[i - 1] - s.y[i]) * t;
                  PZ = s.z[i] + (s.z[i - 1] - s.z[i]) * t;
                  double tx = s.x[i] - s.x[i - 1];
                  double tz = s.z[i] - s.z[i - 1];
                  double tl = Math.sqrt(tx * tx + tz * tz);
                  PTX = tl < 1.0E-6 ? 1.0 : tx / tl;
                  PTZ = tl < 1.0E-6 ? 0.0 : tz / tl;
                  PA_AGE = s.age[i] + (s.age[i - 1] - s.age[i]) * (float)t;
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%s, %s, %s every %.1f blocks, wobble %.0f%%, boil %.0f/s (seed %d); drawn: %d point(s), %d doodle(s)",
         this.orientation.get().displayName(),
         this.tool.get().displayName(),
         this.doodles.get().displayName(),
         this.doodleSpacing.getFloat(),
         this.wobble.getFloat(),
         this.boil.getFloat(),
         this.lastSeed,
         this.drawnPoints,
         this.drawnDoodles
      );
   }

   public static enum Doodles implements Displayable {
      MIXED("Mixed", "Stars, spirals, loops and hearts"),
      STARS("Stars", "Five-pointed stars"),
      SPIRALS("Spirals", "Little spirals"),
      LOOPS("Loops", "A row of cursive loops"),
      HEARTS("Hearts", "Little hearts"),
      NONE("None", "Just the line");

      private final String displayName;
      private final String description;

      private Doodles(String displayName, String description) {
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

   public static enum Orientation implements Displayable {
      FACING("Facing camera", "The line faces you"),
      GROUND("On the ground", "Drawn on the ground, like chalk on pavement");

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
