package xyz.daikirai.render.furiah.module.landing.styles;

import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Procedural;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class NeuralNetStyle extends LandingStyleBase {
   private static final int POINTS = 26;
   private static final int FORK_POINTS = 12;
   private static final float GROW_END = 0.55F;
   private static final float RETRACT_END = 0.86F;
   public final SliderSetting tendrils;
   public final SliderSetting length;
   public final SliderSetting wiggle;
   private final Rng rng = new Rng();

   public NeuralNetStyle() {
      super(
         LandingStyle.NEURAL_NET,
         1.3F,
         2.4F,
         new ThemeTint()
      );
      this.tendrils = this.setting(new SliderSetting("neural.tendrils", "Tendrils", "How many tendrils crawl out.", 8.0, 14.0, 11.0, 1.0));
      this.length = this.setting(new SliderSetting("neural.length", "Length", "How far they reach.", 1.0, 3.0, 2.0, 0.05).unit(" blocks"));
      this.wiggle = this.setting(new SliderSetting("neural.wiggle", "Wiggle", "How much they wriggle.", 0.0, 2.0, 1.0, 0.05).unit("x"));
   }

   private int count(LandingFx fx) {
      return this.tendrils.getInt();
   }

   private double angle(LandingFx fx, int i, int n) {
      return (Math.PI * 2) * (i + 0.35 * fx.srand(i * 3 + 1)) / n;
   }

   private void path(LandingFx fx, int i, int n, int offset) {
      double step = this.length.get() * fx.size / 25.0 * (0.8 + 0.4 * fx.rand(i * 3 + 2));
      Procedural.tendril(offset, 26, fx.x(), fx.y(), fx.z(), this.angle(fx, i, n), step, 0.95F * this.wiggle.getFloat(), 1.3F, fx.seed + i * 101);
   }

   @Override
   public void start(LandingFx fx) {
      if (!fx.lod) {
         this.rng.seed(fx.seed);
         int n = this.count(fx);

         for (int i = 0; i < n; i++) {
            this.path(fx, i, n, 0);
            int sparks = fx.count(4.0F);

            for (int s = 0; s < sparks; s++) {
               float along = 0.3F + 0.7F * this.rng.next();
               int idx = Math.min(25, (int)(along * 25.0F));
               double x = Vfx.X[idx];
               double z = Vfx.Z[idx];
               double dx = x - fx.x();
               double dz = z - fx.z();
               double dl = Math.max(1.0E-4, Math.sqrt(dx * dx + dz * dz));
               float out = this.rng.range(0.8F, 1.8F) * (float)Math.sqrt(fx.size);
               float when = 0.55F + 0.31F * (float)Math.pow(along, 1.4);
               fx.particles
                  .spawn(
                     7,
                     x,
                     fx.ground.at(x, z) + 0.06,
                     z,
                     (float)(dx / dl * out),
                     this.rng.range(0.3F, 0.9F),
                     (float)(dz / dl * out),
                     fx.seconds(this.rng.range(0.45F, 0.65F)),
                     this.rng.range(0.045F, 0.07F) * fx.size
                  )
                  .gravity(3.0F)
                  .drag(2.6F)
                  .color(this.color, (float)i / Math.max(1, n - 1))
                  .alpha(fx.opacity)
                  .flags(2)
                  .sway(0.0F, this.rng.next())
                  .delay(fx.life * when)
                  .ground(fx.y() + 0.04, 0, 0.3F);
            }
         }
      }
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      int n = this.count(fx);
      float flash = 1.0F - Easing.smoothstep(0.0F, 0.14F, p);
      if (flash > 0.003F) {
         double x = f.rx(fx.x());
         double y = f.ry(fx.y() + 0.15);
         double z = f.rz(fx.z());
         int c = this.color.getColor(0.5F);
         RenderState.translucent(false);
         Vfx.sprite(Textures.FLARE, x, y, z, 1.2 * fx.size * (1.2F - 0.2F * flash), 0.3F, alpha(c, 0.7F * flash * a));
         RenderState.additive();
         Vfx.sprite(Textures.GLOW, x, y, z, 1.6 * fx.size, 0.0F, alpha(c, 0.5F * flash * a));
         Vfx.sprite(Textures.FLARE, x, y, z, 0.8 * fx.size * (1.2F - 0.2F * flash), 0.3F, alpha(Neon.tint(c), flash * a));
      }

      float grow = Easing.outCubic(Easing.clamp01((p - 0.04F) / 0.51F));
      float retract = Easing.inQuad(Easing.clamp01((p - 0.55F) / 0.31F));
      if (!(grow <= 0.0F) && !(retract >= 1.0F)) {
         RenderState.decal(true);
         int offset = 0;

         for (int i = 0; i < n; i++) {
            offset = this.addTendril(fx, f, i, n, (float)i / Math.max(1, n - 1), grow, retract, a, offset);
         }

         Neon.flush();
         RenderState.decal(false);
      }
   }

   private int addTendril(LandingFx fx, FxFrame f, int i, int n, float t, float grow, float retract, float a, int offset) {
      this.path(fx, i, n, offset);
      int head = Math.max(1, Math.round(grow * 25.0F));
      int tail = Math.round(retract * 25.0F);
      int c = this.color.getColor(t);
      double baseW = 0.095 * Math.sqrt(fx.size);
      int from = 7 + (int)(fx.rand(i * 5 + 7) * 8.0F);
      double rootX = Vfx.X[offset + from];
      double rootZ = Vfx.Z[offset + from];
      int count = fill(offset, tail, head, c, baseW, a);
      if (count >= 2) {
         Ground.toRender(f, fx.ground, offset + tail, count);
         Neon.addFlat(offset + tail, count);
      }

      int next = offset + 26;
      if (head > from + 1 && tail < from) {
         double dir = this.angle(fx, i, n) + (fx.rand(i * 5 + 9) < 0.5F ? -0.7 : 0.7);
         double step = this.length.get() * fx.size / 25.0 * 0.8;
         int forkLen = Math.max(2, Math.round((float)(head - from) / (25 - from) * 12.0F));
         Procedural.tendril(next, forkLen, rootX, fx.y(), rootZ, dir, step, 0.8F * this.wiggle.getFloat(), 1.6F, fx.seed + i * 211);
         int fc = fill(next, 0, forkLen - 1, c, baseW * 0.6, a * 0.85F);
         if (fc >= 2) {
            Ground.toRender(f, fx.ground, next, fc);
            Neon.addFlat(next, fc);
         }

         next += 12;
      }

      return next;
   }

   private static int fill(int offset, int tail, int head, int c, double baseW, float a) {
      for (int k = tail; k <= head; k++) {
         float s = (float)k / Math.max(1, head);
         float taper = 1.0F - 0.7F * s;
         float tipGlow = 1.0F + 0.5F * (float)Math.exp(-(head - k) * 0.6);
         Vfx.W[offset + k] = (float)(baseW * taper);
         Vfx.C[offset + k] = ColorUtil.mulAlpha(c, Math.min(1.0F, a * tipGlow));
      }

      return head - tail + 1;
   }
}
