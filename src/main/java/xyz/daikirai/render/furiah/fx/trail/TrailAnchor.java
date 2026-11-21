package xyz.daikirai.render.furiah.fx.trail;

import xyz.daikirai.render.furiah.setting.Displayable;

public enum TrailAnchor implements Displayable {
   FEET("Feet", "At the feet"),
   BOTH_FEET("Both feet", "One trail from each foot"),
   WAIST("Waist", "At the hips"),
   BACK("Back", "Just behind the back"),
   HEAD("Head", "At the head"),
   HANDS("Hands", "One trail from each hand");

   private static final double PX = 0.05859375;
   private static final double SNEAK_LEAN = 0.5;
   private final String name;
   private final String description;

   private TrailAnchor(String name, String description) {
      this.name = name;
      this.description = description;
   }

   @Override
   public String displayName() {
      return this.name;
   }

   @Override
   public String description() {
      return this.description;
   }

   public int sides() {
      return this != BOTH_FEET && this != HANDS ? 1 : 2;
   }

   public void place(TrailPose p, int side, double[] out) {
      double yaw = Math.toRadians(p.bodyYaw);
      double fx = -Math.sin(yaw);
      double fz = Math.cos(yaw);
      double rx = -fz;
      double right = 0.0;
      double mz = 0.0;
      boolean r = side == 1;
      double my;
      switch (this) {
         case FEET:
            out[0] = p.x;
            out[1] = p.y + 0.06;
            out[2] = p.z;
            return;
         case BOTH_FEET:
            double b = p.legAngle(r);
            double hy = p.sneaking ? 9.0 : 12.0;
            double hz = p.sneaking ? 4.0 : 0.0;
            right = r ? 1.9 : -1.9;
            my = hy + 11.0 * Math.cos(b);
            mz = hz + 11.0 * Math.sin(b);
            break;
         case HANDS:
            double a = p.armAngle(r);
            right = r ? 6.0 : -6.0;
            my = 2.0 + 10.0 * Math.cos(a);
            mz = 10.0 * Math.sin(a);
            break;
         case WAIST:
            my = 9.5;
            break;
         case BACK:
            my = 2.7;
            mz = 4.8;
            break;
         default:
            my = p.sneaking ? -3.0 : -4.0;
      }

      if (p.sneaking && (this == WAIST || this == BACK)) {
         double c = Math.cos(0.5);
         double s = Math.sin(0.5);
         double ny = my * c - mz * s;
         double nz = my * s + mz * c;
         my = ny;
         mz = nz;
      }

      double up = (24.0 - my) * 0.05859375;
      if (p.sneaking) {
         up -= 0.1875 + (p.other ? 0.125 : 0.0);
      }

      double fwd = -mz * 0.05859375;
      double side2 = right * 0.05859375;
      out[0] = p.x + fx * fwd + rx * side2;
      out[1] = p.y + up;
      out[2] = p.z + fz * fwd + fx * side2;
   }
}
