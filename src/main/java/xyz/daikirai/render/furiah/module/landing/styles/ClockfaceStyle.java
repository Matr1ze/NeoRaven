package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Clockwork;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
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
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ClockfaceStyle extends LandingStyleBase {
   private static final float SLAM = 0.12F;
   private static final float SWEPT = 0.72F;
   private static final float FOLDED = 1.12F;
   private static final float END = 1.42F;
   private static final float JUMP_SLAM = 0.08F;
   private static final float JUMP_TICK = 0.22F;
   private static final float JUMP_FOLDED = 0.5F;
   private static final int MAX_GEARS = 8;
   private static final double RADIUS = 0.95;
   private static final double MODULE = 0.034;
   private static final int[] TEETH = new int[]{12, 8, 14, 9, 16, 10, 12, 8};
   private static final float RIM = 0.16F;
   private static final int ENGRAVING = -15068148;
   private static final float DUST_WIND = 0.3F;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final ThemeTint tint;
   public final SliderSetting gears;
   public final ModeSetting<ClockfaceStyle.Numerals> numerals;
   public final BooleanSetting handSweep;
   public final SliderSetting size;
   public final ModeSetting<ClockfaceStyle.Motion> motion;
   private static final double[] E = new double[6];

   public ClockfaceStyle() {
      super(
         LandingStyle.CLOCKFACE,
         1.42F,
         1.8F,
         new ThemeTint()
      );
      this.material = this.materialSetting("clockface", Material.BRASS, "What the dial and the gears are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("clockface", Finish.METALLIC, this.material);
      this.tint = ThemeTint.detail(-3562674);
      this.gears = this.look(this.setting(new SliderSetting("clockface.gears", "Gears", "How many gears turn round the rim.", 3.0, 8.0, 5.0, 1.0)));
      this.numerals = this.setting(
         new ModeSetting<>("clockface.numerals", "Numerals", "What is engraved round the dial.", ClockfaceStyle.Numerals.class, ClockfaceStyle.Numerals.ROMAN)
            .style(ModeSetting.Style.CHIPS)
      );
      this.handSweep = this.setting(new BooleanSetting("clockface.handSweep", "Hand Sweep", "A hand sweeps once round the dial.", true));
      this.size = this.look(this.setting(new SliderSetting("clockface.size", "Size", "How big the dial is.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.motion = this.setting(
         new ModeSetting<>("clockface.motion", "Motion", "Ratchet ticks, or gliding round.", ClockfaceStyle.Motion.class, ClockfaceStyle.Motion.CLOCKWORK)
            .style(ModeSetting.Style.CHIPS)
      );
   }

   @Override
   public int passes(LandingFx fx) {
      int solid = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return solid | FxPass.GLOW.bit();
   }

   @Override
   public void start(LandingFx fx) {
      ClockfaceStyle.State s = new ClockfaceStyle.State();
      s.jump = fx.land.jump;
      s.radius = 0.95 * this.size.getFloat() / 100.0 * fx.size * (s.jump ? 0.45 : 1.0);
      s.module = s.radius * 0.034;
      s.y = fx.y() + 0.02;
      s.turn = fx.rand(1) * (Math.PI * 2);
      int n = s.jump ? 0 : this.gears.getInt() + Math.round(Math.max(0.0F, fx.land.fallScale - 1.0F) * 3.0F);
      s.gears = Math.max(0, Math.min(8, n));
      s.sweep = 0.6F / Math.max(1.0F, 0.85F + 0.5F * (fx.land.fallScale - 0.85F));
      double angle = s.turn;
      double prevR = 0.0;
      double prevX = 0.0;
      double prevZ = 0.0;

      for (int k = 0; k < s.gears; k++) {
         s.teeth[k] = TEETH[k % TEETH.length];
         s.tip[k] = Clockwork.tipRadius(s.module, s.teeth[k]);
         double r = s.radius + s.tip[k] * 0.55;
         if (k > 0) {
            double d = Clockwork.meshDistance(s.module, s.teeth[k - 1], s.teeth[k]);
            double c = (prevR * prevR + r * r - d * d) / (2.0 * prevR * r);
            angle += Math.acos(Math.max(-1.0, Math.min(1.0, c)));
         }

         s.gx[k] = Math.cos(angle) * r;
         s.gz[k] = Math.sin(angle) * r;
         if (k > 0) {
            s.towards[k] = Clockwork.angleOf(0.0, 1.0, 0.0, prevX - s.gx[k], 0.0, prevZ - s.gz[k]);
         }

         prevR = r;
         prevX = s.gx[k];
         prevZ = s.gz[k];
      }

      fx.data = s;
   }

   private static float slam(LandingFx fx, ClockfaceStyle.State s) {
      return Easing.clamp01(fx.styleTime() / (s.jump ? 0.08F : 0.12F));
   }

   private double hand(LandingFx fx, ClockfaceStyle.State s) {
      float t = fx.styleTime();
      boolean smooth = this.motion.get() == ClockfaceStyle.Motion.SMOOTH;
      if (s.jump) {
         double k = Easing.clamp01((t - 0.08F) / 0.14F);
         return Clockwork.ratchet(k, 1.0, Math.PI / 6, smooth);
      } else {
         double k = Easing.clamp01((t - 0.12F) / s.sweep);
         return Math.min(Math.PI * 2, Clockwork.ratchet(k * 12.0, 1.0, Math.PI / 6, smooth));
      }
   }

   private double driver(LandingFx fx, ClockfaceStyle.State s) {
      float t = Math.max(0.0F, fx.styleTime() - 0.06F);
      return this.motion.get() == ClockfaceStyle.Motion.SMOOTH ? 5.0 * t - 1.2 * t * t : Clockwork.ratchet(t, 0.1, (Math.PI * 2) / s.teeth[0] * 1.5, false);
   }

   private static float folded(LandingFx fx, ClockfaceStyle.State s, int k) {
      float from = (s.jump ? 0.22F : 0.72F) + k * 0.1F;
      float len = s.jump ? 0.15F : 0.22F;
      return Easing.inCubic(Easing.clamp01((fx.styleTime() - from) / len));
   }

   private static float rolled(LandingFx fx) {
      return Easing.clamp01((fx.styleTime() - 1.12F) / 0.29999995F);
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      ClockfaceStyle.State s = (ClockfaceStyle.State)fx.data;
      if (s != null) {
         Camera cam = Vfx.camera();
         double cx = f.rx(fx.x());
         double cy = f.ry(s.y);
         double cz = f.rz(fx.z());
         float alpha = fx.alpha() * NearCamera.fade(cam, cx, cy + 0.3, cz);
         if (pass == FxPass.GLOW) {
            this.glow(fx, f, s, cx, cy, cz, alpha);
         } else {
            boolean custom = this.material.get() == Material.CUSTOM;
            int col = custom ? this.tint.getColor(0.5F) : -1;
            int metal = ColorUtil.withAlpha(col, alpha * (custom ? (col >>> 24) / 255.0F : 1.0F));
            float sl = slam(fx, s);
            double grow = 1.0 + 0.3 * (1.0 - Easing.outCubic(sl));
            double drop = (1.0 - Easing.outCubic(sl)) * 0.5;
            double r = s.radius * grow;
            MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.16F, -1, true, pass == FxPass.SOLID);
            this.dial(fx, s, cx, cy + drop, cz, r, metal);
            this.gearsMesh(fx, f, s, cx, cy + drop, cz, metal, grow);
            MeshBatch.end();
            this.engraving(fx, s, cx, cy + drop, cz, r, alpha);
            this.dust(fx, s);
         }
      }
   }

   private void dial(LandingFx fx, ClockfaceStyle.State s, double cx, double cy, double cz, double r, int metal) {
      Clockwork.across(0.0, 1.0, 0.0, E);
      double ex = E[0];
      double ey = E[1];
      double ez = E[2];
      double fx2 = E[3];
      double fy2 = E[4];
      double fz2 = E[5];
      double t = r * 0.07;
      float f0 = folded(fx, s, 0);
      float f1 = folded(fx, s, 1);
      float f2 = folded(fx, s, 2);
      if (f0 < 1.0F) {
         ringPart(cx, cy + t * 0.5, cz, r * 0.86, r, t, f0, s.turn, metal, 0.1F, ex, ey, ez, fx2, fy2, fz2);
      }

      if (f1 < 1.0F) {
         ringPart(cx, cy + t * 0.2, cz, r * 0.05, r * 0.88, t * 0.4, f1, s.turn + 2.1, metal, 0.05F, ex, ey, ez, fx2, fy2, fz2);
      }

      if (f2 < 1.0F) {
         ringPart(cx, cy + t * 0.55, cz, r * 0.38, r * 0.46, t * 0.6, f2, s.turn + 4.2, metal, 0.12F, ex, ey, ez, fx2, fy2, fz2);
      }

      if (this.handSweep.get() && f1 < 0.5F) {
         double a = s.turn + this.hand(fx, s);
         double hx = Math.cos(a);
         double hz = Math.sin(a);
         double sink = f1 * t * 4.0;
         Clockwork.hand(cx, cy + t * 0.6 - sink, cz, 0.0, 1.0, 0.0, hx, 0.0, hz, r * 0.72, r * 0.12, r * 0.03, metal, 0.3F);
      }
   }

   private static void ringPart(
      double cx,
      double cy,
      double cz,
      double inner,
      double outer,
      double t,
      float fold,
      double axis,
      int col,
      float glint,
      double ex,
      double ey,
      double ez,
      double fx,
      double fy,
      double fz
   ) {
      if (fold <= 0.0F) {
         Clockwork.ring(cx, cy, cz, 0.0, 1.0, 0.0, ex, ey, ez, fx, fy, fz, inner, outer, t, 48, col, glint);
      } else {
         double tip = fold * 0.5;
         double kx = Math.cos(axis);
         double kz = Math.sin(axis);
         double c = Math.cos(tip);
         double sn = Math.sin(tip);
         double ax = -kz * sn;
         double az = kx * sn;
         Clockwork.across(ax, c, az, E);
         double sink = fold * (outer * 0.6 + t);
         Clockwork.ring(cx, cy - sink, cz, ax, c, az, E[0], E[1], E[2], E[3], E[4], E[5], inner, outer, t, 48, col, glint);
      }
   }

   private void gearsMesh(LandingFx fx, FxFrame f, ClockfaceStyle.State s, double cx, double cy, double cz, int metal, double grow) {
      if (s.gears != 0) {
         float roll = rolled(fx);
         int col = ColorUtil.mulAlpha(metal, 1.0F - Easing.smoothstep(0.35F, 1.0F, roll));
         if (col >>> 24 != 0) {
            double turn;
            double prev = turn = this.driver(fx, s);

            for (int k = 0; k < s.gears; k++) {
               double gt;
               prev = gt = k == 0 ? turn : Clockwork.meshed(prev, s.teeth[k - 1], s.teeth[k], s.towards[k]);
               double out = Easing.inCubic(roll) * 1.2 * s.radius;
               double ox = s.gx[k];
               double oz = s.gz[k];
               double ol = Math.sqrt(ox * ox + oz * oz) + 1.0E-9;
               double x = cx + ox * grow + ox / ol * out;
               double z = cz + oz * grow + oz / ol * out;
               double thick = s.tip[k] * 0.22;
               Clockwork.gear(
                  x, cy + thick * 0.5 + 0.004, z, 0.0, 1.0, 0.0, s.tip[k] * grow, thick, gt - out / s.tip[k], s.teeth[k], s.teeth[k] >= 12 ? 4 : 0, col, 0.2F
               );
            }
         }
      }
   }

   private void engraving(LandingFx fx, ClockfaceStyle.State s, double cx, double cy, double cz, double r, float alpha) {
      float f1 = folded(fx, s, 1);
      if (!(f1 > 0.02F) && this.numerals.get() != ClockfaceStyle.Numerals.NONE) {
         float a = 0.8F * alpha * Easing.clamp01(slam(fx, s) * 2.0F);
         if (!(a <= 0.003F)) {
            int dark = ColorUtil.withAlpha(-15068148, a);
            double lift = r * 0.07 * 0.4 + 0.003;
            RenderState.decal(true);
            RenderState.translucent(false);

            try {
               Clockwork.ticks(cx, cy, cz, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, lift, 0.0, r * 0.72, r * 0.18, s.turn, dark, null);
               if (this.numerals.get() == ClockfaceStyle.Numerals.ROMAN) {
                  Clockwork.glyphs(cx, cy, cz, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, lift, 0.0, r * 0.56, r * 0.2, s.turn, Math.PI / 6, 1, true, dark, null);
               }
            } finally {
               RenderState.decal(false);
               RenderState.translucent(true);
            }
         }
      }
   }

   private void dust(LandingFx fx, ClockfaceStyle.State s) {
      if (!s.dusted && !fx.lod && !(slam(fx, s) < 1.0F)) {
         s.dusted = true;
         int n = fx.count(s.jump ? 5.0F : 12.0F);

         for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2) * i / n + fx.rand(70 + i) * 0.5;
            double r = s.radius * 1.02;
            float v = 0.6F + fx.rand(90 + i) * 0.6F;
            fx.particles
               .spawn(
                  8,
                  fx.x() + Math.cos(ang) * r,
                  fx.y() + 0.05,
                  fx.z() + Math.sin(ang) * r,
                  (float)Math.cos(ang) * v,
                  0.2F + fx.rand(110 + i) * 0.25F,
                  (float)Math.sin(ang) * v,
                  fx.seconds(0.7F),
                  0.28F * fx.size
               )
               .end(0.6F)
               .drag(2.4F)
               .color(ColorUtil.lerp(fx.land.blockColor, -4675448, 0.5F))
               .alpha(0.5F * fx.alpha())
               .wind(0.3F);
         }
      }
   }

   private void glow(LandingFx fx, FxFrame f, ClockfaceStyle.State s, double cx, double cy, double cz, float alpha) {
      int warm = this.color.getColor(0.5F);
      float t = fx.styleTime();
      float flash = (float)Math.exp(-Math.abs(t - (s.jump ? 0.08F : 0.12F)) * 16.0) * Easing.clamp01(slam(fx, s) * 3.0F);
      if (flash > 0.01F) {
         Vfx.sprite(Textures.GLOW, cx, cy + 0.1, cz, s.radius * 3.0, 0.0F, alpha(warm, 0.35F * flash * alpha));
      }

      if (!(folded(fx, s, 1) > 0.02F)) {
         double r = s.radius;
         double a = s.turn + this.hand(fx, s);
         if (this.numerals.get() != ClockfaceStyle.Numerals.NONE) {
            float done = (float)(this.hand(fx, s) / (Math.PI * 2));

            for (int h = 0; h < 12; h++) {
               double ha = s.turn + (Math.PI * 2) * h / 12.0;
               float passed = done * 12.0F - h;
               float k = passed < 0.0F ? 0.0F : (float)Math.exp(-passed * 0.5);
               if (!(k <= 0.02F)) {
                  double x = cx + Math.cos(ha) * r * 0.56;
                  double z = cz + Math.sin(ha) * r * 0.56;
                  Vfx.sprite(Textures.GLOW, x, cy + 0.03, z, r * 0.3, 0.0F, alpha(warm, 0.45F * k * alpha));
               }
            }
         }

         if (this.handSweep.get()) {
            double x = cx + Math.cos(a) * r * 0.7;
            double z = cz + Math.sin(a) * r * 0.7;
            Vfx.sprite(Textures.GLOW, x, cy + 0.05, z, r * 0.22, 0.0F, alpha(warm, 0.5F * alpha));
         }
      }
   }

   public static enum Motion implements Displayable {
      CLOCKWORK("Clockwork", "In ratchet ticks"),
      SMOOTH("Smooth", "Gliding round");

      private final String displayName;
      private final String description;

      private Motion(String displayName, String description) {
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

   public static enum Numerals implements Displayable {
      ROMAN("Roman", "I to XII round the dial"),
      TICKS("Ticks", "Tick marks only"),
      NONE("None", "A plain dial");

      private final String displayName;
      private final String description;

      private Numerals(String displayName, String description) {
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
      boolean dusted;
      double radius;
      double module;
      double y;
      double turn;
      int gears;
      final double[] gx = new double[8];
      final double[] gz = new double[8];
      final double[] tip = new double[8];
      final double[] towards = new double[8];
      final int[] teeth = new int[8];
      float sweep;

      private State() {
      }
   }
}
