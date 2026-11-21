package xyz.daikirai.render.furiah.fx.trail;

public final class PathRecorder {
   public static final int ON_GROUND = 1;
   public static final int SPRINTING = 2;
   public static final int SNEAKING = 4;
   public static final int IN_WATER = 8;
   public static final int FLYING = 16;
   public static final int RIDING = 32;
   public static final int BREAK = 64;
   private static final double MOVING_SAMPLE_SECONDS = 0.05;
   private final int capacity;
   private final double[] xs;
   private final double[] ys;
   private final double[] zs;
   private final double[] dist;
   private final double[] time;
   private final float[] speed;
   private final float[] yaw;
   private final byte[] flags;
   private int first;
   private int count;
   private double traveled;
   private double lastX;
   private double lastY;
   private double lastZ;
   private double lastSampleTime;
   private boolean hasLast;
   private boolean broken;

   public PathRecorder(int capacity) {
      this.capacity = capacity;
      this.xs = new double[capacity];
      this.ys = new double[capacity];
      this.zs = new double[capacity];
      this.dist = new double[capacity];
      this.time = new double[capacity];
      this.speed = new float[capacity];
      this.yaw = new float[capacity];
      this.flags = new byte[capacity];
   }

   public boolean feed(double x, double y, double z, double t, float spd, float bodyYaw, int state, double spacing, boolean grow) {
      if (!grow) {
         if (this.hasLast) {
            this.broken = true;
         }

         this.hasLast = false;
         return false;
      } else if (!this.hasLast) {
         this.hasLast = true;
         this.lastX = x;
         this.lastY = y;
         this.lastZ = z;
         this.lastSampleTime = t;
         this.add(x, y, z, t, spd, bodyYaw, state);
         return true;
      } else {
         double dx = x - this.lastX;
         double dy = y - this.lastY;
         double dz = z - this.lastZ;
         double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
         boolean far = moved >= spacing;
         boolean due = moved > 0.002 && t - this.lastSampleTime >= 0.05;
         if (!far && !due) {
            return false;
         } else {
            this.traveled += moved;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
            this.lastSampleTime = t;
            this.add(x, y, z, t, spd, bodyYaw, state);
            return true;
         }
      }
   }

   private void add(double x, double y, double z, double t, float spd, float bodyYaw, int state) {
      int i;
      if (this.count == this.capacity) {
         i = this.first;
         this.first = (this.first + 1) % this.capacity;
      } else {
         i = (this.first + this.count) % this.capacity;
         this.count++;
      }

      this.xs[i] = x;
      this.ys[i] = y;
      this.zs[i] = z;
      this.dist[i] = this.traveled;
      this.time[i] = t;
      this.speed[i] = spd;
      this.yaw[i] = bodyYaw;
      this.flags[i] = (byte)(state | (this.broken ? 64 : 0));
      this.broken = false;
   }

   public void finish(double x, double y, double z, double t, float spd, float bodyYaw, int state) {
      if (this.hasLast) {
         double dx = x - this.lastX;
         double dy = y - this.lastY;
         double dz = z - this.lastZ;
         double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
         if (moved > 0.002) {
            this.traveled += moved;
            this.add(x, y, z, t, spd, bodyYaw, state);
         }

         this.hasLast = false;
         this.broken = true;
      }
   }

   public void breakRun() {
      if (this.hasLast) {
         this.broken = true;
      }

      this.hasLast = false;
   }

   public void expire(double now, double maxAge, double maxLength, double headTravel) {
      for (double headDist = this.traveled + headTravel; this.count > 0; this.count--) {
         int i = this.first;
         if (!(now - this.time[this.first] > maxAge) && !(headDist - this.dist[i] > maxLength)) {
            break;
         }

         this.first = (this.first + 1) % this.capacity;
      }
   }

   public void reset() {
      this.first = 0;
      this.count = 0;
      this.hasLast = false;
      this.broken = false;
      this.traveled = 0.0;
   }

   public boolean attached() {
      return this.hasLast && this.count > 0;
   }

   public int count() {
      return this.count;
   }

   private int at(int i) {
      return (this.first + i) % this.capacity;
   }

   public double x(int i) {
      return this.xs[this.at(i)];
   }

   public double y(int i) {
      return this.ys[this.at(i)];
   }

   public double z(int i) {
      return this.zs[this.at(i)];
   }

   public double distance(int i) {
      return this.dist[this.at(i)];
   }

   public double time(int i) {
      return this.time[this.at(i)];
   }

   public float speed(int i) {
      return this.speed[this.at(i)];
   }

   public float yaw(int i) {
      return this.yaw[this.at(i)];
   }

   public int flags(int i) {
      return this.flags[this.at(i)];
   }

   public double traveled() {
      return this.traveled;
   }

   public double newestTime() {
      return this.count == 0 ? Double.NaN : this.time[this.at(this.count - 1)];
   }

   public double sinceLast(double x, double y, double z) {
      if (!this.attached()) {
         return 0.0;
      } else {
         double dx = x - this.lastX;
         double dy = y - this.lastY;
         double dz = z - this.lastZ;
         return Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }
}
