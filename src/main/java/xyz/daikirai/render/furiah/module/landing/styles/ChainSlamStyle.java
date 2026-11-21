package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.fx.kit.Chains;
import xyz.daikirai.render.furiah.fx.kit.Clockwork;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
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

public final class ChainSlamStyle extends LandingStyleBase {
   private static final float WHIP = 0.25F;
   private static final float TAUT = 0.6F;
   private static final float PULLED = 1.0F;
   private static final float END = 1.2F;
   private static final float JUMP_WHIP = 0.18F;
   private static final float JUMP_PULLED = 0.5F;
   private static final int MAX_CHAINS = 8;
   private static final int MAX_POINTS = 40;
   private static final double LINK = 0.21;
   private static final double LINK_WIDTH = 0.54;
   private static final double RING_IN = 0.72;
   private static final double RING_OUT = 0.92;
   private static final double RING_T = 0.09;
   private static final float RING_FALL = 4.0F;
   private static final float RIM = 0.16F;
   private static final float DUST_WIND = 0.3F;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final SliderSetting chains;
   public final SliderSetting length;
   public final ModeSetting<ChainSlamStyle.Ring> ring;
   public final SliderSetting linkSize;
   public final BooleanSetting pullBack;
   private final double[] a = new double[3];
   private final double[] b = new double[3];

   public ChainSlamStyle() {
      super(
         LandingStyle.CHAIN_SLAM,
         1.2F,
         4.3F,
         new ThemeTint()
      );
      this.material = this.materialSetting("chainslam", Material.GOLD, "What the chains are made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting("chainslam", Finish.METALLIC, this.material);
      this.chains = this.look(this.setting(new SliderSetting("chainslam.chains", "Chains", "How many chains whip out.", 2.0, 8.0, 5.0, 1.0)));
      this.length = this.look(this.setting(new SliderSetting("chainslam.length", "Length", "How far the chains reach.", 1.0, 4.0, 2.5, 0.25).unit(" blocks")));
      this.ring = this.setting(
         new ModeSetting<>(
               "chainslam.ring",
               "Ring",
               "A heavy gilded ring slams down round the feet: on drops of 4 blocks or more, always, or never.",
               ChainSlamStyle.Ring.class,
               ChainSlamStyle.Ring.BIG_DROPS
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.linkSize = this.look(this.setting(new SliderSetting("chainslam.linkSize", "Link Size", "How big the links are.", 50.0, 150.0, 100.0, 5.0).unit("%")));
      this.pullBack = this.setting(
         new BooleanSetting("chainslam.pullBack", "Pull Back", "The chains pull back into the middle and vanish. Off: they lie where they fell and fade.", true)
      );
   }

   @Override
   public int passes(LandingFx fx) {
      int solid = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return solid | (fx.lod ? 0 : FxPass.GLOW.bit());
   }

   @Override
   public void start(LandingFx fx) {
      ChainSlamStyle.State s = new ChainSlamStyle.State();
      s.jump = fx.land.jump;
      float fallScale = fx.land.fallScale;
      int n = this.chains.getInt() + (s.jump ? 0 : Math.round(Math.max(0.0F, fallScale - 1.0F) * 4.0F));
      s.n = Math.max(2, Math.min(8, n));
      s.link = 0.21 * this.linkSize.getFloat() / 100.0 * Math.sqrt(fx.size);
      double turn = fx.rand(1) * (Math.PI * 2);

      for (int k = 0; k < s.n; k++) {
         double ang = turn + (Math.PI * 2) * (k + 0.3 * fx.srand(10 + k)) / s.n;
         s.dx[k] = Math.cos(ang);
         s.dz[k] = Math.sin(ang);
         s.len[k] = this.length.getFloat() * fx.size * (s.jump ? 0.45 : 1.0) * (0.85 + 0.3 * fx.rand(20 + k));
         double spacing = s.link * 0.72;
         s.points[k] = Math.max(3, Math.min(40, (int)Math.ceil(s.len[k] / spacing) + 1));
         s.len[k] = (s.points[k] - 1) * spacing;
      }

      ChainSlamStyle.Ring r = this.ring.get();
      s.ring = !s.jump && (r == ChainSlamStyle.Ring.ALWAYS || r == ChainSlamStyle.Ring.BIG_DROPS && fx.land.fallHeight >= 4.0F);
      s.floor.hf = fx.ground;
      s.floor.lift = s.link * 0.54 * 0.5;
      fx.data = s;
   }

   private static float whipEnd(ChainSlamStyle.State s) {
      return s.jump ? 0.18F : 0.25F;
   }

   private static float whip(LandingFx fx, ChainSlamStyle.State s) {
      return Easing.clamp01(fx.styleTime() / whipEnd(s));
   }

   private static float pulled(LandingFx fx, ChainSlamStyle.State s) {
      float from = s.jump ? 0.18F : 0.6F;
      float to = s.jump ? 0.5F : 1.0F;
      return Easing.inOutCubic(Easing.clamp01((fx.styleTime() - from) / (to - from)));
   }

   private float faded(LandingFx fx, ChainSlamStyle.State s) {
      return this.pullBack.get() ? 0.0F : Easing.smoothstep(s.jump ? 0.18F : 0.6F, 1.2F, fx.styleTime());
   }

   private static float ringIn(LandingFx fx, ChainSlamStyle.State s) {
      return !s.ring ? 0.0F : Easing.clamp01((fx.styleTime() - 0.04F) / 0.14F);
   }

   private void whipPoint(LandingFx fx, ChainSlamStyle.State s, int k, double u, float w, double[] out) {
      double reach = s.len[k] * Easing.outCubic(w);
      double t = u * reach;
      double gx = fx.x() + s.dx[k] * t;
      double gz = fx.z() + s.dz[k] * t;
      double ground = this.groundAt(fx, s, gx, gz);
      double lift = 0.45 * fx.size * Math.sin(Math.PI * w) * u * u;
      double wave = 0.12 * fx.size * Math.sin(u * Math.PI * 3.0 - w * 12.0) * Math.sin(Math.PI * u) * (1.0 - w);
      out[0] = gx - s.dz[k] * wave;
      out[1] = ground + lift;
      out[2] = gz + s.dx[k] * wave;
   }

   private double groundAt(LandingFx fx, ChainSlamStyle.State s, double x, double z) {
      double h = fx.ground.at(x, z);
      return (Double.isNaN(h) ? fx.y() : h) + s.floor.lift;
   }

   private void toPhysics(LandingFx fx, ChainSlamStyle.State s) {
      s.physics = true;

      for (int k = 0; k < s.n; k++) {
         int pts = s.points[k];
         Chains.Rope r = s.rope[k];
         double spacing = s.link * 0.72;
         r.reset(pts, spacing, fx.x(), fx.y(), fx.z(), s.dx[k], 0.0, s.dz[k]);

         for (int i = 0; i < pts; i++) {
            this.whipPoint(fx, s, k, i / (pts - 1.0), 1.0F, this.a);
            r.place(i, this.a[0], this.a[1], this.a[2]);
            double u = i / (pts - 1.0);
            double kick = (s.jump ? 0.8 : 2.2) * Math.sqrt(fx.size) * Math.sin(Math.PI * Math.min(1.0, u * 1.3)) * (0.8 + 0.4 * fx.rand(40 + k * 41 + i));
            r.push(i, 0.0, kick, 0.0);
         }

         r.pin(0, true, fx.x(), this.groundAt(fx, s, fx.x(), fx.z()), fx.z());
      }
   }

   private void step(LandingFx fx, ChainSlamStyle.State s, FxFrame f) {
      if (s.frame != f.frameId) {
         s.frame = f.frameId;
         float now = fx.styleTime();
         float dt = s.lastTime < 0.0F ? 0.0F : Math.max(0.0F, Math.min(0.1F, now - s.lastTime));
         s.lastTime = now;
         if (!s.physics && now >= whipEnd(s)) {
            this.toPhysics(fx, s);
         }

         if (s.physics && dt > 0.0F) {
            for (int k = 0; k < s.n; k++) {
               s.rope[k].step(dt, s.floor);
            }
         }
      }
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      ChainSlamStyle.State s = (ChainSlamStyle.State)fx.data;
      if (s != null) {
         this.step(fx, s, f);
         if (pass == FxPass.GLOW) {
            this.glow(fx, f, s);
         } else {
            Camera cam = Vfx.camera();
            boolean custom = this.material.get() == Material.CUSTOM;
            int col = custom ? this.color.getColor(0.5F) : -1;
            float alpha = fx.alpha() * (1.0F - this.faded(fx, s)) * (custom ? (col >>> 24) / 255.0F : 1.0F);
            float t = fx.styleTime();
            float glint = 0.06F + 0.5F * (float)Math.exp(-Math.abs(t - whipEnd(s)) * 22.0) + 0.35F * (float)Math.exp(-Math.abs(t - 1.0F) * 18.0);
            MeshBatch.begin(this.material.get(), this.finish.get(), fx.light, 0.16F, -1, true, pass == FxPass.SOLID);
            if (alpha > 0.003F) {
               for (int k = 0; k < s.n; k++) {
                  this.chain(fx, f, s, k, col, alpha, glint, cam);
               }
            }

            this.ringMesh(fx, f, s, col, cam);
            MeshBatch.end();
            this.dust(fx, s);
         }
      }
   }

   private void chain(LandingFx fx, FxFrame f, ChainSlamStyle.State s, int k, int col, float alpha, float glint, Camera cam) {
      int pts = s.points[k];
      if (!s.physics) {
         float w = whip(fx, s);
         if (!(w <= 0.0F)) {
            this.whipPoint(fx, s, k, 0.0, w, this.a);

            for (int i = 1; i < pts; i++) {
               this.whipPoint(fx, s, k, i / (pts - 1.0), w, this.b);
               this.link(f, s, this.a, this.b, i, col, alpha, glint, cam);
               this.a[0] = this.b[0];
               this.a[1] = this.b[1];
               this.a[2] = this.b[2];
            }
         }
      } else {
         Chains.Rope r = s.rope[k];
         float pull = this.pullBack.get() ? pulled(fx, s) : 0.0F;
         double shift = pull * (pts - 1);
         if (!(shift >= pts - 1.001)) {
            along(r, pts, shift, this.a);

            double at;
            for (int i = 1; i < pts && !((at = i + shift) > pts - 1); i++) {
               along(r, pts, at, this.b);
               this.link(f, s, this.a, this.b, i, col, alpha, glint, cam);
               this.a[0] = this.b[0];
               this.a[1] = this.b[1];
               this.a[2] = this.b[2];
            }
         }
      }
   }

   private static void along(Chains.Rope r, int pts, double at, double[] out) {
      int i = Math.max(0, Math.min(pts - 2, (int)Math.floor(at)));
      double t = Math.max(0.0, Math.min(1.0, at - i));
      out[0] = r.x(i) + (r.x(i + 1) - r.x(i)) * t;
      out[1] = r.y(i) + (r.y(i + 1) - r.y(i)) * t;
      out[2] = r.z(i) + (r.z(i + 1) - r.z(i)) * t;
   }

   private void link(FxFrame f, ChainSlamStyle.State s, double[] p, double[] q, int i, int col, float alpha, float glint, Camera cam) {
      double x0 = f.rx(p[0]);
      double y0 = f.ry(p[1]);
      double z0 = f.rz(p[2]);
      double x1 = f.rx(q[0]);
      double z1;
      double y1;
      float a = alpha * NearCamera.fade(cam, (x0 + x1) * 0.5, (y0 + (y1 = f.ry(q[1]))) * 0.5, (z0 + (z1 = f.rz(q[2]))) * 0.5);
      if (!(a <= 0.003F)) {
         Chains.linkBetween(x0, y0, z0, x1, y1, z1, 0.0, 1.0, 0.0, (i & 1) == 1, s.link, ColorUtil.withAlpha(col, a), glint, true);
      }
   }

   private void ringMesh(LandingFx fx, FxFrame f, ChainSlamStyle.State s, int col, Camera cam) {
      float in = ringIn(fx, s);
      if (!(in <= 0.0F)) {
         float t = fx.styleTime();
         float out = Easing.smoothstep(0.85F, 1.2F, t);
         double drop = (1.0 - Easing.outBack(in, 1.6F)) * 1.1 * fx.size;
         double sink = out * 0.09 * 1.2 * fx.size;
         double cx = f.rx(fx.x());
         double cy = f.ry(fx.y() + 0.045 * fx.size + drop - sink);
         double cz = f.rz(fx.z());
         float a = fx.alpha() * Easing.clamp01(in * 4.0F) * (1.0F - out) * NearCamera.fade(cam, cx, cy, cz);
         if (!(a <= 0.003F)) {
            double grow = 1.0 + 0.3 * (1.0 - in);
            Clockwork.ring(
               cx,
               cy,
               cz,
               0.0,
               1.0,
               0.0,
               1.0,
               0.0,
               0.0,
               0.0,
               0.0,
               1.0,
               0.72 * fx.size * grow,
               0.92 * fx.size * grow,
               0.09 * fx.size,
               48,
               ColorUtil.withAlpha(col, a),
               0.06F + 0.4F * (float)Math.exp(-Math.abs(t - 0.18F) * 20.0)
            );
         }
      }
   }

   private void dust(LandingFx fx, ChainSlamStyle.State s) {
      if (!fx.lod) {
         if (s.ring && !s.dusted && fx.styleTime() >= 0.17F) {
            s.dusted = true;
            int n = fx.count(14.0F);

            for (int i = 0; i < n; i++) {
               double ang = (Math.PI * 2) * i / n + fx.rand(70 + i) * 0.4;
               double r = 0.92 * fx.size;
               float v = 0.7F + fx.rand(90 + i) * 0.6F;
               fx.particles
                  .spawn(
                     8,
                     fx.x() + Math.cos(ang) * r,
                     fx.y() + 0.05,
                     fx.z() + Math.sin(ang) * r,
                     (float)Math.cos(ang) * v,
                     0.25F + fx.rand(110 + i) * 0.3F,
                     (float)Math.sin(ang) * v,
                     fx.seconds(0.8F),
                     0.3F * fx.size
                  )
                  .end(0.7F)
                  .drag(2.2F)
                  .color(ColorUtil.lerp(fx.land.blockColor, -4675448, 0.5F))
                  .alpha(0.55F * fx.alpha())
                  .wind(0.3F);
            }
         }
      }
   }

   private void glow(LandingFx fx, FxFrame f, ChainSlamStyle.State s) {
      Camera cam = Vfx.camera();
      float t = fx.styleTime();
      float alpha = fx.alpha();
      int warm = -6237;
      float run;
      if (s.physics && (run = Easing.clamp01((t - whipEnd(s)) / 0.22F)) > 0.0F && run < 1.0F) {
         for (int k = 0; k < s.n; k++) {
            int pts = s.points[k];
            along(s.rope[k], pts, run * (pts - 1), this.a);
            double x = f.rx(this.a[0]);
            double y = f.ry(this.a[1] + 0.05);
            double z = f.rz(this.a[2]);
            float k2 = (float)Math.sin(Math.PI * run) * alpha * NearCamera.fade(cam, x, y, z);
            Vfx.sprite(Textures.SPARKLE, x, y, z, 0.35 * Math.sqrt(fx.size), run * 3.0F, core(warm, 0.8F * k2));
         }
      }

      float flash;
      if (s.ring && (flash = (float)Math.exp(-Math.abs(t - 0.18F) * 16.0) * Easing.clamp01((t - 0.12F) * 30.0F)) > 0.01F) {
         double x = f.rx(fx.x());
         double y = f.ry(fx.y() + 0.1);
         double z = f.rz(fx.z());
         Vfx.sprite(Textures.GLOW, x, y, z, 2.6 * fx.size, 0.0F, alpha(warm, 0.35F * flash * alpha * NearCamera.fade(cam, x, y, z)));
      }

      float end;
      float last;
      if (this.pullBack.get()
         && (last = (float)Math.exp(-Math.abs(t - (end = s.jump ? 0.5F : 1.0F)) * 14.0) * Easing.clamp01((t - end + 0.15F) * 8.0F)) > 0.01F) {
         double x = f.rx(fx.x());
         double y = f.ry(fx.y() + 0.12);
         double z = f.rz(fx.z());
         float k = last * alpha * NearCamera.fade(cam, x, y, z);
         Vfx.sprite(Textures.GLOW, x, y, z, 1.2 * Math.sqrt(fx.size), 0.0F, alpha(warm, 0.4F * k));
         Vfx.sprite(Textures.SPARKLE, x, y, z, 0.7 * Math.sqrt(fx.size), t * 2.0F, core(warm, 0.9F * k));
      }
   }

   private static double min(double[] v, int n) {
      double m = Double.MAX_VALUE;

      for (int i = 0; i < n; i++) {
         m = Math.min(m, v[i]);
      }

      return m;
   }

   private static double max(double[] v, int n) {
      double m = 0.0;

      for (int i = 0; i < n; i++) {
         m = Math.max(m, v[i]);
      }

      return m;
   }

   private static final class Floor implements Surface {
      Terrain.HeightField hf;
      double lift;

      private Floor() {
      }

      @Override
      public double at(double x, double y, double z) {
         double h = this.hf.at(x, z);
         return Double.isNaN(h) ? h : h + this.lift;
      }
   }

   public static enum Ring implements Displayable {
      BIG_DROPS("On big drops", "From 4 blocks of fall"),
      ALWAYS("Always", "On every landing"),
      NEVER("Never", "No ring");

      private final String displayName;
      private final String description;

      private Ring(String displayName, String description) {
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
      int n;
      final double[] dx = new double[8];
      final double[] dz = new double[8];
      final double[] len = new double[8];
      final int[] points = new int[8];
      final Chains.Rope[] rope = new Chains.Rope[8];
      final ChainSlamStyle.Floor floor = new ChainSlamStyle.Floor();
      double link;
      boolean physics;
      boolean ring;
      boolean jump;
      float lastTime = -1.0F;
      long frame = -1L;
      boolean dusted;
      boolean flashed;

      State() {
         for (int i = 0; i < 8; i++) {
            this.rope[i] = new Chains.Rope();
         }
      }
   }
}
