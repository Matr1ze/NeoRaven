package xyz.daikirai.render.furiah.module.ambience;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SettingStyle;
import xyz.daikirai.render.furiah.setting.SliderSetting;

abstract class AmbientStyleBase implements SettingStyle {
   protected static final double TWO_PI = Math.PI * 2;
   static final float FADE_IN = 0.9F;
   static final float FADE_OUT = 0.7F;
   static final int DYING = 1;
   protected final FuriahAmbienceModule module;
   protected final AmbientStyle type;
   protected final AmbientStyleBase.Spec spec;
   final BooleanSetting on;
   final ThemeTint color = new ThemeTint();
   final ColorSetting palette;
   final SliderSetting density;
   final SliderSetting size;
   final SliderSetting speed;
   final SliderSetting opacity;
   final SliderSetting glowIntensity;
   final SliderSetting followWind;
   final SliderSetting customDistance;
   final SliderSetting heightMin;
   final SliderSetting heightMax;
   final SliderSetting cap;
   final BooleanSetting glow;
   final ModeSetting<AmbientStyleBase.DistanceMode> distanceMode;
   final ModeSetting<AmbientStyleBase.TimeRule> time;
   final BooleanSetting collides;
   private final List<Setting> own = new ArrayList<>();
   private final List<Setting> all = new ArrayList<>();
   AmbientPool pool;
   float presence;
   int target;
   protected final Rng rng = new Rng();
   private int seeds = 1;
   protected double wx;
   protected double wy;
   protected double wz;
   protected double rx;
   protected double ry;
   protected double rz;
   protected float dist;
   protected float fogK;
   private int[] order = new int[0];
   private float[] keys = new float[0];
   private int orderCount = -1;

   AmbientStyleBase(final FuriahAmbienceModule module, final AmbientStyle type, AmbientStyleBase.Spec spec) {
      this.module = module;
      this.type = type;
      this.spec = spec;
      this.rng.seed(type.ordinal() * 7919 + 17);
      String k = type.key;
      String name = type.displayName();
      this.on = new BooleanSetting(k + ".on", name, "Turns " + name + " on or off. Any number of styles can be on at once.", spec.onByDefault).nested();
      this.palette = new ColorSetting(name + " — Color", spec.defaultColor, module.palette);

      this.density = new SliderSetting(k + ".density", "Density", spec.densityHelp, 0.0, 200.0, spec.density, 5.0).unit("%");
      this.density
         .info(
            new Supplier<String>() {
               public String get() {
                  return "≈ " + module.formatCount(AmbientStyleBase.this.estimate(AmbientStyleBase.this.radiusSetting())) + " particles";
               }
            },
            new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return AmbientStyleBase.this.estimate(AmbientStyleBase.this.radiusSetting()) >= AmbientStyleBase.this.cap.getInt()
                     && AmbientStyleBase.this.density.get() > 0.0;
               }
            }
         );
      this.cap = new SliderSetting(
         k + ".cap", "Max Particles", "The most this style ever shows (the section also has a total cap).", 100.0, 4000.0, spec.cap, 50.0
      );
      this.size = new SliderSetting(k + ".size", "Size", "Scales every particle.", 0.25, 3.0, 1.0, 0.05).unit("×");
      this.speed = new SliderSetting(k + ".speed", "Speed", "How fast they move and animate.", 0.1, 3.0, 1.0, 0.05).unit("×");
      this.opacity = new SliderSetting(k + ".opacity", "Opacity", "How strong they are.", 0.0, 100.0, spec.opacity, 5.0).unit("%");
      this.glow = new BooleanSetting(k + ".glow", "Glow", "A soft glow around them.", spec.glow);
      this.glowIntensity = new SliderSetting(k + ".glowIntensity", "Glow Intensity", "How bright the glow is.", 0.0, 200.0, spec.glowIntensity, 5.0)
         .unit("%")
         .visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return AmbientStyleBase.this.glow.get();
            }
         });
      this.followWind = new SliderSetting(k + ".followWind", "Follow Wind", "How much the shared wind carries them.", 0.0, 100.0, spec.followWind, 5.0)
         .unit("%");
      this.distanceMode = new ModeSetting<>(
         k + ".distanceMode",
         "Render Distance",
         "The section's distance or one of its own.",
         AmbientStyleBase.DistanceMode.class,
         spec.customDistance ? AmbientStyleBase.DistanceMode.CUSTOM : AmbientStyleBase.DistanceMode.SECTION
      );
      this.customDistance = new SliderSetting(k + ".distance", "Distance", "How far around you this style reaches.", 8.0, 96.0, spec.distance, 1.0)
         .unit(" blocks")
         .visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return AmbientStyleBase.this.distanceMode.get() == AmbientStyleBase.DistanceMode.CUSTOM;
            }
         });
      this.heightMin = new SliderSetting(k + ".heightMin", "Lowest", "Lowest height above the ground.", 0.0, spec.heightLimit, spec.heightMin, 0.5)
         .unit(" blocks");
      this.heightMax = new SliderSetting(k + ".heightMax", "Highest", "Highest height above the ground.", 0.0, spec.heightLimit, spec.heightMax, 0.5)
         .unit(" blocks");
      this.time = new ModeSetting<>(k + ".time", "Time", "When it shows (where there are day and night).", AmbientStyleBase.TimeRule.class, spec.time);
      this.collides = spec.collides == null
         ? null
         : new BooleanSetting(k + ".collides", "Collides with Blocks", "Never passes into solid blocks.", spec.collides);

   }

   protected final <S extends Setting> S setting(S setting) {
      this.own.add(setting);
      return setting;
   }

   final List<Setting> getSettings() {
      if (this.all.isEmpty()) {
         this.all.addAll(this.own);
         if (this.spec.volume) {
            this.all.add(this.density);
         }

         this.all.add(this.size);
         this.all.add(this.speed);
         this.all.add(this.opacity);
         this.all.add(this.glow);
         this.all.add(this.glowIntensity);
         if (this.spec.volume) {
            this.all.add(this.followWind);
            this.all.add(this.heightMin);
            this.all.add(this.heightMax);
            this.all.add(this.distanceMode);
            this.all.add(this.customDistance);
         }

         this.all.add(this.time);
         if (this.collides != null) {
            this.all.add(this.collides);
         }

         if (this.spec.volume) {
            this.all.add(this.cap);
         }

         for (Setting s : this.all) {
            s.group(this.type.displayName());
            s.ownedBy(this);
         }
      }

      return Collections.unmodifiableList(this.all);
   }

   @Override
   public final String styleName() {
      return this.type.displayName();
   }

   @Override
   public final boolean isShown() {
      return this.module.editing.get() == this.type;
   }

   @Override
   public final boolean isActive() {
      return this.on.get();
   }

   @Override
   public final boolean isMultiple() {
      return true;
   }

   @Override
   public final void show() {
      this.module.editing.set(this.type);
   }

   public final AmbientStyle getType() {
      return this.type;
   }

   public final boolean isOn() {
      return this.on.get();
   }

   private float radiusSetting() {
      return this.distanceMode.get() == AmbientStyleBase.DistanceMode.CUSTOM ? this.customDistance.getFloat() : this.module.renderDistance.getFloat();
   }

   final float radius(AmbientEnv env) {
      if (env.preview) {
         return env.radius;
      } else {
         float r = this.distanceMode.get() == AmbientStyleBase.DistanceMode.CUSTOM ? this.customDistance.getFloat() : this.module.renderDistance.getFloat();
         return Math.max(4.0F, Math.min(r, this.module.minecraftDistance()));
      }
   }

   final float lowest() {
      return Math.min(this.heightMin.getFloat(), this.heightMax.getFloat());
   }

   final float highest() {
      return Math.max(this.heightMin.getFloat(), this.heightMax.getFloat());
   }

   double volume(float r) {
      float band = Math.max(0.5F, this.highest() - this.lowest());
      return Math.PI * r * r * Math.min(band, 2.0F * r);
   }

   int estimate(float r) {
      if (!this.spec.volume) {
         return this.fixedCount();
      } else {
         double n = this.spec.baseDensity * this.density.get() / 100.0 * this.volume(Math.min(r, this.module.minecraftDistance()));
         return (int)Math.min((long)this.cap.getInt(), Math.round(n));
      }
   }

   int fixedCount() {
      return 0;
   }

   final boolean timeAllows(AmbientEnv env) {
      if (!env.dayCycle) {
         return true;
      } else {
         switch ((AmbientStyleBase.TimeRule)this.time.get()) {
            case DAY:
               return env.daylight > 0.5F;
            case NIGHT:
               return env.daylight < 0.5F;
            default:
               return true;
         }
      }
   }

   final void ensurePool(AmbientEnv env) {
      int want = env.preview ? Math.max(16, env.previewCap) : (this.spec.volume ? Math.max(this.cap.getInt(), 16) : Math.max(this.fixedCount(), 16));
      if (this.pool == null) {
         this.pool = new AmbientPool(want, this.spec.tail);
      } else {
         this.pool.ensureCapacity(want);
      }
   }

   protected final int nextSeed() {
      return Rng.mix(this.seeds++ * -1640531535 + this.type.ordinal());
   }

   final void tick(AmbientEnv env, float scale, boolean reseed, boolean refill) {
      float dt = 0.05F;
      boolean want = env.preview ? this.timeAllows(env) : this.module.isEnabled() && this.isOn() && this.timeAllows(env) && env.globalAlpha > 0.002F;
      this.presence = Easing.approach(this.presence, want ? 1.0F : 0.0F, 1.25F, dt);
      if (!want && this.presence <= 0.001F) {
         this.presence = 0.0F;
         if (this.pool != null) {
            this.pool.clear();
         }
      } else {
         this.ensurePool(env);
         if (reseed) {
            this.pool.clear();
         }

         float r = this.radius(env);
         this.target = want ? Math.min(this.pool.capacity, Math.round(this.estimate(r) * scale)) : this.pool.count;
         int budget = Math.max(4, this.target / (!reseed && !refill ? 20 : 10));

         int i;
         while (this.pool.count < this.target && budget-- > 0 && (i = this.pool.add(env.camX, env.camY, env.camZ, this.nextSeed())) >= 0) {
            if (!this.spawn(env, i, r, true)) {
               this.pool.remove(i);
               break;
            }

            this.pool.px[i] = this.pool.x[i];
            this.pool.py[i] = this.pool.y[i];
            this.pool.pz[i] = this.pool.z[i];
         }

         int surplus = this.pool.count - this.target;

         for (int i2 = this.pool.count - 1; i2 >= 0 && surplus > 0; i2--) {
            if ((this.pool.flags[i2] & 1) == 0) {
               this.pool.flags[i2] = this.pool.flags[i2] | 1;
            }

            surplus--;
         }

         this.beforeUpdate(env, r);
         float fadeInStep = dt / 0.9F;
         float fadeOutStep = dt / 0.7F;
         boolean wake = this.spec.light && !env.preview && this.module.wakeActive();
         int i3 = 0;

         while (i3 < this.pool.count) {
            this.pool.px[i3] = this.pool.x[i3];
            this.pool.py[i3] = this.pool.y[i3];
            this.pool.pz[i3] = this.pool.z[i3];
            this.pool.age[i3] = this.pool.age[i3] + dt;
            if (this.pool.fadeIn[i3] < 1.0F) {
               this.pool.fadeIn[i3] = Math.min(1.0F, this.pool.fadeIn[i3] + fadeInStep);
            }

            if ((this.pool.flags[i3] & 1) != 0) {
               this.pool.fadeOut[i3] = this.pool.fadeOut[i3] - fadeOutStep;
               if (this.pool.fadeOut[i3] <= 0.0F) {
                  this.pool.remove(i3);
                  continue;
               }
            }

            if (wake) {
               this.module.wake(this.pool, i3, dt);
            }

            boolean alive;
            if (alive = this.step(env, i3, r, dt)) {
               if (this.pool.kx[i3] != 0.0F || this.pool.ky[i3] != 0.0F || this.pool.kz[i3] != 0.0F) {
                  this.pool.x[i3] = this.pool.x[i3] + this.pool.kx[i3] * dt;
                  this.pool.y[i3] = this.pool.y[i3] + this.pool.ky[i3] * dt;
                  this.pool.z[i3] = this.pool.z[i3] + this.pool.kz[i3] * dt;
                  this.pool.kx[i3] = this.pool.kx[i3] * 0.9F;
                  this.pool.ky[i3] = this.pool.ky[i3] * 0.9F;
                  this.pool.kz[i3] = this.pool.kz[i3] * 0.9F;
                  if (Math.abs(this.pool.kx[i3]) + Math.abs(this.pool.ky[i3]) + Math.abs(this.pool.kz[i3]) < 0.01F) {
                     this.pool.kz[i3] = 0.0F;
                     this.pool.ky[i3] = 0.0F;
                     this.pool.kx[i3] = 0.0F;
                  }
               }

               alive = this.wrap(env, i3, r);
            }

            if (!alive) {
               if (!this.spawn(env, i3, r, false)) {
                  this.pool.remove(i3);
                  continue;
               }

               this.pool.fadeIn[i3] = 0.0F;
               this.pool.px[i3] = this.pool.x[i3];
               this.pool.py[i3] = this.pool.y[i3];
               this.pool.pz[i3] = this.pool.z[i3];
               this.pool.filled[i3] = 0;
            }

            this.pool.record(i3);
            i3++;
         }
      }
   }

   protected final void windAt(AmbientEnv env, int i, float follow, float[] out) {
      AmbientPool p = this.pool;
      if (Float.isNaN(p.tx[i]) || (env.tickCount + i) % 3L == 0L) {
         env.wind.swirl(p.x[i], p.y[i], p.z[i], out);
         p.tx[i] = out[0];
         p.ty[i] = out[1];
         p.tz[i] = out[2];
      }

      Wind w = env.wind;
      out[0] = (w.dirX * w.speed + p.tx[i]) * follow;
      out[1] = p.ty[i] * follow;
      out[2] = (w.dirZ * w.speed + p.tz[i]) * follow;
   }

   protected void beforeUpdate(AmbientEnv env, float r) {
   }

   protected abstract boolean spawn(AmbientEnv var1, int var2, float var3, boolean var4);

   protected abstract boolean step(AmbientEnv var1, int var2, float var3, float var4);

   protected boolean wrap(AmbientEnv env, int i, float r) {
      double dx = this.pool.x[i] - env.camX;
      double dz = this.pool.z[i] - env.camZ;
      double d2 = dx * dx + dz * dz;
      if (d2 <= (double)r * r) {
         return true;
      } else {
         double d = Math.sqrt(d2);
         double back = r * 0.96;
         if (this.spec.nearBias > 1.0F) {
            back = Math.max(r * 0.12, Math.sqrt(Math.pow(this.rng.next(), this.spec.nearBias)) * back);
         }

         double k = -back / d;
         double turn = this.rng.signed() * 0.35;
         double cos = Math.cos(turn);
         double sin = Math.sin(turn);
         double nx = (dx * cos - dz * sin) * k;
         double nz = (dx * sin + dz * cos) * k;
         double oldGround = this.pool.ground[i];
         double x = env.camX + nx;
         double z = env.camZ + nz;
         double y = this.pool.y[i];
         if (!Double.isNaN(oldGround)) {
            double g = env.outdoorsOnly ? env.groundAt(x, z) : env.floorBelow(x, y + 2.0, z, 24);
            if (!Double.isNaN(g)) {
               y = g + (this.pool.y[i] - oldGround);
               this.pool.ground[i] = g;
            }
         }

         if (env.solidAt(x, y, z)) {
            return false;
         } else {
            this.pool.teleport(i, x, y, z);
            this.pool.fadeIn[i] = 0.0F;
            return true;
         }
      }
   }

   protected final boolean randomPlace(AmbientEnv env, int i, float r, float minR) {
      for (int attempt = 0; attempt < 4; attempt++) {
         double ang = this.rng.next() * (Math.PI * 2);
         double u = this.spec.nearBias == 1.0F ? this.rng.next() : Math.pow(this.rng.next(), this.spec.nearBias);
         double dist = Math.sqrt(minR * minR + (r * r - minR * minR) * u);
         double x = env.camX + Math.cos(ang) * dist;
         double z = env.camZ + Math.sin(ang) * dist;
         float top = this.highest();
         double g;
         if (env.outdoorsOnly) {
            g = env.groundAt(x, z);
         } else {
            double ref = env.camY + this.rng.signed() * Math.min(r * 0.5F, 12.0F);
            g = env.floorBelow(x, ref, z, 24);
            if (!Double.isNaN(g) && (top = Math.min(top, (float)env.headroom(x, g, z, (int)Math.ceil(top) + 1) - 0.3F)) < this.lowest()) {
               continue;
            }
         }

         double y;
         if (!Double.isNaN(g) && !(Math.abs((y = g + this.lowest() + (top - this.lowest()) * this.rng.next()) - env.camY) > r) && !env.solidAt(x, y, z)) {
            this.pool.teleport(i, x, y, z);
            this.pool.ground[i] = g;
            return true;
         }
      }

      return false;
   }

   protected final double bandTop(AmbientEnv env, int i) {
      double g = this.pool.ground[i];
      float top = this.highest();
      if (!env.outdoorsOnly) {
         top = Math.min(top, (float)env.headroom(this.pool.x[i], g, this.pool.z[i], (int)Math.ceil(top) + 1) - 0.3F);
      }

      return g + Math.max(this.lowest(), top);
   }

   protected final int lit(AmbientEnv env, int color) {
      return env.light >= 0.999F ? color : ColorUtil.shade(color, env.light);
   }

   protected final boolean place(AmbientEnv env, int i, float cullRadius) {
      float pt = env.partialTicks;
      this.wx = this.pool.px[i] + (this.pool.x[i] - this.pool.px[i]) * pt;
      this.wy = this.pool.py[i] + (this.pool.y[i] - this.pool.py[i]) * pt;
      this.wz = this.pool.pz[i] + (this.pool.z[i] - this.pool.pz[i]) * pt;
      this.rx = this.wx - env.viewX;
      this.ry = this.wy - env.viewY;
      this.rz = this.wz - env.viewZ;
      if (!env.frustum.sphere(this.rx, this.ry, this.rz, cullRadius)) {
         return false;
      } else {
         double dx = this.wx - env.eyeX;
         double dy = this.wy - env.eyeY;
         double dz = this.wz - env.eyeZ;
         this.dist = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         this.fogK = env.fog(this.dist);
         return true;
      }
   }

   protected final float fade(AmbientEnv env, int i, float r) {
      float a = this.pool.fadeIn[i] * this.pool.fadeOut[i];
      a *= env.edgeFade(this.wx - env.eyeX, this.wz - env.eyeZ, r);
      return a * AmbientEnv.nearFade(this.dist) * this.presence * env.globalAlpha * this.opacity.getFloat() / 100.0F;
   }

   protected final float fogged(float alpha) {
      return alpha * (1.0F - this.fogK);
   }

   protected final int fogColor(AmbientEnv env, int color) {
      return this.fogK <= 0.0F ? color : ColorUtil.lerp(color, env.fogColor | 0xFF000000, this.fogK) & 16777215 | color & 0xFF000000;
   }

   protected final float glowK() {
      return this.glow.get() ? this.glowIntensity.getFloat() / 100.0F : 0.0F;
   }

   abstract void draw(AmbientEnv var1);

   void drawSky(AmbientEnv env) {
   }

   protected final int[] backToFront(AmbientEnv env) {
      int n = this.pool.count;
      if (this.order.length < n) {
         this.order = new int[this.pool.capacity];
         this.keys = new float[this.pool.capacity];
         this.orderCount = -1;
      }

      if (this.orderCount != n) {
         int i = 0;

         while (i < n) {
            this.order[i] = i++;
         }
      }

      float pt = env.partialTicks;

      for (int k = 0; k < n; k++) {
         int i = this.order[k];
         double x = this.pool.px[i] + (this.pool.x[i] - this.pool.px[i]) * pt - env.eyeX;
         double y = this.pool.py[i] + (this.pool.y[i] - this.pool.py[i]) * pt - env.eyeY;
         double z = this.pool.pz[i] + (this.pool.z[i] - this.pool.pz[i]) * pt - env.eyeZ;
         this.keys[k] = (float)(-(x * x + y * y + z * z));
      }

      boolean sorted = this.orderCount == n && this.insertionSort(n, 6 * n);
      if (!sorted) {
         this.shellSort(n);
      }

      this.orderCount = n;
      return this.order;
   }

   private boolean insertionSort(int n, int budget) {
      for (int k = 1; k < n; k++) {
         float key = this.keys[k];
         int idx = this.order[k];

         int j;
         for (j = k - 1; j >= 0 && this.keys[j] > key; j--) {
            this.keys[j + 1] = this.keys[j];
            this.order[j + 1] = this.order[j];
            if (--budget < 0) {
               this.keys[j + 1] = key;
               this.order[j + 1] = idx;
               return false;
            }
         }

         this.keys[j + 1] = key;
         this.order[j + 1] = idx;
      }

      return true;
   }

   private void shellSort(int n) {
      int gap = 1;

      while (gap < n / 3) {
         gap = gap * 3 + 1;
      }

      while (gap > 0) {
         for (int k = gap; k < n; k++) {
            float key = this.keys[k];
            int idx = this.order[k];

            int j;
            for (j = k; j >= gap && this.keys[j - gap] > key; j -= gap) {
               this.keys[j] = this.keys[j - gap];
               this.order[j] = this.order[j - gap];
            }

            this.keys[j] = key;
            this.order[j] = idx;
         }

         gap /= 3;
      }
   }

   protected final float hash(int i, int k) {
      return Rng.hash(this.pool.seed[i], k);
   }

   final int count() {
      return this.pool == null ? 0 : this.pool.count;
   }

   final void clear() {
      if (this.pool != null) {
         this.pool.clear();
      }

      this.presence = 0.0F;
   }

   String describe() {
      return String.format(Locale.ROOT, "%s %d", this.type.displayName(), this.count());
   }

   public static enum DistanceMode implements Displayable {
      SECTION("Section Default", "The section's Render Distance"),
      CUSTOM("Custom", "Its own distance, below");

      private final String displayName;
      private final String description;

      private DistanceMode(String displayName, String description) {
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

   static final class Spec {
      float baseDensity = 0.002F;
      float density = 100.0F;
      float opacity = 80.0F;
      boolean glow = true;
      float glowIntensity = 100.0F;
      float followWind = 70.0F;
      float heightMin = 0.5F;
      float heightMax = 6.0F;
      float heightLimit = 40.0F;
      boolean customDistance;
      int distance = 32;
      AmbientStyleBase.TimeRule time = AmbientStyleBase.TimeRule.ALWAYS;
      int cap = 1500;
      Boolean collides;
      int tail;
      boolean volume = true;
      boolean light;
      int defaultColor = -4204033;
      boolean onByDefault;
      String densityHelp = "How many there are.";
      float nearBias = 1.0F;
   }

   public static enum TimeRule implements Displayable {
      ALWAYS("Always", "Day and night"),
      DAY("Day Only", "Only while the sun is up"),
      NIGHT("Night Only", "Only at night");

      private final String displayName;
      private final String description;

      private TimeRule(String displayName, String description) {
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
