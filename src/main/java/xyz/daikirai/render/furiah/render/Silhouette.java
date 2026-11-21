package xyz.daikirai.render.furiah.render;

import xyz.daikirai.render.furiah.setting.ThemeTint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

public final class Silhouette {
   private static final int PARTS = 6;
   private static final int HEAD = 0;
   private static final int BODY = 1;
   private static final int RIGHT_ARM = 2;
   private static final int LEFT_ARM = 3;
   private static final int RIGHT_LEG = 4;
   private static final int LEFT_LEG = 5;
   private static final int LEVELS = 4;
   private static final int PER_PART = 20;
   private static int levels = 4;
   private static double colorTime;
   private static final double BODY_HEIGHT = 1.9;
   private static final float[] ANG_X = new float[6];
   private static final float[] ANG_Y = new float[6];
   private static final float[] ANG_Z = new float[6];
   private static final float[] RP_X = new float[6];
   private static final float[] RP_Y = new float[6];
   private static final float[] RP_Z = new float[6];
   private static final float[] B_X = new float[6];
   private static final float[] B_Y = new float[6];
   private static final float[] B_Z = new float[6];
   private static final float[] B_W = new float[6];
   private static final float[] B_H = new float[6];
   private static final float[] B_D = new float[6];
   private static final double[] VX = new double[120];
   private static final double[] VY = new double[120];
   private static final double[] VZ = new double[120];
   private static final float[] VT = new float[120];
   private static final float[] VA = new float[120];
   private static final int[] VC = new int[120];
   private static final double[] AX = new double[18];
   private static final double[] AY = new double[18];
   private static final double[] AZ = new double[18];
   private static final double[] E = new double[9];
   private static final double[] P = new double[9];
   private static final double[] L = new double[9];
   private static final double[] T = new double[9];
   private static final double[] R1 = new double[9];
   private static double pullMin;
   private static double pullMax;
   private static double pullLo;
   private static double pullHi;
   public static final int FILL = 1;
   public static final int RIM = 2;

   private Silhouette() {
   }

   private static void poseParts(PoseSnapshot p) {
      float pi = (float) Math.PI;
      float headY = p.netHeadYaw / (180.0F / (float)Math.PI);
      float headX = p.headPitch / (180.0F / (float)Math.PI);
      float ls = p.limbSwing;
      float la = p.limbSwingAmount;
      float rArmX = (float)Math.cos(ls * 0.6662F + (float) Math.PI) * 2.0F * la * 0.5F;
      float lArmX = (float)Math.cos(ls * 0.6662F) * 2.0F * la * 0.5F;
      float rArmZ = 0.0F;
      float lArmZ = 0.0F;
      float rLegX = (float)Math.cos(ls * 0.6662F) * 1.4F * la;
      float lLegX = (float)Math.cos(ls * 0.6662F + (float) Math.PI) * 1.4F * la;
      if (p.heldItemRight == 1) {
         rArmX = rArmX * 0.5F - (float) (Math.PI / 10);
      }

      float rArmY = 0.0F;
      if (p.heldItemRight == 3) {
         rArmX = rArmX * 0.5F - (float) (Math.PI * 3.0 / 10.0);
         rArmY = (float) (-Math.PI / 6);
      }

      float lArmY = 0.0F;
      float swing = p.swingProgress;
      float bodyY = (float)Math.sin(Math.sqrt(swing) * (float) Math.PI * 2.0) * 0.2F;
      float rArmRpX = (float)(-Math.cos(bodyY)) * 5.0F;
      float rArmRpZ = (float)Math.sin(bodyY) * 5.0F;
      float lArmRpX = (float)Math.cos(bodyY) * 5.0F;
      float lArmRpZ = (float)(-Math.sin(bodyY)) * 5.0F;
      rArmY += bodyY;
      lArmY += bodyY;
      lArmX += bodyY;
      float f = 1.0F - swing;
      f *= f;
      f *= f;
      f = 1.0F - f;
      float f1 = (float)Math.sin(f * (float) Math.PI);
      float f2 = (float)Math.sin(swing * (float) Math.PI) * -(headX - 0.7F) * 0.75F;
      rArmX -= f1 * 1.2F + f2;
      rArmY += bodyY * 2.0F;
      rArmZ += (float)Math.sin(swing * (float) Math.PI) * -0.4F;
      float headRpY;
      float legRpY;
      float legRpZ;
      float bodyX;
      if (p.sneaking) {
         bodyX = 0.5F;
         rArmX += 0.4F;
         lArmX += 0.4F;
         legRpZ = 4.0F;
         legRpY = 9.0F;
         headRpY = 1.0F;
      } else {
         bodyX = 0.0F;
         legRpZ = 0.1F;
         legRpY = 12.0F;
         headRpY = 0.0F;
      }

      float age = p.ageInTicks;
      rArmZ += (float)Math.cos(age * 0.09F) * 0.05F + 0.05F;
      lArmZ -= (float)Math.cos(age * 0.09F) * 0.05F + 0.05F;
      rArmX += (float)Math.sin(age * 0.067F) * 0.05F;
      lArmX -= (float)Math.sin(age * 0.067F) * 0.05F;
      if (p.aimedBow) {
         rArmZ = 0.0F;
         lArmZ = 0.0F;
         rArmY = -0.1F + headY;
         lArmY = 0.1F + headY + 0.4F;
         rArmX = (float) (-Math.PI / 2) + headX;
         lArmX = (float) (-Math.PI / 2) + headX;
         rArmZ += (float)Math.cos(age * 0.09F) * 0.05F + 0.05F;
         lArmZ -= (float)Math.cos(age * 0.09F) * 0.05F + 0.05F;
         rArmX += (float)Math.sin(age * 0.067F) * 0.05F;
         lArmX -= (float)Math.sin(age * 0.067F) * 0.05F;
      }

      part(0, headX, headY, 0.0F, 0.0F, headRpY, 0.0F, -4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F);
      part(1, bodyX, bodyY, 0.0F, 0.0F, 0.0F, 0.0F, -4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F);
      float armW = p.slim ? 3.0F : 4.0F;
      float armRpY = p.slim ? 2.5F : 2.0F;
      part(2, rArmX, rArmY, rArmZ, rArmRpX, armRpY, rArmRpZ, p.slim ? -2.0F : -3.0F, -2.0F, -2.0F, armW, 12.0F, 4.0F);
      part(3, lArmX, lArmY, lArmZ, lArmRpX, armRpY, lArmRpZ, -1.0F, -2.0F, -2.0F, armW, 12.0F, 4.0F);
      part(4, rLegX, 0.0F, 0.0F, -1.9F, legRpY, legRpZ, -2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
      part(5, lLegX, 0.0F, 0.0F, 1.9F, legRpY, legRpZ, -2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F);
   }

   private static void part(int i, float ax, float ay, float az, float rpx, float rpy, float rpz, float bx, float by, float bz, float w, float h, float d) {
      ANG_X[i] = ax;
      ANG_Y[i] = ay;
      ANG_Z[i] = az;
      RP_X[i] = rpx;
      RP_Y[i] = rpy;
      RP_Z[i] = rpz;
      B_X[i] = bx;
      B_Y[i] = by;
      B_Z[i] = bz;
      B_W[i] = w;
      B_H[i] = h;
      B_D[i] = d;
   }

   private static void mul(double[] a, double[] b, double[] out) {
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 3; c++) {
            T[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
         }
      }

      System.arraycopy(T, 0, out, 0, 9);
   }

   private static void rotX(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set(out, 1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
   }

   private static void rotY(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set(out, c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
   }

   private static void rotZ(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set(out, c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
   }

   private static void set(double[] m, double a, double b, double c, double d, double e, double f, double g, double h, double i) {
      m[0] = a;
      m[1] = b;
      m[2] = c;
      m[3] = d;
      m[4] = e;
      m[5] = f;
      m[6] = g;
      m[7] = h;
      m[8] = i;
   }

   private static void build(PoseSnapshot pose, float inflate, Silhouette.Look look) {
      poseParts(pose);
      rotY(Math.toRadians(180.0F - pose.bodyYaw), E);
      rotZ(Math.toRadians(pose.deathAngle), R1);
      mul(E, R1, E);
      double s = pose.scale;
      set(R1, -s, 0.0, 0.0, 0.0, -s, 0.0, 0.0, 0.0, s);
      mul(E, R1, E);
      double baseY = -1.5078125 + (pose.sneaking ? 0.2 : 0.0);
      double cx = pose.x;
      double cy = pose.y + 0.95;
      double cz = pose.z;

      for (int part = 0; part < 6; part++) {
         rotZ(ANG_Z[part], P);
         rotY(ANG_Y[part], R1);
         mul(P, R1, P);
         rotX(ANG_X[part], R1);
         mul(P, R1, P);
         mul(E, P, L);
         double ox = RP_X[part] / 16.0;
         double oy = baseY + RP_Y[part] / 16.0;
         double oz = RP_Z[part] / 16.0;
         double tx = pose.x + E[0] * ox + E[1] * oy + E[2] * oz;
         double ty = pose.y + E[3] * ox + E[4] * oy + E[5] * oz;
         double tz = pose.z + E[6] * ox + E[7] * oy + E[8] * oz;

         for (int k = 0; k < 3; k++) {
            double lx = L[k];
            double ly = L[3 + k];
            double lz = L[6 + k];
            double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
            int o = part * 3 + k;
            AX[o] = lx / len;
            AY[o] = ly / len;
            AZ[o] = lz / len;
         }

         float x0 = B_X[part] - inflate;
         float x1 = B_X[part] + B_W[part] + inflate;
         float z0 = B_Z[part] - inflate;
         float z1 = B_Z[part] + B_D[part] + inflate;
         float y0 = B_Y[part] - inflate;
         float y1 = B_Y[part] + B_H[part] + inflate;

         for (int level = 0; level <= levels; level++) {
            double ly = (y0 + (double)((y1 - y0) * level) / levels) / 16.0;

            for (int c = 0; c < 4; c++) {
               double lx = (c != 1 && c != 2 ? x0 : x1) / 16.0;
               double lz = (c >= 2 ? z1 : z0) / 16.0;
               int v = part * 20 + level * 4 + c;
               double wx = tx + L[0] * lx + L[1] * ly + L[2] * lz;
               double wy = ty + L[3] * lx + L[4] * ly + L[5] * lz;
               double wz = tz + L[6] * lx + L[7] * ly + L[8] * lz;
               if (look.stretch != 1.0F) {
                  double d = (wx - cx) * look.stretchX + (wy - cy) * look.stretchY + (wz - cz) * look.stretchZ;
                  double k = d * (look.stretch - 1.0F);
                  wx += look.stretchX * k;
                  wy += look.stretchY * k;
                  wz += look.stretchZ * k;
               }

               VX[v] = wx + look.shiftX;
               VY[v] = wy + look.shiftY;
               VZ[v] = wz + look.shiftZ;
               float t = (float)((wy - pose.y) / 1.9);
               VT[v] = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
            }
         }
      }
   }

   private static void pull(Silhouette.Look look, Camera cam, double offX, double offY, double offZ, boolean measure) {
      if (!(look.pullDistance <= 0.0)) {
         if (measure) {
            pullMin = Double.MAX_VALUE;
            pullMax = 0.0;

            for (int v = 0; v < VX.length; v++) {
               double rx = VX[v] - offX - cam.posX;
               double ry = VY[v] - offY - cam.posY;
               double rz = VZ[v] - offZ - cam.posZ;
               double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
               pullMin = Math.min(pullMin, d);
               pullMax = Math.max(pullMax, d);
            }

            pullHi = look.pullDistance - 0.04;
            pullLo = Math.min(pullMin, look.pullDistance - 0.45);
         }

         if (!(pullMax <= pullHi) && !(pullMax - pullMin < 1.0E-6)) {
            double scale = (pullHi - pullLo) / (pullMax - pullMin);

            for (int v = 0; v < VX.length; v++) {
               double rx = VX[v] - offX - cam.posX;
               double ry = VY[v] - offY - cam.posY;
               double rz = VZ[v] - offZ - cam.posZ;
               double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
               if (!(d < 1.0E-6)) {
                  double k = (pullLo + (d - pullMin) * scale) / d;
                  VX[v] = cam.posX + rx * k + offX;
                  VY[v] = cam.posY + ry * k + offY;
                  VZ[v] = cam.posZ + rz * k + offZ;
               }
            }
         }
      }
   }

   private static void vertexAlpha(Silhouette.Look look, Camera cam, double offX, double offY, double offZ) {
      int used = (levels + 1) * 4;

      for (int v = 0; v < VX.length; v++) {
         if (v % 20 < used) {
            VC[v] = look.color != null ? look.color.getColor(VT[v], colorTime) : look.staticColor;
            float a = 1.0F;
            if (look.dissolve >= 0.0F) {
               a *= Easing.smoothstep(look.dissolve - 0.02F, look.dissolve + 0.14F, VT[v]);
            }

            if (look.nearOuter > 0.0F) {
               double dx = VX[v] - offX - cam.posX;
               double dy = VY[v] - offY - cam.posY;
               double dz = VZ[v] - offZ - cam.posZ;
               a *= Easing.smoothstep(look.nearInner, look.nearOuter, (float)Math.sqrt(dx * dx + dy * dy + dz * dz));
            }

            VA[v] = a;
         }
      }
   }

   public static void draw(PoseSnapshot pose, double offX, double offY, double offZ, Silhouette.Look look) {
      if (pose != null && pose.valid && !(look.alpha <= 0.003F)) {
         levels = Math.max(1, Math.min(4, look.levels));
         colorTime = ThemeTint.clock();
         Camera cam = Vfx.camera();
         if (look.fill > 0.003F) {
            build(pose, look.inflate, look);
            pull(look, cam, offX, offY, offZ, true);
            vertexAlpha(look, cam, offX, offY, offZ);
            if (look.prepass) {
               Vfx.flush();
               GlStateManager.colorMask(false, false, false, false);
               GlStateManager.depthMask(true);
               faces(offX, offY, offZ, look, cam, 0, true);
               Vfx.flush();
               GlStateManager.colorMask(true, true, true, true);
            }

            if (look.additive) {
               RenderState.additive();
            } else {
               RenderState.translucent(false);
            }

            faces(offX, offY, offZ, look, cam, 1, false);
         }

         if (look.rim > 0.003F) {
            build(pose, look.inflate + look.rimWidth, look);
            pull(look, cam, offX, offY, offZ, look.fill <= 0.003F);
            vertexAlpha(look, cam, offX, offY, offZ);
            RenderState.additive();
            faces(offX, offY, offZ, look, cam, 2, false);
         }

         RenderState.additive();
      }
   }

   public static void drawGlitch(
      PoseSnapshot pose,
      double offX,
      double offY,
      double offZ,
      Silhouette.Look look,
      int colorA,
      int colorB,
      double sx,
      double sy,
      double sz,
      int slices,
      int seed,
      float shift,
      float alpha,
      boolean world
   ) {
      if (pose != null && pose.valid && !(alpha <= 0.003F)) {
         ThemeTint oldColor = look.color;
         int oldStatic = look.staticColor;
         boolean oldAdditive = look.additive;
         boolean oldPrepass = look.prepass;
         double oldX = look.shiftX;
         double oldY = look.shiftY;
         double oldZ = look.shiftZ;
         float oldAlpha = look.alpha;

         try {
            look.color = null;
            look.additive = true;
            look.prepass = false;
            look.alpha = alpha;

            for (int ch = 0; ch < 2; ch++) {
               double sign = ch == 0 ? 1.0 : -1.0;
               look.staticColor = ch == 0 ? colorA : colorB;
               look.shiftX = oldX + sx * sign;
               look.shiftY = oldY + sy * sign;
               look.shiftZ = oldZ + sz * sign;
               draw(pose, offX, offY, offZ, look);
            }

            if (!world || slices <= 0) {
               return;
            }

            Camera cam = Vfx.camera();
            Minecraft mc = Minecraft.getMinecraft();
            int h = mc.displayHeight;
            int w = mc.displayWidth;
            double scale = cam.projectionScaleY > 0.1 ? cam.projectionScaleY : 1.428;
            double feet = screenY(pose.x - offX, pose.y - offY, pose.z - offZ, cam, scale, h);
            double head = screenY(pose.x - offX, pose.y + 1.95 - offY, pose.z - offZ, cam, scale, h);
            if (!Double.isNaN(feet) && !Double.isNaN(head)) {
               double lo = Math.min(feet, head);
               double hi = Math.max(feet, head);
               int n = Math.min(12, slices);
               look.staticColor = -1;
               look.alpha = alpha * 0.75F;
               Vfx.flush();
               GL11.glEnable(3089);

               try {
                  for (int k = 0; k < n; k++) {
                     int hk = seed * 668265261 ^ (k + 374761393) * -1640531535;
                     hk ^= hk >>> 15;
                     hk *= -2048144789;
                     float r;
                     if (!((r = ((hk = hk ^ hk >>> 13) >>> 8) / 1.6777216E7F) >= 0.5F)) {
                        double jump = (r / 0.5F * 2.0 - 1.0) * shift;
                        int y0 = (int)Math.floor(lo + (hi - lo) * k / n);
                        int y1 = (int)Math.ceil(lo + (hi - lo) * (k + 1) / n);
                        GL11.glScissor(0, Math.max(0, y0), w, Math.max(1, y1 - y0));
                        look.staticColor = (k & 1) == 0 ? colorA : colorB;
                        look.shiftX = oldX + cam.rightX * jump;
                        look.shiftY = oldY + cam.rightY * jump;
                        look.shiftZ = oldZ + cam.rightZ * jump;
                        draw(pose, offX, offY, offZ, look);
                        Vfx.flush();
                     }
                  }

                  return;
               } finally {
                  GL11.glDisable(3089);
               }
            }
         } finally {
            look.color = oldColor;
            look.staticColor = oldStatic;
            look.additive = oldAdditive;
            look.prepass = oldPrepass;
            look.shiftX = oldX;
            look.shiftY = oldY;
            look.shiftZ = oldZ;
            look.alpha = oldAlpha;
         }
      }
   }

   private static double screenY(double x, double y, double z, Camera cam, double scale, int height) {
      double dx = x - cam.posX;
      double dy = y - cam.posY;
      double dz = z - cam.posZ;
      double d = dx * cam.forwardX + dy * cam.forwardY + dz * cam.forwardZ;
      if (d < 0.05) {
         return Double.NaN;
      } else {
         double ndc = (dx * cam.upX + dy * cam.upY + dz * cam.upZ) / d * scale;
         return (ndc + 1.0) * 0.5 * height;
      }
   }

   public static void drawPass(PoseSnapshot pose, double offX, double offY, double offZ, Silhouette.Look look, int pass) {
      if (pose != null && pose.valid && !(look.alpha <= 0.003F)) {
         levels = Math.max(1, Math.min(4, look.levels));
         colorTime = ThemeTint.clock();
         Camera cam = Vfx.camera();
         if (pass == 1) {
            if (look.fill <= 0.003F) {
               return;
            }

            build(pose, look.inflate, look);
            vertexAlpha(look, cam, offX, offY, offZ);
            faces(offX, offY, offZ, look, cam, 3, false);
         } else {
            if (look.rim <= 0.003F) {
               return;
            }

            build(pose, look.inflate + look.rimWidth, look);
            vertexAlpha(look, cam, offX, offY, offZ);
            faces(offX, offY, offZ, look, cam, 2, false);
         }
      }
   }

   private static void faces(double offX, double offY, double offZ, Silhouette.Look look, Camera cam, int mode, boolean depthOnly) {
      for (int part = 0; part < 6; part++) {
         int base = part * 20;

         for (int c = 0; c < 4; c++) {
            int n = c + 1 & 3;
            int axis = (c & 1) == 0 ? 2 : 0;
            double sign = c != 0 && c != 3 ? 1.0 : -1.0;
            double nx = AX[part * 3 + axis] * sign;
            double ny = AY[part * 3 + axis] * sign;
            double nz = AZ[part * 3 + axis] * sign;

            for (int level = 0; level < levels; level++) {
               int a = base + level * 4 + c;
               int b = base + level * 4 + n;
               int d = a + 4;
               int e = b + 4;
               quad(a, b, e, d, nx, ny, nz, offX, offY, offZ, look, cam, mode, depthOnly);
            }
         }

         double nx = AX[part * 3 + 1];
         double ny = AY[part * 3 + 1];
         double nz = AZ[part * 3 + 1];
         quad(base, base + 1, base + 2, base + 3, -nx, -ny, -nz, offX, offY, offZ, look, cam, mode, depthOnly);
         int top = base + levels * 4;
         quad(top, top + 1, top + 2, top + 3, nx, ny, nz, offX, offY, offZ, look, cam, mode, depthOnly);
      }
   }

   private static void quad(
      int a,
      int b,
      int c,
      int d,
      double nx,
      double ny,
      double nz,
      double offX,
      double offY,
      double offZ,
      Silhouette.Look look,
      Camera cam,
      int mode,
      boolean depthOnly
   ) {
      double mx = (VX[a] + VX[c]) * 0.5 - offX - cam.posX;
      double my = (VY[a] + VY[c]) * 0.5 - offY - cam.posY;
      double mz = (VZ[a] + VZ[c]) * 0.5 - offZ - cam.posZ;
      double facing = nx * mx + ny * my + nz * mz;
      if (mode != 2 || !(facing <= 0.0)) {
         if (!(facing > 0.0) || mode != 0 && mode != 3 && (mode != 1 || !look.prepass)) {
            if (depthOnly) {
               Vfx.quad(
                  VX[a] - offX,
                  VY[a] - offY,
                  VZ[a] - offZ,
                  VX[b] - offX,
                  VY[b] - offY,
                  VZ[b] - offZ,
                  VX[c] - offX,
                  VY[c] - offY,
                  VZ[c] - offZ,
                  VX[d] - offX,
                  VY[d] - offY,
                  VZ[d] - offZ,
                  -1,
                  -1,
                  -1,
                  -1
               );
            } else {
               double dist = Math.sqrt(mx * mx + my * my + mz * mz);
               float edgeOn = dist < 1.0E-6 ? 0.0F : 1.0F - (float)Math.abs(facing / dist);
               float mixCore;
               float k;
               if (mode == 2) {
                  k = look.rim * look.alpha;
                  mixCore = 0.45F;
               } else {
                  float light = 0.78F + 0.22F * (float)Math.max(0.0, ny);
                  k = look.fill * look.alpha * (0.72F + 0.55F * edgeOn * edgeOn) * light;
                  mixCore = 0.15F + 0.4F * edgeOn * edgeOn;
               }

               Vfx.quad(
                  VX[a] - offX,
                  VY[a] - offY,
                  VZ[a] - offZ,
                  VX[b] - offX,
                  VY[b] - offY,
                  VZ[b] - offZ,
                  VX[c] - offX,
                  VY[c] - offY,
                  VZ[c] - offZ,
                  VX[d] - offX,
                  VY[d] - offY,
                  VZ[d] - offZ,
                  color(look, a, k, mixCore),
                  color(look, b, k, mixCore),
                  color(look, c, k, mixCore),
                  color(look, d, k, mixCore)
               );
            }
         }
      }
   }

   private static int color(Silhouette.Look look, int v, float k, float mixCore) {
      int c = VC[v];
      c = ColorUtil.lerp(c, ColorUtil.core(c), mixCore);
      return ColorUtil.mulAlpha(c, Math.min(1.0F, k * VA[v]));
   }

   public static void randomPoint(float r1, float r2, float r3, double[] out) {
      int part = Math.min(5, (int)(r1 * 6.0F));
      int level = Math.min(levels, (int)(r2 * (levels + 1)));
      int c = Math.min(3, (int)(r3 * 4.0F));
      int v = part * 20 + level * 4 + c;
      int w = part * 20 + level * 4 + (c + 1 & 3);
      float f = r3 * 4.0F % 1.0F;
      out[0] = VX[v] + (VX[w] - VX[v]) * f;
      out[1] = VY[v] + (VY[w] - VY[v]) * f;
      out[2] = VZ[v] + (VZ[w] - VZ[v]) * f;
   }

   public static float randomPointHeight(float r1, float r2, float r3) {
      int part = Math.min(5, (int)(r1 * 6.0F));
      int level = Math.min(levels, (int)(r2 * (levels + 1)));
      int c = Math.min(3, (int)(r3 * 4.0F));
      return VT[part * 20 + level * 4 + c];
   }

   public static final class Look {
      public ThemeTint color;
      public int staticColor = -1;
      public float alpha = 1.0F;
      public float fill = 0.5F;
      public float rim = 1.0F;
      public float inflate = 0.25F;
      public float rimWidth = 0.9F;
      public boolean additive = true;
      public boolean prepass = true;
      public float dissolve = -1.0F;
      public double stretchX;
      public double stretchY;
      public double stretchZ;
      public float stretch = 1.0F;
      public double shiftX;
      public double shiftY;
      public double shiftZ;
      public float nearInner;
      public float nearOuter;
      public double pullDistance;
      public int levels = 4;

      public Silhouette.Look reset() {
         this.color = null;
         this.staticColor = -1;
         this.alpha = 1.0F;
         this.fill = 0.5F;
         this.rim = 1.0F;
         this.inflate = 0.25F;
         this.rimWidth = 0.9F;
         this.additive = false;
         this.prepass = true;
         this.dissolve = -1.0F;
         this.stretch = 1.0F;
         this.stretchZ = 0.0;
         this.stretchY = 0.0;
         this.stretchX = 0.0;
         this.shiftZ = 0.0;
         this.shiftY = 0.0;
         this.shiftX = 0.0;
         this.nearOuter = 0.0F;
         this.nearInner = 0.0F;
         this.pullDistance = 0.0;
         this.levels = 4;
         return this;
      }
   }
}
