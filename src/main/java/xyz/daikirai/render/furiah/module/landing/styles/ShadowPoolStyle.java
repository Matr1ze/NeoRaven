package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Breeze;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Smoke;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ShadowPoolStyle extends LandingStyleBase {
   private static final int MAX_PUFFS = 44;
   private static final int MAX_EMBERS = 24;
   private static final int POOL_PATCHES = 5;
   private static final double REACH = 2.1;
   private static final double RIDE = 0.22;
   private static final double EDGE = 0.42;
   private static final float SMOKE_WIND = 0.25F;
   public final ThemeTint embers;
   public final ThemeTint edgeColor;
   public final SliderSetting ringSize;
   public final SliderSetting density;
   public final SliderSetting emberAmount;
   public final BooleanSetting pool;
   public final ModeSetting<Smoke.EdgeLight> edgeLight;
   private final double[] flow = new double[3];

   public ShadowPoolStyle() {
      super(
         LandingStyle.SHADOW_POOL,
         1.6F,
         3.4F,
         ThemeTint.detail(-15593964)
      );
      this.embers = ThemeTint.detail(-45778);
      this.edgeColor = ThemeTint.detail(-4124129);
      this.ringSize = this.look(
         this.setting(new SliderSetting("shadowpool.ringSize", "Ring Size", "How far the ring of smoke rolls out.", 50.0, 150.0, 100.0, 5.0).unit("%"))
      );
      this.density = this.look(this.setting(new SliderSetting("shadowpool.density", "Density", "How thick the smoke is.", 0.0, 100.0, 60.0, 5.0).unit("%")));
      this.emberAmount = this.setting(
         new SliderSetting("shadowpool.emberAmount", "Embers", "How many sparks ride in the smoke.", 0.0, 100.0, 40.0, 5.0).unit("%")
      );
      this.pool = this.setting(new BooleanSetting("shadowpool.pool", "Pool", "A pool of dark mist spreads under the feet.", true));
      this.edgeLight = this.setting(
         new ModeSetting<>(
               "shadowpool.edgeLight",
               "Edge Light",
               "A thin red rim round the smoke: where it is dark (Auto), always, or never.",
               Smoke.EdgeLight.class,
               Smoke.EdgeLight.AUTO
            )
            .style(ModeSetting.Style.CHIPS)
      );
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   private static double driftX(LandingFx fx, ShadowPoolStyle.State s) {
      return s.wind[0] * fx.styleTime();
   }

   private static double driftZ(LandingFx fx, ShadowPoolStyle.State s) {
      return s.wind[2] * fx.styleTime();
   }

   @Override
   public void start(LandingFx fx) {
      ShadowPoolStyle.State s = new ShadowPoolStyle.State();
      s.jump = fx.land.jump;
      float d = this.density.getFloat() / 100.0F;
      s.reach = 2.1 * this.ringSize.getFloat() / 100.0 * fx.size * (s.jump ? 0.3 : 1.0);
      int n = s.jump ? 6 : Math.round((18.0F + 22.0F * d) * (fx.lod ? 0.5F : 1.0F) * Math.min(1.5F, fx.density));
      s.n = Math.max(4, Math.min(44, n));

      for (int i = 0; i < s.n; i++) {
         s.angle[i] = (Math.PI * 2) * (i + 0.5 * fx.rand(10 + i)) / s.n;
         s.far[i] = s.reach * (0.82 + 0.3 * fx.rand(30 + i));
         s.size[i] = (0.62 + 0.4 * fx.rand(50 + i)) * Math.sqrt(fx.size) * (s.jump ? 0.7 : 1.0) * (0.8 + 0.4 * d);
         s.spin[i] = fx.rand(70 + i) * (Math.PI * 2);
      }

      int e = !s.jump && !fx.lod ? Math.round(24.0F * this.emberAmount.getFloat() / 100.0F * Math.min(1.5F, fx.density)) : 0;
      s.embers = Math.min(24, e);

      for (int i = 0; i < s.embers; i++) {
         s.eAngle[i] = fx.rand(100 + i) * (Math.PI * 2);
         s.eLag[i] = 0.2 + 0.5 * fx.rand(130 + i);
         s.eHeight[i] = 0.06 + 0.25 * fx.rand(160 + i);
      }

      Breeze.at(fx.x(), fx.y() + 0.3, fx.z(), fx.preview, s.wind);
      s.wind[0] = s.wind[0] * 0.25F;
      s.wind[1] = 0.0F;
      s.wind[2] = s.wind[2] * 0.25F;
      fx.data = s;
   }

   private static float out(LandingFx fx) {
      return Easing.outQuart(Easing.clamp01(fx.progress() / 0.7F));
   }

   private static float thin(LandingFx fx) {
      return 1.0F - Easing.smoothstep(0.35F, 0.95F, fx.progress());
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      ShadowPoolStyle.State s = (ShadowPoolStyle.State)fx.data;
      if (s != null) {
         Camera cam = Vfx.camera();
         if (pass == FxPass.GLOW) {
            this.light(fx, f, s, cam);
         } else {
            if (this.pool.get()) {
               this.poolMist(fx, f, s, cam);
            }

            this.ring(fx, f, s, cam);
         }
      }
   }

   private boolean place(LandingFx fx, FxFrame f, ShadowPoolStyle.State s, Camera cam, int i, int k) {
      float o = out(fx);
      float th = thin(fx);
      if (th <= 0.003F) {
         return false;
      } else {
         double r = 0.15 * s.reach + (s.far[i] - 0.15 * s.reach) * o;
         double a = s.angle[i];
         double x = fx.x() + Math.cos(a) * r + driftX(fx, s);
         double z = fx.z() + Math.sin(a) * r + driftZ(fx, s);
         double age = fx.styleTime();
         Smoke.curl(x, fx.y(), z, age, 1.3, this.flow);
         double size = s.size[i] * (0.6 + 0.7 * o);
         double var36;
         double var37;
         double ground = Ground.y(fx.ground, var36 = x + this.flow[0] * 0.18 * o, var37 = z + this.flow[2] * 0.18 * o);
         if (!Ground.lastValid()) {
            return false;
         } else {
            double y = ground + Math.max(0.08, size * 0.22 + this.flow[1] * 0.05);
            double rx = f.rx(var36);
            double ry = f.ry(y);
            double rz = f.rz(var37);
            float a8 = NearCamera.dark(cam, rx, ry, rz, 0.74F * th);
            double close = Math.sqrt(cam.distanceSq(rx, ry, rz));
            if (close < 2.0) {
               double cap = f.ry(fx.y() + 0.45);
               ry = Math.min(ry, cap);
               size *= 0.6 + 0.4 * Easing.smoothstep(0.8F, 2.0F, (float)close);
            }

            Smoke.Puffs p = s.puffs;
            p.x[k] = rx;
            p.y[k] = ry;
            p.z[k] = rz;
            p.size[k] = (float)size;
            p.alpha[k] = a8;
            p.turn[k] = (float)(s.spin[i] + o * 3.0);
            p.cell[k] = i % 4;
            return true;
         }
      }
   }

   private void ring(LandingFx fx, FxFrame f, ShadowPoolStyle.State s, Camera cam) {
      Smoke.Puffs p = s.puffs;
      p.count = 0;

      for (int i = 0; i < s.n; i++) {
         if (this.place(fx, f, s, cam, i, p.count)) {
            p.count++;
         }
      }

      if (p.count != 0) {
         p.sort(cam);
         int smoke = ColorUtil.shade(this.color.getColor(0.5F), Math.max(0.5F, fx.light)) | 0xFF000000;
         RenderState.translucent(false);
         p.draw(smoke, fx.alpha(), null, cam, 0.0, 0.0, 1.0);
      }
   }

   private void poolMist(LandingFx fx, FxFrame f, ShadowPoolStyle.State s, Camera cam) {
      float pr = fx.progress();
      float spread = Easing.outCubic(Easing.clamp01(pr / 0.45F));
      float thinning = 1.0F - Easing.smoothstep(0.55F, 1.0F, pr);
      float a = 0.8F * thinning * fx.alpha();
      if (!(a <= 0.003F)) {
         double half = s.reach * (s.jump ? 0.9 : 0.62) * (0.4 + 0.6 * spread);
         int smoke = ColorUtil.shade(this.color.getColor(0.5F), Math.max(0.5F, fx.light)) | 0xFF000000;
         RenderState.decal(true);
         RenderState.translucent(false);

         for (int k = 0; k < 5; k++) {
            double ang = (Math.PI * 2) * k / 5.0 + fx.rand(200 + k);
            double off = k == 0 ? 0.0 : half * 0.35;
            double x = fx.x() + Math.cos(ang) * off;
            double z = fx.z() + Math.sin(ang) * off;
            double y = Ground.y(fx.ground, x, z) + 0.004 * (k + 1);
            if (Ground.lastValid()) {
               double rx = f.rx(x);
               double ry = f.ry(y);
               double rz = f.rz(z);
               float ka = NearCamera.dark(cam, rx, ry, rz, a * (k == 0 ? 1.0F : 0.7F));
               Smoke.mist(rx, ry, rz, half * (k == 0 ? 1.0 : 0.75), fx.styleTime() * 0.15 * (k % 2 == 0 ? 1 : -1) + k, k % 4, Smoke.alphaOf(smoke, ka));
            }
         }

         RenderState.decal(false);
      }
   }

   private void light(LandingFx fx, FxFrame f, ShadowPoolStyle.State s, Camera cam) {
      float o = out(fx);
      float th = thin(fx);
      float alpha = fx.alpha();
      int ember = this.embers.getColor(0.5F);
      double time = fx.styleTime();

      for (int i = 0; i < s.embers; i++) {
         double r = Math.max(0.1, s.reach * o * (1.0 - 0.35 * s.eLag[i]));
         double x = fx.x() + Math.cos(s.eAngle[i]) * r + driftX(fx, s);
         double z = fx.z() + Math.sin(s.eAngle[i]) * r + driftZ(fx, s);
         double y = Ground.y(fx.ground, x, z) + s.eHeight[i] * (0.6 + 0.4 * Math.sin(time * 5.0 + i));
         float flick = 0.55F + 0.45F * (float)Math.sin(time * 17.0 + i * 2.3);
         float k = th * flick * alpha;
         double rx = f.rx(x);
         double ry = f.ry(y);
         double rz = f.rz(z);
         Smoke.ember(rx, ry, rz, (float)(0.22 * Math.sqrt(fx.size)), (float)(0.06 * Math.sqrt(fx.size)), ember, k * NearCamera.fade(cam, rx, ry, rz));
      }

      Smoke.EdgeLight mode = this.edgeLight.get();
      float edge = mode == Smoke.EdgeLight.ON ? 1.0F : (mode == Smoke.EdgeLight.AUTO ? fx.night : 0.0F);
      if (!(edge <= 0.01F)) {
         double r = 0.15 * s.reach + (s.reach * 0.95 - 0.15 * s.reach) * o + s.reach * 0.08;
         int col = alpha(this.edgeColor.getColor(0.5F), 0.55F * edge * th * alpha);
         RenderState.decal(true);
         Ground.ring(f, fx.ground, Textures.LINE_GLOW, fx.x() + driftX(fx, s), fx.z() + driftZ(fx, s), r, 0.12 * Math.sqrt(fx.size), 64, null, col, 1.0F, 0.0F);
         RenderState.decal(false);
      }
   }

   private static final class State {
      final Smoke.Puffs puffs = new Smoke.Puffs(49);
      int n;
      int embers;
      double reach;
      boolean jump;
      final double[] angle = new double[44];
      final double[] far = new double[44];
      final double[] size = new double[44];
      final double[] spin = new double[44];
      final double[] eAngle = new double[24];
      final double[] eLag = new double[24];
      final double[] eHeight = new double[24];
      final float[] wind = new float[3];

      private State() {
      }
   }
}
