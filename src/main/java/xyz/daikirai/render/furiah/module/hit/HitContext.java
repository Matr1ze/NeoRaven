package xyz.daikirai.render.furiah.module.hit;

import xyz.daikirai.render.furiah.render.PoseSnapshot;
import net.minecraft.entity.Entity;

public final class HitContext {
   public Entity target;
   public int targetId = -1;
   public double feetX;
   public double feetY;
   public double feetZ;
   public double offX;
   public double offY;
   public double offZ;
   public double dirX;
   public double dirY;
   public double dirZ = 1.0;
   public double swipeX = 1.0;
   public double swipeY;
   public double swipeZ;
   public float screenSwipeX = 1.0F;
   public float screenSwipeY;
   public int side = 1;
   public boolean crit;
   public float width = 0.6F;
   public float height = 1.8F;
   public double groundY;
   public long time;
   public final PoseSnapshot pose = new PoseSnapshot();

   public double impactX() {
      return this.feetX + this.offX;
   }

   public double impactY() {
      return this.feetY + this.offY;
   }

   public double impactZ() {
      return this.feetZ + this.offZ;
   }
}
