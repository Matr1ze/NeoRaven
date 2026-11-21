package xyz.daikirai.render.furiah.render;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import xyz.daikirai.render.furiah.render.look.BodyLook;
import xyz.daikirai.render.furiah.render.look.BodyLookShader;
import xyz.daikirai.render.furiah.render.look.EraseShader;
import xyz.daikirai.render.furiah.render.look.ItemLookShader;
import xyz.daikirai.render.furiah.render.look.LookTextures;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Matcaps;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class BodyProxy {
   public static final int PARTS = 6;
   public static final int DETAIL_PLATES = 1;
   private static final double[] FX = new double[6];
   private static final double[] FY = new double[6];
   private static final double[] FZ = new double[6];
   private static final double[] FR = new double[54];
   private static final float[] C = new float[3];
   private static final float[] UV = new float[4];
   private static final double[] WX = new double[8];
   private static final double[] WY = new double[8];
   private static final double[] WZ = new double[8];
   private static final double[] PX = new double[12];
   private static final double[] PY = new double[12];
   private static final double[] PZ = new double[12];
   private static final double[] PU = new double[12];
   private static final double[] PV = new double[12];
   private static final double[] QX = new double[12];
   private static final double[] QY = new double[12];
   private static final double[] QZ = new double[12];
   private static final double[] QU = new double[12];
   private static final double[] QV = new double[12];
   private static final double[] L0 = normalize(0.2, 1.0, -0.7);
   private static final double[] L1 = normalize(-0.2, 1.0, 0.7);
   private static final double[] CAPE_C = new double[3];
   private static final double[] CAPE_R = new double[9];
   private static final BodyProxy.Part CAPE_PART = new BodyProxy.Part();
   private static final Map<String, ResourceLocation> ARMOR_TEXTURES = new HashMap<>();
   private static final BodyProxy.Look GLOW_LOOK = new BodyProxy.Look();
   private static final double[] SX = new double[12];
   private static final double[] SY = new double[12];
   private static final double[] SZ = new double[12];
   private static final double[] SA = new double[12];
   private static final int[][] EDGES = new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
   private static final int L_DEPTH = 0;
   private static final int L_LOOK = 1;
   private static final int L_HATCH = 2;
   private static final int L_EMBERS = 3;
   private static final int L_HULL = 4;
   private static final int L_MATCAP = 5;
   private static final int[] PARAMS = new int[11];
   private static final BodyProxy.Look GLITCH_LOOK = new BodyProxy.Look();
   private static final BodyProxy.Part[] GLITCH_PARTS = newParts();
   private static double hullPerDepth;
   private static long hullBoilStep;
   private static int hullPart;
   private static final float[] SUV = new float[2];
   private static boolean lookSweep;
   private static boolean derivedReady;
   private static final int PLATE_PX = 64;
   private static final double PLATE = 0.234375;
   private static int platesTexture;
   private static float materialTilt;

   private BodyProxy() {
   }

   public static void rest(BodyProxy.Snapshot s, BodyProxy.Part[] parts) {
      PlayerModel.frames(s.pose, FX, FY, FZ, FR);

      for (int i = 0; i < 6; i++) {
         BodyProxy.Part p = parts[i];
         p.x = FX[i];
         p.y = FY[i];
         p.z = FZ[i];
         System.arraycopy(FR, i * 9, p.r, 0, 9);
         p.scale = 1.0F;
         p.stretch = 1.0F;
         p.alpha = 1.0F;
         p.visible = true;
         p.clip = false;
         p.clip2 = false;
         p.capColor = 0;
      }
   }

   public static BodyProxy.Part[] newParts() {
      BodyProxy.Part[] parts = new BodyProxy.Part[6];

      for (int i = 0; i < 6; i++) {
         parts[i] = new BodyProxy.Part();
      }

      return parts;
   }

   private static double[] normalize(double x, double y, double z) {
      double l = Math.sqrt(x * x + y * y + z * z);
      return new double[]{x / l, y / l, z / l};
   }

   public static void draw(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, double offX, double offY, double offZ) {
      if (s != null && s.valid && !(look.alpha <= 0.003F)) {
         if (look.kind == BodyLook.SKIN && look.sweepTo == null) {
            Minecraft mc = Minecraft.getMinecraft();
            boolean fading = look.alpha < 0.999F;

            for (int i = 0; i < 6 && !fading; i++) {
               fading = parts[i].visible && parts[i].alpha < 0.999F;
            }

            boolean shader = look.dissolve >= 0.0F && DissolveShader.available();
            boolean lightmap = look.lit && look.lightmap;
            Vfx.flush();
            if (lightmap) {
               mc.entityRenderer.enableLightmap();
               OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, s.light & 65535, s.light >>> 16);
            }

            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, shader ? 0.0F : 0.1F);

            try {
               if (fading) {
                  RenderState.translucent(true);
                  GlStateManager.colorMask(false, false, false, false);
                  layers(s, parts, look, offX, offY, offZ, false, true);
                  Vfx.flush();
                  GlStateManager.colorMask(true, true, true, true);
                  RenderState.translucent(false);
               } else {
                  RenderState.translucent(true);
               }

               if (shader) {
                  DissolveShader.begin(look, s.pose.x - offX, s.pose.y - offY, s.pose.z - offZ);
               }

               layers(s, parts, look, offX, offY, offZ, shader, false);
               Vfx.flush();
               if (shader) {
                  DissolveShader.end();
               }

               if (look.glowAmount > 0.003F && look.dissolve < 0.0F) {
                  RenderState.additive();
                  GlStateManager.depthFunc(515);
                  glowPass(s, parts, look, offX, offY, offZ);
                  Vfx.flush();
               }
            } finally {
               GlStateManager.alphaFunc(516, 0.1F);
               GlStateManager.disableAlpha();
               if (lightmap) {
                  Vfx.flush();
                  mc.entityRenderer.disableLightmap();
               }

               RenderState.translucent(false);
            }

            caps(s, parts, look, offX, offY, offZ);
            if (look.heldItem && s.held != null && look.alpha > 0.6F && look.dissolve <= 0.0F) {
               BodyProxy.Part arm = parts[2];
               if (arm.visible && arm.alpha > 0.6F && !arm.clip && !arm.clip2 && arm.stretch == 1.0F) {
                  heldItem(mc, s, arm, offX, offY, offZ);
               }
            }
         } else {
            drawLooks(s, parts, look, offX, offY, offZ);
         }
      }
   }

   private static void layers(
      BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, double offX, double offY, double offZ, boolean sweep, boolean depthOnly
   ) {
      int skin = texture(s.skin != null ? s.skin : DefaultPlayerSkin.getDefaultSkin(s.id != null ? s.id : new UUID(0L, 0L)));

      for (int i = 0; i < 6; i++) {
         BodyProxy.Part p = parts[i];
         if (p.visible && !(p.alpha <= 0.003F)) {
            box(skin, PlayerModel.base(i, s.slim), p, 0.0F, 0.0F, s, look, offX, offY, offZ, -1, sweep, depthOnly);
            if (look.wear) {
               box(skin, PlayerModel.wear(i, s.slim), p, 0.0F, 0.0F, s, look, offX, offY, offZ, -1, sweep, depthOnly);
            }
         }
      }

      if (look.armor) {
         for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = s.armor[slot];
            if (stack != null && stack.getItem() instanceof ItemArmor) {
               armorSlot(s, parts, look, stack, slot, offX, offY, offZ, sweep, depthOnly);
            }
         }
      }

      if (look.cape && s.cape != null) {
         BodyProxy.Part body = parts[1];
         if (body.visible && body.alpha > 0.003F && !body.clip && !body.clip2) {
            PlayerModel.capeFrame(body.r, body.x, body.y, body.z, look.capeTilt, s.pose.sneaking, CAPE_C, CAPE_R);
            BodyProxy.Part cape = CAPE_PART;
            cape.copy(body);
            cape.x = CAPE_C[0];
            cape.y = CAPE_C[1];
            cape.z = CAPE_C[2];
            System.arraycopy(CAPE_R, 0, cape.r, 0, 9);
            Vfx.flush();
            box(texture(s.cape), PlayerModel.CAPE, cape, 0.0F, 0.0F, s, look, offX, offY, offZ, -1, sweep, depthOnly);
         }
      }
   }

   private static void armorSlot(
      BodyProxy.Snapshot s,
      BodyProxy.Part[] parts,
      BodyProxy.Look look,
      ItemStack stack,
      int slot,
      double offX,
      double offY,
      double offZ,
      boolean sweep,
      boolean depthOnly
   ) {
      ItemArmor item = (ItemArmor)stack.getItem();
      boolean legs = slot == 1;
      int forgeSlot = legs ? 2 : 1;
      ResourceLocation tex = armorTexture(s, stack, forgeSlot, legs, null);
      int color = item.getColor(stack);
      int tint = color == -1 ? -1 : 0xFF000000 | color;

      for (int pass = 0; pass < (color == -1 ? 1 : 2); pass++) {
         ResourceLocation t = pass == 0 ? tex : armorTexture(s, stack, forgeSlot, legs, "overlay");
         int id = texture(t);
         Vfx.flush();

         for (int i = 0; i < 6; i++) {
            BodyProxy.Part p = parts[i];
            if (p.visible && !(p.alpha <= 0.003F) && PlayerModel.armorCovers(slot, i)) {
               PlayerModel.Box b = PlayerModel.armor(i, legs);
               float dx = PlayerModel.armorOffsetX(i, s.slim);
               float dy = PlayerModel.armorOffsetY(i, s.slim);
               box(id, b, p, dx, dy, s, look, offX, offY, offZ, pass == 0 ? tint : -1, sweep, depthOnly);
               if (slot == 3 && i == 0) {
                  box(id, PlayerModel.HELMET_OVERLAY, p, dx, dy, s, look, offX, offY, offZ, pass == 0 ? tint : -1, sweep, depthOnly);
               }
            }
         }
      }
   }

   private static ResourceLocation armorTexture(BodyProxy.Snapshot s, ItemStack stack, int slot, boolean legs, String type) {
      ItemArmor item = (ItemArmor)stack.getItem();
      String texture = item.getArmorMaterial().getName();
      String domain = "minecraft";
      int idx = texture.indexOf(58);
      if (idx != -1) {
         domain = texture.substring(0, idx);
         texture = texture.substring(idx + 1);
      }

      String path = String.format("%s:textures/models/armor/%s_layer_%d%s.png", domain, texture, legs ? 2 : 1, type == null ? "" : "_" + type);
      ResourceLocation r = ARMOR_TEXTURES.get(path);
      if (r == null) {
         r = new ResourceLocation(path);
         ARMOR_TEXTURES.put(path, r);
      }

      return r;
   }

   public static int texture(ResourceLocation r) {
      Minecraft mc = Minecraft.getMinecraft();
      ITextureObject t = mc.getTextureManager().getTexture(r);
      if (t == null) {
         mc.getTextureManager().bindTexture(r);
         Vfx.resetTextureBinding();
         t = mc.getTextureManager().getTexture(r);
      }

      return t == null ? Textures.WHITE : t.getGlTextureId();
   }

   private static void box(
      int texture,
      PlayerModel.Box b,
      BodyProxy.Part p,
      float dx,
      float dy,
      BodyProxy.Snapshot s,
      BodyProxy.Look look,
      double offX,
      double offY,
      double offZ,
      int tint,
      boolean sweep,
      boolean depthOnly
   ) {
      float bcx = b.cx() - dx;
      float bcy = b.cy() - dy;
      float bcz = b.cz();
      double k = 0.05859375 * p.scale;

      for (int c = 0; c < 8; c++) {
         PlayerModel.corner(b, c, C);
         double lx = (C[0] - bcx) * k;
         double ly = (C[1] - bcy) * k;
         double lz = (C[2] - bcz) * k;
         double wx = p.x + p.r[0] * lx + p.r[1] * ly + p.r[2] * lz;
         double wy = p.y + p.r[3] * lx + p.r[4] * ly + p.r[5] * lz;
         double wz = p.z + p.r[6] * lx + p.r[7] * ly + p.r[8] * lz;
         if (p.stretch != 1.0F) {
            double d = (wx - p.ox) * p.sx + (wy - p.oy) * p.sy + (wz - p.oz) * p.sz;
            double e = d * (p.stretch - 1.0F);
            wx += p.sx * e;
            wy += p.sy * e;
            wz += p.sz * e;
         }

         WX[c] = wx;
         WY[c] = wy;
         WZ[c] = wz;
      }

      Camera cam = Vfx.camera();
      float alpha = look.alpha * p.alpha;

      for (int f = 0; f < 6; f++) {
         int[] fc = PlayerModel.FACE_CORNERS[f];
         double ay = WY[fc[1]] - WY[fc[0]];
         double bz = WZ[fc[3]] - WZ[fc[0]];
         double az = WZ[fc[1]] - WZ[fc[0]];
         double by = WY[fc[3]] - WY[fc[0]];
         double nx = ay * bz - az * by;
         double mx = (WX[fc[0]] + WX[fc[2]]) * 0.5;
         double cx8 = (WX[0] + WX[6]) * 0.5;
         double bx = WX[fc[3]] - WX[fc[0]];
         double ax = WX[fc[1]] - WX[fc[0]];
         double ny = az * bx - ax * bz;
         double my = (WY[fc[0]] + WY[fc[2]]) * 0.5;
         double cy8 = (WY[0] + WY[6]) * 0.5;
         double nz = ax * by - ay * bx;
         double mz = (WZ[fc[0]] + WZ[fc[2]]) * 0.5;
         double cz8 = (WZ[0] + WZ[6]) * 0.5;
         if (nx * (mx - cx8) + ny * (my - cy8) + nz * (mz - cz8) < 0.0) {
            nx = -nx;
            ny = -ny;
            nz = -nz;
         }

         double nl;
         if (!((nl = Math.sqrt(nx * nx + ny * ny + nz * nz)) < 1.0E-12)
            && (
               !((nx = nx / nl) * (mx - offX - cam.posX) + (ny = ny / nl) * (my - offY - cam.posY) + (nz = nz / nl) * (mz - offZ - cam.posZ) > 0.0)
                  || p.clip
                  || p.clip2
            )) {
            PlayerModel.faceUV(b, f, UV);
            double u1 = UV[0] / b.texW;
            double v1 = UV[1] / b.texH;
            double u2 = UV[2] / b.texW;
            double v2 = UV[3] / b.texH;
            int n = 4;

            for (int k2 = 0; k2 < 4; k2++) {
               int c = fc[k2];
               PX[k2] = WX[c];
               PY[k2] = WY[c];
               PZ[k2] = WZ[c];
               PU[k2] = k2 != 0 && k2 != 3 ? u1 : u2;
               PV[k2] = k2 <= 1 ? v1 : v2;
            }

            if ((!p.clip || (n = clip(n, p.cnx, p.cny, p.cnz, p.cd)) >= 3) && (!p.clip2 || (n = clip(n, p.c2x, p.c2y, p.c2z, p.c2d)) >= 3)) {
               int color = depthOnly ? -1 : faceColor(look, tint, nx, ny, nz, alpha);
               emit(texture, n, color, look, sweep, offX, offY, offZ);
            }
         }
      }
   }

   private static int faceColor(BodyProxy.Look look, int tint, double nx, double ny, double nz, float alpha) {
      float shade = 1.0F;
      if (look.lit) {
         double d0 = Math.max(0.0, nx * L0[0] + ny * L0[1] + nz * L0[2]);
         double d1 = Math.max(0.0, nx * L1[0] + ny * L1[1] + nz * L1[2]);
         shade = (float)Math.min(1.0, 0.4 + 0.6 * (d0 + d1));
      }

      float r = (tint >> 16 & 0xFF) / 255.0F;
      float g = (tint >> 8 & 0xFF) / 255.0F;
      float b = (tint & 0xFF) / 255.0F;
      if (look.tintAmount > 0.0F) {
         float k = look.tintAmount;
         r *= 1.0F + ((look.tint >> 16 & 0xFF) / 255.0F - 1.0F) * k;
         g *= 1.0F + ((look.tint >> 8 & 0xFF) / 255.0F - 1.0F) * k;
         b *= 1.0F + ((look.tint & 0xFF) / 255.0F - 1.0F) * k;
      }

      int ri = Math.min(255, Math.round(r * shade * 255.0F));
      int gi = Math.min(255, Math.round(g * shade * 255.0F));
      int bi = Math.min(255, Math.round(b * shade * 255.0F));
      int ai = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      return ai << 24 | ri << 16 | gi << 8 | bi;
   }

   private static void emit(int texture, int n, int color, BodyProxy.Look look, boolean sweep, double offX, double offY, double offZ) {
      for (int k = 1; k + 1 < n; k++) {
         int a = 0;
         int c = k + 1;
         Vfx.quadUV(
            texture,
            PX[a] - offX,
            PY[a] - offY,
            PZ[a] - offZ,
            PU[a],
            PV[a],
            sweep ? sweepColor(look, color, a) : color,
            PX[k] - offX,
            PY[k] - offY,
            PZ[k] - offZ,
            PU[k],
            PV[k],
            sweep ? sweepColor(look, color, k) : color,
            PX[c] - offX,
            PY[c] - offY,
            PZ[c] - offZ,
            PU[c],
            PV[c],
            sweep ? sweepColor(look, color, c) : color,
            PX[c] - offX,
            PY[c] - offY,
            PZ[c] - offZ,
            PU[c],
            PV[c],
            sweep ? sweepColor(look, color, c) : color
         );
      }
   }

   private static int sweepColor(BodyProxy.Look look, int color, int i) {
      double d = PX[i] * look.sweepX + PY[i] * look.sweepY + PZ[i] * look.sweepZ;
      double t = (d - look.sweepFrom) / Math.max(0.001, look.sweepLength);
      int a = 1 + (int)Math.round(254.0 * Math.max(0.0, Math.min(1.0, t)));
      return color & 16777215 | a << 24;
   }

   private static int clip(int n, double nx, double ny, double nz, double d) {
      int m = 0;

      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         double di = PX[i] * nx + PY[i] * ny + PZ[i] * nz - d;
         double dj = PX[j] * nx + PY[j] * ny + PZ[j] * nz - d;
         if (di >= 0.0) {
            put(m++, i, i, 0.0);
         }

         if (di >= 0.0 != dj >= 0.0) {
            double t = di / (di - dj);
            put(m++, i, j, t);
         }
      }

      for (int var18 = 0; var18 < m; var18++) {
         PX[var18] = QX[var18];
         PY[var18] = QY[var18];
         PZ[var18] = QZ[var18];
         PU[var18] = QU[var18];
         PV[var18] = QV[var18];
      }

      return m;
   }

   private static void put(int m, int i, int j, double t) {
      QX[m] = PX[i] + (PX[j] - PX[i]) * t;
      QY[m] = PY[i] + (PY[j] - PY[i]) * t;
      QZ[m] = PZ[i] + (PZ[j] - PZ[i]) * t;
      QU[m] = PU[i] + (PU[j] - PU[i]) * t;
      QV[m] = PV[i] + (PV[j] - PV[i]) * t;
   }

   private static void glowPass(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, double offX, double offY, double offZ) {
      BodyProxy.Look g = GLOW_LOOK;
      g.alpha = look.alpha * look.glowAmount;
      g.lit = false;
      g.tint = look.glow;
      g.tintAmount = 1.0F;
      g.armor = look.armor;
      g.wear = look.wear;
      g.cape = look.cape;
      int skin = texture(s.skin != null ? s.skin : DefaultPlayerSkin.getDefaultSkin(s.id != null ? s.id : new UUID(0L, 0L)));

      for (int i = 0; i < 6; i++) {
         BodyProxy.Part p = parts[i];
         if (p.visible && p.alpha > 0.003F) {
            box(skin, PlayerModel.base(i, s.slim), p, 0.0F, 0.0F, s, g, offX, offY, offZ, -1, false, false);
         }
      }
   }

   private static void caps(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, double offX, double offY, double offZ) {
      boolean any = false;

      for (BodyProxy.Part p : parts) {
         any |= p.visible && (p.clip || p.clip2) && p.capColor != 0 && p.alpha > 0.003F;
      }

      if (any) {
         RenderState.additive();

         for (int i = 0; i < 6; i++) {
            BodyProxy.Part p = parts[i];
            if (p.visible && p.capColor != 0 && !(p.alpha <= 0.003F)) {
               PlayerModel.Box b = PlayerModel.base(i, s.slim);
               if (p.clip) {
                  capOf(p, b, look, offX, offY, offZ, p.cnx, p.cny, p.cnz, p.cd, p.clip2, p.c2x, p.c2y, p.c2z, p.c2d);
               }

               if (p.clip2) {
                  capOf(p, b, look, offX, offY, offZ, p.c2x, p.c2y, p.c2z, p.c2d, p.clip, p.cnx, p.cny, p.cnz, p.cd);
               }
            }
         }

         Vfx.flush();
      }
   }

   private static void capOf(
      BodyProxy.Part p,
      PlayerModel.Box b,
      BodyProxy.Look look,
      double offX,
      double offY,
      double offZ,
      double pnx,
      double pny,
      double pnz,
      double pd,
      boolean other,
      double onx,
      double ony,
      double onz,
      double od
   ) {
      double k = 0.05859375 * p.scale;

      for (int c = 0; c < 8; c++) {
         PlayerModel.corner(b, c, C);
         double lx = (C[0] - b.cx()) * k;
         double ly = (C[1] - b.cy()) * k;
         double lz = (C[2] - b.cz()) * k;
         WX[c] = p.x + p.r[0] * lx + p.r[1] * ly + p.r[2] * lz;
         WY[c] = p.y + p.r[3] * lx + p.r[4] * ly + p.r[5] * lz;
         WZ[c] = p.z + p.r[6] * lx + p.r[7] * ly + p.r[8] * lz;
      }

      int n = 0;

      for (int[] e : EDGES) {
         double da = WX[e[0]] * pnx + WY[e[0]] * pny + WZ[e[0]] * pnz - pd;
         double db;
         if (da >= 0.0 != (db = WX[e[1]] * pnx + WY[e[1]] * pny + WZ[e[1]] * pnz - pd) >= 0.0) {
            double t = da / (da - db);
            SX[n] = WX[e[0]] + (WX[e[1]] - WX[e[0]]) * t;
            SY[n] = WY[e[0]] + (WY[e[1]] - WY[e[0]]) * t;
            SZ[n] = WZ[e[0]] + (WZ[e[1]] - WZ[e[0]]) * t;
            n++;
         }
      }

      if (n >= 3) {
         double cx = 0.0;
         double cy = 0.0;
         double cz = 0.0;

         for (int i2 = 0; i2 < n; i2++) {
            cx += SX[i2];
            cy += SY[i2];
            cz += SZ[i2];
         }

         cx /= n;
         cy /= n;
         cz /= n;
         double uz;
         double uy;
         double ux;
         if (Math.abs(pny) < 0.9) {
            ux = pnz;
            uy = 0.0;
            uz = -pnx;
         } else {
            ux = 0.0;
            uy = -pnz;
            uz = pny;
         }

         double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
         double var59;
         double var60;
         double vx = pny * (var59 = uz / ul) - pnz * (var60 = uy / ul);
         double var61;
         double vy = pnz * (var61 = ux / ul) - pnx * var59;
         double vz = pnx * var60 - pny * var61;

         for (int i = 0; i < n; i++) {
            double dx = SX[i] - cx;
            double dy = SY[i] - cy;
            double dz = SZ[i] - cz;
            SA[i] = Math.atan2(dx * vx + dy * vy + dz * vz, dx * var61 + dy * var60 + dz * var59);
         }

         for (int var56 = 1; var56 < n; var56++) {
            for (int j = var56; j > 0 && SA[j] < SA[j - 1]; j--) {
               swap(j, j - 1);
            }
         }

         if (other) {
            for (int var57 = 0; var57 < n; var57++) {
               PX[var57] = SX[var57];
               PY[var57] = SY[var57];
               PZ[var57] = SZ[var57];
               PU[var57] = 0.0;
               PV[var57] = 0.0;
            }

            if ((n = clip(n, onx, ony, onz, od)) < 3) {
               return;
            }

            cz = 0.0;
            cy = 0.0;
            cx = 0.0;

            for (int var58 = 0; var58 < n; var58++) {
               SX[var58] = PX[var58];
               SY[var58] = PY[var58];
               SZ[var58] = PZ[var58];
               cx += SX[var58];
               cy += SY[var58];
               cz += SZ[var58];
            }

            cx /= n;
            cy /= n;
            cz /= n;
         }

         int color = ColorUtil.mulAlpha(p.capColor, look.alpha * p.alpha);
         int core = ColorUtil.mulAlpha(ColorUtil.core(p.capColor), look.alpha * p.alpha);

         for (int i3 = 0; i3 < n; i3++) {
            int j = (i3 + 1) % n;
            Vfx.quadUV(
               Textures.WHITE,
               cx - offX,
               cy - offY,
               cz - offZ,
               0.5,
               0.5,
               core,
               SX[i3] - offX,
               SY[i3] - offY,
               SZ[i3] - offZ,
               0.5,
               0.5,
               color,
               SX[j] - offX,
               SY[j] - offY,
               SZ[j] - offZ,
               0.5,
               0.5,
               color,
               SX[j] - offX,
               SY[j] - offY,
               SZ[j] - offZ,
               0.5,
               0.5,
               color
            );
         }
      }
   }

   private static void swap(int a, int b) {
      double t = SX[a];
      SX[a] = SX[b];
      SX[b] = t;
      t = SY[a];
      SY[a] = SY[b];
      SY[b] = t;
      t = SZ[a];
      SZ[a] = SZ[b];
      SZ[b] = t;
      t = SA[a];
      SA[a] = SA[b];
      SA[b] = t;
   }

   public static boolean sweepCrossfades() {
      return !BodyLookShader.usable();
   }

   private static void drawLooks(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, double offX, double offY, double offZ) {
      Minecraft mc = Minecraft.getMinecraft();
      BodyLook a = look.kind;
      BodyLook b = look.sweepTo;
      float t = b == null ? 0.0F : Math.max(0.0F, Math.min(1.0F, look.sweep));
      if (b == null || t <= 0.0F) {
         one(s, parts, look, a, 0, 1.0F, offX, offY, offZ);
      } else if (t >= 1.0F) {
         one(s, parts, look, b, 0, 1.0F, offX, offY, offZ);
      } else if (BodyLookShader.usable() && !look.crossfade) {
         one(s, parts, look, a, -1, 1.0F, offX, offY, offZ);
         one(s, parts, look, b, 1, 1.0F, offX, offY, offZ);
      } else {
         one(s, parts, look, a, 0, 1.0F - t, offX, offY, offZ);
         one(s, parts, look, b, 0, t, offX, offY, offZ);
      }

      BodyLook itemLook = b != null && t >= 0.5F ? b : a;
      if (look.heldItem && s.held != null && look.alpha > 0.6F && look.dissolve <= 0.0F && itemLook != BodyLook.GLITCH) {
         BodyProxy.Part arm = parts[2];
         if (arm.visible && arm.alpha > 0.6F && !arm.clip && !arm.clip2 && arm.stretch == 1.0F) {
            boolean shaded = itemLook != BodyLook.SKIN && ItemLookShader.usable();
            heldItem(mc, s, arm, offX, offY, offZ, shaded ? itemLook : null, look);
         }
      }
   }

   private static void one(
      BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, BodyLook kind, int side, float share, double offX, double offY, double offZ
   ) {
      float alpha = look.alpha * share;
      if (!(alpha <= 0.003F)) {
         if (kind == BodyLook.GLITCH) {
            glitch(s, parts, look, alpha, offX, offY, offZ);
         } else if (kind == BodyLook.MATERIAL && side == 0) {
            material(s, parts, look, alpha, offX, offY, offZ);
         } else {
            boolean dissolve = look.dissolve >= 0.0F && side == 0;
            if (dissolve && !DissolveShader.available()) {
               alpha *= 1.0F - Math.max(0.0F, Math.min(1.0F, look.dissolve));
               dissolve = false;
               if (alpha <= 0.003F) {
                  return;
               }
            }

            boolean erase = look.eraseStrokes > 0 && side == 0 && !dissolve;
            if (erase && !EraseShader.available()) {
               float gone = 0.0F;

               for (int i = 0; i < Math.min(3, look.eraseStrokes); i++) {
                  gone += look.eraseDone[i] >= 1.0F ? 1.0F : 0.0F;
               }

               alpha *= 1.0F - gone / Math.min(3, look.eraseStrokes);
               erase = false;
               if (alpha <= 0.003F) {
                  return;
               }
            }

            Minecraft mc = Minecraft.getMinecraft();
            boolean lightmap = kind == BodyLook.SKIN && look.lit && look.lightmap;
            if (dissolve) {
               dissolving(s, parts, look, kind, alpha, lightmap, offX, offY, offZ);
            } else if (erase) {
               erasing(s, parts, look, kind, alpha, lightmap, offX, offY, offZ);
            } else {
               boolean fading = alpha < 0.999F || kind.seeThrough();

               for (int i = 0; i < 6 && !fading; i++) {
                  fading = parts[i].visible && parts[i].alpha < 0.999F;
               }

               Camera cam = Vfx.camera();
               double scaleY = cam.projectionScaleY > 0.1 ? cam.projectionScaleY : 1.428;
               hullPerDepth = look.outlineWidth / 1080.0 * 2.0 / scaleY;
               hullBoilStep = look.outlineBoilRate > 0.0F ? (long)Math.floor(look.now / 1.0E9 * look.outlineBoilRate) : 0L;
               Vfx.flush();
               if (lightmap) {
                  mc.entityRenderer.enableLightmap();
                  OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, s.light & 65535, s.light >>> 16);
               }

               GlStateManager.enableAlpha();
               GlStateManager.alphaFunc(516, side != 0 ? 0.0F : 0.1F);
               boolean shader = side != 0;

               try {
                  if (shader) {
                     BodyLookShader.begin(
                        cam,
                        look.sweepMode,
                        look.sweepOX - offX,
                        look.sweepOY - offY,
                        look.sweepOZ - offZ,
                        look.sweepSpan,
                        look.sweep,
                        look.sweepNoise,
                        look.sweepEdge,
                        look.sweepEdgeColor,
                        side,
                        lightmap,
                        0.1F
                     );
                  }

                  if (fading) {
                     RenderState.translucent(true);
                     GlStateManager.colorMask(false, false, false, false);
                     layersLook(s, parts, look, kind, 0, alpha, offX, offY, offZ);
                     Vfx.flush();
                     GlStateManager.colorMask(true, true, true, true);
                     RenderState.translucent(false);
                  } else {
                     RenderState.translucent(true);
                  }

                  layersLook(s, parts, look, kind, kind == BodyLook.MATERIAL ? 5 : 1, alpha, offX, offY, offZ);
                  Vfx.flush();
                  if (kind.outlined() && look.outlineWidth > 0.0F) {
                     RenderState.translucent(!fading);
                     layersLook(s, parts, look, kind, 4, alpha, offX, offY, offZ);
                     Vfx.flush();
                  }

                  if (kind == BodyLook.SKETCH && look.hatching > 0.003F) {
                     RenderState.translucent(false);
                     layersLook(s, parts, look, kind, 2, alpha, offX, offY, offZ);
                     Vfx.flush();
                  }

                  if (kind == BodyLook.SHADOW && look.embers > 0.003F) {
                     RenderState.additive();
                     layersLook(s, parts, look, kind, 3, alpha, offX, offY, offZ);
                     Vfx.flush();
                  }
               } finally {
                  if (shader) {
                     Vfx.flush();
                     BodyLookShader.end();
                  }

                  GlStateManager.alphaFunc(516, 0.1F);
                  GlStateManager.disableAlpha();
                  if (lightmap) {
                     Vfx.flush();
                     mc.entityRenderer.disableLightmap();
                  }

                  RenderState.translucent(false);
               }
            }
         }
      }
   }

   private static void dissolving(
      BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, BodyLook kind, float alpha, boolean lightmap, double offX, double offY, double offZ
   ) {
      Minecraft mc = Minecraft.getMinecraft();
      Camera cam = Vfx.camera();
      double scaleY = cam.projectionScaleY > 0.1 ? cam.projectionScaleY : 1.428;
      hullPerDepth = look.outlineWidth / 1080.0 * 2.0 / scaleY;
      hullBoilStep = look.outlineBoilRate > 0.0F ? (long)Math.floor(look.now / 1.0E9 * look.outlineBoilRate) : 0L;
      Vfx.flush();
      if (lightmap) {
         mc.entityRenderer.enableLightmap();
         OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, s.light & 65535, s.light >>> 16);
      }

      GlStateManager.enableAlpha();
      GlStateManager.alphaFunc(516, 0.0F);
      float before = look.alpha;
      look.alpha = alpha;

      try {
         RenderState.translucent(true);
         DissolveShader.begin(look, s.pose.x - offX, s.pose.y - offY, s.pose.z - offZ, lightmap);
         lookSweep = true;

         try {
            layersLook(s, parts, look, kind, 1, alpha, offX, offY, offZ);
            Vfx.flush();
            if (kind.outlined() && look.outlineWidth > 0.0F) {
               layersLook(s, parts, look, kind, 4, alpha, offX, offY, offZ);
               Vfx.flush();
            }

            if (kind == BodyLook.SHADOW && look.embers > 0.003F) {
               RenderState.additive();
               layersLook(s, parts, look, kind, 3, alpha, offX, offY, offZ);
               Vfx.flush();
            }
         } finally {
            lookSweep = false;
            DissolveShader.end();
         }
      } finally {
         look.alpha = before;
         GlStateManager.alphaFunc(516, 0.1F);
         GlStateManager.disableAlpha();
         if (lightmap) {
            Vfx.flush();
            mc.entityRenderer.disableLightmap();
         }

         RenderState.translucent(false);
      }
   }

   private static void erasing(
      BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, BodyLook kind, float alpha, boolean lightmap, double offX, double offY, double offZ
   ) {
      Minecraft mc = Minecraft.getMinecraft();
      Camera cam = Vfx.camera();
      double scaleY = cam.projectionScaleY > 0.1 ? cam.projectionScaleY : 1.428;
      hullPerDepth = look.outlineWidth / 1080.0 * 2.0 / scaleY;
      hullBoilStep = look.outlineBoilRate > 0.0F ? (long)Math.floor(look.now / 1.0E9 * look.outlineBoilRate) : 0L;
      Vfx.flush();
      if (lightmap) {
         mc.entityRenderer.enableLightmap();
         OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, s.light & 65535, s.light >>> 16);
      }

      GlStateManager.enableAlpha();
      GlStateManager.alphaFunc(516, 0.0F);

      try {
         RenderState.translucent(true);
         EraseShader.begin(
            look.eraseOX - offX,
            look.eraseOY - offY,
            look.eraseOZ - offZ,
            look.eraseRX,
            look.eraseRY,
            look.eraseRZ,
            look.eraseUX,
            look.eraseUY,
            look.eraseUZ,
            look.eraseSeg,
            look.eraseRadius,
            look.eraseDone
         );

         try {
            layersLook(s, parts, look, kind, 1, alpha, offX, offY, offZ);
            Vfx.flush();
            if (kind.outlined() && look.outlineWidth > 0.0F) {
               layersLook(s, parts, look, kind, 4, alpha, offX, offY, offZ);
               Vfx.flush();
            }

            if (kind == BodyLook.SKETCH && look.hatching > 0.003F) {
               RenderState.translucent(false);
               layersLook(s, parts, look, kind, 2, alpha, offX, offY, offZ);
               Vfx.flush();
            }
         } finally {
            EraseShader.end();
         }
      } finally {
         GlStateManager.alphaFunc(516, 0.1F);
         GlStateManager.disableAlpha();
         if (lightmap) {
            Vfx.flush();
            mc.entityRenderer.disableLightmap();
         }

         RenderState.translucent(false);
      }
   }

   private static void layersLook(
      BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, BodyLook kind, int mode, float alpha, double offX, double offY, double offZ
   ) {
      ResourceLocation skinRl = s.skin != null ? s.skin : DefaultPlayerSkin.getDefaultSkin(s.id != null ? s.id : new UUID(0L, 0L));
      int skinTex = textureFor(skinRl, kind, mode, s.slim ? 1 : 0, look);
      int fallbackTint = mode == 1 && skinTex >= 0 && kind.derived() && !derivedReady ? standIn(kind) : -1;

      for (int i = 0; i < 6; i++) {
         BodyProxy.Part p = parts[i];
         if (p.visible && !(p.alpha <= 0.003F)) {
            hullPart = i;
            lookBox(skinTex, PlayerModel.base(i, s.slim), p, 0.0F, 0.0F, look, kind, mode, alpha, offX, offY, offZ, fallbackTint);
            if (look.wear && mode != 4) {
               lookBox(skinTex, PlayerModel.wear(i, s.slim), p, 0.0F, 0.0F, look, kind, mode, alpha, offX, offY, offZ, fallbackTint);
            }
         }
      }

      if (look.armor && mode != 2) {
         for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = s.armor[slot];
            if (stack != null && stack.getItem() instanceof ItemArmor) {
               ItemArmor item = (ItemArmor)stack.getItem();
               boolean legs = slot == 1;
               int forgeSlot = legs ? 2 : 1;
               int color = item.getColor(stack);
               int tint = color == -1 ? -1 : 0xFF000000 | color;

               for (int pass = 0; pass < (color == -1 ? 1 : 2); pass++) {
                  ResourceLocation rl = armorTexture(s, stack, forgeSlot, legs, pass == 0 ? null : "overlay");
                  int tex = textureFor(rl, kind, mode, legs ? 3 : 2, look);
                  int t = pass == 0 ? tint : -1;
                  if (mode == 1 && kind.derived() && !derivedReady) {
                     t = ColorUtil.multiply(t, standIn(kind));
                  }

                  Vfx.flush();

                  for (int ix = 0; ix < 6; ix++) {
                     BodyProxy.Part p = parts[ix];
                     if (p.visible && !(p.alpha <= 0.003F) && PlayerModel.armorCovers(slot, ix)) {
                        hullPart = ix + 6;
                        PlayerModel.Box b = PlayerModel.armor(ix, legs);
                        float dx = PlayerModel.armorOffsetX(ix, s.slim);
                        float dy = PlayerModel.armorOffsetY(ix, s.slim);
                        lookBox(tex, b, p, dx, dy, look, kind, mode, alpha, offX, offY, offZ, t);
                        if (slot == 3 && ix == 0) {
                           lookBox(tex, PlayerModel.HELMET_OVERLAY, p, dx, dy, look, kind, mode, alpha, offX, offY, offZ, t);
                        }
                     }
                  }
               }
            }
         }
      }

      if (look.cape && s.cape != null && mode != 2) {
         BodyProxy.Part body = parts[1];
         if (body.visible && body.alpha > 0.003F && !body.clip && !body.clip2) {
            PlayerModel.capeFrame(body.r, body.x, body.y, body.z, look.capeTilt, s.pose.sneaking, CAPE_C, CAPE_R);
            BodyProxy.Part cape = CAPE_PART;
            cape.copy(body);
            cape.x = CAPE_C[0];
            cape.y = CAPE_C[1];
            cape.z = CAPE_C[2];
            System.arraycopy(CAPE_R, 0, cape.r, 0, 9);
            Vfx.flush();
            hullPart = 12;
            int tex = textureFor(s.cape, kind, mode, 4, look);
            lookBox(
               tex,
               PlayerModel.CAPE,
               cape,
               0.0F,
               0.0F,
               look,
               kind,
               mode,
               alpha,
               offX,
               offY,
               offZ,
               mode == 1 && kind.derived() && !derivedReady ? standIn(kind) : -1
            );
         }
      }
   }

   private static int textureFor(ResourceLocation rl, BodyLook kind, int mode, int texKind, BodyProxy.Look look) {
      derivedReady = true;
      switch (mode) {
         case 1:
            if (kind.derived()) {
               System.arraycopy(look.params, 0, PARAMS, 0, PARAMS.length);
               PARAMS[0] = texKind;
               PARAMS[10] = 0;
               int id = LookTextures.get(rl, kind, PARAMS);
               if (id >= 0) {
                  return id;
               }

               derivedReady = false;
            }

            return texture(rl);
         case 2:
            return LookTextures.hatch();
         case 3:
            System.arraycopy(look.params, 0, PARAMS, 0, PARAMS.length);
            PARAMS[0] = texKind;
            PARAMS[10] = 1;
            return LookTextures.get(rl, BodyLook.SHADOW, PARAMS);
         case 4:
            return Textures.WHITE;
         case 5:
            return Matcaps.texture(look.material, look.finish, false);
         default:
            return texture(rl);
      }
   }

   private static int standIn(BodyLook kind) {
      switch (kind) {
         case INK:
            return -6645612;
         case SKETCH:
            return -1514276;
         case GLASS:
            return -4664065;
         case PAPER:
            return -989482;
         case SHADOW:
            return -13753818;
         default:
            return -1;
      }
   }

   private static void lookBox(
      int texture,
      PlayerModel.Box b,
      BodyProxy.Part p,
      float dx,
      float dy,
      BodyProxy.Look look,
      BodyLook kind,
      int mode,
      float alpha,
      double offX,
      double offY,
      double offZ,
      int tint
   ) {
      if (texture >= 0) {
         float bcx = b.cx() - dx;
         float bcy = b.cy() - dy;
         float bcz = b.cz();
         double k = 0.05859375 * p.scale;
         Camera cam = Vfx.camera();
         double grow = 0.0;
         if (mode == 4) {
            double depth = (p.x - offX - cam.posX) * cam.forwardX + (p.y - offY - cam.posY) * cam.forwardY + (p.z - offZ - cam.posZ) * cam.forwardZ;
            grow = hullPerDepth * Math.max(0.05, depth) + b.inflate * 0.05859375 * 0.0;
            if (look.outlineBoil > 0.0F) {
               int h = hullPart * 668265261 + (int)hullBoilStep * 374761393;
               h ^= h >>> 15;
               h *= -2048144789;
               h ^= h >>> 13;
               grow *= 1.0 + look.outlineBoil * ((h >>> 8 & 65535) / 65535.0 - 0.5) * 1.1;
            }
         }

         for (int c = 0; c < 8; c++) {
            PlayerModel.corner(b, c, C);
            double lx = (C[0] - bcx) * k;
            double ly = (C[1] - bcy) * k;
            double lz = (C[2] - bcz) * k;
            if (grow > 0.0) {
               lx += Math.signum(lx) * grow;
               ly += Math.signum(ly) * grow;
               lz += Math.signum(lz) * grow;
            }

            double wx = p.x + p.r[0] * lx + p.r[1] * ly + p.r[2] * lz;
            double wy = p.y + p.r[3] * lx + p.r[4] * ly + p.r[5] * lz;
            double wz = p.z + p.r[6] * lx + p.r[7] * ly + p.r[8] * lz;
            if (p.stretch != 1.0F) {
               double d = (wx - p.ox) * p.sx + (wy - p.oy) * p.sy + (wz - p.oz) * p.sz;
               double e = d * (p.stretch - 1.0F);
               wx += p.sx * e;
               wy += p.sy * e;
               wz += p.sz * e;
            }

            WX[c] = wx;
            WY[c] = wy;
            WZ[c] = wz;
         }

         float a = alpha * p.alpha;
         double cx8 = (WX[0] + WX[6]) * 0.5;
         double cy8 = (WY[0] + WY[6]) * 0.5;
         double cz8 = (WZ[0] + WZ[6]) * 0.5;

         for (int f = 0; f < 6; f++) {
            int[] fc = PlayerModel.FACE_CORNERS[f];
            double ay = WY[fc[1]] - WY[fc[0]];
            double bz = WZ[fc[3]] - WZ[fc[0]];
            double az = WZ[fc[1]] - WZ[fc[0]];
            double by = WY[fc[3]] - WY[fc[0]];
            double nx = ay * bz - az * by;
            double mx = (WX[fc[0]] + WX[fc[2]]) * 0.5;
            double bx = WX[fc[3]] - WX[fc[0]];
            double ax = WX[fc[1]] - WX[fc[0]];
            double ny = az * bx - ax * bz;
            double my = (WY[fc[0]] + WY[fc[2]]) * 0.5;
            double nz = ax * by - ay * bx;
            double mz = (WZ[fc[0]] + WZ[fc[2]]) * 0.5;
            if (nx * (mx - cx8) + ny * (my - cy8) + nz * (mz - cz8) < 0.0) {
               nx = -nx;
               ny = -ny;
               nz = -nz;
            }

            double nl;
            if (!((nl = Math.sqrt(nx * nx + ny * ny + nz * nz)) < 1.0E-12)) {
               double vx = mx - offX - cam.posX;
               double vy = my - offY - cam.posY;
               double vz = mz - offZ - cam.posZ;
               double var91;
               double var92;
               double var93;
               boolean away = (var91 = nx / nl) * vx + (var92 = ny / nl) * vy + (var93 = nz / nl) * vz > 0.0;
               if (mode == 4 ? away : !away || p.clip || p.clip2) {
                  int n = 4;
                  if (mode == 2) {
                     double la = Math.sqrt(ax * ax + ay * ay + az * az) * 6.0;
                     double lb = Math.sqrt(bx * bx + by * by + bz * bz) * 6.0;

                     for (int k2 = 0; k2 < 4; k2++) {
                        int c = fc[k2];
                        PX[k2] = WX[c];
                        PY[k2] = WY[c];
                        PZ[k2] = WZ[c];
                        PU[k2] = k2 != 1 && k2 != 2 ? 0.0 : la;
                        PV[k2] = k2 >= 2 ? lb : 0.0;
                     }
                  } else {
                     PlayerModel.faceUV(b, f, UV);
                     double u1 = UV[0] / b.texW;
                     double v1 = UV[1] / b.texH;
                     double u2 = UV[2] / b.texW;
                     double v2 = UV[3] / b.texH;

                     for (int k2 = 0; k2 < 4; k2++) {
                        int c = fc[k2];
                        PX[k2] = WX[c];
                        PY[k2] = WY[c];
                        PZ[k2] = WZ[c];
                        PU[k2] = k2 != 0 && k2 != 3 ? u1 : u2;
                        PV[k2] = k2 <= 1 ? v1 : v2;
                     }
                  }

                  if ((!p.clip || (n = clip(n, p.cnx, p.cny, p.cnz, p.cd)) >= 3) && (!p.clip2 || (n = clip(n, p.c2x, p.c2y, p.c2z, p.c2d)) >= 3)) {
                     int color;
                     switch (mode) {
                        case 0:
                           color = -1;
                           break;
                        case 1:
                        default:
                           color = lookColor(kind, look, tint, var91, var92, var93, a);
                           break;
                        case 2:
                           float shade = shade(var91, var92, var93);
                           float h = Math.max(0.0F, Math.min(1.0F, (0.86F - shade) * 2.4F)) * look.hatching;
                           if (h <= 0.01F) {
                              continue;
                           }

                           color = ColorUtil.mulAlpha(-12171700, a * 0.8F * h);
                           break;
                        case 3:
                           double time = look.now / 1.0E9;
                           float flick = 0.62F + 0.38F * (float)(0.5 + 0.5 * Math.sin(time * 7.3 + hullPart * 2.1) * Math.sin(time * 3.1 + f));
                           float glow = (lookSweep ? p.alpha : a) * look.embers * flick;
                           color = lookSweep ? ColorUtil.shade(look.emberColor, Math.min(1.0F, glow)) : ColorUtil.mulAlpha(look.emberColor, glow);
                           break;
                        case 4:
                           color = ColorUtil.mulAlpha(look.outlineColor, a);
                           break;
                        case 5:
                           int base = look.material == Material.CUSTOM ? look.materialTint : -1;
                           color = ColorUtil.mulAlpha(ColorUtil.shade(base, look.worldLight), a);
                     }

                     if (mode == 5) {
                        emitMatcap(texture, n, color, var91, var92, var93, cx8, cy8, cz8, look.roundness, look.reflectTilt, offX, offY, offZ, cam);
                     } else {
                        emit(texture, n, color, look, lookSweep, offX, offY, offZ);
                     }
                  }
               }
            }
         }
      }
   }

   private static float shade(double nx, double ny, double nz) {
      double d0 = Math.max(0.0, nx * L0[0] + ny * L0[1] + nz * L0[2]);
      double d1 = Math.max(0.0, nx * L1[0] + ny * L1[1] + nz * L1[2]);
      return (float)Math.min(1.0, 0.4 + 0.6 * (d0 + d1));
   }

   private static int lookColor(BodyLook kind, BodyProxy.Look look, int tint, double nx, double ny, double nz, float alpha) {
      float s = look.lit ? shade(nx, ny, nz) : 1.0F;
      float k;
      switch (kind) {
         case INK:
            k = 0.84F + 0.16F * s;
            break;
         case SKETCH:
            k = 0.9F + 0.1F * s;
            break;
         case GLASS:
            k = 0.74F + 0.2F * s + 0.3F * (float)Math.max(0.0, ny);
            break;
         case PAPER:
         default:
            k = s;
            break;
         case SHADOW:
            k = 0.55F + 0.45F * s;
            break;
         case TOON:
            k = s >= 0.78F ? 1.0F : 0.74F;
      }

      if (kind != BodyLook.SKIN) {
         k *= look.worldLight;
      }

      float r = (tint >> 16 & 0xFF) / 255.0F * k;
      float g = (tint >> 8 & 0xFF) / 255.0F * k;
      float b = (tint & 0xFF) / 255.0F * k;
      if (look.tintAmount > 0.0F && kind.derived()) {
         float m = look.tintAmount;
         r *= 1.0F + ((look.tint >> 16 & 0xFF) / 255.0F - 1.0F) * m;
         g *= 1.0F + ((look.tint >> 8 & 0xFF) / 255.0F - 1.0F) * m;
         b *= 1.0F + ((look.tint & 0xFF) / 255.0F - 1.0F) * m;
      }

      int ai = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      return ai << 24 | Math.min(255, Math.round(r * 255.0F)) << 16 | Math.min(255, Math.round(g * 255.0F)) << 8 | Math.min(255, Math.round(b * 255.0F));
   }

   private static void emitMatcap(
      int texture,
      int n,
      int color,
      double fnx,
      double fny,
      double fnz,
      double cx,
      double cy,
      double cz,
      float round,
      float tilt,
      double offX,
      double offY,
      double offZ,
      Camera cam
   ) {
      for (int i = 0; i < n; i++) {
         double ox = PX[i] - cx;
         double oy = PY[i] - cy;
         double oz = PZ[i] - cz;
         double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
         if (ol < 1.0E-9) {
            ol = 1.0;
         }

         double vx = fnx * (1.0F - round) + ox / ol * round;
         double vy = fny * (1.0F - round) + oy / ol * round - tilt;
         double vz = fnz * (1.0F - round) + oz / ol * round;
         double vl = Math.max(1.0E-9, Math.sqrt(vx * vx + vy * vy + vz * vz));
         sphereUV(PX[i] - offX, PY[i] - offY, PZ[i] - offZ, vx / vl, vy / vl, vz / vl, cam);
         PU[i] = SUV[0];
         PV[i] = SUV[1];
      }

      emit(texture, n, color, null, false, offX, offY, offZ);
   }

   private static void sphereUV(double x, double y, double z, double nxw, double nyw, double nzw, Camera cam) {
      double dx = x - cam.posX;
      double dy = y - cam.posY;
      double dz = z - cam.posZ;
      double dl = Math.max(1.0E-9, Math.sqrt(dx * dx + dy * dy + dz * dz));
      double ux = (dx * cam.rightX + dy * cam.rightY + dz * cam.rightZ) / dl;
      double uy = (dx * cam.upX + dy * cam.upY + dz * cam.upZ) / dl;
      double uz = -(dx * cam.forwardX + dy * cam.forwardY + dz * cam.forwardZ) / dl;
      double nx = nxw * cam.rightX + nyw * cam.rightY + nzw * cam.rightZ;
      double ny = nxw * cam.upX + nyw * cam.upY + nzw * cam.upZ;
      double nz = -(nxw * cam.forwardX + nyw * cam.forwardY + nzw * cam.forwardZ);
      double nu = nx * ux + ny * uy + nz * uz;
      double rx = ux - 2.0 * nu * nx;
      double ry = uy - 2.0 * nu * ny;
      double rz = uz - 2.0 * nu * nz;
      double m = 2.0 * Math.sqrt(rx * rx + ry * ry + (rz + 1.0) * (rz + 1.0));
      SUV[0] = (float)(rx / m + 0.5);
      SUV[1] = (float)(ry / m + 0.5);
   }

   private static void material(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, float alpha, double offX, double offY, double offZ) {
      boolean glass = look.material.translucent(look.finish);
      boolean solid = alpha >= 0.999F && !glass;
      Vfx.flush();
      RenderState.translucent(solid);
      int base = look.material == Material.CUSTOM ? look.materialTint : -1;
      MeshBatch.begin(look.material, look.finish, look.worldLight, 0.18F, -1, true, solid);

      try {
         for (int i = 0; i < 6; i++) {
            BodyProxy.Part p = parts[i];
            if (p.visible && !(p.alpha <= 0.003F)) {
               materialTilt = look.reflectTilt;
               materialBox(PlayerModel.base(i, s.slim), p, 0.0F, 0.0F, ColorUtil.mulAlpha(base, alpha * p.alpha), look.glint, look.roundness, offX, offY, offZ);
               if (look.armor) {
                  for (int slot = 0; slot < 4; slot++) {
                     ItemStack stack = s.armor[slot];
                     if (stack != null && stack.getItem() instanceof ItemArmor && PlayerModel.armorCovers(slot, i)) {
                        materialBox(
                           PlayerModel.armor(i, slot == 1),
                           p,
                           PlayerModel.armorOffsetX(i, s.slim),
                           PlayerModel.armorOffsetY(i, s.slim),
                           ColorUtil.mulAlpha(base, alpha * p.alpha),
                           look.glint,
                           look.roundness,
                           offX,
                           offY,
                           offZ
                        );
                     }
                  }
               }
            }
         }
      } finally {
         MeshBatch.end();
      }

      try {
         if (look.detail == 1) {
            plates(s, parts, look, alpha, offX, offY, offZ);
         }
      } finally {
         RenderState.translucent(false);
      }
   }

   private static int platesTexture() {
      if (platesTexture == 0) {
         int n = 64;
         int[] px = new int[n * n];
         double[] rivets = new double[]{9.5, n - 9.5};

         for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
               int edge = Math.min(Math.min(x, n - 1 - x), Math.min(y, n - 1 - y));
               float a = edge < 1 ? 0.8F : (edge < 2 ? 0.45F : (edge < 3 ? 0.12F : 0.0F));

               for (double rx : rivets) {
                  for (double ry : rivets) {
                     double d = Math.sqrt((x + 0.5 - rx) * (x + 0.5 - rx) + (y + 0.5 - ry) * (y + 0.5 - ry));
                     if (d < 4.2) {
                        double lit = (x + 0.5 - rx + (y + 0.5 - ry)) / 4.2;
                        a = Math.max(a, d > 3.2 ? 0.85F : (float)Math.max(0.15, 0.45 + 0.3 * lit));
                     }
                  }
               }

               px[y * n + x] = Math.round(a * 255.0F) << 24;
            }
         }

         platesTexture = Textures.uploadImage(px, n, n, true, true);
      }

      return platesTexture;
   }

   private static void plates(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, float alpha, double offX, double offY, double offZ) {
      int tex = platesTexture();
      int metal = look.material == Material.CUSTOM ? look.materialTint : look.material.swatch();
      int col = ColorUtil.mulAlpha(ColorUtil.shade(metal, 0.28F * look.worldLight) | 0xFF000000, alpha);
      Camera cam = Vfx.camera();
      RenderState.translucent(false);
      RenderState.decal(true);

      try {
         for (int i = 0; i < 6; i++) {
            BodyProxy.Part p = parts[i];
            if (p.visible && !(p.alpha <= 0.003F)) {
               PlayerModel.Box b = PlayerModel.base(i, s.slim);
               float bcx = b.cx();
               float bcy = b.cy();
               float bcz = b.cz();
               double k = 0.05859375 * p.scale;

               for (int c = 0; c < 8; c++) {
                  PlayerModel.corner(b, c, C);
                  double lx = (C[0] - bcx) * k;
                  double ly = (C[1] - bcy) * k;
                  double lz = (C[2] - bcz) * k;
                  WX[c] = p.x + p.r[0] * lx + p.r[1] * ly + p.r[2] * lz - offX;
                  WY[c] = p.y + p.r[3] * lx + p.r[4] * ly + p.r[5] * lz - offY;
                  WZ[c] = p.z + p.r[6] * lx + p.r[7] * ly + p.r[8] * lz - offZ;
               }

               double cx8 = (WX[0] + WX[6]) * 0.5;
               double cy8 = (WY[0] + WY[6]) * 0.5;
               double cz8 = (WZ[0] + WZ[6]) * 0.5;
               int pc = ColorUtil.mulAlpha(col, p.alpha);

               for (int f = 0; f < 6; f++) {
                  int[] fc = PlayerModel.FACE_CORNERS[f];
                  double mx = (WX[fc[0]] + WX[fc[2]]) * 0.5;
                  double my = (WY[fc[0]] + WY[fc[2]]) * 0.5;
                  double mz = (WZ[fc[0]] + WZ[fc[2]]) * 0.5;
                  if (!((mx - cx8) * (mx - cam.posX) + (my - cy8) * (my - cam.posY) + (mz - cz8) * (mz - cam.posZ) >= 0.0)) {
                     double ax = WX[fc[1]] - WX[fc[0]];
                     double ay = WY[fc[1]] - WY[fc[0]];
                     double az = WZ[fc[1]] - WZ[fc[0]];
                     double bx = WX[fc[3]] - WX[fc[0]];
                     double by = WY[fc[3]] - WY[fc[0]];
                     double bz = WZ[fc[3]] - WZ[fc[0]];
                     double la = Math.sqrt(ax * ax + ay * ay + az * az) / (0.234375 * p.scale);
                     double lb = Math.sqrt(bx * bx + by * by + bz * bz) / (0.234375 * p.scale);
                     Vfx.quadUV(
                        tex,
                        WX[fc[0]],
                        WY[fc[0]],
                        WZ[fc[0]],
                        0.0,
                        0.0,
                        pc,
                        WX[fc[1]],
                        WY[fc[1]],
                        WZ[fc[1]],
                        la,
                        0.0,
                        pc,
                        WX[fc[2]],
                        WY[fc[2]],
                        WZ[fc[2]],
                        la,
                        lb,
                        pc,
                        WX[fc[3]],
                        WY[fc[3]],
                        WZ[fc[3]],
                        0.0,
                        lb,
                        pc
                     );
                  }
               }
            }
         }

         Vfx.flush();
      } finally {
         RenderState.decal(false);
      }
   }

   private static void materialBox(
      PlayerModel.Box b, BodyProxy.Part p, float dx, float dy, int color, float glint, float round, double offX, double offY, double offZ
   ) {
      float bcx = b.cx() - dx;
      float bcy = b.cy() - dy;
      float bcz = b.cz();
      double k = 0.05859375 * p.scale;

      for (int c = 0; c < 8; c++) {
         PlayerModel.corner(b, c, C);
         double lx = (C[0] - bcx) * k;
         double ly = (C[1] - bcy) * k;
         double lz = (C[2] - bcz) * k;
         WX[c] = p.x + p.r[0] * lx + p.r[1] * ly + p.r[2] * lz - offX;
         WY[c] = p.y + p.r[3] * lx + p.r[4] * ly + p.r[5] * lz - offY;
         WZ[c] = p.z + p.r[6] * lx + p.r[7] * ly + p.r[8] * lz - offZ;
      }

      double cx8 = (WX[0] + WX[6]) * 0.5;
      double cy8 = (WY[0] + WY[6]) * 0.5;
      double cz8 = (WZ[0] + WZ[6]) * 0.5;
      int v = MeshBatch.stage(24);

      for (int f = 0; f < 6; f++) {
         int[] fc = PlayerModel.FACE_CORNERS[f];
         double mx = (WX[fc[0]] + WX[fc[2]]) * 0.5;
         double nx = mx - cx8;
         double my = (WY[fc[0]] + WY[fc[2]]) * 0.5;
         double ny = my - cy8;
         double mz = (WZ[fc[0]] + WZ[fc[2]]) * 0.5;
         double nz = mz - cz8;
         double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
         if (nl < 1.0E-9) {
            nl = 1.0;
         }

         nx /= nl;
         ny /= nl;
         nz /= nl;
         int at = v;

         for (int k2 = 0; k2 < 4; k2++) {
            int c = fc[k2];
            double ox = WX[c] - cx8;
            double oy = WY[c] - cy8;
            double oz = WZ[c] - cz8;
            double ol = Math.sqrt(ox * ox + oy * oy + oz * oz);
            if (ol < 1.0E-9) {
               ol = 1.0;
            }

            double vx = nx * (1.0F - round) + ox / ol * round;
            double vy = ny * (1.0F - round) + oy / ol * round - materialTilt;
            double vz = nz * (1.0F - round) + oz / ol * round;
            double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
            MeshBatch.vertex(v++, WX[c], WY[c], WZ[c], vx / vl, vy / vl, vz / vl, color, glint);
         }

         MeshBatch.quad(at, at + 1, at + 2, at + 3);
      }
   }

   private static void glitch(BodyProxy.Snapshot s, BodyProxy.Part[] parts, BodyProxy.Look look, float alpha, double offX, double offY, double offZ) {
      BodyProxy.Look g = GLITCH_LOOK;
      g.reset();
      g.lit = look.lit;
      g.lightmap = look.lightmap;
      g.armor = look.armor;
      g.heldItem = false;
      g.cape = look.cape;
      g.wear = look.wear;
      g.capeTilt = look.capeTilt;

      for (int ch = 0; ch < 2; ch++) {
         double sign = ch == 0 ? 1.0 : -1.0;

         for (int i = 0; i < 6; i++) {
            GLITCH_PARTS[i].copy(parts[i]);
            GLITCH_PARTS[i].x = GLITCH_PARTS[i].x + look.glitchX * sign;
            GLITCH_PARTS[i].y = GLITCH_PARTS[i].y + look.glitchY * sign;
            GLITCH_PARTS[i].z = GLITCH_PARTS[i].z + look.glitchZ * sign;
         }

         g.tint = ch == 0 ? look.glitchA : look.glitchB;
         g.tintAmount = 0.9F;
         g.alpha = alpha * 0.5F;
         draw(s, GLITCH_PARTS, g, offX, offY, offZ);
      }

      g.tint = -1;
      g.tintAmount = 0.0F;
      g.alpha = alpha;
      g.heldItem = look.heldItem;
      if (look.slices <= 0) {
         draw(s, parts, g, offX, offY, offZ);
      } else {
         double feet = s.pose.y;
         double height = 1.95;
         double gl = Math.sqrt(look.glitchX * look.glitchX + look.glitchZ * look.glitchZ);
         double sx = gl < 1.0E-6 ? 1.0 : look.glitchX / gl;
         double sz = gl < 1.0E-6 ? 0.0 : look.glitchZ / gl;
         int n = Math.min(12, look.slices);

         for (int k = 0; k < n; k++) {
            double y0 = feet + height * k / n;
            double y1 = feet + height * (k + 1) / n;
            float h = hash01(look.sliceSeed, k);
            double jump = h < 0.45F ? (h / 0.45F * 2.0 - 1.0) * look.sliceShift : 0.0;

            for (int i = 0; i < 6; i++) {
               BodyProxy.Part q = GLITCH_PARTS[i];
               q.copy(parts[i]);
               q.x += sx * jump;
               q.z += sz * jump;
               q.clip = true;
               q.cnx = 0.0;
               q.cny = 1.0;
               q.cnz = 0.0;
               q.cd = y0;
               q.clip2 = true;
               q.c2x = 0.0;
               q.c2y = -1.0;
               q.c2z = 0.0;
               q.c2d = -y1;
               q.capColor = 0;
            }

            g.heldItem = false;
            draw(s, GLITCH_PARTS, g, offX, offY, offZ);
         }
      }
   }

   private static float hash01(int seed, int k) {
      int h = seed * 668265261 ^ (k + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8) / 1.6777216E7F;
   }

   private static void heldItem(Minecraft mc, BodyProxy.Snapshot s, BodyProxy.Part arm, double offX, double offY, double offZ) {
      heldItem(mc, s, arm, offX, offY, offZ, null, null);
   }

   private static void heldItem(
      Minecraft mc, BodyProxy.Snapshot s, BodyProxy.Part arm, double offX, double offY, double offZ, BodyLook lookKind, BodyProxy.Look look
   ) {
      EntityLivingBase holder = (EntityLivingBase)(s.entity != null ? s.entity : mc.thePlayer);
      if (holder != null) {
         Vfx.flush();
         RenderState.suspend();
         RenderHelper.enableStandardItemLighting();
         GlStateManager.pushMatrix();

         try {
            PlayerModel.Box b = PlayerModel.base(2, s.slim);
            double k = 0.05859375 * arm.scale;
            double lx = -b.cx() * k;
            double ly = -b.cy() * k;
            double lz = -b.cz() * k;
            double px = arm.x + arm.r[0] * lx + arm.r[1] * ly + arm.r[2] * lz;
            double py = arm.y + arm.r[3] * lx + arm.r[4] * ly + arm.r[5] * lz;
            double pz = arm.z + arm.r[6] * lx + arm.r[7] * ly + arm.r[8] * lz;
            GlStateManager.translate((float)(px - offX), (float)(py - offY), (float)(pz - offZ));
            rotate(arm.r);
            float scale = 0.9375F * arm.scale;
            GlStateManager.scale(scale, scale, scale);
            if (s.slim) {
               GlStateManager.translate(0.0625F, 0.0F, 0.0F);
            }

            GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);
            if (s.pose.sneaking) {
               GlStateManager.translate(0.0F, 0.203125F, 0.0F);
            }

            GlStateManager.enableRescaleNormal();
            mc.entityRenderer.enableLightmap();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, s.light & 65535, s.light >>> 16);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (lookKind != null) {
               int tint = look.material == Material.CUSTOM ? look.materialTint : look.material.swatch();
               ItemLookShader.begin(lookKind, tint, look.params[1]);
            }

            try {
               mc.getItemRenderer().renderItem(holder, s.held, TransformType.THIRD_PERSON);
            } finally {
               if (lookKind != null) {
                  ItemLookShader.end();
               }
            }
         } catch (RuntimeException var39) {
         } finally {
            GlStateManager.popMatrix();
            RenderHelper.disableStandardItemLighting();
            mc.entityRenderer.disableLightmap();
            GlStateManager.disableRescaleNormal();
            RenderState.resume();
         }
      }
   }

   private static void rotate(double[] r) {
      double trace = r[0] + r[4] + r[8];
      double angle = Math.acos(Math.max(-1.0, Math.min(1.0, (trace - 1.0) / 2.0)));
      if (!(angle < 1.0E-6)) {
         double ax = r[7] - r[5];
         double ay = r[2] - r[6];
         double az = r[3] - r[1];
         double len = Math.sqrt(ax * ax + ay * ay + az * az);
         if (len < 1.0E-6) {
            ax = Math.sqrt(Math.max(0.0, (r[0] + 1.0) / 2.0));
            ay = Math.sqrt(Math.max(0.0, (r[4] + 1.0) / 2.0)) * (r[1] >= 0.0 ? 1 : -1);
            az = Math.sqrt(Math.max(0.0, (r[8] + 1.0) / 2.0)) * (r[2] >= 0.0 ? 1 : -1);
            len = Math.sqrt(ax * ax + ay * ay + az * az);
         }

         GlStateManager.rotate((float)Math.toDegrees(angle), (float)(ax / len), (float)(ay / len), (float)(az / len));
      }
   }

   public static final class Look {
      public float alpha = 1.0F;
      public int tint = -1;
      public float tintAmount;
      public int glow = -1;
      public float glowAmount;
      public boolean lit = true;
      public boolean lightmap = true;
      public boolean armor = true;
      public boolean heldItem = true;
      public boolean cape = true;
      public boolean wear = true;
      public float capeTilt = 8.0F;
      public float dissolve = -1.0F;
      public double sweepX;
      public double sweepY = -1.0;
      public double sweepZ;
      public double sweepFrom;
      public double sweepLength = 2.0;
      public float dissolveNoise = 0.45F;
      public int edgeColor = -30147;
      public float edgeWidth = 0.08F;
      public BodyLook kind = BodyLook.SKIN;
      public Material material = Material.GOLD;
      public Finish finish = Finish.METALLIC;
      public int materialTint = -2300950;
      public float glint;
      public float roundness = 0.65F;
      public float reflectTilt;
      public int detail;
      public int eraseStrokes;
      public double eraseOX;
      public double eraseOY;
      public double eraseOZ;
      public double eraseRX;
      public double eraseRY;
      public double eraseRZ;
      public double eraseUX;
      public double eraseUY;
      public double eraseUZ;
      public final float[] eraseSeg = new float[12];
      public final float[] eraseRadius = new float[3];
      public final float[] eraseDone = new float[3];
      public final int[] params = new int[11];
      public int outlineColor = -15658735;
      public float outlineWidth = 2.0F;
      public float outlineBoil;
      public float outlineBoilRate = 10.0F;
      public long now;
      public float worldLight = 1.0F;
      public int emberColor = -45778;
      public float embers = 1.0F;
      public float hatching = 1.0F;
      public BodyLook sweepTo;
      public float sweep;
      public int sweepMode;
      public double sweepOX;
      public double sweepOY;
      public double sweepOZ;
      public float sweepSpan = 2.0F;
      public float sweepNoise = 0.22F;
      public float sweepEdge = 0.06F;
      public int sweepEdgeColor = -1;
      public boolean crossfade;
      public int glitchA = -54314;
      public int glitchB = -16718337;
      public double glitchX;
      public double glitchY;
      public double glitchZ;
      public int slices;
      public int sliceSeed;
      public float sliceShift = 0.12F;

      public void resetParams() {
         Arrays.fill(this.params, 0);
         this.params[1] = 3;
         this.params[2] = 1;
         this.params[3] = 0;
         this.params[4] = 100;
         this.params[5] = -13935399;
         this.params[6] = -4184513;
         this.params[7] = -868018;
         this.params[8] = -15066594;
         this.params[9] = 75;
      }

      public BodyProxy.Look reset() {
         this.alpha = 1.0F;
         this.tint = -1;
         this.tintAmount = 0.0F;
         this.glow = -1;
         this.glowAmount = 0.0F;
         this.lit = true;
         this.lightmap = true;
         this.wear = true;
         this.cape = true;
         this.heldItem = true;
         this.armor = true;
         this.capeTilt = 8.0F;
         this.dissolve = -1.0F;
         this.sweepX = 0.0;
         this.sweepY = -1.0;
         this.sweepZ = 0.0;
         this.sweepFrom = 0.0;
         this.sweepLength = 2.0;
         this.dissolveNoise = 0.45F;
         this.edgeColor = -30147;
         this.edgeWidth = 0.08F;
         this.kind = BodyLook.SKIN;
         this.material = Material.GOLD;
         this.finish = Finish.METALLIC;
         this.materialTint = -2300950;
         this.glint = 0.0F;
         this.roundness = 0.65F;
         this.reflectTilt = 0.0F;
         this.detail = 0;
         this.eraseStrokes = 0;
         this.resetParams();
         this.outlineColor = -15658735;
         this.outlineWidth = 2.0F;
         this.outlineBoil = 0.0F;
         this.outlineBoilRate = 10.0F;
         this.worldLight = 1.0F;
         this.emberColor = -45778;
         this.embers = 1.0F;
         this.hatching = 1.0F;
         this.sweepTo = null;
         this.sweep = 0.0F;
         this.sweepMode = 0;
         this.sweepOZ = 0.0;
         this.sweepOY = 0.0;
         this.sweepOX = 0.0;
         this.sweepSpan = 2.0F;
         this.sweepNoise = 0.22F;
         this.sweepEdge = 0.06F;
         this.sweepEdgeColor = -1;
         this.crossfade = false;
         this.glitchA = -54314;
         this.glitchB = -16718337;
         this.glitchZ = 0.0;
         this.glitchY = 0.0;
         this.glitchX = 0.0;
         this.slices = 0;
         this.sliceSeed = 0;
         this.sliceShift = 0.12F;
         return this;
      }

      public Look() {
         this.resetParams();
      }
   }

   public static final class Part {
      public double x;
      public double y;
      public double z;
      public final double[] r = new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0};
      public float scale = 1.0F;
      public float stretch = 1.0F;
      public double sx;
      public double sy = 1.0;
      public double sz;
      public double ox;
      public double oy;
      public double oz;
      public float alpha = 1.0F;
      public boolean visible = true;
      public boolean clip;
      public double cnx;
      public double cny;
      public double cnz;
      public double cd;
      public boolean clip2;
      public double c2x;
      public double c2y;
      public double c2z;
      public double c2d;
      public int capColor;

      public BodyProxy.Part copy(BodyProxy.Part o) {
         this.x = o.x;
         this.y = o.y;
         this.z = o.z;
         System.arraycopy(o.r, 0, this.r, 0, 9);
         this.scale = o.scale;
         this.stretch = o.stretch;
         this.sx = o.sx;
         this.sy = o.sy;
         this.sz = o.sz;
         this.ox = o.ox;
         this.oy = o.oy;
         this.oz = o.oz;
         this.alpha = o.alpha;
         this.visible = o.visible;
         this.clip = o.clip;
         this.cnx = o.cnx;
         this.cny = o.cny;
         this.cnz = o.cnz;
         this.cd = o.cd;
         this.clip2 = o.clip2;
         this.c2x = o.c2x;
         this.c2y = o.c2y;
         this.c2z = o.c2z;
         this.c2d = o.c2d;
         this.capColor = o.capColor;
         return this;
      }
   }

   public static final class Snapshot {
      public final PoseSnapshot pose = new PoseSnapshot();
      public ResourceLocation skin;
      public ResourceLocation cape;
      public boolean slim;
      public final ItemStack[] armor = new ItemStack[4];
      public ItemStack held;
      public EntityLivingBase entity;
      public String name = "";
      public UUID id;
      public int entityId = -1;
      public double vx;
      public double vy;
      public double vz;
      public int light = 15728880;
      public boolean valid;

      public BodyProxy.Snapshot capture(EntityPlayer p, float pt) {
         this.pose.capture(p, pt);
         this.entity = p;
         this.name = p.getName();
         this.id = p.getUniqueID();
         this.entityId = p.getEntityId();
         if (p instanceof AbstractClientPlayer) {
            AbstractClientPlayer a = (AbstractClientPlayer)p;
            this.skin = a.getLocationSkin();
            this.cape = a.getLocationCape();
            this.slim = "slim".equals(a.getSkinType());
         } else {
            this.skin = DefaultPlayerSkin.getDefaultSkin(p.getUniqueID());
            this.cape = null;
            this.slim = false;
         }

         this.pose.slim = this.slim;

         for (int i = 0; i < 4; i++) {
            ItemStack s = p.getCurrentArmor(i);
            this.armor[i] = s != null ? s.copy() : null;
         }

         ItemStack h = p.getHeldItem();
         this.held = h == null ? null : h.copy();
         this.vx = (p.posX - p.prevPosX) * 20.0;
         this.vy = (p.posY - p.prevPosY) * 20.0;
         this.vz = (p.posZ - p.prevPosZ) * 20.0;
         this.light = p.getBrightnessForRender(pt);
         this.valid = true;
         return this;
      }

      public BodyProxy.Snapshot copy(BodyProxy.Snapshot o) {
         this.pose.copy(o.pose);
         this.skin = o.skin;
         this.cape = o.cape;
         this.slim = o.slim;
         System.arraycopy(o.armor, 0, this.armor, 0, 4);
         this.held = o.held;
         this.entity = o.entity;
         this.name = o.name;
         this.id = o.id;
         this.entityId = o.entityId;
         this.vx = o.vx;
         this.vy = o.vy;
         this.vz = o.vz;
         this.light = o.light;
         this.valid = o.valid;
         return this;
      }
   }
}
