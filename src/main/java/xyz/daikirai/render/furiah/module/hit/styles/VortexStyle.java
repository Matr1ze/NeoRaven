package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class VortexStyle extends HitStyleBase {
   private static final int ARM_POINTS = 18;
   private static final int RIM_POINTS = 56;
   private static final int INNER_OFFSET = 144;
   private static final int RIM_OFFSET = 216;
   public final SliderSetting arms = this.setting(new SliderSetting("vortex.arms", "Arms", "How many spiral arms.", 5.0, 8.0, 6.0, 1.0));
   public final SliderSetting spin = this.setting(new SliderSetting("vortex.spin", "Spin", "How fast it turns.", 0.5, 2.0, 1.0, 0.05).unit("x"));
   public final BooleanSetting rays = this.setting(new BooleanSetting("vortex.rays", "Light Rays", "Soft wedges of light fanning out behind the target.", true));

   public VortexStyle() {
      super(HitStyle.VORTEX, 0.5F, new ThemeTint());
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float a = fx.alpha();
      if (!(a <= 0.003F)) {
         Basis b = fx.basis;
         float size = fx.size * (fx.crit ? 1.25F : 1.0F);
         double maxR = 0.95 * size;
         double r = p < 0.12F
            ? maxR * 0.35 * Math.max(0.0F, Easing.outBack(p / 0.12F, 2.2F))
            : (p < 0.58F ? maxR * (0.35 + 0.65 * Easing.outCubic((p - 0.12F) / 0.46F)) : maxR * (1.0 - Easing.inCubic(Easing.clamp01((p - 0.58F) / 0.32F))));
         double t = fx.styleTime();
         int dir = fx.hit.side;
         double angle = dir * this.spin.get() * (13.0 * t + 22.0 * t * t);
         double twist = 1.7 + 3.4 * Easing.smoothstep(0.55F, 0.92F, p);
         double cx = fx.ix() - b.fx * 0.06;
         double cy = fx.iy() - b.fy * 0.06;
         double cz = fx.iz() - b.fz * 0.06;
         float live = 1.0F - Easing.smoothstep(0.86F, 0.93F, p);
         if (this.rays.get() && !fx.lod) {
            this.drawRays(fx, f, p, a, size);
         }

         if (r > 0.005 && live > 0.0F) {
            float la = a * live;
            RenderState.translucent(false);
            Vfx.sprite(
               Textures.GLOW, f.rx(cx), f.ry(cy), f.rz(cz), r * 1.9, 0.0F, ColorUtil.withAlpha(ColorUtil.shade(this.color.getColor(0.0F), 0.12F), 0.62F * la)
            );
            RenderState.additive();
            Vfx.sprite(Textures.GLOW, f.rx(cx), f.ry(cy), f.rz(cz), r * 3.0, 0.0F, alpha(this.color.getColor(0.2F), 0.22F * la));
            int n = this.arms.getInt();
            this.spiral(f, b, cx, cy, cz, r, angle, twist, dir, n, 0.075 * size, la, 0);
            if (fx.crit) {
               this.spiral(f, b, cx, cy, cz, r * 0.55, -angle * 1.3, twist * 0.8, -dir, 4, 0.055 * size, la, 144);
            }

            this.rim(f, b, cx, cy, cz, r, angle, la, size);
            Neon.flush();
            this.flakes(fx, f, b, cx, cy, cz, r, angle, la);
         }

         float flash;
         if ((flash = Easing.smoothstep(0.84F, 0.9F, p) * (1.0F - Easing.smoothstep(0.9F, 1.0F, p))) > 0.003F) {
            int c = this.color.getColor(0.5F);
            RenderState.translucent(false);
            Vfx.sprite(Textures.FLARE, f.rx(cx), f.ry(cy), f.rz(cz), 1.1 * size * (0.6F + 0.4F * flash), (float)angle, alpha(c, 0.7F * flash * a));
            RenderState.additive();
            Vfx.sprite(Textures.GLOW, f.rx(cx), f.ry(cy), f.rz(cz), 1.3 * size, 0.0F, alpha(c, 0.6F * flash * a));
            Vfx.sprite(Textures.FLARE, f.rx(cx), f.ry(cy), f.rz(cz), 0.8 * size * (0.6F + 0.4F * flash), (float)angle, core(c, flash * a));
         }
      }
   }

   private void spiral(
      FxFrame f, Basis b, double cx, double cy, double cz, double r, double angle, double twist, int dir, int n, double width, float a, int offset
   ) {
      for (int arm = 0; arm < n; arm++) {
         double base = (Math.PI * 2) * arm / n + angle;
         int o = offset + arm * 18;

         for (int k = 0; k < 18; k++) {
            float s = k / 17.0F;
            double rho = r * (0.1 + 0.9 * s);
            double phi = base - dir * twist * s;
            double lx = Math.cos(phi) * rho;
            double ly = Math.sin(phi) * rho;
            Vfx.X[o + k] = f.rx(cx + b.rx * lx + b.ux * ly);
            Vfx.Y[o + k] = f.ry(cy + b.ry * lx + b.uy * ly);
            Vfx.Z[o + k] = f.rz(cz + b.rz * lx + b.uz * ly);
            float taper = (float)Math.pow(Math.sin(Math.PI * s), 0.8);
            Vfx.W[o + k] = (float)(width * (0.25 + 0.75 * taper));
            Vfx.C[o + k] = alpha(this.color.getColor(s), a * (0.25F + 0.75F * taper));
         }

         Neon.add(o, 18, false);
      }
   }

   private void rim(FxFrame f, Basis b, double cx, double cy, double cz, double r, double angle, float a, float size) {
      for (int k = 0; k < 56; k++) {
         double phi = (Math.PI * 2) * k / 56.0;
         double lx = Math.cos(phi) * r;
         double ly = Math.sin(phi) * r;
         int o = 216 + k;
         Vfx.X[o] = f.rx(cx + b.rx * lx + b.ux * ly);
         Vfx.Y[o] = f.ry(cy + b.ry * lx + b.uy * ly);
         Vfx.Z[o] = f.rz(cz + b.rz * lx + b.uz * ly);
         float wave = 0.55F + 0.45F * (float)Math.sin(phi * 3.0 + angle * 1.5);
         Vfx.W[o] = (float)(0.05 * size);
         Vfx.C[o] = alpha(this.color.getColor(loop(k / 56.0F * 0.5F + 0.5F)), a * wave);
      }

      Neon.add(216, 56, true);
   }

   private void flakes(HitFx fx, FxFrame f, Basis b, double cx, double cy, double cz, double r, double angle, float a) {
      for (int pass = 0; pass < 2; pass++) {
         for (int k = 0; k < 9; k++) {
            float appear = Easing.smoothstep(0.06F + fx.rand(k) * 0.1F, 0.2F + fx.rand(k) * 0.1F, fx.progress());
            if (!(appear <= 0.0F)) {
               double phi = (Math.PI * 2) * fx.rand(k + 20) + angle * (1.1 + 0.4 * fx.rand(k + 40));
               double rho = r * (1.05 + 0.4 * fx.rand(k + 60));
               double lx = Math.cos(phi) * rho;
               double ly = Math.sin(phi) * rho;
               double px = cx + b.rx * lx + b.ux * ly;
               double py = cy + b.ry * lx + b.uy * ly;
               double pz = cz + b.rz * lx + b.uz * ly;
               float s = (0.035F + 0.03F * fx.rand(k + 80)) * fx.size;
               int c = this.color.getColor(fx.rand(k + 90));
               if (pass == 0) {
                  Vfx.sprite(Textures.GLOW, f.rx(px), f.ry(py), f.rz(pz), s * 3.5F, 0.0F, alpha(c, 0.35F * a * appear));
               } else {
                  Vfx.sprite(Textures.SPARK, f.rx(px), f.ry(py), f.rz(pz), s, 0.0F, alpha(Neon.tint(c), a * appear));
               }
            }
         }
      }
   }

   private void drawRays(HitFx fx, FxFrame f, float p, float a, float size) {
      float ra = Easing.smoothstep(0.0F, 0.06F, p) * (1.0F - Easing.smoothstep(0.14F, 0.62F, p));
      if (!(ra <= 0.003F)) {
         Basis b = fx.basis;
         double ox = fx.cx() + b.fx * fx.hit.width * 0.35;
         double oy = fx.cy() + b.fy * fx.hit.width * 0.35;
         double oz = fx.cz() + b.fz * fx.hit.width * 0.35;
         float grow = Easing.outCubic(Easing.clamp01(p / 0.3F));
         int count = 6 + Math.round(4.0F * fx.rand(500));

         for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) {
               RenderState.translucent(false);
            } else {
               RenderState.additive();
            }

            for (int k = 0; k < count; k++) {
               double phi = (Math.PI * 2) * (k + 0.5 * fx.rand(k + 510)) / count;
               double spread = 0.8 + 0.4 * fx.rand(k + 520);
               double dx = b.fx * 0.6 + (b.rx * Math.cos(phi) + b.ux * Math.sin(phi)) * spread;
               double dy = b.fy * 0.6 + (b.ry * Math.cos(phi) + b.uy * Math.sin(phi)) * spread;
               double dz = b.fz * 0.6 + (b.rz * Math.cos(phi) + b.uz * Math.sin(phi)) * spread;
               double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
               double length = (1.3 + 0.9 * fx.rand(k + 530)) * size * grow;
               int c = this.color.getColor(0.3F + 0.7F * fx.rand(k + 540));
               wedge(
                  f,
                  ox,
                  oy,
                  oz,
                  dx / len,
                  dy / len,
                  dz / len,
                  length,
                  0.03 * size,
                  0.45 * length,
                  pass == 0 ? alpha(c, 0.3F * ra * a) : alpha(Neon.tint(c), 0.3F * ra * a)
               );
            }
         }
      }
   }

   static void wedge(FxFrame f, double ox, double oy, double oz, double dx, double dy, double dz, double length, double baseWidth, double endWidth, int color) {
      Camera cam = Vfx.camera();
      double bx = f.rx(ox);
      double by = f.ry(oy);
      double bz = f.rz(oz);
      double ez;
      double mz = (bz + (ez = bz + dz * length)) * 0.5 - cam.posZ;
      double ey;
      double my;
      double sx = dy * mz - dz * (my = (by + (ey = by + dy * length)) * 0.5 - cam.posY);
      double sz;
      double ex;
      double mx;
      double sy;
      double sl = Math.sqrt(sx * sx + (sy = dz * (mx = (bx + (ex = bx + dx * length)) * 0.5 - cam.posX) - dx * mz) * sy + (sz = dx * my - dy * mx) * sz);
      if (!(sl < 1.0E-9)) {
         double h0 = baseWidth * 0.5;
         double h1 = endWidth * 0.5;
         double var51;
         double var52;
         double var53;
         Vfx.quadUV(
            Textures.RAY,
            bx - (var53 = sx / sl) * h0,
            by - (var52 = sy / sl) * h0,
            bz - (var51 = sz / sl) * h0,
            0.0,
            0.0,
            color,
            bx + var53 * h0,
            by + var52 * h0,
            bz + var51 * h0,
            1.0,
            0.0,
            color,
            ex + var53 * h1,
            ey + var52 * h1,
            ez + var51 * h1,
            1.0,
            1.0,
            color,
            ex - var53 * h1,
            ey - var52 * h1,
            ez - var51 * h1,
            0.0,
            1.0,
            color
         );
      }
   }
}
