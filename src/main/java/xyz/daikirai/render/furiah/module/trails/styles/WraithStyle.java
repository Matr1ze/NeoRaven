package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Smoke;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.fx.trail.TrailShape;
import xyz.daikirai.render.furiah.fx.trail.TrailStrip;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;
import net.minecraft.client.Minecraft;

public final class WraithStyle extends TrailStyleBase {
   private static final int MAX_TENDRILS = 5;
   private static final double BASE_WIDTH = 0.3;
   private static final double SPREAD = 0.46;
   private static final int CURL_STEP = 4;
   private static final double WISP_LENGTH = 1.6;
   private static final double WISP_SPEED = 0.45;
   private static final float TENDRIL_WIND = 0.35F;
   private static final float EMBER_WIND = 0.6F;
   private static final double WIND_AGE = 1.5;
   public final ThemeTint embersColor;
   public final ThemeTint edgeColor;
   public final SliderSetting tendrils;
   public final SliderSetting width;
   public final SliderSetting curl;
   public final SliderSetting embers;
   public final ModeSetting<Smoke.EdgeLight> edgeLight;
   private final Rng rng = new Rng();
   private final Map<PlayerTrail, WraithStyle.Tendrils[]> tendrilsOf = new IdentityHashMap<>();
   private final double[] flow = new double[3];
   private static final float[] U = new float[1024];
   private static final double[] FLOW_X = new double[258];
   private static final double[] FLOW_Y = new double[258];
   private static final double[] FLOW_Z = new double[258];

   public WraithStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.WRAITH,
         spec().length(1.4).anchor(TrailAnchor.BACK, false).animated().fade(TrailStyleBase.FadeCurve.LATE)
      );
      this.edgeColor = this.signatureColor("edgeColor", "Edge Light", "The faint light round the smoke's edges.", -4124129, -38339);
      this.embersColor = this.signatureColor("embersColor", "Embers", "The glowing cracks and the embers drifting up.", -45778, -15811);
      this.doesNotApply(new Setting[]{this.size, this.taper});
      this.tendrils = this.setting(new SliderSetting("wraith.tendrils", "Tendrils", "How many smoke tendrils stream from your back.", 2.0, 5.0, 3.0, 1.0));
      this.width = this.setting(new SliderSetting("wraith.width", "Width", "How wide the tendrils are.", 50.0, 150.0, 100.0, 5.0).unit("%"));
      this.curl = this.setting(new SliderSetting("wraith.curl", "Curl", "How much the smoke curls toward the tail.", 0.0, 100.0, 50.0, 1.0).unit("%"));
      this.embers = this.setting(
         new SliderSetting("wraith.embers", "Embers", "How strongly the cracks glow and how many embers drift up.", 0.0, 100.0, 40.0, 1.0).unit("%")
      );
      this.edgeLight = this.setting(
         new ModeSetting<>(
               "wraith.edgeLight",
               "Edge Light",
               "A faint light round the smoke: where it is dark (Night Visibility), always, or never.",
               Smoke.EdgeLight.class,
               Smoke.EdgeLight.AUTO
            )
            .style(ModeSetting.Style.CHIPS)
      );
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.width);
   }

   @Override
   public int passes() {
      return FxPass.TRANSLUCENT.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void update(TrailFrame f) {
      super.update(f);
      if (!this.tendrilsOf.isEmpty()) {
         Iterator<Entry<PlayerTrail, WraithStyle.Tendrils[]>> it = this.tendrilsOf.entrySet().iterator();

         while (it.hasNext()) {
            WraithStyle.Tendrils[] t = it.next().getValue();
            double seen = -1.0;
            TrailFrame scene = null;

            for (WraithStyle.Tendrils one : t) {
               if (one != null) {
                  seen = Math.max(seen, one.seen);
                  scene = one.scene;
               }
            }

            if (scene == f && f.now - seen > 2.0) {
               it.remove();
            }
         }
      }
   }

   private float edgeAmount() {
      switch ((Smoke.EdgeLight)this.edgeLight.get()) {
         case ON:
            return 1.0F;
         case OFF:
            return 0.0F;
         default:
            return this.night;
      }
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      TrailShape s = p.shape;
      if (s != null && s.runs != 0) {
         WraithStyle.Tendrils[] slots = this.tendrilsOf.get(p);
         if (slots == null) {
            slots = new WraithStyle.Tendrils[4];
            this.tendrilsOf.put(p, slots);
         }

         int slot;
         if (slots[slot = p.side * 2 + (p.ghost ? 1 : 0)] == null) {
            slots[slot] = new WraithStyle.Tendrils();
         }

         WraithStyle.Tendrils t = slots[slot];
         t.seen = f.now;
         t.scene = f;
         if (pass == FxPass.TRANSLUCENT) {
            for (int k = 0; k < s.runs; k++) {
               if (this.layout(p, f, k, t)) {
                  this.smoke(f, t);
               }
            }

            if (!p.ghost && !p.reduced && this.embers.getFloat() > 0.0F && this.spawnPass(pass)) {
               this.emit(p, f);
            }
         } else if (t.stamp == f.now) {
            this.light(p, f, t);
         }
      }
   }

   private boolean layout(PlayerTrail p, TrailFrame f, int k, WraithStyle.Tendrils t) {
      int n = this.fill(p, f, k, 1.0F, 0.0F, 0.0F);
      if (n < 2) {
         return false;
      } else {
         TrailShape s = p.shape;
         int from = s.runStart[k];
         float total = Math.max(0.001F, s.length);
         int count = p.reduced ? 2 : this.tendrils.getInt();
         t.ensure(n);
         t.count = count;
         t.points = n;
         t.stamp = f.now;
         boolean mine = p.self && !f.preview && Minecraft.getMinecraft().gameSettings.thirdPersonView == 0;
         float w0 = (float)(0.3 * this.width.getFloat() / 100.0) * (p.reduced ? 1.35F : 1.0F);
         float curlAmt = this.curl.getFloat() / 100.0F;
         double time = f.now * (this.animSpeed == null ? 1.0 : this.animSpeed.getFloat() / 100.0);
         float[] air = this.breeze(f, p.headX, p.headY, p.headZ, 0.35F);
         double windX = air[0];
         double windY = air[1];
         double windZ = air[2];

         for (int j = 0; j < n; j++) {
            t.u[j] = Math.min(1.0F, s.along[from + j] / total);
            t.c[j] = Vfx.C[j] | 0xFF000000;
         }

         t.length = total;
         int samples = (n - 1) / 4 + 2;

         for (int c = 0; c < count; c++) {
            double across = count == 1 ? 0.0 : ((double)c / (count - 1) - 0.5) * 0.46;
            double lift = (0.5 - Math.abs((double)c / Math.max(1, count - 1) - 0.5)) * -0.08 + Math.abs(across) * 0.25;
            int base = c * n;
            if (curlAmt > 0.0F) {
               for (int i = 0; i < samples; i++) {
                  int j = Math.min(n - 1, i * 4);
                  Smoke.curl(Vfx.X[j] + f.viewX + c * 7.3, Vfx.Y[j] + f.viewY, Vfx.Z[j] + f.viewZ - c * 4.1, time, 0.7, this.flow);
                  FLOW_X[i] = this.flow[0];
                  FLOW_Y[i] = this.flow[1];
                  FLOW_Z[i] = this.flow[2];
               }
            }

            for (int j = 0; j < n; j++) {
               float u = t.u[j];
               int ja = Math.max(0, j - 1);
               int jb = Math.min(n - 1, j + 1);
               double tx = Vfx.X[jb] - Vfx.X[ja];
               double tz;
               double tl = Math.sqrt(tx * tx + (tz = Vfx.Z[jb] - Vfx.Z[ja]) * tz);
               double sx = tl < 1.0E-6 ? 1.0 : -tz / tl;
               double sz = tl < 1.0E-6 ? 0.0 : tx / tl;
               double spread = across * (1.0 + 1.2 * u);
               double ox = 0.0;
               double oy = 0.0;
               double oz = 0.0;
               if (curlAmt > 0.0F) {
                  int i0 = j / 4;
                  int i1 = Math.min(samples - 1, i0 + 1);
                  int j0 = Math.min(n - 1, i0 * 4);
                  int j1 = Math.min(n - 1, i1 * 4);
                  double b = j1 > j0 ? (double)(j - j0) / (j1 - j0) : 0.0;
                  double amp = curlAmt * 0.85 * Math.pow(u, 1.5);
                  ox = (FLOW_X[i0] + (FLOW_X[i1] - FLOW_X[i0]) * b) * amp;
                  oy = (FLOW_Y[i0] + (FLOW_Y[i1] - FLOW_Y[i0]) * b) * amp * 0.6;
                  oz = (FLOW_Z[i0] + (FLOW_Z[i1] - FLOW_Z[i0]) * b) * amp;
               }

               double drift = Math.min(1.5, (double)s.age[from + j]);
               double x = Vfx.X[j] + sx * spread + ox + windX * drift;
               double y = Vfx.Y[j] + lift * (1.0 - u) + oy + windY * drift;
               double z = Vfx.Z[j] + sz * spread + oz + windZ * drift;
               t.x[base + j] = (float)x;
               t.y[base + j] = (float)y;
               t.z[base + j] = (float)z;
               t.w[base + j] = w0 * (0.8F + 1.9F * u) * (float)Math.sqrt(Math.max(0.25F, 1.0F + this.speedBoost(s.speed[from + j]))) * p.widthScale;
               float torn = 1.0F - Easing.smoothstep(0.5F, 1.0F, u) * 0.7F * Easing.smoothstep(0.35F, 0.75F, (float)noise(u * 9.0 - time * 0.7 + c * 3.1));
               float a = POINT_ALPHA[j] * torn;
               if (mine) {
                  a = NearCamera.dark(Vfx.camera(), x, y, z, a);
               }

               t.a[base + j] = a;
            }
         }

         return true;
      }
   }

   private void smoke(TrailFrame f, WraithStyle.Tendrils t) {
      int n = t.points;
      double scroll = f.now * 0.45;
      RenderState.translucent(false);
      int tex = Smoke.wisps();

      for (int c = 0; c < t.count; c++) {
         int base = c * n;

         for (int j = 0; j < n; j++) {
            Vfx.X[j] = t.x[base + j];
            Vfx.Y[j] = t.y[base + j];
            Vfx.Z[j] = t.z[base + j];
            Vfx.W[j] = t.w[base + j];
            Vfx.C[j] = ColorUtil.mulAlpha(t.c[j], t.a[base + j]);
            U[j] = (float)(t.u[j] * t.length / 1.6 + scroll + c * 0.31);
         }

         TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
         TrailStrip.drawAlong(tex, 0, n, 1.0F, 0.0, 1.0F, U);
      }
   }

   private void light(PlayerTrail p, TrailFrame f, WraithStyle.Tendrils t) {
      int n = t.points;
      float glowAmount = this.glow.getFloat() / 100.0F;
      float em = p.reduced ? 0.0F : this.embers.getFloat() / 100.0F;
      float edge = this.edgeAmount();
      double time = f.now;
      double colorTime = f.colorTime;
      RenderState.additive();
      if (edge > 0.01F) {
         int tex = Smoke.wisps();
         double scroll = f.now * 0.45;

         for (int c = 0; c < t.count; c++) {
            int base = c * n;

            for (int j = 0; j < n; j++) {
               Vfx.X[j] = t.x[base + j];
               Vfx.Y[j] = t.y[base + j];
               Vfx.Z[j] = t.z[base + j];
               Vfx.W[j] = t.w[base + j] * 1.25F;
               Vfx.C[j] = ColorUtil.mulAlpha(this.slotColor(this.edgeColor, t.u[j], colorTime), t.a[base + j] * 0.16F * edge * (0.6F + 0.4F * glowAmount));
               U[j] = (float)(t.u[j] * t.length / 1.6 + scroll + c * 0.31 + 0.5);
            }

            TrailStrip.sides(0, n, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
            TrailStrip.drawAlong(tex, 0, n, 1.0F, 0.0, 1.0F, U);
         }
      }

      if (!(em <= 0.01F)) {
         for (int c = 0; c < t.count; c++) {
            int base = c * n;
            int m = 0;
            double side = 0.0;

            for (int j = 0; j < n; j++) {
               float u = t.u[j];
               float lit = Easing.smoothstep(0.1F, 0.2F, u) * (1.0F - Easing.smoothstep(0.45F, 0.6F, u));
               float a = lit
                  * Easing.smoothstep(0.62F, 0.7F, (float)noise(u * 26.0 + c * 3.3 - time * 0.15))
                  * (0.6F + 0.4F * (float)noise(time * 9.0 + j * 0.7 + c * 5.0))
                  * t.a[base + j]
                  * em
                  * (0.45F + 0.45F * glowAmount);
               if (a <= 0.01F) {
                  if (m >= 2) {
                     crackLine(f, m);
                  }

                  m = 0;
               } else {
                  if (m == 0) {
                     side = (hash(j * 3 + c * 17) - 0.5) * 0.5;
                  }

                  int ja = Math.max(0, j - 1);
                  int jb = Math.min(n - 1, j + 1);
                  double ty = t.y[base + jb] - t.y[base + ja];
                  double vy;
                  double tz;
                  double vz;
                  double sx = ty * (vz = f.cam.posZ - t.z[base + j]) - (tz = t.z[base + jb] - t.z[base + ja]) * (vy = f.cam.posY - t.y[base + j]);
                  double sz;
                  double tx;
                  double vx;
                  double sy;
                  double sl = Math.sqrt(
                     sx * sx + (sy = tz * (vx = f.cam.posX - t.x[base + j]) - (tx = t.x[base + jb] - t.x[base + ja]) * vz) * sy + (sz = tx * vy - ty * vx) * sz
                  );
                  double jag = sl < 1.0E-9 ? 0.0 : (side + (hash(j * 7 + c * 131) - 0.5) * 0.1) * t.w[base + j] / sl;
                  Vfx.X[m] = t.x[base + j] + sx * jag;
                  Vfx.Y[m] = t.y[base + j] + sy * jag;
                  Vfx.Z[m] = t.z[base + j] + sz * jag;
                  Vfx.W[m] = Math.max(0.01F, t.w[base + j] * 0.05F);
                  Vfx.C[m] = ColorUtil.mulAlpha(this.slotColor(this.embersColor, u, colorTime), Math.min(1.0F, a));
                  m++;
               }
            }

            if (m >= 2) {
               crackLine(f, m);
            }
         }
      }
   }

   private static void crackLine(TrailFrame f, int m) {
      TrailStrip.sides(0, m, 0, f.cam.posX, f.cam.posY, f.cam.posZ, 0.0F, null, 0);
      TrailStrip.draw(Textures.LINE_GLOW, 0, m, 3.5F, 0.0, 0.4F);
      TrailStrip.draw(Textures.LINE_CORE, 0, m, 1.0F, 0.0, 0.85F);
   }

   private void emit(PlayerTrail p, TrailFrame f) {
      int due = this.piecesDue(p, 1.6 * this.embers.getFloat() / 100.0, 4);
      if (due != 0) {
         TrailShape s = p.shape;
         int k = s.runs - 1;
         int to = s.runEnd[k];
         int from = s.runStart[k];
         if (to - from >= 2) {
            FxParticles pool = f.pieces(this.type);
            float life = 0.8F + this.lengthNow(p.speed) * 0.4F;

            for (int i = 0; i < due && !this.module.piecesFull(f); i++) {
               this.rng.seed((int)(f.now * 4507.0) + i * 173 + p.entityId * 11);
               double want = s.length * (0.1 + this.rng.next() * 0.4);
               int j = to - 1;

               while (j > from && s.along[j] < want) {
                  j--;
               }

               double x = s.x[j] + this.rng.signed() * 0.46 * 0.6;
               double z;
               double y;
               if (!(f.nearFade(x, y = s.y[j] + this.rng.signed() * 0.12, z = s.z[j] + this.rng.signed() * 0.46 * 0.6) < 0.4F)) {
                  pool.spawn(
                        7,
                        x,
                        y,
                        z,
                        this.rng.signed() * 0.2F,
                        0.4F + this.rng.next() * 0.4F,
                        this.rng.signed() * 0.2F,
                        life * (0.7F + this.rng.next() * 0.6F),
                        0.05F + this.rng.next() * 0.035F
                     )
                     .gravity(-0.15F)
                     .drag(0.6F)
                     .alpha(this.opacity.getFloat() / 100.0F * p.alpha)
                     .flags(2)
                     .color(this.tint != 0 ? this.tint : this.embersColor.getColor(this.rng.next()))
                     .wind(0.6F);
               }
            }
         }
      }
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      if (pass == FxPass.GLOW) {
         FxParticles pool = f.piecesIfAny(this.type);
         if (pool != null) {
            pool.render(f.viewX, f.viewY, f.viewZ);
         }
      }
   }

   private static double noise(double x) {
      double fl = Math.floor(x);
      int i = (int)fl;
      double t = x - fl;
      t = t * t * (3.0 - 2.0 * t);
      return hash(i) + (hash(i + 1) - hash(i)) * t;
   }

   private static double hash(int i) {
      int h = i * 668265261 ^ 374761393;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "%d tendril(s), width %.0f%%, curl %.0f%%, embers %.0f%%, edge light %s",
         this.tendrils.getInt(),
         this.width.getFloat(),
         this.curl.getFloat(),
         this.embers.getFloat(),
         this.edgeLight.get().displayName()
      );
   }

   private static final class Tendrils {
      double stamp = -1.0;
      double seen;
      TrailFrame scene;
      int count;
      int points;
      float length;
      float[] x = new float[0];
      float[] y = new float[0];
      float[] z = new float[0];
      float[] w = new float[0];
      float[] a = new float[0];
      float[] u = new float[0];
      int[] c = new int[0];

      private Tendrils() {
      }

      void ensure(int n) {
         int need = n * 5;
         if (this.x.length < need) {
            this.x = new float[need];
            this.y = new float[need];
            this.z = new float[need];
            this.w = new float[need];
            this.a = new float[need];
         }

         if (this.u.length < n) {
            this.u = new float[n];
            this.c = new int[n];
         }
      }
   }
}
