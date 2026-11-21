package xyz.daikirai.render.furiah.module.trails;

import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.trail.LightCache;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.Easing;
import net.minecraft.world.World;

public final class TrailFrame {
   public final Camera cam = new Camera();
   public double viewX;
   public double viewY;
   public double viewZ;
   public double eyeX;
   public double eyeY;
   public double eyeZ;
   public float partialTicks;
   public float dt;
   public double now;
   public double colorTime;
   public boolean preview;
   public World world;
   public float nearInner;
   public float nearOuter;
   public float pixelsPerBlock;
   public boolean fog;
   public boolean lit;
   private final LightCache lights = new LightCache();
   private final FxParticles[] pieces = new FxParticles[TrailStyle.values().length];
   public int pieceCapacity = 768;
   private final TrailStyleBase.Store[] stores = new TrailStyleBase.Store[TrailStyle.values().length];

   public float lightAt(double x, double y, double z) {
      return this.lit ? this.lights.at(this.world, x, y, z) : 1.0F;
   }

   public void updateLight() {
      if (this.lit) {
         this.lights.frame(this.world, this.partialTicks);
      }
   }

   public FxParticles pieces(TrailStyle type) {
      FxParticles p = this.pieces[type.ordinal()];
      if (p == null) {
         FxParticles fxParticles = new FxParticles(this.pieceCapacity, this.preview);
         this.pieces[type.ordinal()] = fxParticles;
         p = fxParticles;
      }

      return p;
   }

   public FxParticles piecesIfAny(TrailStyle type) {
      return this.pieces[type.ordinal()];
   }

   public int pieceCount() {
      int n = 0;

      for (FxParticles p : this.pieces) {
         if (p != null) {
            n += p.size();
         }
      }

      return n;
   }

   public void clearPieces(TrailStyle type) {
      for (int i = 0; i < this.pieces.length; i++) {
         if (this.pieces[i] != null && (type == null || type.ordinal() == i)) {
            this.pieces[i].clear();
         }

         if (this.stores[i] != null && (type == null || type.ordinal() == i)) {
            this.stores[i].clear();
         }
      }
   }

   public TrailStyleBase.Store store(TrailStyle type) {
      return this.stores[type.ordinal()];
   }

   public void setStore(TrailStyle type, TrailStyleBase.Store store) {
      this.stores[type.ordinal()] = store;
   }

   public int allPieces() {
      int n = this.pieceCount();

      for (TrailStyleBase.Store s : this.stores) {
         if (s != null) {
            n += s.size();
         }
      }

      return n;
   }

   public float nearFade(double x, double y, double z) {
      if (this.nearOuter <= 0.0F) {
         return 1.0F;
      } else {
         double dx = x - this.eyeX;
         double dy = y - this.eyeY;
         double dz = z - this.eyeZ;
         float d = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
         return Easing.smoothstep(this.nearInner, this.nearOuter, d);
      }
   }

   public float fog(double x, double y, double z) { return 1.0F; }

   public float pixels(float width, double x, double y, double z) {
      double dx = x - this.eyeX;
      double dy = y - this.eyeY;
      double dz = z - this.eyeZ;
      double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
      return d < 0.001 ? Float.MAX_VALUE : (float)(width * this.pixelsPerBlock / d);
   }
}
