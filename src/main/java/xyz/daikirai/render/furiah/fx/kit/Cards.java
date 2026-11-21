package xyz.daikirai.render.furiah.fx.kit;

import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.OptiFineCompat;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.Displayable;
import net.minecraft.client.renderer.GlStateManager;

public final class Cards {
   private static final int CORNER_STEPS = 4;
   public static final int OUTLINE = 20;
   private static final double[] OX = new double[20];
   private static final double[] OY = new double[20];
   private static final double[] IX = new double[20];
   private static final double[] IY = new double[20];
   private static final double[] ONX = new double[20];
   private static final double[] ONY = new double[20];
   public static final double SYMBOL_Y = 0.1640625;
   private static final float[] UV = new float[4];
   private static final int CORNERS = 24;
   private static final double[] QX;
   private static final double[] QY;
   private static final double[] QZ;
   private static final double[] QA;
   private static final double[] QB;
   private static final double[] QC;
   private static final double[] QU;
   private static final double[] QV;
   private static final float[] QG;
   private static final double[] SX;
   private static final double[] SY;
   private static final double[] SZ;
   private static final double[] SA;
   private static final double[] SB;
   private static final double[] SC;
   private static final double[] SU;
   private static final double[] SV;
   private static final float[] SG;
   private static int qn;
   private static final int MASK_W = 256;
   private static final int MASK_H = 384;
   private static final double MARGIN = 0.03;
   private static final KitTextures.Painted MASK;

   private Cards() {
   }

   public static int withAlpha(int color, float a) {
      return Math.round(255.0F * Easing.clamp01(a) * ((color >>> 24) / 255.0F)) << 24 | color & 16777215;
   }

   public static void body(Cards.Card c, int col) {
      if (!(c.half <= 1.0E-4)) {
         double s = c.half * 2.0;

         for (int side = -1; side <= 1; side += 2) {
            double th = c.half * 0.04 * side;
            if (c.cut) {
               qn = 0;

               for (int i2 = 0; i2 < 20; i2++) {
                  put(IX[i2] * s, IY[i2] * s, th, 0.0, 0.0, side, 0.0F, 0.0, 0.0);
               }

               cutPolygon(c);
               mesh(c, col);
            } else {
               int base = MeshBatch.stage(21);
               double sx = c.nx * side;
               double sy = c.ny * side;
               double sz = c.nz * side;
               MeshBatch.vertex(base, c.x + c.nx * th, c.y + c.ny * th, c.z + c.nz * th, sx, sy, sz, col, 0.0F);

               for (int i = 0; i < 20; i++) {
                  double lx = IX[i] * s;
                  double ly = IY[i] * s;
                  MeshBatch.vertex(base + 1 + i, c.px(lx, ly, th), c.py(lx, ly, th), c.pz(lx, ly, th), sx, sy, sz, col, 0.0F);
               }

               for (int var19 = 0; var19 < 20; var19++) {
                  MeshBatch.tri(base, base + 1 + var19, base + 1 + (var19 + 1) % 20);
               }
            }
         }
      }
   }

   public static void foil(Cards.Card c, int col, double sweep, boolean shine) {
      double hw = c.half;
      if (!(hw <= 1.0E-4)) {
         double th = hw * 0.04;
         double raise = hw * 0.006;
         if (c.cut) {
            foilCut(c, col, sweep, shine, th, raise);
         } else {
            int base = MeshBatch.stage(120);

            for (int i = 0; i < 20; i++) {
               double olx = OX[i] * hw * 2.0;
               double oly = OY[i] * hw * 2.0;
               double ilx = IX[i] * hw * 2.0;
               double ily = IY[i] * hw * 2.0;
               float gO = shine ? glintAt(OX[i], OY[i], sweep) : 0.0F;
               float gI = shine ? glintAt(IX[i], IY[i], sweep) : 0.0F;

               for (int side = -1; side <= 1; side += 2) {
                  double z = (th + raise) * side;
                  int o = base + i * 6 + (side < 0 ? 0 : 2);
                  vertex(o, c, olx, oly, z, ONX[i] * 0.35, ONY[i] * 0.35, side, col, gO);
                  vertex(o + 1, c, ilx, ily, z, 0.0, 0.0, side, col, gI);
               }

               vertex(base + i * 6 + 4, c, olx, oly, -th, ONX[i], ONY[i], 0.0, col, gO);
               vertex(base + i * 6 + 5, c, olx, oly, th, ONX[i], ONY[i], 0.0, col, gO);
            }

            for (int var27 = 0; var27 < 20; var27++) {
               int a = base + var27 * 6;
               int b = base + (var27 + 1) % 20 * 6;
               MeshBatch.quad(a, b, b + 1, a + 1);
               MeshBatch.quad(a + 2, b + 2, b + 3, a + 3);
               MeshBatch.quad(a + 4, b + 4, b + 5, a + 5);
            }
         }
      }
   }

   public static float glintAt(double x, double y, double sweep) {
      double d = (x * 0.8 + y * 0.6) / 1.2 + 0.5 - sweep;
      return (float)Math.max(0.0, 1.0 - Math.abs(d) / 0.12) * 0.85F;
   }

   private static void vertex(int i, Cards.Card c, double lx, double ly, double z, double nxl, double nyl, double nzl, int col, float glint) {
      double vx = c.rx * nxl + c.ux * nyl + c.nx * nzl;
      double vy = c.ry * nxl + c.uy * nyl + c.ny * nzl;
      double vz = c.rz * nxl + c.uz * nyl + c.nz * nzl;
      double l = Math.sqrt(vx * vx + vy * vy + vz * vz);
      if (l < 1.0E-9) {
         l = 1.0;
      }

      MeshBatch.vertex(i, c.px(lx, ly, z), c.py(lx, ly, z), c.pz(lx, ly, z), vx / l, vy / l, vz / l, col, glint);
   }

   private static void foilCut(Cards.Card c, int col, double sweep, boolean shine, double th, double raise) {
      double s = c.half * 2.0;

      for (int i = 0; i < 20; i++) {
         int j = (i + 1) % 20;
         double oix = OX[i] * s;
         double oiy = OY[i] * s;
         double ojx = OX[j] * s;
         double ojy = OY[j] * s;
         double iix = IX[i] * s;
         double iiy = IY[i] * s;
         double ijx = IX[j] * s;
         double ijy = IY[j] * s;
         float gOi = shine ? glintAt(OX[i], OY[i], sweep) : 0.0F;
         float gOj = shine ? glintAt(OX[j], OY[j], sweep) : 0.0F;
         float gIi = shine ? glintAt(IX[i], IY[i], sweep) : 0.0F;
         float gIj = shine ? glintAt(IX[j], IY[j], sweep) : 0.0F;

         for (int side = -1; side <= 1; side += 2) {
            double z = (th + raise) * side;
            qn = 0;
            put(oix, oiy, z, ONX[i] * 0.35, ONY[i] * 0.35, side, gOi, 0.0, 0.0);
            put(ojx, ojy, z, ONX[j] * 0.35, ONY[j] * 0.35, side, gOj, 0.0, 0.0);
            put(ijx, ijy, z, 0.0, 0.0, side, gIj, 0.0, 0.0);
            put(iix, iiy, z, 0.0, 0.0, side, gIi, 0.0, 0.0);
            cutPolygon(c);
            mesh(c, col);
         }

         qn = 0;
         put(oix, oiy, -th, ONX[i], ONY[i], 0.0, gOi, 0.0, 0.0);
         put(ojx, ojy, -th, ONX[j], ONY[j], 0.0, gOj, 0.0, 0.0);
         put(ojx, ojy, th, ONX[j], ONY[j], 0.0, gOj, 0.0, 0.0);
         put(oix, oiy, th, ONX[i], ONY[i], 0.0, gOi, 0.0, 0.0);
         cutPolygon(c);
         mesh(c, col);
      }
   }

   private static void put(double lx, double ly, double lz, double na, double nb, double nc, float glint, double u, double v) {
      QX[qn] = lx;
      QY[qn] = ly;
      QZ[qn] = lz;
      QA[qn] = na;
      QB[qn] = nb;
      QC[qn] = nc;
      QG[qn] = glint;
      QU[qn] = u;
      QV[qn] = v;
      qn++;
   }

   private static void cutPolygon(Cards.Card c) {
      int m = 0;

      for (int i = 0; i < qn; i++) {
         int j = i + 1 == qn ? 0 : i + 1;
         double di = c.cutA * QX[i] + c.cutB * QY[i] - c.cutD;
         double dj = c.cutA * QX[j] + c.cutB * QY[j] - c.cutD;
         if (di >= 0.0) {
            keep(m++, i, i, 0.0);
         }

         if (di >= 0.0 != dj >= 0.0) {
            keep(m++, i, j, di / (di - dj));
         }
      }

      System.arraycopy(SX, 0, QX, 0, m);
      System.arraycopy(SY, 0, QY, 0, m);
      System.arraycopy(SZ, 0, QZ, 0, m);
      System.arraycopy(SA, 0, QA, 0, m);
      System.arraycopy(SB, 0, QB, 0, m);
      System.arraycopy(SC, 0, QC, 0, m);
      System.arraycopy(SG, 0, QG, 0, m);
      System.arraycopy(SU, 0, QU, 0, m);
      System.arraycopy(SV, 0, QV, 0, m);
      qn = m;
   }

   private static void keep(int m, int i, int j, double t) {
      SX[m] = QX[i] + (QX[j] - QX[i]) * t;
      SY[m] = QY[i] + (QY[j] - QY[i]) * t;
      SZ[m] = QZ[i] + (QZ[j] - QZ[i]) * t;
      SA[m] = QA[i] + (QA[j] - QA[i]) * t;
      SB[m] = QB[i] + (QB[j] - QB[i]) * t;
      SC[m] = QC[i] + (QC[j] - QC[i]) * t;
      SG[m] = (float)(QG[i] + (QG[j] - QG[i]) * t);
      SU[m] = QU[i] + (QU[j] - QU[i]) * t;
      SV[m] = QV[i] + (QV[j] - QV[i]) * t;
   }

   private static void mesh(Cards.Card c, int col) {
      if (qn >= 3) {
         int base = MeshBatch.stage(qn);

         for (int k = 0; k < qn; k++) {
            vertex(base + k, c, QX[k], QY[k], QZ[k], QA[k], QB[k], QC[k], col, QG[k]);
         }

         for (int var4 = 1; var4 + 1 < qn; var4++) {
            MeshBatch.tri(base, base + var4, base + var4 + 1);
         }
      }
   }

   private static void quads(Cards.Card c, int tex, int col) {
      for (int k = 1; k + 1 < qn; k++) {
         int d = k + 1;
         Vfx.quadUV(
            tex,
            c.px(QX[0], QY[0], QZ[0]),
            c.py(QX[0], QY[0], QZ[0]),
            c.pz(QX[0], QY[0], QZ[0]),
            QU[0],
            QV[0],
            col,
            c.px(QX[k], QY[k], QZ[k]),
            c.py(QX[k], QY[k], QZ[k]),
            c.pz(QX[k], QY[k], QZ[k]),
            QU[k],
            QV[k],
            col,
            c.px(QX[d], QY[d], QZ[d]),
            c.py(QX[d], QY[d], QZ[d]),
            c.pz(QX[d], QY[d], QZ[d]),
            QU[d],
            QV[d],
            col,
            c.px(QX[d], QY[d], QZ[d]),
            c.py(QX[d], QY[d], QZ[d]),
            c.pz(QX[d], QY[d], QZ[d]),
            QU[d],
            QV[d],
            col
         );
      }
   }

   public static void art(Cards.Card[] cards, int n, int design, int ink, float backShare) {
      int faceTex = CardArt.face(design);
      int backTex = CardArt.back(design);
      int symTex = CardArt.symbols();
      if (faceTex != 0) {
         for (int k = 0; k < n; k++) {
            decal(cards[k], faceTex, 0.0F, 0.0F, 1.0F, 1.0F, 0.0, 0.0, 1.0, 1.5, 1, withAlpha(ink, cards[k].alpha));
         }
      }

      if (symTex != 0) {
         for (int k = 0; k < n; k++) {
            CardArt.cell(cards[k].symbol, UV);
            decal(cards[k], symTex, UV[0], UV[1], UV[2], UV[3], 0.0, 0.1640625, 0.62, 0.62, 1, withAlpha(ink, cards[k].alpha));
         }
      }

      if (backTex != 0) {
         for (int k = 0; k < n; k++) {
            decal(cards[k], backTex, 0.0F, 0.0F, 1.0F, 1.0F, 0.0, 0.0, 1.0, 1.5, -1, withAlpha(ink, cards[k].alpha * backShare));
         }
      }
   }

   public static void decal(Cards.Card c, int tex, float u0, float v0, float u1, float v1, double cx, double cy, double w, double h, int side, int col) {
      double s = c.half * 2.0;
      if (!(s <= 1.0E-4) && col >>> 24 != 0) {
         double z = s * 0.024 * side;
         double hx = w * 0.5 * s * side;
         double hy = h * 0.5 * s;
         if (c.cut) {
            double lx = cx * s;
            double ly = cy * s;
            qn = 0;
            put(lx - hx, ly - hy, z, 0.0, 0.0, side, 0.0F, u0, v1);
            put(lx + hx, ly - hy, z, 0.0, 0.0, side, 0.0F, u1, v1);
            put(lx + hx, ly + hy, z, 0.0, 0.0, side, 0.0F, u1, v0);
            put(lx - hx, ly + hy, z, 0.0, 0.0, side, 0.0F, u0, v0);
            cutPolygon(c);
            quads(c, tex, col);
         } else {
            double ccx = c.px(cx * s, cy * s, z);
            double ccy = c.py(cx * s, cy * s, z);
            double ccz = c.pz(cx * s, cy * s, z);
            Vfx.quadUV(
               tex,
               ccx - c.rx * hx - c.ux * hy,
               ccy - c.ry * hx - c.uy * hy,
               ccz - c.rz * hx - c.uz * hy,
               u0,
               v1,
               col,
               ccx + c.rx * hx - c.ux * hy,
               ccy + c.ry * hx - c.uy * hy,
               ccz + c.rz * hx - c.uz * hy,
               u1,
               v1,
               col,
               ccx + c.rx * hx + c.ux * hy,
               ccy + c.ry * hx + c.uy * hy,
               ccz + c.rz * hx + c.uz * hy,
               u1,
               v0,
               col,
               ccx - c.rx * hx + c.ux * hy,
               ccy - c.ry * hx + c.uy * hy,
               ccz - c.rz * hx + c.uz * hy,
               u0,
               v0,
               col
            );
         }
      }
   }

   static void warmUp() {
      MASK.warm();
   }

   public static boolean burnWorks() {
      return !OptiFineCompat.shadersActive();
   }

   public static void burnHoles(Cards.Card c, float progress, int variant, Camera cam) {
      if (!(progress <= 0.0F) && !(c.half <= 1.0E-4) && burnWorks()) {
         Vfx.flush();
         GlStateManager.enableAlpha();
         GlStateManager.alphaFunc(513, Math.min(1.001F, progress));
         GlStateManager.colorMask(false, false, false, false);
         GlStateManager.depthMask(true);
         maskSquare(c, variant, cam, 2.0, -1);
         Vfx.flush();
         GlStateManager.colorMask(true, true, true, true);
         GlStateManager.alphaFunc(516, 0.1F);
         GlStateManager.disableAlpha();
      }
   }

   public static void burnEdge(Cards.Card c, float progress, float width, int variant, int color, float alpha, Camera cam) {
      if (!(progress <= 0.0F) && !(progress >= 1.0F) && !(c.half <= 1.0E-4) && burnWorks()) {
         Vfx.flush();
         GlStateManager.enableAlpha();
         GlStateManager.depthMask(false);
         GlStateManager.blendFunc(1, 1);
         GlStateManager.alphaFunc(513, progress + width);
         maskSquare(c, variant, cam, 1.0, scaled(color, 0.55F * alpha));
         Vfx.flush();
         GlStateManager.alphaFunc(513, progress + width * 0.35F);
         maskSquare(c, variant, cam, 1.0, scaled(-2858, 0.8F * alpha));
         Vfx.flush();
         GlStateManager.alphaFunc(516, 0.1F);
         GlStateManager.disableAlpha();
         RenderState.additive();
      }
   }

   private static int scaled(int color, float k) {
      k = Easing.clamp01(k);
      int r = Math.round((color >> 16 & 0xFF) * k);
      int g = Math.round((color >> 8 & 0xFF) * k);
      int b = Math.round((color & 0xFF) * k);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static void maskSquare(Cards.Card c, int variant, Camera cam, double lifts, int col) {
      int tex = mask();
      double s = c.half * 2.0;
      double toCam = (cam.posX - c.x) * c.nx + (cam.posY - c.y) * c.ny + (cam.posZ - c.z) * c.nz;
      double dist = Math.sqrt((cam.posX - c.x) * (cam.posX - c.x) + (cam.posY - c.y) * (cam.posY - c.y) + (cam.posZ - c.z) * (cam.posZ - c.z));
      double hair = Math.max(0.0015, dist * 4.0E-4);
      double z = (toCam >= 0.0 ? 1 : -1) * (c.half * 0.05 + hair * lifts);
      double hx = 0.53 * s;
      double hy = 0.78 * s;
      float m0 = -0.03F;
      float m1 = 1.03F;
      float n0 = -0.02F;
      float n1 = 1.02F;
      float u0 = (variant & 1) == 0 ? m0 : m1;
      float u1 = (variant & 1) == 0 ? m1 : m0;
      float v0 = (variant & 2) == 0 ? n0 : n1;
      float v1 = (variant & 2) == 0 ? n1 : n0;
      double cx = c.px(0.0, 0.0, z);
      double cy = c.py(0.0, 0.0, z);
      double cz = c.pz(0.0, 0.0, z);
      Vfx.quadUV(
         tex,
         cx - c.rx * hx - c.ux * hy,
         cy - c.ry * hx - c.uy * hy,
         cz - c.rz * hx - c.uz * hy,
         u0,
         v1,
         col,
         cx + c.rx * hx - c.ux * hy,
         cy + c.ry * hx - c.uy * hy,
         cz + c.rz * hx - c.uz * hy,
         u1,
         v1,
         col,
         cx + c.rx * hx + c.ux * hy,
         cy + c.ry * hx + c.uy * hy,
         cz + c.rz * hx + c.uz * hy,
         u1,
         v0,
         col,
         cx - c.rx * hx + c.ux * hy,
         cy - c.ry * hx + c.uy * hy,
         cz - c.rz * hx + c.uz * hy,
         u0,
         v0,
         col
      );
   }

   private static int mask() {
      return MASK.texture();
   }

   private static KitTextures.Image[] paintMask() {
      int[] px = new int[98304];

      for (int y = 0; y < 384; y++) {
         for (int x = 0; x < 256; x++) {
            double u = (x + 0.5) / 256.0;
            double v = (y + 0.5) / 384.0 * 1.5;
            double edge = Math.min(Math.min(u, 1.0 - u), Math.min(v, 1.5 - v)) / 0.5;
            double n = noise(u * 6.0, v * 6.0) * 0.65 + noise(u * 17.0 + 9.1, v * 17.0 + 3.7) * 0.35;
            double m = 0.6 * edge + 0.4 * n;
            int a = Math.max(3, Math.min(255, (int)Math.round(m * 255.0)));
            px[y * 256 + x] = a << 24 | 16777215;
         }
      }

      return new KitTextures.Image[]{KitTextures.Image.argb(px, 256, 384, true, false)};
   }

   private static double noise(double x, double y) {
      int ix = (int)Math.floor(x);
      int iy = (int)Math.floor(y);
      double fx = x - ix;
      double fy = y - iy;
      fx = fx * fx * (3.0 - 2.0 * fx);
      fy = fy * fy * (3.0 - 2.0 * fy);
      double a = hash(ix, iy);
      double b = hash(ix + 1, iy);
      double c = hash(ix, iy + 1);
      double d = hash(ix + 1, iy + 1);
      return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
   }

   private static double hash(int x, int y) {
      int h = x * 374761393 + y * 668265263;
      h = (h ^ h >>> 13) * 1274126177;
      return ((h ^ h >>> 16) & 65535) / 65535.0;
   }

   static {
      double w = 0.5;
      double h = 0.75;
      double r = 0.09;
      double inset = 0.055;
      double[][] centers = new double[][]{{w - r, h - r}, {-w + r, h - r}, {-w + r, -h + r}, {w - r, -h + r}};
      int i = 0;

      for (int c = 0; c < 4; c++) {
         for (int s = 0; s <= 4; s++) {
            double a = (c + s / 4.0) * Math.PI / 2.0;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            OX[i] = centers[c][0] + ca * r;
            OY[i] = centers[c][1] + sa * r;
            IX[i] = OX[i] - ca * inset;
            IY[i] = OY[i] - sa * inset;
            ONX[i] = ca;
            ONY[i] = sa;
            i++;
         }
      }

      QX = new double[24];
      QY = new double[24];
      QZ = new double[24];
      QA = new double[24];
      QB = new double[24];
      QC = new double[24];
      QU = new double[24];
      QV = new double[24];
      QG = new float[24];
      SX = new double[24];
      SY = new double[24];
      SZ = new double[24];
      SA = new double[24];
      SB = new double[24];
      SC = new double[24];
      SU = new double[24];
      SV = new double[24];
      SG = new float[24];
      MASK = new KitTextures.Painted("card burn mask", Cards::paintMask);
   }

   public static final class Card {
      public double x;
      public double y;
      public double z;
      public double rx;
      public double ry;
      public double rz;
      public double ux;
      public double uy;
      public double uz;
      public double nx;
      public double ny;
      public double nz;
      public double half;
      public float alpha = 1.0F;
      public int symbol;
      public boolean cut;
      public double cutA;
      public double cutB;
      public double cutD;

      public void cutBelow(double y) {
         this.cutLine(this.ry, this.uy, y - this.y);
      }

      public void cutLine(double a, double b, double d) {
         this.cut = true;
         this.cutA = a;
         this.cutB = b;
         this.cutD = d;
      }

      public void uncut() {
         this.cut = false;
      }

      double px(double lx, double ly, double lz) {
         return this.x + this.rx * lx + this.ux * ly + this.nx * lz;
      }

      double py(double lx, double ly, double lz) {
         return this.y + this.ry * lx + this.uy * ly + this.ny * lz;
      }

      double pz(double lx, double ly, double lz) {
         return this.z + this.rz * lx + this.uz * ly + this.nz * lz;
      }
   }

   public static enum Design implements Displayable {
      CELESTIAL("Celestial", "Stars and moons"),
      FLORAL("Floral", "Leaves and rosettes"),
      MINIMAL("Minimal", "Fine lines");

      private final String displayName;
      private final String description;

      private Design(String displayName, String description) {
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

   public static enum Foil implements Displayable {
      GOLD("Gold", Material.GOLD),
      SILVER("Silver", Material.SILVER),
      ROSE_GOLD("Rose gold", Material.ROSE_GOLD),
      CUSTOM("Custom", Material.CUSTOM);

      private final String displayName;
      public final Material material;

      private Foil(String displayName, Material material) {
         this.displayName = displayName;
         this.material = material;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this == CUSTOM ? "The Foil Tint color, metallic" : this.displayName + " leaf";
      }
   }

   public static enum Symbols implements Displayable {
      SKY("Sky", "Sun, moon, star", 0, 1, 2),
      FATE("Fate", "Eye, tower, wheel", 3, 4, 5),
      MIXED("Mixed", "All six", 0, 3, 1, 4, 2, 5);

      private final String displayName;
      private final String description;
      public final int[] set;

      private Symbols(String displayName, String description, int... set) {
         this.displayName = displayName;
         this.description = description;
         this.set = set;
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
