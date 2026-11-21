package xyz.daikirai.render.furiah.module.hit.styles;

import java.util.ArrayDeque;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.Breeze;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Paper;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class PaperBurstStyle extends HitStyleBase {
   private static final float BURST = 0.1F;
   private static final float FOLD = 0.2F;
   private static final float FADE_FROM = 0.82F;
   private static final int MAX_PIECES = 16;
   private static final int POOL = 8;
   private static final double PIECE = 0.14;
   private static final double FLUTTER_SPEED = 1.5;
   private static final float RIM = 0.08F;
   private static final float PAPER_WIND = 0.6F;
   public final SliderSetting pieces;
   public final ModeSetting<PaperBurstStyle.PaperColors> paperColors;
   public final ModeSetting<PaperBurstStyle.Shapes> shapes;
   public final BooleanSetting fold;
   public final SliderSetting flutter;
   public final BooleanSetting paperTexture;
   private final ArrayDeque<PaperBurstStyle.State> pool = new ArrayDeque<>();
   private final Paper.Pose pose = new Paper.Pose();
   private final float[] hsb = new float[3];
   private static final double[] TMP = new double[3];

   public PaperBurstStyle() {
      super(
         HitStyle.PAPER_BURST,
         0.9F,
         new ThemeTint()
      );
      this.paperColors = this.setting(
         new ModeSetting<>(
            "paperburst.paperColors",
            "Paper Colors",
            "Each piece a different pastel, all the Paper color, or the Paper color's hue turning across the burst.",
            PaperBurstStyle.PaperColors.class,
            PaperBurstStyle.PaperColors.PASTEL
         )
      );
      this.pieces = this.look(this.setting(new SliderSetting("paperburst.pieces", "Pieces", "How many paper squares burst out.", 4.0, 16.0, 8.0, 1.0)));
      this.shapes = this.setting(
         new ModeSetting<>("paperburst.shapes", "Shapes", "What the squares fold into.", PaperBurstStyle.Shapes.class, PaperBurstStyle.Shapes.MIXED)
            .style(ModeSetting.Style.CHIPS)
      );
      this.fold = this.setting(
         new BooleanSetting("paperburst.fold", "Fold in Flight", "The squares fold themselves as they fly (off: they burst out already folded).", true)
      );
      this.flutter = this.look(
         this.setting(new SliderSetting("paperburst.flutter", "Flutter", "How much they sway and tumble on the way down.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.paperTexture = this.setting(new BooleanSetting("paperburst.paperTexture", "Paper Texture", "The fine fibers of the paper.", true));
   }

   @Override
   public void start(HitFx fx) {
      PaperBurstStyle.State s = this.pool.poll();
      if (s == null) {
         s = new PaperBurstStyle.State();
      }

      Basis b = fx.basis;
      int n;
      s.n = n = Math.min(16, fx.count(this.pieces.getInt(), false));
      s.simTime = 0.0;
      s.floor.y = fx.hit.groundY;
      double sc = scale(fx);
      float sway = this.flutter.getFloat() / 100.0F;
      double ix = fx.ix() - b.fx * 0.08;
      double iy = fx.iy() - b.fy * 0.08;
      double iz = fx.iz() - b.fz * 0.08;

      for (int i = 0; i < n; i++) {
         int id = fx.seed + i * 43;
         s.shape[i] = this.shape(i, id);
         double a = (Math.PI * 2) * (i + 0.35 * hash(id, 1)) / n + hash(fx.seed, 2) * (Math.PI * 2);
         double dr = Math.cos(a);
         double du = Math.sin(a);
         double out = (2.0 + 1.0 * hash(id, 3)) * sc;
         double along = (1.2 + 1.0 * hash(id, 4)) * sc;
         double up = (0.2 + 0.7 * hash(id, 5)) * sc;
         double vx = b.fx * along + (b.rx * dr + b.ux * du) * out;
         double vy = b.fy * along + (b.ry * dr + b.uy * du) * out + up;
         double vz = b.fz * along + (b.rz * dr + b.uz * du) * out;
         s.fl[i].start(ix, iy, iz, vx, vy, vz, (float)hash(id, 6), sway * (0.6F + 0.8F * (float)hash(id, 7)), (hash(id, 8) - 0.5) * 6.0);
         s.foldAt[i] = 0.05F + 0.2F * i / Math.max(1.0, n - 1.0) * 0.8 + 0.03 * hash(id, 9);
         double tx = hash(id, 10) - 0.5;
         double ty = hash(id, 11) - 0.5;
         double tz = hash(id, 12) - 0.5;
         double tl = Math.sqrt(tx * tx + ty * ty + tz * tz) + 1.0E-9;
         s.tumble[i * 3] = tx / tl;
         s.tumble[i * 3 + 1] = ty / tl;
         s.tumble[i * 3 + 2] = tz / tl;
         s.tumbleRate[i] = (6.0 + 8.0 * hash(id, 13)) * (0.4 + 0.6 * sway);
         s.size[i] = 0.14 * sc * (0.85 + 0.3 * hash(id, 14));
         s.along[i] = n <= 1 ? 0.0F : (float)i / (n - 1);
      }

      Breeze.at(ix, iy, iz, fx.preview, s.wind);
      int k = 0;

      while (k < 3) {
         int n2 = k++;
         s.wind[n2] = s.wind[n2] * 0.4F;
      }

      fx.data = s;
   }

   private Paper.Shape shape(int i, int id) {
      switch ((PaperBurstStyle.Shapes)this.shapes.get()) {
         case CRANES:
            return Paper.CRANE;
         case STARS:
            return Paper.STAR;
         case HEARTS:
            return Paper.HEART;
         case PLANES:
            return Paper.PLANE;
         default:
            int k = (i + (int)(hash(id, 15) * 2.0)) % 4;
            return k == 0 ? Paper.CRANE : (k == 1 ? Paper.STAR : (k == 2 ? Paper.HEART : Paper.PLANE));
      }
   }

   @Override
   public void finish(HitFx fx) {
      if (fx.data instanceof PaperBurstStyle.State && this.pool.size() < 8) {
         this.pool.push((PaperBurstStyle.State)fx.data);
      }

      fx.data = null;
   }

   @Override
   public int passes(HitFx fx) {
      return FxPass.SOLID.bit();
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      PaperBurstStyle.State s = (PaperBurstStyle.State)fx.data;
      if (s != null) {
         float p = fx.progress();
         float alpha = fx.alpha() * (1.0F - Easing.smoothstep(0.82F, 1.0F, p));
         if (!(alpha <= 0.003F)) {
            advance(s, fx.styleTime() * 1.5);
            double t = fx.styleTime();
            boolean folded = !this.fold.get();
            int base = this.color.getColor(0.5F);
            MeshBatch.begin(Material.CUSTOM, Finish.MATTE, fx.light, 0.08F, -1, false, true);

            for (int i = 0; i < s.n; i++) {
               if (this.place(fx, f, s, i, t, folded)) {
                  int col = this.pieceColor(s, i, base);
                  float a = alpha * Easing.clamp01((float)(t / 0.04)) * fx.keep.cover(this.pose.x, this.pose.y, this.pose.z);
                  Paper.facets(s.shape[i], this.pose, Math.round(255.0F * Easing.clamp01(a)) << 24 | col & 16777215);
               }
            }

            if (fx.crit) {
               this.crane(fx, f, t, alpha, base);
            }

            MeshBatch.end();
            if (this.paperTexture.get()) {
               RenderState.decal(true);

               try {
                  for (int var19 = 0; var19 < s.n; var19++) {
                     if (this.place(fx, f, s, var19, t, folded)) {
                        int col = this.pieceColor(s, var19, base);
                        float a = alpha * 0.55F * fx.keep.cover(this.pose.x, this.pose.y, this.pose.z);
                        if (a > 0.01F) {
                           Paper.fibers(s.shape[var19], this.pose, Math.round(255.0F * a) << 24 | ColorUtil.shade(col, 0.55F * fx.light) & 16777215);
                        }
                     }
                  }
               } finally {
                  RenderState.decal(false);
               }
            }
         }
      }
   }

   private static void advance(PaperBurstStyle.State s, double t) {
      while (s.simTime < t - 1.0E-6) {
         double dt = Math.min(0.008333333333333333, t - s.simTime);

         for (int i = 0; i < s.n; i++) {
            s.fl[i].step(dt, s.floor, 0.0, 0.0, 0.0, s.wind[0], s.wind[1], s.wind[2]);
         }

         s.simTime += dt;
      }
   }

   private int pieceColor(PaperBurstStyle.State s, int i, int base) {
      switch ((PaperBurstStyle.PaperColors)this.paperColors.get()) {
         case ONE:
            return base;
         case GRADIENT:
            return Paper.hueTurn(base, (s.along[i] - 0.5F) * 0.33F, this.hsb);
         default:
            return Paper.PASTEL_SET[i % Paper.PASTEL_SET.length];
      }
   }

   private boolean place(HitFx fx, FxFrame f, PaperBurstStyle.State s, int i, double t, boolean folded) {
      Paper.Flutter fl = s.fl[i];
      double fold = folded ? 1.0 : Easing.smoothstep((float)s.foldAt[i], (float)(s.foldAt[i] + 0.2F), (float)t);
      double grow = 0.35 + 0.65 * Easing.outCubic(Easing.clamp01((float)(t / 0.1F)));
      if (s.shape[i] == Paper.PLANE && !fl.landed) {
         double vl = Math.sqrt(fl.vx * fl.vx + fl.vy * fl.vy + fl.vz * fl.vz);
         if (vl < 1.0E-6) {
            fl.pose(this.pose, s.size[i] * grow, fold);
         } else {
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

            double var41;
            double ux = -(var41 = rz / rl) * fy0;
            double var40;
            double uy = var41 * fx0 - (var40 = rx / rl) * fz0;
            double uz = var40 * fy0;
            double bank = 0.4 * fl.rock();
            double cb = Math.cos(bank);
            double sb = Math.sin(bank);
            this.pose.x = fl.x;
            this.pose.y = fl.y;
            this.pose.z = fl.z;
            this.pose.fx = fx0;
            this.pose.fy = fy0;
            this.pose.fz = fz0;
            this.pose.ux = ux * cb + var40 * sb;
            this.pose.uy = uy * cb;
            this.pose.uz = uz * cb + var41 * sb;
            this.pose.rx = var40 * cb - ux * sb;
            this.pose.ry = -uy * sb;
            this.pose.rz = var41 * cb - uz * sb;
            this.pose.scale = s.size[i] * grow;
            this.pose.fold = fold;
            this.pose.flap = 0.0;
         }
      } else {
         fl.pose(this.pose, s.size[i] * grow, fold);
         if (!fl.landed) {
            double ang = s.tumbleRate[i] * (1.0 - Math.exp(-2.5 * t)) / 2.5;
            this.tumble(s.tumble[i * 3], s.tumble[i * 3 + 1], s.tumble[i * 3 + 2], ang);
         }
      }

      this.pose.x = f.rx(this.pose.x);
      this.pose.y = f.ry(this.pose.y);
      this.pose.z = f.rz(this.pose.z);
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
      double d = kx * x + ky * y + kz * z;
      v[0] = x * c + (ky * z - kz * y) * s + kx * d * (1.0 - c);
      v[1] = y * c + (kz * x - kx * z) * s + ky * d * (1.0 - c);
      v[2] = z * c + (kx * y - ky * x) * s + kz * d * (1.0 - c);
   }

   private void crane(HitFx fx, FxFrame f, double t, float alpha, int base) {
      Basis b = fx.basis;
      double sc = scale(fx);
      double life = 0.9;
      double k = Math.min(1.0, t / life);
      double ix = fx.ix() - b.fx * 0.08;
      double iy = fx.iy() - b.fy * 0.08;
      double iz = fx.iz() - b.fz * 0.08;
      double along = 1.4 * sc * t;
      double up = (3.0 * t - 1.6 * t * t) * sc;
      double side = 0.55 * sc * Math.sin(Math.PI * k);
      double x = ix + b.fx * along + b.rx * side;
      double y = iy + b.fy * along + up;
      double z = iz + b.fz * along + b.rz * side;
      double vx = b.fx * 1.4 + b.rx * 0.55 * Math.PI / life * Math.cos(Math.PI * k);
      double vy = b.fy * 1.4 + (3.0 - 3.2 * t);
      double vz = b.fz * 1.4 + b.rz * 0.55 * Math.PI / life * Math.cos(Math.PI * k);
      double vl = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1.0E-9;
      double fx0 = vx / vl;
      double fy0 = vy / vl;
      double fz0 = vz / vl;
      double rx = -fz0;
      double rl = Math.sqrt(rx * rx + fx0 * fx0) + 1.0E-9;
      rx /= rl;
      double rz = fx0 / rl;
      this.pose.x = f.rx(x);
      this.pose.y = f.ry(y);
      this.pose.z = f.rz(z);
      this.pose.fx = fx0;
      this.pose.fy = fy0;
      this.pose.fz = fz0;
      this.pose.ux = -rz * fy0;
      this.pose.uy = rz * fx0 - rx * fz0;
      this.pose.uz = rx * fy0;
      this.pose.rx = rx;
      this.pose.ry = 0.0;
      this.pose.rz = rz;
      this.pose.scale = 0.30800000000000005 * sc * (0.4 + 0.6 * Easing.outBack(Easing.clamp01((float)(t / 0.12)), 1.6F));
      this.pose.fold = 1.0;
      double beat = Math.min(1.0, t / (life * 0.7));
      this.pose.flap = 0.65 * Math.sin(beat * Math.PI * 4.0) * (1.0 - beat * 0.3);
      float a = alpha * fx.keep.cover(this.pose.x, this.pose.y, this.pose.z);
      int col = this.paperColors.get() == PaperBurstStyle.PaperColors.PASTEL ? -1 : base;
      Paper.facets(Paper.CRANE, this.pose, Math.round(255.0F * Easing.clamp01(a)) << 24 | col & 16777215);
   }

   @Override
   public String stateForTest(HitFx fx) {
      PaperBurstStyle.State s = (PaperBurstStyle.State)fx.data;
      if (s == null) {
         return null;
      } else {
         double t = fx.styleTime();
         int folding = 0;
         int folded = 0;
         int landed = 0;

         for (int i = 0; i < s.n; i++) {
            double k = this.fold.get() ? Easing.smoothstep((float)s.foldAt[i], (float)(s.foldAt[i] + 0.2F), (float)t) : 1.0;
            if (k >= 0.999) {
               folded++;
            } else if (k > 0.001) {
               folding++;
            }

            if (s.fl[i].landed) {
               landed++;
            }
         }

         return String.format(
            Locale.ROOT,
            "%s, %s, %d pieces: %d folding, %d folded, %d landed; flutter %.0f%%%s",
            this.shapes.get().displayName(),
            this.paperColors.get().displayName(),
            s.n,
            folding,
            folded,
            landed,
            this.flutter.getFloat(),
            fx.crit ? "; crit crane" : ""
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

   private static final class Floor implements Surface {
      double y;

      private Floor() {
      }

      @Override
      public double at(double x, double y, double z) {
         return this.y;
      }
   }

   public static enum PaperColors implements Displayable {
      PASTEL("Pastel set", "Each piece a different pastel"),
      ONE("One color", "Every piece the Paper color"),
      GRADIENT("Gradient across the burst", "The Paper color, its hue turning piece by piece");

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

   public static enum Shapes implements Displayable {
      MIXED("Mixed", "Cranes, stars, hearts and planes"),
      CRANES("Cranes", "Paper cranes"),
      STARS("Stars", "Puffy folded stars"),
      HEARTS("Hearts", "Folded hearts"),
      PLANES("Planes", "Paper planes that glide");

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

   private static final class State {
      final Paper.Flutter[] fl = new Paper.Flutter[16];
      final Paper.Shape[] shape = new Paper.Shape[16];
      final double[] foldAt = new double[16];
      final double[] tumble = new double[48];
      final double[] tumbleRate = new double[16];
      final double[] size = new double[16];
      final float[] along = new float[16];
      final PaperBurstStyle.Floor floor = new PaperBurstStyle.Floor();
      int n;
      double simTime;
      final float[] wind = new float[3];

      State() {
         for (int i = 0; i < 16; i++) {
            this.fl[i] = new Paper.Flutter();
         }
      }
   }
}
