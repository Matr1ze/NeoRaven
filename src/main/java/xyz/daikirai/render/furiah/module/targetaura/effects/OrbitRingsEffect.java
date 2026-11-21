package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class OrbitRingsEffect extends AuraEffect {
   private static final int SEGMENTS = 96;
   private static final int MAX_RINGS = 4;
   private static final double[] TILT = new double[]{0.5, 0.5, 1.05, 0.82};
   private static final double[] AZIMUTH = new double[]{0.0, Math.PI, 1.9, 4.2};
   public final SliderSetting rings;
   public final SliderSetting thickness;
   public final BooleanSetting comet;
   private final double[] headX = new double[4];
   private final double[] headY = new double[4];
   private final double[] headZ = new double[4];
   private final float[] ringGrow = new float[4];

   public OrbitRingsEffect() {
      super(EffectType.ORBIT_RINGS, "rings", new ThemeTint());
      this.rings = this.setting(new SliderSetting("rings.count", "Rings", "Number of rings.", 1.0, 4.0, 3.0, 1.0));
      this.thickness = this.setting(new SliderSetting("rings.thickness", "Thickness", "Width of the rings and their glow.", 0.5, 2.0, 1.0, 0.05).unit("x"));
      this.comet = this.setting(new BooleanSetting("rings.comet", "Comet Heads", "A bright spot travels along each ring.", true));
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float thick = this.thickness.getFloat();
      float spd = this.speed();
      int n = this.rings.getInt();
      boolean heads = this.comet.get();
      double cx = c.x;
      double cy = c.y + c.height * 0.55;
      double cz = c.z;

      for (int i = 0; i < n; i++) {
         float in = Easing.clamp01((c.intro - i * 0.1F) / 0.75F);
         float grow;
         this.ringGrow[i] = grow = Easing.outBack(in, 1.3F);
         double radius = (0.6 + 0.075 * i) * sz * Math.max(0.0F, grow);
         double dir = i % 2 == 0 ? 1.0 : -1.0;
         double bob = 0.035 * Math.sin(c.time * 1.3 + i * 2.0);
         double az = AZIMUTH[i] + c.time * 0.3 * spd * dir;
         double tilt = TILT[i];
         double hx = -Math.sin(az);
         double hz = Math.cos(az);
         double dx = Math.cos(az) * Math.cos(tilt);
         double dy = Math.sin(tilt);
         double dz = Math.sin(az) * Math.cos(tilt);
         double head = dir * c.time * (Math.PI * 2) * 0.55 * spd + i * 2.1;
         this.headX[i] = cx + radius * (hx * Math.cos(head) + dx * Math.sin(head));
         this.headY[i] = cy + bob + radius * (dy * Math.sin(head));
         this.headZ[i] = cz + radius * (hz * Math.cos(head) + dz * Math.sin(head));
         int base = i * 96;

         for (int k = 0; k < 96; k++) {
            double a = (Math.PI * 2) * k / 96.0;
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            Vfx.X[base + k] = cx + radius * (hx * cos + dx * sin);
            Vfx.Y[base + k] = cy + bob + radius * (dy * sin);
            Vfx.Z[base + k] = cz + radius * (hz * cos + dz * sin);
         }
      }

      for (int pass = 0; pass < 2; pass++) {
         boolean bloom = pass == 0;

         for (int i2 = 0; i2 < n; i2++) {
            if (!(this.ringGrow[i2] <= 0.001F)) {
               double dir = i2 % 2 == 0 ? 1.0 : -1.0;
               double head = dir * c.time * (Math.PI * 2) * 0.55 * spd + i2 * 2.1;
               float widthGrow = Math.min(1.0F, this.ringGrow[i2] * 1.5F);
               int base = i2 * 96;

               for (int k = 0; k < 96; k++) {
                  double a = (Math.PI * 2) * k / 96.0;
                  float glow = 1.0F;
                  if (heads) {
                     double behind = dir * (head - a);
                     behind -= Math.floor(behind / (Math.PI * 2)) * (Math.PI * 2);
                     glow = 0.3F + 0.7F * (float)Math.exp(-behind * 1.25);
                  }

                  int col = this.color(c, loop(k / 96.0F + i2 * 0.33F));
                  if (bloom) {
                     Vfx.W[base + k] = 0.2F * sz * thick * widthGrow;
                     Vfx.C[base + k] = alpha(col, inten * glow * 0.5F);
                  } else {
                     Vfx.W[base + k] = 0.042F * sz * thick * widthGrow;
                     Vfx.C[base + k] = core(col, inten * glow);
                  }
               }

               Vfx.ribbon(bloom ? Textures.LINE_GLOW : Textures.LINE_CORE, base, 96, true);
            }
         }
      }

      if (heads) {
         for (int var47 = 0; var47 < n; var47++) {
            if (!(this.ringGrow[var47] <= 0.001F)) {
               int col = this.color(c, var47 * 0.33F);
               Vfx.sprite(Textures.GLOW, this.headX[var47], this.headY[var47], this.headZ[var47], 0.6F * sz, 0.0F, alpha(col, 0.6F * inten));
            }
         }

         for (int var48 = 0; var48 < n; var48++) {
            if (this.ringGrow[var48] > 0.001F) {
               Vfx.sprite(
                  Textures.FLARE,
                  this.headX[var48],
                  this.headY[var48],
                  this.headZ[var48],
                  0.42F * sz,
                  (float)(c.time * 1.4 + var48),
                  core(this.color(c, var48 * 0.33F), 0.5F * inten)
               );
            }
         }

         for (int var49 = 0; var49 < n; var49++) {
            if (this.ringGrow[var49] > 0.001F) {
               Vfx.sprite(Textures.SPARK, this.headX[var49], this.headY[var49], this.headZ[var49], 0.2F * sz, 0.0F, core(this.color(c, var49 * 0.33F), inten));
            }
         }
      }
   }
}
