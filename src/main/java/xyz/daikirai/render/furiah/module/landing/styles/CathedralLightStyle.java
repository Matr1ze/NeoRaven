package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Glass;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class CathedralLightStyle extends LandingStyleBase {
   private static final float LIT = 0.25F;
   private static final float FADE_FROM = 1.0F;
   private static final float END = 1.6F;
   private static final double RADIUS = 1.35;
   private static final double COLUMN = 3.2;
   private static final double TURN = 0.3;
   private static final double INSET = 0.9;
   private static final int MAX_POINTS = 64;
   private static final int SUB = 3;
   private static final float MOTE_WIND = 0.25F;
   public final ThemeTint lead;
   public final ModeSetting<Glass.GlassColors> glassColors;
   public final ThemeTint glass1;
   public final ThemeTint glass2;
   public final ThemeTint glass3;
   public final ModeSetting<CathedralLightStyle.Pattern> pattern;
   public final SliderSetting size;
   public final BooleanSetting column;
   public final BooleanSetting motes;
   public final SliderSetting brightness;
   private final Glass.Pane pane = new Glass.Pane();
   private final double[] tu = new double[64];
   private final double[] tv = new double[64];
   private final int[] palette = new int[4];
   private final double[] center = new double[2];

   public CathedralLightStyle() {
      super(
         LandingStyle.CATHEDRAL_LIGHT,
         1.6F,
         2.3F,
         new ThemeTint()
      );
      this.lead = ThemeTint.detail(-15066594);
      this.glassColors = this.setting(
         new ModeSetting<>(
               "cathedrallight.glassColors",
               "Glass Colors",
               "Cathedral: ruby, sapphire, emerald and gold. Tinted: the three glass colors.",
               Glass.GlassColors.class,
               Glass.GlassColors.CATHEDRAL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      BooleanSupplier tinted = new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return CathedralLightStyle.this.glassColors.get() == Glass.GlassColors.TINTED;
         }
      };
      this.glass1 = ThemeTint.detail(-13935399);
      this.glass2 = ThemeTint.detail(-4184513);
      this.glass3 = ThemeTint.detail(-868018);
      this.pattern = this.setting(
         new ModeSetting<>(
               "cathedrallight.pattern", "Pattern", "The window the light falls through.", CathedralLightStyle.Pattern.class, CathedralLightStyle.Pattern.ROSE
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.size = this.look(this.setting(new SliderSetting("cathedrallight.size", "Size", "How big the pool of light is.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.column = this.setting(new BooleanSetting("cathedrallight.column", "Light Column", "A soft column of light rises from the pattern.", true));
      this.motes = this.setting(new BooleanSetting("cathedrallight.motes", "Motes", "Dust motes drift slowly up in the light.", true));
      this.brightness = this.look(
         this.setting(new SliderSetting("cathedrallight.brightness", "Brightness", "How bright the light is.", 0.0, 100.0, 70.0, 5.0).unit("%"))
      );
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void start(LandingFx fx) {
      CathedralLightStyle.State s = new CathedralLightStyle.State();
      s.jump = fx.land.jump;
      s.radius = 1.35 * this.size.getFloat() / 100.0 * fx.size * (s.jump ? 0.6 : 1.0);
      s.height = 3.2 * Math.sqrt(Math.max(0.6F, fx.land.fallScale)) * this.size.getFloat() / 100.0;
      s.bright = this.brightness.getFloat() / 100.0F * (0.85F + 0.15F * fx.land.fallScale);
      s.yaw = Math.toRadians(fx.land.yaw);
      fx.data = s;
   }

   private static Glass.Cells cells(CathedralLightStyle.Pattern p) {
      switch (p) {
         case MANDALA:
            return Glass.mandala();
         case LANCET:
            return Glass.lancet();
         default:
            return Glass.rose();
      }
   }

   private static float lit(LandingFx fx, Glass.Cells c, int i) {
      double appear = 0.25 * Math.min(1.0, c.rad[i] / 0.875);
      return Easing.clamp01((float)((fx.styleTime() - appear) / 0.07));
   }

   private static float fade(LandingFx fx) {
      return 1.0F - Easing.smoothstep(1.0F, 1.6F, fx.styleTime());
   }

   private Glass.Pane pane(LandingFx fx, FxFrame f, CathedralLightStyle.State s, double lift) {
      double cy = Math.cos(s.yaw);
      double sy = Math.sin(s.yaw);
      this.pane.ox = f.rx(fx.x());
      this.pane.oy = f.ry(fx.y());
      this.pane.oz = f.rz(fx.z());
      this.pane.rx = cy;
      this.pane.ry = 0.0;
      this.pane.rz = sy;
      this.pane.ux = -sy;
      this.pane.uy = 0.0;
      this.pane.uz = cy;
      this.pane.nx = 0.0;
      this.pane.ny = 1.0;
      this.pane.nz = 0.0;
      this.pane.scale = s.radius;
      this.pane.offX = 0.0;
      this.pane.offY = 0.0;
      this.pane.offZ = 0.0;
      this.pane.onGround(fx.ground, f.viewX, f.viewY, f.viewZ, lift);
      return this.pane;
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      CathedralLightStyle.State s = (CathedralLightStyle.State)fx.data;
      if (s != null) {
         float a = fx.alpha() * fade(fx);
         if (!(a <= 0.003F)) {
            if (this.glassColors.get() == Glass.GlassColors.TINTED) {
               Glass.tinted(this.glass1.getColor(0.5F), this.glass2.getColor(0.5F), this.glass3.getColor(0.5F), this.palette);
            } else {
               System.arraycopy(Glass.CATHEDRAL_SET, 0, this.palette, 0, 4);
            }

            Glass.Cells c = cells(this.pattern.get());
            double turn = fx.styleTime() * 0.3;
            if (pass == FxPass.TRANSLUCENT) {
               this.shadows(fx, f, s, c, turn, a);
            } else {
               this.light(fx, f, s, c, turn, a);
               if (this.column.get() && !s.jump) {
                  this.column(fx, f, s, a);
               }

               if (this.motes.get() && !s.jump && !fx.lod) {
                  this.motes(fx, f, s);
               }
            }
         }
      }
   }

   private int cell(Glass.Cells c, int i, double turn, double scale, double[] center) {
      double ct = c.turns[i] ? Math.cos(turn) : 1.0;
      double st = c.turns[i] ? Math.sin(turn) : 0.0;
      double cu = c.cu[i] * ct - c.cv[i] * st;
      double cv = c.cu[i] * st + c.cv[i] * ct;
      center[0] = cu;
      center[1] = cv;
      return Glass.shard(c, i, c.turns[i] ? turn : 0.0, scale, cu, cv, this.tu, this.tv);
   }

   private boolean grounded(LandingFx fx, CathedralLightStyle.State s, double u, double v) {
      double cy = Math.cos(s.yaw);
      double sy = Math.sin(s.yaw);
      double x = fx.x() + (cy * u - sy * v) * s.radius;
      double z = fx.z() + (sy * u + cy * v) * s.radius;
      Ground.y(fx.ground, x, z);
      return Ground.lastValid();
   }

   private void shadows(LandingFx fx, FxFrame f, CathedralLightStyle.State s, Glass.Cells c, double turn, float a) {
      int shade = ColorUtil.shade(this.lead.getColor(0.5F), Math.max(0.5F, fx.light)) | 0xFF000000;
      float k = a * 0.55F * (0.6F + 0.4F * s.bright);
      Glass.Pane p = this.pane(fx, f, s, 0.008);
      RenderState.decal(true);
      RenderState.translucent(false);

      try {
         float ring = Easing.clamp01((fx.styleTime() - 0.25F) / 0.1F);
         int sub = fx.ground.isFlat() ? 1 : 3;
         if (c.stone) {
            this.ringLead(fx, s, p, 0.0, 0.655, alpha(shade, k * 0.7F * ring));
         }

         for (int i = 0; i < c.n; i++) {
            float l = lit(fx, c, i);
            if (!(l <= 0.01F)) {
               int n2 = this.cell(c, i, turn, 1.0, this.center);
               if (this.grounded(fx, s, this.center[0], this.center[1])) {
                  lead(p, this.tu, this.tv, n2, 0.044800000000000006, alpha(shade, k * l), sub);
               }
            }
         }

         this.ringLead(fx, s, p, 0.655, 0.705, alpha(shade, k * ring));
         this.ringLead(fx, s, p, 0.875, 1.0, alpha(shade, k * 0.8F * ring));
      } finally {
         RenderState.decal(false);
         p.onGround(null, 0.0, 0.0, 0.0, 0.0);
      }
   }

   private void light(LandingFx fx, FxFrame f, CathedralLightStyle.State s, Glass.Cells c, double turn, float a) {
      float k = a * s.bright * 0.85F;
      if (!(k <= 0.003F)) {
         Glass.Pane p = this.pane(fx, f, s, 0.012);
         int sub = fx.ground.isFlat() ? 1 : 3;
         RenderState.decal(true);
         RenderState.additive();

         try {
            for (int i = 0; i < c.n; i++) {
               float l = lit(fx, c, i);
               if (!(l <= 0.01F)) {
                  int n = this.cell(c, i, turn, 0.9, this.center);
                  if (this.grounded(fx, s, this.center[0], this.center[1])) {
                     int base = ColorUtil.shade(this.palette[c.color[i]], 0.9F + 0.2F * hash(i, 13));
                     float flash = (1.0F - l) * 0.6F + 0.15F * (1.0F - Easing.clamp01((fx.styleTime() - 0.25F * (float)(c.rad[i] / 0.875)) / 0.2F));
                     int mid = alpha(ColorUtil.lerp(base, -1, 0.3F + flash), k * l);
                     int edge = alpha(base, k * l * 0.75F);
                     fan(p, this.tu, this.tv, n, this.center[0], this.center[1], mid, edge, sub);
                  }
               }
            }
         } finally {
            RenderState.decal(false);
            p.onGround(null, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   private static void fan(Glass.Pane p, double[] u, double[] v, int n, double cu, double cv, int mid, int edge, int sub) {
      if (sub <= 1) {
         Glass.glass(p, u, v, n, cu, cv, mid, edge);
      } else {
         int tex = Glass.texture();

         for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            double au = u[k] - cu;
            double av = v[k] - cv;
            double bu = u[k2] - u[k];
            double bv = v[k2] - v[k];

            for (int i = 0; i < sub; i++) {
               for (int j = 0; j <= i; j++) {
                  tri(p, tex, cu, cv, au, av, bu, bv, sub, mid, edge, i, j, i + 1, j, i + 1, j + 1);
                  if (j < i) {
                     tri(p, tex, cu, cv, au, av, bu, bv, sub, mid, edge, i, j, i + 1, j + 1, i, j + 1);
                  }
               }
            }
         }
      }
   }

   private static void tri(
      Glass.Pane p,
      int tex,
      double cu,
      double cv,
      double au,
      double av,
      double bu,
      double bv,
      int sub,
      int mid,
      int edge,
      int i0,
      int j0,
      int i1,
      int j1,
      int i2,
      int j2
   ) {
      double u0 = cu + (au * i0 + bu * j0) / sub;
      double v0 = cv + (av * i0 + bv * j0) / sub;
      double u1 = cu + (au * i1 + bu * j1) / sub;
      double v1 = cv + (av * i1 + bv * j1) / sub;
      double u2 = cu + (au * i2 + bu * j2) / sub;
      double v2 = cv + (av * i2 + bv * j2) / sub;
      int c0 = ColorUtil.lerp(mid, edge, (float)i0 / sub);
      int c1 = ColorUtil.lerp(mid, edge, (float)i1 / sub);
      int c2 = ColorUtil.lerp(mid, edge, (float)i2 / sub);
      Vfx.quadUV(
         tex,
         p.x(u0, v0),
         p.y(u0, v0),
         p.z(u0, v0),
         u0 * 1.6,
         v0 * 1.6,
         c0,
         p.x(u1, v1),
         p.y(u1, v1),
         p.z(u1, v1),
         u1 * 1.6,
         v1 * 1.6,
         c1,
         p.x(u2, v2),
         p.y(u2, v2),
         p.z(u2, v2),
         u2 * 1.6,
         v2 * 1.6,
         c2,
         p.x(u2, v2),
         p.y(u2, v2),
         p.z(u2, v2),
         u2 * 1.6,
         v2 * 1.6,
         c2
      );
   }

   private static void lead(Glass.Pane p, double[] u, double[] v, int n, double width, int col, int sub) {
      if (sub <= 1) {
         Glass.lead(p, u, v, n, width, col);
      } else {
         double hw = width * 0.5;

         for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            double eu = u[k2] - u[k];
            double ev = v[k2] - v[k];
            double el = Math.sqrt(eu * eu + ev * ev);
            if (!(el < 1.0E-6)) {
               double var43;
               double pu = -(var43 = ev / el) * hw;
               double var42;
               double pv = (var42 = eu / el) * hw;
               double su = u[k] - var42 * hw;
               double sv = v[k] - var43 * hw;
               double len = el + 2.0 * hw;
               int pieces = Math.max(1, (int)Math.ceil(len * p.scale / 0.12));

               for (int q = 0; q < pieces; q++) {
                  double a0 = len * q / pieces;
                  double a1 = len * (q + 1) / pieces;
                  double u0 = su + var42 * a0;
                  double v0 = sv + var43 * a0;
                  double u1 = su + var42 * a1;
                  double v1 = sv + var43 * a1;
                  Vfx.quadUV(
                     Textures.WHITE,
                     p.x(u0 - pu, v0 - pv),
                     p.y(u0 - pu, v0 - pv),
                     p.z(u0 - pu, v0 - pv),
                     0.5,
                     0.5,
                     col,
                     p.x(u1 - pu, v1 - pv),
                     p.y(u1 - pu, v1 - pv),
                     p.z(u1 - pu, v1 - pv),
                     0.5,
                     0.5,
                     col,
                     p.x(u1 + pu, v1 + pv),
                     p.y(u1 + pu, v1 + pv),
                     p.z(u1 + pu, v1 + pv),
                     0.5,
                     0.5,
                     col,
                     p.x(u0 + pu, v0 + pv),
                     p.y(u0 + pu, v0 + pv),
                     p.z(u0 + pu, v0 + pv),
                     0.5,
                     0.5,
                     col
                  );
               }
            }
         }
      }
   }

   private void ringLead(LandingFx fx, CathedralLightStyle.State s, Glass.Pane p, double r0, double r1, int col) {
      if (col >>> 24 != 0) {
         double rm = (r0 + r1) * 0.5;

         for (int i = 0; i < 48; i++) {
            double c0 = Glass.RING_COS[i];
            double c1 = Glass.RING_COS[i + 1];
            double cm = (c0 + c1) * 0.5;
            double s0 = Glass.RING_SIN[i];
            double s1 = Glass.RING_SIN[i + 1];
            double sm = (s0 + s1) * 0.5;
            if (this.grounded(fx, s, cm * rm, sm * rm)) {
               Vfx.quadUV(
                  Textures.WHITE,
                  p.x(c0 * r0, s0 * r0),
                  p.y(c0 * r0, s0 * r0),
                  p.z(c0 * r0, s0 * r0),
                  0.5,
                  0.5,
                  col,
                  p.x(c1 * r0, s1 * r0),
                  p.y(c1 * r0, s1 * r0),
                  p.z(c1 * r0, s1 * r0),
                  0.5,
                  0.5,
                  col,
                  p.x(c1 * r1, s1 * r1),
                  p.y(c1 * r1, s1 * r1),
                  p.z(c1 * r1, s1 * r1),
                  0.5,
                  0.5,
                  col,
                  p.x(c0 * r1, s0 * r1),
                  p.y(c0 * r1, s0 * r1),
                  p.z(c0 * r1, s0 * r1),
                  0.5,
                  0.5,
                  col
               );
            }
         }
      }
   }

   private void column(LandingFx fx, FxFrame f, CathedralLightStyle.State s, float a) {
      float on = Easing.smoothstep(0.12F, 0.35F, fx.styleTime());
      Camera cam = Vfx.camera();
      double x = f.rx(fx.x());
      double z = f.rz(fx.z());
      double y0 = f.ry(fx.y() + 0.02);
      double y1 = f.ry(fx.y() + s.height);
      double dx = cam.posX - x;
      double dz = cam.posZ - z;
      float near = Easing.smoothstep(0.7F, 1.8F, (float)Math.sqrt(dx * dx + dz * dz));
      float k = a * on * near * s.bright;
      if (!(k <= 0.003F)) {
         int light = this.color.getColor(0.5F) | 0xFF000000;
         RenderState.additive();
         Glass.shaft(cam, x, y0, z, x, y1, z, s.radius * 1.7, s.radius * 2.1, alpha(light, 0.3F * k));
         Glass.shaft(cam, x, y0, z, x, y1, z, s.radius * 0.9, s.radius * 1.1, alpha(light, 0.42F * k));
      }
   }

   private void motes(LandingFx fx, FxFrame f, CathedralLightStyle.State s) {
      float t = fx.styleTime();
      if (!(t > 1.0F) && !(f.dt <= 0.0F)) {
         s.moteDebt = s.moteDebt + f.dt * 22.0F * Math.min(2.0F, fx.density) * (float)(s.radius / 1.35);
         int light = this.color.getColor(0.5F) | 0xFF000000;

         while (s.moteDebt >= 1.0F) {
            s.moteDebt--;
            int i = s.motes++;
            double ang = fx.rand(1000 + i % 400) * (Math.PI * 2);
            double d = Math.sqrt(fx.rand(1400 + i % 400)) * s.radius * 0.8;
            double x = fx.x() + Math.cos(ang) * d;
            double z = fx.z() + Math.sin(ang) * d;
            double y = fx.y() + 0.1 + fx.rand(1800 + i % 400) * s.height * (this.column.get() ? 0.75 : 0.25);
            int col = i % 3 == 0 ? light : this.palette[i % 4];
            fx.particles
               .spawn(
                  3,
                  x,
                  y,
                  z,
                  (fx.rand(2200 + i % 400) - 0.5F) * 0.08F,
                  0.12F + fx.rand(2600 + i % 400) * 0.18F,
                  (fx.rand(3000 + i % 400) - 0.5F) * 0.08F,
                  fx.seconds(0.9F + fx.rand(3400 + i % 400) * 0.6F),
                  0.035F + fx.rand(3800 + i % 400) * 0.03F
               )
               .end(0.01F)
               .drag(0.6F)
               .color(col)
               .alpha(fx.alpha() * s.bright)
               .flags(2)
               .sway(0.06F, fx.rand(4200 + i % 400))
               .wind(0.25F);
         }
      }
   }

   private static float hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8) / 1.6777216E7F;
   }

   public static enum Pattern implements Displayable {
      ROSE("Rose", "A rose window's petals and rings"),
      MANDALA("Mandala", "Rings of pointed cells"),
      LANCET("Lancet trio", "Three tall lancets in a circle of stone");

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

   private static final class State {
      boolean jump;
      double radius;
      double height;
      double yaw;
      float bright;
      float moteDebt;
      int motes;

      private State() {
      }
   }
}
