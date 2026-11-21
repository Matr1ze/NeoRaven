package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.MiniMesh;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.util.MathHelper;

final class PetalsStyle extends AmbientStyleBase {
   private static final int[] CHERRY = new int[]{-18475, -13857, -24892, -10522, -548412};
   private static final int[] AUTUMN = new int[]{-2074837, -1013212, -1525719, -4179669, -4889315, -678078};
   private static final int[] GREEN = new int[]{-9650102, -11624643, -7613606, -12612561, -6039177};
   private static final float SETTLE_SECONDS = 2.2F;
   final ModeSetting<PetalsStyle.Kind> kind;
   final BooleanSetting tinted;
   final SliderSetting fallSpeed;
   final SliderSetting flutter;
   private final float[] flow = new float[3];
   private final double[] corner = new double[12];

   PetalsStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.PETALS, spec());
      this.kind = this.setting(new ModeSetting<>("petals.kind", "Type", "What falls.", PetalsStyle.Kind.class, PetalsStyle.Kind.CHERRY));
      this.tinted = this.setting(new BooleanSetting("petals.tinted", "Tinted",
            "Use a uniform palette color. Otherwise retain the petal and leaf shading.", false));
      this.fallSpeed = this.setting(new SliderSetting("petals.fall", "Fall Speed", "How fast they fall.", 0.2, 3.0, 1.0, 0.05).unit("×"));
      this.flutter = this.setting(
         new SliderSetting("petals.flutter", "Flutter", "How much they tumble and swing as they fall.", 0.0, 100.0, 60.0, 5.0).unit("%")
      );
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.005F;
      s.nearBias = 1.6F;
      s.opacity = 95.0F;
      s.glow = false;
      s.followWind = 70.0F;
      s.heightMin = 0.0F;
      s.heightMax = 16.0F;
      s.heightLimit = 50.0F;
      s.cap = 600;
      s.collides = true;
      s.light = true;
      s.densityHelp = "How many petals or leaves there are.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         if (!fresh && !Double.isNaN(p.ground[i])) {
            double top = this.bandTop(env, i);
            double y = Math.max(p.ground[i] + this.lowest(), top - this.rng.next() * Math.min(2.0, top - p.ground[i]));
            if (!env.solidAt(p.x[i], y, p.z[i])) {
               p.teleport(i, p.x[i], y, p.z[i]);
            }
         }

         p.a[i] = this.rng.next() * 6.283F;
         p.b[i] = this.rng.signed() * 1.5F;
         p.c[i] = this.rng.next() * 6.283F;
         p.vx[i] = MathHelper.cos(p.c[i] * 2.3F);
         p.vz[i] = MathHelper.sin(p.c[i] * 2.3F);
         PetalsStyle.Kind k = this.kind.get();
         p.d[i] = k == PetalsStyle.Kind.AUTUMN ? (this.rng.next() < 0.55F ? 2 : 1) : (k == PetalsStyle.Kind.GREEN ? 1.0F : 0.0F);
         p.e[i] = 0.0F;
         p.size[i] = this.rng.range(0.7F, 1.3F);
         p.life[i] = this.rng.range(0.75F, 1.25F);
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      if (p.e[i] > 0.0F) {
         p.e[i] = p.e[i] + dt;
         return p.e[i] < 2.2F;
      } else {
         float sp = this.speed.getFloat();
         float fl = this.flutter.getFloat() / 100.0F;
         this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
         float phase = p.age[i] * 2.4F * sp + p.c[i];
         float swing = MathHelper.sin(phase);
         float fall = 0.95F * this.fallSpeed.getFloat() * p.life[i] * (1.0F - 0.45F * fl * swing * swing);
         float sx = p.vx[i] * swing * fl * 1.1F;
         float sz = p.vz[i] * swing * fl * 1.1F;
         double nx = p.x[i] + (this.flow[0] + sx) * sp * dt;
         double ny = p.y[i] + (this.flow[1] * 0.3F - fall) * sp * dt;
         double nz = p.z[i] + (this.flow[2] + sz) * sp * dt;
         p.a[i] = p.a[i] + p.b[i] * sp * dt;
         double top = env.outdoorsOnly ? env.groundAt(nx, nz) : Double.NaN;
         boolean hit = !Double.isNaN(top) && ny <= top;
         if (!hit && !env.outdoorsOnly && env.solidAt(nx, ny, nz)) {
            hit = true;
            top = Math.floor(ny) + 1.0;
         }

         if (hit && this.collides.get()) {
            if (!env.liquidAt(nx, top - 0.5, nz) && (env.outdoorsOnly || !env.solidAt(nx, top + 0.05, nz))) {
               p.x[i] = nx;
               p.y[i] = top + 0.02;
               p.z[i] = nz;
               p.e[i] = 0.001F;
               return true;
            } else {
               return false;
            }
         } else if (!Double.isNaN(p.ground[i]) && ny < p.ground[i] - 3.0) {
            return false;
         } else {
            p.x[i] = nx;
            p.y[i] = ny;
            p.z[i] = nz;
            if (!Double.isNaN(top)) {
               p.ground[i] = top;
            }

            return true;
         }
      }
   }

   @Override
   protected boolean wrap(AmbientEnv env, int i, float r) {
      return this.pool.e[i] > 0.0F || super.wrap(env, i, r);
   }

   private int ownColor(int i) {
      switch ((PetalsStyle.Kind)this.kind.get()) {
         case AUTUMN:
            return AUTUMN[(int)(this.hash(i, 9) * AUTUMN.length) % AUTUMN.length];
         case GREEN:
            return GREEN[(int)(this.hash(i, 9) * GREEN.length) % GREEN.length];
         default:
            return CHERRY[(int)(this.hash(i, 9) * CHERRY.length) % CHERRY.length];
      }
   }

   private int petalColor(int i) {
      int base = this.color.getColor(this.hash(i, 9));
      if (this.tinted.get()) return base;
      int pigment = this.ownColor(i);
      float shade = 0.75F + (ColorUtil.red(pigment) + ColorUtil.green(pigment) + ColorUtil.blue(pigment)) / 3060.0F;
      return ColorUtil.shade(base, shade);
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float cell = 0.33333334F;
      float s0 = 0.3F * this.size.getFloat();
      int[] order = this.backToFront(env);
      RenderState.translucent(false);

      for (int k = 0; k < this.pool.count; k++) {
         int i = order[k];
         float s = s0 * this.pool.size[i];
         if (this.place(env, i, s)) {
            float a = this.fade(env, i, r);
            if (this.pool.e[i] > 0.0F) {
               a *= 1.0F - Easing.smoothstep(0.88000005F, 2.2F, this.pool.e[i]);
            }

            if (!(a <= 0.004F) && !(this.fogK >= 1.0F)) {
               int c = this.petalColor(i);
               c = this.fogColor(env, this.lit(env, c));
               float u0 = (int)this.pool.d[i] * cell;
               this.flutterQuad(env, i, Textures.FOLIAGE_ATLAS, u0, u0 + cell, s, c, a);
            }
         }
      }

      Vfx.flush();
   }

   private void flutterQuad(AmbientEnv env, int i, int tex, float u0, float u1, float s, int c, float a) {
      AmbientPool p = this.pool;
      double half = s * 0.5;
      double yaw = p.a[i] + p.b[i] * this.speed.getFloat() * 0.05F * (env.partialTicks - 1.0F);
      double landed = p.e[i] > 0.0F ? 1.0 : 0.0;
      double t = p.age[i] + env.partialTicks * 0.05;
      float fl = this.flutter.getFloat() / 100.0F;
      double rock = landed > 0.0
         ? 0.0
         : 0.6 * fl * Math.sin(t * 2.4 * this.speed.getFloat() + p.c[i]) + 0.5 + (fl > 0.0F ? 0.6 * Math.sin(t * 0.9 + p.c[i] * 3.0) : 0.0);
      double ax = this.hash(i, 1) * 2.0F - 1.0F;
      double az = this.hash(i, 2) * 2.0F - 1.0F;
      MiniMesh.rotation(ax, 0.15, az, rock);
      double cy = Math.cos(yaw);
      double sy = Math.sin(yaw);
      double ux = cy * half;
      double uz = sy * half;
      double vx = -sy * half;
      double vz = cy * half;
      this.corner(ux, uz, vx, vz, -1, -1, 0);
      this.corner(ux, uz, vx, vz, 1, -1, 3);
      this.corner(ux, uz, vx, vz, 1, 1, 6);
      this.corner(ux, uz, vx, vz, -1, 1, 9);
      float light = (float)(0.72 + 0.28 * Math.abs(MiniMesh.ry(0.0, 1.0, 0.0)));
      int shaded = ColorUtil.mulAlpha(ColorUtil.shade(c, light), a);
      double lift = landed > 0.0 ? 0.012 : 0.0;
      Vfx.quadUV(
         tex,
         this.rx + this.corner[0],
         this.ry + this.corner[1] + lift,
         this.rz + this.corner[2],
         u0,
         1.0,
         shaded,
         this.rx + this.corner[3],
         this.ry + this.corner[4] + lift,
         this.rz + this.corner[5],
         u1,
         1.0,
         shaded,
         this.rx + this.corner[6],
         this.ry + this.corner[7] + lift,
         this.rz + this.corner[8],
         u1,
         0.0,
         shaded,
         this.rx + this.corner[9],
         this.ry + this.corner[10] + lift,
         this.rz + this.corner[11],
         u0,
         0.0,
         shaded
      );
   }

   private void corner(double ux, double uz, double vx, double vz, int a, int b, int o) {
      double lx = ux * a + vx * b;
      double lz = uz * a + vz * b;
      this.corner[o] = MiniMesh.rx(lx, 0.0, lz);
      this.corner[o + 1] = MiniMesh.ry(lx, 0.0, lz);
      this.corner[o + 2] = MiniMesh.rz(lx, 0.0, lz);
   }

   static enum Kind implements Displayable {
      CHERRY("Cherry Petals", "Pink petals"),
      AUTUMN("Autumn Leaves", "Red, orange and golden leaves"),
      GREEN("Green Leaves", "Fresh green leaves");

      private final String displayName;
      private final String description;

      private Kind(String displayName, String description) {
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
