package xyz.daikirai.render.furiah.fx;

import xyz.daikirai.render.furiah.render.Easing;

public abstract class EffectInstance {
   public final Anchor anchor;
   public final Lifecycle lifecycle;
   public final float life;
   public final float timeScale;
   public final int seed;
   public final int entityId;
   public float age;
   public boolean lod;

   protected EffectInstance(Anchor anchor, Lifecycle lifecycle, float life, float timeScale, int seed, int entityId) {
      this.anchor = anchor;
      this.lifecycle = lifecycle;
      this.life = Math.max(0.05F, life);
      this.timeScale = Math.max(0.05F, timeScale);
      this.seed = seed;
      this.entityId = entityId;
   }

   public void start() {
   }

   public void update(float dt) {
      this.age += dt;
   }

   public abstract void render(FxFrame var1);

   public boolean isFinished() {
      return this.age >= this.life;
   }

   public void finished() {
   }

   public final float progress() {
      return Easing.clamp01(this.age / this.life);
   }

   public final float styleTime() {
      return this.age / this.timeScale;
   }

   public final float rand(int i) {
      return Rng.hash(this.seed, i);
   }

   public final float srand(int i) {
      return Rng.hash(this.seed, i) * 2.0F - 1.0F;
   }
}
