package xyz.daikirai.render.furiah.module.targetaura;

import xyz.daikirai.render.furiah.fx.look.KeepVisible;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ParticleSystem;

public final class EffectContext {
   public double x;
   public double y;
   public double z;
   public double groundY;
   public float groundAlpha;
   public double worldX;
   public double worldY;
   public double worldZ;
   public double worldGroundY;
   public float width;
   public float height;
   public static final float HEAD_TOP = 1.875F;
   public float headTop = 1.875F;
   public float nameBottom = 1.875F;
   public float nameTop = 1.875F;
   public float bodyYaw;
   public float alpha;
   public float scale;
   public float intro;
   public double time;
   public double globalTime;
   public float dt;
   public int seed;
   public boolean spawnParticles;
   public boolean preview;
   public boolean fadingOut;
   public float outro;
   public float light = 1.0F;
   public float lightLevel = 1.0F;
   public float night;
   public final KeepVisible keep = new KeepVisible();
   public double distanceSq;
   public AuraState state;
   public Camera cam;
   public ParticleSystem particles;
}
