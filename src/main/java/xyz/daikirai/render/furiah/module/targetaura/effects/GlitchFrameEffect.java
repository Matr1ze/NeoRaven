package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Glitch;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;

public final class GlitchFrameEffect extends AuraEffect {
   private static final String[] ERRORS = new String[]{"ERR", "0x7F", "NULL", "▓▒░"};
   private static final String[] BLOCKS = new String[]{"▓▒░", "░▒▓", "█▓▒", "▒▒░"};
   private static final String HEX_DIGITS = "0123456789ABCDEF";
   private static final int SLOTS = 4;
   private static final double LINE = 0.018;
   private static final double BEHIND = 0.06;
   private static final int STREAKS = 7;
   public final ThemeTint channelB;
   public final ThemeTint glyphColor;
   public final SliderSetting amount;
   public final SliderSetting rate;
   public final SliderSetting split;
   public final BooleanSetting scanlines;
   public final BooleanSetting pixelSort;
   public final BooleanSetting glyphs;
   public final ModeSetting<GlitchFrameEffect.GlyphSet> glyphSet;
   public final TextSetting text;
   public final ModeSetting<GlitchFrameEffect.Frame> frame;
   private final Glitch.Plane plane = new Glitch.Plane();
   private double hw;
   private double hh;
   private final Glitch.Bursts bursts = new Glitch.Bursts();
   private float burst;
   private float flash;
   private final StringBuilder hexText = new StringBuilder(4);

   public GlitchFrameEffect() {
      super(
         EffectType.GLITCH_FRAME,
         "glitchframe",
         new ThemeTint(),
         true
      );
      this.channelB = ThemeTint.detail(-16718337);
      this.glyphColor = ThemeTint.detail(-657931);
      this.amount = this.look(
         this.setting(new SliderSetting("glitchframe.amount", "Glitch Intensity", "How much a burst corrupts the frame.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.rate = this.look(
         this.setting(new SliderSetting("glitchframe.rate", "Glitch Rate", "Bursts a second (at most 2 with Reduce Flashing).", 0.5, 6.0, 2.0, 0.5).unit("/s"))
      );
      this.split = this.setting(
         new SliderSetting("glitchframe.split", "Split Distance", "How far the color channels split apart.", 0.0, 100.0, 50.0, 5.0).unit("%")
      );
      this.scanlines = this.setting(new BooleanSetting("glitchframe.scanlines", "Scanlines", "Bands of scanlines drifting down the frame.", true));
      this.pixelSort = this.setting(new BooleanSetting("glitchframe.pixelSort", "Pixel Sort", "Blocky streaks dripping down in a burst.", true));
      this.glyphs = this.setting(new BooleanSetting("glitchframe.glyphs", "Glyphs", "Error fragments flickering at the edges.", true));
      this.glyphSet = this.setting(
         new ModeSetting<>("glitchframe.glyphSet", "Glyph Set", "What the fragments say.", GlitchFrameEffect.GlyphSet.class, GlitchFrameEffect.GlyphSet.ERRORS)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return GlitchFrameEffect.this.glyphs.get();
               }
            })
      );
      this.text = this.setting(
         new TextSetting("glitchframe.text", "Custom Text", "Your own fragment (up to 12 characters).", "GG", 12).visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return GlitchFrameEffect.this.glyphs.get() && GlitchFrameEffect.this.glyphSet.get() == GlitchFrameEffect.GlyphSet.CUSTOM;
            }
         })
      );
      this.frame = this.setting(
         new ModeSetting<>(
            "glitchframe.frame",
            "Frame",
            "Corner brackets, a whole rectangle, or no frame lines.",
            GlitchFrameEffect.Frame.class,
            GlitchFrameEffect.Frame.BRACKETS
         )
      );
   }

   private static boolean reduceFlashing() {
      return Glitch.reduceFlashing();
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      float alpha = this.intensity(c);
      if (!(alpha <= 0.003F)) {
         if (pass == FxPass.TRANSLUCENT) {
            this.layout(c);
            this.draw(c, alpha);
         } else if (pass == FxPass.GLOW) {
            this.layout(c);
            this.dot(c, alpha);
         }
      }
   }

   private void layout(EffectContext c) {
      double s = this.size(c);
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      double top = c.y + c.height + 0.2 * s;
      double bottom = c.y + 0.06;
      double cy = (top + bottom) * 0.5;
      this.hw = (c.width * 0.5 + 0.28) * s;
      this.hh = (top - bottom) * 0.5;
      this.hh = Math.min(this.hh, (cy - c.groundY - 0.05) / Math.max(0.25, cam.upY));
      this.plane
         .set(c.x + cam.forwardX * 0.06, cy + cam.forwardY * 0.06, c.z + cam.forwardZ * 0.06, cam.rightX, cam.rightY, cam.rightZ, cam.upX, cam.upY, cam.upZ);
      double t = c.time;
      boolean reduce = reduceFlashing();
      this.flash = 0.0F;
      this.burst = this.bursts.at(t, this.rate.getFloat() * this.speed(), c.seed, reduce);
      float intro;
      if (t < 0.24 && (intro = 1.0F - Easing.smoothstep(0.16F, 0.24F, (float)t)) > this.burst) {
         this.burst = intro;
         this.bursts.seed = c.seed;
         this.bursts.since = t;
      }

      if (!reduce && this.burst > 0.0F && this.bursts.since < 0.035) {
         this.flash = (float)(1.0 - this.bursts.since / 0.035);
      }

      this.burst = this.burst * (this.amount.getFloat() / 100.0F);
   }

   public String burstsForTest() {
      return new Glitch.Bursts().countForTest(this.rate.getFloat() * this.speed(), reduceFlashing());
   }

   private void rect(double u0, double v0, double u1, double v1, int col) {
      this.plane.rect(u0, v0, u1, v1, col);
   }

   private void splitRect(double u0, double v0, double u1, double v1, double d, int a, int b, int core) {
      this.plane.splitRect(u0, v0, u1, v1, d, a, b, core);
   }

   private void draw(EffectContext c, float alpha) {
      double t = c.time;
      boolean reduce = reduceFlashing();
      float open = Easing.outExpo(Easing.clamp01((float)(t / 0.18)));
      float out = c.outro;
      float squash = Glitch.crtHeight(out);
      float narrow = Glitch.crtWidth(out);
      double h = this.hh * Math.max(0.0, (double)(open * squash));
      double w = this.hw * narrow;
      float inside = alpha * Easing.clamp01(squash * 1.5F - 0.2F);
      int ca = this.color(c, 0.5F);
      int cb = this.channelB.getColor(0.5F);
      int cg = this.glyphColor.getColor(0.5F);
      double d = (0.004 + 0.05 * this.split.getFloat() / 100.0) * (0.35 + 0.65 * this.burst) * this.size(c);
      float peak = reduce ? 0.6F : 0.92F;
      float introLine = (float)Math.max(0.0, 1.0 - t / 0.14) * (reduce ? 0.55F : 1.0F);
      if (introLine > 0.0F || out > 0.55F) {
         double lh = 0.013499999999999998;
         float la = out > 0.55F ? alpha * (1.0F - Easing.clamp01((out - 0.85F) / 0.15F)) : alpha * introLine;
         this.splitRect(-w, -lh, w, lh, d * 0.5, alphaOf(ca, la * 0.7F), alphaOf(cb, la * 0.7F), alphaOf(cg, la));
         if (out > 0.55F) {
            return;
         }
      }

      if (!(h <= 0.018)) {
         if (this.flash > 0.0F) {
            this.rect(-w, -h, w, h, alphaOf(cg, inside * 0.16F * this.flash));
         }

         if (this.scanlines.get()) {
            Glitch.scanlines(this.plane, t, this.speed(), w, h, inside, this.burst, cg, ca, cb);
         }

         this.frameLines(w, h, d, inside, ca, cb, cg, peak);
         if (this.burst > 0.01F) {
            Glitch.blocks(this.plane, this.bursts.seed, this.burst, w, h, d, inside, ca, cb, cg, peak);
         }

         if (this.pixelSort.get()) {
            float k = Math.max(this.burst, (float)Math.max(0.0, 1.0 - this.bursts.since / 0.7) * 0.7F * this.amount.getFloat() / 100.0F);
            Glitch.streaks(this.plane, this.bursts.seed, k, 7, w, h, h, inside, ca, cb, cg);
         }

         if (this.glyphs.get()) {
            this.fragments(c, t, w, h, d, inside, ca, cb, cg, reduce);
         }
      }
   }

   private void frameLines(double w, double h, double d, float a, int ca, int cb, int cg, float peak) {
      GlitchFrameEffect.Frame f = this.frame.get();
      if (f != GlitchFrameEffect.Frame.NONE) {
         double th = 0.009;
         int A = alphaOf(ca, a * 0.8F);
         int B = alphaOf(cb, a * 0.8F);
         int G = alphaOf(cg, a * peak);
         boolean brackets = f == GlitchFrameEffect.Frame.BRACKETS;
         double arm = Math.min(w, h) * 0.38;

         for (int side = 0; side < 4; side++) {
            boolean horizontal = side < 2;
            double sign = (side & 1) == 0 ? 1.0 : -1.0;
            double len = horizontal ? 2.0 * w : 2.0 * h;
            int pieces = brackets ? 2 : 4;

            for (int p = 0; p < pieces; p++) {
               double s1;
               double s0;
               if (brackets) {
                  s0 = p == 0 ? -len / 2.0 : len / 2.0 - arm;
                  s1 = p == 0 ? -len / 2.0 + arm : len / 2.0;
               } else {
                  s0 = -len / 2.0 + len * p / pieces;
                  s1 = -len / 2.0 + len * (p + 1) / pieces;
               }

               double jump = 0.0;
               if (this.burst > 0.01F && hash(this.bursts.seed + side * 7, p) < 0.5) {
                  jump = (hash(this.bursts.seed + side * 7, p + 11) - 0.5) * 0.55 * this.burst;
               }

               if (horizontal) {
                  double v = sign * h;
                  this.splitRect(s0 + jump, v - th, s1 + jump, v + th, d, A, B, G);
               } else {
                  double u = sign * w;
                  this.splitRect(u - th + jump, s0, u + th + jump, s1, d, A, B, G);
               }
            }
         }
      }
   }

   private void fragments(EffectContext c, double t, double w, double h, double d, float a, int ca, int cb, int cg, boolean reduce) {
      int tex = Glitch.atlas();
      double gh = 0.14 * this.size(c);
      GlitchFrameEffect.GlyphSet set = this.glyphSet.get();

      for (int slot = 0; slot < 4; slot++) {
         double period = 0.35 + 0.3 * hash(slot, 3);
         long tick = (long)Math.floor((t * this.speed() + hash(slot, 5) * period) / period);
         float on;
         if (this.burst > 0.2F) {
            on = 1.0F;
         } else if (reduce) {
            on = 0.35F + 0.35F * (float)(0.5 + 0.5 * Math.sin(t * 2.2 + slot * 1.7));
         } else {
            on = hash((int)tick, slot + 31) < 0.6F ? 1.0F : 0.0F;
         }

         if (!(on <= 0.0F)) {
            CharSequence s;
            switch (set) {
               case HEX:
                  s = this.hexBytes((int)tick * 7 + slot);
                  break;
               case BLOCKS:
                  s = BLOCKS[(int)((tick + slot) % BLOCKS.length)];
                  break;
               case CUSTOM:
                  s = this.text.get();
                  break;
               default:
                  s = ERRORS[(int)((tick + slot) % ERRORS.length)];
            }

            if (s != null && s.length() != 0) {
               double width = Glitch.textWidth(s) * gh;
               double v;
               double startU;
               switch (slot) {
                  case 0:
                     startU = -w;
                     v = h + gh * 0.45;
                     break;
                  case 1:
                     startU = w + 0.05;
                     v = h * 0.55;
                     break;
                  case 2:
                     startU = -w - 0.05 - width;
                     v = -h * 0.35;
                     break;
                  default:
                     startU = w + 0.05;
                     v = -h + gh * 0.3;
               }

               double jitter = this.burst > 0.2F ? (hash(this.bursts.seed + slot, 17) - 0.5) * 0.08 : 0.0;
               float ga = a * on;
               this.plane.splitText(tex, s, startU + jitter, v, gh, d, alphaOf(ca, ga * 0.7F), alphaOf(cb, ga * 0.7F), alphaOf(cg, ga * 0.95F));
            }
         }
      }
   }

   private CharSequence hexBytes(int seed) {
      this.hexText.setLength(0);
      this.hexText
         .append('0')
         .append('x')
         .append("0123456789ABCDEF".charAt((int)(hash(seed, 1) * 16.0F)))
         .append("0123456789ABCDEF".charAt((int)(hash(seed, 2) * 16.0F)));
      return this.hexText;
   }

   private void dot(EffectContext c, float alpha) {
      float out = c.outro;
      int cg = this.glyphColor.getColor(0.5F);
      if (out > 0.75F) {
         float k = this.intensity.getFloat() / 100.0F * Glitch.crtDot(out);
         Vfx.sprite(Textures.SPARK, this.plane.ox, this.plane.oy, this.plane.oz, 0.12F * this.size(c), 0.0F, core(cg, k));
         Vfx.sprite(Textures.GLOW, this.plane.ox, this.plane.oy, this.plane.oz, 0.3F * this.size(c), 0.0F, alpha(cg, 0.35F * k));
      } else if (c.time < 0.12 && !reduceFlashing()) {
         float k = 1.0F - Easing.clamp01((float)(c.time / 0.12));
         Vfx.sprite(Textures.GLOW, this.plane.ox, this.plane.oy, this.plane.oz, (float)(this.hw * 2.4), 0.0F, alpha(cg, 0.25F * alpha * k));
      }
   }

   private static int alphaOf(int color, float a) {
      return Glitch.alphaOf(color, a);
   }

   public static enum Frame implements Displayable {
      BRACKETS("Brackets", "Four corner brackets"),
      FULL("Full frame", "A whole rectangle"),
      NONE("None", "No frame lines");

      private final String displayName;
      private final String description;

      private Frame(String displayName, String description) {
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

   public static enum GlyphSet implements Displayable {
      ERRORS("Errors", "ERR, 0x7F, NULL"),
      HEX("Hex", "Hex bytes"),
      BLOCKS("Blocks", "Shade blocks"),
      CUSTOM("Custom text", "Your own text (up to 12 characters)");

      private final String displayName;
      private final String description;

      private GlyphSet(String displayName, String description) {
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
