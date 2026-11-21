package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.kit.Sketch;
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
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class DoodleEffect extends AuraEffect {
   private static final int NIGHT = -3549953;
   private static final double BEHIND = 0.45;
   public final ThemeTint highlights;
   public final ModeSetting<Sketch.Tool> tool;
   public final ModeSetting<DoodleEffect.Doodles> doodles;
   public final SliderSetting lineWidth;
   public final SliderSetting wobble;
   public final SliderSetting boil;
   private final Strokes.Style style = new Strokes.Style();
   private Sketch.Tool lastTool;
   private double ox;
   private double oy;
   private double oz;
   private double rx;
   private double ry;
   private double rz;
   private double ux;
   private double uy;
   private double uz;
   private double fx;
   private double fy;
   private double fz;
   private double radU;
   private double radV;
   private double s;
   private double crownV;
   private final Sketch.Plane plane = new Sketch.Plane();

   public DoodleEffect() {
      super(
         EffectType.DOODLE,
         "doodle",
         new ThemeTint(),
         true
      );
      this.highlights = ThemeTint.detail(-14020);
      this.tool = this.setting(
         new ModeSetting<>(
               "doodle.tool",
               "Tool",
               "Pencil, marker or chalk. With Lines Static and still the tool's color, switching the tool switches the color too.",
               Sketch.Tool.class,
               Sketch.Tool.PENCIL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.doodles = this.setting(
         new ModeSetting<>(
               "doodle.doodles",
               "Doodles",
               "What is drawn round the target besides the circle and the arrows.",
               DoodleEffect.Doodles.class,
               DoodleEffect.Doodles.STARS
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.lineWidth = this.look(
         this.setting(new SliderSetting("doodle.lineWidth", "Line Width", "How thick the lines are.", 50.0, 200.0, 100.0, 5.0).unit("%"))
      );
      this.wobble = this.look(this.setting(new SliderSetting("doodle.wobble", "Wobble", "How shaky the hand is.", 0.0, 100.0, 50.0, 5.0).unit("%")));
      this.boil = this.look(
         this.setting(
            new SliderSetting("doodle.boil", "Boiling", "How many times a second the lines are redrawn with a new wobble.", 0.0, 12.0, 10.0, 1.0).unit("/s")
         )
      );
      this.lastTool = this.tool.get();
      this.tool.addListener(new Runnable() {
         @Override
         public void run() {
            DoodleEffect.this.toolChanged();
         }
      });
   }

   private void toolChanged() {
      Sketch.Tool now = this.tool.get();
      Sketch.Tool before = this.lastTool;
      this.lastTool = now;
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      float alpha = this.intensity(c);
      if (!(alpha <= 0.003F)) {
         if (pass == FxPass.TRANSLUCENT) {
            this.layout(c);
            this.draw(c, alpha);
         } else if (pass == FxPass.GLOW && c.night > 0.01F && this.tool.get() == Sketch.Tool.PENCIL) {
            this.layout(c);
            this.nightLight(c, alpha);
         }
      }
   }

   private void layout(EffectContext c) {
      this.s = this.size(c);
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      this.rx = cam.rightX;
      this.ry = cam.rightY;
      this.rz = cam.rightZ;
      this.ux = cam.upX;
      this.uy = cam.upY;
      this.uz = cam.upZ;
      this.fx = cam.forwardX;
      this.fy = cam.forwardY;
      this.fz = cam.forwardZ;
      double hl = Math.sqrt(this.fx * this.fx + this.fz * this.fz);
      double back = hl < 1.0E-6 ? 0.0 : 0.45 / hl;
      double cy = c.y + c.height * 0.55;
      this.ox = c.x + this.fx * back;
      this.oy = cy;
      this.oz = c.z + this.fz * back;
      this.radU = 0.72 * this.s;
      this.radV = Math.min(0.95 * this.s, cy - c.groundY - 0.05);
      if (c.nameBottom > c.headTop + 0.01) {
         this.radV = Math.min(this.radV, c.y + c.nameBottom - 0.05 - cy);
      }

      this.radV = Math.max(0.3, this.radV);
      double crown = c.headTop + 0.04;
      if (c.nameBottom > c.headTop + 0.01) {
         crown = Math.min(crown, c.nameBottom - 0.2);
      }

      this.crownV = c.y + crown - cy;
      this.plane.ox = this.ox;
      this.plane.oy = this.oy;
      this.plane.oz = this.oz;
      this.plane.rx = this.rx;
      this.plane.ry = this.ry;
      this.plane.rz = this.rz;
      this.plane.ux = this.ux;
      this.plane.uy = this.uy;
      this.plane.uz = this.uz;
   }

   private void at(int i, double u, double v) {
      Strokes.X[i] = this.ox + this.rx * u + this.ux * v;
      Strokes.Y[i] = this.oy + this.ry * u + this.uy * v;
      Strokes.Z[i] = this.oz + this.rz * u + this.uz * v;
   }

   private void draw(EffectContext c, float alpha) {
      double t = c.time;
      Sketch.Tool tl = this.tool.get();
      int lines = this.color(c, 0.5F);
      int high = this.highlights.getColor(0.5F);
      double width = tl.width() * this.lineWidth.getFloat() / 100.0 * this.s;
      float out = c.outro;
      float fade = alpha * (1.0F - Easing.smoothstep(0.6F, 1.0F, out));
      float erase = Easing.clamp01(out * 1.15F);
      int seed = this.boil.getInt() <= 0 ? 0 : (int)Math.floor(t * this.boil.getFloat() * this.speed());
      double amount = this.wobble.getFloat() / 100.0 * 0.035 * this.s;
      int n = 96;

      for (int i = 0; i < n; i++) {
         double a = Math.toRadians(110.0) - 6.7230082786821574 * i / (n - 1.0);
         double r = 1.0 + 0.035 * Math.sin(a * 2.0 + 0.6) + 0.04 * i / (n - 1.0);
         this.at(i, Math.cos(a) * this.radU * r, Math.sin(a) * this.radV * r);
      }

      this.line(c, n, tl, lines, width, fade, step(t, 0.0, 0.2), erase, seed, amount, 0);
      this.arrow(c, -1.5 * this.s, 0.85 * this.radV, -0.86 * this.radU, 0.32 * this.radV, tl, lines, width, fade, t, 0.2, erase, seed, amount, 1);
      this.arrow(c, 1.5 * this.s, -0.6 * this.radV, 0.88 * this.radU, -0.24 * this.radV, tl, lines, width, fade, t, 0.33, erase, seed, amount, 4);
      DoodleEffect.Doodles d = this.doodles.get();
      double start = 0.46;
      if (d == DoodleEffect.Doodles.STARS || d == DoodleEffect.Doodles.ALL) {
         this.star(c, 1.08 * this.s, 0.68 * this.radV, 0.13 * this.s, tl, high, width, fade, step(t, start, 0.05), erase, seed, amount, 7, t);
         this.star(c, -1.12 * this.s, -0.42 * this.radV, 0.1 * this.s, tl, high, width, fade, step(t, start + 0.04, 0.05), erase, seed, amount, 8, t);
         start += 0.08;
      }

      if (d == DoodleEffect.Doodles.CROWN || d == DoodleEffect.Doodles.ALL) {
         this.crown(c, tl, high, width, fade, step(t, start, 0.06), erase, seed, amount, 9, t);
         start += 0.06;
      }

      if (d == DoodleEffect.Doodles.HEARTS || d == DoodleEffect.Doodles.ALL) {
         this.heart(c, 1.1 * this.s, 0.12 * this.radV, 0.12 * this.s, tl, high, width, fade, step(t, start, 0.05), erase, seed, amount, 10, t);
         this.heart(c, -1.06 * this.s, 0.95 * this.radV, 0.1 * this.s, tl, high, width, fade, step(t, start + 0.04, 0.05), erase, seed, amount, 11, t);
      }

      if (out > 0.0F && out < 1.0F) {
         double front = (-1.7 + 3.4 * erase) * this.s;
         int m = Sketch.smear(this.plane, front, this.radV + 0.15 * this.s, 0.07 * this.s);
         this.style.reset();
         this.style.tool = Strokes.Tool.CHALK;
         this.style.color = Sketch.smearColor(lines);
         this.style.width = width * 3.2;
         this.style.alpha = alpha * 0.32F * (1.0F - out);
         this.style.light = c.light;
         Strokes.draw(m, this.style);
      }
   }

   private static float step(double t, double start, double dur) {
      return Easing.outCubic(Easing.clamp01((float)((t - start) / dur)));
   }

   private void line(
      EffectContext c, int n, Sketch.Tool tl, int color, double width, float alpha, float progress, float erase, int seed, double amount, int index
   ) {
      if (!(progress <= 0.0F)) {
         Strokes.wobble(n, amount, seed * 31 + index * 7 + 1, this.fx, this.fy, this.fz);
         this.style.reset();
         this.style.tool = tl.stroke();
         this.style.color = color;
         this.style.width = width;
         this.style.alpha = alpha;
         this.style.progress = progress;
         this.style.erase = erase;
         this.style.eraseX = this.rx;
         this.style.eraseY = this.ry;
         this.style.eraseZ = this.rz;
         this.style.tile = 0.5;
         this.style.light = c.light;
         Strokes.draw(n, this.style);
      }
   }

   private void arrow(
      EffectContext c,
      double u0,
      double v0,
      double u1,
      double v1,
      Sketch.Tool tl,
      int color,
      double width,
      float alpha,
      double t,
      double start,
      float erase,
      int seed,
      double amount,
      int index
   ) {
      int n = 28;
      double du = u1 - u0;
      double dv = v1 - v0;
      double len = Math.sqrt(du * du + dv * dv);
      double pu = -dv / len;
      double pv = du / len;

      for (int i = 0; i < n; i++) {
         double k = i / (n - 1.0);
         double bow = Math.sin(Math.PI * k) * 0.07 * len;
         this.at(i, u0 + du * k + pu * bow, v0 + dv * k + pv * bow);
      }

      this.line(c, n, tl, color, width, alpha, step(t, start, 0.07), erase, seed, amount, index);
      double hu = -du / len;
      double hv = -dv / len;
      double head = 0.18 * this.s;

      for (int side = -1; side <= 1; side += 2) {
         double a = Math.toRadians(28.0) * side;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double bu = hu * ca - hv * sa;
         double bv = hu * sa + hv * ca;
         int m = 8;

         for (int i = 0; i < m; i++) {
            double k = i / (m - 1.0);
            this.at(i, u1 + bu * head * k, v1 + bv * head * k);
         }

         this.line(c, m, tl, color, width, alpha, step(t, start + 0.07 + (side + 1) * 0.015, 0.03), erase, seed, amount * 0.5, index + 1 + (side + 1) / 2);
      }
   }

   private void star(
      EffectContext c,
      double u,
      double v,
      double r,
      Sketch.Tool tl,
      int color,
      double width,
      float alpha,
      float progress,
      float erase,
      int seed,
      double amount,
      int index,
      double t
   ) {
      int n = Sketch.star(this.plane, u, v + this.bounce(t, index), r);
      this.line(c, n, tl, color, width * 0.85, alpha, progress, erase, seed, amount * 0.35, index);
   }

   private void heart(
      EffectContext c,
      double u,
      double v,
      double r,
      Sketch.Tool tl,
      int color,
      double width,
      float alpha,
      float progress,
      float erase,
      int seed,
      double amount,
      int index,
      double t
   ) {
      int n = Sketch.heart(this.plane, u, v + this.bounce(t, index), r);
      this.line(c, n, tl, color, width * 0.85, alpha, progress, erase, seed, amount * 0.35, index);
   }

   private void crown(
      EffectContext c, Sketch.Tool tl, int color, double width, float alpha, float progress, float erase, int seed, double amount, int index, double t
   ) {
      double w = 0.17 * this.s;
      double h = 0.16 * this.s;
      double v0 = this.crownV + this.bounce(t, index) * 0.5;
      double[][] pts = new double[][]{{-w, 0.0}, {-w, h * 0.85}, {-w * 0.5, h * 0.4}, {0.0, h}, {w * 0.5, h * 0.4}, {w, h * 0.85}, {w, 0.0}, {-w, 0.0}};
      int per = 5;
      int n = (pts.length - 1) * per + 1;

      for (int p = 0; p < pts.length - 1; p++) {
         for (int i = 0; i < per; i++) {
            double k = (double)i / per;
            this.at(p * per + i, pts[p][0] + (pts[p + 1][0] - pts[p][0]) * k, v0 + pts[p][1] + (pts[p + 1][1] - pts[p][1]) * k);
         }
      }

      this.at(n - 1, pts[pts.length - 1][0], v0 + pts[pts.length - 1][1]);
      this.line(c, n, tl, color, width * 0.85, alpha, progress, erase, seed, amount * 0.3, index);
   }

   private double bounce(double t, int index) {
      return Math.abs(Math.sin(t * 3.0 * this.speed() + index * 1.3)) * 0.04 * this.s;
   }

   private void nightLight(EffectContext c, float alpha) {
      float a = alpha * c.night * 0.1F * (1.0F - Easing.clamp01(c.outro)) * step(c.time, 0.0, 0.2);
      if (!(a <= 0.003F)) {
         int n = 24;

         for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2) * i / n;
            double u = Math.cos(ang) * this.radU;
            double v = Math.sin(ang) * this.radV;
            Vfx.sprite(
               Textures.GLOW,
               this.ox + this.rx * u + this.ux * v,
               this.oy + this.ry * u + this.uy * v,
               this.oz + this.rz * u + this.uz * v,
               0.28F * (float)this.s,
               0.0F,
               alpha(-3549953, a)
            );
         }
      }
   }

   public String stateForTest(EffectContext c) {
      this.layout(c);
      return String.format(
         Locale.ROOT,
         "%s, lines %s, circle %.2f x %.2f (top %.2f above the feet; name tag from %.2f)",
         this.tool.get().displayName(),
         ColorUtil.toHex(this.color.getColor(0.5F)),
         this.radU * 2.0,
         this.radV * 2.0,
         this.oy + this.radV - c.y,
         c.nameBottom
      );
   }

   public static enum Doodles implements Displayable {
      CIRCLE_ARROWS("Circle + arrows", "Just the circle and the arrows"),
      STARS("Stars", "And a few stars"),
      CROWN("Crown", "And a crown on the head"),
      HEARTS("Hearts", "And a few hearts"),
      ALL("All", "Stars, a crown and hearts");

      private final String displayName;
      private final String description;

      private Doodles(String displayName, String description) {
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
