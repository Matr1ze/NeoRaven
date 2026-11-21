package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class BokehStyle extends AmbientStyleBase {
   private static final float NEAR = 5.0F;
   private static final float FAR = 30.0F;
   final ModeSetting<BokehStyle.Shape> shape;
   final SliderSetting orbSize;
   final SliderSetting softness;
   private final float[] flow = new float[3];

   BokehStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.BOKEH, spec());
      this.shape = this.setting(
         new ModeSetting<>("bokeh.shape", "Shape", "Round or hexagonal (a hexagon aperture).", BokehStyle.Shape.class, BokehStyle.Shape.CIRCLE)
      );
      this.orbSize = this.setting(new SliderSetting("bokeh.orbSize", "Orb Size", "How big the orbs are.", 0.5, 5.0, 1.8, 0.1).unit(" blocks"));
      this.softness = this.setting(
         new SliderSetting("bokeh.softness", "Softness", "From a crisp disc with a bright rim to a very soft glow.", 0.0, 100.0, 60.0, 5.0).unit("%")
      );
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.0012F;
      s.opacity = 28.0F;
      s.followWind = 20.0F;
      s.heightMin = 1.0F;
      s.heightMax = 14.0F;
      s.heightLimit = 40.0F;
      s.distance = 40;
      s.cap = 250;
      s.glow = false;
      s.densityHelp = "How many orbs there are.";
      return s;
   }

   @Override
   double volume(float r) {
      float far = Math.min(30.0F, r);
      float near = Math.min(5.0F, far * 0.5F);
      float band = Math.max(0.5F, this.highest() - this.lowest());
      return Math.PI * (far * far - near * near) * Math.min(band, 2.0F * far);
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      float far = Math.min(30.0F, r);
      if (!this.randomPlace(env, i, far, Math.min(5.0F, far * 0.5F))) {
         return false;
      } else {
         AmbientPool p = this.pool;
         p.size[i] = this.rng.range(0.6F, 1.5F);
         double ang = this.rng.next() * 6.283;
         p.a[i] = (float)Math.cos(ang) * this.rng.range(0.1F, 0.3F);
         p.b[i] = (float)Math.sin(ang) * this.rng.range(0.1F, 0.3F);
         p.c[i] = this.rng.next() * 6.283F;
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      float sp = this.speed.getFloat();
      this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
      p.x[i] = p.x[i] + (p.a[i] + this.flow[0] * 0.3F) * sp * dt;
      p.z[i] = p.z[i] + (p.b[i] + this.flow[2] * 0.3F) * sp * dt;
      p.y[i] = p.y[i] + 0.08F * (float)Math.sin(p.age[i] * 0.4F + p.c[i]) * sp * dt;
      return true;
   }

   @Override
   protected boolean wrap(AmbientEnv env, int i, float r) {
      return super.wrap(env, i, Math.min(30.0F, r));
   }

   @Override
   void draw(AmbientEnv env) {
      float r = Math.min(30.0F, this.radius(env));
      float soft = this.softness.getFloat() / 100.0F;
      boolean hex = this.shape.get() == BokehStyle.Shape.HEXAGON;
      RenderState.additive();
      if (soft < 1.0F) {
         this.layer(env, r, hex ? Textures.BOKEH_HEX : Textures.BOKEH_CIRCLE, 1.0F - soft);
      }

      if (soft > 0.0F) {
         this.layer(env, r, hex ? Textures.BOKEH_HEX_SOFT : Textures.BOKEH_CIRCLE_SOFT, soft);
      }
   }

   private void layer(AmbientEnv env, float r, int texture, float weight) {
      float s0 = this.orbSize.getFloat() * this.size.getFloat();
      float glowK = this.glowK();

      for (int i = 0; i < this.pool.count; i++) {
         float s = s0 * this.pool.size[i];
         if (this.place(env, i, s)) {
            float near = Easing.smoothstep(3.0F, 6.0F, this.dist);
            float shimmer = 0.85F + 0.15F * (float)Math.sin(env.time * 0.7 + this.pool.c[i]);
            float a = this.fogged(this.fade(env, i, r)) * near * shimmer * weight * (1.0F + 0.5F * glowK);
            if (a > 0.003F) {
               Vfx.sprite(texture, this.rx, this.ry, this.rz, s, 0.0F, ColorUtil.mulAlpha(this.color.getColor(this.hash(i, 7)), a));
            }
         }
      }

      Vfx.flush();
   }

   static enum Shape implements Displayable {
      CIRCLE("Circle", "Round discs"),
      HEXAGON("Hexagon", "Six-sided, like a camera's aperture");

      private final String displayName;
      private final String description;

      private Shape(String displayName, String description) {
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
