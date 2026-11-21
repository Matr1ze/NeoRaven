package xyz.daikirai.render.furiah.render.material;

import java.util.Arrays;
import xyz.daikirai.render.furiah.core.GameClock;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;

public final class MeshBatch {
   public static final int MAX_STAGED = 24576;
   private static final int MAX_QUAD_INDICES = 98304;
   private static final int MAX_TRI_INDICES = 49152;
   public static final float[] X = new float[24576];
   public static final float[] Y = new float[24576];
   public static final float[] Z = new float[24576];
   public static final float[] NX = new float[24576];
   public static final float[] NY = new float[24576];
   public static final float[] NZ = new float[24576];
   public static final int[] C = new int[24576];
   public static final float[] G = new float[24576];
   private static final float[] SU = new float[24576];
   private static final float[] SV = new float[24576];
   private static final int[] SC = new int[24576];
   private static final int[] SHEEN = new int[24576];
   private static final int[] QUADS = new int[98304];
   private static final int[] TRIS = new int[49152];
   private static int staged;
   private static int quads;
   private static int tris;
   private static Material material = Material.CUSTOM;
   private static Finish finish = Finish.METALLIC;
   private static float light = 1.0F;
   private static float rim;
   private static int rimColor = -1;
   private static boolean cull;
   private static boolean depthWrite;
   private static boolean open;
   private static final int[] DATA = new int[688128];
   private static final int[][] CHUNKS6 = new int[18][];
   private static final int[][] CHUNKS7 = new int[18][];
   private static boolean outline;
   private static int outlineColor;
   private static float outlineWidth;
   private static float outlineBoil;
   private static long outlineBoilStep;
   private static final float REFERENCE_HEIGHT = 1080.0F;
   private static final int HASH = 65536;
   private static int[] hashKeyX;
   private static int[] hashKeyY;
   private static int[] hashKeyZ;
   private static int[] hashUsed;
   private static int[] vertexSlot;
   private static float[] hashNX;
   private static float[] hashNY;
   private static float[] hashNZ;
   private static int hashUsedCount;
   private static final float[] HX = new float[24576];
   private static final float[] HY = new float[24576];
   private static final float[] HZ = new float[24576];
   private static final int[] PACKED = new int[172032];

   private MeshBatch() {
   }

   public static void begin(Material m, Finish f, float light, float rim, int rimColor, boolean cull, boolean depthWrite) {
      material = m == null ? Material.CUSTOM : m;
      finish = f == null ? Finish.METALLIC : f;
      MeshBatch.light = Math.max(0.0F, Math.min(1.0F, light));
      MeshBatch.rim = Math.max(0.0F, rim);
      MeshBatch.rimColor = rimColor;
      MeshBatch.cull = cull;
      MeshBatch.depthWrite = depthWrite;
      staged = 0;
      quads = 0;
      tris = 0;
      open = true;
      outline = false;
   }

   public static void outline(int color, float widthPx, float boil, float boilRate) {
      outline(color, widthPx, boil, boilRate, GameClock.nanos());
   }

   public static void outline(int color, float widthPx, float boil, float boilRate, long now) {
      outline = widthPx > 0.0F && color >>> 24 > 0;
      outlineColor = color;
      outlineWidth = widthPx;
      outlineBoil = Math.max(0.0F, Math.min(1.0F, boil));
      outlineBoilStep = boilRate > 0.0F ? (long)Math.floor(now / 1.0E9 * boilRate) : 0L;
   }

   private static void drawHull() {
      if (hashKeyX == null) {
         hashKeyX = new int[65536];
         hashKeyY = new int[65536];
         hashKeyZ = new int[65536];
         hashNX = new float[65536];
         hashNY = new float[65536];
         hashNZ = new float[65536];
         hashUsed = new int[65536];
         vertexSlot = new int[24576];
         Arrays.fill(hashKeyX, Integer.MIN_VALUE);
      }

      for (int i = 0; i < staged; i++) {
         int qx = Math.round(X[i] * 512.0F);
         int qy = Math.round(Y[i] * 512.0F);
         int qz = Math.round(Z[i] * 512.0F);
         int h = (qx * 73856093 ^ qy * 19349663 ^ qz * 83492791) & 65535;

         while (true) {
            if (hashKeyX[h] == Integer.MIN_VALUE) {
               hashKeyX[h] = qx;
               hashKeyY[h] = qy;
               hashKeyZ[h] = qz;
               hashNX[h] = NX[i];
               hashNY[h] = NY[i];
               hashNZ[h] = NZ[i];
               hashUsed[hashUsedCount++] = h;
               break;
            }

            if (hashKeyX[h] == qx && hashKeyY[h] == qy && hashKeyZ[h] == qz) {
               hashNX[h] = hashNX[h] + NX[i];
               hashNY[h] = hashNY[h] + NY[i];
               hashNZ[h] = hashNZ[h] + NZ[i];
               break;
            }

            h = h + 1 & 65535;
         }

         vertexSlot[i] = h;
      }

      Camera cam = Vfx.camera();
      double scaleY = cam.projectionScaleY > 0.1 ? cam.projectionScaleY : 1.428;
      double perDepth = outlineWidth / 1080.0F * 2.0 / scaleY;

      for (int i = 0; i < staged; i++) {
         int h = vertexSlot[i];
         float mx = hashNX[h];
         float my = hashNY[h];
         float mz = hashNZ[h];
         float ml = (float)Math.sqrt(mx * mx + my * my + mz * mz);
         float dz;
         float dy;
         float dx;
         if (ml < 1.0E-4F) {
            dx = NX[i];
            dy = NY[i];
            dz = NZ[i];
         } else {
            float var26;
            float var27;
            float var28;
            float own = Math.max(0.3F, (var26 = mx / ml) * NX[i] + (var27 = my / ml) * NY[i] + (var28 = mz / ml) * NZ[i]);
            dx = var26 / own;
            dy = var27 / own;
            dz = var28 / own;
         }

         double depth = (X[i] - cam.posX) * cam.forwardX + (Y[i] - cam.posY) * cam.forwardY + (Z[i] - cam.posZ) * cam.forwardZ;
         double w = perDepth * Math.max(0.05, depth);
         if (outlineBoil > 0.0F) {
            int k = h * 31 + (int)outlineBoilStep * -1640531535;
            k ^= k >>> 15;
            k *= -2048144789;
            k ^= k >>> 13;
            w *= 1.0 + outlineBoil * ((k >>> 8 & 65535) / 65535.0 - 0.5) * 1.1;
         }

         HX[i] = (float)(X[i] + dx * w);
         HY[i] = (float)(Y[i] + dy * w);
         HZ[i] = (float)(Z[i] + dz * w);
      }

      for (int k = 0; k < hashUsedCount; k++) {
         hashKeyX[hashUsed[k]] = Integer.MIN_VALUE;
      }

      hashUsedCount = 0;
      GlStateManager.bindTexture(Textures.WHITE);
      Vfx.resetTextureBinding();
      GlStateManager.enableCull();
      GlStateManager.cullFace(1028);

      try {
         submit(7, DefaultVertexFormats.POSITION_TEX_COLOR, packHull(QUADS, quads), 6);
         submit(4, DefaultVertexFormats.POSITION_TEX_COLOR, packHull(TRIS, tris), 6);
      } finally {
         GlStateManager.cullFace(1029);
         if (!cull) {
            GlStateManager.disableCull();
         }
      }
   }

   private static int packHull(int[] idx, int count) {
      int oa = outlineColor >>> 24;

      for (int k = 0; k < count; k++) {
         int i = idx[k];
         int o = k * 6;
         DATA[o] = Float.floatToRawIntBits(HX[i]);
         DATA[o + 1] = Float.floatToRawIntBits(HY[i]);
         DATA[o + 2] = Float.floatToRawIntBits(HZ[i]);
         DATA[o + 3] = Float.floatToRawIntBits(0.5F);
         DATA[o + 4] = Float.floatToRawIntBits(0.5F);
         int a = (C[i] >>> 24) * oa / 255;
         DATA[o + 5] = abgr(a << 24 | outlineColor & 16777215);
      }

      return count;
   }

   public static int stage(int n) {
      if (staged + n > 24576) {
         flush();
      }

      int at = staged;
      staged = staged + Math.min(n, 24576 - staged);
      return at;
   }

   public static int room() {
      return 24576 - staged;
   }

   public static void vertex(int i, double x, double y, double z, double nx, double ny, double nz, int color, float glint) {
      X[i] = (float)x;
      Y[i] = (float)y;
      Z[i] = (float)z;
      NX[i] = (float)nx;
      NY[i] = (float)ny;
      NZ[i] = (float)nz;
      C[i] = xyz.daikirai.render.furiah.setting.ThemeTint.recolor(color);
      G[i] = glint;
   }

   public static void fade(int from, int n, float k) {
      if (!(k >= 0.999F)) {
         for (int i = from; i < from + n; i++) {
            int a = Math.round((C[i] >>> 24) * Math.max(0.0F, k));
            C[i] = a << 24 | C[i] & 16777215;
         }
      }
   }

   private static boolean facesOut(int a, int b, int c) {
      float uy = Y[b] - Y[a];
      float vz = Z[c] - Z[a];
      float uz = Z[b] - Z[a];
      float vy = Y[c] - Y[a];
      float cx = uy * vz - uz * vy;
      float vx = X[c] - X[a];
      float ux = X[b] - X[a];
      float cy = uz * vx - ux * vz;
      float cz = ux * vy - uy * vx;
      return cx * (NX[a] + NX[b] + NX[c]) + cy * (NY[a] + NY[b] + NY[c]) + cz * (NZ[a] + NZ[b] + NZ[c]) >= 0.0F;
   }

   public static void tri(int a, int b, int c) {
      if (tris + 3 <= 49152) {
         TRIS[tris++] = a;
         if (facesOut(a, b, c)) {
            TRIS[tris++] = b;
            TRIS[tris++] = c;
         } else {
            TRIS[tris++] = c;
            TRIS[tris++] = b;
         }
      }
   }

   public static boolean facingOut(int a, int b, int c) {
      return facesOut(a, b, c);
   }

   public static void quad(int a, int b, int c, int d, boolean asGiven) {
      if (quads + 4 <= 98304) {
         QUADS[quads++] = a;
         if (asGiven) {
            QUADS[quads++] = b;
            QUADS[quads++] = c;
            QUADS[quads++] = d;
         } else {
            QUADS[quads++] = d;
            QUADS[quads++] = c;
            QUADS[quads++] = b;
         }
      }
   }

   public static void quad(int a, int b, int c, int d) {
      if (quads + 4 <= 98304) {
         QUADS[quads++] = a;
         if (facesOut(a, b, c)) {
            QUADS[quads++] = b;
            QUADS[quads++] = c;
            QUADS[quads++] = d;
         } else {
            QUADS[quads++] = d;
            QUADS[quads++] = c;
            QUADS[quads++] = b;
         }
      }
   }

   public static void end() {
      flush();
      open = false;
   }

   private static void flush() {
      if (open && quads + tris != 0) {
         Vfx.flush();
         boolean shader = MaterialShader.usable();
         boolean tintable = true;
         boolean glass = material.translucent(finish);
         int texture = Matcaps.texture(material, finish, shader);
         int standInTint = 0;
         GlStateManager.bindTexture(texture);
         Vfx.resetTextureBinding();
         if (cull) {
            GlStateManager.enableCull();
         }

         try {
            if (shader) {
               drawShaded(tintable, glass, standInTint);
            } else {
               drawFallback(tintable, glass, standInTint);
            }

            if (outline) {
               drawHull();
            }
         } finally {
            if (cull) {
               GlStateManager.disableCull();
            }

            Vfx.resetTextureBinding();
            staged = 0;
            quads = 0;
            tris = 0;
         }
      } else {
         staged = 0;
         quads = 0;
         tris = 0;
      }
   }

   private static void drawShaded(boolean tintable, boolean glass, int standInTint) {
      MaterialShader.begin(tintable || standInTint != 0, light, xyz.daikirai.render.furiah.setting.ThemeTint.recolor(rimColor), rim, glass);

      try {
         packStaged(standInTint);
         submitCorners(7, QUADS, quads);
         submitCorners(4, TRIS, tris);
      } finally {
         MaterialShader.end();
      }
   }

   private static void packStaged(int standInTint) {
      boolean noGlint = toon();

      for (int i = 0; i < staged; i++) {
         int o = i * 7;
         PACKED[o] = Float.floatToRawIntBits(X[i]);
         PACKED[o + 1] = Float.floatToRawIntBits(Y[i]);
         PACKED[o + 2] = Float.floatToRawIntBits(Z[i]);
         PACKED[o + 3] = Float.floatToRawIntBits(noGlint ? 0.0F : G[i]);
         PACKED[o + 4] = 0;
         PACKED[o + 5] = abgr(standInTint != 0 ? withAlpha(standInTint, C[i] >>> 24) : C[i]);
         PACKED[o + 6] = (int)(NX[i] * 127.0F) & 0xFF | ((int)(NY[i] * 127.0F) & 0xFF) << 8 | ((int)(NZ[i] * 127.0F) & 0xFF) << 16;
      }
   }

   private static void submitCorners(int mode, int[] idx, int count) {
      if (count != 0) {
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer wr = tessellator.getWorldRenderer();
         wr.begin(mode, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
         int at = 0;

         for (int k = CHUNKS7.length - 1; k >= 0; k--) {
            int n = 1 << k;
            if ((count & n) != 0) {
               int[] chunk = CHUNKS7[k];
               if (chunk == null) {
                  CHUNKS7[k] = new int[n * 7];
                  chunk = CHUNKS7[k];
               }

               for (int c = 0; c < n; c++) {
                  int s = idx[at + c] * 7;
                  int o = c * 7;
                  chunk[o] = PACKED[s];
                  chunk[o + 1] = PACKED[s + 1];
                  chunk[o + 2] = PACKED[s + 2];
                  chunk[o + 3] = PACKED[s + 3];
                  chunk[o + 4] = PACKED[s + 4];
                  chunk[o + 5] = PACKED[s + 5];
                  chunk[o + 6] = PACKED[s + 6];
               }

               wr.addVertexData(chunk);
               at += n;
            }
         }

         tessellator.draw();
      }
   }

   private static void drawFallback(boolean tintable, boolean glass, int standInTint) {
      Camera cam = Vfx.camera();
      boolean anySheen = false;

      for (int i = 0; i < staged; i++) {
         double dx = X[i] - cam.posX;
         double dy = Y[i] - cam.posY;
         double dz = Z[i] - cam.posZ;
         double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (dl < 1.0E-9) {
            dl = 1.0E-9;
         }

         double uz;
         double nz;
         double uy;
         double ny;
         double ux;
         double nx;
         double nu;
         if ((
               nu = (nx = NX[i] * cam.rightX + NY[i] * cam.rightY + NZ[i] * cam.rightZ) * (ux = (dx * cam.rightX + dy * cam.rightY + dz * cam.rightZ) / dl)
                  + (ny = NX[i] * cam.upX + NY[i] * cam.upY + NZ[i] * cam.upZ) * (uy = (dx * cam.upX + dy * cam.upY + dz * cam.upZ) / dl)
                  + (nz = -(NX[i] * cam.forwardX + NY[i] * cam.forwardY + NZ[i] * cam.forwardZ))
                     * (uz = -(dx * cam.forwardX + dy * cam.forwardY + dz * cam.forwardZ) / dl)
            )
            > 0.0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
            nu = -nu;
         }

         double rx = ux - 2.0 * nu * nx;
         double ry = uy - 2.0 * nu * ny;
         double rz = uz - 2.0 * nu * nz;
         double m = 2.0 * Math.sqrt(rx * rx + ry * ry + (rz + 1.0) * (rz + 1.0));
         SU[i] = (float)(rx / m + 0.5);
         SV[i] = (float)(ry / m + 0.5);
         float facing = (float)Math.min(1.0, -nu);
         float edge = 1.0F - facing;
         int c = C[i];
         int alpha = c >>> 24;
         if (glass) {
            alpha = Math.round(alpha * (0.3F + 0.65F * edge * edge));
         }

         int base = standInTint != 0 ? standInTint : (tintable ? c : -1);
         float r = (base >> 16 & 0xFF) / 255.0F * light;
         float g = (base >> 8 & 0xFF) / 255.0F * light;
         float b = (base & 0xFF) / 255.0F * light;
         SC[i] = alpha << 24
            | Math.round(Math.min(1.0F, r) * 255.0F) << 16
            | Math.round(Math.min(1.0F, g) * 255.0F) << 8
            | Math.round(Math.min(1.0F, b) * 255.0F);
         float rimA = rim * edge * edge * edge;
         float glint = toon() ? 0.0F : G[i] * (0.35F + 0.65F * facing) * Math.max(light, 0.35F);
         float sr = (rimColor >> 16 & 0xFF) / 255.0F * rimA + glint;
         float sb;
         float sg;
         float s = Math.max(sr, Math.max(sg = (rimColor >> 8 & 0xFF) / 255.0F * rimA + glint, sb = (rimColor & 0xFF) / 255.0F * rimA + glint));
         if (s > 0.004F && alpha > 0) {
            anySheen = true;
            SHEEN[i] = alpha << 24
               | Math.round(Math.min(1.0F, sr) * 255.0F) << 16
               | Math.round(Math.min(1.0F, sg) * 255.0F) << 8
               | Math.round(Math.min(1.0F, sb) * 255.0F);
         } else {
            SHEEN[i] = 0;
         }
      }

      submit(7, DefaultVertexFormats.POSITION_TEX_COLOR, packMapped(QUADS, quads), 6);
      submit(4, DefaultVertexFormats.POSITION_TEX_COLOR, packMapped(TRIS, tris), 6);
      if (anySheen) {
         GlStateManager.bindTexture(Textures.WHITE);
         Vfx.resetTextureBinding();
         GlStateManager.tryBlendFuncSeparate(770, 1, 1, 771);
         GlStateManager.depthMask(false);
         submit(7, DefaultVertexFormats.POSITION_TEX_COLOR, packSheen(QUADS, quads, 4), 6);
         submit(4, DefaultVertexFormats.POSITION_TEX_COLOR, packSheen(TRIS, tris, 3), 6);
         RenderState.translucent(depthWrite);
      }
   }

   private static int packMapped(int[] idx, int count) {
      for (int k = 0; k < count; k++) {
         int i = idx[k];
         int o = k * 6;
         DATA[o] = Float.floatToRawIntBits(X[i]);
         DATA[o + 1] = Float.floatToRawIntBits(Y[i]);
         DATA[o + 2] = Float.floatToRawIntBits(Z[i]);
         DATA[o + 3] = Float.floatToRawIntBits(SU[i]);
         DATA[o + 4] = Float.floatToRawIntBits(SV[i]);
         DATA[o + 5] = abgr(SC[i]);
      }

      return count;
   }

   private static int packSheen(int[] idx, int count, int corners) {
      int n = 0;
      int k = 0;

      while (k + corners <= count) {
         int any = 0;

         for (int j = 0; j < corners; j++) {
            any |= SHEEN[idx[k + j]];
         }

         if (any != 0) {
            for (int var9 = 0; var9 < corners; var9++) {
               int i = idx[k + var9];
               int o = n * 6;
               DATA[o] = Float.floatToRawIntBits(X[i]);
               DATA[o + 1] = Float.floatToRawIntBits(Y[i]);
               DATA[o + 2] = Float.floatToRawIntBits(Z[i]);
               DATA[o + 3] = Float.floatToRawIntBits(0.5F);
               DATA[o + 4] = Float.floatToRawIntBits(0.5F);
               DATA[o + 5] = abgr(SHEEN[i]);
               n++;
            }
         }

         k += corners;
      }

      return n;
   }

   private static void submit(int mode, VertexFormat format, int vertices, int intsPerVertex) {
      if (vertices != 0) {
         Tessellator tessellator = Tessellator.getInstance();
         WorldRenderer wr = tessellator.getWorldRenderer();
         wr.begin(mode, format);
         int[][] chunks = intsPerVertex == 7 ? CHUNKS7 : CHUNKS6;
         int offset = 0;

         for (int k = chunks.length - 1; k >= 0; k--) {
            int n = 1 << k;
            if ((vertices & n) != 0) {
               int[] chunk = chunks[k];
               if (chunk == null) {
                  chunks[k] = new int[n * intsPerVertex];
                  chunk = chunks[k];
               }

               System.arraycopy(DATA, offset, chunk, 0, n * intsPerVertex);
               wr.addVertexData(chunk);
               offset += n * intsPerVertex;
            }
         }

         tessellator.draw();
      }
   }

   private static boolean toon() {
      return finish == Finish.TOON && material == Material.CUSTOM;
   }

   private static int abgr(int c) {
      return c & -16711936 | c >> 16 & 0xFF | (c & 0xFF) << 16;
   }

   private static int withAlpha(int rgb, int alpha) {
      return alpha << 24 | rgb & 16777215;
   }

   public static String stateForTest() {
      return MaterialShader.stateForTest() + ", matcaps " + Matcaps.stateForTest();
   }

   public static void forceFallbackForTest(Boolean on) {
      MaterialShader.forceFallbackForTest(on);
   }
}
