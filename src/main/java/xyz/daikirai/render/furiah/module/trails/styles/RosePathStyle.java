package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Vines;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.module.trails.GroundLayout;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public final class RosePathStyle extends TrailStyleBase {
   private static final double TWO_PI = Math.PI * 2;
   private static final double STEP = 0.125;
   private static final double OTHERS_STEP = 0.2;
   private static final double FAR_STEP = 0.3;
   private static final int MAX_STEMS = 3;
   private static final int MAX_LAID = 400;
   private static final double TWIST = 4.0;
   private static final double WIND = 1.8;
   private static final double SIZE_DEFAULT = 0.085;
   private static final double ROSE = 0.16;
   private static final double THORN = 0.1;
   private static final float WITHER_FROM = 0.55F;
   private static final float SHED_AT = 0.72F;
   private static final float SINK_FROM = 0.8F;
   private static final int MAX_PETALS = 160;
   private static final int NIGHT_RIM = -3549953;
   private static final float PETAL_GRAVITY = 1.6F;
   private static final float PETAL_FALL = 0.65F;
   private static final float PETAL_LIE = 1.1F;
   public final ThemeTint thornColor;
   public final ThemeTint roseColor;
   public final SliderSetting stems;
   public final SliderSetting roses;
   public final SliderSetting thornDensity;
   public final BooleanSetting wither;
   public final BooleanSetting petals;
   private final GroundCache ground = new GroundCache();
   private static final double[] WANDER = new double[400];
   private final Vines kit = new Vines(1200);
   private final Rng rng = new Rng();
   private int drawnPieces;
   private int drawnRoses;
   private int drawnThorns;
   private int livePetals;
   private boolean counting;

   public RosePathStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.ROSE_PATH,
         spec()
            .size(0.03, 0.15, 0.085, 0.005)
            .taper(25.0)
            .length(1.5)
            .anchor(TrailAnchor.FEET, true)
            .along(TrailStyleBase.ColorAlong.BY_AGE)
            .fade(TrailStyleBase.FadeCurve.LATE)
            .windy()
      );
      this.roseColor = this.signatureColor("roseColor", "Roses", "The roses along the vine, and their petals.", -5041618, -10614246);
      this.thornColor = this.signatureColor("thornColor", "Thorn Tips", "The thorns' pale tips.", -1452088, -2840);
      this.doesNotApply(new Setting[]{this.glow});
      this.stems = this.setting(new SliderSetting("rose_path.stems", "Stems", "How many bramble stems twist round each other.", 1.0, 3.0, 2.0, 1.0));
      this.roses = this.setting(new SliderSetting("rose_path.roses", "Roses", "How many roses bloom along the vine.", 0.0, 100.0, 40.0, 1.0).unit("%"));
      this.thornDensity = this.setting(
         new SliderSetting("rose_path.thornDensity", "Thorn Density", "How thick with thorns the stems are.", 0.0, 100.0, 60.0, 1.0).unit("%")
      );
      this.wither = this.setting(
         new BooleanSetting(
            "rose_path.wither", "Wither Toward the Tail", "Toward its tail the vine darkens, thins and sinks into the ground, and the roses wilt.", true
         )
      );
      this.petals = this.setting(
         new BooleanSetting("rose_path.petals", "Falling Petals", "The roses drop their petals near the tail; they tumble to the ground and fade.", true)
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.thornDensity);
   }

   @Override
   public int passes() {
      return FxPass.SOLID.bit();
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new RosePathStyle.Petals();
   }

   @Override
   public void update(TrailFrame f) {
      RosePathStyle.Petals d = (RosePathStyle.Petals)f.store(this.type);
      if (d != null && d.n > 0) {
         float[] w = this.breeze(f, f.eyeX, f.eyeY, f.eyeZ, 0.0F);
         d.wx = w[0];
         d.wy = w[1];
         d.wz = w[2];
      }

      super.update(f);
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      boolean bl = this.counting = p.self && !p.ghost && !f.preview;
      if (this.counting) {
         this.drawnThorns = 0;
         this.drawnRoses = 0;
         this.drawnPieces = 0;
      }

      TrailShape s = p.shape;
      if (p.shape != null && s.runs != 0) {
         boolean mine = p.self && !f.preview && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
         double head = traveledAtHead(p);
         double step = p.reduced ? 0.3 : (p.self ? 0.125 : 0.2);

         for (int k = 0; k < s.runs; k++) {
            if (GroundLayout.lay(this, p, f, k, head, this.size.getFloat(), step) >= 2 && GroundLayout.pieces > 0) {
               this.vine(p, f, head, mine, this.spawnPass(pass));
            }
         }
      }
   }

   private static double wander(double a) {
      return sin(a * 0.83 + 1.3) * 0.035 + sin(a * 2.1) * 0.012;
   }

   private static double sin(double x) {
      return MathHelper.sin((float)(x - (Math.PI * 2) * Math.floor(x / (Math.PI * 2))));
   }

   private static double cos(double x) {
      return MathHelper.cos((float)(x - (Math.PI * 2) * Math.floor(x / (Math.PI * 2))));
   }

   private static double tip(double along) {
      return 0.3 + 0.7 * Easing.smoothstep(0.0F, 0.6F, (float)along);
   }

   private void vine(PlayerTrail p, TrailFrame f, double head, boolean mine, boolean spawn) {
      int pieces = GroundLayout.pieces;
      int[] pa = GroundLayout.PIECE_FROM;
      int[] pb = GroundLayout.PIECE_TO;
      if (this.counting) {
         this.drawnPieces += pieces;
      }

      int count = Math.max(1, Math.min(3, this.stems.getInt()));
      boolean withers = this.wither.get();
      Camera cam = f.cam;

      for (int pc = 0; pc < pieces; pc++) {
         for (int q = pa[pc]; q < pb[pc]; q++) {
            WANDER[q] = wander(GroundLayout.DIST[q]);
         }
      }

      for (int i = 0; i < count; i++) {
         for (int pc = 0; pc < pieces; pc++) {
            for (int q = pa[pc]; q < pb[pc]; q++) {
               this.stemPoint(i, count, q, head, withers, mine, cam);
            }
         }
      }

      boolean coarse = !p.self || p.reduced;
      MeshBatch.begin(Material.CUSTOM, Finish.MATTE, this.matLight, 0.1F + 0.55F * this.night, -3549953, true, true);

      try {
         for (int i = 0; i < count; i++) {
            for (int pc = 0; pc < pieces; pc++) {
               int at = i * 400 + pa[pc];
               int n = pb[pc] - pa[pc];
               this.kit.groundFrames(at, n);
               this.kit.tube(at, n, false, cam, 1.0F, coarse);
            }
         }
      } finally {
         MeshBatch.end();
      }

      MeshBatch.begin(Material.CUSTOM, Finish.SATIN, this.matLight, 0.1F + 0.25F * this.night, -3549953, false, true);

      try {
         float dens = this.thornDensity.getFloat() / 100.0F;
         if (dens > 0.0F) {
            int tipColor = this.slotColor(this.thornColor, 0.5F, f.colorTime) | 0xFF000000;

            for (int i = 0; i < count; i++) {
               for (int pc = 0; pc < pieces; pc++) {
                  this.thorns(p, i, pa[pc], pb[pc], head, dens, tipColor, withers);
               }
            }
         }

         if (!p.reduced && this.roses.getFloat() > 0.0F) {
            boolean shed = spawn && this.petals.get() && !p.ghost;

            for (int pc = 0; pc < pieces; pc++) {
               this.roses(p, f, pa[pc], pb[pc], head, count, withers, mine, cam, shed);
            }
         }
      } finally {
         MeshBatch.end();
      }
   }

   private void stemPoint(int i, int count, int q, double head, boolean withers, boolean mine, Camera cam) {
      double a = GroundLayout.DIST[q];
      float u = GroundLayout.SHARE[q];
      float w = withers ? Easing.smoothstep(0.55F, 1.0F, u) : 0.0F;
      double r = GroundLayout.WIDTH[q] * 0.5 * (1.0 - 0.45 * w) * tip(head - a);
      double up;
      double across;
      if (count == 1) {
         across = sin(a * 1.7) * r * 0.8;
         up = 0.0;
      } else {
         double th = a * 4.0 + i * (Math.PI * 2) / count;
         across = cos(th) * r * 1.8;
         up = (1.0 + sin(th)) * 0.5 * r * 1.8;
      }

      double sink = withers ? Easing.smoothstep(0.8F, 1.0F, u) : 0.0;
      int idx = i * 400 + q;
      double var29;
      double x = GroundLayout.X[q] + GroundLayout.SIDE_X[q] * (var29 = across + WANDER[q]);
      double z = GroundLayout.Z[q] + GroundLayout.SIDE_Z[q] * var29;
      double y = GroundLayout.GROUND[q] + 0.01 + r + up - (2.2 * r + up) * sink;
      this.kit.px[idx] = x;
      this.kit.py[idx] = y;
      this.kit.pz[idx] = z;
      this.kit.rad[idx] = r;
      int bark = ColorUtil.shade(ColorUtil.lerp(GroundLayout.COLOR[q], -15856630, 0.65F * w), (float)(0.84 + 0.16 * sin(a * 9.0 + i * 2.3)));
      this.kit.col[idx] = ColorUtil.mulAlpha(bark | 0xFF000000, GroundLayout.ALPHA[q]);
      this.kit.keep[idx] = mine ? NearCamera.dark(cam, x, y, z, 1.0F) : 1.0F;
   }

   private void thorns(PlayerTrail p, int i, int a, int b, double head, float dens, int tipColor, boolean withers) {
      double rate = (1.2 + 5.0 * dens) * (p.reduced ? 0.4 : (p.self ? 1.0 : 0.7));
      double gap = 1.0 / rate;
      double thick = Math.sqrt(this.size.getFloat() / 0.085) * p.widthScale;
      long q0 = (long)Math.floor(GroundLayout.DIST[a] / gap);
      long q1 = (long)Math.ceil(GroundLayout.DIST[b - 1] / gap);
      int m = a;

      for (long q = q0; q <= q1; q++) {
         int qi = (int)q;
         double at = (q + 0.7 * (Rng.hash(qi, 31 + i) - 0.5)) * gap;
         if (!(at <= GroundLayout.DIST[a]) && !(at >= GroundLayout.DIST[b - 1]) && !(head - at < 0.2)) {
            while (m < b - 2 && GroundLayout.DIST[m + 1] < at) {
               m++;
            }

            double fr = (at - GroundLayout.DIST[m]) / Math.max(1.0E-9, GroundLayout.DIST[m + 1] - GroundLayout.DIST[m]);
            float u = GroundLayout.SHARE[m] + (GroundLayout.SHARE[m + 1] - GroundLayout.SHARE[m]) * (float)fr;
            float w = withers ? Easing.smoothstep(0.55F, 1.0F, u) : 0.0F;
            double around = ((qi & 1) == 0 ? 1 : -1) * (0.35 + 0.9 * Rng.hash(qi, 37 + i));
            double len = 0.1 * thick * (0.8 + 0.4 * Rng.hash(qi, 41 + i)) * (1.0 - 0.4 * w) * tip(head - at);
            this.kit.thorn(i * 400 + a, b - a, m - a + fr, around, len, tipColor, w, 1.0F);
            if (this.counting) {
               this.drawnThorns++;
            }
         }
      }
   }

   private void roses(PlayerTrail p, TrailFrame f, int a, int b, double head, int count, boolean withers, boolean mine, Camera cam, boolean shed) {
      float amount = this.roses.getFloat() / 100.0F;
      double gap = 1.1 / (0.1 + 0.9 * amount);
      double k = Math.sqrt(this.size.getFloat() / 0.085) * p.widthScale;
      long q0 = (long)Math.floor(GroundLayout.DIST[a] / gap);
      long q1 = (long)Math.ceil(GroundLayout.DIST[b - 1] / gap);
      int m = a;

      for (long q = q0; q <= q1; q++) {
         int qi = (int)q;
         double at = (q + 0.6 * (Rng.hash(qi, 101) - 0.5)) * gap;
         if (!(at <= GroundLayout.DIST[a]) && !(at >= GroundLayout.DIST[b - 1])) {
            while (m < b - 2 && GroundLayout.DIST[m + 1] < at) {
               m++;
            }

            double fr = (at - GroundLayout.DIST[m]) / Math.max(1.0E-9, GroundLayout.DIST[m + 1] - GroundLayout.DIST[m]);
            float t = (float)fr;
            float u = GroundLayout.SHARE[m] + (GroundLayout.SHARE[m + 1] - GroundLayout.SHARE[m]) * t;
            float age = GroundLayout.AGE[m] + (GroundLayout.AGE[m + 1] - GroundLayout.AGE[m]) * t;
            float alpha = GroundLayout.ALPHA[m] + (GroundLayout.ALPHA[m + 1] - GroundLayout.ALPHA[m]) * t;
            double r = (GroundLayout.WIDTH[m] + (GroundLayout.WIDTH[m + 1] - GroundLayout.WIDTH[m]) * t) * 0.5 * tip(head - at);
            float w = withers ? Easing.smoothstep(0.6F, 0.95F, u) : 0.0F;
            double open = Easing.outBack(Easing.clamp01((age - 0.05F) / 0.45F), 1.4F);
            double sz = 0.16 * k * (0.85 + 0.3 * Rng.hash(qi, 103)) * (0.3 + 0.7 * Math.min(1.0, open));
            double sx = GroundLayout.SIDE_X[m] + (GroundLayout.SIDE_X[m + 1] - GroundLayout.SIDE_X[m]) * t;
            double sz2 = GroundLayout.SIDE_Z[m] + (GroundLayout.SIDE_Z[m + 1] - GroundLayout.SIDE_Z[m]) * t;
            double side = (Rng.hash(qi, 107) - 0.5) * 0.09 + wander(at);
            double sink = withers ? Easing.smoothstep(0.8F, 1.0F, u) : 0.0;
            double x = GroundLayout.X[m] + (GroundLayout.X[m + 1] - GroundLayout.X[m]) * fr + sx * side;
            double z = GroundLayout.Z[m] + (GroundLayout.Z[m + 1] - GroundLayout.Z[m]) * fr + sz2 * side;
            double base = GroundLayout.GROUND[m] + (GroundLayout.GROUND[m + 1] - GroundLayout.GROUND[m]) * fr;
            double y = base + 0.01 + r * (count > 1 ? 2.35 : 1.4) - (sz * 0.8 + r * 2.2) * sink;
            double lean = (Rng.hash(qi, 109) - 0.5) * 0.9;
            double ax = sx * lean;
            double ay = 1.0;
            double az = sz2 * lean;
            double al = Math.sqrt(ax * ax + ay * ay + az * az);
            int col = ColorUtil.lerp(this.slotColor(this.roseColor, Rng.hash(qi, 111), f.colorTime) | 0xFF000000, -15856630, 0.7F * w) | 0xFF000000;
            float shown = mine ? NearCamera.dark(cam, x, y, z, alpha) : alpha;
            if (shown > 0.01F) {
               this.kit.rose(qi & 7, x, y, z, ax / al, ay / al, az / al, sz, Math.min(1.1, open) * (1.0 - 0.35 * w), col, shown);
               if (this.counting) {
                  this.drawnRoses++;
               }
            }

            if (shed && u >= 0.72F && at > p.style.vars[p.side]) {
               p.style.vars[p.side] = at;
               this.shed(p, f, x, y, z, sz, col, alpha, qi);
            }
         }
      }
   }

   private void shed(PlayerTrail p, TrailFrame f, double x, double y, double z, double roseSize, int col, float alpha, int qi) {
      RosePathStyle.Petals d = this.store(f);
      double wx = x + f.viewX;
      double wy = y + f.viewY;
      double wz = z + f.viewZ;
      double g = this.ground.surface(f.world, wx, wy, wz, 0.5, 3.0);
      float floor = (float)((Double.isNaN(g) ? wy - 3.0 : g) + 0.012);
      int petal = ColorUtil.shade(ColorUtil.lerp(col, -15856630, 0.25F), Math.max(0.2F, this.matLight)) | 0xFF000000;
      int count = 3 + (Rng.hash(qi, 113) > 0.6F ? 1 : 0);
      this.rng.seed(qi, p.entityId);

      for (int e = 0; e < count && !this.module.piecesFull(f); e++) {
         int i = d.add();
         float ang = this.rng.next() * (float) (Math.PI * 2);
         float out = 0.15F + this.rng.next() * 0.2F;
         d.x[i] = wx + Math.cos(ang) * roseSize * 0.3;
         d.y[i] = wy + roseSize * 0.25;
         d.z[i] = wz + Math.sin(ang) * roseSize * 0.3;
         d.vx[i] = (float)Math.cos(ang) * out;
         d.vy[i] = 0.25F + this.rng.next() * 0.3F;
         d.vz[i] = (float)Math.sin(ang) * out;
         d.groundY[i] = floor;
         d.age[i] = 0.0F;
         d.life[i] = 4.0F;
         d.landed[i] = -1.0F;
         d.size[i] = (float)(roseSize * (0.45 + 0.2 * this.rng.next()));
         d.alpha[i] = alpha;
         d.spin[i] = this.rng.next() * (float) (Math.PI * 2);
         d.spinRate[i] = this.rng.signed() * 2.5F;
         d.phase[i] = this.rng.next() * (float) (Math.PI * 2);
         d.rest[i] = 0.06F + this.rng.next() * 0.25F;
         d.color[i] = petal;
      }
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      RosePathStyle.Petals d = (RosePathStyle.Petals)f.store(this.type);
      int n = this.livePetals = d == null ? 0 : d.n;
      if (d != null && d.n != 0 && pass == FxPass.SOLID) {
         MeshBatch.begin(Material.CUSTOM, Finish.SATIN, 1.0F, 0.1F, -3549953, false, true);

         try {
            for (int i = 0; i < d.n; i++) {
               double x = d.x[i];
               double y = d.y[i];
               double z = d.z[i];
               float age = d.age[i];
               float a = d.alpha[i]
                  * Easing.clamp01(age / 0.12F)
                  * (1.0F - Easing.smoothstep(d.life[i] - 0.7F, d.life[i], age))
                  * f.nearFade(x, y, z)
                  * f.fog(x, y, z);
               if (!(a <= 0.01F)) {
                  boolean down = d.landed[i] >= 0.0F;
                  float flight = down ? d.landed[i] : age;
                  double spin = d.spin[i] + d.spinRate[i] * flight;
                  double tumble = down ? d.rest[i] : 0.9 * Math.sin(age * 5.0 + d.phase[i]);
                  this.kit.tumblingPetal(x - f.viewX, y - f.viewY, z - f.viewZ, spin, tumble, d.size[i], d.color[i], a);
               }
            }
         } finally {
            MeshBatch.end();
         }
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%d stem(s), roses %.0f%%, thorns %.0f%%, wither %s, petals %s; drawn: %d piece(s), %d rose(s), %d thorn(s); %d petal(s) falling or lying",
         this.stems.getInt(),
         this.roses.getFloat(),
         this.thornDensity.getFloat(),
         this.wither.get() ? "on" : "off",
         this.petals.get() ? "on" : "off",
         this.drawnPieces,
         this.drawnRoses,
         this.drawnThorns,
         this.livePetals
      );
   }

   private static final class Petals implements TrailStyleBase.Store {
      final double[] x = new double[160];
      final double[] y = new double[160];
      final double[] z = new double[160];
      final float[] vx = new float[160];
      final float[] vy = new float[160];
      final float[] vz = new float[160];
      final float[] groundY = new float[160];
      final float[] age = new float[160];
      final float[] life = new float[160];
      final float[] landed = new float[160];
      final float[] size = new float[160];
      final float[] alpha = new float[160];
      final float[] spin = new float[160];
      final float[] spinRate = new float[160];
      final float[] phase = new float[160];
      final float[] rest = new float[160];
      final int[] color = new int[160];
      int n;
      int oldest;
      float wx;
      float wy;
      float wz;

      private Petals() {
      }

      int add() {
         if (this.n < 160) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 160;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         if (!(dt <= 0.0F)) {
            float drag = (float)Math.exp(-1.6 * dt);
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               this.age[i] = this.age[i] + dt;
               if (!(this.age[i] >= this.life[i])) {
                  if (this.landed[i] < 0.0F) {
                     this.vx[i] = this.vx[i] * drag;
                     this.vz[i] = this.vz[i] * drag;
                     this.vy[i] = Math.max(-0.65F, this.vy[i] - 1.6F * dt);
                     float sway = (float)Math.sin(this.age[i] * 4.2F + this.phase[i]) * 0.18F;
                     this.x[i] = this.x[i] + (this.vx[i] + this.wx + sway * (float)Math.cos(this.phase[i])) * dt;
                     this.y[i] = this.y[i] + (this.vy[i] + this.wy) * dt;
                     this.z[i] = this.z[i] + (this.vz[i] + this.wz + sway * (float)Math.sin(this.phase[i])) * dt;
                     if (this.y[i] <= this.groundY[i]) {
                        this.y[i] = this.groundY[i];
                        this.landed[i] = this.age[i];
                        this.life[i] = this.age[i] + 1.1F;
                     }
                  }

                  if (w != i) {
                     this.copy(i, w);
                  }

                  w++;
               }
            }

            this.n = w;
            this.oldest = 0;
         }
      }

      private void copy(int a, int b) {
         this.x[b] = this.x[a];
         this.y[b] = this.y[a];
         this.z[b] = this.z[a];
         this.vx[b] = this.vx[a];
         this.vy[b] = this.vy[a];
         this.vz[b] = this.vz[a];
         this.groundY[b] = this.groundY[a];
         this.age[b] = this.age[a];
         this.life[b] = this.life[a];
         this.landed[b] = this.landed[a];
         this.size[b] = this.size[a];
         this.alpha[b] = this.alpha[a];
         this.spin[b] = this.spin[a];
         this.spinRate[b] = this.spinRate[a];
         this.phase[b] = this.phase[a];
         this.rest[b] = this.rest[a];
         this.color[b] = this.color[a];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
         this.oldest = 0;
      }
   }
}
