package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.Ink;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.stroke.Strokes;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class InkBlotStyle extends LandingStyleBase {
   private static final float BLOT = 0.12F;
   private static final float DROPS = 0.32F;
   private static final float SEAL_FROM = 0.32F;
   private static final float SEAL = 0.47F;
   private static final float SOAK = 0.5F;
   private static final float SOAK_TIME = 0.9F;
   private static final int MAX_DROPS = 40;
   private static final int MAX_FLUNG = 8;
   private static final int RING_POINTS = 64;
   private static final double BLOT_HALF = 0.74;
   private static final int NIGHT_RIM = -2563329;
   public final ThemeTint seal;
   public final SliderSetting blotSize;
   public final SliderSetting splatter;
   public final BooleanSetting sealOn;
   public final ModeSetting<Ink.SealDesign> sealDesign;
   public final BooleanSetting enso;
   public final SliderSetting soakSpeed;
   private final Strokes.Style brush = new Strokes.Style();

   public InkBlotStyle() {
      super(
         LandingStyle.INK_BLOT, 1.4F, 2.6F, ThemeTint.detail(-15461356)
      );
      this.seal = ThemeTint.detail(-3660241);
      this.blotSize = this.look(this.setting(new SliderSetting("inkblot.blotSize", "Blot Size", "How big the blot is.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.splatter = this.look(
         this.setting(new SliderSetting("inkblot.splatter", "Splatter", "How far the rays reach and how many droplets fly.", 0.0, 100.0, 60.0, 5.0).unit("%"))
      );
      this.sealOn = this.setting(new BooleanSetting("inkblot.sealOn", "Seal", "A red seal stamps beside the blot.", true));
      this.sealDesign = this.setting(
         new ModeSetting<>("inkblot.sealDesign", "Seal Design", "The crest cut into the seal.", Ink.SealDesign.class, Ink.SealDesign.SUN)
            .style(ModeSetting.Style.CHIPS)
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return InkBlotStyle.this.sealOn.get();
               }
            })
      );
      this.enso = this.setting(
         new BooleanSetting("inkblot.enso", "Enso Ring", "A brush circle painted round the blot.", false)
            .keywords(new String[]{"enso", "ensō", "circle", "brush ring"})
      );
      this.soakSpeed = this.setting(
         new SliderSetting("inkblot.soakSpeed", "Soak Speed", "How fast the ink soaks into the ground.", 50.0, 200.0, 100.0, 5.0).unit("%")
      );
   }

   @Override
   public float baseSeconds() {
      return 0.5F + 0.9F / Math.max(0.5F, this.soakSpeed.getFloat() / 100.0F);
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.TRANSLUCENT.bit() | (fx.night > 0.01F ? FxPass.GLOW.bit() : 0);
   }

   @Override
   public void start(LandingFx fx) {
      InkBlotStyle.State s = new InkBlotStyle.State();
      s.jump = fx.land.jump;
      float k = this.splatter.getFloat() / 100.0F;
      s.half = 0.74 * this.blotSize.getFloat() / 100.0 * fx.size * (s.jump ? 0.55 : 1.0);
      s.rot = fx.rand(1) * (Math.PI * 2);
      s.blotCell = 0 + (fx.rand(2) < 0.5F ? 0 : 1);
      s.rayCell = 2 + (fx.rand(3) < 0.5F ? 0 : 1);
      s.soak = Math.max(0.5F, this.soakSpeed.getFloat() / 100.0F);
      double yaw = Math.toRadians(fx.land.yaw);
      double fwdX = -Math.sin(yaw);
      double fwdZ = Math.cos(yaw);
      if (s.jump) {
         int n = Math.min(40, 3 + Math.round(4.0F * k * fx.density));

         for (int i = 0; i < n; i++) {
            double back = s.half * (1.3 + 0.9 * i / Math.max(1, n - 1)) + 0.1 * fx.rand(40 + i);
            double side = fx.srand(60 + i) * s.half * 0.35;
            s.dx[i] = -fwdX * back - fwdZ * side;
            s.dz[i] = -fwdZ * back + fwdX * side;
            s.dsize[i] = s.half * (0.32 - 0.16 * i / Math.max(1, n - 1)) * (0.8 + 0.4 * fx.rand(80 + i));
            s.dturn[i] = Math.atan2(-fwdZ, -fwdX);
            s.tail[i] = true;
            s.dat[i] = 0.12F + 0.12F * i / Math.max(1, n - 1);
         }

         s.drops = n;
      } else {
         int n = Math.min(40, Math.round((6.0F + 14.0F * k) * fx.density * (fx.lod ? 0.5F : 1.0F)));

         for (int i = 0; i < n; i++) {
            double ang = (Math.PI * 2) * (i + 0.6 * fx.rand(100 + i)) / Math.max(1, n);
            double r = s.half * (1.05 + (0.5 + 0.8 * k) * fx.rand(140 + i));
            s.dx[i] = Math.cos(ang) * r;
            s.dz[i] = Math.sin(ang) * r;
            s.dsize[i] = s.half * (0.07 + 0.11 * fx.rand(180 + i)) * (1.25 - 0.4 * (r / s.half - 1.0));
            s.dturn[i] = ang;
            s.tail[i] = fx.rand(220 + i) < 0.55F;
            s.dat[i] = 0.12F + 0.19999999F * (float)((r / s.half - 1.0) / (0.5 + 0.8 * k + 0.001)) * 0.9F;
         }

         s.drops = n;
         if (fx.land.fallHeight >= 4.0F && !fx.lod) {
            int m = Math.min(8, 3 + Math.round(fx.land.fallScale * 3.0F));

            for (int i = 0; i < m && s.drops < 40; i++) {
               double ang = fx.rand(300 + i) * (Math.PI * 2);
               double reach = s.half * (1.8 + 1.4 * fx.rand(320 + i));
               float up = 2.4F + fx.rand(340 + i) * 1.4F;
               float flight = up * 2.0F / 14.0F;
               s.fvx[i] = Math.cos(ang) * reach / flight;
               s.fvz[i] = Math.sin(ang) * reach / flight;
               s.fvy[i] = up;
               s.fland[i] = 0.04F + flight;
               int d = s.drops++;
               s.dx[d] = Math.cos(ang) * reach;
               s.dz[d] = Math.sin(ang) * reach;
               s.dsize[d] = s.half * (0.1 + 0.06 * fx.rand(360 + i));
               s.dturn[d] = ang;
               s.tail[d] = true;
               s.dat[d] = s.fland[i];
            }

            s.flung = m;
         }
      }

      double rightX = -fwdZ;
      double out = s.half * 1.15;
      s.sx = rightX * out + fwdX * s.half * 0.25;
      s.sz = fwdX * out + fwdZ * s.half * 0.25;
      s.shalf = 0.21 * Math.sqrt(fx.size);
      s.sturn = yaw + 0.25 * fx.srand(5);
      fx.data = s;
   }

   private static float soaked(LandingFx fx, InkBlotStyle.State s) {
      return Easing.clamp01((fx.styleTime() - 0.5F) / (0.9F / s.soak));
   }

   private static float left(float soak, float from, float to) {
      return 1.0F - Easing.smoothstep(from, to, soak);
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      InkBlotStyle.State s = (InkBlotStyle.State)fx.data;
      if (s != null) {
         if (pass == FxPass.GLOW) {
            this.night(fx, f, s);
         } else {
            Camera cam = Vfx.camera();
            float t = fx.styleTime();
            float soak = soaked(fx, s);
            int ink = ColorUtil.shade(this.color.getColor(0.5F), Math.max(0.4F, fx.light)) | 0xFF000000;
            double cx = f.rx(fx.x());
            double cy = f.ry(fx.y());
            double cz = f.rz(fx.z());
            float alpha = NearCamera.dark(cam, cx, cy, cz, fx.alpha());
            RenderState.decal(true);
            RenderState.translucent(false);

            try {
               double spread = 1.0 + 0.08 * soak;
               float grow = Easing.outCubic(Easing.clamp01(t / 0.12F));
               if (grow > 0.0F) {
                  double half = s.half * (0.35 + 0.65 * grow) * spread;
                  if (!s.jump) {
                     decal(
                        fx,
                        f,
                        Ink.shapes(),
                        s.rayCell,
                        6,
                        0.0,
                        0.0,
                        half * (1.15 + 0.45 * this.splatter.getFloat() / 100.0),
                        s.rot + 1.3,
                        alpha(ink, alpha * left(soak, 0.1F, 0.65F))
                     );
                  }

                  decal(fx, f, Ink.shapes(), s.blotCell, 6, 0.0, 0.0, half, s.rot, alpha(ink, alpha * left(soak, 0.25F, 0.85F)));
                  decal(
                     fx,
                     f,
                     Ink.shapes(),
                     s.blotCell,
                     6,
                     0.0,
                     0.0,
                     half * 0.62,
                     s.rot,
                     alpha(ink, alpha * 0.85F * left(soak, 0.45F, 1.0F) * (1.0F - left(soak, 0.25F, 0.85F)))
                  );
               }

               this.drops(fx, f, s, t, soak, ink, alpha);
               if (this.sealOn.get() && !s.jump) {
                  this.sealDecal(fx, f, s, t, soak, alpha);
               }
            } finally {
               RenderState.decal(false);
               RenderState.translucent(true);
            }

            if (this.enso.get() && !s.jump) {
               this.ring(fx, f, s, t, soak, ink, alpha);
            }

            this.flung(fx, f, s, t, ink, alpha, cam);
         }
      }
   }

   private static void decal(LandingFx fx, FxFrame f, int tex, int cell, int cells, double ox, double oz, double half, double turn, int col) {
      if (col >>> 24 != 0 && !(half <= 1.0E-4)) {
         float u0 = (float)cell / cells;
         float u1 = (float)(cell + 1) / cells;
         Ground.decal(f, fx.ground, tex, u0, 0.0F, u1, 1.0F, fx.x() + ox, fx.z() + oz, half, turn, col, half > 0.4 ? 6 : 2);
      }
   }

   private void drops(LandingFx fx, FxFrame f, InkBlotStyle.State s, float t, float soak, int ink, float alpha) {
      float a = alpha * left(soak, 0.0F, 0.55F);
      if (!(a <= 0.003F)) {
         int tex = Ink.drops();

         for (int i = 0; i < s.drops; i++) {
            float land = (t - s.dat[i]) / 0.05F;
            if (!(land <= 0.0F)) {
               double pop = 1.0 + 0.35 * (1.0 - Easing.clamp01(land));
               double half = s.dsize[i] * pop * (1.0 + 0.06 * soak);
               decal(fx, f, tex, s.tail[i] ? 1 : 0, 2, s.dx[i], s.dz[i], half, s.dturn[i], alpha(ink, a * Easing.clamp01(land)));
            }
         }
      }
   }

   private void sealDecal(LandingFx fx, FxFrame f, InkBlotStyle.State s, float t, float soak, float alpha) {
      float in = Easing.clamp01((t - 0.32F) / 0.15F);
      if (!(in <= 0.0F)) {
         double press = 1.0 + 0.4 * (1.0 - Easing.outCubic(in));
         int red = ColorUtil.shade(this.seal.getColor(0.5F), Math.max(0.45F, fx.light)) | 0xFF000000;
         decal(
            fx,
            f,
            Ink.seals(),
            this.sealDesign.get().ordinal(),
            4,
            s.sx,
            s.sz,
            s.shalf * press,
            s.sturn,
            alpha(red, alpha * Easing.clamp01(in * 2.5F) * left(soak, 0.3F, 0.9F))
         );
      }
   }

   private void ring(LandingFx fx, FxFrame f, InkBlotStyle.State s, float t, float soak, int ink, float alpha) {
      float drawn = Easing.inOutCubic(Easing.clamp01((t - 0.12F) / 0.3F));
      if (!(drawn <= 0.0F)) {
         double r = s.half * 1.45 + 0.1 * fx.size;
         double start = s.rot + 2.1;
         int n = 64;

         for (int i = 0; i < n; i++) {
            double a = start + 5.843362335677016 * i / (n - 1);
            double wobble = 1.0 + 0.04 * Math.sin(a * 3.0 + s.rot) + 0.02 * Math.sin(a * 7.0);
            double x = fx.x() + Math.cos(a) * r * wobble;
            double z = fx.z() + Math.sin(a) * r * wobble;
            Strokes.X[i] = f.rx(x);
            Strokes.Y[i] = f.ry(Ground.y(fx.ground, x, z) + 0.004);
            Strokes.Z[i] = f.rz(z);
         }

         Strokes.Style b = this.brush.reset();
         b.tool = Strokes.Tool.BRUSH;
         b.color = ink;
         b.width = 0.16 * Math.sqrt(fx.size);
         b.alpha = alpha * 0.92F;
         b.progress = drawn;
         b.flat = true;
         b.normalX = 0.0;
         b.normalY = 1.0;
         b.normalZ = 0.0;
         b.heavyStart = 1.0F;
         b.thinEnd = 0.75F;
         b.dryness = 0.6F;
         b.diffuse = Easing.smoothstep(0.35F, 1.0F, soak);
         b.light = Math.max(0.4F, fx.light);
         Strokes.draw(n, b);
      }
   }

   private void flung(LandingFx fx, FxFrame f, InkBlotStyle.State s, float t, int ink, float alpha, Camera cam) {
      if (s.flung != 0) {
         int tex = Ink.drops();
         RenderState.translucent(false);

         for (int i = 0; i < s.flung; i++) {
            float age = t - 0.04F;
            if (!(age <= 0.0F) && !(t >= s.fland[i])) {
               double x = fx.x() + s.fvx[i] * age;
               double y = fx.y() + 0.15 + s.fvy[i] * age - 7.0 * age * age;
               double z = fx.z() + s.fvz[i] * age;
               double vx = s.fvx[i];
               double vy = s.fvy[i] - 14.0 * age;
               double vz = s.fvz[i];
               double rx = f.rx(x);
               double ry = f.ry(y);
               double rz = f.rz(z);
               double sxv = vx * cam.rightX + vy * cam.rightY + vz * cam.rightZ;
               double syv = vx * cam.upX + vy * cam.upY + vz * cam.upZ;
               float turn = (float)Math.atan2(syv, sxv);
               double size = s.half * 0.16;
               Vfx.sprite(tex, rx, ry, rz, size * 1.2, size * 0.6, turn, 0.5F, 0.0F, 1.0F, 1.0F, alpha(ink, NearCamera.dark(cam, rx, ry, rz, alpha)));
            }
         }

         RenderState.translucent(true);
      }
   }

   private void night(LandingFx fx, FxFrame f, InkBlotStyle.State s) {
      float k = fx.night * fx.alpha() * left(soaked(fx, s), 0.2F, 0.85F) * Easing.clamp01(fx.styleTime() / 0.12F);
      if (!(k <= 0.01F)) {
         RenderState.decal(true);
         decal(fx, f, Ink.shapes(), s.blotCell, 6, 0.0, 0.0, s.half * 1.08, s.rot, alpha(-2563329, 0.16F * k));
         RenderState.decal(false);
      }
   }

   private static final class State {
      double half;
      double rot;
      int blotCell;
      int rayCell;
      int drops;
      final double[] dx = new double[40];
      final double[] dz = new double[40];
      final double[] dsize = new double[40];
      final double[] dturn = new double[40];
      final boolean[] tail = new boolean[40];
      final float[] dat = new float[40];
      int flung;
      final double[] fvx = new double[8];
      final double[] fvy = new double[8];
      final double[] fvz = new double[8];
      final float[] fland = new float[8];
      double sx;
      double sz;
      double shalf;
      double sturn;
      float soak;
      boolean jump;

      private State() {
      }
   }
}
