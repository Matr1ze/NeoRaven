package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Glass;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.module.trails.GroundLayout;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class StainedGlassStyle extends TrailStyleBase {
   private static final double TILE_U = 2.0;
   private static final double TILE_V = 1.0;
   private static final double UNIT = 0.75;
   private static final int CELLS = 10;
   private static final long SEED = 5743078L;
   private static final double MOTTLE = 2.0;
   private static final double STEP = 0.125;
   private static final double OTHERS_STEP = 0.2;
   private static final double FAR_STEP = 0.3;
   private static final double LEAD = 0.035;
   private static final double RISE = 0.6;
   private static final double LIT_BELOW = 0.4;
   private static final int MAX_POLY = 40;
   private static final float MOTE_WIND = 0.5F;
   public final ModeSetting<Glass.GlassColors> glassColors;
   public final ThemeTint glass2;
   public final ThemeTint glass3;
   public final ThemeTint lead;
   public final ModeSetting<StainedGlassStyle.Variant> variant;
   public final SliderSetting height;
   public final SliderSetting cellSize;
   public final SliderSetting leadWidth;
   public final SliderSetting glassOpacity;
   public final BooleanSetting groundLight;
   public final BooleanSetting motes;
   private final Glass.Cells cells = Glass.periodic(10, 5743078L, 2.0, 1.0);
   private final double[] minU = new double[10];
   private final double[] maxU = new double[10];
   private final double[] minV = new double[10];
   private final double[] maxV = new double[10];
   private final int[] palette = new int[4];
   private final Rng rng = new Rng();
   private final Map<PlayerTrail, StainedGlassStyle.Wall[]> wallsOf = new IdentityHashMap<>();
   private final double[] pu = new double[40];
   private final double[] pv = new double[40];
   private final double[] qu = new double[40];
   private final double[] qv = new double[40];
   private final double[] mx = new double[41];
   private final double[] my = new double[41];
   private final double[] mz = new double[41];
   private int drawnCells;
   private int droppingCells;
   private boolean counting;

   public StainedGlassStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.STAINED_GLASS,
         spec().length(1.6).anchor(TrailAnchor.FEET, true).fade(TrailStyleBase.FadeCurve.LATE)
      );
      this.lead = this.signatureColor("leadColor", "Lead", "The lead lines between the cells.", -15066594, -12961212);
      this.glass3 = this.signatureColor("glass3Color", "Glass 3", "The third glass color (Tinted).", -868018, -6496);
      this.glass2 = this.signatureColor("glass2Color", "Glass 2", "The second glass color (Tinted).", -4184513, -38273);
      this.glassColors = this.setting(
         new ModeSetting<>(
               "stained_glass.glassColors",
               "Glass Colors",
               "Cathedral: ruby, sapphire, emerald and gold. Tinted: the three glass colors.",
               Glass.GlassColors.class,
               Glass.GlassColors.CATHEDRAL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.glassColors.group("Colors");
      BooleanSupplier tinted = new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return StainedGlassStyle.this.glassColors.get() == Glass.GlassColors.TINTED;
         }
      };
      this.doesNotApply(new Setting[]{this.size, this.taper});
      this.variant = this.setting(
         new ModeSetting<>(
               "stained_glass.variant",
               "Variant",
               "A low wall standing along your path, or a band lying on the ground.",
               StainedGlassStyle.Variant.class,
               StainedGlassStyle.Variant.WALL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.height = this.setting(
         new SliderSetting("stained_glass.height", "Height", "How tall the wall stands (Floor: how wide the band is).", 0.2, 2.0, 0.7, 0.05).unit(" blocks")
      );
      this.cellSize = this.setting(new SliderSetting("stained_glass.cellSize", "Cell Size", "How big the glass cells are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.leadWidth = this.setting(
         new SliderSetting("stained_glass.leadWidth", "Lead Width", "How wide the lead lines are.", 50.0, 200.0, 100.0, 5.0).unit("%")
      );
      this.glassOpacity = this.setting(
         new SliderSetting("stained_glass.glassOpacity", "Glass Opacity", "How solid the glass looks.", 40.0, 100.0, 75.0, 1.0).unit("%")
      );
      this.groundLight = this.setting(new BooleanSetting("stained_glass.groundLight", "Ground Light", "Colored light falls on the ground on both sides.", true));
      this.motes = this.setting(new BooleanSetting("stained_glass.motes", "Motes", "Dust motes drift slowly up in the light.", false));

      for (int c = 0; c < this.cells.n; c++) {
         double a0 = Double.MAX_VALUE;
         double a1 = -Double.MAX_VALUE;
         double b0 = Double.MAX_VALUE;
         double b1 = -Double.MAX_VALUE;

         for (int k = this.cells.start[c]; k < this.cells.start[c] + this.cells.count[c]; k++) {
            a0 = Math.min(a0, this.cells.u[k]);
            a1 = Math.max(a1, this.cells.u[k]);
            b0 = Math.min(b0, this.cells.v[k]);
            b1 = Math.max(b1, this.cells.v[k]);
         }

         this.minU[c] = a0;
         this.maxU[c] = a1;
         this.minV[c] = b0;
         this.maxV[c] = b1;
      }
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.height);
      out.add(this.glassOpacity);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void update(TrailFrame f) {
      super.update(f);
      if (!this.wallsOf.isEmpty()) {
         Iterator<Entry<PlayerTrail, StainedGlassStyle.Wall[]>> it = this.wallsOf.entrySet().iterator();

         while (it.hasNext()) {
            double seen = -1.0;
            TrailFrame scene = null;

            for (StainedGlassStyle.Wall w : it.next().getValue()) {
               if (w != null) {
                  seen = Math.max(seen, w.seen);
                  scene = w.scene;
               }
            }

            if (scene == f && f.now - seen > 2.0) {
               it.remove();
            }
         }
      }
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      TrailShape s = p.shape;
      if (s != null && s.runs != 0) {
         StainedGlassStyle.Wall[] slots = this.wallsOf.get(p);
         if (slots == null) {
            slots = new StainedGlassStyle.Wall[4];
            this.wallsOf.put(p, slots);
         }

         int slot;
         if (slots[slot = p.side * 2 + (p.ghost ? 1 : 0)] == null) {
            slots[slot] = new StainedGlassStyle.Wall();
         }

         StainedGlassStyle.Wall w = slots[slot];
         w.seen = f.now;
         w.scene = f;
         if (pass == FxPass.GLOW) {
            if (w.stamp == f.now) {
               RenderState.additive();
               this.light(w);
            }
         } else {
            boolean bl = this.counting = p.self && !p.ghost && !f.preview;
            if (this.counting) {
               this.droppingCells = 0;
               this.drawnCells = 0;
            }

            w.stamp = f.now;
            w.cells = 0;
            w.points = 0;
            w.floor = this.variant.get() == StainedGlassStyle.Variant.FLOOR;
            w.even = !p.self;
            this.palette(f);
            double head = traveledAtHead(p);
            double step = p.reduced ? 0.3 : (p.self ? 0.125 : 0.2);
            RenderState.translucent(false);

            for (int k = 0; k < s.runs; k++) {
               if (GroundLayout.lay(this, p, f, k, head, 1.0F, step) >= 2 && GroundLayout.pieces > 0) {
                  this.wall(p, f, w, head, Math.max(0.001F, s.length));
               }
            }

            this.leads(w, 0.035 * this.leadWidth.getFloat() / 100.0 * 0.5, this.slotColor(this.lead, 0.5F, f.colorTime) | 0xFF000000);
            if (this.motes.get() && !p.reduced && !p.ghost && this.spawnPass(pass)) {
               this.motes(p, f);
            }
         }
      }
   }

   private void palette(TrailFrame f) {
      if (this.tint != 0) {
         for (int i = 0; i < 4; i++) {
            this.palette[i] = ColorUtil.shade(this.tint | 0xFF000000, 0.7F + 0.1F * i);
         }
      } else if (this.glassColors.get() == Glass.GlassColors.TINTED) {
         Glass.tinted(this.color.getColor(0.5F, f.colorTime), this.glass2.getColor(0.5F, f.colorTime), this.glass3.getColor(0.5F, f.colorTime), this.palette);
      } else {
         System.arraycopy(Glass.CATHEDRAL_SET, 0, this.palette, 0, 4);
      }

      if (this.light < 0.999F) {
         for (int i = 0; i < 4; i++) {
            this.palette[i] = ColorUtil.shade(this.palette[i], this.light);
         }
      }
   }

   private void wall(PlayerTrail p, TrailFrame f, StainedGlassStyle.Wall w, double head, float total) {
      boolean floor = w.floor;
      double unit = 0.75 * this.cellSize.getFloat() / 100.0;
      double hb = this.height.getFloat() * p.widthScale;
      double hu = hb / unit;
      double u0 = GroundLayout.DIST[0] / unit;
      double u1 = GroundLayout.DIST[GroundLayout.n - 1] / unit;
      long ku0 = (long)Math.floor(u0 / 2.0) - 1L;
      long ku1 = (long)Math.floor(u1 / 2.0) + 1L;
      int kv1 = (int)Math.floor(hu / 1.0) + 1;
      boolean tintedGlass1 = this.tint == 0 && this.glassColors.get() == Glass.GlassColors.TINTED;
      boolean patches = this.groundLight.get() && !p.reduced && !floor;
      float opacityK = this.glassOpacity.getFloat() / 100.0F;
      w.spread = 0.3 + 0.2 * hb;
      w.size = 0.5 + 0.25 * hb;

      for (long ku = ku0; ku <= ku1; ku++) {
         double ou = ku * 2.0;

         for (int kv = -1; kv <= kv1; kv++) {
            double ov = kv * 1.0;

            for (int c = 0; c < this.cells.n; c++) {
               int np;
               if (!(this.maxU[c] + ou <= u0)
                  && !(this.minU[c] + ou >= u1)
                  && !(this.maxV[c] + ov <= 0.0)
                  && !(this.minV[c] + ov >= hu)
                  && (np = this.clip(c, ou, ov, u0, u1, hu)) >= 3) {
                  double cu = 0.0;
                  double cv = 0.0;

                  for (int k = 0; k < np; k++) {
                     cu += this.pu[k];
                     cv += this.pv[k];
                  }

                  cv /= np;
                  double var54;
                  double dc = (var54 = cu / np) * unit;
                  int qc = GroundLayout.indexAt(dc);
                  int piece = GroundLayout.PIECE[qc];
                  if (piece >= 0 && GroundLayout.PIECE[qc + 1] == piece) {
                     float share = (float)((head - dc) / total);
                     int id = (int)(ku * 31L + kv * 977L + c * 7919L);
                     float t0 = 0.62F + 0.3F * Rng.hash(id, 5);
                     float dark = Easing.smoothstep(t0, t0 + 0.06F, share);
                     float drop = Easing.clamp01((share - t0 - 0.06F) / 0.12F);
                     if (!(drop >= 1.0F)) {
                        double fall = drop * drop * 0.9;
                        double tip = drop * 0.9 * (Rng.hash(id, 7) < 0.5F ? -1 : 1);
                        float a;
                        if (this.place(np, var54, cv, tip, unit, hb, head, floor, piece, fall) && !((a = alphaAt(dc) * (1.0F - drop)) <= 0.01F)) {
                           int col = this.palette[this.cells.color[c]];
                           if (tintedGlass1 && this.cells.color[c] == 1) {
                              col = this.colorAt(Easing.clamp01(share), Easing.clamp01(share), 0.0F, f.colorTime, 0.0F);
                           }

                           col = ColorUtil.shade(col, 1.0F - 0.75F * dark) | 0xFF000000;
                           this.glass(np, ou, ov, ColorUtil.mulAlpha(col, a * opacityK));
                           this.keep(w, np, col, a, patches && drop <= 0.0F && cv * unit <= 0.4, qc);
                           if (this.counting) {
                              this.drawnCells++;
                              if (drop > 0.0F) {
                                 this.droppingCells++;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int clip(int c, double ou, double ov, double u0, double u1, double hu) {
      int n = Math.min(40, this.cells.count[c]);

      for (int k = 0; k < n; k++) {
         this.pu[k] = this.cells.u[this.cells.start[c] + k] + ou;
         this.pv[k] = this.cells.v[this.cells.start[c] + k] + ov;
      }

      n = this.clipSide(n, 1.0, 0.0, -u0);
      n = this.clipSide(n, -1.0, 0.0, u1);
      n = this.clipSide(n, 0.0, 1.0, 0.0);
      return this.clipSide(n, 0.0, -1.0, hu);
   }

   private int clipSide(int n, double a, double b, double d) {
      if (n < 3) {
         return n;
      } else {
         int m = 0;

         for (int k = 0; k < n; k++) {
            int kn = (k + 1) % n;
            double e0 = a * this.pu[k] + b * this.pv[k] + d;
            double e1 = a * this.pu[kn] + b * this.pv[kn] + d;
            if (e0 >= 0.0 && m < 40) {
               this.qu[m] = this.pu[k];
               this.qv[m] = this.pv[k];
               m++;
            }

            if (e0 < 0.0 != e1 < 0.0 && m < 40) {
               double t = e0 / (e0 - e1);
               this.qu[m] = this.pu[k] + (this.pu[kn] - this.pu[k]) * t;
               this.qv[m] = this.pv[k] + (this.pv[kn] - this.pv[k]) * t;
               m++;
            }
         }

         System.arraycopy(this.qu, 0, this.pu, 0, m);
         System.arraycopy(this.qv, 0, this.pv, 0, m);
         return m;
      }
   }

   private static float alphaAt(double d) {
      int q = GroundLayout.indexAt(d);
      double b = GroundLayout.DIST[q + 1];
      double a;
      float t = b - (a = GroundLayout.DIST[q]) > 1.0E-9 ? Easing.clamp01((d - a) / (b - a)) : 0.0F;
      return GroundLayout.ALPHA[q] + (GroundLayout.ALPHA[q + 1] - GroundLayout.ALPHA[q]) * t;
   }

   private boolean place(int np, double cu, double cv, double tip, double unit, double hb, double head, boolean floor, int piece, double fall) {
      double ct = Math.cos(tip);
      double st = Math.sin(tip);
      int k = 0;

      while (k <= np) {
         double u = k == np ? cu : this.pu[k];
         double v = k == np ? cv : this.pv[k];
         if (tip != 0.0) {
            double du = u - cu;
            double dv = v - cv;
            u = cu + du * ct - dv * st;
            v = cv + du * st + dv * ct;
         }

         if (!this.map(u, v, unit, hb, head, floor, piece, k)) {
            return false;
         }

         int n = k++;
         this.my[n] = this.my[n] - fall;
      }

      return true;
   }

   private boolean map(double u, double v, double unit, double hb, double head, boolean floor, int piece, int i) {
      double d = u * unit;
      int q = GroundLayout.indexAt(d);
      if (GroundLayout.PIECE[q] == piece && GroundLayout.PIECE[q + 1] == piece) {
         double b = GroundLayout.DIST[q + 1];
         double a = GroundLayout.DIST[q];
         double t = b - a > 1.0E-9 ? Math.max(0.0, Math.min(1.0, (d - a) / (b - a))) : 0.0;
         double x = GroundLayout.X[q] + (GroundLayout.X[q + 1] - GroundLayout.X[q]) * t;
         double z = GroundLayout.Z[q] + (GroundLayout.Z[q + 1] - GroundLayout.Z[q]) * t;
         double ground = GroundLayout.GROUND[q] + (GroundLayout.GROUND[q + 1] - GroundLayout.GROUND[q]) * t;
         double h = v * unit;
         if (floor) {
            double sx = GroundLayout.SIDE_X[q] + (GroundLayout.SIDE_X[q + 1] - GroundLayout.SIDE_X[q]) * t;
            double sz = GroundLayout.SIDE_Z[q] + (GroundLayout.SIDE_Z[q + 1] - GroundLayout.SIDE_Z[q]) * t;
            double across = h - hb * 0.5;
            this.mx[i] = x + sx * across;
            this.my[i] = ground + 0.012;
            this.mz[i] = z + sz * across;
         } else {
            double rise = Easing.smoothstep(0.0F, 0.6F, (float)(head - d));
            this.mx[i] = x;
            this.my[i] = ground + h - (1.0 - rise) * (hb + 0.05);
            this.mz[i] = z;
         }

         return true;
      } else {
         return false;
      }
   }

   private void glass(int np, double ou, double ov, int col) {
      int tex = Glass.texture();
      double u0 = (this.pu[0] - ou) * 2.0;
      double v0 = (this.pv[0] - ov) * 2.0;

      for (int k = 1; k + 1 < np; k += 2) {
         int k2 = k + 1;
         int k3 = Math.min(np - 1, k + 2);
         Vfx.quadUV(
            tex,
            this.mx[0],
            this.my[0],
            this.mz[0],
            u0,
            v0,
            col,
            this.mx[k],
            this.my[k],
            this.mz[k],
            (this.pu[k] - ou) * 2.0,
            (this.pv[k] - ov) * 2.0,
            col,
            this.mx[k2],
            this.my[k2],
            this.mz[k2],
            (this.pu[k2] - ou) * 2.0,
            (this.pv[k2] - ov) * 2.0,
            col,
            this.mx[k3],
            this.my[k3],
            this.mz[k3],
            (this.pu[k3] - ou) * 2.0,
            (this.pv[k3] - ov) * 2.0,
            col
         );
      }
   }

   private void leads(StainedGlassStyle.Wall w, double hw, int leadCol) {
      double lift = w.floor ? 0.002 : 0.0;

      for (int c = 0; c < w.cells; c++) {
         int at = w.start[c];
         int np = w.count[c];
         int col = ColorUtil.mulAlpha(leadCol, Math.min(1.0F, w.alpha[c] * 1.15F));
         double nx = w.floor ? 0.0 : w.nx[c];
         double ny = w.floor ? 1.0 : 0.0;
         double nz = w.floor ? 0.0 : w.nz[c];

         for (int k = 0; k < np; k++) {
            int i2 = at + (k + 1) % np;
            int i = at + k;
            double ex = w.x[i2] - w.x[i];
            double ey = w.y[i2] - w.y[i];
            double ez = w.z[i2] - w.z[i];
            double el = Math.sqrt(ex * ex + ey * ey + ez * ez);
            double wz;
            double wy;
            double wx;
            double wl;
            if (!(el < 1.0E-6)
               && !(
                  (
                        wl = Math.sqrt(
                           (wx = (ey = ey / el) * nz - (ez = ez / el) * ny) * wx + (wy = ez * nx - (ex = ex / el) * nz) * wy + (wz = ex * ny - ey * nx) * wz
                        )
                     )
                     < 1.0E-6
               )) {
               wx *= hw / wl;
               wy *= hw / wl;
               wz *= hw / wl;
               double ax = w.x[i] - ex * hw;
               double ay = w.y[i] - ey * hw + lift;
               double az = w.z[i] - ez * hw;
               double bx = w.x[i2] + ex * hw;
               double by = w.y[i2] + ey * hw + lift;
               double bz = w.z[i2] + ez * hw;
               Vfx.quadUV(
                  Textures.WHITE,
                  ax - wx,
                  ay - wy,
                  az - wz,
                  0.5,
                  0.5,
                  col,
                  bx - wx,
                  by - wy,
                  bz - wz,
                  0.5,
                  0.5,
                  col,
                  bx + wx,
                  by + wy,
                  bz + wz,
                  0.5,
                  0.5,
                  col,
                  ax + wx,
                  ay + wy,
                  az + wz,
                  0.5,
                  0.5,
                  col
               );
            }
         }
      }
   }

   private void keep(StainedGlassStyle.Wall w, int np, int col, float a, boolean lights, int q) {
      int c = w.add(np);
      int at = w.start[c];

      for (int k = 0; k <= np; k++) {
         w.x[at + k] = this.mx[k];
         w.y[at + k] = this.my[k];
         w.z[at + k] = this.mz[k];
      }

      w.color[c] = col;
      w.alpha[c] = a;
      w.nx[c] = GroundLayout.SIDE_X[q];
      w.nz[c] = GroundLayout.SIDE_Z[q];
      if (lights) {
         w.gx[c] = this.mx[np];
         w.gy[c] = GroundLayout.GROUND[q] + 0.02;
         w.gz[c] = this.mz[np];
         w.sx[c] = GroundLayout.SIDE_X[q];
         w.sz[c] = GroundLayout.SIDE_Z[q];
      } else {
         w.gy[c] = Double.NaN;
      }
   }

   private void light(StainedGlassStyle.Wall w) {
      float glowAmount = this.glow.getFloat() / 100.0F;
      if (!(glowAmount <= 0.005F)) {
         float k = glowAmount * (w.floor ? 0.55F : 0.3F);

         for (int c = 0; c < w.cells; c++) {
            int at = w.start[c];
            int np = w.count[c];
            int mid = at + np;
            float a = w.alpha[c] * k;
            if (!(a <= 0.005F)) {
               int col = w.color[c];
               if (w.even) {
                  int even = ColorUtil.mulAlpha(col, a * 0.7F);

                  for (int i = 1; i + 1 < np; i += 2) {
                     int i2 = i + 1;
                     int i3 = Math.min(np - 1, i + 2);
                     Vfx.quadUV(
                        Textures.WHITE,
                        w.x[at],
                        w.y[at],
                        w.z[at],
                        0.5,
                        0.5,
                        even,
                        w.x[at + i],
                        w.y[at + i],
                        w.z[at + i],
                        0.5,
                        0.5,
                        even,
                        w.x[at + i2],
                        w.y[at + i2],
                        w.z[at + i2],
                        0.5,
                        0.5,
                        even,
                        w.x[at + i3],
                        w.y[at + i3],
                        w.z[at + i3],
                        0.5,
                        0.5,
                        even
                     );
                  }
               } else {
                  int center = ColorUtil.mulAlpha(col, a);
                  int edge = ColorUtil.mulAlpha(col, a * 0.4F);

                  for (int i = 0; i < np; i++) {
                     int i2 = (i + 1) % np;
                     Vfx.quadUV(
                        Textures.WHITE,
                        w.x[mid],
                        w.y[mid],
                        w.z[mid],
                        0.5,
                        0.5,
                        center,
                        w.x[at + i],
                        w.y[at + i],
                        w.z[at + i],
                        0.5,
                        0.5,
                        edge,
                        w.x[at + i2],
                        w.y[at + i2],
                        w.z[at + i2],
                        0.5,
                        0.5,
                        edge,
                        w.x[at + i2],
                        w.y[at + i2],
                        w.z[at + i2],
                        0.5,
                        0.5,
                        edge
                     );
                  }
               }
            }
         }

         for (int var14 = 0; var14 < w.cells; var14++) {
            if (!Double.isNaN(w.gy[var14])) {
               int patch = ColorUtil.mulAlpha(w.color[var14], w.alpha[var14] * glowAmount * 0.3F);

               for (int side = -1; side <= 1; side += 2) {
                  Glass.patch(w.gx[var14] + w.sx[var14] * w.spread * side, w.gy[var14], w.gz[var14] + w.sz[var14] * w.spread * side, (float)w.size, patch);
               }
            }
         }
      }
   }

   private void motes(PlayerTrail p, TrailFrame f) {
      int due = this.piecesDue(p, 1.2, 2);
      if (due != 0 && GroundLayout.n >= 2) {
         FxParticles pool = f.pieces(this.type);
         double hb = this.height.getFloat();

         for (int i = 0; i < due && !this.module.piecesFull(f); i++) {
            this.rng.seed((int)(f.now * 3917.0) + i * 113 + p.entityId * 19);
            int q = (int)(this.rng.next() * (GroundLayout.n - 1));
            if (!Double.isNaN(GroundLayout.GROUND[q])) {
               double side = this.rng.signed() * 0.4;
               double x = GroundLayout.X[q] + GroundLayout.SIDE_X[q] * side + f.viewX;
               double y = GroundLayout.GROUND[q] + this.rng.next() * hb + f.viewY;
               double z = GroundLayout.Z[q] + GroundLayout.SIDE_Z[q] * side + f.viewZ;
               pool.spawn(
                     3,
                     x,
                     y,
                     z,
                     this.rng.signed() * 0.05F,
                     0.08F + this.rng.next() * 0.08F,
                     this.rng.signed() * 0.05F,
                     1.6F + this.rng.next(),
                     0.025F + this.rng.next() * 0.02F
                  )
                  .alpha(0.7F * this.opacity.getFloat() / 100.0F * p.alpha)
                  .flags(2)
                  .color(this.palette[(int)(this.rng.next() * 4.0F) & 3])
                  .wind(0.5F);
            }
         }
      }
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      if (pass == FxPass.GLOW) {
         FxParticles pool = f.piecesIfAny(this.type);
         if (pool != null) {
            pool.render(f.viewX, f.viewY, f.viewZ);
         }
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%s, %s, height %.2f, cells %.0f%%, lead %.0f%%, glass %.0f%%, ground light %s, motes %s; drawn: %d cell(s), %d dropping out",
         this.variant.get().displayName(),
         this.glassColors.get().displayName(),
         this.height.getFloat(),
         this.cellSize.getFloat(),
         this.leadWidth.getFloat(),
         this.glassOpacity.getFloat(),
         this.groundLight.get() ? "on" : "off",
         this.motes.get() ? "on" : "off",
         this.drawnCells,
         this.droppingCells
      );
   }

   public static enum Variant implements Displayable {
      WALL("Wall", "A low wall of glass standing along your path"),
      FLOOR("Floor", "A band of glass lying on the ground, lit from below");

      private final String displayName;
      private final String description;

      private Variant(String displayName, String description) {
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

   private static final class Wall {
      double stamp = -1.0;
      double seen;
      TrailFrame scene;
      int cells;
      int points;
      boolean floor;
      boolean even;
      int[] start = new int[64];
      int[] count = new int[64];
      int[] color = new int[64];
      float[] alpha = new float[64];
      double[] nx = new double[64];
      double[] nz = new double[64];
      double[] gx = new double[64];
      double[] gy = new double[64];
      double[] gz = new double[64];
      double[] sx = new double[64];
      double[] sz = new double[64];
      double spread;
      double size;
      double[] x = new double[512];
      double[] y = new double[512];
      double[] z = new double[512];

      private Wall() {
      }

      int add(int n) {
         if (this.cells == this.start.length) {
            int cap = this.cells * 2;
            this.start = Arrays.copyOf(this.start, cap);
            this.count = Arrays.copyOf(this.count, cap);
            this.color = Arrays.copyOf(this.color, cap);
            this.alpha = Arrays.copyOf(this.alpha, cap);
            this.nx = Arrays.copyOf(this.nx, cap);
            this.nz = Arrays.copyOf(this.nz, cap);
            this.gx = Arrays.copyOf(this.gx, cap);
            this.gy = Arrays.copyOf(this.gy, cap);
            this.gz = Arrays.copyOf(this.gz, cap);
            this.sx = Arrays.copyOf(this.sx, cap);
            this.sz = Arrays.copyOf(this.sz, cap);
         }

         if (this.points + n + 1 > this.x.length) {
            int cap = Math.max(this.x.length * 2, this.points + n + 1);
            this.x = Arrays.copyOf(this.x, cap);
            this.y = Arrays.copyOf(this.y, cap);
            this.z = Arrays.copyOf(this.z, cap);
         }

         int c = this.cells++;
         this.start[c] = this.points;
         this.count[c] = n;
         this.points += n + 1;
         return c;
      }
   }
}
