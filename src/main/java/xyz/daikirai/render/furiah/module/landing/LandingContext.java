package xyz.daikirai.render.furiah.module.landing;

import xyz.daikirai.render.furiah.render.PoseSnapshot;
import net.minecraft.entity.player.EntityPlayer;

public final class LandingContext {
   public boolean jump;
   public float strength = 1.0F;
   public EntityPlayer player;
   public int playerId = -1;
   public boolean self;
   public double x;
   public double y;
   public double z;
   public float fallHeight;
   public float fallScale = 1.0F;
   public float yaw;
   public final PoseSnapshot pose = new PoseSnapshot();
   public int blockColor = -7697782;
}
