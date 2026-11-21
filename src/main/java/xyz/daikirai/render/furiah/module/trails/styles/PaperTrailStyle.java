package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Paper;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;

public final class PaperTrailStyle extends TrailStyleBase {
   private static final int MAX_PIECES = 160;
   private static final float FOLD = 0.3F;
   private static final float LIE = 0.9F;
   private static final float FADE = 0.5F;
   private static final float SNAP = 0.3F;
   private static final float BEAT = 0.45F;
   private static final float GLIDE_FLAP = 0.18F;
   private static final float RIM = 0.08F;
   private static final double PER_BLOCK = 1.5;
   public final ModeSetting<PaperTrailStyle.PaperColors> paperColors;
   public final ModeSetting<PaperTrailStyle.Shapes> shapes;
   public final ModeSetting<PaperTrailStyle.Flight> flight;
   public final BooleanSetting settle;
   public final BooleanSetting paperTexture;
   private final GroundCache ground = new GroundCache();
   private TrailFrame groundFrame;
   private final Surface floor = new Surface() {
      @Override
      public double at(double x, double y, double z) {
         return PaperTrailStyle.this.ground
            .surface(PaperTrailStyle.this.groundFrame == null ? null : PaperTrailStyle.this.groundFrame.world, x, y + 0.5, z, 0.8, 4.0);
      }
   };
   private final Rng rng = new Rng();
   private final Paper.Pose pose = new Paper.Pose();
   private final float[] hsb = new float[3];
   private int spawned;
   private static final double[] TMP = new double[3];

   public PaperTrailStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.PAPER_TRAIL,
         spec().size(0.08, 0.3, 0.18, 0.01).length(2.2).anchor(TrailAnchor.BACK, false).windy()
      );
      this.paperColors = this.setting(
         new ModeSetting<>(
            "paper_trail.paperColors",
            "Paper Colors",
            "Each piece a different pastel, all the Paper color, or the Paper color's hue turning piece by piece.",
            PaperTrailStyle.PaperColors.class,
            PaperTrailStyle.PaperColors.PASTEL
         )
      );
      this.doesNotApply(new Setting[]{this.glow});
      this.shapes = this.setting(
         new ModeSetting<>("paper_trail.shapes", "Shapes", "What flutters off behind you.", PaperTrailStyle.Shapes.class, PaperTrailStyle.Shapes.MIXED)
            .style(ModeSetting.Style.CHIPS)
      );
      this.flight = this.setting(
         new ModeSetting<>(
            "paper_trail.flight",
            "Flight",
            "Planes and cranes glide (stars and confetti tumble), or every piece tumbles down.",
            PaperTrailStyle.Flight.class,
            PaperTrailStyle.Flight.GLIDE
         )
      );
      this.settle = this.setting(
         new BooleanSetting(
            "paper_trail.settle", "Settle on the Ground", "The pieces land, lie flat for a moment and fade (off: they fade as they land).", true
         )
      );
      this.paperTexture = this.setting(new BooleanSetting("paper_trail.paperTexture", "Paper Texture", "The fine fibers of the paper.", true));
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new PaperTrailStyle.Pieces(this.floor);
   }

   @Override
   public int passes() {
      return FxPass.SOLID.bit();
   }

   @Override
   public void update(TrailFrame f) {
      this.groundFrame = f;
      PaperTrailStyle.Pieces d = (PaperTrailStyle.Pieces)f.store(this.type);
      if (d != null && d.n > 0) {
         float[] w = this.breeze(f, f.eyeX, f.eyeY, f.eyeZ, 0.0F);
         d.wx = w[0];
         d.wy = w[1];
         d.wz = w[2];
      }

      super.update(f);
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      if (!p.ghost && this.spawnPass(pass)) {
         int due = this.piecesDue(p, 1.5, 3);
         if (due != 0) {
            this.groundFrame = f;
            PaperTrailStyle.Pieces d = this.store(f);
            double yaw = Math.toRadians(p.pose.bodyYaw);
            double fx = -Math.sin(yaw);
            double fz = Math.cos(yaw);
            float life = this.lengthNow(p.speed);

            for (int i = 0; i < due && !this.module.piecesFull(f); i++) {
               this.rng.seed((int)(f.now * 5113.0) + i * 131 + p.entityId * 37);
               double side = this.rng.signed();
               double x = p.headX - fx * 0.2 + fz * side * 0.25;
               double y = p.headY + this.rng.signed() * 0.1;
               double z = p.headZ - fz * 0.2 - fx * side * 0.25;
               if (!(f.nearFade(x, y, z) < 0.3F)) {
                  int k = d.add();
                  Paper.Shape shape = this.pick(this.rng.next());
                  boolean glides = this.flight.get() == PaperTrailStyle.Flight.GLIDE && (shape == Paper.PLANE || shape == Paper.CRANE);
                  double back = glides ? 1.3 + this.rng.next() * 0.6 : 0.5 + this.rng.next() * 0.5;
                  double out = this.rng.signed() * 0.6;
                  double vx = -fx * back + fz * out;
                  double vy = (glides ? 0.45 : 0.8) + this.rng.next() * 0.45;
                  double vz = -fz * back - fx * out;
                  d.fl[k].start(x, y, z, vx, vy, vz, this.rng.next(), (glides ? 0.25F : 0.55F) + this.rng.next() * 0.4F, this.rng.signed() * 3.0);
                  d.shape[k] = shape;
                  d.age[k] = 0.0F;
                  d.life[k] = life * (0.85F + this.rng.next() * 0.3F);
                  d.landed[k] = -1.0F;
                  d.size[k] = this.size.getFloat() * (0.85F + this.rng.next() * 0.3F) * p.widthScale;
                  d.alpha[k] = this.opacity.getFloat() / 100.0F * p.alpha;
                  d.light[k] = this.matLight;
                  double tx = this.rng.signed();
                  double ty = this.rng.signed();
                  double tz = this.rng.signed();
                  double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
                  d.ax[k] = (float)(tx / tl);
                  d.ay[k] = (float)(ty / tl);
                  d.az[k] = (float)(tz / tl);
                  d.rate[k] = glides ? 0.0F : 5.0F + this.rng.next() * 5.0F;
                  d.glides[k] = glides;
                  d.far[k] = p.reduced;
                  d.settles[k] = this.settle.get();
                  this.colorOf(d, k, this.rng.next(), p.speed);
                  this.spawned++;
               }
            }
         }
      }
   }

   private Paper.Shape pick(float r) {
      switch ((PaperTrailStyle.Shapes)this.shapes.get()) {
         case PLANES:
            return Paper.PLANE;
         case CRANES:
            return Paper.CRANE;
         case STARS:
            return Paper.STAR;
         case CONFETTI:
            return Paper.CONFETTI;
         default:
            return r < 0.3F ? Paper.PLANE : (r < 0.55F ? Paper.CRANE : (r < 0.8F ? Paper.STAR : Paper.CONFETTI));
      }
   }

   private void colorOf(PaperTrailStyle.Pieces d, int k, float r, float speed) {
      d.spot[k] = 0.0F;
      if (this.tint != 0) {
         d.color[k] = this.tint | 0xFF000000;
      } else {
         switch ((PaperTrailStyle.PaperColors)this.paperColors.get()) {
            case PASTEL:
               d.color[k] = Paper.PASTEL_SET[Math.min(Paper.PASTEL_SET.length - 1, (int)(r * Paper.PASTEL_SET.length))];
               return;
            case GRADIENT:
               d.color[k] = Paper.hueTurn(this.color.getColor(0.0F) | 0xFF000000, this.spawned % 12 / 12.0F * 0.4F - 0.2F, this.hsb);
               return;
            default:
               d.color[k] = 0;
               switch ((TrailStyleBase.ColorAlong)this.colorAlong) {
                  case BY_AGE:
                     d.spot[k] = -1.0F;
                     break;
                  case BY_SPEED:
                     d.spot[k] = Easing.clamp01((speed - 4.3F) / 6.8999996F);
                     break;
                  default:
                     d.spot[k] = r;
               }
         }
      }
   }

   private float alphaOf(PaperTrailStyle.Pieces d, int i, TrailFrame f) {
      float a = d.alpha[i] * Easing.clamp01(d.age[i] / 0.08F);
      if (d.landed[i] >= 0.0F) {
         float since = d.age[i] - d.landed[i];
         a *= d.settles[i] ? 1.0F - Easing.smoothstep(0.9F, 1.4F, since) : 1.0F - Easing.clamp01(since / 0.3F);
      } else {
         a *= 1.0F - Easing.smoothstep(d.life[i] - 0.5F, d.life[i], d.age[i]);
      }

      Paper.Flutter fl = d.fl[i];
      return a * f.nearFade(fl.x, fl.y, fl.z) * f.fog(fl.x, fl.y, fl.z);
   }

   private int colorNow(PaperTrailStyle.Pieces d, int i, TrailFrame f) {
      int c = d.color[i];
      if (c == 0) {
         float pos = d.spot[i] < 0.0F ? Easing.clamp01(d.age[i] / Math.max(0.1F, d.life[i])) : d.spot[i];
         c = this.color.getColor(pos, f.colorTime) | 0xFF000000;
      }

      Paper.Flutter fl = d.fl[i];
      c = lit(c, f, fl.x, fl.y, fl.z);
      return d.light[i] < 0.999F ? ColorUtil.shade(c, d.light[i]) : c;
   }

   private boolean place(PaperTrailStyle.Pieces d, int i, TrailFrame f) {
      Paper.Flutter fl = d.fl[i];
      float age = d.age[i];
      double fold = d.far[i] ? 1.0 : Easing.smoothstep(0.0F, 0.3F, age);
      double scale = d.size[i] * (0.5 + 0.5 * Easing.outCubic(Easing.clamp01(age / 0.3F)));
      double vl = Math.sqrt(fl.vx * fl.vx + fl.vy * fl.vy + fl.vz * fl.vz);
      if (d.glides[i] && !fl.landed && vl > 1.0E-6) {
         double fx0 = fl.vx / vl;
         double fy0 = fl.vy / vl;
         double fz0 = fl.vz / vl;
         double rx = -fz0;
         double rz = fx0;
         double rl = Math.sqrt(rx * rx + fx0 * fx0);
         if (rl < 1.0E-6) {
            rx = 1.0;
            rz = 0.0;
            rl = 1.0;
         }

         double var37;
         double ux = -(var37 = rz / rl) * fy0;
         double var36;
         double uy = var37 * fx0 - (var36 = rx / rl) * fz0;
         double uz = var36 * fy0;
         double bank = 0.45 * fl.rock();
         double cb = Math.cos(bank);
         double sb = Math.sin(bank);
         this.pose.x = fl.x;
         this.pose.y = fl.y;
         this.pose.z = fl.z;
         this.pose.fx = fx0;
         this.pose.fy = fy0;
         this.pose.fz = fz0;
         this.pose.ux = ux * cb + var36 * sb;
         this.pose.uy = uy * cb;
         this.pose.uz = uz * cb + var37 * sb;
         this.pose.rx = var36 * cb - ux * sb;
         this.pose.ry = -uy * sb;
         this.pose.rz = var37 * cb - uz * sb;
         this.pose.scale = scale;
         this.pose.fold = fold;
         this.pose.flap = 0.0;
      } else {
         fl.pose(this.pose, scale, fold);
         if (!fl.landed && d.rate[i] > 0.0F) {
            this.tumble(d.ax[i], d.ay[i], d.az[i], d.rate[i] * age);
         }
      }

      if (d.shape[i] == Paper.CRANE) {
         this.pose.flap = age < 0.45F ? 0.9 * Math.sin((Math.PI * 2) * age / 0.45F) : (fl.landed ? 0.0 : 0.18F);
      }

      this.pose.x = this.pose.x - f.viewX;
      this.pose.y = this.pose.y - f.viewY;
      this.pose.z = this.pose.z - f.viewZ;
      return this.pose.scale > 1.0E-4;
   }

   private void tumble(double kx, double ky, double kz, double ang) {
      double c = Math.cos(ang);
      double sn = Math.sin(ang);
      double[] v = TMP;
      v[0] = this.pose.fx;
      v[1] = this.pose.fy;
      v[2] = this.pose.fz;
      rotate(v, kx, ky, kz, c, sn);
      this.pose.fx = v[0];
      this.pose.fy = v[1];
      this.pose.fz = v[2];
      v[0] = this.pose.ux;
      v[1] = this.pose.uy;
      v[2] = this.pose.uz;
      rotate(v, kx, ky, kz, c, sn);
      this.pose.ux = v[0];
      this.pose.uy = v[1];
      this.pose.uz = v[2];
      v[0] = this.pose.rx;
      v[1] = this.pose.ry;
      v[2] = this.pose.rz;
      rotate(v, kx, ky, kz, c, sn);
      this.pose.rx = v[0];
      this.pose.ry = v[1];
      this.pose.rz = v[2];
   }

   private static void rotate(double[] v, double kx, double ky, double kz, double c, double s) {
      double x = v[0];
      double y = v[1];
      double z = v[2];
      double dt = kx * x + ky * y + kz * z;
      v[0] = x * c + (ky * z - kz * y) * s + kx * dt * (1.0 - c);
      v[1] = y * c + (kz * x - kx * z) * s + ky * dt * (1.0 - c);
      v[2] = z * c + (kx * y - ky * x) * s + kz * dt * (1.0 - c);
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      PaperTrailStyle.Pieces d = (PaperTrailStyle.Pieces)f.store(this.type);
      if (d != null && d.n != 0 && pass == FxPass.SOLID) {
         MeshBatch.begin(Material.CUSTOM, Finish.MATTE, 1.0F, 0.08F, -1, false, true);

         try {
            for (int i = 0; i < d.n; i++) {
               float a = this.alphaOf(d, i, f);
               if (!(a <= 0.01F) && this.place(d, i, f)) {
                  Paper.facets(d.shape[i], this.pose, Math.round(255.0F * Easing.clamp01(a)) << 24 | this.colorNow(d, i, f) & 16777215);
               }
            }
         } finally {
            MeshBatch.end();
         }

         if (this.paperTexture.get()) {
            RenderState.decal(true);

            try {
               for (int var15 = 0; var15 < d.n; var15++) {
                  float a;
                  if (!d.far[var15] && !((a = this.alphaOf(d, var15, f) * 0.55F) <= 0.01F) && this.place(d, var15, f)) {
                     Paper.fibers(
                        d.shape[var15], this.pose, Math.round(255.0F * Easing.clamp01(a)) << 24 | ColorUtil.shade(this.colorNow(d, var15, f), 0.55F) & 16777215
                     );
                  }
               }
            } finally {
               RenderState.decal(false);
            }
         }
      }
   }

   @Override
   public String stateForTest() {
      PaperTrailStyle.Pieces d = this.groundFrame == null ? null : (PaperTrailStyle.Pieces)this.groundFrame.store(this.type);
      int flying = 0;
      int down = 0;
      if (d != null) {
         for (int i = 0; i < d.n; i++) {
            if (d.landed[i] >= 0.0F) {
               down++;
            } else {
               flying++;
            }
         }
      }

      return String.format(
         Locale.ROOT,
         "%s, %s, %s, settle %s, texture %s; %d flying, %d down",
         this.shapes.get().displayName(),
         this.flight.get().displayName(),
         this.paperColors.get().displayName(),
         this.settle.get() ? "on" : "off",
         this.paperTexture.get() ? "on" : "off",
         flying,
         down
      );
   }

   public static enum Flight implements Displayable {
      GLIDE("Glide", "Planes and cranes glide; stars and confetti tumble"),
      TUMBLE("Tumble", "Every piece tumbles down");

      private final String displayName;
      private final String description;

      private Flight(String displayName, String description) {
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

   public static enum PaperColors implements Displayable {
      PASTEL("Pastel set", "Each piece a different pastel"),
      ONE("One color", "Every piece the Paper color (Color Along the Trail)"),
      GRADIENT("Gradient", "The Paper color, its hue turning piece by piece");

      private final String displayName;
      private final String description;

      private PaperColors(String displayName, String description) {
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

   private static final class Pieces implements TrailStyleBase.Store {
      final Paper.Flutter[] fl = new Paper.Flutter[160];
      final Paper.Shape[] shape = new Paper.Shape[160];
      final float[] age = new float[160];
      final float[] life = new float[160];
      final float[] landed = new float[160];
      final float[] size = new float[160];
      final float[] alpha = new float[160];
      final float[] light = new float[160];
      final float[] ax = new float[160];
      final float[] ay = new float[160];
      final float[] az = new float[160];
      final float[] rate = new float[160];
      final float[] spot = new float[160];
      final int[] color = new int[160];
      final boolean[] glides = new boolean[160];
      final boolean[] far = new boolean[160];
      final boolean[] settles = new boolean[160];
      final Surface floor;
      int n;
      int oldest;
      double wx;
      double wy;
      double wz;

      Pieces(Surface floor) {
         this.floor = floor;

         for (int i = 0; i < 160; i++) {
            this.fl[i] = new Paper.Flutter();
         }
      }

      int add() {
         if (this.n < 160) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 160;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         if (!(dt <= 0.0F)) {
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               this.age[i] = this.age[i] + dt;
               Paper.Flutter f = this.fl[i];
               float left = dt;

               while (left > 1.0E-6F) {
                  float step = Math.min(0.008333334F, left);
                  f.step(step, this.floor, 0.0, 0.0, 0.0, this.wx, this.wy, this.wz);
                  left -= step;
               }

               if (f.landed && this.landed[i] < 0.0F) {
                  this.landed[i] = this.age[i];
               }

               boolean gone = this.landed[i] >= 0.0F ? this.age[i] - this.landed[i] >= (this.settles[i] ? 1.4F : 0.3F) : this.age[i] >= this.life[i];
               if (!gone) {
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
         Paper.Flutter t = this.fl[b];
         this.fl[b] = this.fl[a];
         this.fl[a] = t;
         this.shape[b] = this.shape[a];
         this.age[b] = this.age[a];
         this.life[b] = this.life[a];
         this.landed[b] = this.landed[a];
         this.size[b] = this.size[a];
         this.alpha[b] = this.alpha[a];
         this.light[b] = this.light[a];
         this.ax[b] = this.ax[a];
         this.ay[b] = this.ay[a];
         this.az[b] = this.az[a];
         this.rate[b] = this.rate[a];
         this.spot[b] = this.spot[a];
         this.color[b] = this.color[a];
         this.glides[b] = this.glides[a];
         this.far[b] = this.far[a];
         this.settles[b] = this.settles[a];
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

   public static enum Shapes implements Displayable {
      MIXED("Mixed", "Planes, cranes, stars and confetti"),
      PLANES("Planes", "Paper planes that glide and bank"),
      CRANES("Cranes", "Paper cranes that flap once and glide"),
      STARS("Stars", "Folded stars that tumble"),
      CONFETTI("Confetti", "Confetti squares that tumble");

      private final String displayName;
      private final String description;

      private Shapes(String displayName, String description) {
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
