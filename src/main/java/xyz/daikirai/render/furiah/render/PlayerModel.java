package xyz.daikirai.render.furiah.render;

public final class PlayerModel {
   public static final int HEAD = 0;
   public static final int BODY = 1;
   public static final int RIGHT_ARM = 2;
   public static final int LEFT_ARM = 3;
   public static final int RIGHT_LEG = 4;
   public static final int LEFT_LEG = 5;
   public static final int PARTS = 6;
   public static final float SCALE = 0.9375F;
   public static final double PIXEL = 0.05859375;
   private static final PlayerModel.Box[] BASE_WIDE = new PlayerModel.Box[6];
   private static final PlayerModel.Box[] BASE_SLIM = new PlayerModel.Box[6];
   private static final PlayerModel.Box[] WEAR_WIDE = new PlayerModel.Box[6];
   private static final PlayerModel.Box[] WEAR_SLIM = new PlayerModel.Box[6];
   private static final PlayerModel.Box[][] ARMOR = new PlayerModel.Box[2][6];
   public static final PlayerModel.Box HELMET_OVERLAY = new PlayerModel.Box(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 32, 0, 64, 32, 1.5F, false);
   public static final PlayerModel.Box CAPE = new PlayerModel.Box(-5.0F, 0.0F, -1.0F, 10.0F, 16.0F, 1.0F, 0, 0, 64, 32, 0.0F, false);
   public static final int[][] FACE_CORNERS;
   private static final float[] ANG_X;
   private static final float[] ANG_Y;
   private static final float[] ANG_Z;
   private static final float[] RP_X;
   private static final float[] RP_Y;
   private static final float[] RP_Z;
   private static final double[] E;
   private static final double[] P;
   private static final double[] R1;
   private static final double[] T;

   private PlayerModel() {
   }

   public static PlayerModel.Box base(int part, boolean slim) {
      return (slim ? BASE_SLIM : BASE_WIDE)[part];
   }

   public static PlayerModel.Box wear(int part, boolean slim) {
      return (slim ? WEAR_SLIM : WEAR_WIDE)[part];
   }

   public static PlayerModel.Box armor(int part, boolean leggings) {
      return ARMOR[leggings ? 1 : 0][part];
   }

   public static float armorOffsetX(int part, boolean slim) {
      if (!slim) {
         return 0.0F;
      } else {
         return part == 2 ? -0.5F : (part == 3 ? 0.5F : 0.0F);
      }
   }

   public static float armorOffsetY(int part, boolean slim) {
      return !slim || part != 2 && part != 3 ? 0.0F : -0.5F;
   }

   public static boolean armorCovers(int slot, int part) {
      switch (slot) {
         case 0:
            return part == 4 || part == 5;
         case 1:
            return part == 1 || part == 4 || part == 5;
         case 2:
            return part == 1 || part == 2 || part == 3;
         default:
            return part == 0;
      }
   }

   public static void corner(PlayerModel.Box b, int c, float[] out) {
      float x1 = b.x - b.inflate;
      float x2 = b.x + b.w + b.inflate;
      if (b.mirror) {
         float t = x1;
         x1 = x2;
         x2 = t;
      }

      float y1 = b.y - b.inflate;
      float y2 = b.y + b.h + b.inflate;
      float z1 = b.z - b.inflate;
      float z2 = b.z + b.d + b.inflate;
      out[0] = c != 1 && c != 2 && c != 5 && c != 6 ? x1 : x2;
      out[1] = c != 2 && c != 3 && c != 6 && c != 7 ? y1 : y2;
      out[2] = c >= 4 ? z2 : z1;
   }

   public static void faceUV(PlayerModel.Box b, int face, float[] out) {
      float u = b.u;
      float v = b.v;
      float w = b.w;
      float h = b.h;
      float d = b.d;
      switch (face) {
         case 0:
            set(out, u + d + w, v + d, u + d + w + d, v + d + h);
            break;
         case 1:
            set(out, u, v + d, u + d, v + d + h);
            break;
         case 2:
            set(out, u + d, v, u + d + w, v + d);
            break;
         case 3:
            set(out, u + d + w, v + d, u + d + w + w, v);
            break;
         case 4:
            set(out, u + d, v + d, u + d + w, v + d + h);
            break;
         default:
            set(out, u + d + w + d, v + d, u + d + w + d + w, v + d + h);
      }
   }

   private static void set(float[] o, float a, float b, float c, float d) {
      o[0] = a;
      o[1] = b;
      o[2] = c;
      o[3] = d;
   }

   private static void angles(PoseSnapshot p) {
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

      float armRpY = p.slim ? 2.5F : 2.0F;
      set(0, headX, headY, 0.0F, 0.0F, headRpY, 0.0F);
      set(1, bodyX, bodyY, 0.0F, 0.0F, 0.0F, 0.0F);
      set(2, rArmX, rArmY, rArmZ, rArmRpX, armRpY, rArmRpZ);
      set(3, lArmX, lArmY, lArmZ, lArmRpX, armRpY, lArmRpZ);
      set(4, rLegX, 0.0F, 0.0F, -1.9F, legRpY, legRpZ);
      set(5, lLegX, 0.0F, 0.0F, 1.9F, legRpY, legRpZ);
   }

   private static void set(int i, float ax, float ay, float az, float rpx, float rpy, float rpz) {
      ANG_X[i] = ax;
      ANG_Y[i] = ay;
      ANG_Z[i] = az;
      RP_X[i] = rpx;
      RP_Y[i] = rpy;
      RP_Z[i] = rpz;
   }

   public static void frames(PoseSnapshot pose, double[] cx, double[] cy, double[] cz, double[] rot) {
      angles(pose);
      rotY(Math.toRadians(180.0F - pose.bodyYaw), E);
      rotZ(Math.toRadians(pose.deathAngle), R1);
      mul(E, R1, E);
      set9(R1, -1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 1.0);
      mul(E, R1, E);
      double s = pose.scale;
      double baseY = -1.5078125 + (pose.sneaking ? 0.2 : 0.0);

      for (int part = 0; part < 6; part++) {
         rotZ(ANG_Z[part], P);
         rotY(ANG_Y[part], R1);
         mul(P, R1, P);
         rotX(ANG_X[part], R1);
         mul(P, R1, P);
         mul(E, P, R1);
         System.arraycopy(R1, 0, rot, part * 9, 9);
         double ox = RP_X[part] / 16.0;
         double oy = baseY + RP_Y[part] / 16.0;
         double oz = RP_Z[part] / 16.0;
         double px = pose.x + (E[0] * ox + E[1] * oy + E[2] * oz) * s;
         double py = pose.y + (E[3] * ox + E[4] * oy + E[5] * oz) * s;
         double pz = pose.z + (E[6] * ox + E[7] * oy + E[8] * oz) * s;
         PlayerModel.Box b = base(part, pose.slim);
         double lx = b.cx() / 16.0 * s;
         double ly = b.cy() / 16.0 * s;
         double lz = b.cz() / 16.0 * s;
         cx[part] = px + R1[0] * lx + R1[1] * ly + R1[2] * lz;
         cy[part] = py + R1[3] * lx + R1[4] * ly + R1[5] * lz;
         cz[part] = pz + R1[6] * lx + R1[7] * ly + R1[8] * lz;
      }
   }

   public static void capeFrame(double[] bodyRot, double bcx, double bcy, double bcz, float tiltDegrees, boolean sneaking, double[] outCenter, double[] outRot) {
      rotX(Math.toRadians(tiltDegrees), P);
      rotY(Math.PI, R1);
      mul(P, R1, P);
      mul9(bodyRot, P, outRot);
      double hx = 0.0;
      double hy = (sneaking ? 2 : 0) - 6.0;
      double hz = 2.125;
      double lx = 0.0;
      double ly = 8.0;
      double lz = -0.5;
      double s = 0.05859375;
      double wx = bodyRot[0] * hx + bodyRot[1] * hy + bodyRot[2] * hz;
      double wy = bodyRot[3] * hx + bodyRot[4] * hy + bodyRot[5] * hz;
      double wz = bodyRot[6] * hx + bodyRot[7] * hy + bodyRot[8] * hz;
      outCenter[0] = bcx + (wx + outRot[0] * lx + outRot[1] * ly + outRot[2] * lz) * s;
      outCenter[1] = bcy + (wy + outRot[3] * lx + outRot[4] * ly + outRot[5] * lz) * s;
      outCenter[2] = bcz + (wz + outRot[6] * lx + outRot[7] * ly + outRot[8] * lz) * s;
   }

   private static void mul(double[] a, double[] b, double[] out) {
      for (int r = 0; r < 3; r++) {
         for (int c = 0; c < 3; c++) {
            T[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
         }
      }

      System.arraycopy(T, 0, out, 0, 9);
   }

   public static void mul9(double[] a, double[] b, double[] out) {
      mul(a, b, out);
   }

   private static void rotX(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set9(out, 1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c);
   }

   private static void rotY(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set9(out, c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c);
   }

   private static void rotZ(double a, double[] out) {
      double c = Math.cos(a);
      double s = Math.sin(a);
      set9(out, c, -s, 0.0, s, c, 0.0, 0.0, 0.0, 1.0);
   }

   private static void set9(double[] m, double a, double b, double c, double d, double e, double f, double g, double h, double i) {
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

   static {
      BASE_WIDE[0] = BASE_SLIM[0] = new PlayerModel.Box(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 64, 0.0F, false);
      WEAR_WIDE[0] = WEAR_SLIM[0] = new PlayerModel.Box(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 32, 0, 64, 64, 0.5F, false);
      BASE_WIDE[1] = BASE_SLIM[1] = new PlayerModel.Box(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, 64, 64, 0.0F, false);
      WEAR_WIDE[1] = WEAR_SLIM[1] = new PlayerModel.Box(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 32, 64, 64, 0.25F, false);
      BASE_WIDE[2] = new PlayerModel.Box(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 16, 64, 64, 0.0F, false);
      WEAR_WIDE[2] = new PlayerModel.Box(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 32, 64, 64, 0.25F, false);
      BASE_SLIM[2] = new PlayerModel.Box(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, 40, 16, 64, 64, 0.0F, false);
      WEAR_SLIM[2] = new PlayerModel.Box(-2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, 40, 32, 64, 64, 0.25F, false);
      BASE_WIDE[3] = new PlayerModel.Box(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 32, 48, 64, 64, 0.0F, false);
      WEAR_WIDE[3] = new PlayerModel.Box(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 48, 48, 64, 64, 0.25F, false);
      BASE_SLIM[3] = new PlayerModel.Box(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, 32, 48, 64, 64, 0.0F, false);
      WEAR_SLIM[3] = new PlayerModel.Box(-1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F, 48, 48, 64, 64, 0.25F, false);
      BASE_WIDE[4] = BASE_SLIM[4] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 64, 0.0F, false);
      WEAR_WIDE[4] = WEAR_SLIM[4] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 32, 64, 64, 0.25F, false);
      BASE_WIDE[5] = BASE_SLIM[5] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 16, 48, 64, 64, 0.0F, false);
      WEAR_WIDE[5] = WEAR_SLIM[5] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 48, 64, 64, 0.25F, false);

      for (int k = 0; k < 2; k++) {
         float g = k == 0 ? 1.0F : 0.5F;
         ARMOR[k][0] = new PlayerModel.Box(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, 0, 0, 64, 32, g, false);
         ARMOR[k][1] = new PlayerModel.Box(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, 16, 16, 64, 32, g, false);
         ARMOR[k][2] = new PlayerModel.Box(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 16, 64, 32, g, false);
         ARMOR[k][3] = new PlayerModel.Box(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, 40, 16, 64, 32, g, true);
         ARMOR[k][4] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, g, false);
         ARMOR[k][5] = new PlayerModel.Box(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F, 0, 16, 64, 32, g, true);
      }

      FACE_CORNERS = new int[][]{{5, 1, 2, 6}, {0, 4, 7, 3}, {5, 4, 0, 1}, {2, 3, 7, 6}, {1, 0, 3, 2}, {4, 5, 6, 7}};
      ANG_X = new float[6];
      ANG_Y = new float[6];
      ANG_Z = new float[6];
      RP_X = new float[6];
      RP_Y = new float[6];
      RP_Z = new float[6];
      E = new double[9];
      P = new double[9];
      R1 = new double[9];
      T = new double[9];
   }

   public static final class Box {
      public final float x;
      public final float y;
      public final float z;
      public final float w;
      public final float h;
      public final float d;
      public final int u;
      public final int v;
      public final int texW;
      public final int texH;
      public final float inflate;
      public final boolean mirror;

      Box(float x, float y, float z, float w, float h, float d, int u, int v, int texW, int texH, float inflate, boolean mirror) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.w = w;
         this.h = h;
         this.d = d;
         this.u = u;
         this.v = v;
         this.texW = texW;
         this.texH = texH;
         this.inflate = inflate;
         this.mirror = mirror;
      }

      public float cx() {
         return this.x + this.w / 2.0F;
      }

      public float cy() {
         return this.y + this.h / 2.0F;
      }

      public float cz() {
         return this.z + this.d / 2.0F;
      }
   }
}
