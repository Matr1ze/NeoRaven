package xyz.daikirai.render.furiah.module.hit.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Glitch;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.PoseSnapshot;
import xyz.daikirai.render.furiah.render.Silhouette;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;

public final class CorruptStyle extends HitStyleBase {
   private static final String[] ERRORS = new String[]{"ERR", "NULL", "404", "FAULT", "NaN", "0xDEAD"};
   private static final String[] BLOCKS = new String[]{"█▓▒", "▒░▓", "▓█░"};
   private static final String HEX_DIGITS = "0123456789ABCDEF";
   private static final double BURST = 0.15;
   private static final double SECOND = 0.08;
   private static final double SWEEP_FROM = 0.15;
   private static final double SWEEP_TO = 0.25;
   private static final double RENOISE = 0.04;
   private static final long MIN_GAP = 500000000L;
   private static final int TARGETS = 16;
   public final ThemeTint channelB;
   public final ThemeTint glyphColor;
   public final SliderSetting intensity;
   public final SliderSetting split;
   public final SliderSetting slices;
   public final BooleanSetting pixelSort;
   public final BooleanSetting glyphs;
   public final ModeSetting<CorruptStyle.GlyphSet> glyphSet;
   public final TextSetting text;
   private final Silhouette.Look look = new Silhouette.Look();
   private final Glitch.Plane plane = new Glitch.Plane();
   private final StringBuilder hexText = new StringBuilder(6);
   private final int[] lastTarget = new int[16];
   private final long[] lastBurst = new long[16];
   private int lastNext;
   private final double[] dir = new double[2];

   public CorruptStyle() {
      super(
         HitStyle.CORRUPT,
         0.35F,
         new ThemeTint()
      );
      this.channelB = ThemeTint.detail(-16718337);
      this.glyphColor = ThemeTint.detail(-657931);
      this.intensity = this.look(
         this.setting(new SliderSetting("corrupt.intensity", "Intensity", "How strong the glitch is.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.split = this.look(
         this.setting(new SliderSetting("corrupt.split", "Split Distance", "How far apart the two channels split.", 0.0, 100.0, 50.0, 5.0).unit("%"))
      );
      this.slices = this.setting(new SliderSetting("corrupt.slices", "Slices", "Bands of the body that jump sideways.", 0.0, 8.0, 4.0, 1.0));
      this.pixelSort = this.setting(new BooleanSetting("corrupt.pixelSort", "Pixel Sort", "Blocky streaks dripping down from the hit.", true));
      this.glyphs = this.setting(new BooleanSetting("corrupt.glyphsOn", "Glyphs", "An error glyph pops up beside the target.", true));
      this.glyphSet = this.setting(
         new ModeSetting<>("corrupt.glyphSet", "Glyph Set", "What the glyph says.", CorruptStyle.GlyphSet.class, CorruptStyle.GlyphSet.ERRORS)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return CorruptStyle.this.glyphs.get();
               }
            })
      );
      this.text = this.setting(
         new TextSetting("corrupt.text", "Custom Text", "The glyph's text with Custom text.", "SYSTEM ERROR", 12).visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return CorruptStyle.this.glyphs.get() && CorruptStyle.this.glyphSet.get() == CorruptStyle.GlyphSet.CUSTOM;
            }
         })
      );
   }

   @Override
   public void start(HitFx fx) {
      boolean calm = Glitch.reduceFlashing() && !fx.preview && fx.entityId != -1;
      boolean burst = true;
      if (calm) {
         long now = GameClock.nanos();

         for (int i = 0; i < 16; i++) {
            if (this.lastTarget[i] == fx.entityId && this.lastBurst[i] != 0L && now - this.lastBurst[i] < 500000000L) {
               burst = false;
               break;
            }
         }

         if (burst) {
            int slot = -1;

            for (int ix = 0; ix < 16; ix++) {
               if (this.lastTarget[ix] == fx.entityId && this.lastBurst[ix] != 0L) {
                  slot = ix;
                  break;
               }
            }

            if (slot < 0) {
               slot = this.lastNext;
               this.lastNext = (this.lastNext + 1) % 16;
            }

            this.lastTarget[slot] = fx.entityId;
            this.lastBurst[slot] = now;
         }
      }

      fx.data = burst ? Boolean.TRUE : Boolean.FALSE;
   }

   private float envelope(HitFx fx, double t) {
      if (fx.data != Boolean.TRUE) {
         return 0.0F;
      } else {
         float e = burstShape(t);
         if (fx.crit && !Glitch.reduceFlashing()) {
            e = Math.max(e, 1.35F * burstShape(t - 0.08));
         }

         return e * this.intensity.getFloat() / 100.0F;
      }
   }

   private static float burstShape(double t) {
      if (!(t < 0.0) && !(t > 0.15)) {
         float in = Easing.smoothstep(0.0F, 0.01F, (float)t);
         float out = 1.0F - Easing.smoothstep(0.11F, 0.15F, (float)t);
         float flick = 0.75F + 0.25F * Glitch.hash((int)Math.floor(t / 0.04), 7);
         return in * out * flick;
      } else {
         return 0.0F;
      }
   }

   private void screenDir(HitFx fx, Camera cam, double[] out) {
      double sx = fx.basis.fx * cam.rightX + fx.basis.fy * cam.rightY + fx.basis.fz * cam.rightZ;
      double sy = fx.basis.fx * cam.upX + fx.basis.fy * cam.upY + fx.basis.fz * cam.upZ;
      double l = Math.sqrt(sx * sx + sy * sy);
      if (l < 0.25) {
         sx = fx.hit.side >= 0 ? 1.0 : -1.0;
         sy = 0.0;
         l = 1.0;
      }

      out[0] = sx / l;
      out[1] = sy / l;
   }

   @Override
   public int passes(HitFx fx) {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      float alpha = fx.alpha();
      if (!(alpha <= 0.003F)) {
         double t = fx.styleTime();
         Camera cam = Vfx.camera();
         this.screenDir(fx, cam, this.dir);
         float env = this.envelope(fx, t);
         int seed = fx.seed + (int)Math.floor(t / 0.04) * 7919;
         if (pass == FxPass.GLOW) {
            this.channels(fx, f, cam, env, seed, alpha);
         } else {
            this.crisp(fx, f, cam, t, env, seed, alpha);
         }
      }
   }

   private void channels(HitFx fx, FxFrame f, Camera cam, float env, int seed, float alpha) {
      PoseSnapshot pose = fx.hit.pose;
      if (pose.valid && !(env <= 0.01F)) {
         double s = scale(fx);
         double d = (0.02 + 0.12 * this.split.getFloat() / 100.0) * s * (0.4 + 0.6 * env);
         double sx = (cam.rightX * this.dir[0] + cam.upX * this.dir[1]) * d;
         double sy = (cam.rightY * this.dir[0] + cam.upY * this.dir[1]) * d;
         double sz = (cam.rightZ * this.dir[0] + cam.upZ * this.dir[1]) * d;
         this.look.reset();
         this.look.fill = 0.32F;
         this.look.rim = 0.0F;
         this.look.inflate = 0.1F;
         double ex = f.rx(fx.ix()) - cam.posX;
         double ey = f.ry(fx.iy()) - cam.posY;
         double ez = f.rz(fx.iz()) - cam.posZ;
         this.look.pullDistance = Math.max(0.3, Math.sqrt(ex * ex + ey * ey + ez * ez) - 0.12);
         float soft = Glitch.reduceFlashing() ? 0.6F : 1.0F;
         Silhouette.drawGlitch(
            pose,
            f.viewX,
            f.viewY,
            f.viewZ,
            this.look,
            this.color.getColor(0.5F),
            this.channelB.getColor(0.5F),
            sx,
            sy,
            sz,
            this.slices.getInt(),
            seed,
            (float)(0.12 * s * env),
            alpha * soft * Math.min(1.0F, env * 1.3F),
            !f.preview
         );
      }
   }

   private void crisp(HitFx fx, FxFrame f, Camera cam, double t, float env, int seed, float alpha) {
      double s = scale(fx);
      double px = f.rx(fx.ix() - fx.basis.fx * 0.15);
      double py = f.ry(fx.iy() - fx.basis.fy * 0.15);
      double pz = f.rz(fx.iz() - fx.basis.fz * 0.15);
      this.plane.set(px, py, pz, cam.rightX, cam.rightY, cam.rightZ, cam.upX, cam.upY, cam.upZ);
      int ca = this.color.getColor(0.5F);
      int cb = this.channelB.getColor(0.5F);
      int cg = this.glyphColor.getColor(0.5F);
      double d = (0.004 + 0.04 * this.split.getFloat() / 100.0) * s * (0.35 + 0.65 * env);
      float peak = Glitch.reduceFlashing() ? 0.6F : 0.92F;
      double w = 0.32 * s;
      double h = 0.4 * s;
      if (env > 0.01F) {
         Glitch.blocks(this.plane, seed, env, w, h, d, alpha, ca, cb, cg, peak);
      }

      float fade = 1.0F - Easing.smoothstep(0.25F, fx.life / fx.timeScale, (float)t);
      if (this.pixelSort.get()) {
         float k = Math.max(env, 0.6F * fade * (float)Math.min(1.0, t / 0.05) * this.intensity.getFloat() / 100.0F);
         Glitch.streaks(this.plane, fx.seed, k, 6, w * 0.8, 0.0, 0.6 * s * Easing.clamp01((float)(t / 0.2)), alpha * fade, ca, cb, cg);
      }

      if (this.glyphs.get()) {
         this.glyph(fx, t, s, d, alpha * fade, ca, cb, cg);
      }

      if (t >= 0.15 && t <= 0.25) {
         float k = (float)((t - 0.15) / 0.1);
         double top = f.ry(fx.anchor.feetY() + fx.hit.height + 0.1) - py;
         double bottom = f.ry(fx.anchor.feetY()) - py;
         double v = top + (bottom - top) * k;
         double half = fx.hit.width * 0.7 + 0.1;
         float a = alpha * (float)Math.sin(Math.PI * k) * (Glitch.reduceFlashing() ? 0.5F : 0.9F);
         this.plane.splitRect(-half, v - 0.006 * s, half, v + 0.006 * s, d, Glitch.alphaOf(ca, a * 0.6F), Glitch.alphaOf(cb, a * 0.6F), Glitch.alphaOf(cg, a));
      }
   }

   private void glyph(HitFx fx, double t, double s, double d, float a, int ca, int cb, int cg) {
      if (!(a <= 0.003F) && !(t < 0.01)) {
         CharSequence str;
         if (fx.crit) {
            str = "CRIT";
         } else {
            switch ((CorruptStyle.GlyphSet)this.glyphSet.get()) {
               case HEX:
                  this.hexText.setLength(0);
                  this.hexText
                     .append('0')
                     .append('x')
                     .append("0123456789ABCDEF".charAt((int)(Glitch.hash(fx.seed, 1) * 16.0F)))
                     .append("0123456789ABCDEF".charAt((int)(Glitch.hash(fx.seed, 2) * 16.0F)));
                  str = this.hexText;
                  break;
               case BLOCKS:
                  str = BLOCKS[(int)(Glitch.hash(fx.seed, 3) * BLOCKS.length) % BLOCKS.length];
                  break;
               case CUSTOM:
                  str = this.text.get();
                  break;
               default:
                  str = ERRORS[(int)(Glitch.hash(fx.seed, 4) * ERRORS.length) % ERRORS.length];
            }
         }

         if (str != null && str.length() != 0) {
            double gh = 0.13 * s * (fx.crit ? 1.25 : 1.0);
            float pop = Easing.outBack(Easing.clamp01((float)(t / 0.05)), 2.5F);
            double width = Glitch.textWidth(str) * gh;
            double side = fx.hit.side >= 0 ? 1.0 : -1.0;
            double u = side > 0.0 ? fx.hit.width * 0.6 + 0.12 : -(fx.hit.width * 0.6 + 0.12) - width;
            double v = 0.18 * s;
            double jitter = this.envelope(fx, t) > 0.2F ? (Glitch.hash(fx.seed + (int)(t / 0.04), 17) - 0.5) * 0.06 : 0.0;
            float ga = a * Easing.clamp01(pop);
            this.plane
               .splitText(
                  Glitch.atlas(), str, u + jitter, v, gh * pop, d, Glitch.alphaOf(ca, ga * 0.7F), Glitch.alphaOf(cb, ga * 0.7F), Glitch.alphaOf(cg, ga * 0.95F)
               );
         }
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      double t = fx.styleTime();
      Object[] objectArray = new Object[7];
      objectArray[0] = t;
      objectArray[1] = this.envelope(fx, t);
      Object object = objectArray[2] = fx.data == Boolean.TRUE ? "" : " (held back: Reduce Flashing)";
      objectArray[3] = t < 0.15 ? "bursting" : (t < 0.25 ? "sweeping" : "fading");
      objectArray[4] = fx.hit.pose.valid ? "frozen" : "none";
      objectArray[5] = this.slices.getInt();
      objectArray[6] = Glitch.reduceFlashing() ? "on" : "off";
      return String.format(Locale.ROOT, "t %.3f s: glitch %.2f%s, %s, pose %s, slices %d, reduce flashing %s", objectArray);
   }

   public static enum GlyphSet implements Displayable {
      ERRORS("Errors", "ERR, NULL, 404..."),
      HEX("Hex", "0x and a byte"),
      BLOCKS("Blocks", "Shade blocks"),
      CUSTOM("Custom text", "Your own text, up to 12 characters");

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
