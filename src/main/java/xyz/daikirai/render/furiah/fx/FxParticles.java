package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.MiniMesh;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;

public final class FxParticles {
   public static final int GLOW = 0;
   public static final int SPARK = 1;
   public static final int STREAK = 2;
   public static final int MOTE = 3;
   public static final int ICON = 4;
   public static final int SPARKLE = 5;
   public static final int DROPLET = 6;
   public static final int EMBER = 7;
   public static final int DUST = 8;
   public static final int FLUTTER = 9;
   public static final int CUBE = 10;
   public static final int SHARD = 11;
   public static final int SPECK = 12;
   public static final int POP = 1;
   public static final int TWINKLE = 2;
   public static final int SETTLE = 4;
   public static final int SOLID = 8;
   private static final int SETTLED = 65536;
   private static final int SHAPE_FLOATS = 12;
   private final int capacity;
   private int count;
   private int recycle;
   private int cur;
   private final double[] x;
   private final double[] y;
   private final double[] z;
   private final double[] ground;
   private final float[] vx;
   private final float[] vy;
   private final float[] vz;
   private final float[] age;
   private final float[] life;
   private final float[] size;
   private final float[] endSize;
   private final float[] gravity;
   private final float[] drag;
   private final float[] rot;
   private final float[] spin;
   private final float[] ax;
   private final float[] ay;
   private final float[] az;
   private final float[] restitution;
   private final float[] follow;
   private final float[] colorT;
   private final float[] colorTo;
   private final float[] alpha;
   private final float[] fadeIn;
   private final float[] fadeOut;
   private final float[] swayAmp;
   private final float[] swayPhase;
   private final float[] hot;
   private final float[] wind;
   public boolean preview;
   private final float[] windNow = new float[3];
   private final int[] kind;
   private final int[] color;
   private final int[] flags;
   private final int[] bounces;
   private final int[] texture;
   private final ThemeTint[] source;
   private final Anchor[] anchor;
   private final float[] shape;
   private double colorTime;
   private static final int[] TEXTURES = new int[8];
   private int textureCount;
   private static final double[] CORNER = new double[12];

   public FxParticles(int capacity, boolean preview) {
      this(capacity);
      this.preview = preview;
   }

   public FxParticles(int capacity) {
      this.capacity = capacity;
      this.x = new double[capacity];
      this.y = new double[capacity];
      this.z = new double[capacity];
      this.ground = new double[capacity];
      this.vx = new float[capacity];
      this.vy = new float[capacity];
      this.vz = new float[capacity];
      this.age = new float[capacity];
      this.life = new float[capacity];
      this.size = new float[capacity];
      this.endSize = new float[capacity];
      this.gravity = new float[capacity];
      this.drag = new float[capacity];
      this.rot = new float[capacity];
      this.spin = new float[capacity];
      this.ax = new float[capacity];
      this.ay = new float[capacity];
      this.az = new float[capacity];
      this.restitution = new float[capacity];
      this.follow = new float[capacity];
      this.colorT = new float[capacity];
      this.colorTo = new float[capacity];
      this.alpha = new float[capacity];
      this.fadeIn = new float[capacity];
      this.fadeOut = new float[capacity];
      this.swayAmp = new float[capacity];
      this.swayPhase = new float[capacity];
      this.hot = new float[capacity];
      this.wind = new float[capacity];
      this.kind = new int[capacity];
      this.color = new int[capacity];
      this.flags = new int[capacity];
      this.bounces = new int[capacity];
      this.texture = new int[capacity];
      this.source = new ThemeTint[capacity];
      this.anchor = new Anchor[capacity];
      this.shape = new float[capacity * 12];
   }

   public FxParticles spawn(int kind, double px, double py, double pz, float velX, float velY, float velZ, float life, float size) {
      int i;
      if (this.count < this.capacity) {
         i = this.count++;
      } else {
         i = this.recycle;
         this.recycle = (this.recycle + 1) % this.capacity;
      }

      this.cur = i;
      this.kind[i] = kind;
      this.x[i] = px;
      this.y[i] = py;
      this.z[i] = pz;
      this.vx[i] = velX;
      this.vy[i] = velY;
      this.vz[i] = velZ;
      this.age[i] = 0.0F;
      this.life[i] = Math.max(0.03F, life);
      this.size[i] = size;
      this.endSize[i] = size;
      this.gravity[i] = 0.0F;
      this.drag[i] = 0.0F;
      this.rot[i] = 0.0F;
      this.spin[i] = 0.0F;
      this.ax[i] = 0.0F;
      this.ay[i] = 1.0F;
      this.az[i] = 0.0F;
      this.ground[i] = Double.NaN;
      this.bounces[i] = 0;
      this.restitution[i] = 0.4F;
      this.anchor[i] = null;
      this.follow[i] = 0.0F;
      this.source[i] = null;
      this.colorT[i] = 0.0F;
      this.colorTo[i] = Float.NaN;
      this.color[i] = -1;
      this.alpha[i] = 1.0F;
      this.fadeIn[i] = 0.06F;
      this.fadeOut[i] = 0.55F;
      this.swayAmp[i] = 0.0F;
      this.swayPhase[i] = 0.0F;
      this.hot[i] = 0.0F;
      this.wind[i] = 0.0F;
      this.flags[i] = 0;
      this.texture[i] = 0;
      return this;
   }

   public FxParticles wind(float weight) {
      this.wind[this.cur] = weight;
      return this;
   }

   public FxParticles end(float endSize) {
      this.endSize[this.cur] = endSize;
      return this;
   }

   public FxParticles gravity(float g) {
      this.gravity[this.cur] = g;
      return this;
   }

   public FxParticles drag(float d) {
      this.drag[this.cur] = d;
      return this;
   }

   public FxParticles color(ThemeTint setting, float t) {
      this.source[this.cur] = setting;
      this.colorT[this.cur] = t;
      return this;
   }

   public FxParticles color(int argb) {
      this.source[this.cur] = null;
      this.color[this.cur] = argb;
      return this;
   }

   public FxParticles colorAge(float t) {
      this.colorTo[this.cur] = t;
      return this;
   }

   public FxParticles alpha(float a) {
      this.alpha[this.cur] = a;
      return this;
   }

   public FxParticles hot(float h) {
      this.hot[this.cur] = h;
      return this;
   }

   public FxParticles fade(float in, float outStart) {
      this.fadeIn[this.cur] = in;
      this.fadeOut[this.cur] = outStart;
      return this;
   }

   public FxParticles follow(Anchor a, float weight) {
      this.anchor[this.cur] = a;
      this.follow[this.cur] = weight;
      return this;
   }

   public FxParticles ground(double groundY, int bounces, float restitution) {
      this.ground[this.cur] = groundY;
      this.bounces[this.cur] = bounces;
      this.restitution[this.cur] = restitution;
      return this;
   }

   public FxParticles spin(float rotation, float spin) {
      this.rot[this.cur] = rotation;
      this.spin[this.cur] = spin;
      return this;
   }

   public FxParticles axis(float x, float y, float z) {
      this.ax[this.cur] = x;
      this.ay[this.cur] = y;
      this.az[this.cur] = z;
      return this;
   }

   public FxParticles texture(int tex) {
      this.texture[this.cur] = tex;
      return this;
   }

   public FxParticles delay(float seconds) {
      this.age[this.cur] = -seconds;
      return this;
   }

   public FxParticles flags(int f) {
      int n = this.cur;
      this.flags[n] = this.flags[n] | f;
      return this;
   }

   public FxParticles sway(float amplitude, float phase) {
      this.swayAmp[this.cur] = amplitude;
      this.swayPhase[this.cur] = phase;
      return this;
   }

   public FxParticles shard(float x0, float y0, float x1, float y1, float x2, float y2, double rX, double rY, double rZ, double uX, double uY, double uZ) {
      int o = this.cur * 12;
      this.shape[o] = x0;
      this.shape[o + 1] = y0;
      this.shape[o + 2] = x1;
      this.shape[o + 3] = y1;
      this.shape[o + 4] = x2;
      this.shape[o + 5] = y2;
      this.shape[o + 6] = (float)rX;
      this.shape[o + 7] = (float)rY;
      this.shape[o + 8] = (float)rZ;
      this.shape[o + 9] = (float)uX;
      this.shape[o + 10] = (float)uY;
      this.shape[o + 11] = (float)uZ;
      return this;
   }

   public void update(float dt) {
      if (!(dt <= 0.0F)) {
         long frame = FxFrame.currentFrame();
         boolean windAsked = false;
         int i = 0;

         while (i < this.count) {
            Anchor a = this.anchor[i];
            if (a != null && this.follow[i] != 0.0F && a.frame == frame) {
               this.x[i] = this.x[i] + a.dx * this.follow[i];
               this.y[i] = this.y[i] + a.dy * this.follow[i];
               this.z[i] = this.z[i] + a.dz * this.follow[i];
            }

            if (this.age[i] < 0.0F) {
               int n = i++;
               this.age[n] = this.age[n] + dt;
            } else {
               this.age[i] = this.age[i] + dt;
               if (this.age[i] >= this.life[i]) {
                  this.removeAt(i);
               } else if ((this.flags[i] & 65536) != 0) {
                  i++;
               } else {
                  this.vy[i] = this.vy[i] - this.gravity[i] * dt;
                  if (this.drag[i] > 0.0F) {
                     float damp = (float)Math.exp(-this.drag[i] * dt);
                     this.vx[i] = this.vx[i] * damp;
                     this.vy[i] = this.vy[i] * damp;
                     this.vz[i] = this.vz[i] * damp;
                  }

                  if (this.swayAmp[i] != 0.0F) {
                     float w = 3.1F + this.swayPhase[i] % 1.0F;
                     float s = this.swayAmp[i] * w * (float)Math.cos(this.age[i] * w + this.swayPhase[i]) * dt;
                     this.vx[i] = this.vx[i] + s * (float)Math.cos(this.swayPhase[i] * 7.3F);
                     this.vz[i] = this.vz[i] + s * (float)Math.sin(this.swayPhase[i] * 7.3F);
                  }

                  if (this.wind[i] != 0.0F) {
                     if (!windAsked) {
                        Breeze.at(this.x[i], this.y[i], this.z[i], this.preview, this.windNow);
                        windAsked = true;
                     }

                     this.x[i] = this.x[i] + this.windNow[0] * this.wind[i] * dt;
                     this.y[i] = this.y[i] + this.windNow[1] * this.wind[i] * dt;
                     this.z[i] = this.z[i] + this.windNow[2] * this.wind[i] * dt;
                  }

                  this.x[i] = this.x[i] + this.vx[i] * dt;
                  this.y[i] = this.y[i] + this.vy[i] * dt;
                  this.z[i] = this.z[i] + this.vz[i] * dt;
                  this.rot[i] = this.rot[i] + this.spin[i] * dt;
                  if (!Double.isNaN(this.ground[i]) && this.y[i] < this.ground[i] && this.vy[i] <= 0.0F) {
                     if ((this.flags[i] & 4) != 0) {
                        this.y[i] = this.ground[i];
                        this.vz[i] = 0.0F;
                        this.vy[i] = 0.0F;
                        this.vx[i] = 0.0F;
                        this.flags[i] = this.flags[i] | 65536;
                     } else if (this.bounces[i] > 0) {
                        this.y[i] = this.ground[i] + (this.ground[i] - this.y[i]) * this.restitution[i];
                        this.vy[i] = -this.vy[i] * this.restitution[i];
                        this.vx[i] = this.vx[i] * 0.72F;
                        this.vz[i] = this.vz[i] * 0.72F;
                        this.spin[i] = this.spin[i] * 0.6F;
                        this.bounces[i]--;
                     } else {
                        this.y[i] = this.ground[i];
                        this.vx[i] = this.vx[i] * 0.2F;
                        this.vz[i] = this.vz[i] * 0.2F;
                        this.vy[i] = 0.0F;
                        this.gravity[i] = 0.0F;
                        this.life[i] = Math.min(this.life[i], this.age[i] + 0.12F);
                     }
                  }

                  i++;
               }
            }
         }
      }
   }

   private void removeAt(int i) {
      int last;
      if (i == (last = --this.count)) {
         this.anchor[i] = null;
         this.source[i] = null;
      } else {
         this.x[i] = this.x[last];
         this.y[i] = this.y[last];
         this.z[i] = this.z[last];
         this.ground[i] = this.ground[last];
         this.vx[i] = this.vx[last];
         this.vy[i] = this.vy[last];
         this.vz[i] = this.vz[last];
         this.age[i] = this.age[last];
         this.life[i] = this.life[last];
         this.size[i] = this.size[last];
         this.endSize[i] = this.endSize[last];
         this.gravity[i] = this.gravity[last];
         this.drag[i] = this.drag[last];
         this.rot[i] = this.rot[last];
         this.spin[i] = this.spin[last];
         this.ax[i] = this.ax[last];
         this.ay[i] = this.ay[last];
         this.az[i] = this.az[last];
         this.restitution[i] = this.restitution[last];
         this.follow[i] = this.follow[last];
         this.colorT[i] = this.colorT[last];
         this.colorTo[i] = this.colorTo[last];
         this.alpha[i] = this.alpha[last];
         this.fadeIn[i] = this.fadeIn[last];
         this.fadeOut[i] = this.fadeOut[last];
         this.swayAmp[i] = this.swayAmp[last];
         this.swayPhase[i] = this.swayPhase[last];
         this.hot[i] = this.hot[last];
         this.wind[i] = this.wind[last];
         this.kind[i] = this.kind[last];
         this.color[i] = this.color[last];
         this.flags[i] = this.flags[last];
         this.bounces[i] = this.bounces[last];
         this.texture[i] = this.texture[last];
         this.source[i] = this.source[last];
         this.anchor[i] = this.anchor[last];
         System.arraycopy(this.shape, last * 12, this.shape, i * 12, 12);
         this.anchor[last] = null;
         this.source[last] = null;
      }
   }

   private int baseColor(int i) {
      ThemeTint s = this.source[i];
      if (s == null) {
         return this.color[i];
      } else {
         float to = this.colorTo[i];
         return s.getColor(to != to ? this.colorT[i] : this.colorT[i] + (to - this.colorT[i]) * this.lifeT(i), this.colorTime);
      }
   }

   private float lifeT(int i) {
      return Easing.clamp01(this.age[i] / this.life[i]);
   }

   private float fadeAlpha(int i, float t) {
      float in = this.fadeIn[i] <= 0.0F ? 1.0F : Easing.smoothstep(0.0F, this.fadeIn[i], t);
      float out = 1.0F - Easing.smoothstep(this.fadeOut[i], 1.0F, t);
      float a = in * out * this.alpha[i];
      if ((this.flags[i] & 2) != 0) {
         a *= 0.55F + 0.45F * (float)Math.sin(this.age[i] * (17.0F + this.swayPhase[i] * 11.0F) + this.swayPhase[i] * 40.0F);
      }

      return a;
   }

   private float sizeAt(int i, float t) {
      float s = this.size[i] + (this.endSize[i] - this.size[i]) * t;
      if ((this.flags[i] & 1) != 0) {
         s *= Math.max(0.0F, Easing.outBack(Easing.clamp01(t / 0.2F), 2.2F));
      }

      return s;
   }

   private int emberColor(int c, float t) {
      return t < 0.18F ? ColorUtil.lerp(ColorUtil.core(c), c, t / 0.18F) : ColorUtil.shade(c, 1.0F - 0.6F * Easing.smoothstep(0.4F, 1.0F, t));
   }

   public void render(double offX, double offY, double offZ) {
      if (this.count != 0) {
         this.colorTime = ThemeTint.clock();
         this.collectTextures();
         RenderState.translucent(false);

         for (int i2 = 0; i2 < this.count; i2++) {
            if (this.kind[i2] == 8 && this.visible(i2)) {
               Vfx.sprite(
                  Textures.SMOKE,
                  this.x[i2] - offX,
                  this.y[i2] - offY,
                  this.z[i2] - offZ,
                  this.size(i2),
                  this.rot[i2],
                  ColorUtil.mulAlpha(this.baseColor(i2), this.alphaOf(i2))
               );
            }
         }

         for (int var29 = 0; var29 < this.count; var29++) {
            if (this.kind[var29] == 12 && this.visible(var29)) {
               Vfx.sprite(
                  Textures.WHITE,
                  this.x[var29] - offX,
                  this.y[var29] - offY,
                  this.z[var29] - offZ,
                  this.size(var29),
                  this.rot[var29],
                  ColorUtil.mulAlpha(this.baseColor(var29), this.alphaOf(var29))
               );
            }
         }

         for (int tx = 0; tx < this.textureCount; tx++) {
            for (int i = 0; i < this.count; i++) {
               if (this.kind[i] == 9 && this.texture[i] == TEXTURES[tx] && this.visible(i)) {
                  this.drawFlutter(
                     i, this.x[i] - offX, this.y[i] - offY, this.z[i] - offZ, this.size(i), ColorUtil.mulAlpha(this.baseColor(i), this.alphaOf(i))
                  );
               }
            }
         }

         for (int var30 = 0; var30 < this.count; var30++) {
            int k = this.kind[var30];
            if ((k == 10 || k == 11) && this.visible(var30)) {
               double px = this.x[var30] - offX;
               double py = this.y[var30] - offY;
               double pz = this.z[var30] - offZ;
               MiniMesh.rotation(this.ax[var30], this.ay[var30], this.az[var30], this.rot[var30]);
               if (k == 10) {
                  MiniMesh.cubeFaces(px, py, pz, this.size(var30) * 0.5, this.baseColor(var30), this.alphaOf(var30) * 0.9F);
               } else {
                  this.placeShard(var30, px, py, pz, this.size(var30));
                  MiniMesh.shardFace(this.baseColor(var30), this.alphaOf(var30) * 0.45F);
               }
            }
         }

         for (int var31 = 0; var31 < this.count; var31++) {
            if (this.kind[var31] == 2 && this.visible(var31)) {
               this.streak(var31, offX, offY, offZ, Textures.LINE_GLOW, 1.7F, ColorUtil.mulAlpha(this.baseColor(var31), this.alphaOf(var31) * 0.8F));
            }
         }

         for (int var32 = 0; var32 < this.count; var32++) {
            int k = this.kind[var32];
            if ((k == 1 || k == 3 || k == 7) && this.visible(var32)) {
               int c = k == 7 ? this.emberColor(this.baseColor(var32), this.lifeT(var32)) : this.baseColor(var32);
               Vfx.sprite(
                  Textures.SPARK,
                  this.x[var32] - offX,
                  this.y[var32] - offY,
                  this.z[var32] - offZ,
                  this.size(var32) * 1.5F,
                  0.0F,
                  ColorUtil.mulAlpha(c, this.alphaOf(var32) * 0.75F)
               );
            }
         }

         for (int var33 = 0; var33 < this.count; var33++) {
            if (this.kind[var33] == 5 && this.visible(var33)) {
               Vfx.sprite(
                  Textures.SPARKLE,
                  this.x[var33] - offX,
                  this.y[var33] - offY,
                  this.z[var33] - offZ,
                  this.size(var33) * this.twinkle(var33),
                  this.rot[var33],
                  ColorUtil.mulAlpha(this.baseColor(var33), this.alphaOf(var33) * 0.9F)
               );
            }
         }

         for (int var34 = 0; var34 < this.count; var34++) {
            if (this.kind[var34] == 6 && this.visible(var34)) {
               Vfx.sprite(
                  Textures.BUBBLE,
                  this.x[var34] - offX,
                  this.y[var34] - offY,
                  this.z[var34] - offZ,
                  this.size(var34),
                  0.0F,
                  ColorUtil.mulAlpha(this.baseColor(var34), this.alphaOf(var34) * 0.8F)
               );
            }
         }

         for (int var27 = 0; var27 < this.textureCount; var27++) {
            for (int ix = 0; ix < this.count; ix++) {
               if (this.kind[ix] == 4 && this.texture[ix] == TEXTURES[var27] && this.visible(ix)) {
                  Vfx.sprite(
                     this.texture[ix],
                     this.x[ix] - offX,
                     this.y[ix] - offY,
                     this.z[ix] - offZ,
                     this.size(ix),
                     this.rot[ix],
                     ColorUtil.mulAlpha(this.baseColor(ix), this.alphaOf(ix))
                  );
               }
            }
         }

         RenderState.additive();

         for (int var35 = 0; var35 < this.count; var35++) {
            int k = this.kind[var35];
            if (k != 8 && k != 9 && k != 11 && k != 2 && k != 12 && this.visible(var35)) {
               float s = this.size(var35);
               float a = this.alphaOf(var35);
               int c = this.baseColor(var35);
               float halo;
               float haloA;
               switch (k) {
                  case 0:
                     halo = 1.0F;
                     haloA = 0.9F;
                     break;
                  case 1:
                     halo = 3.2F;
                     haloA = 0.35F;
                     break;
                  case 2:
                  case 4:
                  case 5:
                  case 8:
                  case 9:
                  default:
                     halo = 1.8F;
                     haloA = 0.25F;
                     break;
                  case 3:
                     halo = 2.6F;
                     haloA = 0.4F;
                     break;
                  case 6:
                     halo = 2.4F;
                     haloA = 0.25F;
                     break;
                  case 7:
                     halo = 3.4F;
                     haloA = 0.35F;
                     c = this.emberColor(c, this.lifeT(var35));
                     break;
                  case 10:
                     halo = 2.8F;
                     haloA = (this.flags[var35] & 8) != 0 ? 0.07F : 0.25F;
               }

               Vfx.sprite(Textures.GLOW, this.x[var35] - offX, this.y[var35] - offY, this.z[var35] - offZ, s * halo, 0.0F, ColorUtil.mulAlpha(c, a * haloA));
            }
         }

         for (int var36 = 0; var36 < this.count; var36++) {
            int k = this.kind[var36];
            if ((k == 1 || k == 3 || k == 7 || k == 6) && this.visible(var36)) {
               int c = k == 7 ? this.emberColor(this.baseColor(var36), this.lifeT(var36)) : this.baseColor(var36);
               int core = ColorUtil.lerp(Neon.tint(c), -1, this.hot[var36]);
               float s = this.size(var36) * (k == 3 ? 0.7F : (k == 6 ? 0.3F : 0.9F));
               double oy = k == 6 ? this.size(var36) * 0.18 : 0.0;
               Vfx.sprite(
                  Textures.SPARK, this.x[var36] - offX, this.y[var36] - offY + oy, this.z[var36] - offZ, s, 0.0F, ColorUtil.mulAlpha(core, this.alphaOf(var36))
               );
            }
         }

         for (int var37 = 0; var37 < this.count; var37++) {
            if (this.kind[var37] == 2 && this.visible(var37)) {
               this.streak(var37, offX, offY, offZ, Textures.LINE_GLOW, 3.4F, ColorUtil.mulAlpha(this.baseColor(var37), this.alphaOf(var37) * 0.3F));
            }
         }

         for (int var38 = 0; var38 < this.count; var38++) {
            if (this.kind[var38] == 2 && this.visible(var38)) {
               int core = ColorUtil.lerp(Neon.tint(this.baseColor(var38)), -1, this.hot[var38]);
               this.streak(var38, offX, offY, offZ, Textures.LINE_CORE, 0.55F, ColorUtil.mulAlpha(core, this.alphaOf(var38)));
            }
         }

         for (int var39 = 0; var39 < this.count; var39++) {
            int k = this.kind[var39];
            if ((k == 10 || k == 11) && this.visible(var39)) {
               double px = this.x[var39] - offX;
               double py = this.y[var39] - offY;
               double pz = this.z[var39] - offZ;
               int c = this.baseColor(var39);
               float s = this.size(var39);
               float a = this.alphaOf(var39);
               MiniMesh.rotation(this.ax[var39], this.ay[var39], this.az[var39], this.rot[var39]);
               if (k == 10) {
                  boolean solid = (this.flags[var39] & 8) != 0;
                  MiniMesh.cubeEdges(px, py, pz, s * 0.5, solid ? c : Neon.tint(c), a * (solid ? 0.35F : 0.9F), Math.max(0.006, s * 0.08));
               } else {
                  this.placeShard(var39, px, py, pz, s);
                  float glint = MiniMesh.shardGlint();
                  MiniMesh.shardEdges(ColorUtil.lerp(Neon.tint(c), -1, glint), a * (0.6F + 0.4F * glint), 0.012 + 0.01 * glint);
                  if (glint > 0.05F) {
                     Vfx.sprite(
                        Textures.FLARE,
                        MiniMesh.shardCenterX(),
                        MiniMesh.shardCenterY(),
                        MiniMesh.shardCenterZ(),
                        s * 0.9F * glint,
                        this.rot[var39],
                        ColorUtil.mulAlpha(-1, a * glint)
                     );
                  }
               }
            }
         }

         for (int var40 = 0; var40 < this.count; var40++) {
            if (this.kind[var40] == 5 && this.visible(var40)) {
               Vfx.sprite(
                  Textures.SPARKLE,
                  this.x[var40] - offX,
                  this.y[var40] - offY,
                  this.z[var40] - offZ,
                  this.size(var40) * this.twinkle(var40) * 0.55F,
                  this.rot[var40],
                  ColorUtil.mulAlpha(Neon.tint(this.baseColor(var40)), this.alphaOf(var40))
               );
            }
         }

         for (int var28 = 0; var28 < this.textureCount; var28++) {
            for (int ixx = 0; ixx < this.count; ixx++) {
               if (this.kind[ixx] == 4 && this.texture[ixx] == TEXTURES[var28] && this.visible(ixx)) {
                  Vfx.sprite(
                     this.texture[ixx],
                     this.x[ixx] - offX,
                     this.y[ixx] - offY,
                     this.z[ixx] - offZ,
                     this.size(ixx) * 0.55F,
                     this.rot[ixx],
                     ColorUtil.mulAlpha(Neon.tint(this.baseColor(ixx)), this.alphaOf(ixx) * 0.45F)
                  );
               }
            }
         }
      }
   }

   private boolean visible(int i) {
      return this.age[i] >= 0.0F && this.alphaOf(i) > 0.003F;
   }

   private float alphaOf(int i) {
      return this.fadeAlpha(i, this.lifeT(i));
   }

   private float size(int i) {
      return this.sizeAt(i, this.lifeT(i));
   }

   private float twinkle(int i) {
      return 0.75F + 0.25F * (float)Math.sin(this.age[i] * 23.0F + this.swayPhase[i] * 31.0F);
   }

   private void streak(int i, double offX, double offY, double offZ, int tex, float width, int c) {
      double px = this.x[i] - offX;
      double py = this.y[i] - offY;
      double pz = this.z[i] - offZ;
      float speed = (float)Math.sqrt(this.vx[i] * this.vx[i] + this.vy[i] * this.vy[i] + this.vz[i] * this.vz[i]);
      float s = this.size(i);
      float len = Math.min(0.7F, Math.max(s * 1.5F, speed * 0.045F));
      double k = speed > 1.0E-4F ? len / speed : 0.0;
      Vfx.line(tex, px, py, pz, px - this.vx[i] * k, py - this.vy[i] * k, pz - this.vz[i] * k, s * width, c, c & 16777215);
   }

   private void collectTextures() {
      this.textureCount = 0;

      for (int i = 0; i < this.count; i++) {
         if (this.kind[i] == 4 || this.kind[i] == 9) {
            int t = this.texture[i];
            boolean known = false;

            for (int j = 0; j < this.textureCount; j++) {
               if (TEXTURES[j] == t) {
                  known = true;
                  break;
               }
            }

            if (!known && this.textureCount < TEXTURES.length) {
               TEXTURES[this.textureCount++] = t;
            }
         }
      }
   }

   private void placeShard(int i, double px, double py, double pz, float s) {
      int o = i * 12;
      MiniMesh.placeShard(
         px, py, pz, this.shape, o, this.shape[o + 6], this.shape[o + 7], this.shape[o + 8], this.shape[o + 9], this.shape[o + 10], this.shape[o + 11], s
      );
   }

   private void drawFlutter(int i, double px, double py, double pz, float s, int c) {
      int tex = this.texture[i];
      double half = s * 0.5;
      if ((this.flags[i] & 65536) != 0) {
         Vfx.flatSprite(tex, px, py + 0.012, pz, half, this.rot[i], c);
      } else {
         double yawA = this.rot[i];
         double rock = 0.55 * Math.sin(this.age[i] * 5.3 + this.swayPhase[i] * 6.0);
         MiniMesh.rotation(this.ax[i], this.ay[i], this.az[i], rock + 0.6);
         double c1 = Math.cos(yawA);
         double s1 = Math.sin(yawA);
         double ux = c1 * half;
         double uz = s1 * half;
         double vxl = -s1 * half;
         double vzl = c1 * half;
         double[] corner = CORNER;
         corner(ux, 0.0, uz, vxl, 0.0, vzl, -1, -1, 0);
         corner(ux, 0.0, uz, vxl, 0.0, vzl, 1, -1, 3);
         corner(ux, 0.0, uz, vxl, 0.0, vzl, 1, 1, 6);
         corner(ux, 0.0, uz, vxl, 0.0, vzl, -1, 1, 9);
         Vfx.quadUV(
            tex,
            px + corner[0],
            py + corner[1],
            pz + corner[2],
            0.0,
            1.0,
            c,
            px + corner[3],
            py + corner[4],
            pz + corner[5],
            1.0,
            1.0,
            c,
            px + corner[6],
            py + corner[7],
            pz + corner[8],
            1.0,
            0.0,
            c,
            px + corner[9],
            py + corner[10],
            pz + corner[11],
            0.0,
            0.0,
            c
         );
      }
   }

   private static void corner(double ux, double uy, double uz, double vx, double vy, double vz, int a, int b, int o) {
      double lx = ux * a + vx * b;
      double ly = uy * a + vy * b;
      double lz = uz * a + vz * b;
      CORNER[o] = MiniMesh.rx(lx, ly, lz);
      CORNER[o + 1] = MiniMesh.ry(lx, ly, lz);
      CORNER[o + 2] = MiniMesh.rz(lx, ly, lz);
   }

   public void clear() {
      for (int i = 0; i < this.count; i++) {
         this.anchor[i] = null;
         this.source[i] = null;
      }

      this.count = 0;
      this.recycle = 0;
   }

   public boolean isEmpty() {
      return this.count == 0;
   }

   public int size() {
      return this.count;
   }
}
