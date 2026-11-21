package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.trail.PathRecorder;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class StardustStyle extends TrailStyleBase {
   public final ThemeTint stars;
   public final SliderSetting nebula;
   public final SliderSetting starCount;
   public final SliderSetting twinkle;
   public final SliderSetting drift;
   private final Rng rng = new Rng();
   private static final float[] BASE_W = new float[1024];

   public StardustStyle(FuriahTrails module) {
      super(module, TrailStyle.STARDUST, spec().size(0.1, 1.5, 0.55, 0.01).taper(55.0).anchor(TrailAnchor.WAIST, false).animated());
      this.stars = this.secondColor("stars", "Stars", "The stars' color (white at first).", -1, true);
      this.nebula = this.setting(new SliderSetting("stardust.nebula", "Nebula Strength", "How thick the drifting clouds are.", 0.0, 100.0, 70.0, 1.0).unit("%"));
      this.starCount = this.setting(
         new SliderSetting("stardust.starCount", "Star Count", "How many stars twinkle in the band.", 0.0, 200.0, 100.0, 5.0).unit("%")
      );
      this.twinkle = this.setting(new SliderSetting("stardust.twinkle", "Twinkle Speed", "How fast the stars twinkle.", 25.0, 300.0, 100.0, 5.0).unit("%"));
      this.drift = this.setting(new SliderSetting("stardust.drift", "Drift", "How fast the clouds drift along the band.", 0.0, 100.0, 40.0, 1.0).unit("%"));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.nebula);
      out.add(this.starCount);
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      TrailShape s = p.shape;
      float neb = this.nebula.getFloat() / 100.0F;
      float glowAmount = this.glow.getFloat() / 100.0F;
      double flow = f.now * (0.15 + 0.85 * this.drift.getFloat() / 100.0) * this.animSpeed.getFloat() / 100.0;

      for (int k = 0; k < s.runs; k++) {
         int n = this.fill(p, f, k, this.size.getFloat(), -1.0F, 0.0F);
         if (n >= 2) {
            int from = s.runStart[k];

            for (int j = 0; j < n; j++) {
               BASE_W[j] = Vfx.W[j];
            }

            TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);

            for (int strand = 0; strand < 3; strand++) {
               double off = (strand - 1) * 0.33;

               for (int j2 = 0; j2 < n; j2++) {
                  float along = s.along[from + j2];
                  float cloud = noise((float)(along * 1.4 - flow + strand * 7.31), strand * 3.7F);
                  float dense = 1.0F - neb + neb * cloud;
                  int c = this.colorAt(
                     Math.min(1.0F, along / Math.max(0.001F, s.length)), 0.0F, s.speed[from + j2], f.colorTime, (strand - 1) * 0.12F + (cloud - 0.5F) * 0.25F
                  );
                  Vfx.C[j2] = ColorUtil.mulAlpha(c, POINT_ALPHA[j2] * dense);
                  Vfx.W[j2] = BASE_W[j2] * 0.55F;
               }

               RenderState.translucent(false);
               TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.2F, off / 0.55, 0.32F);
               RenderState.additive();
               TrailStrip.draw(Textures.LINE_GLOW, 0, n, 1.6F + 1.4F * glowAmount, off / 0.55, 0.38F * (0.5F + glowAmount));
            }

            for (int var20 = 0; var20 < n; var20++) {
               Vfx.W[var20] = BASE_W[var20];
            }
         }
      }

      this.starsOf(p, f);
      this.drifting(p, f);
   }

   private static float noise(float x, float seed) {
      return 0.65F * value(x, seed) + 0.35F * value(x * 2.3F + 13.1F, seed + 5.7F);
   }

   private static float value(float x, float seed) {
      int i = (int)Math.floor(x);
      float t = x - i;
      float a = Rng.hash(i, (int)(seed * 1000.0F));
      float b = Rng.hash(i + 1, (int)(seed * 1000.0F));
      float u = t * t * (3.0F - 2.0F * t);
      return a + (b - a) * u;
   }

   private void starsOf(PlayerTrail p, TrailFrame f) {
      PathRecorder r = p.path;
      if (r != null && !p.ghost) {
         float perPoint = 0.55F * this.starCount.getFloat() / 100.0F;
         if (!(perPoint <= 0.0F)) {
            float maxAge = Math.max(0.05F, p.maxAge);
            double tw = this.twinkle.getFloat() / 100.0F * this.animSpeed.getFloat() / 100.0F;
            float half = this.size.getFloat() * 0.5F * p.widthScale;
            float op = this.opacity.getFloat() / 100.0F * p.alpha;
            int count = r.count();

            for (int i = 1; i < count; i++) {
               double dz;
               double dx;
               double dl;
               int seed;
               double age;
               if ((r.flags(i) & 64) == 0
                  && !(r.time(i) < p.style.since)
                  && !((age = f.now - r.time(i)) > maxAge)
                  && !(age < 0.0)
                  && !(Rng.hash(seed = (int)(r.distance(i) * 1000.0), 7) > perPoint)
                  && !((dl = Math.sqrt((dx = r.x(i) - r.x(i - 1)) * dx + (dz = r.z(i) - r.z(i - 1)) * dz)) < 1.0E-6)) {
                  double across = (Rng.hash(seed, 11) * 2.0F - 1.0F) * half;
                  double up = (Rng.hash(seed, 13) * 2.0F - 1.0F) * half * 0.6;
                  double wx = r.x(i) - dz / dl * across;
                  double wy = r.y(i) + up;
                  double wz = r.z(i) + dx / dl * across;
                  float fade = this.fade((float)(age / maxAge));
                  float a = op
                     * fade
                     * (0.45F + 0.55F * (float)Math.max(0.0, Math.sin(f.now * tw * (6.0 + Rng.hash(seed, 17) * 8.0) + Rng.hash(seed, 19) * 6.283)))
                     * f.nearFade(wx, wy, wz)
                     * f.fog(wx, wy, wz);
                  if (!(a <= 0.01F)) {
                     float sz = (0.05F + 0.07F * Rng.hash(seed, 23)) * Math.max(0.5F, this.size.getFloat() / 0.55F);
                     int c = this.starColor(Rng.hash(seed, 29), f.colorTime);
                     Vfx.sprite(Textures.SPARKLE, wx - f.viewX, wy - f.viewY, wz - f.viewZ, sz * 2.0F, (float)(f.now * 0.6), ColorUtil.mulAlpha(c, a));
                     Vfx.sprite(Textures.GLOW, wx - f.viewX, wy - f.viewY, wz - f.viewZ, sz * 3.0F, 0.0F, ColorUtil.mulAlpha(c, a * 0.35F));
                  }
               }
            }
         }
      }
   }

   private void drifting(PlayerTrail p, TrailFrame f) {
      int due = this.piecesDue(p, 1.2 * this.starCount.getFloat() / 100.0, 3);
      TrailShape s = p.shape;
      if (due != 0 && s.runs != 0) {
         int k = s.runs - 1;
         int from = s.runStart[k];
         int to = s.runEnd[k];

         for (int i = 0; i < due; i++) {
            if (this.module.piecesFull(f)) {
               return;
            }

            this.rng.seed((int)(f.now * 997.0) + i * 31 + p.entityId);
            int j = from + (int)(this.rng.next() * (to - from));
            double x = s.x[j];
            double y = s.y[j];
            double z = s.z[j];
            if (!(f.nearFade(x, y, z) < 0.5F)) {
               float sz = 0.07F + 0.05F * this.rng.next();
               FxParticles pool = f.pieces(this.type);
               pool.spawn(5, x, y, z, this.rng.signed() * 0.45F, this.rng.signed() * 0.3F + 0.1F, this.rng.signed() * 0.45F, 0.8F + this.rng.next() * 0.7F, sz)
                  .drag(0.8F)
                  .alpha(this.opacity.getFloat() / 100.0F * p.alpha)
                  .flags(2)
                  .sway(0.0F, this.rng.next() * 10.0F)
                  .fade(0.15F, 0.5F)
                  .end(sz * 0.5F);
               if (this.tint == 0 && !(this.light < 0.999F)) {
                  pool.color(this.stars, this.rng.next());
               } else {
                  pool.color(this.starColor(this.rng.next(), f.colorTime));
               }
            }
         }
      }
   }

   private int starColor(float pos, double time) {
      int c = this.tint != 0 ? ColorUtil.lerp(this.tint, -1, 0.6F) : this.stars.getColor(pos, time);
      return this.light < 0.999F ? ColorUtil.shade(c, this.light) : c;
   }
}
