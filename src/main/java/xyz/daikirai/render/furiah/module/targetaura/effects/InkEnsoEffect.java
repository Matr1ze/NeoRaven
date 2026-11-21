package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Ink;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.stroke.Strokes;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class InkEnsoEffect extends AuraEffect {
   private static final int POINTS = 120;
   private static final int NIGHT = -3549953;
   private static final double START = Math.toRadians(238.0);
   private static final double SEAL_AT = Math.toRadians(-38.0);
   public final ThemeTint sealColor;
   public final ModeSetting<InkEnsoEffect.Placement> placement;
   public final SliderSetting brushWidth;
   public final SliderSetting paintSpeed;
   public final SliderSetting dryness;
   public final SliderSetting gap;
   public final BooleanSetting seal;
   public final ModeSetting<Ink.SealDesign> sealDesign;
   public final SliderSetting splatter;
   private final Strokes.Style style = new Strokes.Style();
   private double ox;
   private double oy;
   private double oz;
   private double ax;
   private double ay;
   private double az;
   private double bx;
   private double by;
   private double bz;
   private double radius;

   public InkEnsoEffect() {
      super(
         EffectType.INK_ENSO,
         "inkenso",
         ThemeTint.detail(-15461356),
         true
      );
      this.sealColor = ThemeTint.detail(-3660241);
      this.placement = this.setting(
         new ModeSetting<>(
            "inkenso.placement", "Placement", "Round the body facing you, or flat on the ground.", InkEnsoEffect.Placement.class, InkEnsoEffect.Placement.BODY
         )
      );
      this.brushWidth = this.look(
         this.setting(new SliderSetting("inkenso.width", "Brush Width", "How wide the brush is at its heaviest.", 0.05, 0.4, 0.18, 0.01).unit(" blocks"))
      );
      this.paintSpeed = this.look(
         this.setting(new SliderSetting("inkenso.paintSpeed", "Paint Speed", "How fast the circle is painted.", 50.0, 200.0, 100.0, 5.0).unit("%"))
      );
      this.dryness = this.setting(
         new SliderSetting("inkenso.dryness", "Dryness", "How soon the brush runs dry into bristle streaks.", 0.0, 100.0, 50.0, 5.0).unit("%")
      );
      this.gap = this.setting(new SliderSetting("inkenso.gap", "Gap", "Where the circle doesn't close.", 0.0, 60.0, 25.0, 1.0).unit("°"));
      this.seal = this.setting(new BooleanSetting("inkenso.sealOn", "Seal", "A red seal stamp at the circle's lower right.", true));
      this.sealDesign = this.setting(
         new ModeSetting<>("inkenso.sealDesign", "Seal Design", "The seal's design.", Ink.SealDesign.class, Ink.SealDesign.SUN)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return InkEnsoEffect.this.seal.get();
               }
            })
      );
      this.splatter = this.look(
         this.setting(
            new SliderSetting("inkenso.splatter", "Splatter", "Ink splatters round the seal and where the brush landed.", 0.0, 100.0, 40.0, 5.0).unit("%")
         )
      );
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.TRANSLUCENT) {
         float alpha = this.intensity(c);
         if (alpha <= 0.003F) {
            return;
         }

         this.plane(c);
         float painted = this.painted(c);
         this.stroke(c, alpha, painted);
         this.stamp(c, alpha, painted);
      } else if (pass == FxPass.GLOW && c.night > 0.01F) {
         this.plane(c);
         this.nightLight(c, this.painted(c));
      }
   }

   private float painted(EffectContext c) {
      float f = Easing.clamp01((float)(c.time / (0.45 / Math.max(0.25, this.paintSpeed.getFloat() / 100.0))));
      return f < 0.3F ? 0.31F * Easing.inQuad(f / 0.3F) : 0.31F + 0.69F * Easing.outCubic((f - 0.3F) / 0.7F);
   }

   private void plane(EffectContext c) {
      double sz = this.size(c);
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      if (this.placement.get() == InkEnsoEffect.Placement.GROUND) {
         this.ox = c.x;
         this.oy = c.groundY + 0.02;
         this.oz = c.z;
         double rxv = cam.rightX;
         double rzv = cam.rightZ;
         double rl = Math.sqrt(rxv * rxv + rzv * rzv);
         if (rl < 1.0E-6) {
            rxv = 1.0;
            rzv = 0.0;
            rl = 1.0;
         }

         this.ax = rxv / rl;
         this.ay = 0.0;
         this.az = rzv / rl;
         this.bx = -this.az;
         this.by = 0.0;
         this.bz = this.ax;
         double fx = cam.forwardX;
         double fz = cam.forwardZ;
         if (this.bx * fx + this.bz * fz < 0.0) {
            this.bx = -this.bx;
            this.bz = -this.bz;
         }

         this.radius = 0.85 * (0.8 + 0.2 * sz);
      } else {
         double fx = cam.forwardX;
         double fz = cam.forwardZ;
         double fl = Math.sqrt(fx * fx + fz * fz);
         double back = fl < 1.0E-6 ? 0.0 : 0.45 / fl;
         this.ox = c.x + fx * back;
         this.oy = c.y + c.height * 0.56;
         this.oz = c.z + fz * back;
         this.ax = cam.rightX;
         this.ay = cam.rightY;
         this.az = cam.rightZ;
         this.bx = cam.upX;
         this.by = cam.upY;
         this.bz = cam.upZ;
         this.radius = 0.97 * (0.8 + 0.2 * sz);
      }
   }

   private void at(int i, double a, double r) {
      double ca = Math.cos(a);
      double sa = Math.sin(a);
      Strokes.X[i] = this.ox + (this.ax * ca + this.bx * sa) * r;
      Strokes.Y[i] = this.oy + (this.ay * ca + this.by * sa) * r;
      Strokes.Z[i] = this.oz + (this.az * ca + this.bz * sa) * r;
   }

   private void stroke(EffectContext c, float alpha, float painted) {
      double sweep = (Math.PI * 2) - Math.toRadians(this.gap.getFloat());

      for (int i = 0; i < 120; i++) {
         double u = i / 119.0;
         double a = START - sweep * u;
         double r = this.radius * (0.97 + 0.06 * u + 0.018 * Math.sin(a * 3.0 + 0.7) + 0.01 * Math.sin(a * 7.0 + 1.9));
         this.at(i, a, r);
      }

      this.style.reset();
      this.style.tool = Strokes.Tool.BRUSH;
      this.style.color = this.color(c, 0.5F);
      this.style.width = this.brushWidth.getFloat() * this.size(c) * (1.0 + 0.02 * Math.sin(c.time * 1.3));
      this.style.alpha = alpha;
      this.style.progress = painted;
      this.style.heavyStart = 1.0F;
      this.style.dryness = this.dryness.getFloat() / 100.0F;
      this.style.thinEnd = 0.45F + 0.3F * this.style.dryness;
      this.style.diffuse = Easing.clamp01(c.outro);
      this.style.light = c.light;
      if (this.placement.get() == InkEnsoEffect.Placement.GROUND) {
         this.style.flat = true;
         this.style.normalX = 0.0;
         this.style.normalY = 1.0;
         this.style.normalZ = 0.0;
      }

      Strokes.draw(120, this.style);
   }

   private void stamp(EffectContext c, float alpha, float painted) {
      double t = c.time;
      double sz = this.size(c);
      double paintTime = 0.45 / Math.max(0.25, this.paintSpeed.getFloat() / 100.0);
      float fade = 1.0F - Easing.smoothstep(0.0F, 0.8F, c.outro);
      int ink = ColorUtil.shade(this.color(c, 0.5F), c.light);
      int splats = Math.round(this.splatter.getFloat() / 100.0F * 7.0F);
      if (splats > 0) {
         int tex = Ink.splats();

         for (int k = 0; k < splats; k++) {
            boolean nearSeal = (k & 1) == 1;
            double when = nearSeal ? paintTime : 0.02;
            float show = Easing.smoothstep(0.0F, 0.06F, (float)(t - when));
            if (!(show <= 0.0F)) {
               double a = nearSeal ? SEAL_AT + (hash(k, 3) - 0.5) * 0.9 : START + (hash(k, 3) - 0.3) * 0.45;
               double r = this.radius * (nearSeal ? 1.12 + 0.2 * hash(k, 5) : 0.9 + 0.22 * hash(k, 5));
               double s = sz * (0.035 + 0.06 * hash(k, 7)) * (0.6 + 0.4 * show);
               int col = alpha(ink, alpha * fade * 0.9F * show);
               this.quad(a, r, s, s, (float)(hash(k, 9) * Math.PI * 2.0), tex, k % 4, 4, col);
            }
         }
      }

      if (this.seal.get()) {
         float stampT = (float)((t - paintTime) / 0.16);
         if (!(stampT <= 0.0F)) {
            float e = Easing.outBack(Easing.clamp01(stampT), 2.0F);
            double sx = 1.0 + 0.25 * (1.0 - e);
            double sy = 1.0 - 0.2 * (1.0 - e);
            double s = (this.placement.get() == InkEnsoEffect.Placement.GROUND ? 0.3 : 0.24) * sz;
            int col = alpha(ColorUtil.shade(this.sealColor.getColor(0.5F), c.light), alpha * fade * Easing.clamp01(stampT * 3.0F));
            this.quad(SEAL_AT, this.radius + 0.19 * sz, s * sx, s * sy, 0.06F, Ink.seals(), this.sealDesign.get().ordinal(), 4, col);
         }
      }
   }

   private void quad(double a, double r, double w, double h, float turn, int tex, int cell, int cells, int col) {
      double ca = Math.cos(a);
      double sa = Math.sin(a);
      double px = this.ox + (this.ax * ca + this.bx * sa) * r;
      double py = this.oy + (this.ay * ca + this.by * sa) * r;
      double pz = this.oz + (this.az * ca + this.bz * sa) * r;
      double ct = Math.cos(turn);
      double st = Math.sin(turn);
      double ux = (this.ax * ct + this.bx * st) * w * 0.5;
      double uy = (this.ay * ct + this.by * st) * w * 0.5;
      double uz = (this.az * ct + this.bz * st) * w * 0.5;
      double vx = (this.bx * ct - this.ax * st) * h * 0.5;
      double vy = (this.by * ct - this.ay * st) * h * 0.5;
      double vz = (this.bz * ct - this.az * st) * h * 0.5;
      float u0 = (float)cell / cells;
      float u1 = (float)(cell + 1) / cells;
      Vfx.quadUV(
         tex,
         px - ux - vx,
         py - uy - vy,
         pz - uz - vz,
         u0,
         1.0,
         col,
         px + ux - vx,
         py + uy - vy,
         pz + uz - vz,
         u1,
         1.0,
         col,
         px + ux + vx,
         py + uy + vy,
         pz + uz + vz,
         u1,
         0.0,
         col,
         px - ux + vx,
         py - uy + vy,
         pz - uz + vz,
         u0,
         0.0,
         col
      );
   }

   private void nightLight(EffectContext c, float painted) {
      float a = this.intensity(c) * c.night * 0.12F * (1.0F - Easing.clamp01(c.outro));
      if (!(a <= 0.003F)) {
         double sweep = ((Math.PI * 2) - Math.toRadians(this.gap.getFloat())) * painted;
         int n = 28;
         float s = (float)(this.brushWidth.getFloat() * this.size(c) * 3.2);

         for (int i = 0; i < n; i++) {
            double ang = START - sweep * i / (n - 1.0);
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            Vfx.sprite(
               Textures.GLOW,
               this.ox + (this.ax * ca + this.bx * sa) * this.radius,
               this.oy + (this.ay * ca + this.by * sa) * this.radius,
               this.oz + (this.az * ca + this.bz * sa) * this.radius,
               s,
               0.0F,
               alpha(-3549953, a)
            );
         }
      }
   }

   public String stateForTest(EffectContext c) {
      return String.format(Locale.ROOT, "painted %.2f, outro %.2f, %s", this.painted(c), c.outro, this.placement.get().displayName());
   }

   public static enum Placement implements Displayable {
      BODY("Around body", "A circle round the target, facing you"),
      GROUND("On the ground", "A circle flat on the ground round their feet");

      private final String displayName;
      private final String description;

      private Placement(String displayName, String description) {
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
