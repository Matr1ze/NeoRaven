package xyz.daikirai.render.furiah.module.trails.styles;

import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SmokeStyle extends TrailStyleBase {
   public final ModeSetting<SmokeStyle.Variant> variant;
   public final SliderSetting rise;
   public final SliderSetting spread;
   public final SliderSetting turbulence;
   private final Rng rng = new Rng();

   public SmokeStyle(FuriahTrails module) {
      super(module, TrailStyle.SMOKE, spec().size(0.2, 1.2, 0.45, 0.01).length(2.0).anchor(TrailAnchor.FEET, false).windy());
      this.variant = this.setting(new ModeSetting<>("smoke.variant", "Variant", "Smoke, mist or ink.", SmokeStyle.Variant.class, SmokeStyle.Variant.SMOKE));
      this.rise = this.setting(new SliderSetting("smoke.rise", "Rise", "How fast the puffs rise.", 0.0, 100.0, 40.0, 1.0).unit("%"));
      this.spread = this.setting(new SliderSetting("smoke.spread", "Spread", "How far the puffs spread sideways.", 0.0, 100.0, 50.0, 1.0).unit("%"));
      this.turbulence = this.setting(new SliderSetting("smoke.turbulence", "Turbulence", "How much the puffs swirl.", 0.0, 100.0, 40.0, 1.0).unit("%"));
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new SmokeStyle.Puffs();
   }

   public boolean sortedForTest(TrailFrame f) {
      SmokeStyle.Puffs pf = (SmokeStyle.Puffs)f.store(this.type);
      if (pf == null) {
         return true;
      } else {
         for (int o = 1; o < pf.n; o++) {
            if (pf.dist[pf.order[o]] > pf.dist[pf.order[o - 1]] + 1.0E-9) {
               return false;
            }
         }

         return true;
      }
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      if (!p.ghost) {
         int due = this.piecesDue(p, 5.0, 10);
         if (due != 0) {
            SmokeStyle.Puffs puffs = this.store(f);
            double yaw = Math.toRadians(p.pose.bodyYaw);
            float fx = (float)(-Math.sin(yaw));
            float fz = (float)Math.cos(yaw);
            float[] w = this.wind(f, p.headX, p.headY, p.headZ);
            float windX = w[0];
            float windY = w[1];
            float windZ = w[2];
            float sp = this.spread.getFloat() / 100.0F;
            float up = this.rise.getFloat() / 100.0F;
            float tb = this.turbulence.getFloat() / 100.0F;
            float sz = this.size.getFloat() * (float)Math.sqrt(Math.max(0.25F, 1.0F + this.speedBoost(p.speed))) * p.widthScale;

            for (int i = 0; i < due; i++) {
               if (this.module.piecesFull(f)) {
                  return;
               }

               this.rng.seed((int)(f.now * 3001.0) + i * 97 + p.entityId * 13);
               double x = p.headX - fx * 0.2 + this.rng.signed() * 0.12;
               double y = p.headY + 0.05 + this.rng.next() * 0.1;
               double z = p.headZ - fz * 0.2 + this.rng.signed() * 0.12;
               if (!(f.nearFade(x, y, z) < 0.4F)) {
                  int k = puffs.add();
                  puffs.x[k] = x;
                  puffs.y[k] = y;
                  puffs.z[k] = z;
                  puffs.vx[k] = windX + this.rng.signed() * 0.6F * sp - fx * 0.3F;
                  puffs.vy[k] = windY + (0.15F + this.rng.next() * 0.5F) * up;
                  puffs.vz[k] = windZ + this.rng.signed() * 0.6F * sp - fz * 0.3F;
                  puffs.born[k] = puffs.clock;
                  puffs.life[k] = this.lengthNow(p.speed) * (0.75F + this.rng.next() * 0.5F);
                  puffs.size[k] = sz * (0.7F + this.rng.next() * 0.6F);
                  puffs.rot[k] = this.rng.next() * 6.283F;
                  puffs.spin[k] = this.rng.signed() * 0.6F;
                  puffs.alpha[k] = p.alpha;
                  puffs.colorPos[k] = this.rng.next();
                  puffs.turb[k] = 0.2F + 1.2F * tb;
                  puffs.phase[k] = this.rng.next() * 6.283F;
                  puffs.speed[k] = p.speed;
                  puffs.tint[k] = this.tint;
               }
            }
         }
      }
   }

   @Override
   protected void drawStore(TrailFrame f, TrailStyleBase.Store store) {
      SmokeStyle.Puffs pf = (SmokeStyle.Puffs)store;
      int i2 = 0;

      while (i2 < pf.n) {
         double dx = pf.x[i2] - f.eyeX;
         double dy = pf.y[i2] - f.eyeY;
         double dz = pf.z[i2] - f.eyeZ;
         pf.dist[i2] = dx * dx + dy * dy + dz * dz;

         int j;
         for (j = i2; j > 0 && pf.dist[pf.order[j - 1]] < pf.dist[i2]; j--) {
            pf.order[j] = pf.order[j - 1];
         }

         pf.order[j] = i2++;
      }

      SmokeStyle.Variant v = this.variant.get();
      float op = this.opacity.getFloat() / 100.0F;
      float g = this.glow.getFloat() / 100.0F;
      if (v == SmokeStyle.Variant.MIST) {
         RenderState.additive();
      } else {
         RenderState.translucent(false);
      }

      for (int o = 0; o < pf.n; o++) {
         int i = pf.order[o];
         float u = Math.min(1.0F, (pf.clock - pf.born[i]) / Math.max(0.05F, pf.life[i]));
         double wx = pf.x[i];
         double wy = pf.y[i];
         double wz = pf.z[i];
         float a = op * pf.alpha[i] * Math.min(1.0F, u * 8.0F) * this.fade(u) * f.nearFade(wx, wy, wz) * f.fog(wx, wy, wz);
         if (!(a <= 0.004F)) {
            float s = pf.size[i] * (1.0F + 1.6F * u);
            int c = lit(pf.tint[i] != 0 ? pf.tint[i] : this.colorAt(pf.colorPos[i], u, pf.speed[i], f.colorTime, 0.0F), f, wx, wy, wz);
            double x = wx - f.viewX;
            double y = wy - f.viewY;
            double z = wz - f.viewZ;
            switch (v) {
               case MIST:
                  Vfx.sprite(Textures.SMOKE, x, y, z, s, pf.rot[i], ColorUtil.mulAlpha(ColorUtil.lerp(c, -1, 0.35F), a * 0.5F));
                  break;
               case INK:
                  Vfx.sprite(Textures.SMOKE, x, y, z, s, pf.rot[i], ColorUtil.mulAlpha(-15725032, a * 0.8F));
                  break;
               default:
                  Vfx.sprite(Textures.SMOKE, x, y, z, s, pf.rot[i], ColorUtil.mulAlpha(c, a * 0.7F));
            }
         }
      }

      if (v == SmokeStyle.Variant.INK && g > 0.0F) {
         RenderState.additive();

         for (int var33 = 0; var33 < pf.n; var33++) {
            int i = pf.order[var33];
            float u = Math.min(1.0F, (pf.clock - pf.born[i]) / Math.max(0.05F, pf.life[i]));
            double wx = pf.x[i];
            double wy = pf.y[i];
            double wz = pf.z[i];
            float a = op * pf.alpha[i] * Math.min(1.0F, u * 8.0F) * this.fade(u) * f.nearFade(wx, wy, wz) * f.fog(wx, wy, wz) * g;
            if (!(a <= 0.004F)) {
               float s = pf.size[i] * (1.0F + 1.6F * u) * 1.12F;
               int c = lit(pf.tint[i] != 0 ? pf.tint[i] : this.colorAt(pf.colorPos[i], u, pf.speed[i], f.colorTime, 0.0F), f, wx, wy, wz);
               Vfx.sprite(Textures.SMOKE, wx - f.viewX, wy - f.viewY, wz - f.viewZ, s, pf.rot[i] + 0.4F, ColorUtil.mulAlpha(c, a * 0.22F));
            }
         }
      }

      RenderState.additive();
   }

   static final class Puffs implements TrailStyleBase.Store {
      static final int CAP = 320;
      final double[] x = new double[320];
      final double[] y = new double[320];
      final double[] z = new double[320];
      final float[] vx = new float[320];
      final float[] vy = new float[320];
      final float[] vz = new float[320];
      final float[] born = new float[320];
      final float[] life = new float[320];
      final float[] size = new float[320];
      final float[] rot = new float[320];
      final float[] spin = new float[320];
      final float[] alpha = new float[320];
      final float[] colorPos = new float[320];
      final float[] turb = new float[320];
      final float[] phase = new float[320];
      final float[] speed = new float[320];
      final int[] order = new int[320];
      final int[] tint = new int[320];
      final double[] dist = new double[320];
      int n;
      int oldest;
      float clock;

      int add() {
         if (this.n < 320) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 320;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         if (!(dt <= 0.0F)) {
            int w = 0;
            float damp = (float)Math.exp(-0.9 * dt);

            for (int i = 0; i < this.n; i++) {
               float age = this.clock - this.born[i];
               if (!(age >= this.life[i])) {
                  float t = age * 1.3F + this.phase[i];
                  this.vx[i] = this.vx[i] * damp + (float)Math.sin(t * 1.7F) * this.turb[i] * dt;
                  this.vz[i] = this.vz[i] * damp + (float)Math.cos(t * 1.3F + this.phase[i]) * this.turb[i] * dt;
                  this.vy[i] = this.vy[i] * damp + 0.25F * this.turb[i] * dt * (float)Math.sin(t * 0.9F);
                  this.x[i] = this.x[i] + this.vx[i] * dt;
                  this.y[i] = this.y[i] + this.vy[i] * dt;
                  this.z[i] = this.z[i] + this.vz[i] * dt;
                  this.rot[i] = this.rot[i] + this.spin[i] * dt;
                  if (w != i) {
                     this.copy(i, w);
                  }

                  w++;
               }
            }

            this.n = w;
            this.oldest = 0;
         }
      }

      private void copy(int a, int b) {
         this.x[b] = this.x[a];
         this.y[b] = this.y[a];
         this.z[b] = this.z[a];
         this.vx[b] = this.vx[a];
         this.vy[b] = this.vy[a];
         this.vz[b] = this.vz[a];
         this.born[b] = this.born[a];
         this.life[b] = this.life[a];
         this.size[b] = this.size[a];
         this.rot[b] = this.rot[a];
         this.spin[b] = this.spin[a];
         this.alpha[b] = this.alpha[a];
         this.colorPos[b] = this.colorPos[a];
         this.turb[b] = this.turb[a];
         this.phase[b] = this.phase[a];
         this.speed[b] = this.speed[a];
         this.tint[b] = this.tint[a];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
         this.oldest = 0;
      }
   }

   public static enum Variant implements Displayable {
      SMOKE("Smoke", "Soft, see-through puffs"),
      MIST("Mist", "Bright, airy light"),
      INK("Ink", "Dark curls with a thin glowing edge");

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
