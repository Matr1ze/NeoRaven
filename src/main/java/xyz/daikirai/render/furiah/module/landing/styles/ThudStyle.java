package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Comic;
import xyz.daikirai.render.furiah.fx.kit.Halftone;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.EnumSetSetting;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;

public final class ThudStyle extends LandingStyleBase {
   private static final float SPLAT = 0.1F;
   private static final float WORD = 0.25F;
   private static final float HOLD = 0.65F;
   private static final float END = 0.9F;
   private static final int MAX_CUSTOM = 8;
   private static final int MAX_WORD = 8;
   private static final int LINES = 9;
   private static final int LINE_POINTS = 3;
   private static final double SPLAT_W = 1.7;
   private static final double WORD_H = 0.42;
   private static final double BESIDE = 0.95;
   private static final double AHEAD = 2.2;
   private static final String BIG_WORD = "BOOM!";
   private static final String JUMP_WORD = "HOP!";
   public final ThemeTint lettering;
   public final ThemeTint outline;
   public final ModeSetting<ThudStyle.Words> words;
   public final EnumSetSetting<ThudStyle.Word> wordList;
   public final TextSetting custom;
   public final BooleanSetting wordInFirstPerson;
   public final BooleanSetting halftone;
   public final BooleanSetting puffs;
   public final SliderSetting size;
   private final List<String> pool = new ArrayList<>();
   private int poolVersion = -1;
   private String poolCustom = null;
   private final float[] uv = new float[4];
   private final double[] ws = new double[2];

   public ThudStyle() {
      super(
         LandingStyle.THUD,
         0.9F,
         2.0F,
         new ThemeTint()
      );
      this.lettering = ThemeTint.detail(-1689274);
      this.outline = ThemeTint.detail(-15658735);
      this.words = this.setting(
         new ModeSetting<>(
            "thud.words", "Words", "A word from the list each landing, or always the list's first.", ThudStyle.Words.class, ThudStyle.Words.RANDOM
         )
      );
      this.wordList = this.setting(
         new EnumSetSetting<>(
            "thud.wordList", "Word List", "The words it says.", ThudStyle.Word.class, false, new ThudStyle.Word[]{ThudStyle.Word.THUD, ThudStyle.Word.WHUMP}
         )
      );
      this.custom = this.setting(
         new TextSetting("thud.custom", "Custom Words", "Your own words for the list, separated by commas, up to 8 characters each.", "", 72)
      );
      this.wordInFirstPerson = this.setting(
         new BooleanSetting("thud.wordFirstPerson", "Word In First Person", "Show the word for your own landings while you look through your own eyes.", false)
      );
      this.halftone = this.setting(new BooleanSetting("thud.halftone", "Halftone", "Printed halftone dots in the splat's shade.", true));
      this.puffs = this.setting(new BooleanSetting("thud.puffs", "Puffs", "Two comic puffs pop out to the sides.", true));
      this.size = this.look(this.setting(new SliderSetting("thud.size", "Size", "How big the splat and the word are.", 50.0, 150.0, 100.0, 5.0).unit("%")));
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit();
   }

   private List<String> pool() {
      String c = this.custom.get();
      if (this.poolVersion != this.wordList.version() || !c.equals(this.poolCustom)) {
         this.poolVersion = this.wordList.version();
         this.poolCustom = c;
         this.pool.clear();

         for (ThudStyle.Word w : ThudStyle.Word.values()) {
            if (this.wordList.includes(w)) {
               this.pool.add(w.text);
            }
         }

         for (String part : c.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && this.pool.size() < ThudStyle.Word.values().length + 8) {
               this.pool.add(t.length() > 8 ? t.substring(0, 8) : t);
            }
         }

         if (this.pool.isEmpty()) {
            this.pool.add(ThudStyle.Word.THUD.text);
         }

         for (String wx : this.pool) {
            Comic.word(wx);
         }
      }

      return this.pool;
   }

   @Override
   public void start(LandingFx fx) {
      ThudStyle.State s = new ThudStyle.State();
      s.jump = fx.land.jump;
      s.scale = this.size.getFloat() / 100.0F * fx.size;
      s.turn = fx.rand(1) * (Math.PI * 2);
      s.variant = (int)(fx.rand(2) * 4.0F);
      s.seed = fx.seed;
      if (s.jump) {
         s.word = "HOP!";
      } else {
         List<String> list = this.pool();
         boolean big = this.words.get() == ThudStyle.Words.RANDOM && fx.land.fallHeight >= 5.0F;
         s.word = big ? "BOOM!" : (this.words.get() == ThudStyle.Words.ONE ? list.get(0) : list.get((int)(fx.rand(3) * list.size()) % list.size()));
      }

      Comic.word(s.word);
      Minecraft mc = Minecraft.getMinecraft();
      boolean firstPerson = fx.land.self && mc.gameSettings.thirdPersonView == 0 && !fx.preview;
      s.showWord = !firstPerson || this.wordInFirstPerson.get();
      Entity eye = mc.getRenderViewEntity();
      if (firstPerson && s.showWord && eye != null) {
         Vec3 look = eye.getLook(1.0F);
         double flat = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
         double rx = flat > 0.001 ? -look.zCoord / flat : 1.0;
         double rz = flat > 0.001 ? look.xCoord / flat : 0.0;
         s.ahead = true;
         s.wx = fx.x() + look.xCoord * 2.2 + rx * 0.45;
         s.wz = fx.z() + look.zCoord * 2.2 + rz * 0.45;
         s.wy = Math.max(fx.y() + 0.65, fx.y() + eye.getEyeHeight() + look.yCoord * 2.2 + 0.1);
      }

      fx.data = s;
   }

   private static double splat(LandingFx fx) {
      float t = fx.styleTime();
      return Easing.outBack(Easing.clamp01(t / 0.1F), 1.6F) * (1.0 - Easing.inCubic(Easing.clamp01((t - 0.65F) / 0.25F)));
   }

   private static void wordScale(LandingFx fx, double[] out) {
      float t = fx.styleTime();
      if (t < 0.1F) {
         out[1] = 0.0;
         out[0] = 0.0;
      } else {
         float in = Easing.clamp01((t - 0.1F) / 0.15F);
         if (t < 0.25F) {
            double e = Easing.outBack(in, 2.4F);
            out[0] = e * (1.0 - 0.25 * Math.sin(Math.PI * in));
            out[1] = e * (1.0 + 0.3 * Math.sin(Math.PI * in));
         } else if (t < 0.65F) {
            double bob = 0.04 * Math.sin((t - 0.25F) * 14.0) * Math.exp(-(t - 0.25F) * 4.0);
            out[0] = 1.0 - bob;
            out[1] = 1.0 + bob;
         } else {
            float o = Easing.clamp01((t - 0.65F) / 0.25F);
            out[0] = (1.0 + 0.35 * Easing.outCubic(o)) * (1.0 - Easing.inCubic(o));
            out[1] = (1.0 - Easing.inCubic(o)) * (1.0 - 0.6 * o);
         }
      }
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      ThudStyle.State s = (ThudStyle.State)fx.data;
      if (s != null) {
         Camera cam = Vfx.camera();
         float alpha = fx.alpha();
         int burst = this.color.getColor(0.5F) | 0xFF000000;
         int line = this.outline.getColor(0.5F) | 0xFF000000;
         int dots = ColorUtil.shade(burst, 0.62F) | 0xFF000000;
         double k = splat(fx);
         RenderState.translucent(false);
         if (!s.jump && k > 0.01) {
            RenderState.decal(true);

            try {
               this.splatDecal(fx, f, s, k, burst, dots, line, alpha);
               this.lines(fx, f, s, k, line, alpha);
            } finally {
               RenderState.decal(false);
            }
         }

         if (this.puffs.get() && k > 0.01 && (!fx.lod || s.jump)) {
            this.puffPair(fx, f, s, cam, k, line, alpha);
         }

         if (s.showWord) {
            this.word(fx, f, s, cam, line, alpha);
         }

         RenderState.translucent(true);
      }
   }

   private void splatDecal(LandingFx fx, FxFrame f, ThudStyle.State s, double k, int fill, int dots, int line, float alpha) {
      Comic.balloonCell(0, s.variant, this.uv);
      double half = 0.85 * s.scale * k;
      int a = ColorUtil.mulAlpha(fill, alpha * 0.95F);
      if (Halftone.usable()) {
         Vfx.flush();
         float period = Math.max(3.0F, Minecraft.getMinecraft().displayHeight / 180.0F);
         Halftone.begin(period, this.halftone.get() ? dots : 0, 1.0F);

         try {
            Ground.decal(f, fx.ground, Comic.balloonShade(), this.uv[0], this.uv[1], this.uv[2], this.uv[3], fx.x(), fx.z(), half, s.turn, a, 6);
            Vfx.flush();
         } finally {
            Halftone.end();
         }
      } else {
         Ground.decal(
            f,
            fx.ground,
            this.halftone.get() ? Comic.balloonFill() : Comic.balloonShade(),
            this.uv[0],
            this.uv[1],
            this.uv[2],
            this.uv[3],
            fx.x(),
            fx.z(),
            half,
            s.turn,
            a,
            6
         );
      }

      Ground.decal(
         f, fx.ground, Comic.balloonOutline(), this.uv[0], this.uv[1], this.uv[2], this.uv[3], fx.x(), fx.z(), half, s.turn, ColorUtil.mulAlpha(line, alpha), 6
      );
   }

   private void lines(LandingFx fx, FxFrame f, ThudStyle.State s, double k, int line, float alpha) {
      double inner = 0.935 * s.scale * Math.min(1.0, k);
      double outer = 1.7 * (0.8 + 0.55 * k) * s.scale;
      int col = ColorUtil.mulAlpha(line, alpha);

      for (int i = 0; i < 9; i++) {
         double a = s.turn + (Math.PI * 2) * (i + 0.5 * fx.rand(50 + i)) / 9.0;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double r0 = inner * (0.95 + 0.2 * fx.rand(70 + i));
         double r1 = outer * (0.8 + 0.3 * fx.rand(90 + i));
         double w = 0.07 * s.scale * (0.7 + 0.6 * fx.rand(110 + i));
         int n = Ground.linePoints(f, fx.ground, 0, fx.x() + ca * r0, fx.z() + sa * r0, fx.x() + ca * r1, fx.z() + sa * r1, w, col, col, 2);

         for (int j = 0; j < n; j++) {
            Vfx.W[j] = (float)(w * (j == 0 ? 0.05 : (double)j / (n - 1)));
         }

         Vfx.flatRibbon(Textures.LINE_CORE, 0, n);
      }
   }

   private void puffPair(LandingFx fx, FxFrame f, ThudStyle.State s, Camera cam, double k, int line, float alpha) {
      double side = (s.jump ? 0.5 : 0.95) * s.scale * Math.min(1.0, k);
      double w = (s.jump ? 0.5 : 0.75) * s.scale * k;
      double h = w * 0.62;

      for (int d = -1; d <= 1; d += 2) {
         double x = fx.x() + cam.rightX * side * d;
         double z = fx.z() + cam.rightZ * side * d;
         double y = Ground.y(fx.ground, x, z) + h * 0.42;
         double rx = f.rx(x);
         double ry = f.ry(y);
         double rz = f.rz(z);
         float a = alpha * NearCamera.fade(cam, rx, ry, rz);
         Comic.puff(
            s.seed + d,
            rx,
            ry,
            rz,
            cam.rightX,
            cam.rightY,
            cam.rightZ,
            cam.upX,
            cam.upY,
            cam.upZ,
            w,
            h,
            d * 0.15,
            ColorUtil.mulAlpha(-1, a),
            ColorUtil.mulAlpha(line, a),
            1.0F
         );
      }
   }

   private void word(LandingFx fx, FxFrame f, ThudStyle.State s, Camera cam, int line, float alpha) {
      Comic.Word w = Comic.word(s.word);
      wordScale(fx, this.ws);
      if (w.ready() && !(this.ws[0] <= 0.01) && !(this.ws[1] <= 0.01)) {
         double h = 0.42 * s.scale * (s.jump ? 0.6 : 1.0);
         double x = fx.x() + cam.rightX * 0.95 * s.scale;
         double z = fx.z() + cam.rightZ * 0.95 * s.scale;
         double y = fx.y() + (s.jump ? 0.7 : 1.35) * Math.sqrt(s.scale);
         if (s.ahead) {
            x = s.wx;
            y = s.wy;
            z = s.wz;
         }

         double rz;
         double ry;
         double rx;
         float a;
         if (!((a = alpha * NearCamera.fade(cam, rx = f.rx(x), ry = f.ry(y), rz = f.rz(z))) <= 0.003F)) {
            int letters = ColorUtil.mulAlpha(this.lettering.getColor(0.5F) | 0xFF000000, a);
            double tilt = -0.12;
            double sx = this.ws[0];
            double sy = this.ws[1];
            Comic.lettering(
               w,
               rx,
               ry,
               rz,
               cam.rightX * sx,
               cam.rightY * sx,
               cam.rightZ * sx,
               cam.upX * sy,
               cam.upY * sy,
               cam.upZ * sy,
               h,
               tilt,
               letters,
               ColorUtil.mulAlpha(line, a),
               1.2F
            );
         }
      }
   }

   private static final class State {
      String word;
      boolean jump;
      boolean showWord;
      double scale;
      double turn;
      int variant;
      int seed;
      boolean ahead;
      double wx;
      double wy;
      double wz;

      private State() {
      }
   }

   public static enum Word implements Displayable {
      THUD("THUD!"),
      WHUMP("WHUMP!");

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
         return "Says \"" + this.text + "\"";
      }
   }

   public static enum Words implements Displayable {
      RANDOM("Random from the list", "A word from the list each landing"),
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
