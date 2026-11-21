package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.kit.Glitch;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Glass;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class MosaicStyle extends HitStyleBase {
   private static final int RUBY = 0;
   private static final int SAPPHIRE = 1;
   private static final int EMERALD = 2;
   private static final int GOLD = 3;
   private static final float ASSEMBLE = 0.13333334F;
   private static final float GLOWING = 0.33333334F;
   private static final float FIRST_OUT = 0.33333334F;
   private static final float LAST_OUT = 0.56F;
   private static final float FADE = 0.8F;
   private static final float SECONDS = 0.75F;
   private static final int STONE = -7436419;
   private static final int FLECKS = 8;
   private static final int RING_SEGS = 48;
   private static final double[] RIM_R = new double[]{0.875, 0.905, 0.97, 1.0};
   private static final double[] RIM_Z = new double[]{0.0, 0.03, 0.03, 0.0};
   private static final double[] RIM_LEAN = new double[]{-0.7, 0.0, 0.0, 0.7};
   private static final double LEAD = 0.045;
   private static final double FALL_ALONG = 1.1;
   private static final double FALL_OUT = 0.55;
   private static final double POP = 0.8;
   private static final double GRAVITY = 30.0;
   private static final double BOUNCE = 0.3;
   private static final float CRIT_SIZE = 1.35F;
   public final ThemeTint glass2;
   public final ThemeTint glass3;
   public final ThemeTint lead;
   public final ModeSetting<Glass.GlassColors> glassColors;
   public final ModeSetting<MosaicStyle.Pattern> pattern;
   public final SliderSetting size;
   public final SliderSetting cells;
   public final BooleanSetting flecks;
   public final SliderSetting opacity;
   private static final MosaicStyle.Medallion[] MEDALLIONS = new MosaicStyle.Medallion[102];
   private double mx;
   private double my;
   private double mz;
   private double radius;
   private double ground;
   private Basis b;
   private final int[] palette = new int[4];
   private final double[] tu = new double[64];
   private final double[] tv = new double[64];
   private final Glass.Pane pane = new Glass.Pane();
   private final Glass.Pane shard = new Glass.Pane();
   private double shardX;
   private double shardY;
   private double shardZ;
   private float shardGlint;
   private boolean shardBounced;

   public MosaicStyle() {
      super(
         HitStyle.MOSAIC,
         0.75F,
         new ThemeTint()
      );
      this.glass2 = ThemeTint.detail(-4184513);
      this.glass3 = ThemeTint.detail(-868018);
      this.lead = ThemeTint.detail(-15066594);
      this.glassColors = this.setting(
         new ModeSetting<>(
            "mosaic.glassColors",
            "Glass Colors",
            "Cathedral: ruby, sapphire, emerald and gold. Tinted: the three glass colors. The light takes the glass colors.",
            Glass.GlassColors.class,
            Glass.GlassColors.CATHEDRAL
         )
      );
      this.pattern = this.setting(
         new ModeSetting<>("mosaic.pattern", "Pattern", "The medallion's design.", MosaicStyle.Pattern.class, MosaicStyle.Pattern.ROSE)
            .style(ModeSetting.Style.CHIPS)
      );
      this.size = this.look(this.setting(new SliderSetting("mosaic.size", "Size", "How wide the medallion is.", 0.3, 1.2, 0.6, 0.05).unit(" blocks")));
      this.cells = this.look(this.setting(new SliderSetting("mosaic.cells", "Cells", "About how many glass cells the medallion has.", 12.0, 48.0, 24.0, 1.0)));
      this.flecks = this.setting(new BooleanSetting("mosaic.flecks", "Light Flecks", "Small flecks of colored light land on the target.", true));
      this.opacity = this.setting(
         new SliderSetting("mosaic.opacity", "Glass Opacity", "How much of what is behind the glass it hides.", 40.0, 100.0, 75.0, 5.0).unit("%")
      );
   }

   private static int petals(MosaicStyle.Pattern p, int wanted) {
      double a = (wanted - 1) / 3.0;
      return p == MosaicStyle.Pattern.QUATREFOIL ? Math.max(4, Math.min(16, (int)Math.round(a / 4.0) * 4)) : Math.max(4, Math.min(16, (int)Math.round(a)));
   }

   private static MosaicStyle.Medallion medallion(MosaicStyle.Pattern p, int wanted, boolean crit) {
      int a = petals(p, wanted);
      int key = (p.ordinal() * 17 + a) * 2 + (crit ? 1 : 0);
      MosaicStyle.Medallion m = MEDALLIONS[key];
      if (m == null) {
         MEDALLIONS[key] = m = build(p, a, crit);
      }

      return m;
   }

   private static MosaicStyle.Medallion build(MosaicStyle.Pattern p, int a, boolean crit) {
      Glass.Builder b = new Glass.Builder();
      double outer = crit ? 0.655 : 0.875;
      double w = 360.0 / a;
      double start = -w / 2.0;
      int heart;
      int[] middle;
      int[] inner;
      double[] edge;
      if (p == MosaicStyle.Pattern.QUATREFOIL) {
         edge = Glass.pointed(0.3, 0.26, 90.0, -45.0, 0.5);
         start = -45.0;
         int per = a / 4;
         inner = new int[per * 2];

         for (int i = 0; i < inner.length; i++) {
            inner[i] = i < per ? 1 : 0;
         }

         middle = new int[]{3, 2};
         heart = 3;
      } else if (p == MosaicStyle.Pattern.STAR) {
         edge = Glass.pointed(0.2, 0.42, w, start, 1.0);
         inner = new int[]{3};
         middle = new int[]{1, 0};
         heart = 0;
      } else {
         edge = Glass.pointed(0.36, 0.14, w, start, 1.4);
         inner = new int[]{0, 1};
         middle = new int[]{2, 3};
         heart = 3;
      }

      int segs = 2 * Math.max(2, 24 / a);
      Glass.disc(b, 0.0, 0.0, 0.1, heart, false);
      Glass.ring(b, a, start, Glass.arc(0.1), edge, inner, false, segs);
      Glass.ring(b, a * 2, start, edge, Glass.arc(outer), middle, false, segs / 2);
      if (crit) {
         Glass.ring(b, a * 2, start + w / 4.0, Glass.arc(0.705), Glass.arc(0.875), new int[]{1, 0, 1, 3}, false, 2);
      }

      Glass.Cells c = b.build(false);
      final double[] key = new double[c.n];
      Integer[] rank = new Integer[c.n];

      for (int i = 0; i < c.n; i++) {
         int ring = i == 0 ? 0 : (i <= a ? 1 : (i <= 3 * a ? 2 : 3));
         double clock = ((Math.PI / 2) - Math.atan2(c.cv[i], c.cu[i])) / (Math.PI * 2);
         key[i] = -ring * 10 + (clock - Math.floor(clock));
         rank[i] = i;
      }

      Arrays.sort(rank, new Comparator<Integer>() {
         public int compare(Integer x, Integer y) {
            return Double.compare(key[x], key[y]);
         }
      });
      float[] order = new float[c.n];

      for (int r = 0; r < c.n; r++) {
         order[rank[r]] = c.n > 1 ? (float)r / (c.n - 1) : 0.0F;
      }

      return new MosaicStyle.Medallion(c, order, a);
   }

   @Override
   public int passes(HitFx fx) {
      int base = FxPass.SOLID.bit() | FxPass.TRANSLUCENT.bit();
      return fx.lod ? base : base | FxPass.GLOW.bit();
   }

   private void setUp(HitFx fx, FxFrame f) {
      this.b = fx.basis;
      this.radius = 0.5 * this.size.getFloat() * scale(fx) * (fx.crit ? 1.35F : 1.0F);
      this.mx = f.rx(fx.ix()) - this.b.fx * 0.08;
      this.my = f.ry(fx.iy()) - this.b.fy * 0.08;
      this.mz = f.rz(fx.iz()) - this.b.fz * 0.08;
      this.ground = f.ry(fx.hit.groundY);
      this.pane(this.pane, this.mx, this.my, this.mz, this.b.rx, this.b.ry, this.b.rz, this.b.ux, this.b.uy, this.b.uz);
      if (this.glassColors.get() == Glass.GlassColors.TINTED) {
         Glass.tinted(this.color.getColor(0.5F), this.glass2.getColor(0.5F), this.glass3.getColor(0.5F), this.palette);
      } else {
         System.arraycopy(Glass.CATHEDRAL_SET, 0, this.palette, 0, 4);
      }
   }

   private void pane(Glass.Pane p, double x, double y, double z, double rx, double ry, double rz, double ux, double uy, double uz) {
      p.ox = x;
      p.oy = y;
      p.oz = z;
      p.rx = rx;
      p.ry = ry;
      p.rz = rz;
      p.ux = ux;
      p.uy = uy;
      p.uz = uz;
      p.nx = ry * uz - rz * uy;
      p.ny = rz * ux - rx * uz;
      p.nz = rx * uy - ry * ux;
      p.scale = this.radius;
      p.offX = 0.0;
      p.offY = 0.0;
      p.offZ = 0.0;
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      float p = fx.progress();
      float alpha = fx.alpha() * (1.0F - Easing.smoothstep(0.8F, 1.0F, p));
      if (!(alpha <= 0.003F)) {
         MosaicStyle.Medallion m = medallion(this.pattern.get(), this.cells.getInt(), fx.crit);
         this.setUp(fx, f);
         if (pass == FxPass.SOLID) {
            this.rim(fx, p, alpha);
         } else if (pass == FxPass.TRANSLUCENT) {
            this.glass(fx, m, p, alpha);
         } else if (pass == FxPass.GLOW) {
            this.light(fx, f, m, p, alpha);
         }
      }
   }

   private static float grown(Glass.Cells c, int i, float p) {
      float at = 0.093333334F * (float)Math.min(1.0, c.rad[i] / 0.875);
      return Easing.clamp01((p - at) / 0.040000003F);
   }

   private static float outAt(MosaicStyle.Medallion m, int i, int seed) {
      return 0.33333334F + 0.22666666F * m.order[i] + 0.012F * (float)hash(seed + i * 41, 9);
   }

   private static float glowAmount(float p) {
      return Easing.smoothstep(0.05333334F, 0.13333334F, p) * (1.0F - 0.6F * Easing.smoothstep(0.33333334F, 0.56F, p));
   }

   private static float tracery(float p) {
      return 1.0F - Easing.smoothstep(0.43333334F, 0.8F, p);
   }

   private int place(Glass.Cells c, int i, double k) {
      int from = c.start[i];
      int n = Math.min(c.count[i], this.tu.length);

      for (int q = 0; q < n; q++) {
         this.tu[q] = c.cu[i] + (c.u[from + q] - c.cu[i]) * k;
         this.tv[q] = c.cv[i] + (c.v[from + q] - c.cv[i]) * k;
      }

      return n;
   }

   private void glass(HitFx fx, MosaicStyle.Medallion m, float p, float alpha) {
      Glass.Cells c = m.cells;
      float op = this.opacity.getFloat() / 100.0F;
      float light = Math.max(fx.light, 0.6F);
      float glow = glowAmount(p);
      float tr = tracery(p);
      int leadCol = ColorUtil.shade(this.lead.getColor(0.5F), fx.light);
      RenderState.decal(true);

      try {
         for (int i = 0; i < c.n; i++) {
            float out = outAt(m, i, fx.seed);
            if (!(p < out) && this.fall(fx, c, i, (p - out) * 0.75F)) {
               int n = Glass.shard(c, i, 0.0, 1.0, 0.0, 0.0, this.tu, this.tv);
               float a = alpha * op * fx.keep.cover(this.shardX, this.shardY, this.shardZ);
               int base = ColorUtil.shade(this.palette[c.color[i]], light * (0.9F + 0.2F * (float)hash(i, 13)));
               int mid = alpha(ColorUtil.lerp(base, -1, 0.2F + 0.6F * this.shardGlint), a);
               int edge = alpha(ColorUtil.lerp(ColorUtil.shade(base, 0.78F), -1, 0.4F * this.shardGlint), a);
               Glass.glass(this.shard, this.tu, this.tv, n, 0.0, 0.0, mid, edge);
               Glass.lead(this.shard, this.tu, this.tv, n, 0.027, alpha(leadCol, alpha * fx.keep.cover(this.shardX, this.shardY, this.shardZ)));
            }
         }

         for (int var30 = 0; var30 < c.n; var30++) {
            float g = grown(c, var30, p);
            if (!(g <= 0.0F) && !(p >= outAt(m, var30, fx.seed))) {
               int n = this.place(c, var30, Easing.outBack(g, 1.6F));
               float a = alpha
                  * op
                  * Easing.clamp01(g * 2.0F)
                  * fx.keep.cover(this.pane.x(c.cu[var30], c.cv[var30]), this.pane.y(c.cu[var30], c.cv[var30]), this.pane.z(c.cu[var30], c.cv[var30]));
               int base = ColorUtil.shade(this.palette[c.color[var30]], light * (0.9F + 0.2F * (float)hash(var30, 13)));
               int mid = alpha(ColorUtil.lerp(base, -1, 0.18F + 0.22F * glow), a);
               int edge = alpha(ColorUtil.shade(base, 0.78F), a);
               Glass.glass(this.pane, this.tu, this.tv, n, c.cu[var30], c.cv[var30], mid, edge);
            }
         }

         for (int var31 = 0; var31 < c.n; var31++) {
            float g = grown(c, var31, p);
            if (!(g <= 0.0F)) {
               int n = this.place(c, var31, Easing.outBack(g, 1.6F));
               float a = alpha
                  * tr
                  * Easing.clamp01(g * 2.0F)
                  * fx.keep.cover(this.pane.x(c.cu[var31], c.cv[var31]), this.pane.y(c.cu[var31], c.cv[var31]), this.pane.z(c.cu[var31], c.cv[var31]));
               if (a > 0.003F) {
                  Glass.lead(this.pane, this.tu, this.tv, n, 0.045, alpha(leadCol, a));
               }
            }
         }

         if (fx.crit) {
            float a2 = alpha * tr * Easing.clamp01((p - 0.080000006F) / 0.05333334F) * fx.keep.cover(this.mx, this.my, this.mz);
            Glass.ringLead(this.pane, 0.655, 0.705, alpha(leadCol, a2));
         }
      } finally {
         RenderState.decal(false);
      }
   }

   private boolean fall(HitFx fx, Glass.Cells c, int i, double t) {
      int id = fx.seed + i * 41;
      double s = this.radius / 0.3;
      double ou = c.cu[i];
      double ov = c.cv[i];
      double ol = Math.sqrt(ou * ou + ov * ov);
      if (ol > 1.0E-6) {
         ou /= ol;
         ov /= ol;
      } else {
         ou = 0.0;
         ov = 0.0;
      }

      double along = 1.1 * (0.75 + 0.5 * hash(id, 1)) * s;
      double out = 0.55 * (0.5 + hash(id, 2)) * s;
      double vx = this.b.fx * along + (this.b.rx * ou + this.b.ux * ov) * out;
      double vy = this.b.fy * along + (this.b.ry * ou + this.b.uy * ov) * out + 0.8 * (0.6 + 0.8 * hash(id, 3)) * s;
      double vz = this.b.fz * along + (this.b.rz * ou + this.b.uz * ov) * out;
      double g = 30.0 * s;
      double x0 = this.pane.x(c.cu[i], c.cv[i]);
      double y0 = this.pane.y(c.cu[i], c.cv[i]);
      double z0 = this.pane.z(c.cu[i], c.cv[i]);
      double floor = this.ground + 0.01;
      double h0 = Math.max(0.0, y0 - floor);
      double t1 = (vy + Math.sqrt(vy * vy + 2.0 * g * h0)) / g;
      double rate = 8.0 + 8.0 * hash(id, 4);
      double spin;
      double y;
      double travel;
      if (t <= t1) {
         travel = t;
         y = y0 + vy * t - 0.5 * g * t * t;
         spin = rate * t;
         this.shardBounced = false;
      } else {
         double vb = -(vy - g * t1) * 0.3;
         double t2 = t1 + 2.0 * vb / g;
         double tt = Math.min(t, t2) - t1;
         travel = t1 + 0.5 * tt + 0.15 * Math.max(0.0, Math.min(t - t2, 0.1));
         y = floor + (t <= t2 ? vb * tt - 0.5 * g * tt * tt : 0.0);
         spin = rate * (t1 + 0.4 * (t - t1));
         this.shardBounced = true;
      }

      this.shardX = x0 + vx * travel;
      this.shardY = y;
      this.shardZ = z0 + vz * travel;
      double kx = hash(id, 5) - 0.5;
      double ky = hash(id, 6) - 0.5;
      double kz = hash(id, 7) - 0.5;
      double kl = Math.sqrt(kx * kx + ky * ky + kz * kz) + 1.0E-9;
      double cs = Math.cos(spin);
      double sn = Math.sin(spin);
      double var92;
      double var94;
      double var96;
      double d = (var92 = kx / kl) * this.b.rx + (var94 = ky / kl) * this.b.ry + (var96 = kz / kl) * this.b.rz;
      double rx = this.b.rx * cs + (var94 * this.b.rz - var96 * this.b.ry) * sn + var92 * d * (1.0 - cs);
      double ry = this.b.ry * cs + (var96 * this.b.rx - var92 * this.b.rz) * sn + var94 * d * (1.0 - cs);
      double rz = this.b.rz * cs + (var92 * this.b.ry - var94 * this.b.rx) * sn + var96 * d * (1.0 - cs);
      d = var92 * this.b.ux + var94 * this.b.uy + var96 * this.b.uz;
      double ux = this.b.ux * cs + (var94 * this.b.uz - var96 * this.b.uy) * sn + var92 * d * (1.0 - cs);
      double uy = this.b.uy * cs + (var96 * this.b.ux - var92 * this.b.uz) * sn + var94 * d * (1.0 - cs);
      double uz = this.b.uz * cs + (var92 * this.b.uy - var94 * this.b.ux) * sn + var96 * d * (1.0 - cs);
      this.pane(this.shard, this.shardX, this.shardY, this.shardZ, rx, ry, rz, ux, uy, uz);
      double lx = -this.b.fx * 0.5;
      double ly = 1.0;
      double lz = -this.b.fz * 0.5;
      double hx = lx - this.b.fx;
      double hy = ly - this.b.fy;
      double hz = lz - this.b.fz;
      double hl = Math.sqrt(hx * hx + hy * hy + hz * hz) + 1.0E-9;
      double facing = Math.abs((this.shard.nx * hx + this.shard.ny * hy + this.shard.nz * hz) / hl);
      this.shardGlint = (float)Math.pow(facing, 24.0);
      return true;
   }

   private void rim(HitFx fx, float p, float alpha) {
      float in = Easing.clamp01((p - 0.06666667F) / 0.080000006F);
      float a = alpha * in * tracery(p);
      if (!(a <= 0.003F)) {
         MeshBatch.begin(Material.CUSTOM, Finish.MATTE, fx.light, 0.06F, -1, false, true);
         double grow = 0.92 + 0.08 * Easing.outCubic(in);
         double ox = -this.b.fx;
         double oy = -this.b.fy;
         double oz = -this.b.fz;
         int base = MeshBatch.stage(196);

         for (int s = 0; s <= 48; s++) {
            double cs = Glass.RING_COS[s];
            double sn = Glass.RING_SIN[s];
            double dx = this.b.rx * cs + this.b.ux * sn;
            double dy = this.b.ry * cs + this.b.uy * sn;
            double dz = this.b.rz * cs + this.b.uz * sn;

            for (int r = 0; r < 4; r++) {
               double rr = RIM_R[r] * grow * this.radius;
               double lift = RIM_Z[r] * this.radius;
               double lean = RIM_LEAN[r];
               double nx = ox * (1.0 - Math.abs(lean)) + dx * lean;
               double ny = oy * (1.0 - Math.abs(lean)) + dy * lean;
               double nz = oz * (1.0 - Math.abs(lean)) + dz * lean;
               double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
               double x = this.mx + dx * rr + ox * lift;
               double y = this.my + dy * rr + oy * lift;
               double z = this.mz + dz * rr + oz * lift;
               MeshBatch.vertex(base + s * 4 + r, x, y, z, nx / l, ny / l, nz / l, alpha(-7436419, a * fx.keep.cover(x, y, z)), 0.0F);
            }
         }

         for (int var47 = 0; var47 < 48; var47++) {
            for (int r = 0; r < 3; r++) {
               int a0 = base + var47 * 4 + r;
               int b0 = base + (var47 + 1) * 4 + r;
               MeshBatch.quad(a0, b0, b0 + 1, a0 + 1);
            }
         }

         MeshBatch.end();
      }
   }

   private void light(HitFx fx, FxFrame f, MosaicStyle.Medallion m, float p, float alpha) {
      Glass.Cells c = m.cells;
      boolean calm = Glitch.reduceFlashing();
      int warm = ColorUtil.lerp(this.palette[3], -1, 0.45F);
      if (p < 0.20000002F) {
         float k = Easing.smoothstep(0.0F, 0.033333335F, p) * (1.0F - Easing.smoothstep(0.05333334F, 0.20000002F, p));
         Vfx.sprite(Textures.GLOW, this.mx, this.my, this.mz, this.radius * 3.2, 0.0F, alpha(warm, (calm ? 0.18F : 0.5F) * k * alpha));
      }

      float glow;
      if ((glow = glowAmount(p)) > 0.003F) {
         for (int i = 0; i < c.n; i++) {
            float g = grown(c, i, p);
            if (!(g <= 0.0F) && !(p >= outAt(m, i, fx.seed))) {
               int n = this.place(c, i, Easing.outBack(g, 1.6F));
               int col = this.palette[c.color[i]];
               Glass.glass(this.pane, this.tu, this.tv, n, c.cu[i], c.cv[i], alpha(col, 0.3F * glow * alpha), alpha(col, 0.06F * glow * alpha));
            }
         }

         double back = this.radius * 0.05;
         Vfx.sprite(
            Textures.GLOW,
            this.mx + this.b.fx * back,
            this.my + this.b.fy * back,
            this.mz + this.b.fz * back,
            this.radius * 2.4,
            0.0F,
            alpha(warm, 0.22F * glow * alpha)
         );
      }

      for (int ix = 0; ix < c.n; ix++) {
         float out = outAt(m, ix, fx.seed);
         if (!(p < out) && this.fall(fx, c, ix, (p - out) * 0.75F) && !(this.shardGlint < 0.05F)) {
            float k = this.shardGlint * (calm ? 0.35F : 0.75F) * alpha;
            Vfx.sprite(
               Textures.SPARK,
               this.shardX,
               this.shardY,
               this.shardZ,
               this.radius * 0.3,
               (float)(p * 6.0 + ix),
               core(ColorUtil.lerp(this.palette[c.color[ix]], -1, 0.6F), k)
            );
         }
      }

      if (this.flecks.get()) {
         this.flecks(fx, f, c, p, alpha);
      }

      if (fx.crit) {
         this.beam(fx, p, alpha, warm);
      }
   }

   private void flecks(HitFx fx, FxFrame f, Glass.Cells c, float p, float alpha) {
      int n = fx.crit ? 12 : 8;
      double dx = fx.cx() - fx.ix();
      double dy = fx.cy() - fx.iy();
      double dz = fx.cz() - fx.iz();
      double bodyR = dx * this.b.rx + dy * this.b.ry + dz * this.b.rz;
      double bodyU = dx * this.b.ux + dy * this.b.uy + dz * this.b.uz;
      double hw = fx.hit.width * 0.45;
      double hh = fx.hit.height * 0.42;
      double ix = f.rx(fx.ix()) - this.b.fx * 0.03;
      double iy = f.ry(fx.iy()) - this.b.fy * 0.03;
      double iz = f.rz(fx.iz()) - this.b.fz * 0.03;
      double size = 0.05 * this.radius / 0.3;
      float fade = 1.0F - Easing.smoothstep(0.78F, 0.96F, p);

      for (int k = 0; k < n; k++) {
         int id = fx.seed + k * 59;
         float start = 0.13F + 0.14F * (float)hash(id, 1);
         if (!(p < start)) {
            float q = Easing.clamp01((p - start) / 0.07F);
            float e = Easing.outCubic(q);
            int cell = 1 + (int)(hash(id, 2) * (c.n - 1));
            double sx = this.pane.x(c.cu[cell], c.cv[cell]);
            double sy = this.pane.y(c.cu[cell], c.cv[cell]);
            double sz = this.pane.z(c.cu[cell], c.cv[cell]);
            double lr = bodyR + (hash(id, 3) * 2.0 - 1.0) * hw;
            double lu = bodyU + (hash(id, 4) * 2.0 - 1.0) * hh;
            double tx = ix + this.b.rx * lr + this.b.ux * lu;
            double ty = iy + this.b.ry * lr + this.b.uy * lu;
            double tz = iz + this.b.rz * lr + this.b.uz * lu;
            double x = sx + (tx - sx) * e;
            double y = sy + (ty - sy) * e;
            double z = sz + (tz - sz) * e;
            float a = alpha * (0.4F + 0.6F * q) * fade * (0.75F + 0.25F * (float)Math.sin(p * 40.0 + k));
            int col = this.palette[c.color[cell]];
            Vfx.sprite(Textures.GLOW, x, y, z, size * 2.6, 0.0F, alpha(col, 0.55F * a));
            Vfx.sprite(Textures.SPARK, x, y, z, size, 0.0F, core(col, 0.45F * a));
         }
      }
   }

   private void beam(HitFx fx, float p, float alpha, int warm) {
      float k = Easing.smoothstep(0.13333334F, 0.33333334F, p) * (1.0F - Easing.smoothstep(0.43333334F, 0.61F, p)) * alpha;
      if (!(k <= 0.003F)) {
         double s = this.radius / 0.3;
         double len = fx.hit.width + 1.6 * s;
         Glass.shaft(
            Vfx.camera(),
            this.mx,
            this.my,
            this.mz,
            this.mx + this.b.fx * len,
            this.my + this.b.fy * len,
            this.mz + this.b.fz * len,
            this.radius * 1.1,
            this.radius * 2.0,
            alpha(warm, 0.5F * k)
         );
         double back = fx.hit.width * 0.5 + 0.43;
         Vfx.sprite(
            Textures.GLOW, this.mx + this.b.fx * back, this.my + this.b.fy * back, this.mz + this.b.fz * back, this.radius * 3.0, 0.0F, alpha(warm, 0.45F * k)
         );
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      float p = fx.progress();
      MosaicStyle.Medallion m = medallion(this.pattern.get(), this.cells.getInt(), fx.crit);
      Glass.Cells c = m.cells;
      double r = 0.5 * this.size.getFloat() * scale(fx) * (fx.crit ? 1.35F : 1.0F);
      int inPlace = 0;
      int falling = 0;
      int bounced = 0;

      for (int i = 0; i < c.n; i++) {
         if (!(grown(c, i, p) <= 0.0F)) {
            if (p < outAt(m, i, fx.seed)) {
               inPlace++;
            } else {
               falling++;
            }
         }
      }

      if (falling > 0 && this.b != null) {
         for (int var12 = 0; var12 < c.n; var12++) {
            float out = outAt(m, var12, fx.seed);
            if (p >= out && this.fall(fx, c, var12, (p - out) * 0.75F) && this.shardBounced) {
               bounced++;
            }
         }
      }

      return String.format(
         Locale.ROOT,
         "%s, %d cells (ring of %d), radius %.2f; %d in the lead, %d falling (%d bounced), tracery %.2f, glow %.2f",
         this.pattern.get().displayName(),
         c.n,
         m.petals,
         r,
         inPlace,
         falling,
         bounced,
         tracery(p),
         glowAmount(p)
      );
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   private static final class Medallion {
      final Glass.Cells cells;
      final float[] order;
      final int petals;

      Medallion(Glass.Cells cells, float[] order, int petals) {
         this.cells = cells;
         this.order = order;
         this.petals = petals;
      }
   }

   public static enum Pattern implements Displayable {
      ROSE("Rose", "Petals round a golden heart"),
      QUATREFOIL("Quatrefoil", "Four round lobes"),
      STAR("Star", "A many-pointed star");

      private final String displayName;
      private final String description;

      private Pattern(String displayName, String description) {
         this.displayName = displayName;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
