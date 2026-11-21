package xyz.daikirai.render.furiah.render;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;

public final class PoseSnapshot {
   public static final PoseSnapshot PREVIEW_MANNEQUIN = new PoseSnapshot().mannequin(0.0F, false, 0.0);
   public double x;
   public double y;
   public double z;
   public float bodyYaw;
   public float netHeadYaw;
   public float headPitch;
   public float limbSwing;
   public float limbSwingAmount;
   public float swingProgress;
   public float ageInTicks;
   public float deathAngle;
   public boolean sneaking;
   public boolean slim;
   public boolean aimedBow;
   public int heldItemRight;
   public float scale = 0.9375F;
   public boolean valid;

   public PoseSnapshot capture(EntityLivingBase e, float pt) {
      this.x = e.lastTickPosX + (e.posX - e.lastTickPosX) * pt;
      this.y = e.lastTickPosY + (e.posY - e.lastTickPosY) * pt;
      this.z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * pt;
      this.bodyYaw = interpolateRotation(e.prevRenderYawOffset, e.renderYawOffset, pt);
      float headYaw = interpolateRotation(e.prevRotationYawHead, e.rotationYawHead, pt);
      this.netHeadYaw = headYaw - this.bodyYaw;
      this.headPitch = e.prevRotationPitch + (e.rotationPitch - e.prevRotationPitch) * pt;
      this.ageInTicks = e.ticksExisted + pt;
      this.limbSwingAmount = Math.min(1.0F, e.prevLimbSwingAmount + (e.limbSwingAmount - e.prevLimbSwingAmount) * pt);
      this.limbSwing = e.limbSwing - e.limbSwingAmount * (1.0F - pt);
      this.swingProgress = e.getSwingProgress(pt);
      this.sneaking = e.isSneaking();
      if (e.deathTime > 0) {
         float f = (float)Math.sqrt((e.deathTime + pt - 1.0F) / 20.0F * 1.6F);
         this.deathAngle = Math.min(1.0F, f) * 90.0F;
      } else {
         this.deathAngle = 0.0F;
      }

      this.slim = e instanceof AbstractClientPlayer && "slim".equals(((AbstractClientPlayer)e).getSkinType());
      this.heldItemRight = 0;
      this.aimedBow = false;
      if (e instanceof EntityPlayer) {
         EntityPlayer p = (EntityPlayer)e;
         ItemStack held = p.inventory.getCurrentItem();
         int n = this.heldItemRight = held == null ? 0 : 1;
         if (held != null && p.getItemInUseCount() > 0) {
            EnumAction action = held.getItemUseAction();
            if (action == EnumAction.BLOCK) {
               this.heldItemRight = 3;
            } else if (action == EnumAction.BOW) {
               this.aimedBow = true;
            }
         }

         if (this.sneaking && !(e instanceof EntityPlayerSP)) {
            this.y -= 0.125;
         }
      } else {
         ItemStack heldx = e.getHeldItem();
         this.heldItemRight = heldx == null ? 0 : 1;
      }

      this.scale = 0.9375F;
      this.valid = true;
      return this;
   }

   public PoseSnapshot mannequin(float ageInTicks, boolean slim, double feetY) {
      this.x = 0.0;
      this.y = feetY;
      this.z = 0.0;
      this.bodyYaw = 0.0F;
      this.netHeadYaw = 0.0F;
      this.headPitch = 0.0F;
      this.ageInTicks = ageInTicks;
      this.limbSwing = 0.0F;
      this.limbSwingAmount = 0.0F;
      this.swingProgress = 0.0F;
      this.sneaking = false;
      this.deathAngle = 0.0F;
      this.slim = slim;
      this.heldItemRight = 0;
      this.aimedBow = false;
      this.scale = 0.9375F;
      this.valid = true;
      return this;
   }

   public PoseSnapshot copy(PoseSnapshot o) {
      this.x = o.x;
      this.y = o.y;
      this.z = o.z;
      this.bodyYaw = o.bodyYaw;
      this.netHeadYaw = o.netHeadYaw;
      this.headPitch = o.headPitch;
      this.limbSwing = o.limbSwing;
      this.limbSwingAmount = o.limbSwingAmount;
      this.swingProgress = o.swingProgress;
      this.ageInTicks = o.ageInTicks;
      this.deathAngle = o.deathAngle;
      this.sneaking = o.sneaking;
      this.slim = o.slim;
      this.aimedBow = o.aimedBow;
      this.heldItemRight = o.heldItemRight;
      this.scale = o.scale;
      this.valid = o.valid;
      return this;
   }

   private static float interpolateRotation(float prev, float next, float pt) {
      float f = next - prev;

      while (f < -180.0F) {
         f += 360.0F;
      }

      while (f >= 180.0F) {
         f -= 360.0F;
      }

      return prev + pt * f;
   }
}
