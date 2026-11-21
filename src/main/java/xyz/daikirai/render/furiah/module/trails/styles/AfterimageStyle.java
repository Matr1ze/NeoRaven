package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.Arrays;
import java.util.Locale;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailPose;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.PoseSnapshot;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Silhouette;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.item.ItemStack;

public final class AfterimageStyle extends TrailStyleBase {
   public final SliderSetting spacing;
   public final SliderSetting maxGhosts;
   public final BooleanSetting onlySprinting;
   public final BooleanSetting drift;
   public final ModeSetting<AfterimageStyle.Mode> mode;
   private final Silhouette.Look look = new Silhouette.Look();
   private float fillMillis;
   private float rimMillis;

   public AfterimageStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.AFTERIMAGE,
         spec()
            .size(0.8, 1.2, 1.0, 0.05)
            .sizeUnit("x")
            .length(0.6)
            .anchor(TrailAnchor.WAIST, true)
            .along(TrailStyleBase.ColorAlong.HEAD_TO_TAIL)
      );
      this.mode = this.setting(new ModeSetting<>("afterimage.mode", "Mode", "How the ghosts look.", AfterimageStyle.Mode.class, AfterimageStyle.Mode.SOLID));
      this.spacing = this.setting(new SliderSetting("afterimage.spacing", "Spacing", "How far apart the ghosts are left.", 0.4, 3.0, 1.0, 0.1).unit(" blocks"));
      this.maxGhosts = this.setting(
         new SliderSetting("afterimage.max", "Max Ghosts per Player", "The most ghosts one player leaves at once.", 1.0, 8.0, 4.0, 1.0)
      );
      this.onlySprinting = this.setting(new BooleanSetting("afterimage.sprintOnly", "Only While Sprinting", "Ghosts only while sprinting.", true));
      this.drift = this.setting(new BooleanSetting("afterimage.drift", "Drift", "Ghosts drift a little up and back as they fade.", false));
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new AfterimageStyle.Ghosts();
   }

   @Override
   public boolean sprintOnly() {
      return this.onlySprinting.get();
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      if (!p.ghost && !p.reduced && (!this.onlySprinting.get() || (p.state & 2) != 0)) {
         int due = this.piecesDue(p, 1.0 / this.spacing.getFloat(), 1);
         if (due != 0 && !this.module.piecesFull(f)) {
            AfterimageStyle.Ghosts g = this.store(f);
            int i = g.slot(p.entityId, this.maxGhosts.getInt());
            PoseSnapshot pose = g.pose[i];
            if (p.entity != null) {
               pose.capture(p.entity, f.partialTicks);
            } else {
               fromPose(p.pose, p.slim, pose);
            }

            pose.scale = pose.scale * this.size.getFloat();
            g.born[i] = g.clock;
            g.life[i] = this.lengthNow(p.speed);
            g.alpha[i] = p.alpha;
            g.speed[i] = p.speed;
            double yaw = Math.toRadians(p.pose.bodyYaw);
            g.fx[i] = (float)(-Math.sin(yaw));
            g.fz[i] = (float)Math.cos(yaw);
            g.owner[i] = p.entityId;
            g.seed[i] = (int)(f.now * 1000.0) + p.entityId * 31;
            g.armored[i] = wearsArmor(p);
            g.self[i] = p.self;
            g.tint[i] = this.tint;
            g.live[i] = true;
         }
      }
   }

   public String passMillisForTest() {
      return String.format(Locale.ROOT, "fills %.3f ms, rims %.3f ms", this.fillMillis, this.rimMillis);
   }

   public String ghostsForTest(TrailFrame f, int owner) {
      AfterimageStyle.Ghosts g = (AfterimageStyle.Ghosts)f.store(this.type);
      if (g == null) {
         return "no ghosts";
      } else {
         int all = 0;
         int mine = 0;
         int armored = 0;

         for (int i = 0; i < 24; i++) {
            if (g.live[i]) {
               all++;
               if (g.owner[i] == owner) {
                  mine++;
                  armored += g.armored[i] ? 1 : 0;
               }
            }
         }

         return all + " ghosts, " + mine + " of them yours, " + armored + " of those shaped over armor";
      }
   }

   private static boolean wearsArmor(PlayerTrail p) {
      if (p.entity == null) {
         return false;
      } else {
         for (int slot = 0; slot < 4; slot++) {
            ItemStack s = p.entity.getCurrentArmor(slot);
            if (s != null) {
               return true;
            }
         }

         return false;
      }
   }

   private static void fromPose(TrailPose t, boolean slim, PoseSnapshot out) {
      out.mannequin(t.age, slim, t.y);
      out.x = t.x;
      out.z = t.z;
      out.bodyYaw = t.bodyYaw;
      out.limbSwing = t.limbSwing;
      out.limbSwingAmount = t.limbAmount;
      out.swingProgress = t.swing;
      out.sneaking = t.sneaking;
      out.heldItemRight = t.held;
   }

   @Override
   protected void drawStore(TrailFrame f, TrailStyleBase.Store store) {
      AfterimageStyle.Ghosts g = (AfterimageStyle.Ghosts)store;
      Textures.ensureLoaded();
      int n = 0;

      for (int i = 0; i < 24; i++) {
         if (g.live[i]) {
            PoseSnapshot ps = g.pose[i];
            double dx = ps.x - f.eyeX;
            double dy = ps.y + 0.9 - f.eyeY;
            double dz = ps.z - f.eyeZ;
            g.dist[i] = dx * dx + dy * dy + dz * dz;

            int j;
            for (j = n++; j > 0 && g.dist[g.order[j - 1]] < g.dist[i]; j--) {
               g.order[j] = g.order[j - 1];
            }

            g.order[j] = i;
         }
      }

      AfterimageStyle.Mode m = this.mode.get();
      float glowAmount = this.glow.getFloat() / 100.0F;
      Camera cam = Vfx.camera();
      long t0 = System.nanoTime();
      if (m != AfterimageStyle.Mode.OUTLINE) {
         if (m == AfterimageStyle.Mode.HOLOGRAM) {
            RenderState.additive();
         } else {
            RenderState.translucent(false);
         }

         for (int o = 0; o < n; o++) {
            int ix = g.order[o];
            if (this.prepare(g, ix, f, m, cam, 0)) {
               Silhouette.drawPass(g.pose[ix], f.viewX, f.viewY, f.viewZ, this.look, 1);
            }
         }
      }

      RenderState.additive();
      long t1 = System.nanoTime();

      for (int ox = 0; ox < n; ox++) {
         int ix = g.order[ox];

         for (int copy = 0; copy < (m == AfterimageStyle.Mode.GLITCH ? 3 : 1); copy++) {
            if (this.prepare(g, ix, f, m, cam, copy)) {
               Silhouette.drawPass(g.pose[ix], f.viewX, f.viewY, f.viewZ, this.look, 2);
            }
         }
      }

      Vfx.flush();
      long t2 = System.nanoTime();
      this.fillMillis = this.fillMillis + ((float)(t1 - t0) / 1000000.0F - this.fillMillis) * 0.05F;
      this.rimMillis = this.rimMillis + ((float)(t2 - t1) / 1000000.0F - this.rimMillis) * 0.05F;
      if (m == AfterimageStyle.Mode.HOLOGRAM || m == AfterimageStyle.Mode.GLITCH) {
         for (int ox = 0; ox < n; ox++) {
            int ix = g.order[ox];
            if (this.prepare(g, ix, f, m, cam, 0)) {
               if (m == AfterimageStyle.Mode.HOLOGRAM) {
                  this.scanLines(f, cam, g.pose[ix], this.look, this.look.alpha, this.look.color != null ? 0 : this.look.staticColor, g.seed[ix], glowAmount);
               } else {
                  this.glitchBars(
                     f,
                     cam,
                     g.pose[ix],
                     this.look,
                     this.look.alpha,
                     this.look.color != null ? 0 : this.look.staticColor,
                     g.seed[ix],
                     (int)Math.floor(f.now * 14.0)
                  );
               }
            }
         }
      }

      RenderState.additive();
   }

   private boolean prepare(AfterimageStyle.Ghosts g, int i, TrailFrame f, AfterimageStyle.Mode m, Camera cam, int copy) {
      PoseSnapshot ps = g.pose[i];
      float age = g.clock - g.born[i];
      float u = Math.min(1.0F, age / Math.max(0.05F, g.life[i]));
      float a = this.opacity.getFloat() / 100.0F * g.alpha[i] * Easing.smoothstep(0.0F, 0.1F, age) * this.fade(u) * f.fog(ps.x, ps.y + 0.9, ps.z);
      if (a <= 0.004F) {
         return false;
      } else {
         boolean up = this.colorAlong == TrailStyleBase.ColorAlong.HEAD_TO_TAIL && g.tint[i] == 0 && !f.lit;
         int whole = g.tint[i] != 0 ? g.tint[i] : (up ? 0 : this.colorAt(0.0F, u, g.speed[i], f.colorTime, 0.0F));
         if (f.lit) {
            whole = lit(whole, f, ps.x, ps.y + 1.0, ps.z);
         }

         this.look.reset();
         this.look.color = up ? this.color : null;
         this.look.staticColor = whole;
         this.look.levels = up ? 2 : 1;
         this.look.inflate = g.armored[i] ? 1.1F : 0.35F;
         this.look.rimWidth = 0.9F;
         this.look.nearInner = f.nearInner;
         this.look.nearOuter = f.nearOuter * 1.4F;
         if (this.drift.get()) {
            this.look.shiftX = -g.fx[i] * 0.35 * u;
            this.look.shiftY = 0.25 * u;
            this.look.shiftZ = -g.fz[i] * 0.35 * u;
         }

         switch (m) {
            case OUTLINE:
               this.look.fill = 0.0F;
               this.look.rim = 1.2F;
               this.look.alpha = a;
               break;
            case HOLOGRAM:
               this.look.fill = 0.32F;
               this.look.rim = 0.85F;
               this.look.alpha = a * (0.72F + 0.28F * (float)Math.sin(f.now * 41.0 + g.seed[i]));
               break;
            case GLITCH:
               int slot = (int)Math.floor(f.now * 14.0);
               float jump = Rng.hash(slot, g.seed[i]) > 0.7F ? (Rng.hash(slot + 7, g.seed[i]) * 2.0F - 1.0F) * 0.12F : 0.0F;
               double split = 0.05 + 0.04 * Rng.hash(slot + 3, g.seed[i]);
               this.look.fill = 0.4F;
               this.look.rim = 0.9F;
               this.look.alpha = a;
               double shift = jump;
               if (copy > 0) {
                  this.look.fill = 0.0F;
                  this.look.color = null;
                  this.look.staticColor = copy == 1 ? -49810 : -12720385;
                  this.look.alpha = a * 0.6F;
                  shift = copy == 1 ? jump - split : jump + split;
               }

               this.look.shiftX = this.look.shiftX + cam.rightX * shift;
               this.look.shiftZ = this.look.shiftZ + cam.rightZ * shift;
               break;
            default:
               this.look.fill = 0.5F;
               this.look.rim = 1.0F;
               this.look.alpha = a;
         }

         return true;
      }
   }

   private void glitchBars(TrailFrame f, Camera cam, PoseSnapshot ps, Silhouette.Look l, float a, int whole, int seed, int slot) {
      if (!(Rng.hash(slot + 11, seed) < 0.45F)) {
         double h = 1.85 * ps.scale / 0.9375 * (ps.sneaking ? 0.88 : 1.0);
         double cx = ps.x + l.shiftX - f.viewX;
         double cz = ps.z + l.shiftZ - f.viewZ;
         double base = ps.y + l.shiftY - f.viewY;
         double half = 0.42 * ps.scale / 0.9375;

         for (int k = 0; k < 3; k++) {
            float t = 0.08F + 0.84F * Rng.hash(slot * 3 + k, seed);
            double shift = (Rng.hash(slot * 5 + k, seed + 1) * 2.0F - 1.0F) * 0.22;
            double y = base + t * h;
            double x0 = cx + cam.rightX * (shift - half);
            double z0 = cz + cam.rightZ * (shift - half);
            double x1 = cx + cam.rightX * (shift + half);
            double z1 = cz + cam.rightZ * (shift + half);
            int c = ColorUtil.mulAlpha(whole != 0 ? whole : this.color.getColor(t, f.colorTime), a * 0.7F);
            Vfx.line(Textures.LINE_GLOW, x0, y, z0, x1, y, z1, 0.05 + 0.05 * Rng.hash(slot + k, seed + 2), c, c);
         }
      }
   }

   private void scanLines(TrailFrame f, Camera cam, PoseSnapshot ps, Silhouette.Look l, float a, int whole, int seed, float glowAmount) {
      double h = 1.85 * ps.scale / 0.9375 * (ps.sneaking ? 0.88 : 1.0);
      double cx = ps.x + l.shiftX - f.viewX;
      double cz = ps.z + l.shiftZ - f.viewZ;
      double base = ps.y + l.shiftY - f.viewY;
      double half = 0.38 * ps.scale / 0.9375;

      for (int k = 0; k < 6; k++) {
         double t = (f.now * 0.6 + k / 6.0 + Rng.hash(seed, k) * 0.05) % 1.0;
         double y = base + t * h;
         int c = ColorUtil.mulAlpha(whole != 0 ? whole : this.color.getColor((float)t, f.colorTime), a * 0.55F * (0.5F + 0.5F * glowAmount));
         Vfx.line(Textures.LINE_GLOW, cx - cam.rightX * half, y, cz - cam.rightZ * half, cx + cam.rightX * half, y, cz + cam.rightZ * half, 0.035, c, c);
      }
   }

   static final class Ghosts implements TrailStyleBase.Store {
      static final int CAP = 24;
      final PoseSnapshot[] pose = new PoseSnapshot[24];
      final float[] born = new float[24];
      final float[] life = new float[24];
      final float[] alpha = new float[24];
      final float[] fx = new float[24];
      final float[] fz = new float[24];
      final float[] speed = new float[24];
      final int[] owner = new int[24];
      final int[] seed = new int[24];
      final int[] tint = new int[24];
      final boolean[] armored = new boolean[24];
      final boolean[] self = new boolean[24];
      final boolean[] live = new boolean[24];
      final int[] order = new int[24];
      final double[] dist = new double[24];
      float clock;
      int count;

      Ghosts() {
         for (int i = 0; i < 24; i++) {
            this.pose[i] = new PoseSnapshot();
         }
      }

      int slot(int ownerId, int max) {
         int mine = 0;
         int oldestMine = -1;
         int oldest = -1;
         int free = -1;

         for (int i = 0; i < 24; i++) {
            if (!this.live[i]) {
               if (free < 0) {
                  free = i;
               }
            } else {
               if (this.owner[i] == ownerId) {
                  mine++;
                  if (oldestMine < 0 || this.born[i] < this.born[oldestMine]) {
                     oldestMine = i;
                  }
               }

               if (oldest < 0 || this.born[i] < this.born[oldest]) {
                  oldest = i;
               }
            }
         }

         if (mine >= max && oldestMine >= 0) {
            return oldestMine;
         } else {
            return free >= 0 ? free : oldest;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         this.count = 0;

         for (int i = 0; i < 24; i++) {
            if (this.live[i] && this.clock - this.born[i] >= this.life[i]) {
               this.live[i] = false;
            }

            if (this.live[i]) {
               this.count++;
            }
         }
      }

      @Override
      public int size() {
         return this.count;
      }

      @Override
      public void clear() {
         Arrays.fill(this.live, false);
         this.count = 0;
      }
   }

   public static enum Mode implements Displayable {
      SOLID("Solid", "A flat color with a brighter rim"),
      OUTLINE("Outline", "The rim only"),
      HOLOGRAM("Hologram", "Scan lines and a light flicker"),
      GLITCH("Glitch", "Split red and cyan copies that jump sideways");

      private final String name;
      private final String description;

      private Mode(String name, String description) {
         this.name = name;
         this.description = description;
      }

      @Override
      public String displayName() {
         return this.name;
      }

      @Override
      public String description() {
         return this.description;
      }
   }
}
