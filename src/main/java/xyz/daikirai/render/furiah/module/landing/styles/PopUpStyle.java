package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Paper;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class PopUpStyle extends LandingStyleBase {
   private static final float RIPPLE = 0.2F;
   private static final float POP = 0.15F;
   private static final float WOBBLE_TO = 0.85F;
   private static final float FOLD = 0.3F;
   private static final float FOLDED = 1.25F;
   private static final float END = 1.6F;
   private static final float JUMP_END = 0.6F;
   private static final int MAX_CUTOUTS = 39;
   private static final double HEIGHT = 0.62;
   private static final double THICK = 0.008;
   private static final double EDGE = 1.07;
   private static final double LX = 0.35;
   private static final double LY = 0.86;
   private static final double LZ = 0.37;
   public final ModeSetting<PopUpStyle.PaperColors> paperColors;
   public final SliderSetting cutouts;
   public final ModeSetting<PopUpStyle.Shapes> shapes;
   public final SliderSetting radius;
   public final SliderSetting wobble;
   public final BooleanSetting texture;
   private final float[] hsb = new float[3];

   public PopUpStyle() {
      super(
         LandingStyle.POP_UP,
         1.6F,
         4.9F,
         new ThemeTint()
      );
      this.paperColors = this.setting(
         new ModeSetting<>(
               "popup.paperColors",
               "Paper Colors",
               "A pastel each, the Paper color, or its hue turning round the ring.",
               PopUpStyle.PaperColors.class,
               PopUpStyle.PaperColors.PASTEL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.cutouts = this.look(this.setting(new SliderSetting("popup.cutouts", "Cutouts", "How many cutouts stand in the ring.", 6.0, 16.0, 10.0, 1.0)));
      this.shapes = this.setting(
         new ModeSetting<>("popup.shapes", "Shapes", "Which cutouts pop up.", PopUpStyle.Shapes.class, PopUpStyle.Shapes.MIXED).style(ModeSetting.Style.CHIPS)
      );
      this.radius = this.look(
         this.setting(new SliderSetting("popup.radius", "Ring Radius", "How far round the feet the ring stands.", 0.8, 3.0, 1.5, 0.1).unit(" blocks"))
      );
      this.wobble = this.setting(new SliderSetting("popup.wobble", "Wobble", "How much they wobble on their hinges.", 0.0, 100.0, 50.0, 5.0).unit("%"));
      this.texture = this.setting(new BooleanSetting("popup.texture", "Paper Texture", "The paper's fibers printed on the cutouts.", true));
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   public void start(LandingFx fx) {
      PopUpStyle.State s = new PopUpStyle.State();
      s.jump = fx.land.jump;
      double r = this.radius.getFloat() * Math.sqrt(fx.size) * (s.jump ? 0.5 : 1.0);
      double h = 0.62 * fx.size * (s.jump ? 0.7 : 1.0);
      int inner = s.jump ? 6 : this.cutouts.getInt();
      int outer = !s.jump && fx.land.fallHeight >= 4.0F ? Math.round(inner * 1.4F) : 0;
      s.inner = inner;
      s.n = Math.min(39, inner + outer);
      double turn = fx.rand(1) * (Math.PI * 2);
      int base = this.color.getColor(0.5F);

      for (int i = 0; i < s.n; i++) {
         boolean second = i >= inner;
         int k = second ? i - inner : i;
         int count = second ? outer : inner;
         s.angle[i] = turn + (Math.PI * 2) * (k + (second ? 0.5 : 0.0)) / count;
         s.ring[i] = second ? r * 1.6 : r;
         s.height[i] = h * (second ? 1.15 : 1.0) * (0.9 + 0.2 * fx.rand(10 + i));
         s.delay[i] = (second ? 0.16000001F : 0.0F) + 0.120000005F * k / Math.max(1, count);
         s.phase[i] = fx.rand(30 + i) * 6.28F;
         s.kind[i] = this.kind(i);
         s.color[i] = this.cutoutColor(i, count, k, base);
      }

      fx.data = s;
   }

   private int kind(int i) {
      switch ((PopUpStyle.Shapes)this.shapes.get()) {
         case TREES:
            return 0;
         case FLOWERS:
            return 1;
         case STARS:
            return 2;
         case HEARTS:
            return 3;
         default:
            return i % 4;
      }
   }

   private int cutoutColor(int i, int count, int k, int base) {
      switch ((PopUpStyle.PaperColors)this.paperColors.get()) {
         case ONE:
            return base | 0xFF000000;
         case GRADIENT:
            return Paper.hueTurn(base, (float)k / Math.max(1, count), this.hsb) | 0xFF000000;
         default:
            return Paper.PASTEL_SET[i % Paper.PASTEL_SET.length];
      }
   }

   private double stand(LandingFx fx, PopUpStyle.State s, int i) {
      float t = fx.styleTime() - s.delay[i];
      if (t <= 0.0F) {
         return 0.0;
      } else if (s.jump) {
         return Math.sin(Math.PI * Easing.clamp01(t / 0.45000002F)) * 1.05;
      } else {
         double up = Easing.outBack(Easing.clamp01(t / 0.15F), 2.2F);
         double w = this.wobble.getFloat() / 100.0;
         double since = t - 0.15F;
         double sway = since > 0.0 ? w * 0.22 * Math.sin(since * 19.0 + s.phase[i]) * Math.exp(-since * 3.2) : 0.0;
         double down = Easing.inOutCubic(Easing.clamp01((t - (0.85F - s.delay[i] * 0.0F)) / 0.3F));
         return (up + sway) * (1.0 - down);
      }
   }

   private static float fade(LandingFx fx, PopUpStyle.State s) {
      return s.jump ? 1.0F - Easing.smoothstep(0.5F, 0.6F, fx.styleTime()) : 1.0F - Easing.smoothstep(1.25F, 1.6F, fx.styleTime());
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      PopUpStyle.State s = (PopUpStyle.State)fx.data;
      if (s != null) {
         float alpha = fx.alpha() * fade(fx, s);
         if (!(alpha <= 0.003F)) {
            Camera cam = Vfx.camera();
            int atlas = Paper.cutouts(this.texture.get());
            float light = Math.max(0.35F, fx.light);
            RenderState.translucent(true);

            for (int i = 0; i < s.n; i++) {
               double stand = this.stand(fx, s, i);
               double ca = Math.cos(s.angle[i]);
               double sa = Math.sin(s.angle[i]);
               double x = fx.x() + ca * s.ring[i];
               double z = fx.z() + sa * s.ring[i];
               double gy = Ground.y(fx.ground, x, z);
               if (Ground.lastValid()) {
                  double fx0 = -ca;
                  double fz0 = -sa;
                  double h;
                  double w = h = s.height[i];
                  double rx = f.rx(x);
                  double rz;
                  double ry;
                  float a = alpha * NearCamera.fade(cam, rx, (ry = f.ry(gy + 0.004)) + h * 0.5, rz = f.rz(z));
                  if (!(a <= 0.003F)) {
                     double lean = (1.0 - Easing.clamp01((float)stand)) * Math.PI * 0.5;
                     double nx = fx0 * Math.cos(lean);
                     double ny = Math.sin(lean);
                     double nz = fz0 * Math.cos(lean);
                     float front = (float)(0.8 + 0.2 * Math.max(0.0, nx * 0.35 + ny * 0.86 + nz * 0.37));
                     float back = (float)(0.8 + 0.2 * Math.max(0.0, -nx * 0.35 - ny * 0.86 - nz * 0.37));
                     int col = s.color[i];
                     int faceFront = ColorUtil.mulAlpha(ColorUtil.shade(col, front * light) | 0xFF000000, a);
                     if (fx.lod) {
                        Paper.popup(atlas, s.kind[i], rx, ry, rz, fx0, fz0, w, h, stand, faceFront);
                     } else {
                        int faceBack = ColorUtil.mulAlpha(ColorUtil.shade(col, back * light) | 0xFF000000, a);
                        int edge = ColorUtil.mulAlpha(ColorUtil.shade(-1, Math.max(front, back) * light) | 0xFF000000, a);
                        double ox = nx * 0.008;
                        double oy = ny * 0.008;
                        double oz = nz * 0.008;
                        Paper.popup(atlas, s.kind[i], rx - ox, ry - oy, rz - oz, fx0, fz0, w, h, stand, faceBack);
                        Paper.popup(atlas, s.kind[i], rx - ox * 0.5, ry - oy * 0.5, rz - oz * 0.5, fx0, fz0, w * 1.07, h * 1.07, stand, edge);
                        Paper.popup(atlas, s.kind[i], rx, ry, rz, fx0, fz0, w, h, stand, faceFront);
                     }
                  }
               }
            }

            RenderState.translucent(false);
         }
      }
   }

   public static enum PaperColors implements Displayable {
      PASTEL("Pastel set", "Each cutout a different pastel"),
      ONE("One color", "All in the Paper color"),
      GRADIENT("Gradient around the ring", "The Paper color, its hue turning round the ring");

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
      MIXED("Mixed", "Trees, flowers, stars and hearts in turn"),
      TREES("Trees", "Little fir trees"),
      FLOWERS("Flowers", "Little flowers"),
      STARS("Stars", "Stars"),
      HEARTS("Hearts", "Hearts");

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
      boolean jump;
      int n;
      int inner;
      final double[] angle = new double[39];
      final double[] ring = new double[39];
      final double[] height = new double[39];
      final float[] delay = new float[39];
      final float[] phase = new float[39];
      final int[] kind = new int[39];
      final int[] color = new int[39];

      private State() {
      }
   }
}
