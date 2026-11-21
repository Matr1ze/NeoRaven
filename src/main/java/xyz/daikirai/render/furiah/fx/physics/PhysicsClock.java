package xyz.daikirai.render.furiah.fx.physics;

public final class PhysicsClock {
   public static final double STEP = 0.008333333333333333;
   private static final int MAX_STEPS = 12;
   private double acc;

   public int advance(double dt) {
      this.acc = this.acc + Math.max(0.0, Math.min(0.25, dt));
      int n = (int)(this.acc / 0.008333333333333333);
      if (n > 12) {
         n = 12;
         this.acc = 0.0;
      } else {
         this.acc -= n * 0.008333333333333333;
      }

      return n;
   }

   public double alpha() {
      return Math.max(0.0, Math.min(1.0, this.acc / 0.008333333333333333));
   }
}
