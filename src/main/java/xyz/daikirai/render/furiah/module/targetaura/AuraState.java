package xyz.daikirai.render.furiah.module.targetaura;

import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.World;

public final class AuraState {
   public final EntityLivingBase entity;
   public final long startNanos;
   public final int seed;
   public final Random random;
   public float emitAccumulator;
   boolean aimed;
   long lastAimNanos;
   boolean released;
   boolean visible = true;
   float fade;
   float alpha;
   boolean outroActive;
   double lastX;
   double lastY;
   double lastZ;
   boolean hasLastPosition;
   double groundTarget = Double.NaN;
   double groundSmooth = Double.NaN;
   float lightLevel = Float.NaN;
   float night;
   float headTop = Float.NaN;
   float nameBottom;
   float nameTop;

   AuraState(EntityLivingBase entity, long now) {
      this.entity = entity;
      this.startNanos = now;
      this.lastAimNanos = now;
      long noise = System.nanoTime();
      this.seed = (int)(noise ^ noise >>> 32) * -1640531535 + (entity == null ? 0 : entity.getEntityId());
      this.random = new Random(this.seed);
   }

   public double age(long now) {
      return (now - this.startNanos) / 1.0E9;
   }

   public boolean isFadingOut() {
      return !this.visible;
   }

   void updateGround(World world) {
      if (this.entity != null) {
         Vec3 from = new Vec3(this.entity.posX, this.entity.posY + 0.05, this.entity.posZ);
         Vec3 to = new Vec3(this.entity.posX, this.entity.posY - 4.0, this.entity.posZ);
         MovingObjectPosition hit = world.rayTraceBlocks(from, to, false, true, false);
         this.groundTarget = hit != null && hit.typeOfHit == MovingObjectType.BLOCK && hit.hitVec != null
            ? hit.hitVec.yCoord
            : this.entity.posY - 4.0;
      }
   }
}
