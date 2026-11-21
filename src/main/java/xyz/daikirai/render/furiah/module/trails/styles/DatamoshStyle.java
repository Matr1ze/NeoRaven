package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Glitch;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.hit.styles.CorruptStyle;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import xyz.daikirai.render.furiah.setting.TextSetting;

public final class DatamoshStyle extends TrailStyleBase {
   private static final int MAX_BARS = 16;
   private static final float BAR_FILL = 0.8F;
   private static final float SPLIT = 0.28F;
   private static final double GLYPH_SHOWS = 0.32;
   private static final double GLYPH_HEIGHT = 0.2;
   private static final float GLYPH_BURSTS = 0.45F;
   private static final String[] ERRORS = new String[]{"ERR", "NULL", "0xDEAD", "BAD DATA", "CRC", "EOF", "404", "I/O"};
   private static final String[] BLOCKS = new String[]{"█▓▒░", "▒░▓", "▓█░▒", "░▒▓█"};
   private static final String HEX_DIGITS = "0123456789ABCDEF";
   private static final int CHANNEL_A = 1;
   public final ThemeTint channelB;
   public final ThemeTint glyphColor;
   public final SliderSetting bars;
   public final SliderSetting bandHeight;
   public final SliderSetting jitter;
   public final SliderSetting glitchRate;
   public final BooleanSetting scanlines;
   public final BooleanSetting glyphs;
   public final ModeSetting<CorruptStyle.GlyphSet> glyphSet;
   public final TextSetting text;
   private final Glitch.Bursts bursts = new Glitch.Bursts();
   private final Glitch.Plane plane = new Glitch.Plane();
   private final StringBuilder hexText = new StringBuilder(6);
   private static final double[] PX = new double[1024];
   private static final double[] PY = new double[1024];
   private static final double[] PZ = new double[1024];
   private static final float[] PW = new float[1024];
   private static final float[] PA = new float[1024];
   private static final float[] PL = new float[1024];
   private static final int[] PC = new int[1024];
   private static final int[] CA = new int[1024];
   private static final int[] CB = new int[1024];
   public boolean glyphEveryBurstForTest;
   private int drawnBars;
   private int drawnBlocks;
   private int drawnGlyphs;
   private float lastGlitch;
   private boolean counting;

   public DatamoshStyle(FuriahTrails module) {
      super(
         module, TrailStyle.DATAMOSH, spec().taper(30.0).length(1.0).anchor(TrailAnchor.WAIST, false).animated()
      );
      this.glyphColor = this.signatureColor("glyphColor", "Glyphs", "The error glyphs, the blocks and the scanlines.", -657931, -1);
      this.channelB = this.signatureColor("channelB", "Channel B", "The second channel's color: the bars mix toward it.", -16718337, -13893752);
      this.doesNotApply(new Setting[]{this.size, this.glow});
      this.bars = this.setting(new SliderSetting("datamosh.bars", "Bars", "How many bars of color stretch back from you.", 4.0, 16.0, 8.0, 1.0));
      this.bandHeight = this.setting(
         new SliderSetting("datamosh.bandHeight", "Band Height", "How tall the band of bars is.", 0.4, 2.0, 1.2, 0.05).unit(" blocks")
      );
      this.jitter = this.setting(new SliderSetting("datamosh.jitter", "Jitter", "How far the bars jump and smear.", 0.0, 100.0, 50.0, 1.0).unit("%"));
      this.glitchRate = this.setting(
         new SliderSetting("datamosh.glitchRate", "Glitch Rate", "How often it glitches: the bars change and jump.", 0.5, 6.0, 2.0, 0.5).unit("/s")
      );
      this.scanlines = this.setting(new BooleanSetting("datamosh.scanlines", "Scanlines", "Thin lines drifting down through the band.", true));
      this.glyphs = this.setting(new BooleanSetting("datamosh.glyphs", "Glyphs", "Now and then an error glyph pops on the trail.", true));
      this.glyphSet = this.setting(
         new ModeSetting<>("datamosh.glyphSet", "Glyph Set", "What the glyphs say.", CorruptStyle.GlyphSet.class, CorruptStyle.GlyphSet.ERRORS)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return DatamoshStyle.this.glyphs.get();
               }
            })
      );
      this.text = this.setting(
         new TextSetting("datamosh.text", "Custom Text", "The glyphs' text with Custom text.", "DATAMOSH", 12).visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return DatamoshStyle.this.glyphs.get() && DatamoshStyle.this.glyphSet.get() == CorruptStyle.GlyphSet.CUSTOM;
            }
         })
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.jitter);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit();
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      boolean bl = this.counting = p.self && !p.ghost && !f.preview;
      if (this.counting) {
         this.drawnGlyphs = 0;
         this.drawnBlocks = 0;
         this.drawnBars = 0;
      }

      TrailShape s = p.shape;
      if (p.shape != null && s.runs != 0) {
         boolean calm = Glitch.reduceFlashing();
         double t = f.now;
         float g = this.bursts.at(t, this.glitchRate.getFloat(), p.entityId * 977 + p.side * 31 + (p.ghost ? 7 : 0), calm);
         int seed = this.bursts.seed;
         double since = this.bursts.since;
         if (this.counting) {
            this.lastGlitch = g;
         }

         int cb = this.slotColor(this.channelB, 0.5F, f.colorTime) | 0xFF000000;
         int cg = this.slotColor(this.glyphColor, 0.5F, f.colorTime) | 0xFF000000;
         RenderState.translucent(false);

         for (int k = 0; k < s.runs; k++) {
            int n = this.copyRun(p, f, k);
            if (n >= 2) {
               this.bars(p, f, n, t, g, seed, calm, cb, cg);
               if (this.scanlines.get()) {
                  this.scanlines(f, n, t, cg);
               }

               if (this.glyphs.get() && !p.reduced && !p.ghost && k == s.runs - 1) {
                  this.glyph(f, n, since, seed, calm, cb, cg);
               }
            }
         }
      }
   }

   private int copyRun(PlayerTrail p, TrailFrame f, int k) {
      int n = this.fill(p, f, k, 1.0F, -1.0F, 0.0F);
      TrailShape s = p.shape;
      int from = s.runStart[k];

      for (int j = 0; j < n; j++) {
         PX[j] = Vfx.X[j];
         PY[j] = Vfx.Y[j];
         PZ[j] = Vfx.Z[j];
         PW[j] = Vfx.W[j];
         PA[j] = POINT_ALPHA[j];
         PC[j] = Vfx.C[j] | 0xFF000000;
         PL[j] = s.along[from + j];
      }

      return n;
   }

   private void bars(PlayerTrail p, TrailFrame f, int n, double t, float g, int seed, boolean calm, int cb, int cg) {
      int count = this.bars.getInt();
      if (p.reduced) {
         count = (count + 1) / 2;
      }

      float jit = this.jitter.getFloat() / 100.0F;
      double band = this.bandHeight.getFloat();
      double head = PL[n - 1];
      double runLen = PL[0] - head;
      if (!(runLen < 0.05)) {
         double stepT = t * this.glitchRate.getFloat();
         int step = (int)Math.floor(stepT);
         float ease = calm ? Easing.smoothstep(0.55F, 1.0F, (float)(stepT - Math.floor(stepT))) : 0.0F;
         double slot = band / count;

         for (int i = 0; i < count; i++) {
            double yo = (0.5 - (i + 0.5) / count) * band;
            float th = (float)(slot * 0.8F * (0.75 + 0.5 * Rng.hash(i, 7)));
            float h = stepped(i * 31, 11, step, ease);
            float back = stepped(i * 31, 17, step, ease);
            double len = runLen * (0.22 + 0.78 * h);
            double start = jit * 0.45 * back;
            double shift = g * jit * (Rng.hash(seed, i * 3) - 0.5) * 0.9;
            double lift = g * jit * (Rng.hash(seed, i * 3 + 1) - 0.5) * slot * 1.4;
            double s0 = head + Math.max(0.0, start + shift);
            double s1 = Math.min(head + runLen, s0 + len);
            if (!(s1 - s0 < 0.04)) {
               float d = 0.28F * (1.0F + 1.5F * jit * g);
               int runs = 1 + (int)((calm ? Rng.hash(i, 19) : stepped(i * 31, 19, step, 0.0F)) * 2.99F);
               boolean drawn = false;
               double from = s0;

               for (int r = 0; r < runs; r++) {
                  double to = s1;
                  double next = s1;
                  if (r < runs - 1) {
                     double cut = s0 + (s1 - s0) * (r + 0.3 + 0.4 * stepped(i * 31 + r * 7, 23, step, ease)) / runs;
                     double gap = 0.05 + 0.15 * stepped(i * 31 + r * 7, 29, step, ease);
                     to = cut - gap * 0.5;
                     next = cut + gap * 0.5;
                  }

                  if (to - from > 0.03) {
                     float mix = r == 0 ? Rng.hash(i, 13) : Rng.hash(i * 5 + r, 13);
                     drawn |= this.run(f, n, from, to, yo, lift, th, mix, cb, d);
                  }

                  from = next;
               }

               if (drawn) {
                  if (this.counting) {
                     this.drawnBars++;
                  }

                  this.blocks(f, n, i, s0, s1, yo, lift, th, step, t, g, calm, cb, cg);
               }
            }
         }
      }
   }

   private boolean run(TrailFrame f, int n, double s0, double s1, double yo, double lift, float th, float mix, int cb, float d) {
      int m = this.strip(n, s0, s1, yo, lift, th, mix, cb, 0, 1.0F);
      if (m < 2) {
         return false;
      } else {
         TrailStrip.sides(0, m, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
         TrailStrip.draw(Textures.WHITE, 0, m, 1.0F, 0.0, 1.0F);
         System.arraycopy(CA, 0, Vfx.C, 0, m);
         TrailStrip.draw(Textures.WHITE, 0, m, d, -(0.5 + d * 0.5), 1.0F);
         System.arraycopy(CB, 0, Vfx.C, 0, m);
         TrailStrip.draw(Textures.WHITE, 0, m, d, 0.5 + d * 0.5, 1.0F);
         return true;
      }
   }

   private static float stepped(int key, int salt, int step, float ease) {
      float h = Rng.hash(key ^ step * -1640531535, salt);
      return ease <= 0.0F ? h : h + (Rng.hash(key ^ (step + 1) * -1640531535, salt) - h) * ease;
   }

   private void blocks(
      TrailFrame f, int n, int i, double s0, double s1, double yo, double lift, float th, int step, double t, float g, boolean calm, int cb, int cg
   ) {
      int count = 1 + Math.round(2.0F * g);
      int flick = (int)Math.floor(t * 9.0);

      for (int b = 0; b < count; b++) {
         int key = i * 7 + b;
         float vis = calm ? 0.5F + 0.5F * (float)Math.sin(t * 2.2 + key * 1.7) : (Rng.hash(key, flick) < 0.6F ? 1.0F : 0.0F);
         if (b > 0) {
            vis *= g;
         }

         double bl;
         if (!(vis <= 0.02F) && !(s1 - s0 < (bl = th * (2.0 + 2.0 * Rng.hash(key, 9))) * 1.2)) {
            double at = s0 + (s1 - s0 - bl) * Rng.hash(key, step + 5);
            double off;
            int m = this.strip(
               n,
               at,
               at + bl,
               yo,
               off = lift + (Rng.hash(key, step + 6) - 0.5) * th * 0.6,
               th * 1.5F,
               0.0F,
               cb,
               (b & 1) == 0 ? cg : (Rng.hash(key, 3) < 0.5F ? 1 : cb),
               vis
            );
            if (m >= 2) {
               TrailStrip.sides(0, m, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
               TrailStrip.draw(Textures.WHITE, 0, m, 1.0F, 0.0, 1.0F);
               if (this.counting) {
                  this.drawnBlocks++;
               }
            }
         }
      }
   }

   private int strip(int n, double s0, double s1, double yo, double lift, float th, float mix, int cb, int flat, float alpha) {
      int m = 0;

      for (int j = n - 1; j > 0 && m < 1022; j--) {
         double a = PL[j];
         double b = PL[j - 1];
         if (!(b <= s0)) {
            if (a >= s1) {
               break;
            }

            if (m == 0) {
               m = this.point(m, j, s0 > a ? (s0 - a) / (b - a) : 0.0, s0, s1, yo, lift, th, mix, cb, flat, alpha);
            }

            if (!(b < s1)) {
               m = this.point(m, j, (s1 - a) / (b - a), s0, s1, yo, lift, th, mix, cb, flat, alpha);
               break;
            }

            m = this.point(m, j - 1, 0.0, s0, s1, yo, lift, th, mix, cb, flat, alpha);
         }
      }

      return m;
   }

   private int point(int m, int j, double w, double s0, double s1, double yo, double lift, float th, float mix, int cb, int flat, float alpha) {
      int k = Math.max(0, j - 1);
      float wf = (float)w;
      float scale = PW[j] + (PW[k] - PW[j]) * wf;
      float a = (PA[j] + (PA[k] - PA[j]) * wf) * alpha;
      double along = PL[j] + (PL[k] - PL[j]) * w;
      Vfx.X[m] = PX[j] + (PX[k] - PX[j]) * w;
      Vfx.Y[m] = PY[j] + (PY[k] - PY[j]) * w + yo * scale + lift;
      Vfx.Z[m] = PZ[j] + (PZ[k] - PZ[j]) * w;
      Vfx.W[m] = th * scale;
      int ca = ColorUtil.lerp(PC[j], PC[k], wf);
      if (flat != 0) {
         Vfx.C[m] = ColorUtil.mulAlpha(flat == 1 ? ca : flat, a);
      } else {
         float v = (float)((along - s0) / Math.max(1.0E-6, s1 - s0));
         int c = ColorUtil.lerp(ca, cb, mix);
         c = v < 0.25F ? ColorUtil.lerp(c, -1, 0.5F * (0.25F - v) / 0.25F) : ColorUtil.shade(c, 1.0F - 0.55F * (v - 0.25F) / 0.75F);
         Vfx.C[m] = ColorUtil.mulAlpha(c | 0xFF000000, a);
         CA[m] = ColorUtil.mulAlpha(ca, a * 0.8F);
         CB[m] = ColorUtil.mulAlpha(cb, a * 0.8F);
      }

      return m + 1;
   }

   private void scanlines(TrailFrame f, int n, double t, int cg) {
      double band = this.bandHeight.getFloat();
      double speed = this.animSpeed == null ? 1.0 : this.animSpeed.getFloat() / 100.0;

      for (int l = 0; l < 2; l++) {
         double q = t * speed / 1.6 + l * 0.5;
         double pos = 0.5 - (q - Math.floor(q));
         float edge = (float)Math.sin(Math.PI * (pos + 0.5));

         for (int pass = 0; pass < 2; pass++) {
            float w = pass == 0 ? 0.09F : 0.016F;
            float k = (pass == 0 ? 0.12F : 0.55F) * edge;

            for (int j = 0; j < n; j++) {
               Vfx.X[j] = PX[j];
               Vfx.Y[j] = PY[j] + pos * band * 1.05 * PW[j];
               Vfx.Z[j] = PZ[j];
               Vfx.W[j] = w;
               Vfx.C[j] = ColorUtil.mulAlpha(cg, PA[j] * k);
            }

            TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
            TrailStrip.draw(pass == 0 ? Textures.LINE_GLOW : Textures.WHITE, 0, n, 1.0F, 0.0, 1.0F);
         }
      }
   }

   private void glyph(TrailFrame f, int n, double since, int seed, boolean calm, int cb, int cg) {
      if (!(since > 0.32)) {
         float f2 = Rng.hash(seed, 47);
         float f3 = this.glyphEveryBurstForTest ? 1.0F : 0.45F;
         if (!(f2 >= f3)) {
            f2 = calm ? Easing.smoothstep(0.0F, 0.06F, (float)since) * (1.0F - Easing.smoothstep(0.22F, 0.32F, (float)since)) : 1.0F;
            CharSequence str = this.glyphText(seed);
            if (str != null && str.length() != 0 && !(f2 <= 0.01F)) {
               double head = PL[n - 1];
               double along = head + (PL[0] - head) * (0.15 + 0.55 * Rng.hash(seed, 41));
               int j = n - 1;

               while (j > 1 && PL[j - 1] < along) {
                  j--;
               }

               int k = j - 1;
               double w = PL[k] - PL[j] > 1.0E-6 ? (along - PL[j]) / (PL[k] - PL[j]) : 0.0;
               float wf = (float)Math.max(0.0, Math.min(1.0, w));
               float scale = PW[j] + (PW[k] - PW[j]) * wf;
               float op = (PA[j] + (PA[k] - PA[j]) * wf) * f2;
               if (op <= 0.01F) {
                  return;
               }

               double gh = 0.2 * scale;
               double x = PX[j] + (PX[k] - PX[j]) * wf;
               double z = PZ[j] + (PZ[k] - PZ[j]) * wf;
               double y = PY[j] + (PY[k] - PY[j]) * wf + (Rng.hash(seed, 43) - 0.5) * this.bandHeight.getFloat() * 0.6 * scale;
               double width = Glitch.textWidth(str) * gh;
               this.plane.set(x, y, z, f.cam.rightX, f.cam.rightY, f.cam.rightZ, f.cam.upX, f.cam.upY, f.cam.upZ);
               int ca = ColorUtil.lerp(PC[j], PC[k], wf);
               this.plane
                  .splitText(
                     Glitch.atlas(),
                     str,
                     -width * 0.5,
                     -gh * 0.3,
                     gh,
                     gh * 0.08,
                     Glitch.alphaOf(ca, 0.7F * op),
                     Glitch.alphaOf(cb, 0.7F * op),
                     Glitch.alphaOf(cg, 0.95F * op)
                  );
               if (this.counting) {
                  this.drawnGlyphs++;
               }

               return;
            }

            return;
         }
      }
   }

   private CharSequence glyphText(int pick) {
      int q = pick & 2147483647;
      switch ((CorruptStyle.GlyphSet)this.glyphSet.get()) {
         case HEX:
            this.hexText.setLength(0);
            this.hexText.append('0').append('x').append("0123456789ABCDEF".charAt(q % 16)).append("0123456789ABCDEF".charAt(q / 16 % 16));
            return this.hexText;
         case BLOCKS:
            return BLOCKS[q % BLOCKS.length];
         case CUSTOM:
            return this.text.get();
         default:
            return ERRORS[q % ERRORS.length];
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%d bar(s), band %.2f, jitter %.0f%%, %.1f glitches/s, scanlines %s, glyphs %s (%s); reduce flashing %s; glitch %.2f; drawn: %d bar(s), %d block(s), %d glyph(s)",
         this.bars.getInt(),
         this.bandHeight.getFloat(),
         this.jitter.getFloat(),
         this.glitchRate.getFloat(),
         this.scanlines.get() ? "on" : "off",
         this.glyphs.get() ? "on" : "off",
         this.glyphSet.get().displayName(),
         Glitch.reduceFlashing() ? "on" : "off",
         this.lastGlitch,
         this.drawnBars,
         this.drawnBlocks,
         this.drawnGlyphs
      );
   }
}
