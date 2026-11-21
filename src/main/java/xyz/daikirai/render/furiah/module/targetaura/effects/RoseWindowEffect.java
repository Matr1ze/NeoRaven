package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.kit.Glass;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
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

public final class RoseWindowEffect extends AuraEffect {
   private static final int RUBY = 0;
   private static final int SAPPHIRE = 1;
   private static final int EMERALD = 2;
   private static final int GOLD = 3;
   private static final int[] CATHEDRAL = new int[]{-5238742, -15382856, -15496642, -1527238};
   private static final int STONE = -7436419;
   private static final double LEAD = 0.028;
   private static final double FRAME_IN = 0.875;
   private static final double BAND_IN = 0.655;
   private static final double BAND_OUT = 0.705;
   private static final int RING_SEGS = 48;
   private static final int FRAME_PIECES = 8;
   private static final double[] RING_COS = Glass.RING_COS;
   private static final double[] RING_SIN = Glass.RING_SIN;
   private static final double[] FRAME_R = new double[]{0.875, 0.905, 0.97, 1.0};
   private static final double[] FRAME_Z = new double[]{0.0, 0.025, 0.025, 0.0};
   private static final double[] FRAME_LEAN = new double[]{-0.7, 0.0, 0.0, 0.7};
   private static final int SHAFTS = 6;
   private static final int MOTES = 3;
   private static final double BEHIND = 0.4;
   public final ThemeTint glass2;
   public final ThemeTint glass3;
   public final ThemeTint leadColor;
   public final ModeSetting<Glass.GlassColors> glassColors;
   public final ModeSetting<RoseWindowEffect.Pattern> pattern;
   public final ModeSetting<RoseWindowEffect.Position> position;
   public final SliderSetting shafts;
   public final BooleanSetting groundLight;
   public final SliderSetting opacity;
   public final BooleanSetting motes;
   private double ox;
   private double oy;
   private double oz;
   private double rx;
   private double rz;
   private double nx;
   private double nz;
   private double radius;
   private double turn;
   private final int[] palette = new int[4];
   private final double[] tu = new double[64];
   private final double[] tv = new double[64];
   private double tcu;
   private double tcv;
   private double dz;
   private double dy;
   private float cellAlpha;
   private final double[] sx = new double[6];
   private final double[] sy = new double[6];
   private final double[] sz = new double[6];
   private final int[] scol = new int[6];
   private final Glass.Pane pane = new Glass.Pane();

   private static Glass.Cells cells(RoseWindowEffect.Pattern p) {
      return p == RoseWindowEffect.Pattern.MANDALA ? Glass.mandala() : (p == RoseWindowEffect.Pattern.LANCET ? Glass.lancet() : Glass.rose());
   }

   public RoseWindowEffect() {
      super(
         EffectType.ROSE_WINDOW,
         "rosewindow",
         new ThemeTint(),
         true
      );
      this.glass2 = ThemeTint.detail(-4184513);
      this.glass3 = ThemeTint.detail(-868018);
      this.leadColor = ThemeTint.detail(-15066594);
      this.glassColors = this.setting(
         new ModeSetting<>(
            "rosewindow.glassColors",
            "Glass Colors",
            "Cathedral: ruby, sapphire, emerald and gold. Tinted: the three glass colors. The light takes the glass colors.",
            Glass.GlassColors.class,
            Glass.GlassColors.CATHEDRAL
         )
      );
      this.pattern = this.setting(
         new ModeSetting<>("rosewindow.pattern", "Pattern", "The window's design.", RoseWindowEffect.Pattern.class, RoseWindowEffect.Pattern.ROSE)
            .style(ModeSetting.Style.CHIPS)
      );
      this.position = this.setting(
         new ModeSetting<>(
            "rosewindow.position",
            "Position",
            "Behind the head and shoulders, or behind the whole body.",
            RoseWindowEffect.Position.class,
            RoseWindowEffect.Position.HEAD
         )
      );
      this.shafts = this.look(
         this.setting(
            new SliderSetting("rosewindow.shafts", "Light Shafts", "How strong the colored light through the glass is.", 0.0, 100.0, 60.0, 5.0).unit("%")
         )
      );
      this.groundLight = this.setting(new BooleanSetting("rosewindow.groundLight", "Ground Light", "Tinted patches of light on the ground.", true));
      this.opacity = this.setting(
         new SliderSetting("rosewindow.opacity", "Glass Opacity", "How much of what is behind the glass it hides.", 40.0, 100.0, 85.0, 5.0).unit("%")
      );
      this.motes = this.setting(new BooleanSetting("rosewindow.motes", "Motes", "Dust drifting in the light.", true));
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      float alpha = this.intensity(c);
      if (!(alpha <= 0.003F)) {
         if (pass == FxPass.SOLID) {
            this.layout(c);
            this.frame(c, alpha);
         } else if (pass == FxPass.TRANSLUCENT) {
            this.layout(c);
            this.glass(c, alpha);
         } else if (pass == FxPass.GLOW) {
            this.layout(c);
            this.light(c, alpha);
         }
      }
   }

   private void layout(EffectContext c) {
      double s = this.size(c);
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      double tx = cam.posX - c.x;
      double tz = cam.posZ - c.z;
      double tl = Math.sqrt(tx * tx + tz * tz);
      if (tl < 1.0E-6) {
         tx = 0.0;
         tz = 1.0;
         tl = 1.0;
      }

      this.nx = tx / tl;
      this.rx = this.nz = tz / tl;
      this.rz = -this.nx;
      double cy;
      if (this.position.get() == RoseWindowEffect.Position.BODY) {
         this.radius = 0.85 * s;
         cy = Math.max(c.y + 0.94, c.groundY + this.radius + 0.05);
      } else {
         this.radius = 0.58 * s;
         cy = c.y + c.headTop - 0.15;
         if (c.nameBottom > c.headTop + 0.01) {
            cy = Math.min(cy, c.y + c.nameBottom - 0.03 - this.radius);
         }
      }

      this.ox = c.x - this.nx * 0.4;
      this.oy = cy;
      this.oz = c.z - this.nz * 0.4;
      this.turn = c.time * this.speed() * Math.toRadians(4.0);
      if (this.glassColors.get() == Glass.GlassColors.TINTED) {
         Glass.tinted(this.color(c, 0.5F), this.glass2.getColor(0.5F), this.glass3.getColor(0.5F), this.palette);
      } else {
         System.arraycopy(CATHEDRAL, 0, this.palette, 0, 4);
      }
   }

   public String stateForTest(EffectContext c) {
      this.layout(c);
      double dx = this.ox - c.x;
      double dz2 = this.oz - c.z;
      return String.format(
         Locale.ROOT,
         "%s, %d cells, center %.2f up and %.2f behind, radius %.2f (top %.2f; name tag from %.2f)",
         this.pattern.get().displayName(),
         cells(this.pattern.get()).n,
         this.oy - c.y,
         Math.sqrt(dx * dx + dz2 * dz2),
         this.radius,
         this.oy - c.y + this.radius,
         c.nameBottom
      );
   }

   private double wx(double u) {
      return this.ox + this.rx * u * this.radius + this.nx * this.dz;
   }

   private double wy(double v) {
      return this.oy + v * this.radius + this.dy;
   }

   private double wz(double u) {
      return this.oz + this.rz * u * this.radius + this.nz * this.dz;
   }

   private boolean place(EffectContext c, Glass.Cells p, int i) {
      double t = c.time;
      double appear = 0.04 + 0.32 * Math.min(1.0, p.rad[i] / 0.875) + 0.04 * hash(i, 3);
      float grow = Easing.clamp01((float)((t - appear) / 0.12));
      if (grow <= 0.0F) {
         return false;
      } else {
         double sc = Easing.outBack(grow, 1.6F);
         float a = Easing.clamp01(grow * 2.0F);
         float out = c.outro;
         double f = out <= 0.0F ? 0.0 : Easing.clamp01((out - 0.2F * hash(i, 5)) / 0.8F);
         double spin = (hash(i, 7) - 0.5) * 3.0 * f;
         double drift = 0.3 * f;
         this.dy = -1.5 * f * f;
         this.dz = 0.12 * f * hash(i, 11);
         float var52;
         this.cellAlpha = var52 = a * (1.0F - Easing.smoothstep(0.5F, 1.0F, (float)f));
         if (var52 <= 0.003F) {
            return false;
         } else {
            double ct = p.turns[i] ? Math.cos(this.turn) : 1.0;
            double st = p.turns[i] ? Math.sin(this.turn) : 0.0;
            double cu = p.cu[i] * ct - p.cv[i] * st;
            double cv = p.cu[i] * st + p.cv[i] * ct;
            double len = Math.sqrt(cu * cu + cv * cv);
            double ou = len > 1.0E-6 ? cu / len : 0.0;
            double ov = len > 1.0E-6 ? cv / len : -1.0;
            this.tcu = cu + ou * drift;
            this.tcv = cv + ov * drift;
            double cs = Math.cos(spin);
            double ss = Math.sin(spin);
            int from = p.start[i];
            int n = Math.min(p.count[i], this.tu.length);

            for (int k = 0; k < n; k++) {
               double ru = p.u[from + k] - p.cu[i];
               double rv = p.v[from + k] - p.cv[i];
               double au = ru * ct - rv * st;
               double av = ru * st + rv * ct;
               double bu = au * cs - av * ss;
               double bv = au * ss + av * cs;
               this.tu[k] = this.tcu + bu * sc;
               this.tv[k] = this.tcv + bv * sc;
            }

            return true;
         }
      }
   }

   private Glass.Pane pane() {
      this.pane.ox = this.ox;
      this.pane.oy = this.oy;
      this.pane.oz = this.oz;
      this.pane.rx = this.rx;
      this.pane.ry = 0.0;
      this.pane.rz = this.rz;
      this.pane.ux = 0.0;
      this.pane.uy = 1.0;
      this.pane.uz = 0.0;
      this.pane.nx = this.nx;
      this.pane.ny = 0.0;
      this.pane.nz = this.nz;
      this.pane.scale = this.radius;
      this.pane.offX = this.nx * this.dz;
      this.pane.offY = this.dy;
      this.pane.offZ = this.nz * this.dz;
      return this.pane;
   }

   private void glass(EffectContext c, float alpha) {
      Glass.Cells p = cells(this.pattern.get());
      float glassLight = Math.max(c.light, 0.6F);
      float op = this.opacity.getFloat() / 100.0F;
      int lead = ColorUtil.shade(this.leadColor.getColor(0.5F), c.light);
      RenderState.decal(true);

      try {
         for (int i = 0; i < p.n; i++) {
            if (this.place(c, p, i)) {
               int base = ColorUtil.shade(this.palette[p.color[i]], glassLight * (0.9F + 0.2F * hash(i, 13)));
               float a = alpha * op * this.cellAlpha;
               int mid = alphaOf(ColorUtil.lerp(base, -1, 0.22F), a);
               int edge = alphaOf(ColorUtil.shade(base, 0.78F), a);
               Glass.glass(this.pane(), this.tu, this.tv, p.count[i], this.tcu, this.tcv, mid, edge);
            }
         }

         for (int var15 = 0; var15 < p.n; var15++) {
            if (this.place(c, p, var15)) {
               Glass.lead(this.pane(), this.tu, this.tv, p.count[var15], 0.028, alphaOf(lead, alpha * this.cellAlpha));
            }
         }

         this.dz = 0.0;
         this.dy = 0.0;
         Glass.ringLead(this.pane(), 0.655, 0.705, alphaOf(lead, alpha * this.bandAlpha(c)));
      } finally {
         RenderState.decal(false);
      }
   }

   private float bandAlpha(EffectContext c) {
      return Easing.clamp01((float)((c.time - 0.28) / 0.1)) * (1.0F - Easing.smoothstep(0.0F, 0.35F, c.outro));
   }

   private void frame(EffectContext c, float alpha) {
      double t = c.time;
      float in = Easing.clamp01((float)((t - 0.3) / 0.15));
      if (!(in <= 0.0F)) {
         Glass.Cells p = cells(this.pattern.get());
         MeshBatch.begin(Material.CUSTOM, Finish.MATTE, c.light, 0.06F, -1, false, true);
         int per = 6;
         double grow = 0.92 + 0.08 * Easing.outCubic(in);

         for (int piece = 0; piece < 8; piece++) {
            float out = c.outro;
            double f = out <= 0.0F ? 0.0 : Easing.clamp01((out - 0.15F * hash(piece, 17)) / 0.85F);
            float a2 = alpha * in * (1.0F - Easing.smoothstep(0.5F, 1.0F, (float)f));
            if (!(a2 <= 0.003F)) {
               int col = alphaOf(-7436419, a2);
               this.dy = -1.5 * f * f;
               this.dz = 0.1 * f * hash(piece, 19);
               double mid = (piece + 0.5) * (Math.PI * 2) / 8.0;
               double du = Math.cos(mid) * 0.25 * f;
               double dv = Math.sin(mid) * 0.25 * f;
               int base = MeshBatch.stage((per + 1) * 4);

               for (int s = 0; s <= per; s++) {
                  int seg = piece * per + s;
                  double cs = RING_COS[seg];
                  double sn = RING_SIN[seg];

                  for (int r = 0; r < 4; r++) {
                     double rr = FRAME_R[r] * grow;
                     double u = cs * rr + du;
                     double v = sn * rr + dv;
                     double lean = FRAME_LEAN[r];
                     double nxv = this.nx * (1.0 - Math.abs(lean)) + this.rx * cs * lean;
                     double nyv = sn * lean;
                     double nzv = this.nz * (1.0 - Math.abs(lean)) + this.rz * cs * lean;
                     double l = Math.sqrt(nxv * nxv + nyv * nyv + nzv * nzv);
                     double zz = FRAME_Z[r] * this.radius;
                     MeshBatch.vertex(base + s * 4 + r, this.wx(u) + this.nx * zz, this.wy(v), this.wz(u) + this.nz * zz, nxv / l, nyv / l, nzv / l, col, 0.0F);
                  }
               }

               for (int var51 = 0; var51 < per; var51++) {
                  for (int r = 0; r < 3; r++) {
                     int a0 = base + var51 * 4 + r;
                     int b0 = base + (var51 + 1) * 4 + r;
                     MeshBatch.quad(a0, b0, b0 + 1, a0 + 1);
                  }
               }
            }
         }

         float a;
         if (p.stone && (a = alpha * in * (1.0F - Easing.smoothstep(0.0F, 0.4F, c.outro))) > 0.003F) {
            int col = alphaOf(ColorUtil.shade(-7436419, 0.86F), a);
            this.dy = 0.0;
            this.dz = -0.004 / Math.max(0.01, this.radius);
            int base = MeshBatch.stage(49);
            MeshBatch.vertex(base, this.wx(0.0), this.wy(0.0), this.wz(0.0), this.nx, 0.0, this.nz, col, 0.0F);

            for (int s = 0; s < 48; s++) {
               double u = RING_COS[s] * 0.655;
               double v = RING_SIN[s] * 0.655;
               MeshBatch.vertex(base + 1 + s, this.wx(u), this.wy(v), this.wz(u), this.nx, 0.0, this.nz, col, 0.0F);
            }

            for (int var50 = 0; var50 < 48; var50++) {
               MeshBatch.tri(base, base + 1 + var50, base + 1 + (var50 + 1) % 48);
            }
         }

         MeshBatch.end();
         this.dz = 0.0;
         this.dy = 0.0;
      }
   }

   private void light(EffectContext c, float alpha) {
      float strength = this.shafts.getFloat() / 100.0F;
      float on = Easing.smoothstep(0.45F, 0.75F, (float)c.time) * (1.0F - Easing.smoothstep(0.0F, 0.25F, c.outro));
      float a = alpha * on;
      if (!(a <= 0.003F) && (!(strength <= 0.0F) || this.groundLight.get())) {
         Glass.Cells p = cells(this.pattern.get());
         Camera cam = c.cam != null ? c.cam : Vfx.camera();
         double s = this.size(c);

         for (int k = 0; k < 6; k++) {
            int i = p.shaftCells[k];
            double ct = p.turns[i] ? Math.cos(this.turn) : 1.0;
            double st = p.turns[i] ? Math.sin(this.turn) : 0.0;
            double cu = p.cu[i] * ct - p.cv[i] * st;
            double cv = p.cu[i] * st + p.cv[i] * ct;
            this.dz = 0.0;
            this.dy = 0.0;
            double px = this.wx(cu);
            double py = this.wy(cv);
            double pz = this.wz(cu);
            double len0 = Math.abs(cu) + 0.2;
            double spread = cu / len0 * 0.45;
            double dxv = this.nx * 0.35 + this.rx * spread;
            double dyv = -1.0;
            double dzv = this.nz * 0.35 + this.rz * spread;
            double dl = Math.sqrt(dxv * dxv + dyv * dyv + dzv * dzv);
            dxv /= dl;
            dyv /= dl;
            dzv /= dl;
            double drop = py - (c.groundY + 0.02);
            if (!(drop < 0.1)) {
               double len = drop / -dyv;
               double ex = px + dxv * len;
               double ey = py + dyv * len;
               double ez = pz + dzv * len;
               int col = this.palette[p.color[i]];
               this.sx[k] = px;
               this.sy[k] = py;
               this.sz[k] = pz;
               this.scol[k] = col;
               if (strength > 0.0F) {
                  Glass.shaft(cam, px, py, pz, ex, ey, ez, 0.1 * this.radius * 2.0, 0.55 * s, alphaOf(col, a * 0.8F * strength));
               }

               if (this.groundLight.get()) {
                  Glass.patch(ex, ey + 0.015, ez, 0.42F * (float)s, alphaOf(col, a * 0.5F * c.groundAlpha));
               }

               if (this.motes.get() && strength > 0.0F) {
                  for (int m = 0; m < 3; m++) {
                     double q = c.time * 0.08 * this.speed() + k * 0.37 + m * 0.33;
                     q -= Math.floor(q);
                     double side = Math.sin(c.time * 0.7 + m * 2.1 + k) * 0.06;
                     double mx = px + (ex - px) * q + this.rx * side;
                     double my = py + (ey - py) * q + Math.sin(c.time * 1.3 + m + k) * 0.02;
                     double mz = pz + (ez - pz) * q + this.rz * side;
                     float tw = (float)Math.sin(Math.PI * q);
                     Vfx.sprite(Textures.SPARK, mx, my, mz, 0.035F * (float)s, 0.0F, alphaOf(-3368, a * 0.75F * tw * strength));
                  }
               }
            }
         }

         this.dz = 0.0;
         this.dy = 0.0;
      }
   }

   private static int alphaOf(int color, float a) {
      return Math.round(255.0F * Easing.clamp01(a) * ((color >>> 24) / 255.0F)) << 24 | color & 16777215;
   }

   public static enum Pattern implements Displayable {
      ROSE("Rose", "Petals round a golden heart"),
      MANDALA("Mandala", "Rings of pointed stars"),
      LANCET("Lancet trio", "Three tall pointed windows");

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

   public static enum Position implements Displayable {
      HEAD("Behind head", "A halo behind the head and shoulders"),
      BODY("Behind body", "Larger, behind the whole body");

      private final String displayName;
      private final String description;

      private Position(String displayName, String description) {
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
