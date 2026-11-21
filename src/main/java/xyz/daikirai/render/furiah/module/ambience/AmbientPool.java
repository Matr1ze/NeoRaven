package xyz.daikirai.render.furiah.module.ambience;

final class AmbientPool {
   int capacity;
   int count;
   final int tail;
   double[] x;
   double[] y;
   double[] z;
   double[] px;
   double[] py;
   double[] pz;
   float[] vx;
   float[] vy;
   float[] vz;
   float[] kx;
   float[] ky;
   float[] kz;
   float[] tx;
   float[] ty;
   float[] tz;
   float[] age;
   float[] life;
   float[] fadeIn;
   float[] fadeOut;
   float[] size;
   float[] a;
   float[] b;
   float[] c;
   float[] d;
   float[] e;
   int[] seed;
   int[] flags;
   double[] ground;
   double[] hx;
   double[] hy;
   double[] hz;
   int[] head;
   int[] filled;

   AmbientPool(int capacity, int tail) {
      this.tail = tail;
      this.allocate(capacity);
   }

   private void allocate(int cap) {
      this.capacity = cap;
      this.x = grow(this.x, cap);
      this.y = grow(this.y, cap);
      this.z = grow(this.z, cap);
      this.px = grow(this.px, cap);
      this.py = grow(this.py, cap);
      this.pz = grow(this.pz, cap);
      this.ground = grow(this.ground, cap);
      this.vx = grow(this.vx, cap);
      this.vy = grow(this.vy, cap);
      this.vz = grow(this.vz, cap);
      this.kx = grow(this.kx, cap);
      this.ky = grow(this.ky, cap);
      this.kz = grow(this.kz, cap);
      this.tx = grow(this.tx, cap);
      this.ty = grow(this.ty, cap);
      this.tz = grow(this.tz, cap);
      this.age = grow(this.age, cap);
      this.life = grow(this.life, cap);
      this.fadeIn = grow(this.fadeIn, cap);
      this.fadeOut = grow(this.fadeOut, cap);
      this.size = grow(this.size, cap);
      this.a = grow(this.a, cap);
      this.b = grow(this.b, cap);
      this.c = grow(this.c, cap);
      this.d = grow(this.d, cap);
      this.e = grow(this.e, cap);
      this.seed = grow(this.seed, cap);
      this.flags = grow(this.flags, cap);
      this.head = grow(this.head, cap);
      this.filled = grow(this.filled, cap);
      if (this.tail > 0) {
         this.hx = grow(this.hx, cap * this.tail);
         this.hy = grow(this.hy, cap * this.tail);
         this.hz = grow(this.hz, cap * this.tail);
      }
   }

   void ensureCapacity(int cap) {
      if (cap > this.capacity) {
         this.allocate(Math.max(cap, this.capacity + this.capacity / 2));
      }
   }

   private static double[] grow(double[] old, int n) {
      double[] a = new double[n];
      if (old != null) {
         System.arraycopy(old, 0, a, 0, Math.min(old.length, n));
      }

      return a;
   }

   private static float[] grow(float[] old, int n) {
      float[] a = new float[n];
      if (old != null) {
         System.arraycopy(old, 0, a, 0, Math.min(old.length, n));
      }

      return a;
   }

   private static int[] grow(int[] old, int n) {
      int[] a = new int[n];
      if (old != null) {
         System.arraycopy(old, 0, a, 0, Math.min(old.length, n));
      }

      return a;
   }

   int add(double px0, double py0, double pz0, int seedValue) {
      if (this.count >= this.capacity) {
         return -1;
      } else {
         int i = this.count++;
         this.x[i] = this.px[i] = px0;
         this.y[i] = this.py[i] = py0;
         this.z[i] = this.pz[i] = pz0;
         this.ground[i] = Double.NaN;
         this.vz[i] = 0.0F;
         this.vy[i] = 0.0F;
         this.vx[i] = 0.0F;
         this.kz[i] = 0.0F;
         this.ky[i] = 0.0F;
         this.kx[i] = 0.0F;
         this.tx[i] = Float.NaN;
         this.age[i] = 0.0F;
         this.life[i] = 1.0E9F;
         this.fadeIn[i] = 0.0F;
         this.fadeOut[i] = 1.0F;
         this.size[i] = 1.0F;
         this.e[i] = 0.0F;
         this.d[i] = 0.0F;
         this.c[i] = 0.0F;
         this.b[i] = 0.0F;
         this.a[i] = 0.0F;
         this.seed[i] = seedValue;
         this.flags[i] = 0;
         this.head[i] = 0;
         this.filled[i] = 0;
         return i;
      }
   }

   void teleport(int i, double nx, double ny, double nz) {
      this.x[i] = this.px[i] = nx;
      this.y[i] = this.py[i] = ny;
      this.z[i] = this.pz[i] = nz;
      this.tx[i] = Float.NaN;
      this.filled[i] = 0;
      this.head[i] = 0;
   }

   void record(int i) {
      if (this.tail != 0) {
         int h;
         this.head[i] = h = (this.head[i] + 1) % this.tail;
         int o = i * this.tail + h;
         this.hx[o] = this.x[i];
         this.hy[o] = this.y[i];
         this.hz[o] = this.z[i];
         if (this.filled[i] < this.tail) {
            this.filled[i]++;
         }
      }
   }

   int historyIndex(int i, int k) {
      return i * this.tail + ((this.head[i] - k) % this.tail + this.tail) % this.tail;
   }

   void remove(int i) {
      int last;
      if (i != (last = --this.count)) {
         this.x[i] = this.x[last];
         this.y[i] = this.y[last];
         this.z[i] = this.z[last];
         this.px[i] = this.px[last];
         this.py[i] = this.py[last];
         this.pz[i] = this.pz[last];
         this.ground[i] = this.ground[last];
         this.vx[i] = this.vx[last];
         this.vy[i] = this.vy[last];
         this.vz[i] = this.vz[last];
         this.kx[i] = this.kx[last];
         this.ky[i] = this.ky[last];
         this.kz[i] = this.kz[last];
         this.tx[i] = this.tx[last];
         this.ty[i] = this.ty[last];
         this.tz[i] = this.tz[last];
         this.age[i] = this.age[last];
         this.life[i] = this.life[last];
         this.fadeIn[i] = this.fadeIn[last];
         this.fadeOut[i] = this.fadeOut[last];
         this.size[i] = this.size[last];
         this.a[i] = this.a[last];
         this.b[i] = this.b[last];
         this.c[i] = this.c[last];
         this.d[i] = this.d[last];
         this.e[i] = this.e[last];
         this.seed[i] = this.seed[last];
         this.flags[i] = this.flags[last];
         this.head[i] = this.head[last];
         this.filled[i] = this.filled[last];
         if (this.tail > 0) {
            System.arraycopy(this.hx, last * this.tail, this.hx, i * this.tail, this.tail);
            System.arraycopy(this.hy, last * this.tail, this.hy, i * this.tail, this.tail);
            System.arraycopy(this.hz, last * this.tail, this.hz, i * this.tail, this.tail);
         }
      }
   }

   void clear() {
      this.count = 0;
   }
}
