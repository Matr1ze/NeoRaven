package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.Chains;
import xyz.daikirai.render.furiah.fx.kit.Surface;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.GroundCache;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class GildedChainStyle extends TrailStyleBase {
   private static final double LINK = 0.2;
   private static final float LOOSE_FROM = 0.8F;
   private static final float RIM = 0.25F;
   private static final int MAX_LINKS = 140;
   private static final double FULL_WITHIN = 3.5;
   private static final double COARSE_WITHIN = 8.0;
   private static final double TINY_WITHIN = 13.0;
   private static final double OTHERS_COARSE_WITHIN = 3.0;
   private static final double OTHERS_TINY_WITHIN = 5.0;
   private static final double OTHERS_GLINT_WITHIN = 8.0;
   public final ModeSetting<Material> material;
   public final ModeSetting<Finish> finish;
   public final ModeSetting<GildedChainStyle.Variant> variant;
   public final SliderSetting linkSize;
   public final BooleanSetting loose;
   public final BooleanSetting glint;
   public final SliderSetting chainLength;
   private final GroundCache ground = new GroundCache();
   private final Map<PlayerTrail, GildedChainStyle.Drag> drags = new IdentityHashMap<>();
   private TrailFrame groundFrame;
   private final Surface floor = new Surface() {
      @Override
      public double at(double x, double y, double z) {
         return GildedChainStyle.this.ground
            .surface(GildedChainStyle.this.groundFrame == null ? null : GildedChainStyle.this.groundFrame.world, x, y + 0.5, z, 1.0, 3.0);
      }
   };
   private final double[] at = new double[8];
   private int cursor;

   public GildedChainStyle(FuriahTrails module) {
      super(module, TrailStyle.GILDED_CHAIN, spec().taper(0.0).length(1.2).anchor(TrailAnchor.WAIST, false));
      this.material = this.materialSetting(Material.GOLD, "What the chain is made of. Custom takes the Tint, with a finish.");
      this.finish = this.finishSetting(Finish.METALLIC, this.material);
      this.doesNotApply(new Setting[]{this.size, this.glow});
      this.variant = this.setting(
         new ModeSetting<>(
               "gilded_chain.variant",
               "Variant",
               "Along your path at the anchor's height, or hanging from it and dragging along the ground.",
               GildedChainStyle.Variant.class,
               GildedChainStyle.Variant.FLOATING
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.linkSize = this.setting(new SliderSetting("gilded_chain.linkSize", "Link Size", "How big the links are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.loose = this.setting(
         new BooleanSetting("gilded_chain.loose", "Loose Links at the Tail", "The last links come loose one by one, drop and fade.", true)
      );
      this.glint = this.setting(new BooleanSetting("gilded_chain.glint", "Glint", "The links glint as they catch the light.", true));
      this.chainLength = this.setting(
         new SliderSetting("gilded_chain.chainLength", "Chain Length", "How long the dragging chain is.", 1.0, 8.0, 3.0, 0.5)
            .unit(" blocks")
            .visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return GildedChainStyle.this.variant.get() == GildedChainStyle.Variant.DRAGGING;
               }
            })
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.linkSize);
   }

   @Override
   public int passes() {
      int body = this.material.get().translucent(this.finish.get()) ? FxPass.TRANSLUCENT.bit() : FxPass.SOLID.bit();
      return this.glint.get() ? body | FxPass.GLOW.bit() : body;
   }

   private double linkScale(float u) {
      float tp = this.taper == null ? 0.0F : this.taper.getFloat() / 100.0F;
      return 0.2 * this.linkSize.getFloat() / 100.0 * (1.0 - tp * u * 0.6);
   }

   @Override
   public void update(TrailFrame f) {
      super.update(f);
      if (!this.drags.isEmpty()) {
         Iterator<Entry<PlayerTrail, GildedChainStyle.Drag>> it = this.drags.entrySet().iterator();

         while (it.hasNext()) {
            GildedChainStyle.Drag d = it.next().getValue();
            if (d.scene == f && f.now - d.seen > 2.0) {
               it.remove();
            }
         }
      }
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      boolean dragging = this.variant.get() == GildedChainStyle.Variant.DRAGGING && !p.reduced && !p.ghost;
      if (pass != FxPass.GLOW) {
         Material m = this.materialFor(this.material.get());
         int vc = m == Material.CUSTOM ? (this.tint != 0 ? this.tint : this.color.getColor(0.5F)) | 0xFF000000 : -1;
         MeshBatch.begin(m, this.finish.get(), this.matLight, 0.25F + 0.3F * this.night, -1, true, pass == FxPass.SOLID);

         try {
            if (dragging) {
               this.drag(p, f, vc, this.spawnPass(pass));
            } else {
               this.floating(p, f, vc);
            }
         } finally {
            MeshBatch.end();
         }
      } else {
         double gx = p.headX - f.eyeX;
         double gy = p.headY - f.eyeY;
         double gz = p.headZ - f.eyeZ;
         if (!p.reduced && (p.self || gx * gx + gy * gy + gz * gz < 64.0)) {
            this.glints(p, f, dragging);
         }
      }
   }

   private boolean pointAt(TrailShape s, int k, double along, double[] out, boolean fromHead) {
      int from = s.runStart[k];
      int to = s.runEnd[k];
      if (fromHead || this.cursor > to - 1 || this.cursor <= from) {
         this.cursor = to - 1;
      }

      for (int i = this.cursor; i > from; i--) {
         double a0 = s.along[i];
         double a1 = s.along[i - 1];
         if (along >= a0 && along <= a1) {
            this.cursor = i;
            double t = a1 - a0 < 1.0E-9 ? 0.0 : (along - a0) / (a1 - a0);
            out[0] = s.x[i] + (s.x[i - 1] - s.x[i]) * t;
            out[1] = s.y[i] + (s.y[i - 1] - s.y[i]) * t;
            out[2] = s.z[i] + (s.z[i - 1] - s.z[i]) * t;
            double tx = s.x[i] - s.x[i - 1];
            double ty = s.y[i] - s.y[i - 1];
            double tz = s.z[i] - s.z[i - 1];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1.0E-6) {
               tx = 1.0;
               ty = 0.0;
               tz = 0.0;
               tl = 1.0;
            }

            out[3] = tx / tl;
            out[4] = ty / tl;
            out[5] = tz / tl;
            out[6] = s.age[i] + (s.age[i - 1] - s.age[i]) * t;
            return true;
         }
      }

      return false;
   }

   private void floating(PlayerTrail p, TrailFrame f, int vc) {
      TrailShape s = p.shape;
      if (s != null && s.runs != 0) {
         double full = p.self && !p.reduced ? 12.25 : -1.0;
         double coarse = p.reduced ? -1.0 : (p.self ? 64.0 : 9.0);
         double tiny = p.reduced ? -1.0 : (p.self ? 169.0 : 25.0);
         boolean drops = this.loose.get() && !p.reduced;
         float total = Math.max(0.001F, s.length);
         float cap = this.maxLength == null ? Float.MAX_VALUE : this.maxLength.getFloat();
         float op = this.opacity.getFloat() / 100.0F * p.alpha;
         double traveled = !p.ghost && p.path != null ? p.path.traveled() + p.path.sinceLast(p.headX, p.headY, p.headZ) : 0.0;
         double time = f.now;

         for (int k = 0; k < s.runs; k++) {
            double head = s.along[s.runEnd[k] - 1];
            double runEnd = s.along[s.runStart[k]];
            double scale0 = this.linkScale(0.0F);
            double pitch = scale0 * 0.72;
            double phase = traveled % pitch;
            int j = 0;

            for (double a = head + phase; a <= runEnd && j < 140; j++) {
               if (this.pointAt(s, k, a, this.at, j == 0)) {
                  float u = (float)Math.min(1.0, a / total);
                  float alpha = op
                     * smooth(0.0F, 0.25F, (float)a)
                     * (1.0F - smooth(cap * 0.85F, cap, (float)a))
                     * f.nearFade(this.at[0], this.at[1], this.at[2])
                     * f.fog(this.at[0], this.at[1], this.at[2]);
                  double x = this.at[0];
                  double y = this.at[1];
                  double z = this.at[2];
                  double fx = this.at[3];
                  double fy = this.at[4];
                  double fz = this.at[5];
                  boolean turned = ((long)Math.floor((traveled - a) / pitch) & 1L) == 1L;
                  double spin = 0.0;
                  if (u > 0.8F) {
                     float q = (u - 0.8F) / 0.19999999F;
                     alpha *= this.fade(q);
                     if (drops) {
                        y -= q * q * 0.7;
                        spin = q * 1.4 * (turned ? -1 : 1);
                     }
                  }

                  if (!(alpha <= 0.01F)) {
                     double ex = x - f.viewX;
                     double ey = y - f.viewY;
                     double ez = z - f.viewZ;
                     double d22 = ex * ex + ey * ey + ez * ez;
                     this.link(
                        f,
                        x,
                        y,
                        z,
                        fx,
                        fy,
                        fz,
                        turned,
                        spin,
                        this.linkScale(u),
                        ColorUtil.mulAlpha(vc, Math.min(1.0F, alpha)),
                        this.glintAt(j, time),
                        d22 < full ? 0 : (d22 < coarse ? 1 : (d22 < tiny ? 2 : 3))
                     );
                  }
               }

               a += pitch;
            }
         }
      }
   }

   private float glintAt(int j, double time) {
      if (!this.glint.get()) {
         return 0.0F;
      } else {
         double v = time * 6.0 - j * 0.35;
         double run = v - 9.0 * (long)(v / 9.0);
         return !(run < 0.0) && !(run > 1.0) ? (float)Math.sin(Math.PI * run) * 0.9F : 0.0F;
      }
   }

   private void link(
      TrailFrame f, double x, double y, double z, double fx, double fy, double fz, boolean turned, double spin, double scale, int col, float shine, int detail
   ) {
      double wx = -fz;
      double wy = 0.0;
      double wz = fx;
      double wl = Math.sqrt(wx * wx + fx * fx);
      if (wl < 1.0E-6) {
         wx = 1.0;
         wz = 0.0;
         wl = 1.0;
      }

      double var53;
      double ux = fy * (var53 = wz / wl) - fz * wy;
      double var52;
      double uy = fz * (var52 = wx / wl) - fx * var53;
      double uz = fx * wy - fy * var52;
      double sn;
      double c;
      if (spin == 0.0) {
         c = turned ? 0.0 : 1.0;
         sn = turned ? 1.0 : 0.0;
      } else {
         double ang = (turned ? Math.PI / 2 : 0.0) + spin;
         c = Math.cos(ang);
         sn = Math.sin(ang);
      }

      double bx = var52 * c + ux * sn;
      double by = wy * c + uy * sn;
      double bz = var53 * c + uz * sn;
      double qx = ux * c - var52 * sn;
      double qy = uy * c - wy * sn;
      double qz = uz * c - var53 * sn;
      Chains.linkDetail(x - f.viewX, y - f.viewY, z - f.viewZ, fx, fy, fz, bx, by, bz, qx, qy, qz, scale, col, shine, detail);
   }

   private void drag(PlayerTrail p, TrailFrame f, int vc, boolean step) {
      GildedChainStyle.Drag d = this.drags.get(p);
      double scale = this.linkScale(0.0F);
      double rest = scale * 0.72;
      int n = Math.max(3, Math.min(40, (int)Math.round(this.chainLength.getFloat() / rest)));
      if (d == null) {
         d = new GildedChainStyle.Drag();
         this.drags.put(p, d);
      }

      if (!d.ready || d.rope.count() != n || Math.abs(d.rope.rest() - rest) > 1.0E-6) {
         d.rope.reset(n, rest, p.headX, p.headY, p.headZ, 0.0, -1.0, 0.0);
         d.ready = true;
      }

      d.seen = f.now;
      d.scene = f;
      Chains.Rope r = d.rope;
      if (step && f.dt > 0.0F) {
         this.groundFrame = f;
         r.pin(0, true, p.headX, p.headY, p.headZ);
         r.step(Math.min(0.05, (double)f.dt), this.floor);
      }

      float op = this.opacity.getFloat() / 100.0F * p.alpha;
      double full = p.self ? 12.25 : -1.0;
      double coarse = p.self ? 64.0 : 9.0;
      double time = f.now;

      for (int i = 0; i + 1 < r.count(); i++) {
         double x0 = r.x(i);
         double y0 = r.y(i);
         double z0 = r.z(i);
         double x1 = r.x(i + 1);
         double y1 = r.y(i + 1);
         double z1 = r.z(i + 1);
         float alpha = op * f.nearFade(x0, y0, z0) * f.fog(x0, y0, z0);
         if (!(alpha <= 0.01F)) {
            double ex = x0 - f.viewX;
            double ey = y0 - f.viewY;
            double ez = z0 - f.viewZ;
            double d2 = ex * ex + ey * ey + ez * ez;
            Chains.linkBetween(
               ex,
               ey,
               ez,
               x1 - f.viewX,
               y1 - f.viewY,
               z1 - f.viewZ,
               0.0,
               1.0,
               0.0,
               (i & 1) == 1,
               scale,
               ColorUtil.mulAlpha(vc, Math.min(1.0F, alpha)),
               this.glintAt(i, time),
               d2 < full ? 0 : (d2 < coarse ? 1 : 2)
            );
         }
      }
   }

   private void glints(PlayerTrail p, TrailFrame f, boolean dragging) {
      float op = this.opacity.getFloat() / 100.0F * p.alpha;
      double time = f.now;
      RenderState.additive();
      if (dragging) {
         GildedChainStyle.Drag d = this.drags.get(p);
         if (d != null && d.ready) {
            for (int i = 0; i < d.rope.count(); i++) {
               float g = this.glintAt(i, time);
               if (g > 0.4F) {
                  this.sparkle(f, d.rope.x(i), d.rope.y(i), d.rope.z(i), g * op, i);
               }
            }
         }
      } else {
         TrailShape s = p.shape;
         if (s != null && s.runs != 0) {
            double traveled = !p.ghost && p.path != null ? p.path.traveled() + p.path.sinceLast(p.headX, p.headY, p.headZ) : 0.0;
            double pitch = this.linkScale(0.0F) * 0.72;
            float total = Math.max(0.001F, s.length);

            for (int k = 0; k < s.runs; k++) {
               double head = s.along[s.runEnd[k] - 1];
               double runEnd = s.along[s.runStart[k]];
               int j = 0;
               boolean first = true;

               for (double a = head + traveled % pitch; a <= runEnd && j < 140; j++) {
                  float g = this.glintAt(j, time);
                  if (!(g <= 0.4F) && this.pointAt(s, k, a, this.at, first)) {
                     first = false;
                     float u = (float)Math.min(1.0, a / total);
                     this.sparkle(f, this.at[0], this.at[1], this.at[2], g * op * this.fade((float)(this.at[6] / Math.max(0.05F, p.maxAge))) * (1.0F - u), j);
                  }

                  a += pitch;
               }
            }
         }
      }
   }

   private void sparkle(TrailFrame f, double x, double y, double z, float a, int i) {
      float k = a * f.nearFade(x, y, z) * f.fog(x, y, z);
      if (!(k <= 0.01F)) {
         double size = this.linkScale(0.0F) * 1.1;
         Vfx.sprite(Textures.SPARKLE, x - f.viewX, y - f.viewY, z - f.viewZ, size, (float)(i * 0.7 + f.now * 2.0), ColorUtil.mulAlpha(-2858, Math.min(1.0F, k)));
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%s, %s, link %.0f%%, loose %s, glint %s, %d dragging chain(s)",
         this.variant.get().displayName(),
         this.material.get().displayName(),
         this.linkSize.getFloat(),
         this.loose.get() ? "on" : "off",
         this.glint.get() ? "on" : "off",
         this.drags.size()
      );
   }

   private static final class Drag {
      final Chains.Rope rope = new Chains.Rope();
      double seen;
      TrailFrame scene;
      boolean ready;

      private Drag() {
      }
   }

   public static enum Variant implements Displayable {
      FLOATING("Floating", "Along your path at the anchor's height"),
      DRAGGING("Dragging", "Hanging from the anchor, dragging along the ground");

      private final String displayName;
      private final String description;

      private Variant(String displayName, String description) {
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
