package xyz.daikirai.render.furiah.module.landing;

import xyz.daikirai.render.furiah.fx.Anchor;
import xyz.daikirai.render.furiah.fx.EffectInstance;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.pass.PassItem;

public final class LandingFx extends EffectInstance implements PassItem {
   public final LandingStyleBase style;
   public final LandingContext land;
   public final FxParticles particles;
   public final float size;
   public final float opacity;
   public final float density;
   public final boolean preview;
   public Terrain.HeightField ground;
   public Object data;
   FxFrame passFrame;
   public float light = 1.0F;
   public float night;

   LandingFx(
      LandingStyleBase style,
      LandingContext land,
      Anchor anchor,
      float life,
      float timeScale,
      int seed,
      FxParticles particles,
      float size,
      float opacity,
      float density,
      boolean preview
   ) {
      super(anchor, style.lifecycle(), life, timeScale, seed, land.playerId);
      this.style = style;
      this.land = land;
      this.particles = particles;
      this.size = size;
      this.opacity = opacity;
      this.density = density;
      this.preview = preview;
   }

   @Override
   public void start() {
      this.style.start(this);
   }

   @Override
   public void render(FxFrame f) {
      this.style.render(this, f);
   }

   @Override
   public int passes() {
      return this.style.passes(this);
   }

   @Override
   public void drawPass(FxPass pass) {
      if (this.passFrame != null) {
         this.style.renderPass(this, this.passFrame, pass);
      }
   }

   @Override
   public double passDistanceSq() {
      return this.passFrame == null ? 0.0 : this.passFrame.distanceSqToEye(this.x(), this.y() + 0.5, this.z());
   }

   @Override
   public void passFailed() {
      this.age = this.life;
   }

   public double x() {
      return this.anchor.x;
   }

   public double y() {
      return this.anchor.y;
   }

   public double z() {
      return this.anchor.z;
   }

   public float alpha() {
      return this.opacity;
   }

   public int count(float base) {
      return Math.max(1, Math.round(base * this.density));
   }

   public float seconds(float base) {
      return base * this.timeScale;
   }
}
