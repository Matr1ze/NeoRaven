package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
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

public final class LightningStyle extends TrailStyleBase {
   public final ThemeTint glowColor;
   public final SliderSetting arcs;
   public final SliderSetting jaggedness;
   public final SliderSetting flicker;
   public final SliderSetting branches;
   private final Rng rng = new Rng();
   private static final int MAX_KNOTS = 96;
   private static final double[] KX = new double[96];
   private static final double[] KY = new double[96];
   private static final double[] KZ = new double[96];
   private static final double[] KSX = new double[96];
   private static final double[] KSY = new double[96];
   private static final double[] KSZ = new double[96];
   private static final double[] KUX = new double[96];
   private static final double[] KUY = new double[96];
   private static final double[] KUZ = new double[96];
   private static final float[] KW = new float[96];
   private static final float[] KA = new float[96];
   private static final float[] KU = new float[96];
   private static final float[] KS = new float[96];

   public LightningStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.LIGHTNING,
         spec().size(0.01, 0.15, 0.035, 0.005).length(0.5).taper(30.0).anchor(TrailAnchor.FEET, false).animated()
      );
      this.glowColor = this.secondColor("glowColor", "Glow", "The colored glow around the bolts.", -9798401, false);
      this.arcs = this.setting(new SliderSetting("lightning.arcs", "Arcs", "How many bolts crackle along the path at once.", 1.0, 4.0, 2.0, 1.0));
      this.jaggedness = this.setting(new SliderSetting("lightning.jaggedness", "Jaggedness", "How wildly the bolts zigzag.", 0.0, 100.0, 55.0, 1.0).unit("%"));
      this.flicker = this.setting(
         new SliderSetting("lightning.flicker", "Flicker Rate", "How often the bolts are drawn anew (100%: every 50 ms).", 25.0, 300.0, 100.0, 5.0).unit("%")
      );
      this.branches = this.setting(
         new SliderSetting("lightning.branches", "Branches", "Small branches flicking off the bolts.", 0.0, 100.0, 40.0, 1.0).unit("%")
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.jaggedness);
      out.add(this.branches);
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      TrailShape s = p.shape;
      float glowAmount = this.glow.getFloat() / 100.0F;
      float anim = this.animSpeed.getFloat() / 100.0F;
      long slot = (long)Math.floor(f.now * 20.0 * this.flicker.getFloat() / 100.0 * anim);
      int count = this.arcs.getInt();
      float jag = this.jaggedness.getFloat() / 100.0F;

      for (int k = 0; k < s.runs; k++) {
         int n = this.fill(p, f, k, this.size.getFloat(), -1.0F, 0.0F);
         int knots;
         if (n >= 2 && (knots = knots(s, k, n)) >= 2) {
            for (int a = 0; a < count; a++) {
               int seed = (int)(slot * 31L + a * 7919 + k * 104729 + p.entityId * 13);
               this.bolt(f, knots, seed, jag, 0.22, glowAmount, 1.0F, p, s.length);
               if (this.branches.getFloat() > 0.0F) {
                  this.branchesOf(f, knots, seed, jag, glowAmount, p);
               }
            }
         }
      }

      this.sparks(p, f);
   }

   private static int knots(TrailShape s, int k, int n) {
      int from = s.runStart[k];
      int m = 0;
      float lastAlong = Float.NaN;

      for (int j = 0; j < n && m < 96; j++) {
         float along = s.along[from + j];
         if (j == 0 || j == n - 1 || Float.isNaN(lastAlong) || !(lastAlong - along < 0.22F)) {
            lastAlong = along;
            KX[m] = Vfx.X[j];
            KY[m] = Vfx.Y[j];
            KZ[m] = Vfx.Z[j];
            KW[m] = Vfx.W[j];
            KA[m] = POINT_ALPHA[j];
            KU[m] = s.length > 0.0F ? Math.min(1.0F, along / s.length) : 0.0F;
            KS[m] = s.speed[from + j];
            m++;
         }
      }

      for (int i = 0; i < m; i++) {
         int a = Math.max(0, i - 1);
         int b = Math.min(m - 1, i + 1);
         double tx = KX[b] - KX[a];
         double ty;
         double tz;
         double tl = Math.sqrt(tx * tx + (ty = KY[b] - KY[a]) * ty + (tz = KZ[b] - KZ[a]) * tz);
         if (tl < 1.0E-6) {
            tx = 0.0;
            ty = 0.0;
            tz = 1.0;
         } else {
            tx /= tl;
            ty /= tl;
            tz /= tl;
         }

         double sx = -tz;
         double sl = Math.sqrt(sx * sx + tx * tx);
         double sz;
         if (sl < 1.0E-4) {
            sx = 1.0;
            sz = 0.0;
         } else {
            sx /= sl;
            sz = tx / sl;
         }

         KSX[i] = sx;
         KSY[i] = 0.0;
         KSZ[i] = sz;
         KUX[i] = -sz * ty;
         KUY[i] = sz * tx - sx * tz;
         KUZ[i] = sx * ty;
      }

      return m;
   }

   private void bolt(TrailFrame f, int knots, int seed, float jag, double spread, float glowAmount, float alphaScale, PlayerTrail p, float length) {
      int n = 0;

      for (int i = 0; i < knots && n < 1024; i++) {
         double amount = i != 0 && i != knots - 1 ? jag * spread : 0.0;
         double o1 = (Rng.hash(seed, i * 2) * 2.0F - 1.0F) * amount;
         double o2 = (Rng.hash(seed, i * 2 + 1) * 2.0F - 1.0F) * amount;
         Vfx.X[n] = KX[i] + KSX[i] * o1 + KUX[i] * o2;
         Vfx.Y[n] = KY[i] + KSY[i] * o1 + KUY[i] * o2;
         Vfx.Z[n] = KZ[i] + KSZ[i] * o1 + KUZ[i] * o2;
         Vfx.W[n] = KW[i];
         Vfx.C[n] = ColorUtil.mulAlpha(this.slotColor(this.glowColor, KU[i], f.colorTime), KA[i] * alphaScale);
         n++;
      }

      this.drawBolt(f, n, glowAmount, knots);
   }

   private void drawBolt(TrailFrame f, int n, float glowAmount, int knots) {
      if (n >= 2) {
         TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
         RenderState.additive();
         TrailStrip.draw(Textures.LINE_GLOW, 0, n, 3.0F + 7.0F * glowAmount, 0.0, 0.45F + 0.4F * glowAmount);

         for (int j = 0; j < n; j++) {
            int a = Vfx.C[j] >>> 24;
            int c = this.slotColor(this.color, KU[Math.min(j, knots - 1)], f.colorTime);
            Vfx.C[j] = a << 24 | ColorUtil.core(c) & 16777215;
         }

         TrailStrip.draw(Textures.LINE_CORE, 0, n, 1.0F, 0.0, 1.0F);
      }
   }

   private void branchesOf(TrailFrame f, int knots, int seed, float jag, float glowAmount, PlayerTrail p) {
      float chance = this.branches.getFloat() / 100.0F * 0.45F;

      for (int i = 1; i < knots - 1; i++) {
         if (!(Rng.hash(seed + 977, i) > chance) && !(KA[i] < 0.05F)) {
            double dirA = Rng.hash(seed + 31, i) * Math.PI * 2.0;
            double dx = KSX[i] * Math.cos(dirA) + KUX[i] * Math.sin(dirA);
            double dy = KSY[i] * Math.cos(dirA) + KUY[i] * Math.sin(dirA);
            double dz = KSZ[i] * Math.cos(dirA) + KUZ[i] * Math.sin(dirA);
            double len = 0.15 + 0.3 * Rng.hash(seed + 59, i);
            int n = 0;

            for (int j = 0; j <= 3; j++) {
               double t = j / 3.0;
               double wob = j == 0 ? 0.0 : (Rng.hash(seed + 101 + j, i) * 2.0F - 1.0F) * 0.06 * (0.5 + jag);
               Vfx.X[n] = KX[i] + dx * len * t + KUX[i] * wob;
               Vfx.Y[n] = KY[i] + dy * len * t + KUY[i] * wob;
               Vfx.Z[n] = KZ[i] + dz * len * t + KUZ[i] * wob;
               Vfx.W[n] = KW[i] * (float)(0.7 * (1.0 - t * 0.6));
               Vfx.C[n] = ColorUtil.mulAlpha(this.slotColor(this.glowColor, KU[i], f.colorTime), KA[i] * (float)(0.8 * (1.0 - t)));
               n++;
            }

            this.drawBolt(f, n, glowAmount * 0.7F, knots);
         }
      }
   }

   private void sparks(PlayerTrail p, TrailFrame f) {
      int due = this.piecesDue(p, 2.5, 4);

      for (int i = 0; i < due; i++) {
         if (this.module.piecesFull(f)) {
            return;
         }

         this.rng.seed((int)(f.now * 1000.0) + i * 17 + p.entityId);
         double x = p.pose.x;
         double y = p.pose.y + 0.05;
         double z = p.pose.z;
         if (!(f.nearFade(x, y, z) < 0.5F)) {
            FxParticles pool = f.pieces(this.type);
            pool.spawn(
                  1,
                  x,
                  y,
                  z,
                  this.rng.signed() * 1.6F,
                  1.2F + this.rng.next() * 1.6F,
                  this.rng.signed() * 1.6F,
                  0.18F + this.rng.next() * 0.2F,
                  0.03F + this.rng.next() * 0.03F
               )
               .gravity(14.0F)
               .ground(p.pose.y, 1, 0.35F)
               .alpha(this.opacity.getFloat() / 100.0F * p.alpha)
               .flags(2)
               .sway(0.0F, this.rng.next() * 10.0F);
            this.colorPiece(pool, this.rng.next(), 0.0F, p.speed);
         }
      }
   }
}
