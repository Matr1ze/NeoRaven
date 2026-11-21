package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Distortion;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class HexShieldStyle extends HitStyleBase {
   public final SliderSetting cellSize;
   public final BooleanSetting shimmer;
   private static final int MAX_CELLS = 600;
   private final double[] cellX = new double[600];
   private final double[] cellY = new double[600];
   private final double[] cellZ = new double[600];
   private final double[] cellTheta = new double[600];
   private final double[] cellLocalY = new double[600];
   private final double[] cellS = new double[600];
   private final int[] cellColor = new int[600];
   private final float[] cellK = new float[600];
   private final float[] cellGlow = new float[600];
   private int cells;

   public HexShieldStyle() {
      super(HitStyle.HEX_SHIELD, 0.5F, new ThemeTint());
      this.cellSize = this.setting(new SliderSetting("hexshield.cellSize", "Cell Size", "Size of the hexagons.", 0.6, 1.6, 1.0, 0.05).unit("x"));
      this.shimmer = this.setting(new BooleanSetting("hexshield.shimmer", "Shimmer", "A faint glow over the whole shield.", true));
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha() * (1.0F - Easing.smoothstep(0.72F, 1.0F, p)) * Easing.smoothstep(0.0F, 0.04F, p);
      if (!(a <= 0.003F)) {
         double cx = fx.cx();
         double cy = fx.cy();
         double cz = fx.cz();
         double ra = (fx.hit.width * 0.5 + 0.34) * fx.size;
         double rb = (fx.hit.height * 0.5 + 0.28) * fx.size;
         double ex = fx.ix() - cx;
         double ez;
         double ey;
         double el = Math.sqrt(ex * ex + (ey = (fx.iy() - cy) * ra / rb) * ey + (ez = fx.iz() - cz) * ez);
         if (el < 1.0E-6) {
            ex = -fx.basis.fx;
            ey = 0.0;
            ez = -fx.basis.fz;
            el = Math.sqrt(ex * ex + ez * ez);
         }

         double var90;
         double hx = cx + (var90 = ex / el) * ra;
         double var89;
         double hy = cy + (var89 = ey / el) * rb;
         double var88;
         double hz = cz + (var88 = ez / el) * ra;
         if (this.shimmer.get()) {
            float shim = 0.2F * a * (1.0F - Easing.smoothstep(0.2F, 0.9F, p));
            this.drawShell(f, cx, cy, cz, ra, rb, shim);
         }

         this.cells = 0;
         double s = 0.13 * this.cellSize.get() * fx.size;
         int columns = Math.max(6, (int)Math.round((Math.PI * 2) * ra / (1.5 * s)));
         columns += columns % 2;
         double colStep = (Math.PI * 2) / columns;
         double rowStep = Math.sqrt(3.0) * s;
         int rows = (int)Math.floor(rb * 0.82 / rowStep);
         double front = 2.3 * fx.size * Easing.outQuad(Easing.clamp01(p / 0.85F));
         Camera cam = Vfx.camera();

         for (int col = 0; col < columns; col++) {
            double theta = col * colStep;
            double shift = col % 2 * rowStep * 0.5;

            for (int row = -rows; row <= rows; row++) {
               double yLocal = row * rowStep + shift;
               if (!(Math.abs(yLocal) > rb * 0.86)) {
                  double ringR = ra * Math.sqrt(Math.max(0.0, 1.0 - yLocal / rb * (yLocal / rb)));
                  double px = cx + Math.cos(theta) * ringR;
                  double py = cy + yLocal;
                  double pz = cz + Math.sin(theta) * ringR;
                  double nx = Math.cos(theta);
                  double ny = yLocal / rb * (ra / rb);
                  double nz = Math.sin(theta);
                  double vx = f.rx(px) - cam.posX;
                  if (!(nx * vx + ny * (f.ry(py) - cam.posY) + nz * (f.rz(pz) - cam.posZ) > 0.0)) {
                     double dx = px - hx;
                     double dy = py - hy;
                     double dz = pz - hz;
                     double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                     float patch = (float)Math.exp(-(d * d) / 0.12) * (1.0F - Easing.smoothstep(0.1F, 0.45F, p));
                     double off;
                     float glow = Math.max(
                        patch,
                        Math.max(
                           (float)Math.exp(-(off = (d - front) / 0.22) * off) * (1.0F - 0.6F * Easing.smoothstep(0.8F, 2.6F, (float)d)),
                           d < front ? 0.3F * (float)Math.exp(-(front - d) * 2.5) : 0.0F
                        )
                     );
                     float k = Math.max(glow, this.shimmer.get() ? 0.1F * (1.0F - Easing.smoothstep(0.6F, 2.0F, (float)d)) : 0.0F) * a;
                     if (!(k <= 0.01F)) {
                        int c = this.color.getColor(Easing.clamp01((float)(d / 1.8)));
                        this.keep(px, py, pz, theta, yLocal, s * (0.92 - 0.08 * glow), c, k, glow);
                     }
                  }
               }
            }
         }

         RenderState.translucent(false);

         for (int i = 0; i < this.cells; i++) {
            this.cell(f, i, ra, rb, alpha(this.cellColor[i], Math.min(1.0F, this.cellK[i] * (0.45F + 0.5F * this.cellGlow[i]))), 1.0F);
         }

         RenderState.additive();

         for (int var86 = 0; var86 < this.cells; var86++) {
            this.cell(f, var86, ra, rb, alpha(Neon.tint(this.cellColor[var86]), Math.min(1.0F, this.cellK[var86] * 0.8F * this.cellGlow[var86])), 0.9F);
         }

         for (int var87 = 0; var87 < this.cells; var87++) {
            if (this.cellGlow[var87] > 0.45F) {
               Vfx.sprite(
                  Textures.GLOW,
                  f.rx(this.cellX[var87]),
                  f.ry(this.cellY[var87]),
                  f.rz(this.cellZ[var87]),
                  this.cellS[var87] * 2.6,
                  0.0F,
                  alpha(this.cellColor[var87], 0.3F * this.cellK[var87] * this.cellGlow[var87])
               );
            }
         }
      }
   }

   private void keep(double px, double py, double pz, double theta, double yLocal, double s, int c, float k, float glow) {
      if (this.cells < 600) {
         int i = this.cells++;
         this.cellX[i] = px;
         this.cellY[i] = py;
         this.cellZ[i] = pz;
         this.cellTheta[i] = theta;
         this.cellLocalY[i] = yLocal;
         this.cellS[i] = s;
         this.cellColor[i] = c;
         this.cellK[i] = k;
         this.cellGlow[i] = glow;
      }
   }

   private void cell(FxFrame f, int i, double ra, double rb, int color, float scale) {
      double theta = this.cellTheta[i];
      double yLocal = this.cellLocalY[i];
      double s = this.cellS[i] * scale;
      double tx = -Math.sin(theta);
      double tz = Math.cos(theta);
      double slope = -(yLocal / (rb * rb)) * ra * ra / Math.max(0.001, ra * Math.sqrt(Math.max(1.0E-4, 1.0 - yLocal / rb * (yLocal / rb))));
      double ux = Math.cos(theta) * slope;
      double uy = 1.0;
      double uz = Math.sin(theta) * slope;
      double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
      double x = f.rx(this.cellX[i]);
      double y = f.ry(this.cellY[i]);
      double z = f.rz(this.cellZ[i]);
      double var35;
      double var36;
      double var37;
      Vfx.quadUV(
         Textures.HEX_FILL,
         x - tx * s - ux * s,
         y - (var36 = uy / ul) * s,
         z - tz * s - (var37 = uz / ul) * s,
         0.0,
         1.0,
         color,
         x + tx * s - (var35 = ux / ul) * s,
         y - var36 * s,
         z + tz * s - var37 * s,
         1.0,
         1.0,
         color,
         x + tx * s + var35 * s,
         y + var36 * s,
         z + tz * s + var37 * s,
         1.0,
         0.0,
         color,
         x - tx * s + var35 * s,
         y + var36 * s,
         z - tz * s + var37 * s,
         0.0,
         0.0,
         color
      );
   }

   private void drawShell(FxFrame f, double cx, double cy, double cz, double ra, double rb, float a) {
      if (!(a <= 0.003F)) {
         int around = 28;
         int rings = 14;
         Distortion.begin(f.frameId, 0.0F, this.color.getColor(0.5F), 0.9F, a, false);

         for (int j = 0; j < rings; j++) {
            double lat0 = (-Math.PI / 2) + Math.PI * j / rings;
            double lat1 = (-Math.PI / 2) + Math.PI * (j + 1) / rings;

            for (int i = 0; i < around; i++) {
               double lon0 = (Math.PI * 2) * i / around;
               double lon1 = (Math.PI * 2) * (i + 1) / around;
               shellVertex(f, cx, cy, cz, ra, rb, lat0, lon0);
               shellVertex(f, cx, cy, cz, ra, rb, lat0, lon1);
               shellVertex(f, cx, cy, cz, ra, rb, lat1, lon1);
               shellVertex(f, cx, cy, cz, ra, rb, lat1, lon0);
            }
         }

         Distortion.end();
      }
   }

   private static void shellVertex(FxFrame f, double cx, double cy, double cz, double ra, double rb, double lat, double lon) {
      double cl = Math.cos(lat);
      double dx = cl * Math.cos(lon);
      double dy = Math.sin(lat);
      double dz = cl * Math.sin(lon);
      double nx = dx / ra;
      double ny = dy / rb;
      double nz = dz / ra;
      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      Distortion.vertex(f.rx(cx + dx * ra), f.ry(cy + dy * rb), f.rz(cz + dz * ra), nx / nl, ny / nl, nz / nl);
   }
}
