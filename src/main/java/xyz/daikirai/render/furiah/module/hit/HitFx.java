package xyz.daikirai.render.furiah.module.hit;

import xyz.daikirai.render.furiah.fx.Anchor;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.EffectInstance;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.look.KeepVisible;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.pass.PassItem;

public final class HitFx extends EffectInstance implements PassItem {
   public final HitStyleBase style;
   public final HitContext hit;
   public final Basis basis = new Basis();
   public final FxParticles particles;
   public final float size;
   public final float opacity;
   public final float density;
   public final boolean crit;
   public final boolean preview;
   public float fade = 1.0F;
   public Object data;
   float cut = -1.0F;
   static final float CUT_SECONDS = 0.12F;
   FxFrame passFrame;
   public float light = 1.0F;
   public float night;
   public final KeepVisible keep = new KeepVisible();

   HitFx(
      HitStyleBase style,
      HitContext hit,
      Anchor anchor,
      float life,
      float timeScale,
      int seed,
      FxParticles particles,
      float size,
      float opacity,
      float density,
      boolean crit,
      boolean preview
   ) {
      super(anchor, style.lifecycle(), life, timeScale, seed, hit.targetId);
      this.style = style;
      this.hit = hit;
      this.particles = particles;
      this.size = size;
      this.opacity = opacity;
      this.density = density;
      this.crit = crit;
      this.preview = preview;
      this.basis.set(hit.dirX, hit.dirY, hit.dirZ);
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
   public void finished() {
      this.style.finish(this);
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
      return this.passFrame == null ? 0.0 : this.passFrame.distanceSqToEye(this.cx(), this.cy(), this.cz());
   }

   @Override
   public void passFailed() {
      this.age = this.life;
   }

   @Override
   public void update(float dt) {
      super.update(dt);
      if (this.cut >= 0.0F) {
         this.cut += dt;
         if (this.cut >= 0.12F) {
            this.age = this.life;
         }
      }
   }

   public double ix() {
      return this.anchor.x;
   }

   public double iy() {
      return this.anchor.y;
   }

   public double iz() {
      return this.anchor.z;
   }

   public double cx() {
      return this.anchor.feetX();
   }

   public double cy() {
      return this.anchor.feetY() + this.hit.height * 0.5;
   }

   public double cz() {
      return this.anchor.feetZ();
   }

   public float alpha() {
      float a = this.opacity * this.fade;
      return this.cut < 0.0F ? a : a * Math.max(0.0F, 1.0F - this.cut / 0.12F);
   }

   public int count(float base, boolean critDoubles) {
      float n = base * this.density * (critDoubles && this.crit ? 2.0F : 1.0F);
      return Math.max(1, Math.round(n));
   }

   public float seconds(float base) {
      return base * this.timeScale;
   }
}
