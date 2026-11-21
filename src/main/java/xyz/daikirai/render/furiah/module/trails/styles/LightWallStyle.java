package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class LightWallStyle extends TrailStyleBase {
   public final SliderSetting height;
   public final SliderSetting edge;
   public final SliderSetting fillOpacity;
   public final ModeSetting<LightWallStyle.Variant> variant;
   public final BooleanSetting rise;
   public final BooleanSetting scan;
   public final BooleanSetting dashed;
   private final GroundCache ground = new GroundCache();
   private static final double[] PX = new double[1024];
   private static final double[] PY = new double[1024];
   private static final double[] PZ = new double[1024];
   private static final double[] BASE = new double[1024];
   private static final double[] TOP = new double[1024];
   private static final int[] COL = new int[1024];
   private static final float[] WID = new float[1024];
   private int wallPieces;

   public LightWallStyle(FuriahTrails module) {
      super(module, TrailStyle.LIGHT_WALL, spec().size(0.02, 0.2, 0.06, 0.01).taper(20.0).anchor(TrailAnchor.FEET, true).animated());
      this.variant = this.setting(
         new ModeSetting<>(
            "light_wall.variant", "Variant", "A standing wall, a line on the ground, or both.", LightWallStyle.Variant.class, LightWallStyle.Variant.WALL
         )
      );
      this.height = this.setting(new SliderSetting("light_wall.height", "Height", "How tall the wall stands.", 0.2, 2.0, 0.6, 0.05).unit(" blocks"));
      this.rise = this.setting(new BooleanSetting("light_wall.rise", "Rise with Jumps", "The wall's top follows your feet when you jump.", false));
      this.edge = this.setting(
         new SliderSetting("light_wall.edge", "Edge Brightness", "How bright the top edge and the base line are.", 0.0, 100.0, 80.0, 1.0).unit("%")
      );
      this.fillOpacity = this.setting(
         new SliderSetting("light_wall.fill", "Fill Opacity", "How strong the light between the edges is.", 0.0, 100.0, 35.0, 1.0).unit("%")
      );
      this.scan = this.setting(new BooleanSetting("light_wall.scan", "Scan Lines", "Thin lines drifting up the wall.", false));
      this.dashed = this.setting(new BooleanSetting("light_wall.dashed", "Dashed", "The wall in dashes.", false));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.height);
      out.add(this.edge);
      out.add(this.fillOpacity);
   }

   public int wallPiecesForTest() {
      return this.wallPieces;
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      TrailShape s = p.shape;
      LightWallStyle.Variant v = this.variant.get();
      this.wallPieces = 0;

      for (int k = 0; k < s.runs; k++) {
         int n = this.fill(p, f, k, this.size.getFloat(), -1.0F, 0.0F);
         if (n >= 2) {
            this.groundOf(p, f, s, k, n);
            int start = 0;

            for (int j = 1; j <= n; j++) {
               if (j == n || Math.abs(BASE[j] - BASE[j - 1]) > 1.05) {
                  if (j - start >= 2) {
                     this.piece(p, f, s, k, start, j, v);
                     this.wallPieces++;
                  }

                  start = j;
               }
            }
         }
      }
   }

   private void groundOf(PlayerTrail p, TrailFrame f, TrailShape s, int k, int n) {
      int from = s.runStart[k];
      double h = this.height.getFloat() * Math.sqrt(p.widthScale);
      boolean dash = this.dashed.get();
      double last = Double.NaN;

      for (int j = 0; j < n; j++) {
         PX[j] = Vfx.X[j];
         PY[j] = Vfx.Y[j];
         PZ[j] = Vfx.Z[j];
         WID[j] = Vfx.W[j];
         int c = Vfx.C[j];
         if (dash && ((int)Math.floor(s.along[from + j] / 0.35F) & 1) == 1) {
            c = ColorUtil.mulAlpha(c, 0.12F);
         }

         COL[j] = c;
         double wx = PX[j] + f.viewX;
         double feet = PY[j] + f.viewY - 0.06;
         double wz = PZ[j] + f.viewZ;
         double g = this.ground.surface(f.world, wx, feet, wz, 0.6, 3.0);
         if (Double.isNaN(g)) {
            g = Double.isNaN(last) ? feet : last;
         }

         last = g;
         BASE[j] = g - f.viewY + 0.012;
         TOP[j] = this.rise.get() ? Math.max(BASE[j], feet - f.viewY) + h : BASE[j] + h;
      }
   }

   private void piece(PlayerTrail p, TrailFrame f, TrailShape s, int k, int a, int b, LightWallStyle.Variant v) {
      float edges = this.edge.getFloat() / 100.0F;
      float fillA = this.fillOpacity.getFloat() / 100.0F;
      float glowAmount = this.glow.getFloat() / 100.0F;
      RenderState.additive();
      if (v != LightWallStyle.Variant.LINE) {
         if (fillA > 0.0F) {
            for (int pass = 0; pass < 2; pass++) {
               if (pass == 0) {
                  RenderState.translucent(false);
               } else {
                  RenderState.additive();
               }

               float bottom = pass == 0 ? 0.95F : 0.45F;
               float top = pass == 0 ? 0.12F : 0.16F;

               for (int j = a + 1; j < b; j++) {
                  int c0 = COL[j - 1];
                  int c1 = COL[j];
                  int b0 = ColorUtil.mulAlpha(c0, fillA * bottom);
                  int b1 = ColorUtil.mulAlpha(c1, fillA * bottom);
                  int t0 = ColorUtil.mulAlpha(c0, fillA * top);
                  int t1 = ColorUtil.mulAlpha(c1, fillA * top);
                  Vfx.quad(PX[j - 1], BASE[j - 1], PZ[j - 1], PX[j - 1], TOP[j - 1], PZ[j - 1], PX[j], TOP[j], PZ[j], PX[j], BASE[j], PZ[j], b0, t0, t1, b1);
               }
            }
         }

         RenderState.translucent(false);
         this.edgeLine(f, a, b, 1.0, Textures.LINE_GLOW, 1.4F, 0.45F * edges, 0.0F);
         this.edgeLine(f, a, b, 0.0, Textures.LINE_GLOW, 1.8F, 0.55F * edges, 0.0F);
         RenderState.additive();
         if (this.scan.get()) {
            double rate = 0.45 * this.animSpeed.getFloat() / 100.0;

            for (int line = 0; line < 3; line++) {
               double u = (f.now * rate + line / 3.0) % 1.0;
               this.edgeLine(f, a, b, u, Textures.LINE_GLOW, 0.6F, 0.35F * (float)(1.0 - u), 0.0F);
            }
         }

         this.edgeLine(f, a, b, 1.0, Textures.LINE_GLOW, 2.2F + 3.0F * glowAmount, 0.35F * edges * (0.4F + glowAmount), 0.0F);
         this.edgeLine(f, a, b, 1.0, Textures.LINE_CORE, 0.9F, 0.85F * edges, 1.0F);
         this.edgeLine(f, a, b, 0.0, Textures.LINE_GLOW, 2.6F + 3.0F * glowAmount, 0.45F * edges * (0.4F + glowAmount), 0.0F);
         this.edgeLine(f, a, b, 0.0, Textures.LINE_CORE, 1.2F, edges, 1.0F);
      }

      if (v != LightWallStyle.Variant.WALL) {
         int n = 0;

         for (int j = a; j < b; j++) {
            Vfx.X[n] = PX[j];
            Vfx.Y[n] = BASE[j] + 0.004;
            Vfx.Z[n] = PZ[j];
            Vfx.W[n] = WID[j] * 4.0F;
            Vfx.C[n] = COL[j];
            n++;
         }

         TrailStrip.sides(0, n, 1, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
         RenderState.translucent(false);
         TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.0F, 0.0, 0.6F);
         RenderState.additive();
         TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.6F + 2.0F * glowAmount, 0.0, 0.5F);
         TrailStrip.draw(Textures.LINE_CORE, 0, n, 0.45F, 0.0, Math.max(0.3F, edges));
      }
   }

   private void edgeLine(TrailFrame f, int a, int b, double u, int texture, float widthScale, float alpha, float white) {
      if (!(alpha <= 0.0F)) {
         int n = 0;

         for (int j = a; j < b; j++) {
            Vfx.X[n] = PX[j];
            Vfx.Y[n] = BASE[j] + (TOP[j] - BASE[j]) * u;
            Vfx.Z[n] = PZ[j];
            Vfx.W[n] = WID[j];
            int c = COL[j];
            Vfx.C[n] = white > 0.0F ? c & 0xFF000000 | ColorUtil.lerp(c, ColorUtil.core(c), white) & 16777215 : c;
            n++;
         }

         TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
         TrailStrip.draw(texture, 0, n, widthScale, 0.0, alpha);
      }
   }

   public static enum Variant implements Displayable {
      WALL("Wall", "A standing sheet of light"),
      LINE("Ground line", "A flat glowing line on the ground"),
      BOTH("Both", "The wall and the line");

      private final String name;
      private final String description;

      private Variant(String name, String description) {
         this.name = name;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.name;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
