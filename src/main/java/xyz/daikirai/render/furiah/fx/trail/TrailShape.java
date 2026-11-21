package xyz.daikirai.render.furiah.fx.trail;

public final class TrailShape {
   public static final int MAX = 1024;
   private static final int MAX_RUNS = 32;
   private static final int MAX_CONTROL = 512;
   public final double[] x = new double[1024];
   public final double[] y = new double[1024];
   public final double[] z = new double[1024];
   public final float[] along = new float[1024];
   public final float[] age = new float[1024];
   public final float[] speed = new float[1024];
   public int n;
   public final int[] runStart = new int[32];
   public final int[] runEnd = new int[32];
   public int runs;
   public float length;
   private final double[] cx = new double[512];
   private final double[] cy = new double[512];
   private final double[] cz = new double[512];
   private final double[] sx = new double[512];
   private final double[] sy = new double[512];
   private final double[] sz = new double[512];
   private final float[] cAlong = new float[512];
   private final float[] cAge = new float[512];
   private final float[] cSpeed = new float[512];
   private static final double[] TURN_STEP_COS = new double[10];

   public void save(TrailShape.Saved s) {
      if (s.x.length < this.n) {
         int cap = Math.min(1024, Math.max(this.n, s.x.length * 2));
         s.x = new double[cap];
         s.y = new double[cap];
         s.z = new double[cap];
         s.along = new float[cap];
         s.age = new float[cap];
         s.speed = new float[cap];
      }

      System.arraycopy(this.x, 0, s.x, 0, this.n);
      System.arraycopy(this.y, 0, s.y, 0, this.n);
      System.arraycopy(this.z, 0, s.z, 0, this.n);
      System.arraycopy(this.along, 0, s.along, 0, this.n);
      System.arraycopy(this.age, 0, s.age, 0, this.n);
      System.arraycopy(this.speed, 0, s.speed, 0, this.n);
      System.arraycopy(this.runStart, 0, s.runStart, 0, this.runs);
      System.arraycopy(this.runEnd, 0, s.runEnd, 0, this.runs);
      s.n = this.n;
      s.runs = this.runs;
      s.length = this.length;
   }

   public void restore(TrailShape.Saved s) {
      this.n = s.n;
      this.runs = s.runs;
      this.length = s.length;
      System.arraycopy(s.x, 0, this.x, 0, this.n);
      System.arraycopy(s.y, 0, this.y, 0, this.n);
      System.arraycopy(s.z, 0, this.z, 0, this.n);
      System.arraycopy(s.along, 0, this.along, 0, this.n);
      System.arraycopy(s.age, 0, this.age, 0, this.n);
      System.arraycopy(s.speed, 0, this.speed, 0, this.n);
      System.arraycopy(s.runStart, 0, this.runStart, 0, this.runs);
      System.arraycopy(s.runEnd, 0, this.runEnd, 0, this.runs);
   }

   public void build(
      PathRecorder r,
      double hx,
      double hy,
      double hz,
      boolean head,
      float headSpeed,
      double now,
      double since,
      double until,
      float maxAge,
      float maxAlong,
      float smoothing,
      int detail,
      double ex,
      double ey,
      double ez
   ) {
      int count = r.count();
      this.n = 0;
      this.runs = 0;
      this.length = 0.0F;
      if (until < Double.POSITIVE_INFINITY) {
         while (count > 0 && r.time(count - 1) > until) {
            count--;
         }

         head = false;
      }

      double headDist = count == 0 ? 0.0 : (count < r.count() ? r.distance(count - 1) : r.traveled() + (head ? r.sinceLast(hx, hy, hz) : 0.0));
      int i = 0;

      while (i < count && (r.time(i) < since || now - r.time(i) > maxAge || headDist - r.distance(i) > maxAlong)) {
         i++;
      }

      if (i != count) {
         boolean cutIn = false;
         double bx = 0.0;
         double by = 0.0;
         double bz = 0.0;
         float bAlong = 0.0F;
         float bAge = 0.0F;
         float bSpeed = 0.0F;
         if (i > 0 && i < count && (r.flags(i) & 64) == 0 && r.time(i - 1) >= since) {
            double alongA = headDist - r.distance(i - 1);
            double alongB = headDist - r.distance(i);
            double ageA = now - r.time(i - 1);
            double ageB = now - r.time(i);
            double t = 0.0;
            if (alongA > maxAlong && alongA - alongB > 1.0E-9) {
               t = Math.max(t, (alongA - maxAlong) / (alongA - alongB));
            }

            if (ageA > maxAge && ageA - ageB > 1.0E-9) {
               t = Math.max(t, (ageA - maxAge) / (ageA - ageB));
            }

            if ((t = Math.min(1.0, t)) < 0.999) {
               cutIn = true;
               bx = r.x(i - 1) + (r.x(i) - r.x(i - 1)) * t;
               by = r.y(i - 1) + (r.y(i) - r.y(i - 1)) * t;
               bz = r.z(i - 1) + (r.z(i) - r.z(i - 1)) * t;
               bAlong = (float)(alongA + (alongB - alongA) * t);
               bAge = (float)(ageA + (ageB - ageA) * t);
               bSpeed = r.speed(i - 1) + (r.speed(i) - r.speed(i - 1)) * (float)t;
            }
         }

         if (cutIn) {
            this.length = bAlong;
         } else if (i < count) {
            this.length = (float)(headDist - r.distance(i));
         }

         boolean first = true;

         while (i < count && this.runs < 32) {
            int end = i + 1;

            while (end < count && (r.flags(end) & 64) == 0) {
               end++;
            }

            boolean newest = end == count;
            int m = 0;
            if (first && cutIn) {
               this.cx[0] = bx;
               this.cy[0] = by;
               this.cz[0] = bz;
               this.cAlong[0] = bAlong;
               this.cAge[0] = bAge;
               this.cSpeed[0] = bSpeed;
               m = 1;
            }

            first = false;

            for (int k = i; k < end && m < 511; k++) {
               this.cx[m] = r.x(k);
               this.cy[m] = r.y(k);
               this.cz[m] = r.z(k);
               this.cAlong[m] = (float)(headDist - r.distance(k));
               this.cAge[m] = (float)Math.max(0.0, now - r.time(k));
               this.cSpeed[m] = r.speed(k);
               m++;
            }

            if (newest && head) {
               this.cx[m] = hx;
               this.cy[m] = hy;
               this.cz[m] = hz;
               this.cAlong[m] = 0.0F;
               this.cAge[m] = 0.0F;
               this.cSpeed[m] = headSpeed;
               m++;
            }

            if (m >= 2) {
               this.smooth(m, smoothing);
               int start = this.n;
               this.spline(m, detail, ex, ey, ez);
               if (this.n - start >= 2) {
                  this.runStart[this.runs] = start;
                  this.runEnd[this.runs] = this.n;
                  this.runs++;
               } else {
                  this.n = start;
               }
            }

            i = end;
         }
      }
   }

   private void smooth(int m, float smoothing) {
      if (!(smoothing <= 0.0F) && m >= 3) {
         float w = Math.min(1.0F, smoothing) * 0.5F;

         for (int pass = 0; pass < 2; pass++) {
            for (int k = 1; k < m - 1; k++) {
               this.sx[k] = this.cx[k] + w * ((this.cx[k - 1] + this.cx[k + 1]) * 0.5 - this.cx[k]);
               this.sy[k] = this.cy[k] + w * ((this.cy[k - 1] + this.cy[k + 1]) * 0.5 - this.cy[k]);
               this.sz[k] = this.cz[k] + w * ((this.cz[k - 1] + this.cz[k + 1]) * 0.5 - this.cz[k]);
            }

            for (int var6 = 1; var6 < m - 1; var6++) {
               this.cx[var6] = this.sx[var6];
               this.cy[var6] = this.sy[var6];
               this.cz[var6] = this.sz[var6];
            }
         }
      }
   }

   private void spline(int m, int detail, double ex, double ey, double ez) {
      int maxCuts = detail == 0 ? 2 : (detail == 1 ? 5 : 9);

      for (int k = 0; k < m - 1 && this.n < 1023; k++) {
         int k0 = Math.max(0, k - 1);
         int k3 = Math.min(m - 1, k + 2);
         double segLen = this.dist(k, k + 1);
         double cosTurn = Math.min(this.cosAngle(k0, k, k + 1), this.cosAngle(k, k + 1, k3));
         double camDist = Math.sqrt(sq(this.cx[k] - ex) + sq(this.cy[k] - ey) + sq(this.cz[k] - ez));
         int cuts = 1;

         while (cuts < maxCuts && cosTurn <= TURN_STEP_COS[cuts]) {
            cuts++;
         }

         if (camDist > 24.0) {
            cuts = Math.min(cuts, 2);
         }

         cuts = Math.max(1, Math.min(cuts, (int)Math.ceil(segLen / 0.03)));
         double t1 = this.knot(k0, k);
         double t2 = t1 + this.knot(k, k + 1);
         double t3 = t2 + this.knot(k + 1, k3);

         for (int c = 0; c < cuts && this.n < 1023; c++) {
            float t = (float)c / cuts;
            this.emit(k0, k, k + 1, k3, t, t1, t2, t3);
         }
      }

      int a0 = Math.max(0, m - 3);
      int a1 = m - 2;
      int a2 = m - 1;
      double t1 = this.knot(a0, a1);
      double t2 = t1 + this.knot(a1, a2);
      double t3 = t2 + this.knot(a2, a2);
      this.emit(a0, a1, a2, a2, 1.0F, t1, t2, t3);
   }

   private double dist(int a, int b) {
      return Math.sqrt(sq(this.cx[a] - this.cx[b]) + sq(this.cy[a] - this.cy[b]) + sq(this.cz[a] - this.cz[b]));
   }

   private static double sq(double v) {
      return v * v;
   }

   private double cosAngle(int a, int b, int c) {
      if (a != b && b != c) {
         double ux = this.cx[b] - this.cx[a];
         double uy = this.cy[b] - this.cy[a];
         double uz = this.cz[b] - this.cz[a];
         double vx = this.cx[c] - this.cx[b];
         double vy = this.cy[c] - this.cy[b];
         double vz = this.cz[c] - this.cz[b];
         double lu = Math.sqrt(ux * ux + uy * uy + uz * uz);
         double lv = Math.sqrt(vx * vx + vy * vy + vz * vz);
         return !(lu < 1.0E-6) && !(lv < 1.0E-6) ? Math.max(-1.0, Math.min(1.0, (ux * vx + uy * vy + uz * vz) / (lu * lv))) : 1.0;
      } else {
         return 1.0;
      }
   }

   private void emit(int p0, int p1, int p2, int p3, float t, double t1, double t2, double t3) {
      double t0 = 0.0;
      double tt = t1 + (t2 - t1) * t;
      this.x[this.n] = cr(this.cx[p0], this.cx[p1], this.cx[p2], this.cx[p3], t0, t1, t2, t3, tt);
      this.y[this.n] = cr(this.cy[p0], this.cy[p1], this.cy[p2], this.cy[p3], t0, t1, t2, t3, tt);
      this.z[this.n] = cr(this.cz[p0], this.cz[p1], this.cz[p2], this.cz[p3], t0, t1, t2, t3, tt);
      this.along[this.n] = this.cAlong[p1] + (this.cAlong[p2] - this.cAlong[p1]) * t;
      this.age[this.n] = this.cAge[p1] + (this.cAge[p2] - this.cAge[p1]) * t;
      this.speed[this.n] = this.cSpeed[p1] + (this.cSpeed[p2] - this.cSpeed[p1]) * t;
      this.n++;
   }

   private double knot(int a, int b) {
      return Math.max(1.0E-4, Math.sqrt(this.dist(a, b)));
   }

   private static double cr(double p0, double p1, double p2, double p3, double t0, double t1, double t2, double t3, double t) {
      double a1 = (t1 - t) / (t1 - t0) * p0 + (t - t0) / (t1 - t0) * p1;
      double a2 = (t2 - t) / (t2 - t1) * p1 + (t - t1) / (t2 - t1) * p2;
      double a3 = (t3 - t) / (t3 - t2) * p2 + (t - t2) / (t3 - t2) * p3;
      double b1 = (t2 - t) / (t2 - t0) * a1 + (t - t0) / (t2 - t0) * a2;
      double b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3;
      return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2;
   }

   static {
      for (int k = 0; k < TURN_STEP_COS.length; k++) {
         TURN_STEP_COS[k] = Math.cos(k * Math.PI / 24.0);
      }
   }

   public static final class Saved {
      double[] x = new double[0];
      double[] y = new double[0];
      double[] z = new double[0];
      float[] along = new float[0];
      float[] age = new float[0];
      float[] speed = new float[0];
      final int[] runStart = new int[32];
      final int[] runEnd = new int[32];
      int n;
      int runs;
      float length;
   }
}
