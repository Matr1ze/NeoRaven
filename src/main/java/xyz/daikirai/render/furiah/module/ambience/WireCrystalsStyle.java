package xyz.daikirai.render.furiah.module.ambience;

import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.MiniMesh;
import xyz.daikirai.render.furiah.render.Polyhedra;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

final class WireCrystalsStyle extends AmbientStyleBase {
   private static final Polyhedra.Shape[] SHAPES = Polyhedra.Shape.values();
   final ModeSetting<WireCrystalsStyle.Shapes> shapes;
   final SliderSetting crystalSize;
   final SliderSetting spin;
   final SliderSetting edgeThickness;
   final SliderSetting faceOpacity;
   private final float[] flow = new float[3];

   WireCrystalsStyle(FuriahAmbienceModule module) {
      super(module, AmbientStyle.WIRE_CRYSTALS, spec());
      this.shapes = this.setting(
         new ModeSetting<>("crystals.shapes", "Shapes", "Which shapes float around.", WireCrystalsStyle.Shapes.class, WireCrystalsStyle.Shapes.MIXED)
      );
      this.crystalSize = this.setting(
         new SliderSetting("crystals.crystalSize", "Crystal Size", "How big the crystals are.", 0.2, 1.5, 0.75, 0.05).unit(" blocks")
      );
      this.spin = this.setting(new SliderSetting("crystals.spin", "Spin Speed", "How fast they tumble.", 0.0, 3.0, 1.0, 0.05).unit("×"));
      this.edgeThickness = this.setting(new SliderSetting("crystals.edge", "Edge Thickness", "How thick the glowing edges are.", 0.3, 3.0, 1.0, 0.05).unit("×"));
      this.faceOpacity = this.setting(new SliderSetting("crystals.faces", "Face Opacity", "How strongly the faces are tinted.", 0.0, 60.0, 12.0, 1.0).unit("%"));
   }

   private static AmbientStyleBase.Spec spec() {
      AmbientStyleBase.Spec s = new AmbientStyleBase.Spec();
      s.baseDensity = 0.0018F;
      s.nearBias = 1.3F;
      s.opacity = 90.0F;
      s.followWind = 25.0F;
      s.heightMin = 1.0F;
      s.heightMax = 10.0F;
      s.heightLimit = 40.0F;
      s.cap = 300;
      s.densityHelp = "How many crystals float around.";
      return s;
   }

   @Override
   protected boolean spawn(AmbientEnv env, int i, float r, boolean fresh) {
      if (!this.randomPlace(env, i, r, 0.0F)) {
         return false;
      } else {
         AmbientPool p = this.pool;
         p.a[i] = this.rng.next() * 6.283F;
         p.b[i] = this.rng.range(0.25F, 0.7F) * (this.rng.next() < 0.5F ? -1 : 1);
         p.c[i] = this.rng.next() * 6.283F;
         WireCrystalsStyle.Shapes sh = this.shapes.get();
         p.d[i] = sh == WireCrystalsStyle.Shapes.MIXED ? this.rng.nextInt(3) : sh.ordinal();
         p.size[i] = this.rng.range(0.7F, 1.3F);
         p.life[i] = this.rng.range(9.0F, 20.0F);
         p.age[i] = fresh ? this.rng.next() * p.life[i] * 0.6F : 0.0F;
         return true;
      }
   }

   @Override
   protected boolean step(AmbientEnv env, int i, float r, float dt) {
      AmbientPool p = this.pool;
      if (p.age[i] >= p.life[i]) {
         return false;
      } else {
         float sp = this.speed.getFloat();
         this.windAt(env, i, this.followWind.getFloat() / 100.0F, this.flow);
         float bob = 0.18F * (float)Math.cos(p.age[i] * 0.8F * sp + p.c[i]);
         double nx = p.x[i] + this.flow[0] * 0.35F * sp * dt;
         double nz = p.z[i] + this.flow[2] * 0.35F * sp * dt;
         double ny = p.y[i] + (bob + this.flow[1] * 0.2F) * sp * dt;
         if (env.solidAt(nx, ny, nz)) {
            return true;
         } else {
            p.x[i] = nx;
            p.y[i] = ny;
            p.z[i] = nz;
            p.a[i] = p.a[i] + p.b[i] * this.spin.getFloat() * sp * dt;
            return true;
         }
      }
   }

   @Override
   void draw(AmbientEnv env) {
      float r = this.radius(env);
      float faces = this.faceOpacity.getFloat() / 100.0F;
      float glowK = this.glowK();
      if (faces > 0.0F) {
         RenderState.translucent(false);

         for (int i = 0; i < this.pool.count; i++) {
            float a = this.crystal(env, i, r);
            if (a > 0.0F) {
               Polyhedra.faces(
                  SHAPES[(int)this.pool.d[i]], this.rx, this.ry, this.rz, this.radiusOf(i), this.fogColor(env, this.lit(env, this.colorOf(i))), a * faces
               );
            }
         }

         Vfx.flush();
      }

      RenderState.additive();
      if (glowK > 0.0F) {
         for (int ix = 0; ix < this.pool.count; ix++) {
            float a = this.crystal(env, ix, r);
            if (a > 0.0F) {
               double w = Math.max(0.02, this.radiusOf(ix) * 0.18 * this.edgeThickness.getFloat());
               Polyhedra.edges(
                  SHAPES[(int)this.pool.d[ix]],
                  this.rx,
                  this.ry,
                  this.rz,
                  this.radiusOf(ix),
                  Textures.LINE_GLOW,
                  w,
                  this.colorOf(ix),
                  this.fogged(a) * 0.45F * glowK
               );
            }
         }

         Vfx.flush();
      }

      for (int ixx = 0; ixx < this.pool.count; ixx++) {
         float a = this.crystal(env, ixx, r);
         if (a > 0.0F) {
            double w = Math.max(0.008, this.radiusOf(ixx) * 0.045 * this.edgeThickness.getFloat());
            Polyhedra.edges(
               SHAPES[(int)this.pool.d[ixx]],
               this.rx,
               this.ry,
               this.rz,
               this.radiusOf(ixx),
               Textures.LINE_CORE,
               w,
               Neon.tint(this.colorOf(ixx)),
               this.fogged(a)
            );
         }
      }

      Vfx.flush();
   }

   private double radiusOf(int i) {
      return this.crystalSize.getFloat() * 0.5 * this.pool.size[i] * this.size.getFloat();
   }

   private int colorOf(int i) {
      return this.color.getColor(this.hash(i, 11));
   }

   private float crystal(AmbientEnv env, int i, float r) {
      if (this.place(env, i, (float)this.radiusOf(i) * 1.2F) && !(this.fogK >= 1.0F)) {
         AmbientPool p = this.pool;
         float life = Easing.smoothstep(0.0F, 1.2F, p.age[i]) * (1.0F - Easing.smoothstep(p.life[i] - 1.5F, p.life[i], p.age[i]));
         float a = this.fade(env, i, r) * life;
         if (a <= 0.004F) {
            return 0.0F;
         } else {
            float ax = this.hash(i, 1) * 2.0F - 1.0F;
            float ay = this.hash(i, 2) * 2.0F - 1.0F + 0.4F;
            float az = this.hash(i, 3) * 2.0F - 1.0F;
            float angle = p.a[i] - p.b[i] * this.spin.getFloat() * this.speed.getFloat() * 0.05F * (1.0F - env.partialTicks);
            MiniMesh.rotation(ax, ay, az, angle);
            return a;
         }
      } else {
         return 0.0F;
      }
   }

   static enum Shapes implements Displayable {
      CUBE("Cube", "Cubes"),
      OCTAHEDRON("Octahedron", "Eight-sided diamonds"),
      ICOSAHEDRON("Icosahedron", "Twenty-sided gems"),
      MIXED("Mixed", "All three");

      private final String displayName;
      private final String description;

      private Shapes(String displayName, String description) {
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
