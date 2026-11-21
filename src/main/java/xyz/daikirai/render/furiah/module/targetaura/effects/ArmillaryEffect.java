package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Clockwork;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
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

public final class ArmillaryEffect extends AuraEffect {
   private static final int SEGMENTS = 48;
   private static final double[] COS = Clockwork.COS;
   private static final double[] SIN = Clockwork.SIN;
   private static final int MAX_RINGS = 5;
   private static final double[] PERIOD = new double[]{1.6, 1.0, 1.35, 0.8, 1.15};
   private static final double[] STEP = new double[]{
      Math.toRadians(9.0), Math.toRadians(-14.0), Math.toRadians(11.0), Math.toRadians(-7.0), Math.toRadians(16.0)
   };
   private static final double[] LEAN = new double[]{0.0, Math.toRadians(25.0), Math.toRadians(-38.0), Math.toRadians(52.0), Math.toRadians(-20.0)};
   private static final float RIM = 0.16F;
   public final SliderSetting rings;
   public final ModeSetting<ArmillaryEffect.Position> position;
   public final ModeSetting<ArmillaryEffect.Motion> motion;
   public final ModeSetting<ArmillaryEffect.Glyphs> glyphs;
   public final BooleanSetting star;
   public final BooleanSetting glyphGlow;
   public final ThemeTint starColor;
   public final ThemeTint glyphColor;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   private final double[] ux = new double[5];
   private final double[] uy = new double[5];
   private final double[] uz = new double[5];
   private final double[] vx = new double[5];
   private final double[] vy = new double[5];
   private final double[] vz = new double[5];
   private final double[] nx = new double[5];
   private final double[] ny = new double[5];
   private final double[] nz = new double[5];
   private final double[] radius = new double[5];
   private final double[] spin = new double[5];
   private final int[] face = new int[5];
   private double cx;
   private double cy;
   private double cz;
   private double axisTop;
   private double axisBottom;
   private double ringWidth;
   private double ringThick;
   private double unfold;
   private int ringCount;
   private final int[] sectionColors = new int[48];

   public ArmillaryEffect() {
      super(
         EffectType.ARMILLARY,
         "armillary",
         new ThemeTint(),
         true
      );
      this.material = this.materialSetting("armillary", Material.BRASS, "What the sphere is made of. Custom takes the Metal Tint, with a finish.");
      this.finish = this.finishSetting("armillary", Finish.METALLIC, this.material);
      this.starColor = new ThemeTint();
      this.glyphGlow = this.setting(
         new BooleanSetting("armillary.glyphGlow", "Glyph Glow", "The engraved numerals and stars glow softly instead of being cut into the metal.", false)
      );
      this.glyphColor = ThemeTint.detail(-2858);
      this.rings = this.look(this.setting(new SliderSetting("armillary.rings", "Rings", "How many rings turn inside each other.", 3.0, 5.0, 4.0, 1.0)));
      this.position = this.setting(
         new ModeSetting<>(
            "armillary.position", "Position", "Round the torso, or smaller above the head.", ArmillaryEffect.Position.class, ArmillaryEffect.Position.BODY
         )
      );
      this.motion = this.setting(
         new ModeSetting<>(
            "armillary.motion",
            "Motion",
            "Clockwork: the rings step forward in small ticks with a tiny overshoot. Smooth: they turn evenly.",
            ArmillaryEffect.Motion.class,
            ArmillaryEffect.Motion.CLOCKWORK
         )
      );
      this.glyphs = this.setting(
         new ModeSetting<>("armillary.glyphs", "Glyphs", "What is engraved on the rings.", ArmillaryEffect.Glyphs.class, ArmillaryEffect.Glyphs.TICKS)
            .style(ModeSetting.Style.CHIPS)
      );
      this.star = this.setting(new BooleanSetting("armillary.starOn", "Star", "A small star glowing at the top of the axis.", true));
   }

   private BooleanSupplier customMaterial() {
      return new BooleanSupplier() {
         @Override
         public boolean getAsBoolean() {
            return ArmillaryEffect.this.material.get() == Material.CUSTOM;
         }
      };
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.SOLID || pass == FxPass.TRANSLUCENT && this.material.get().translucent(this.finish.get())) {
         if (pass == FxPass.SOLID != this.material.get().translucent(this.finish.get())) {
            this.layout(c);
            this.solid(c, pass == FxPass.SOLID);
            this.engraving(c, false);
         }
      } else if (pass == FxPass.GLOW) {
         this.layout(c);
         if (this.glyphGlow.get()) {
            this.engraving(c, true);
         }

         this.starGlow(c);
      }
   }

   private void layout(EffectContext c) {
      double sz = this.size(c);
      boolean head = this.position.get() == ArmillaryEffect.Position.HEAD;
      double big = (head ? 0.3 : 0.64) * sz;
      this.cx = c.x;
      this.cz = c.z;
      this.cy = head ? c.y + c.nameTop + 0.06 + big * 1.12 : c.y + c.height * 0.56;
      this.ringCount = this.rings.getInt();
      this.ringWidth = (head ? 0.026 : 0.046) * sz;
      this.ringThick = (head ? 0.01 : 0.016) * sz;
      float in = Easing.outCubic(Easing.clamp01(c.intro / 0.75F));
      float out = c.outro;
      this.unfold = Math.max(0.0, Math.min(1.0, in * (1.0 - Easing.inOutCubic(Easing.clamp01(out / 0.7F)))));
      Camera cam = c.cam != null ? c.cam : Vfx.camera();
      double ex = cam.posX - this.cx;
      double ey = cam.posY - this.cy;
      double ez = cam.posZ - this.cz;
      double t = c.time * this.speed();
      double fx = 1.0;
      double fy = 0.0;
      double fz = 0.0;
      double gx = 0.0;
      double gy = 1.0;
      double gz = 0.0;
      double hx = 0.0;
      double hy = 0.0;
      double hz = 1.0;

      for (int k = 0; k < this.ringCount; k++) {
         double angle = this.turn(t, k) + LEAN[k];
         double fold = Easing.smoothstep(k * 0.12F, k * 0.12F + 0.72F, (float)this.unfold);
         double a = angle * fold;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         if (k == 0) {
            fx = ca;
            fy = 0.0;
            fz = sa;
            gx = 0.0;
            gy = 1.0;
            gz = 0.0;
            hx = -sa;
            hy = 0.0;
            hz = ca;
         } else {
            double ngx = gx * ca + hx * sa;
            double ngy = gy * ca + hy * sa;
            double ngz = gz * ca + hz * sa;
            double nhx = hx * ca - gx * sa;
            double nhy = hy * ca - gy * sa;
            double nhz = hz * ca - gz * sa;
            double ofx = fx;
            double ofy = fy;
            double ofz = fz;
            fx = ngx;
            fy = ngy;
            fz = ngz;
            gx = ofx;
            gy = ofy;
            gz = ofz;
            hx = nhx;
            hy = nhy;
            hz = nhz;
         }

         this.ux[k] = fx;
         this.uy[k] = fy;
         this.uz[k] = fz;
         this.vx[k] = gx;
         this.vy[k] = gy;
         this.vz[k] = gz;
         this.nx[k] = hx;
         this.ny[k] = hy;
         this.nz[k] = hz;
         this.face[k] = ex * hx + ey * hy + ez * hz >= 0.0 ? 1 : -1;
         this.radius[k] = big * (1.0 - 0.13 * k) * (0.45 + 0.55 * this.unfold);
         this.spin[k] = this.turn(t * 0.35, (k + 2) % 5) * 0.5;
      }

      double r0 = big * (0.45 + 0.55 * this.unfold);
      double top;
      if (head) {
         top = this.cy + r0 * 1.25;
      } else {
         double room = c.nameBottom - c.headTop;
         double lift = room > 0.0 ? Math.min(0.1, room * 0.5) : 0.1;
         top = Math.max(c.y + c.headTop + lift, this.cy + r0 * 1.06);
      }

      double axisFade = out > 0.7F ? 1.0 - Easing.clamp01((out - 0.7F) / 0.3F) : 1.0;
      this.axisBottom = this.cy - r0 * 1.12 * axisFade;
      this.axisTop = this.cy + (top - this.cy) * axisFade;
   }

   public double turnForTest(double t, int k) {
      return Math.toDegrees(this.turn(t, k));
   }

   public double starHeightForTest(EffectContext c) {
      this.layout(c);
      return this.axisTop - c.y;
   }

   public double bottomForTest(EffectContext c) {
      this.layout(c);
      return this.axisBottom - c.y;
   }

   private double turn(double t, int k) {
      return Clockwork.ratchet(t, PERIOD[k], STEP[k], this.motion.get() == ArmillaryEffect.Motion.SMOOTH);
   }

   private void solid(EffectContext c, boolean depthWrite) {
      float alpha = this.intensity(c) * (float)Math.min(1.0, 0.15 + this.unfold * 1.2);
      if (!(alpha <= 0.003F)) {
         Material m = this.material.get();
         boolean custom = m == Material.CUSTOM;
         MeshBatch.begin(m, this.finish.get(), c.light, 0.16F, -1, true, depthWrite);

         for (int k = 0; k < this.ringCount; k++) {
            this.ring(c, k, alpha, custom);
         }

         double gr = this.ringWidth * 0.75;
         double t = c.time * this.speed();

         for (int k = 0; k < this.ringCount; k++) {
            if (k == 0) {
               double spin0 = this.turn(t, 0) * 3.0;

               for (int s = -1; s <= 1; s += 2) {
                  this.gear(c, this.cx, this.cy + s * this.radius[0], this.cz, 0.0, 1.0, 0.0, gr, this.ringThick * 1.6, spin0, alpha, custom);
               }
            } else {
               double ax = this.ux[k];
               double ay = this.uy[k];
               double az = this.uz[k];
               double r = this.radius[k] + (this.radius[k - 1] - this.radius[k]) * 0.5;
               double spinK = this.turn(t, k) * 2.5;

               for (int s = -1; s <= 1; s += 2) {
                  this.gear(
                     c, this.cx + ax * r * s, this.cy + ay * r * s, this.cz + az * r * s, ax, ay, az, gr * 0.85, this.ringThick * 1.4, spinK * s, alpha, custom
                  );
               }
            }
         }

         this.axis(c, alpha, custom);
         MeshBatch.end();
      }
   }

   private void ring(EffectContext c, int k, float alpha, boolean custom) {
      double r = this.radius[k];
      double ax = this.ux[k];
      double ay = this.uy[k];
      double az = this.uz[k];
      double bx = this.vx[k];
      double by = this.vy[k];
      double bz = this.vz[k];

      for (int i = 0; i < 48; i++) {
         double rx = ax * COS[i] + bx * SIN[i];
         double ry = ay * COS[i] + by * SIN[i];
         double rz = az * COS[i] + bz * SIN[i];
         double px = this.cx + rx * r;
         double py = this.cy + ry * r;
         double pz = this.cz + rz * r;
         this.sectionColors[i] = this.colorAt(c, custom, i / 48.0F, alpha * c.keep.solid(px, py, pz), 1.0F);
      }

      Clockwork.band(
         this.cx,
         this.cy,
         this.cz,
         ax,
         ay,
         az,
         bx,
         by,
         bz,
         this.nx[k],
         this.ny[k],
         this.nz[k],
         r,
         this.ringWidth,
         this.ringThick,
         this.sectionColors,
         this.face[k]
      );
   }

   private void gear(
      EffectContext c, double px, double py, double pz, double ax, double ay, double az, double r, double t, double turn, float alpha, boolean custom
   ) {
      int col = this.colorAt(c, custom, 0.5F, alpha * c.keep.solid(px, py, pz), 0.92F);
      Clockwork.kiteGear(px, py, pz, ax, ay, az, r, t, turn, col);
   }

   private void axis(EffectContext c, float alpha, boolean custom) {
      if (!(this.axisTop - this.axisBottom < 0.001)) {
         int sides = 8;
         double r = this.ringThick * 0.55;
         int col = this.colorAt(c, custom, 0.5F, alpha * c.keep.solid(this.cx, (this.axisTop + this.axisBottom) * 0.5, this.cz), 0.95F);
         int base = MeshBatch.stage(sides * 2);

         for (int i = 0; i < sides; i++) {
            double dx = COS[i * 6];
            double dz = SIN[i * 6];
            MeshBatch.vertex(base + i, this.cx + dx * r, this.axisBottom, this.cz + dz * r, dx, 0.0, dz, col, 0.0F);
            MeshBatch.vertex(base + sides + i, this.cx + dx * r, this.axisTop, this.cz + dz * r, dx, 0.0, dz, col, 0.0F);
         }

         for (int var14 = 0; var14 < sides; var14++) {
            int j = (var14 + 1) % sides;
            MeshBatch.quad(base + var14, base + j, base + sides + j, base + sides + var14);
         }
      }
   }

   private int colorAt(EffectContext c, boolean custom, float along, float alpha, float shade) {
      int rgb = custom ? this.color(c, along) : -1;
      if (shade < 1.0F) {
         rgb = ColorUtil.lerp(-16777216, rgb | 0xFF000000, shade);
      }

      return Math.round(255.0F * Math.max(0.0F, Math.min(1.0F, alpha))) << 24 | rgb & 16777215;
   }

   private void engraving(EffectContext c, boolean glow) {
      ArmillaryEffect.Glyphs g = this.glyphs.get();
      if (g != ArmillaryEffect.Glyphs.NONE && !(this.unfold < 0.05)) {
         float alpha = this.intensity(c) * (float)Math.min(1.0, this.unfold * 1.3);
         RenderState.decal(true);
         if (!glow) {
            int dark = ColorUtil.withAlpha(-15068148, 0.78F * alpha);

            for (int k = 0; k < this.ringCount; k++) {
               this.ticks(c, k, dark);
            }
         }

         if (g != ArmillaryEffect.Glyphs.TICKS) {
            int color = glow ? alphaOf(this.glyphColor.getColor(0.5F), alpha * 0.9F) : ColorUtil.withAlpha(-15068148, 0.78F * alpha);

            for (int k = 0; k < this.ringCount; k++) {
               this.glyphs(c, k, g == ArmillaryEffect.Glyphs.ROMAN, color);
            }
         }

         RenderState.decal(false);
      }
   }

   private static int alphaOf(int color, float a) {
      return ColorUtil.withAlpha(color, (color >>> 24) / 255.0F * Math.max(0.0F, Math.min(1.0F, a)));
   }

   private void ticks(EffectContext c, int k, int dark) {
      double ht = (this.ringThick * 0.5 + 8.0E-4) * this.face[k];
      Clockwork.ticks(
         this.cx,
         this.cy,
         this.cz,
         this.ux[k],
         this.uy[k],
         this.uz[k],
         this.vx[k],
         this.vy[k],
         this.vz[k],
         this.nx[k] * ht,
         this.ny[k] * ht,
         this.nz[k] * ht,
         this.radius[k],
         this.ringWidth,
         this.spin[k],
         dark,
         c.keep
      );
   }

   private void glyphs(EffectContext c, int k, boolean roman, int color) {
      double ht = (this.ringThick * 0.5 + 0.0012) * this.face[k];
      Clockwork.glyphs(
         this.cx,
         this.cy,
         this.cz,
         this.ux[k],
         this.uy[k],
         this.uz[k],
         this.vx[k],
         this.vy[k],
         this.vz[k],
         this.nx[k] * ht,
         this.ny[k] * ht,
         this.nz[k] * ht,
         this.radius[k],
         this.ringWidth,
         this.spin[k],
         0.06544984694978735,
         this.face[k],
         roman,
         color,
         c.keep
      );
   }

   private void starGlow(EffectContext c) {
      if (this.star.get() && !(this.unfold < 0.02)) {
         float a = this.intensity(c) * (float)Math.min(1.0, this.unfold * 1.5) * (1.0F - Easing.clamp01(c.outro * 1.4F));
         if (!(a <= 0.003F)) {
            float s = Math.min(1.2F, this.size(c)) * (this.position.get() == ArmillaryEffect.Position.HEAD ? 0.75F : 1.0F);
            int col = this.starColor.getColor(0.5F);
            float twinkle = 0.85F + 0.15F * (float)Math.sin(c.time * 3.1);
            double y = this.axisTop + 0.012 * s;
            Vfx.sprite(Textures.GLOW, this.cx, y, this.cz, 0.36F * s, 0.0F, alpha(col, 0.55F * a * twinkle));
            Vfx.sprite(Textures.FLARE, this.cx, y, this.cz, 0.26F * s * twinkle, (float)(c.time * 0.4), core(col, 0.95F * a));
            Vfx.sprite(Textures.SPARK, this.cx, y, this.cz, 0.08F * s, 0.0F, core(col, a));
         }
      }
   }

   public static enum Glyphs implements Displayable {
      TICKS("Ticks", "Tick marks"),
      ROMAN("Roman numerals", "Tick marks and numerals"),
      STARS("Stars", "Tick marks and small stars"),
      NONE("None", "Plain rings");

      private final String displayName;
      private final String description;

      private Glyphs(String displayName, String description) {
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

   public static enum Motion implements Displayable {
      CLOCKWORK("Clockwork", "Steps forward in small ticks"),
      SMOOTH("Smooth", "Turns evenly");

      private final String displayName;
      private final String description;

      private Motion(String displayName, String description) {
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

   public static enum Position implements Displayable {
      BODY("Around body", "Round the torso"),
      HEAD("Above head", "Smaller, above the head");

      private final String displayName;
      private final String description;

      private Position(String displayName, String description) {
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
