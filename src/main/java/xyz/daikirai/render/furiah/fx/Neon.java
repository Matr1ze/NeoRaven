package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class Neon {
   public static final float BLOOM_WIDTH = 2.6F;
   public static final float BLOOM_ALPHA = 0.32F;
   public static final float BODY_ALPHA = 0.85F;
   public static final float CORE_WIDTH = 0.3F;
   public static final float CORE_ALPHA = 0.95F;
   private static final int MAX_RIBBONS = 96;
   private static final int[] OFFSET = new int[96];
   private static final int[] COUNT = new int[96];
   private static final boolean[] CLOSED = new boolean[96];
   private static final boolean[] FLAT = new boolean[96];
   private static final float[] W = new float[1024];
   private static final int[] C = new int[1024];
   private static int ribbons;
   private static final double[] GX = new double[1024];
   private static final double[] GZ = new double[1024];

   private Neon() {
   }

   public static int tint(int color) {
      return ColorUtil.lerp(color, color | 16777215, 0.5F);
   }

   public static void add(int offset, int n, boolean closed) {
      add(offset, n, closed, false);
   }

   public static void addFlat(int offset, int n) {
      add(offset, n, false, true);
   }

   private static void add(int offset, int n, boolean closed, boolean flat) {
      if (n >= 2 && ribbons < 96) {
         OFFSET[ribbons] = offset;
         COUNT[ribbons] = n;
         CLOSED[ribbons] = closed;
         FLAT[ribbons] = flat;
         ribbons++;
      }
   }

   public static void flush() {
      flush(1.0F, 1.0F, 1.0F);
   }

   public static void flush(float bloom, float body, float core) {
      if (ribbons != 0) {
         for (int r = 0; r < ribbons; r++) {
            for (int i = OFFSET[r]; i < OFFSET[r] + COUNT[r]; i++) {
               W[i] = Vfx.W[i];
               C[i] = Vfx.C[i];
            }
         }

         if (bloom > 0.0F) {
            RenderState.additive();
            layer(2.6F, 0.32F * bloom, false, Textures.LINE_GLOW);
         }

         RenderState.translucent(false);
         if (body > 0.0F) {
            layer(1.0F, 0.85F * body, false, Textures.LINE_GLOW);
         }

         if (core > 0.0F) {
            layer(0.3F, 0.95F * core, true, Textures.LINE_CORE);
         }

         RenderState.additive();

         for (int var6 = 0; var6 < ribbons; var6++) {
            for (int i = OFFSET[var6]; i < OFFSET[var6] + COUNT[var6]; i++) {
               Vfx.W[i] = W[i];
               Vfx.C[i] = C[i];
            }
         }

         ribbons = 0;
      }
   }

   private static void layer(float width, float alpha, boolean tint, int tex) {
      for (int r = 0; r < ribbons; r++) {
         for (int i = OFFSET[r]; i < OFFSET[r] + COUNT[r]; i++) {
            Vfx.W[i] = W[i] * width;
            int c = tint ? tint(C[i]) : C[i];
            Vfx.C[i] = ColorUtil.mulAlpha(c, Math.min(1.0F, alpha));
         }

         if (FLAT[r]) {
            Vfx.flatRibbon(tex, OFFSET[r], COUNT[r]);
         } else {
            Vfx.ribbon(tex, OFFSET[r], COUNT[r], CLOSED[r]);
         }
      }
   }

   public static void line(double x1, double y1, double z1, double x2, double y2, double z2, double width, int c1, int c2) {
      RenderState.additive();
      Vfx.line(Textures.LINE_GLOW, x1, y1, z1, x2, y2, z2, width * 2.6F, ColorUtil.mulAlpha(c1, 0.32F), ColorUtil.mulAlpha(c2, 0.32F));
      RenderState.translucent(false);
      Vfx.line(Textures.LINE_GLOW, x1, y1, z1, x2, y2, z2, width, ColorUtil.mulAlpha(c1, 0.85F), ColorUtil.mulAlpha(c2, 0.85F));
      Vfx.line(Textures.LINE_CORE, x1, y1, z1, x2, y2, z2, width * 0.3F, ColorUtil.mulAlpha(tint(c1), 0.95F), ColorUtil.mulAlpha(tint(c2), 0.95F));
      RenderState.additive();
   }

   public static void ring(
      FxFrame f,
      Terrain.HeightField hf,
      double cx,
      double cz,
      double radius,
      double width,
      int segments,
      ThemeTint color,
      int staticColor,
      float alpha,
      float tShift
   ) {
      if (!(alpha <= 0.003F)) {
         RenderState.additive();
         Ground.ring(f, hf, Textures.LINE_GLOW, cx, cz, radius, width * 2.6F, segments, color, staticColor, alpha * 0.32F, tShift);
         RenderState.translucent(false);
         Ground.ring(f, hf, Textures.LINE_GLOW, cx, cz, radius, width, segments, color, staticColor, alpha * 0.85F, tShift);
         Ground.lift = 0.5F;
         Ground.ring(f, hf, Textures.LINE_CORE, cx, cz, radius, width * 0.3F, segments, color, staticColor, alpha * 0.95F, tShift);
         Ground.lift = 0.0F;
         RenderState.additive();
      }
   }

   public static void arc(
      FxFrame f, Terrain.HeightField hf, double cx, double cz, double radius, double width, double a0, double span, int segments, int color, float alpha
   ) {
      if (!(alpha <= 0.003F)) {
         double w = width * 2.6F * 0.5;
         RenderState.additive();
         Ground.band(f, hf, Textures.LINE_GLOW, cx, cz, Math.max(0.0, radius - w), radius + w, a0, span, segments, null, color, alpha * 0.32F, 0.0F);
         RenderState.translucent(false);
         Ground.band(
            f, hf, Textures.LINE_GLOW, cx, cz, Math.max(0.0, radius - width * 0.5), radius + width * 0.5, a0, span, segments, null, color, alpha * 0.85F, 0.0F
         );
         double c = width * 0.3F * 0.5;
         Ground.band(f, hf, Textures.LINE_CORE, cx, cz, Math.max(0.0, radius - c), radius + c, a0, span, segments, null, tint(color), alpha * 0.95F, 0.0F);
         RenderState.additive();
      }
   }

   public static void groundLine(FxFrame f, Terrain.HeightField hf, double x1, double z1, double x2, double z2, double width, int c1, int c2, int parts) {
      RenderState.additive();
      Ground.line(f, hf, Textures.LINE_GLOW, x1, z1, x2, z2, width * 2.6F, ColorUtil.mulAlpha(c1, 0.32F), ColorUtil.mulAlpha(c2, 0.32F), parts);
      RenderState.translucent(false);
      Ground.line(f, hf, Textures.LINE_GLOW, x1, z1, x2, z2, width, ColorUtil.mulAlpha(c1, 0.85F), ColorUtil.mulAlpha(c2, 0.85F), parts);
      Ground.line(f, hf, Textures.LINE_CORE, x1, z1, x2, z2, width * 0.3F, ColorUtil.mulAlpha(tint(c1), 0.95F), ColorUtil.mulAlpha(tint(c2), 0.95F), parts);
      RenderState.additive();
   }

   public static void groundRibbon(FxFrame f, Terrain.HeightField hf, int offset, int n) {
      if (n >= 2) {
         for (int i = offset; i < offset + n; i++) {
            GX[i] = Vfx.X[i];
            GZ[i] = Vfx.Z[i];
            W[i] = Vfx.W[i];
            C[i] = Vfx.C[i];
         }

         for (int pass = 0; pass < 3; pass++) {
            for (int i = offset; i < offset + n; i++) {
               Vfx.X[i] = GX[i];
               Vfx.Z[i] = GZ[i];
               if (pass == 0) {
                  Vfx.W[i] = W[i] * 2.6F;
                  Vfx.C[i] = ColorUtil.mulAlpha(C[i], 0.32F);
               } else if (pass == 1) {
                  Vfx.W[i] = W[i];
                  Vfx.C[i] = ColorUtil.mulAlpha(C[i], 0.85F);
               } else {
                  Vfx.W[i] = W[i] * 0.3F;
                  Vfx.C[i] = ColorUtil.mulAlpha(tint(C[i]), 0.95F);
               }
            }

            if (pass == 0) {
               RenderState.additive();
            } else {
               RenderState.translucent(false);
            }

            Ground.ribbon(f, hf, pass == 2 ? Textures.LINE_CORE : Textures.LINE_GLOW, offset, n);
         }

         RenderState.additive();
      }
   }
}
