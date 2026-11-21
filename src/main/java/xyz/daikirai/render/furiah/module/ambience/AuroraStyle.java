package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Procedural;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;

final class AuroraStyle extends AmbientStyleBase {
   private static final int COLUMNS = 56;
   final SliderSetting brightness;
   final SliderSetting altitude;
   final SliderSetting curtains;
   final BooleanSetting overworldOnly;
   private final Rng rng2 = new Rng();

   AuroraStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.AURORA, spec());
      this.brightness = this.setting(new SliderSetting("aurora.brightness", "Brightness", "How bright the curtains are.", 10.0, 200.0, 100.0, 5.0).unit("%"));
      this.altitude = this.setting(
         new SliderSetting("aurora.height", "Height", "How high in the sky, and how tall, the curtains are.", 0.5, 2.0, 1.0, 0.05).unit("×")
      );
      this.curtains = this.setting(new SliderSetting("aurora.curtains", "Curtains", "How many curtains there are.", 1.0, 6.0, 3.0, 1.0));
      this.overworldOnly = this.setting(new BooleanSetting("aurora.overworldOnly", "Overworld Only", "Only in the overworld's sky.", true));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.volume = false;
      s.opacity = 85.0F;
      s.time = AmbientStyleBase.TimeRule.NIGHT;
      s.defaultColor = -13176165;
      return s;
   }

   @Override
   int fixedCount() {
      return 0;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      return false;
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      return false;
   }

   @Override
   void draw(AmbientEnv env) {
   }

   @Override
   void drawSky(AmbientEnv env) {
      if (!(this.presence <= 0.0F) && (env.preview || env.world != null)) {
         if (env.preview || !this.overworldOnly.get() || env.world.provider.getDimensionId() == 0) {
            float night = this.time.get() == AmbientStyleBase.TimeRule.NIGHT
               ? 1.0F - Easing.smoothstep(0.1F, 0.6F, env.daylight)
               : (this.time.get() == AmbientStyleBase.TimeRule.DAY ? Easing.smoothstep(0.4F, 0.9F, env.daylight) : 1.0F);
            float rain = env.preview ? 1.0F : 1.0F - env.world.getRainStrength(env.partialTicks) * 0.85F;
            float a = this.presence * env.globalAlpha * this.opacity.getFloat() / 100.0F * this.brightness.getFloat() / 100.0F * night * rain;
            if (!(a <= 0.003F)) {
               float far = env.preview ? env.previewFar : Math.max(64.0F, Minecraft.getMinecraft().gameSettings.renderDistanceChunks * 16.0F);
               float clip = env.skyPass ? far * 2.0F : far * 1.41F;
               double topMax = 1.4 * this.altitude.getFloat();
               double dist = Math.min(far * 1.1, clip * 0.9 / Math.sqrt(1.0 + topMax * topMax));
               double t = env.time * this.speed.getFloat();
               float glowK = this.glowK();
               RenderState.additive();
               int n = this.curtains.getInt();

               for (int k = 0; k < n; k++) {
                  this.curtain(k, dist, t, a, glowK, false);
               }

               Vfx.flush();
               if (glowK > 0.0F) {
                  for (int var16 = 0; var16 < n; var16++) {
                     this.curtain(var16, dist, t, a, glowK, true);
                  }

                  Vfx.flush();
               }
            }
         }
      }
   }

   private void curtain(int k, double dist, double t, float a, float glowK, boolean edge) {
      this.rng2.seed(k * 7919 + 13, 5);
      int n = this.curtains.getInt();
      double center = (-Math.PI / 2) + (k - (n - 1) / 2.0) * 0.85 + (this.rng2.next() - 0.5) * 0.4 + t * 0.004 * (this.rng2.next() < 0.5 ? -1 : 1);
      double span = Math.toRadians(this.rng2.range(95.0F, 150.0F));
      float alt = this.altitude.getFloat();
      double bottom = dist * (0.36 + 0.06 * k + 0.06 * this.rng2.next()) * alt;
      double height = dist * (0.6 + 0.25 * this.rng2.next()) * alt;
      int seed = k * 131 + 7;
      float scroll = (float)(t * 0.012 + this.rng2.next());
      double prevX = 0.0;
      double prevZ = 0.0;
      double prevTop = 0.0;
      double prevBottom = 0.0;
      int prevC = 0;
      int prevCTop = 0;
      float prevU = 0.0F;

      for (int j = 0; j <= 56; j++) {
         float s = j / 56.0F;
         double phi = center - span / 2.0 + span * s;
         double wave = dist * (0.07 * Procedural.fbm((float)(phi * 1.7 + t * 0.03), seed) + 0.03 * Math.sin(phi * 5.0 + t * 0.2));
         double rad = dist + wave;
         double x = Math.cos(phi) * rad;
         double z = Math.sin(phi) * rad;
         float ripple = 0.55F + 0.45F * (float)Math.sin(phi * 9.0 - t * 0.6 + k);
         float ends = Easing.smoothstep(0.0F, 0.18F, s) * Easing.smoothstep(0.0F, 0.18F, 1.0F - s);
         float lift = (float)(Procedural.fbm((float)(phi * 2.3 - t * 0.02), seed + 5) * height * 0.18);
         double b = bottom + lift;
         double top = b + height * (0.8 + 0.2 * Procedural.noise((float)(phi * 3.0 + t * 0.05), seed + 9));
         int c = this.color.getColor(s);
         float edgeA = a * ends * ripple;
         int cBottom = ColorUtil.mulAlpha(c, Math.min(1.0F, edgeA * 0.9F));
         int cTop = ColorUtil.mulAlpha(ColorUtil.lerp(c, -1, 0.1F), 0.0F);
         float u = s * 7.0F + scroll;
         if (j > 0 && !edge) {
            Vfx.quadUV(
               Textures.STREAKS,
               prevX,
               prevBottom,
               prevZ,
               prevU,
               0.0,
               prevC,
               x,
               b,
               z,
               u,
               0.0,
               cBottom,
               x,
               top,
               z,
               u,
               1.0,
               cTop,
               prevX,
               prevTop,
               prevZ,
               prevU,
               1.0,
               prevCTop
            );
         } else if (j > 0) {
            double eh = height * 0.07;
            float pa = (prevC >>> 24) / 255.0F;
            int e0 = ColorUtil.mulAlpha(ColorUtil.lerp(prevC | 0xFF000000, -1, 0.25F), pa * 0.85F * glowK);
            int e1 = ColorUtil.mulAlpha(ColorUtil.lerp(c, -1, 0.25F), edgeA * 0.8F * glowK);
            Vfx.quadUV(
               Textures.WHITE,
               prevX,
               prevBottom - eh * 0.25,
               prevZ,
               0.5,
               0.5,
               e0 & 16777215,
               x,
               b - eh * 0.25,
               z,
               0.5,
               0.5,
               e1 & 16777215,
               x,
               b,
               z,
               0.5,
               0.5,
               e1,
               prevX,
               prevBottom,
               prevZ,
               0.5,
               0.5,
               e0
            );
            Vfx.quadUV(
               Textures.WHITE,
               prevX,
               prevBottom,
               prevZ,
               0.5,
               0.5,
               e0,
               x,
               b,
               z,
               0.5,
               0.5,
               e1,
               x,
               b + eh,
               z,
               0.5,
               0.5,
               e1 & 16777215,
               prevX,
               prevBottom + eh,
               prevZ,
               0.5,
               0.5,
               e0 & 16777215
            );
         }

         prevX = x;
         prevZ = z;
         prevBottom = b;
         prevTop = top;
         prevC = cBottom;
         prevCTop = cTop;
         prevU = u;
      }
   }
}
