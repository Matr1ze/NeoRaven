package xyz.daikirai.render.furiah.module.trails;

import java.util.Arrays;
import xyz.daikirai.render.furiah.fx.trail.PathRecorder;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailPose;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

public final class PlayerTrail {
   static final int CAPACITY = 512;
   public final int entityId;
   public final boolean self;
   final PathRecorder[][] paths = new PathRecorder[TrailAnchor.values().length][2];
   final long[] fed = new long[TrailAnchor.values().length];
   public final TrailPose pose = new TrailPose();
   public ResourceLocation skin;
   public boolean slim;
   public EntityPlayer entity;
   final PlayerTrail.StyleState[] styles = new PlayerTrail.StyleState[TrailStyle.values().length];
   double lastX;
   double lastY;
   double lastZ;
   boolean hasLast;
   public float speed;
   public float moveSpeed;
   public int state;
   public boolean growing;
   float sneak;
   float shown = 1.0F;
   boolean canShow;
   float fadeTime = 0.3F;
   long pickedFrame;
   double lastSeen = Double.NaN;
   double lookAgain;
   public int teamColor;
   public boolean reduced;
   public float light = 1.0F;
   public float matLight = 1.0F;
   public float night;
   boolean wasSprinting;
   double burstAt = Double.NEGATIVE_INFINITY;
   float bubblesDue;
   public TrailShape shape;
   public PathRecorder path;
   public int side;
   public double headX;
   public double headY;
   public double headZ;
   public boolean attached;
   public float alpha;
   public float widthScale;
   public float maxAge;
   public PlayerTrail.StyleState style;
   public boolean ghost;

   PlayerTrail(int entityId, boolean self) {
      this.entityId = entityId;
      this.self = self;
   }

   PathRecorder path(TrailAnchor anchor, int side) {
      PathRecorder r = this.paths[anchor.ordinal()][side];
      if (r == null) {
         PathRecorder pathRecorder = new PathRecorder(512);
         this.paths[anchor.ordinal()][side] = pathRecorder;
         r = pathRecorder;
      }

      return r;
   }

   public PlayerTrail.StyleState styleOf(TrailStyle type) {
      return this.styles[type.ordinal()];
   }

   public float sneakForTest() {
      return this.sneak;
   }

   PlayerTrail.StyleState style(TrailStyle type) {
      PlayerTrail.StyleState s = this.styles[type.ordinal()];
      if (s == null) {
         PlayerTrail.StyleState styleState = new PlayerTrail.StyleState();
         this.styles[type.ordinal()] = styleState;
         s = styleState;
      }

      return s;
   }

   void reset() {
      PathRecorder[][] pathRecorderArray = this.paths;
      int n = pathRecorderArray.length;

      for (int i = 0; i < n; i++) {
         PathRecorder[] pathRecorderArray2;
         for (PathRecorder r : pathRecorderArray2 = pathRecorderArray[i]) {
            if (r != null) {
               r.reset();
            }
         }
      }

      for (PlayerTrail.StyleState st : this.styles) {
         if (st != null) {
            st.reset();
         }
      }

      this.hasLast = false;
      this.moveSpeed = 0.0F;
      this.speed = 0.0F;
      this.growing = false;
   }

   public static final class StyleState {
      public float alpha = 1.0F;
      public float head;
      public float retract;
      public float retractFrom;
      public double since = Double.NEGATIVE_INFINITY;
      public double frozenAt = Double.NaN;
      public float maxAge = 1.0F;
      public double ghostSince = Double.NEGATIVE_INFINITY;
      public double ghostUntil = Double.NaN;
      public double ghostFrozenAt = Double.NaN;
      public float ghostAlpha;
      public float ghostMaxAge;
      boolean grew;
      public final float[] length = new float[2];
      public final double[] next = new double[2];
      public final double[] vars = new double[4];

      public boolean hasGhost() {
         return !Double.isNaN(this.ghostUntil);
      }

      void reset() {
         this.alpha = 1.0F;
         this.head = 0.0F;
         this.retract = 0.0F;
         this.since = Double.NEGATIVE_INFINITY;
         this.frozenAt = Double.NaN;
         this.ghostUntil = Double.NaN;
         this.grew = false;
         this.length[1] = 0.0F;
         this.length[0] = 0.0F;
         this.next[1] = 0.0;
         this.next[0] = 0.0;
         Arrays.fill(this.vars, 0.0);
      }
   }
}
