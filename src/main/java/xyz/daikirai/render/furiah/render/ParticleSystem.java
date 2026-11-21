package xyz.daikirai.render.furiah.render;

public final class ParticleSystem {
   public static final int GLOW = 0;
   public static final int SPARK = 1;
   public static final int HEART = 2;
   public static final int STAR = 3;
   private final int capacity;
   private int count;
   private int recycle;
   private final double[] x;
   private final double[] y;
   private final double[] z;
   private final float[] vx;
   private final float[] vy;
   private final float[] vz;
   private final float[] age;
   private final float[] life;
   private final float[] size;
   private final float[] endSize;
   private final float[] drag;
   private final float[] gravity;
   private final float[] rot;
   private final float[] spin;
   private final float[] sway;
   private final float[] swayFreq;
   private final float[] phase;
   private final int[] color;
   private final int[] kind;

   public ParticleSystem(int capacity) {
      this.capacity = capacity;
      this.x = new double[capacity];
      this.y = new double[capacity];
      this.z = new double[capacity];
      this.vx = new float[capacity];
      this.vy = new float[capacity];
      this.vz = new float[capacity];
      this.age = new float[capacity];
      this.life = new float[capacity];
      this.size = new float[capacity];
      this.endSize = new float[capacity];
      this.drag = new float[capacity];
      this.gravity = new float[capacity];
      this.rot = new float[capacity];
      this.spin = new float[capacity];
      this.sway = new float[capacity];
      this.swayFreq = new float[capacity];
      this.phase = new float[capacity];
      this.color = new int[capacity];
      this.kind = new int[capacity];
   }

   public void spawn(
      int kind,
      double px,
      double py,
      double pz,
      float velX,
      float velY,
      float velZ,
      float life,
      float size,
      float endSize,
      int color,
      float gravity,
      float drag,
      float swayAmp,
      float seed
   ) {
      int i;
      if (this.count < this.capacity) {
         i = this.count++;
      } else {
         i = this.recycle;
         this.recycle = (this.recycle + 1) % this.capacity;
      }

      this.kind[i] = kind;
      this.x[i] = px;
      this.y[i] = py;
      this.z[i] = pz;
      this.vx[i] = velX;
      this.vy[i] = velY;
      this.vz[i] = velZ;
      this.age[i] = 0.0F;
      this.life[i] = Math.max(0.05F, life);
      this.size[i] = size;
      this.endSize[i] = endSize;
      this.color[i] = color;
      this.gravity[i] = gravity;
      this.drag[i] = drag;
      this.sway[i] = swayAmp;
      this.swayFreq[i] = 1.6F + seed * 7.31F % 1.0F * 1.4F;
      this.phase[i] = seed * (float) (Math.PI * 2);
      this.rot[i] = (seed * 3.17F % 1.0F - 0.5F) * 0.6F;
      this.spin[i] = (seed * 5.71F % 1.0F - 0.5F) * 1.2F;
   }

   public void update(float dt) {
      if (!(dt <= 0.0F)) {
         int i = 0;

         while (i < this.count) {
            this.age[i] = this.age[i] + dt;
            if (this.age[i] >= this.life[i]) {
               this.removeAt(i);
            } else {
               this.vy[i] = this.vy[i] + this.gravity[i] * dt;
               if (this.drag[i] > 0.0F) {
                  float damp = (float)Math.exp(-this.drag[i] * dt);
                  this.vx[i] = this.vx[i] * damp;
                  this.vy[i] = this.vy[i] * damp;
                  this.vz[i] = this.vz[i] * damp;
               }

               this.x[i] = this.x[i] + this.vx[i] * dt;
               this.y[i] = this.y[i] + this.vy[i] * dt;
               this.z[i] = this.z[i] + this.vz[i] * dt;
               this.rot[i] = this.rot[i] + this.spin[i] * dt;
               i++;
            }
         }
      }
   }

   private void removeAt(int i) {
      int last;
      if (i != (last = --this.count)) {
         this.kind[i] = this.kind[last];
         this.x[i] = this.x[last];
         this.y[i] = this.y[last];
         this.z[i] = this.z[last];
         this.vx[i] = this.vx[last];
         this.vy[i] = this.vy[last];
         this.vz[i] = this.vz[last];
         this.age[i] = this.age[last];
         this.life[i] = this.life[last];
         this.size[i] = this.size[last];
         this.endSize[i] = this.endSize[last];
         this.color[i] = this.color[last];
         this.gravity[i] = this.gravity[last];
         this.drag[i] = this.drag[last];
         this.sway[i] = this.sway[last];
         this.swayFreq[i] = this.swayFreq[last];
         this.phase[i] = this.phase[last];
         this.rot[i] = this.rot[last];
         this.spin[i] = this.spin[last];
      }
   }

   public void render(double offX, double offY, double offZ) {
      if (this.count != 0) {
         RenderState.additive();

         for (int pass = 0; pass < 5; pass++) {
            for (int i = 0; i < this.count; i++) {
               int k = this.kind[i];
               float t = this.age[i] / this.life[i];
               float a = Easing.smoothstep(0.0F, 0.12F, t) * (1.0F - Easing.smoothstep(0.45F, 1.0F, t));
               if (!(a <= 0.003F)) {
                  float s = this.size[i] + (this.endSize[i] - this.size[i]) * t;
                  float wob = this.sway[i] == 0.0F ? 0.0F : this.sway[i] * (float)Math.sin(this.age[i] * this.swayFreq[i] + this.phase[i]);
                  double px = this.x[i] - offX + wob * Math.cos(this.phase[i]);
                  double py = this.y[i] - offY;
                  double pz = this.z[i] - offZ + wob * Math.sin(this.phase[i]);
                  int c = this.color[i];
                  switch (pass) {
                     case 0:
                        Vfx.sprite(
                           Textures.GLOW, px, py, pz, s * (k == 1 ? 3.2F : (k == 0 ? 1.0F : 2.3F)), 0.0F, ColorUtil.mulAlpha(c, a * (k == 0 ? 0.9F : 0.33F))
                        );
                        break;
                     case 1:
                        if (k == 1) {
                           Vfx.sprite(Textures.SPARK, px, py, pz, s, 0.0F, ColorUtil.mulAlpha(ColorUtil.core(c), a));
                        }
                        break;
                     case 2:
                        if (k == 2) {
                           Vfx.sprite(Textures.HEART, px, py, pz, s, this.rot[i], ColorUtil.mulAlpha(c, a));
                        }
                        break;
                     case 3:
                        if (k == 3) {
                           Vfx.sprite(Textures.STAR, px, py, pz, s, this.rot[i], ColorUtil.mulAlpha(c, a));
                        }
                        break;
                     default:
                        if (k == 2) {
                           Vfx.sprite(Textures.HEART, px, py, pz, s * 0.5F, this.rot[i], ColorUtil.mulAlpha(ColorUtil.core(c), a * 0.55F));
                        } else if (k == 3) {
                           Vfx.sprite(Textures.STAR, px, py, pz, s * 0.5F, this.rot[i], ColorUtil.mulAlpha(ColorUtil.core(c), a * 0.55F));
                        }
                  }
               }
            }
         }
      }
   }

   public void clear() {
      this.count = 0;
   }

   public boolean isEmpty() {
      return this.count == 0;
   }

   public int size() {
      return this.count;
   }
}
