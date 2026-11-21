package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.Locale;
import xyz.daikirai.render.furiah.fx.kit.Paper;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class PaperCranesEffect extends AuraEffect {
   private static final int[] PASTELS = new int[]{-4855081, -3682582, -9535, -1904437, -801541, -6241025, -3672, -14619};
   public final ModeSetting<PaperCranesEffect.PaperColors> paperColors;
   public final SliderSetting cranes;
   public final ModeSetting<PaperCranesEffect.Flight> flight;
   public final SliderSetting flapSpeed;
   public final BooleanSetting perch;
   public final BooleanSetting paperTexture;
   private static final int MAX_CRANES = 9;
   private final Paper.Pose pose = new Paper.Pose();
   private final double[] cx = new double[9];
   private final double[] cy = new double[9];
   private final double[] cz = new double[9];
   private final double[] fx = new double[9];
   private final double[] fy = new double[9];
   private final double[] fz = new double[9];
   private final double[] ux = new double[9];
   private final double[] uy = new double[9];
   private final double[] uz = new double[9];
   private final double[] rx = new double[9];
   private final double[] ry = new double[9];
   private final double[] rz = new double[9];
   private final double[] scale = new double[9];
   private final double[] fold = new double[9];
   private final double[] flap = new double[9];
   private final float[] alphaOf = new float[9];
   private final int[] colorOf = new int[9];
   private final double[] path = new double[3];
   private final double[] pathA = new double[3];
   private final double[] pathB = new double[3];
   private int count;
   private final float[] hsb = new float[3];

   public PaperCranesEffect() {
      super(
         EffectType.PAPER_CRANES,
         "papercranes",
         new ThemeTint(),
         true
      );
      this.paperColors = this.setting(
         new ModeSetting<>(
            "papercranes.paperColors",
            "Paper Colors",
            "Each crane a different pastel, all the Paper color, or the Paper color's hue turning across the flock.",
            PaperCranesEffect.PaperColors.class,
            PaperCranesEffect.PaperColors.PASTEL
         )
      );
      this.cranes = this.look(this.setting(new SliderSetting("papercranes.cranes", "Cranes", "How many cranes fly round the target.", 3.0, 9.0, 6.0, 1.0)));
      this.flight = this.setting(
         new ModeSetting<>(
               "papercranes.flight",
               "Flight",
               "How the cranes fly: round the target, each its own way, or rising and dipping round it.",
               PaperCranesEffect.Flight.class,
               PaperCranesEffect.Flight.ORBIT
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.flapSpeed = this.look(
         this.setting(new SliderSetting("papercranes.flapSpeed", "Flap Speed", "How fast the wings beat.", 50.0, 200.0, 100.0, 5.0).unit("%"))
      );
      this.perch = this.setting(new BooleanSetting("papercranes.perch", "Perch", "Now and then a crane lands on the target's shoulder for a moment.", true));
      this.paperTexture = this.setting(new BooleanSetting("papercranes.paperTexture", "Paper Texture", "The fine fibers of the paper.", true));
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.SOLID) {
         float alpha = this.intensity(c);
         if (!(alpha <= 0.003F)) {
            this.layout(c);
            MeshBatch.begin(Material.CUSTOM, Finish.MATTE, c.light, 0.08F, -1, false, true);

            for (int k = 0; k < this.count; k++) {
               this.crane(c, k, alpha);
            }

            MeshBatch.end();
            if (this.paperTexture.get()) {
               RenderState.decal(true);

               try {
                  for (int var8 = 0; var8 < this.count; var8++) {
                     this.fibersOn(c, var8, alpha);
                  }
               } finally {
                  RenderState.decal(false);
               }
            }
         }
      }
   }

   private void layout(EffectContext c) {
      double s = this.size(c);
      double t = c.time;
      double spd = this.speed();
      int n;
      this.count = n = this.cranes.getInt();
      float out = c.outro;
      double perchEvery = 6.5;
      double perchLong = 3.0;
      long perchIndex = (long)Math.floor((t - 2.0) / perchEvery);
      double perchAt = t - 2.0 - perchIndex * perchEvery;
      int percher = this.perch.get() && t > 2.0 && perchAt < perchLong ? (int)(perchIndex % n) : -1;
      int base = this.color(c, 0.5F);

      for (int k = 0; k < n; k++) {
         this.pathAt(c, k, n, t * spd, s, this.path);
         this.pathAt(c, k, n, t * spd - 0.05, s, this.pathA);
         this.pathAt(c, k, n, t * spd + 0.05, s, this.pathB);
         double px = this.path[0];
         double py = this.path[1];
         double pz = this.path[2];
         double hx = this.pathB[0] - this.pathA[0];
         double hy = this.pathB[1] - this.pathA[1];
         double hz = this.pathB[2] - this.pathA[2];
         double kx = this.pathB[0] - 2.0 * px + this.pathA[0];
         double kz = this.pathB[2] - 2.0 * pz + this.pathA[2];
         double sc = 0.13 * s;
         double hl = Math.sqrt(hx * hx + hz * hz) + 1.0E-9;
         double side = (kx * -hz + kz * hx) / hl;
         double bank = clamp(-side / Math.max(1.0E-4, hl * hl) * 1.6, -0.6, 0.6);
         if (k == percher) {
            double yaw = Math.toRadians(c.bodyYaw);
            side = (perchIndex & 1L) == 0L ? 1.0 : -1.0;
            double sx = c.x - Math.cos(yaw) * 0.3 * side;
            double sy = c.y + 1.42 + sc * 0.28;
            double sz = c.z - Math.sin(yaw) * 0.3 * side;
            double w = Easing.smoothstep(0.0F, 0.7F, (float)perchAt) * (1.0F - Easing.smoothstep(2.2F, 3.0F, (float)perchAt));
            px += (sx - px) * w;
            py += (sy - py) * w + Math.sin(w * Math.PI) * 0.25;
            pz += (sz - pz) * w;
            double fxs = -Math.sin(yaw);
            double fzs = Math.cos(yaw);
            hx += (fxs - hx) * w;
            hy *= 1.0 - w;
            hz += (fzs - hz) * w;
            bank *= 1.0 - w;
         }

         if ((hl = Math.sqrt(hx * hx + hy * hy + hz * hz)) < 1.0E-9) {
            hx = 1.0;
            hy = 0.0;
            hz = 0.0;
            hl = 1.0;
         }

         double var97;
         double r0x = -(var97 = hz / hl);
         double var95;
         double r0z = var95 = hx / hl;
         double rl = Math.sqrt(r0x * r0x + r0z * r0z) + 1.0E-9;
         double var96;
         double var103;
         double u0x = -(var103 = r0z / rl) * (var96 = hy / hl);
         double var101;
         double u0y = var103 * var95 - (var101 = r0x / rl) * var97;
         double u0z = var101 * var96;
         double cb = Math.cos(bank);
         double sb = Math.sin(bank);
         double rrx = var101 * cb + u0x * sb;
         double rry = u0y * sb;
         double rrz = var103 * cb + u0z * sb;
         double uux = u0x * cb - var101 * sb;
         double uuy = u0y * cb;
         double uuz = u0z * cb - var103 * sb;
         double introFold = Easing.inOutCubic(Easing.clamp01((t - 0.06 * k) / 0.3));
         double unfold = Easing.smoothstep(0.0F, 0.45F, out);
         double f = introFold * (1.0 - unfold);
         if (out > 0.0F) {
            double fall = out * out * 1.1;
            py -= fall;
            px += rrx * Math.sin(out * 9.0 + k) * 0.12 * out;
            pz += rrz * Math.sin(out * 9.0 + k) * 0.12 * out;
         }

         this.cx[k] = px;
         this.cy[k] = py;
         this.cz[k] = pz;
         double flatTurn = (1.0 - f) * (out > 0.0F ? out * 4.0 + k : 0.0);
         double ct = Math.cos(flatTurn);
         double st = Math.sin(flatTurn);
         this.fx[k] = var95 * ct + rrx * st;
         this.fy[k] = var96 * ct + rry * st;
         this.fz[k] = var97 * ct + rrz * st;
         this.rx[k] = rrx * ct - var95 * st;
         this.ry[k] = rry * ct - var96 * st;
         this.rz[k] = rrz * ct - var97 * st;
         this.ux[k] = uux;
         this.uy[k] = uuy;
         this.uz[k] = uuz;
         this.scale[k] = sc;
         this.fold[k] = f;
         double period = 1.2 / Math.max(0.25, this.flapSpeed.getFloat() / 100.0 * spd);
         double cyc = (t + k * 0.37) % period / period;
         double beat = cyc < 0.4 ? Math.sin(cyc / 0.4 * Math.PI * 2.0 * 2.0) * 0.85 : 0.18 + 0.05 * Math.sin(t * 2.0 + k);
         if (k == percher) {
            double w = Easing.smoothstep(0.5F, 0.8F, (float)perchAt) * (1.0F - Easing.smoothstep(2.1F, 2.4F, (float)perchAt));
            beat += (0.95 - beat) * w;
         }

         this.flap[k] = beat;
         this.alphaOf[k] = (float)(c.keep.solid(px, py, pz) * (1.0 - Easing.smoothstep(0.7F, 1.0F, out)));
         this.colorOf[k] = this.paperColor(base, k, n);
      }
   }

   public String stateForTest(EffectContext c) {
      this.layout(c);
      StringBuilder sb = new StringBuilder(this.count + " cranes, folded");

      for (int k = 0; k < this.count; k++) {
         sb.append(String.format(Locale.ROOT, " %.2f", this.fold[k]));
      }

      double dx = this.cx[0] - c.x;
      double dz = this.cz[0] - c.z;
      sb.append(String.format(Locale.ROOT, "; crane 1 %.2f up, %.2f out", this.cy[0] - c.y, Math.sqrt(dx * dx + dz * dz)));
      return sb.toString();
   }

   private void pathAt(EffectContext c, int k, int n, double t, double s, double[] out) {
      double radius = 0.9 * (0.8 + 0.2 * s);
      double phase = (Math.PI * 2) * k / n;
      double hk = hash(k, 41);
      double hk2 = hash(k, 43);
      switch ((PaperCranesEffect.Flight)this.flight.get()) {
         case SWARM: {
            double a = t * (0.55 + 0.35 * hk) + phase * 1.7;
            double b = t * (0.37 + 0.25 * hk2) + phase;
            double r = radius * (0.8 + 0.25 * Math.sin(b * 1.3 + k));
            out[0] = c.x + Math.cos(a) * r;
            out[1] = c.y + 1.15 + 0.5 * Math.sin(b) * (0.8 + 0.4 * hk);
            out[2] = c.z + Math.sin(a) * r;
            break;
         }
         case FIGURE_EIGHT: {
            double a = t * 0.8 + phase;
            double r = radius * (1.0 + 0.06 * Math.sin(a * 3.0 + k));
            out[0] = c.x + Math.cos(a) * r;
            out[1] = c.y + 1.2 + 0.42 * Math.sin(2.0 * a);
            out[2] = c.z + Math.sin(a) * r;
            break;
         }
         default: {
            double a = t * (0.7 + 0.18 * hk) + phase;
            double r = radius * (1.0 + 0.08 * Math.sin(a * 2.0 + k * 1.3));
            out[0] = c.x + Math.cos(a) * r;
            out[1] = c.y + 0.65 + 1.15 * ((k + 0.5) / n) + 0.08 * Math.sin(a * 3.0 + k);
            out[2] = c.z + Math.sin(a) * r;
         }
      }
   }

   private int paperColor(int base, int k, int n) {
      switch ((PaperCranesEffect.PaperColors)this.paperColors.get()) {
         case ONE:
            return base;
         case GRADIENT:
            float t = n <= 1 ? 0.0F : (float)k / (n - 1);
            return Paper.hueTurn(base, (t - 0.5F) * 0.33F, this.hsb);
         default:
            return k == 0 ? base : PASTELS[(k - 1) % PASTELS.length];
      }
   }

   private Paper.Pose pose(int k) {
      this.pose.x = this.cx[k];
      this.pose.y = this.cy[k];
      this.pose.z = this.cz[k];
      this.pose.fx = this.fx[k];
      this.pose.fy = this.fy[k];
      this.pose.fz = this.fz[k];
      this.pose.ux = this.ux[k];
      this.pose.uy = this.uy[k];
      this.pose.uz = this.uz[k];
      this.pose.rx = this.rx[k];
      this.pose.ry = this.ry[k];
      this.pose.rz = this.rz[k];
      this.pose.scale = this.scale[k];
      this.pose.fold = this.fold[k];
      this.pose.flap = this.flap[k];
      return this.pose;
   }

   private void crane(EffectContext c, int k, float alpha) {
      float a = alpha * this.alphaOf[k];
      if (!(a <= 0.003F) && !(this.scale[k] <= 1.0E-4)) {
         Paper.facets(Paper.CRANE, this.pose(k), Math.round(255.0F * Easing.clamp01(a)) << 24 | this.colorOf[k] & 16777215);
      }
   }

   private void fibersOn(EffectContext c, int k, float alpha) {
      float a = alpha * this.alphaOf[k] * 0.55F;
      if (!(a <= 0.01F) && !(this.scale[k] <= 1.0E-4)) {
         Paper.fibers(Paper.CRANE, this.pose(k), Math.round(255.0F * a) << 24 | ColorUtil.shade(this.colorOf[k], 0.55F * c.light) & 16777215);
      }
   }

   private static double clamp(double v, double lo, double hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }

   public static enum Flight implements Displayable {
      ORBIT("Orbit", "Round the target at different heights"),
      SWARM("Swarm", "Each on a wandering path of its own"),
      FIGURE_EIGHT("Figure-eight", "Round the target, rising and dipping twice a lap");

      private final String displayName;
      private final String description;

      private Flight(String displayName, String description) {
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

   public static enum PaperColors implements Displayable {
      PASTEL("Pastel set", "Each crane a different pastel"),
      ONE("One color", "Every crane the Paper color"),
      GRADIENT("Gradient across the flock", "The Paper color, its hue turning crane by crane");

      private final String displayName;
      private final String description;

      private PaperColors(String displayName, String description) {
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
