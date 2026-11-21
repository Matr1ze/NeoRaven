package xyz.daikirai.render.furiah.module.hit.styles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Comic;
import xyz.daikirai.render.furiah.fx.kit.Halftone;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.EnumSetSetting;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;

public final class PowStyle extends HitStyleBase {
   private static final float POP = 0.14545454F;
   private static final float SLAM = 0.25454545F;
   private static final float SHAKE = 0.5272727F;
   private static final float HOLD = 0.7090909F;
   private static final String[] CRIT_WORDS = new String[]{"KAPOW!", "KRAK!"};
   private static final int MAX_CUSTOM = 8;
   private static final int MAX_WORD = 8;
   public final ThemeTint lettering;
   public final ThemeTint outline;
   public final ModeSetting<PowStyle.Words> words;
   public final EnumSetSetting<PowStyle.Word> wordList;
   public final TextSetting custom;
   public final ModeSetting<PowStyle.Shape> shape;
   public final BooleanSetting halftone;
   public final BooleanSetting actionLines;
   public final SliderSetting size;
   public final SliderSetting fill;
   private final List<String> pool = new ArrayList<>();
   private int poolVersion = -1;
   private String poolCustom = null;

   public PowStyle() {
      super(
         HitStyle.POW,
         0.55F,
         new ThemeTint()
      );
      this.lettering = ThemeTint.detail(-1689274);
      this.outline = ThemeTint.detail(-15658735);
      this.words = this.setting(
         new ModeSetting<>("pow.words", "Words", "A word from the list each hit, or always the list's first.", PowStyle.Words.class, PowStyle.Words.RANDOM)
      );
      this.wordList = this.setting(
         new EnumSetSetting<>(
            "pow.wordList",
            "Word List",
            "The words it says.",
            PowStyle.Word.class,
            false,
            new PowStyle.Word[]{PowStyle.Word.POW, PowStyle.Word.BAM, PowStyle.Word.WHAM, PowStyle.Word.SMACK, PowStyle.Word.BONK}
         )
      );
      this.custom = this.setting(
         new TextSetting("pow.custom", "Custom Words", "Your own words for the list, separated by commas, up to 8 characters each.", "", 72)
      );
      this.shape = this.setting(
         new ModeSetting<>("pow.shape", "Burst Shape", "The balloon's shape.", PowStyle.Shape.class, PowStyle.Shape.SPIKY).style(ModeSetting.Style.CHIPS)
      );
      this.halftone = this.setting(new BooleanSetting("pow.halftone", "Halftone", "Printed halftone dots in the balloon's shade.", true));
      this.actionLines = this.setting(new BooleanSetting("pow.actionLines", "Action Lines", "Speed lines all round the balloon.", true));
      this.size = this.look(this.setting(new SliderSetting("pow.size", "Size", "How big the balloon and the word are.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.fill = this.setting(new SliderSetting("pow.fill", "Fill Opacity", "How solid the balloon's fill is.", 40.0, 100.0, 85.0, 5.0).unit("%"));
   }

   private List<String> pool() {
      String c = this.custom.get();
      if (this.poolVersion != this.wordList.version() || !c.equals(this.poolCustom)) {
         this.poolVersion = this.wordList.version();
         this.poolCustom = c;
         this.pool.clear();

         for (PowStyle.Word w : PowStyle.Word.values()) {
            if (this.wordList.includes(w)) {
               this.pool.add(w.text);
            }
         }

         for (String part : c.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && this.pool.size() < PowStyle.Word.values().length + 8) {
               this.pool.add(t.length() > 8 ? t.substring(0, 8) : t);
            }
         }

         if (this.pool.isEmpty()) {
            this.pool.add(PowStyle.Word.POW.text);
         }

         for (String wx : this.pool) {
            Comic.word(wx);
         }
      }

      return this.pool;
   }

   private String wordOf(HitFx fx) {
      if (fx.crit) {
         return CRIT_WORDS[(int)(hash(fx.seed, 3) * CRIT_WORDS.length) % CRIT_WORDS.length];
      } else {
         List<String> list = this.pool();
         return this.words.get() == PowStyle.Words.ONE ? list.get(0) : list.get((int)(hash(fx.seed, 5) * list.size()) % list.size());
      }
   }

   @Override
   public void start(HitFx fx) {
      fx.data = this.wordOf(fx);
      Comic.word((String)fx.data);
   }

   @Override
   public int passes(HitFx fx) {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      float p = fx.progress();
      float alpha = fx.alpha();
      if (!(alpha <= 0.003F) && fx.data instanceof String) {
         Camera cam = Vfx.camera();
         double rx = cam.rightX;
         double ry = cam.rightY;
         double rz = cam.rightZ;
         double ux = cam.upX;
         double uy = cam.upY;
         double uz = cam.upZ;
         double s = scale(fx) * this.size.getFloat() / 100.0 * (fx.crit ? 1.3 : 1.0);
         double nudge = 0.22 * s * fx.hit.side;
         double x = f.rx(fx.ix() - fx.basis.fx * 0.3) + rx * nudge;
         double y = f.ry(fx.iy() - fx.basis.fy * 0.3) + ry * nudge + uy * 0.05 * s;
         double z = f.rz(fx.iz() - fx.basis.fz * 0.3) + rz * nudge;
         double sw = fx.hit.screenSwipeX >= 0.0F ? 1.0 : -1.0;
         float in = Easing.clamp01(p / 0.14545454F);
         double by;
         double bx;
         if (p < 0.14545454F) {
            double e = Easing.outBack(in, 2.4F);
            bx = e * (1.0 - 0.25 * Math.sin(Math.PI * in));
            by = e * (1.0 + 0.3 * Math.sin(Math.PI * in));
         } else if (p < 0.7090909F) {
            double wob = 0.04 * Math.exp(-(p - 0.14545454F) * 18.0) * Math.sin((p - 0.14545454F) * 60.0);
            bx = 1.0 + wob;
            by = 1.0 - wob;
         } else {
            float out = Easing.clamp01((p - 0.7090909F) / 0.2909091F);
            if (out < 0.45F) {
               double q = Easing.outCubic(out / 0.45F);
               bx = 1.0 + 0.35 * q;
               by = 1.0 - 0.55 * q;
            } else {
               double q = Easing.inCubic((out - 0.45F) / 0.55F);
               bx = 1.35 * (1.0 + 0.3 * q) * (1.0 - q);
               by = 0.45 * (1.0 - q);
            }
         }

         double w = 0.95 * s * bx;
         double h = 0.8 * s * by;
         if (!(w <= 0.001) && !(h <= 0.001)) {
            int burst = this.color.getColor(0.5F);
            int line = this.outline.getColor(0.5F);
            int fillCol = ColorUtil.mulAlpha(burst, alpha * this.fill.getFloat() / 100.0F);
            int dots = ColorUtil.mulAlpha(ColorUtil.shade(burst, 0.7F), alpha);
            int lineCol = ColorUtil.mulAlpha(line, alpha);
            int variant = (int)(hash(fx.seed, 7) * 4.0) % 4;
            double turn = sw * Math.toRadians(-6.0);
            if (this.actionLines.get() && p < 0.8090909F) {
               float la = alpha * Easing.clamp01(in * 2.0F) * (1.0F - Easing.smoothstep(0.6590909F, 0.8090909F, p));
               Comic.actionLines(x, y, z, rx, ry, rz, ux, uy, uz, 0.56 * s, 0.86 * s, 12, fx.seed, 0.018 * s, ColorUtil.mulAlpha(line, la));
            }

            if (fx.crit) {
               Comic.balloon(
                  this.shape.get().kit,
                  variant,
                  x,
                  y,
                  z,
                  rx,
                  ry,
                  rz,
                  ux,
                  uy,
                  uz,
                  w * 1.14,
                  h * 1.14,
                  turn,
                  ColorUtil.mulAlpha(-1, alpha),
                  0,
                  false,
                  lineCol,
                  1.6F
               );
            }

            Comic.balloon(this.shape.get().kit, variant, x, y, z, rx, ry, rz, ux, uy, uz, w, h, turn, fillCol, dots, this.halftone.get(), lineCol, 1.4F);
            if (p >= 0.087272726F) {
               Comic.Word word = Comic.word((String)fx.data);
               float slam = Easing.clamp01((p - 0.087272726F) / 0.16727272F);
               double grow = 1.0 + 1.2 * (1.0 - Easing.outCubic(slam));
               double shake = p < 0.5272727F ? 0.035 * s * Math.exp(-(p - 0.25454545F) * 9.0) * Math.max(0.0, (double)Math.signum(p - 0.25454545F)) : 0.0;
               double sx = shake * Math.sin(p * 210.0 + fx.seed);
               double sy = shake * Math.cos(p * 170.0 + fx.seed * 0.7);
               double wordH = 0.3 * s * grow * Math.min(bx, 1.2) * Math.min(by * 1.2, 1.0);
               double tilt = sw * Math.toRadians(-11.0)
                  + (p < 0.5272727F ? Math.toRadians(4.0) * Math.sin(p * 90.0) * Math.exp(-(p - 0.25454545F) * 8.0) : 0.0);
               float wa = alpha * Easing.clamp01(slam * 3.0F);
               double wx = x + rx * sx + ux * sy;
               double wy = y + ry * sx + uy * sy;
               double wz = z + rz * sx + uz * sy;
               Comic.lettering(
                  word,
                  wx,
                  wy,
                  wz,
                  rx,
                  ry,
                  rz,
                  ux,
                  uy,
                  uz,
                  wordH,
                  tilt,
                  ColorUtil.mulAlpha(this.lettering.getColor(0.5F), wa),
                  ColorUtil.mulAlpha(line, wa),
                  1.0F
               );
            }

            if (fx.crit) {
               this.stars(fx, x, y, z, rx, ry, rz, ux, uy, uz, s, p, alpha, burst, line);
            }
         }
      }
   }

   private void stars(
      HitFx fx,
      double x,
      double y,
      double z,
      double rx,
      double ry,
      double rz,
      double ux,
      double uy,
      double uz,
      double s,
      float p,
      float alpha,
      int fillColor,
      int lineColor
   ) {
      float t = Easing.clamp01((p - 0.14545454F) / 0.8545455F);
      if (!(t <= 0.0F)) {
         for (int k = 0; k < 2; k++) {
            double side = k == 0 ? -1.0 : 1.0;
            double d = s * (0.5 + 0.45 * Easing.outCubic(t));
            double up = s * (0.3 + 0.45 * t - 0.6 * t * t);
            double px = x + rx * side * d + ux * up;
            double py = y + ry * side * d + uy * up;
            double pz = z + rz * side * d + uz * up;
            float spinA = (float)(side * t * 9.0);
            double size = 0.2 * s * (1.0 - 0.4 * t);
            float a = alpha * (1.0F - Easing.smoothstep(0.6F, 1.0F, t));
            Vfx.sprite(Textures.STAR, px, py, pz, size * 1.28, spinA, ColorUtil.mulAlpha(lineColor, a));
            Vfx.sprite(Textures.STAR, px, py, pz, size, spinA, ColorUtil.mulAlpha(fillColor, a));
         }
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      float p = fx.progress();
      String phase = p < 0.14545454F
         ? "popping in"
         : (p < 0.25454545F ? "the word slams in" : (p < 0.5272727F ? "shaking" : (p < 0.7090909F ? "holding" : "popping away")));
      Comic.Word w = fx.data instanceof String ? Comic.word((String)fx.data) : null;
      return String.format(
         Locale.ROOT,
         "%s: \"%s\" (%s), %s, halftone %s, %d word(s) to pick from",
         phase,
         fx.data,
         w == null ? "none" : (w.ready() ? "ready" : "painting"),
         this.shape.get().displayName(),
         Halftone.usable() ? "shader" : "printed",
         this.pool().size()
      );
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   public static enum Shape implements Displayable {
      SPIKY("Spiky", "A jagged starburst", 0),
      CLOUD("Cloud", "A puffy cloud", 1),
      ROUND("Round", "A round balloon", 2);

      private final String displayName;
      private final String description;
      final int kit;

      private Shape(String displayName, String description, int kit) {
         this.displayName = displayName;
         this.description = description;
         this.kit = kit;
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

   public static enum Word implements Displayable {
      POW("POW!"),
      BAM("BAM!"),
      WHAM("WHAM!"),
      SMACK("SMACK!"),
      BONK("BONK!");

      final String text;

      private Word(String text) {
         this.text = text;
      }

      @Override
      public String displayName() {
         return this.text;
      }

      @Override
      public String description() {
         return "";
      }
   }

   public static enum Words implements Displayable {
      RANDOM("Random from the list", "A word from the list each hit"),
      ONE("One word", "Always the list's first word");

      private final String displayName;
      private final String description;

      private Words(String displayName, String description) {
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
