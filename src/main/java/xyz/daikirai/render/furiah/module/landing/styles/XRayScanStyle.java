package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.HashMap;
import java.util.Map.Entry;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Terrain;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Silhouette;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

public final class XRayScanStyle extends LandingStyleBase {
   public final ThemeTint gridColor;
   public final SliderSetting radius;
   public final BooleanSetting ghost;
   public final BooleanSetting grid;
   private final Silhouette.Look look = new Silhouette.Look();
   private final double[] point = new double[3];

   public XRayScanStyle() {
      super(LandingStyle.XRAY_SCAN, 1.5F, 5.0F, new ThemeTint());
      this.gridColor = ThemeTint.detail(-48952);
      this.radius = this.setting(new SliderSetting("xray.radius", "Scan Radius", "How far the scan wave travels.", 3.0, 6.0, 4.5, 0.1).unit(" blocks"));
      this.ghost = this.setting(new BooleanSetting("xray.ghost", "Afterimage", "The neon copy of the player.", true));
      this.grid = this.setting(new BooleanSetting("xray.grid", "Scan Grid", "The grid wave over the block tops.", true));
   }

   @Override
   public void start(LandingFx fx) {
      if (this.grid.get()) {
         fx.data = this.scan(fx);
      }
   }

   private XRayScanStyle.Scan scan(LandingFx fx) {
      WorldClient world = fx.preview ? null : Minecraft.getMinecraft().theWorld;
      double r = this.radius.get() * Math.min(1.4F, Math.max(0.8F, fx.size));
      int cx = (int)Math.floor(fx.x());
      int cz = (int)Math.floor(fx.z());
      int span = (int)Math.ceil(r) + 1;
      HashMap<Long, Float> tops = new HashMap<>();

      for (int dx = -span; dx <= span; dx++) {
         for (int dz = -span; dz <= span; dz++) {
            double mx = cx + dx + 0.5;
            double mz = cz + dz + 0.5;
            double ddx = mx - fx.x();
            double ddz;
            if (!(ddx * ddx + (ddz = mz - fx.z()) * ddz > (r + 0.7) * (r + 0.7))) {
               double h = world == null ? fx.y() : Terrain.surfaceAt(world, mx, mz, fx.y(), 2.0, 2.5);
               if (!Double.isNaN(h)) {
                  tops.put(key(cx + dx, cz + dz), (float)h);
               }
            }
         }
      }

      XRayScanStyle.Scan s = new XRayScanStyle.Scan();
      int cap = tops.size() * 4 + 4;
      s.x1 = new float[cap];
      s.z1 = new float[cap];
      s.x2 = new float[cap];
      s.z2 = new float[cap];
      s.y = new float[cap];
      s.fx = new float[tops.size()];
      s.fz = new float[tops.size()];
      s.fy = new float[tops.size()];

      for (Entry e : tops.entrySet()) {
         int bx = (int)((Long)e.getKey() >> 32);
         int bz = ((Long)e.getKey()).intValue();
         float h = (Float)e.getValue();
         s.fx[s.faces] = bx;
         s.fz[s.faces] = bz;
         s.fy[s.faces] = h;
         s.faces++;
         edge(s, bx, bz, bx, bz + 1, h);
         edge(s, bx, bz, bx + 1, bz, h);
         Float east = tops.get(key(bx + 1, bz));
         if (east == null || Math.abs(east - h) > 0.001) {
            edge(s, bx + 1, bz, bx + 1, bz + 1, h);
         }

         Float south;
         if ((south = tops.get(key(bx, bz + 1))) == null || Math.abs(south - h) > 0.001) {
            edge(s, bx, bz + 1, bx + 1, bz + 1, h);
         }
      }

      return s;
   }

   private static long key(int x, int z) {
      return (long)x << 32 | z & 4294967295L;
   }

   private static void edge(XRayScanStyle.Scan s, float x1, float z1, float x2, float z2, float y) {
      int i = s.edges++;
      s.x1[i] = x1;
      s.z1[i] = z1;
      s.x2[i] = x2;
      s.z2[i] = z2;
      s.y[i] = y;
   }

   @Override
   public void render(LandingFx fx, FxFrame f) {
      float p = fx.progress();
      if (this.grid.get() && fx.data instanceof XRayScanStyle.Scan) {
         this.drawGrid(fx, f, (XRayScanStyle.Scan)fx.data, p);
      }

      if (this.ghost.get()) {
         this.drawGhost(fx, f, p);
      }
   }

   private void drawGrid(LandingFx fx, FxFrame f, XRayScanStyle.Scan s, float p) {
      double r = this.radius.get() * Math.min(1.4F, Math.max(0.8F, fx.size));
      double front = r * Easing.outCubic(Easing.clamp01(p / 0.72F));
      float a = fx.alpha() * (1.0F - Easing.smoothstep(0.72F, 1.0F, p));
      if (!(a <= 0.003F)) {
         RenderState.decal(true);
         RenderState.translucent(false);

         for (int i = 0; i < s.faces; i++) {
            double mx = s.fx[i] + 0.5;
            double mz = s.fz[i] + 0.5;
            float k = glow(Math.sqrt((mx - fx.x()) * (mx - fx.x()) + (mz - fx.z()) * (mz - fx.z())), front, r);
            if (k > 0.02F) {
               int c = this.gridColor.getColor((float)(Math.hypot(mx - fx.x(), mz - fx.z()) / r));
               Vfx.flatQuad(Textures.WHITE, f.rx(mx), f.ry(s.fy[i] + 0.012), f.rz(mz), 0.5, 0.0, 0.0, 0.5, 0.0F, 0.0F, 1.0F, 1.0F, alpha(c, 0.14F * k * a));
            }
         }

         for (int pass = 0; pass < 3; pass++) {
            int tex = pass == 2 ? Textures.LINE_CORE : Textures.LINE_GLOW;
            double w = pass == 0 ? 0.24 : (pass == 1 ? 0.1 : 0.03);
            float pa = pass == 0 ? 0.3F : (pass == 1 ? 0.85F : 0.95F);
            if (pass == 0) {
               RenderState.additive();
            } else {
               RenderState.translucent(false);
            }

            for (int ix = 0; ix < s.edges; ix++) {
               double d1 = Math.hypot(s.x1[ix] - fx.x(), s.z1[ix] - fx.z());
               double d2 = Math.hypot(s.x2[ix] - fx.x(), s.z2[ix] - fx.z());
               float k1 = glow(d1, front, r);
               float k2;
               if (!(k1 + (k2 = glow(d2, front, r)) <= 0.01F)) {
                  int c1 = this.gridColor.getColor((float)(d1 / r));
                  int c2 = this.gridColor.getColor((float)(d2 / r));
                  if (pass == 2) {
                     c1 = Neon.tint(c1);
                     c2 = Neon.tint(c2);
                  }

                  Vfx.flatLine(
                     tex,
                     f.rx(s.x1[ix]),
                     f.rz(s.z1[ix]),
                     f.rx(s.x2[ix]),
                     f.rz(s.z2[ix]),
                     f.ry(s.y[ix] + 0.014),
                     w,
                     alpha(c1, pa * k1 * a),
                     alpha(c2, pa * k2 * a)
                  );
               }
            }
         }

         RenderState.additive();
         RenderState.decal(false);
      }
   }

   private static float glow(double d, double front, double r) {
      double off = (d - front) / 0.5;
      float wave = (float)Math.exp(-off * off);
      float trail = d < front ? 0.3F * (float)Math.exp(-(front - d) * 1.1) : 0.0F;
      float edge = 1.0F - Easing.smoothstep((float)(r * 0.7), (float)r, (float)d);
      return Math.max(wave, trail) * edge;
   }

   private void drawGhost(LandingFx fx, FxFrame f, float p) {
      if (fx.land.pose.valid) {
         float a = fx.alpha() * Easing.smoothstep(0.0F, 0.03F, p) * (1.0F - Easing.smoothstep(0.9F, 1.0F, p));
         if (!(a <= 0.003F)) {
            this.look.reset();
            this.look.color = this.color;
            this.look.alpha = a;
            this.look.fill = 0.55F;
            this.look.rim = 1.0F;
            this.look.inflate = 0.35F;
            this.look.rimWidth = 0.9F;
            float dissolve = Easing.smoothstep(0.5F, 0.95F, p);
            float f2 = this.look.dissolve = dissolve > 0.0F ? dissolve * 1.15F - 0.05F : -1.0F;
            if (fx.land.self && f.firstPerson) {
               this.look.nearInner = 0.7F;
               this.look.nearOuter = 1.7F;
            }

            Silhouette.draw(fx.land.pose, f.viewX, f.viewY, f.viewZ, this.look);
            if (!fx.lod && this.look.dissolve > 0.0F && f.dt > 0.0F) {
               int spawn = Math.min(6, Math.round(f.dt * 110.0F * fx.density));

               for (int i = 0; i < spawn; i++) {
                  float r1 = (float)Math.random();
                  float r3;
                  float r2;
                  float h = Silhouette.randomPointHeight(r1, r2 = (float)Math.random(), r3 = (float)Math.random());
                  if (!(Math.abs(h - this.look.dissolve) > 0.16F)) {
                     Silhouette.randomPoint(r1, r2, r3, this.point);
                     fx.particles
                        .spawn(
                           3,
                           this.point[0],
                           this.point[1],
                           this.point[2],
                           (float)(Math.random() - 0.5) * 0.25F,
                           0.6F + (float)Math.random() * 0.6F,
                           (float)(Math.random() - 0.5) * 0.25F,
                           fx.seconds(0.45F + (float)Math.random() * 0.3F),
                           0.05F
                        )
                        .end(0.01F)
                        .drag(1.2F)
                        .color(this.color, h)
                        .alpha(fx.opacity)
                        .flags(2)
                        .sway(0.0F, (float)Math.random());
                  }
               }
            }
         }
      }
   }

   private static final class Scan {
      int edges;
      float[] x1;
      float[] z1;
      float[] x2;
      float[] z2;
      float[] y;
      int faces;
      float[] fx;
      float[] fz;
      float[] fy;

      private Scan() {
      }
   }
}
