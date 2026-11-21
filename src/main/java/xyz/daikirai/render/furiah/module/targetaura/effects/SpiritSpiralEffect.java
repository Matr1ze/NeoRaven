package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Random;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.AuraState;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class SpiritSpiralEffect extends AuraEffect {
   private static final int SAMPLES = 56;
   public final SliderSetting trails = this.setting(new SliderSetting("spiral.trails", "Trails", "Number of comet trails.", 1.0, 3.0, 2.0, 1.0));
   public final SliderSetting trailLength = this.setting(
      new SliderSetting("spiral.length", "Trail Length", "Length of each tail, in seconds of travel.", 0.2, 1.5, 0.8, 0.05).unit("s")
   );
   public final BooleanSetting groundCircle = this.setting(new BooleanSetting("spiral.ground", "Ground Circle", "Soft glowing circle under the target.", true));
   public final ModeSetting<SpiritSpiralEffect.Particles> particles = this.setting(
      new ModeSetting<>(
         "spiral.particles", "Particles", "Icons that float upward and fade.", SpiritSpiralEffect.Particles.class, SpiritSpiralEffect.Particles.HEARTS
      )
   );
   public final SliderSetting particleRate = this.setting(
      new SliderSetting("spiral.particleRate", "Particle Rate", "Icons per second.", 1.0, 20.0, 5.0, 1.0).unit("/s")
   );

   public SpiritSpiralEffect() {
      super(
         EffectType.SPIRIT_SPIRAL,
         "spiral",
         new ThemeTint()
      );
   }

   private static void path(EffectContext c, int j, int n, double tau, double radiusScale, float spd, int idx) {
      double phase = j * (Math.PI * 2) / n;
      double angle = tau * (Math.PI * 2) * 0.85 * spd + phase;
      double vertical = 0.5 - 0.5 * Math.cos(tau * (Math.PI * 2) * 0.22 * spd + phase);
      double r = (0.55 + 0.05 * Math.sin(tau * 3.1 + j * 1.7)) * radiusScale;
      Vfx.X[idx] = c.x + Math.cos(angle) * r;
      Vfx.Y[idx] = c.y + 0.1 + vertical * (c.height - 0.05);
      Vfx.Z[idx] = c.z + Math.sin(angle) * r;
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float spd = this.speed();
      int n = this.trails.getInt();
      float grow = Easing.outCubic(c.intro);
      double tail = Math.min(this.trailLength.get(), c.time * 1.4 + 0.02);
      double radiusScale = sz * (0.35 + 0.65 * grow);

      for (int j = 0; j < n; j++) {
         for (int k = 0; k < 56; k++) {
            path(c, j, n, c.time - tail * k / 55.0, radiusScale, spd, j * 56 + k);
         }
      }

      if (this.groundCircle.get()) {
         this.drawGround(c, sz, inten, grow);
      }

      for (int var21 = 0; var21 < n; var21++) {
         for (int k = 0; k < 56; k++) {
            float f = k / 55.0F;
            float remain = 1.0F - f;
            Vfx.W[var21 * 56 + k] = 0.18F * sz * (float)Math.pow(remain, 0.7);
            Vfx.C[var21 * 56 + k] = alpha(this.color(c, f), inten * 0.55F * (float)Math.pow(remain, 1.5));
         }

         Vfx.ribbon(Textures.LINE_GLOW, var21 * 56, 56, false);
      }

      for (int var22 = 0; var22 < n; var22++) {
         for (int k = 0; k < 56; k++) {
            float f = k / 55.0F;
            float remain = 1.0F - f;
            Vfx.W[var22 * 56 + k] = 0.05F * sz * (float)Math.pow(remain, 0.9);
            Vfx.C[var22 * 56 + k] = core(this.color(c, f), inten * remain * remain);
         }

         Vfx.ribbon(Textures.LINE_CORE, var22 * 56, 56, false);
      }

      for (int var23 = 0; var23 < n; var23++) {
         int i = var23 * 56;
         Vfx.sprite(Textures.GLOW, Vfx.X[i], Vfx.Y[i], Vfx.Z[i], 0.6F * sz, 0.0F, alpha(this.color(c, 0.0F), 0.65F * inten));
      }

      for (int var24 = 0; var24 < n; var24++) {
         int i = var24 * 56;
         Vfx.sprite(Textures.SPARK, Vfx.X[i], Vfx.Y[i], Vfx.Z[i], 0.22F * sz, 0.0F, core(this.color(c, 0.0F), inten));
      }

      this.emitParticles(c, sz);
   }

   private void drawGround(EffectContext c, float sz, float inten, float grow) {
      float a = inten * c.groundAlpha;
      if (!(a <= 0.003F)) {
         float pulse = 0.85F + 0.15F * (float)Math.sin(c.time * 2.2);
         double y = c.groundY + 0.02;
         RenderState.decal(true);
         Vfx.flatSprite(Textures.GROUND_GLOW, c.x, y, c.z, 0.85 * sz * grow, c.time * 0.4, alpha(this.color(c, 0.5F), 0.55F * a * pulse));
         Vfx.flatRing(Textures.LINE_GLOW, c.x, y, c.z, 0.7 * sz * grow, 0.16 * sz, 96, alpha(this.color(c, 0.3F), 0.35F * a));
         Vfx.flatRing(Textures.LINE_CORE, c.x, y, c.z, 0.7 * sz * grow, 0.03 * sz, 96, core(this.color(c, 0.3F), 0.8F * a * pulse));
         RenderState.decal(false);
      }
   }

   private void emitParticles(EffectContext c, float sz) {
      SpiritSpiralEffect.Particles kind = this.particles.get();
      if (kind != SpiritSpiralEffect.Particles.NONE && c.spawnParticles) {
         AuraState s = c.state;
         s.emitAccumulator = s.emitAccumulator + c.dt * this.particleRate.getFloat();
         Random r = s.random;

         while (s.emitAccumulator >= 1.0F) {
            s.emitAccumulator--;
            double angle = r.nextDouble() * (Math.PI * 2);
            double radius = (0.3 + 0.35 * r.nextDouble()) * sz;
            float size = (0.14F + 0.07F * r.nextFloat()) * sz;
            c.particles
               .spawn(
                  kind == SpiritSpiralEffect.Particles.HEARTS ? 2 : 3,
                  c.worldX + Math.cos(angle) * radius,
                  c.worldY + 0.3 + r.nextDouble() * 1.4,
                  c.worldZ + Math.sin(angle) * radius,
                  (float)Math.cos(angle) * 0.06F,
                  0.32F + 0.25F * r.nextFloat(),
                  (float)Math.sin(angle) * 0.06F,
                  1.4F + 0.7F * r.nextFloat(),
                  size,
                  size * 0.75F,
                  this.color(c, r.nextFloat()),
                  0.0F,
                  0.35F,
                  0.07F,
                  r.nextFloat()
               );
         }
      }
   }

   public static enum Particles implements Displayable {
      NONE("None"),
      HEARTS("Hearts"),
      STARS("Stars");

      private final String displayName;

      private Particles(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return "";
      }
   }
}
