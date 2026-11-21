package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.kit.Smoke;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ShadowVeilEffect extends AuraEffect {
   private static final int TENDRILS = 5;
   private static final int MAX_PUFFS = 80;
   private static final int MIST = 6;
   private static final int CRACKS = 3;
   private static final int CRACK_POINTS = 6;
   private static final double STACK = 5.0;
   public final ThemeTint emberColor;
   public final ThemeTint edgeColor;
   public final SliderSetting density;
   public final SliderSetting height;
   public final SliderSetting flowSpeed;
   public final SliderSetting embers;
   public final BooleanSetting groundMist;
   public final ModeSetting<Smoke.EdgeLight> edgeLight;
   private final Smoke.Puffs cloud = new Smoke.Puffs(80);
   private final double[] px = this.cloud.x;
   private final double[] py = this.cloud.y;
   private final double[] pz = this.cloud.z;
   private final float[] psize = this.cloud.size;
   private final float[] palpha = this.cloud.alpha;
   private final float[] pturn = this.cloud.turn;
   private final int[] pcell = this.cloud.cell;
   private int puffs;

   public ShadowVeilEffect() {
      super(
         EffectType.SHADOW_VEIL,
         "shadowveil",
         ThemeTint.detail(-15593964),
         true
      );
      this.emberColor = ThemeTint.detail(-45778);
      this.edgeColor = ThemeTint.detail(-4124129);
      this.density = this.look(this.setting(new SliderSetting("shadowveil.density", "Density", "How thick the smoke is.", 0.0, 100.0, 60.0, 5.0).unit("%")));
      this.height = this.setting(
         new SliderSetting("shadowveil.height", "Height", "How high the smoke rises, as a share of the target's height.", 50.0, 120.0, 90.0, 5.0).unit("%")
      );
      this.flowSpeed = this.look(
         this.setting(new SliderSetting("shadowveil.flow", "Flow Speed", "How fast the smoke flows up.", 50.0, 200.0, 100.0, 5.0).unit("%"))
      );
      this.embers = this.setting(new SliderSetting("shadowveil.emberAmount", "Embers", "How many embers glow in the smoke.", 0.0, 100.0, 40.0, 5.0).unit("%"));
      this.groundMist = this.setting(new BooleanSetting("shadowveil.mist", "Ground Mist", "Dark mist pooling on the ground.", true));
      this.edgeLight = this.setting(
         new ModeSetting<>(
            "shadowveil.edgeLight",
            "Edge Light",
            "A faint red rim round the smoke: on Auto when it is dark round the target.",
            Smoke.EdgeLight.class,
            Smoke.EdgeLight.AUTO
         )
      );
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      float alpha = this.intensity(c);
      if (!(alpha <= 0.003F)) {
         if (pass == FxPass.TRANSLUCENT) {
            this.layout(c);
            if (this.groundMist.get()) {
               this.mist(c, alpha);
            }

            this.smoke(c, alpha);
         } else if (pass == FxPass.GLOW) {
            this.layout(c);
            this.embers(c, alpha);
            this.edge(c, alpha);
         }
      }
   }

   private void layout(EffectContext c) {
      double s = this.size(c);
      double t = c.time;
      double flow = this.flowSpeed.getFloat() / 100.0 * this.speed();
      float dens = this.density.getFloat() / 100.0F;
      this.puffs = Math.min(80, 20 + Math.round(60.0F * dens));
      double top = c.height * this.height.getFloat() / 100.0;
      double ground = Math.max(c.groundY, c.y - 0.6);
      float in = Easing.outCubic(Easing.clamp01((float)(t / 0.3)));
      float out = c.outro;
      Camera cam = c.cam != null ? c.cam : Vfx.camera();

      for (int k = 0; k < this.puffs; k++) {
         int m = k % 5;
         double phase = t * flow * (0.32 + 0.08 * hash(k, 3)) + (double)k / this.puffs + hash(k, 5) * 0.3;
         phase -= Math.floor(phase);
         double dir = (m & 1) == 0 ? 1.0 : -1.0;
         double base = (Math.PI * 2) * m / 5.0 + hash(m, 7) * 0.6;
         double angle = base + dir * phase * 2.4 + 0.45 * Math.sin(t * 0.7 * flow + m * 1.9) + hash(k, 9) * 0.5;
         double radius = s * (0.52 + 0.18 * Math.sin(phase * Math.PI) - 0.12 * phase + 0.08 * hash(k, 11));
         double y = ground + 0.02 + (c.y + top - ground) * Math.pow(phase, 0.85);
         double swirl = 0.09 * s;
         double dx = swirl * Math.sin(1.7 * y + 1.3 * t * flow + m) + 0.04 * Math.sin(3.1 * y - 2.0 * t * flow + k);
         double dz = swirl * Math.cos(1.5 * y - 1.1 * t * flow + m * 2) + 0.04 * Math.cos(2.7 * y + 1.7 * t * flow + k);
         y = ground + (y - ground) * (0.2 + 0.8 * in) + 0.4 * out * phase;
         double var42;
         double var43;
         double x = c.x + Math.cos(var42 = angle + (1.0F - in) * 3.0 * dir) * (var43 = radius * (1.0 + 1.6 * (1.0F - in) + 1.8 * out)) + dx;
         double z = c.z + Math.sin(var42) * var43 + dz;
         this.px[k] = x;
         this.py[k] = y;
         this.pz[k] = z;
         this.psize[k] = (float)(s * (0.72 - 0.32 * phase) * (0.8 + 0.4 * hash(k, 13)) * (1.0 + 0.6 * out));
         float a = (float)(Math.pow(1.0 - phase, 1.1) * Easing.smoothstep(0.0F, 0.08F, (float)phase));
         this.palpha[k] = a * ((0.6F + 0.4F * dens) * (float)Math.pow(1.0F - out, 1.5));
         this.pturn[k] = (float)(hash(k, 17) * (Math.PI * 2) + t * (hash(k, 19) - 0.5) * 0.8);
         this.pcell[k] = (int)(hash(k, 23) * 4.0F);
      }

      this.cloud.count = this.puffs;
      this.cloud.sort(cam);
   }

   private static float readable(EffectContext c, double x, double y, double z, double half, float a) {
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      return Smoke.readable(c.keep, cam, c.x, c.z, x, y, z, half, a, 5.0);
   }

   public String stateForTest(EffectContext c) {
      this.layout(c);
      float alpha = this.intensity(c);
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      double cx = c.x - cam.posX;
      double cy = c.y + c.height * 0.5 - cam.posY;
      double cz = c.z - cam.posZ;
      double bodyDepth = cx * cam.forwardX + cy * cam.forwardY + cz * cam.forwardZ;
      double front = bodyDepth - c.width * 0.5;
      double darkest = 0.0;

      for (int gy = 0; gy <= 16; gy++) {
         for (int gx = 0; gx <= 8; gx++) {
            double u = (gx / 8.0 - 0.5) * 0.9;
            double v = (gy / 16.0 - 0.5) * c.height;
            double sx = (cx * cam.rightX + cy * cam.rightY + cz * cam.rightZ + u) / bodyDepth;
            double sy = (cx * cam.upX + cy * cam.upY + cz * cam.upZ + v) / bodyDepth;
            double clear = 1.0;

            for (int k = 0; k < this.puffs; k++) {
               double dx = this.px[k] - cam.posX;
               double dy = this.py[k] - cam.posY;
               double dz = this.pz[k] - cam.posZ;
               double d = dx * cam.forwardX + dy * cam.forwardY + dz * cam.forwardZ;
               double oy;
               double ox;
               double r;
               if (!(d <= 0.05)
                  && !(d >= front)
                  && !(
                     (
                           r = Math.sqrt(
                                 (ox = (dx * cam.rightX + dy * cam.rightY + dz * cam.rightZ) / d - sx) * ox
                                    + (oy = (dx * cam.upX + dy * cam.upY + dz * cam.upZ) / d - sy) * oy
                              )
                              / (this.psize[k] * 0.5 / d)
                        )
                        >= 1.0
                  )) {
                  double cover = (1.0 - r) * (1.0 - r) * 0.9;
                  clear *= 1.0 - readable(c, this.px[k], this.py[k], this.pz[k], this.psize[k] * 0.5, alpha * this.palpha[k]) * cover;
               }
            }

            darkest = Math.max(darkest, 1.0 - clear);
         }
      }

      return String.format(Locale.ROOT, "%d puffs; the body at most %.2f dark (cap %.2f)", this.puffs, darkest, c.keep.darkCap());
   }

   private void smoke(EffectContext c, float alpha) {
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      this.cloud.draw(this.color(c, 0.5F), alpha, c.keep, cam, c.x, c.z, 5.0);
   }

   private void mist(EffectContext c, float alpha) {
      double s = this.size(c);
      double t = c.time;
      float in = Easing.smoothstep(0.05F, 0.35F, (float)t);
      float out = 1.0F - Easing.smoothstep(0.0F, 0.7F, c.outro);
      float a = alpha * 0.42F * (0.5F + 0.5F * this.density.getFloat() / 100.0F) * in * out * c.groundAlpha;
      if (!(a <= 0.004F)) {
         int smoke = this.color(c, 0.2F);
         double y = c.groundY + 0.015;

         for (int i = 0; i < 6; i++) {
            double ang = (Math.PI * 2) * i / 6.0 + t * 0.08 * (i % 2 == 0 ? 1 : -1);
            double r = s * (0.25 + 0.45 * hash(i, 29)) * (1.0 + 0.8 * c.outro);
            double cx = c.x + Math.cos(ang) * r;
            double cz = c.z + Math.sin(ang) * r;
            double half = s * (0.6 + 0.35 * hash(i, 31)) * (1.0 + 0.5 * c.outro);
            Smoke.mist(cx, y, cz, half, t * 0.1 + i, i % 4, alphaOf(smoke, readable(c, cx, y, cz, half, a)));
         }
      }
   }

   private void embers(EffectContext c, float alpha) {
      float amount = this.embers.getFloat() / 100.0F;
      if (!(amount <= 0.0F)) {
         double t = c.time;
         double s = this.size(c);
         int col = this.emberColor.getColor(0.5F);
         float fade = (float)Math.pow(1.0F - c.outro, 1.5) * Easing.smoothstep(0.1F, 0.4F, (float)t);
         int n = Math.round(amount * 14.0F);

         for (int i = 0; i < n; i++) {
            int k = (i * 7 + 3) % Math.max(1, this.puffs);
            float flick = 0.45F + 0.55F * (float)(0.5 + 0.5 * Math.sin(t * 1.8 + i * 2.3));
            float a = alpha * fade * flick * Math.min(1.0F, this.palpha[k] * 2.2F);
            double ox = (hash(i, 37) - 0.5) * 0.12;
            double oz = (hash(i, 41) - 0.5) * 0.12;
            Smoke.ember(this.px[k] + ox, this.py[k], this.pz[k] + oz, 0.16F * (float)s, 0.045F * (float)s, col, a);
         }

         float ca;
         if (this.groundMist.get() && (ca = alpha * fade * amount * c.groundAlpha * (0.4F + 0.3F * (float)Math.sin(t * 1.1))) > 0.004F) {
            double y = c.groundY + 0.02;

            for (int k = 0; k < 3; k++) {
               double ang = (Math.PI * 2) * (k + 0.3 * hash(k, 43)) / 3.0;
               double r = s * 0.15;
               double x0 = c.x + Math.cos(ang) * r;
               double z0 = c.z + Math.sin(ang) * r;

               for (int p = 0; p < 5; p++) {
                  double a0 = ang + (hash(k * 11 + p, 47) - 0.5) * 0.8;
                  double len = s * (0.1 + 0.08 * hash(k * 11 + p, 53));
                  double x1 = x0 + Math.cos(a0) * len;
                  double z1 = z0 + Math.sin(a0) * len;
                  float fadeOut = 1.0F - p / 5.0F;
                  Smoke.crack(x0, y, z0, x1, z1, 0.035 * s, alpha(col, ca * fadeOut));
                  x0 = x1;
                  z0 = z1;
               }
            }
         }
      }
   }

   private void edge(EffectContext c, float alpha) {
      float amount;
      switch ((Smoke.EdgeLight)this.edgeLight.get()) {
         case ON:
            amount = 1.0F;
            break;
         case OFF:
            amount = 0.0F;
            break;
         default:
            amount = c.night;
      }

      if (!(amount <= 0.01F)) {
         int col = this.edgeColor.getColor(0.5F);

         for (int k = 0; k < this.puffs; k += 3) {
            float a = alpha * amount * 0.13F * this.palpha[k];
            if (!(a <= 0.003F)) {
               Vfx.sprite(Textures.GLOW, this.px[k], this.py[k], this.pz[k], this.psize[k] * 1.35F, 0.0F, alpha(col, a));
            }
         }
      }
   }

   private static int alphaOf(int color, float a) {
      return Math.round(255.0F * Easing.clamp01(a) * ((color >>> 24) / 255.0F)) << 24 | color & 16777215;
   }
}
