package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
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
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class EmbersStyle extends TrailStyleBase {
   public final ModeSetting<EmbersStyle.Variant> variant;
   public final SliderSetting gravity;
   public final SliderSetting flicker;
   public final BooleanSetting bounce;
   private final Rng rng = new Rng();
   private int bursts;

   public EmbersStyle(FuriahTrails module) {
      super(
         module, TrailStyle.EMBERS, spec().size(0.02, 0.3, 0.12, 0.01).length(1.0).anchor(TrailAnchor.FEET, false).animated().windy()
      );
      this.variant = this.setting(
         new ModeSetting<>("embers.variant", "Variant", "Sparks, embers or flames.", EmbersStyle.Variant.class, EmbersStyle.Variant.SPARKS)
      );
      this.gravity = this.setting(
         new SliderSetting("embers.gravity", "Gravity or Rise", "How fast sparks fall, or (Embers, Flames) how fast they rise.", 0.0, 200.0, 100.0, 5.0)
            .unit("%")
      );
      this.bounce = this.setting(new BooleanSetting("embers.bounce", "Bounce", "Sparks bounce once on the ground.", true));
      this.flicker = this.setting(new SliderSetting("embers.flicker", "Flicker", "How much they flicker.", 0.0, 100.0, 50.0, 1.0).unit("%"));
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.flicker);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new EmbersStyle.Wisps();
   }

   public int burstsForTest() {
      return this.bursts;
   }

   @Override
   protected void drawStore(TrailFrame f, TrailStyleBase.Store store) {
      EmbersStyle.Wisps ws = (EmbersStyle.Wisps)store;
      Textures.ensureTrailsLoaded();
      int i = 0;

      while (i < ws.n) {
         double dx = ws.x[i] - f.eyeX;
         double dy = ws.y[i] - f.eyeY;
         double dz = ws.z[i] - f.eyeZ;
         ws.dist[i] = dx * dx + dy * dy + dz * dz;

         int j;
         for (j = i; j > 0 && ws.dist[ws.order[j - 1]] < ws.dist[i]; j--) {
            ws.order[j] = ws.order[j - 1];
         }

         ws.order[j] = i++;
      }

      float glowAmount = this.glow.getFloat() / 100.0F;
      float flick = this.flicker.getFloat() / 100.0F;
      float rate = 14.0F * this.animSpeed.getFloat() / 100.0F;

      for (int pass = 0; pass < 2; pass++) {
         if (pass == 0) {
            RenderState.translucent(false);
         } else {
            RenderState.additive();
         }

         for (int o = 0; o < ws.n; o++) {
            int i2 = ws.order[o];
            float age = ws.clock - ws.born[i2];
            float u = Math.min(1.0F, age / Math.max(0.05F, ws.life[i2]));
            double wx = ws.x[i2];
            double wy = ws.y[i2];
            double wz = ws.z[i2];
            float a = ws.alpha[i2]
               * Easing.smoothstep(0.0F, 0.12F, u)
               * (1.0F - Easing.smoothstep(0.35F, 1.0F, u))
               * (1.0F - flick * 0.35F * (0.5F + 0.5F * (float)Math.sin(age * 23.0F + ws.phase[i2] * 40.0F)))
               * f.nearFade(wx, wy, wz)
               * f.fog(wx, wy, wz);
            if (!(a <= 0.004F)) {
               float h = ws.size[i2] * (1.0F - 0.55F * u);
               float half = h * 0.5F;
               int frame = (int)(age * rate + ws.phase[i2] * 4.0F) % 4;
               float u0 = frame / 4.0F;
               float u1 = (frame + 1) / 4.0F;
               int c = lit(ws.tint[i2] != 0 ? ws.tint[i2] : this.colorAt(ws.spot[i2], u, ws.speed[i2], f.colorTime, 0.0F), f, wx, wy, wz);
               double x = wx - f.viewX;
               double y = wy - f.viewY + half * 0.9;
               double z = wz - f.viewZ;
               if (pass == 0) {
                  Vfx.sprite(Textures.FLAME, x, y, z, half * 0.8, half, ws.lean[i2], u0, 0.0F, u1, 1.0F, ColorUtil.mulAlpha(c, a * 0.65F));
               } else {
                  Vfx.sprite(Textures.FLAME, x, y, z, half * 0.8, half, ws.lean[i2], u0, 0.0F, u1, 1.0F, ColorUtil.mulAlpha(c, a * (0.35F + 0.4F * glowAmount)));
                  Vfx.sprite(
                     Textures.FLAME,
                     x,
                     y - half * 0.25,
                     z,
                     half * 0.45,
                     half * 0.6,
                     ws.lean[i2],
                     u0,
                     0.0F,
                     u1,
                     1.0F,
                     ColorUtil.mulAlpha(ColorUtil.core(c), a * 0.6F)
                  );
               }
            }
         }
      }

      RenderState.additive();
   }

   @Override
   public void render(PlayerTrail p, TrailFrame f) {
      if (!p.ghost) {
         EmbersStyle.Variant v = this.variant.get();
         PlayerTrail.StyleState st = p.style;
         int extra = 0;
         if (v == EmbersStyle.Variant.SPARKS && f.dt > 0.0F) {
            float yaw = p.pose.bodyYaw;
            float last = (float)st.vars[2];
            float turn = Math.abs(((yaw - last) % 360.0F + 540.0F) % 360.0F - 180.0F) / f.dt;
            if (st.vars[3] != 0.0 && turn > 240.0F && p.growing) {
               extra += 2;
            }

            if (p.growing) {
               st.vars[0] = 0.0;
               st.vars[1] = Math.max((double)p.speed, st.vars[1] - 2.0 * f.dt);
            } else if (st.vars[0] == 0.0) {
               st.vars[0] = 1.0;
               if (st.vars[3] != 0.0 && st.vars[1] > 4.8) {
                  extra += 10;
                  this.bursts++;
               }

               st.vars[1] = 0.0;
            }

            st.vars[2] = yaw;
            st.vars[3] = 1.0;
         }

         int due;
         if ((due = this.piecesDue(p, v == EmbersStyle.Variant.FLAMES ? 5.0 : (v == EmbersStyle.Variant.EMBERS ? 4.0 : 6.0), 14) + extra) != 0) {
            double yawR = Math.toRadians(p.pose.bodyYaw);
            float fx = (float)(-Math.sin(yawR));
            float fz = (float)Math.cos(yawR);
            float[] w = this.wind(f, p.headX, p.headY, p.headZ);
            float windX = w[0];
            float windY = w[1];
            float windZ = w[2];
            float g = this.gravity.getFloat() / 100.0F;
            float sz = this.size.getFloat() * (float)Math.sqrt(Math.max(0.25F, 1.0F + this.speedBoost(p.speed))) * p.widthScale;
            float life = this.lengthNow(p.speed);
            float op = this.opacity.getFloat() / 100.0F * p.alpha;
            boolean twinkle = this.flicker.getFloat() >= 25.0F;
            FxParticles pool = f.pieces(this.type);

            for (int i = 0; i < due; i++) {
               if (this.module.piecesFull(f)) {
                  return;
               }

               this.rng.seed((int)(f.now * 4099.0) + i * 131 + p.entityId * 7);
               double x = p.headX - fx * 0.15 + this.rng.signed() * 0.18;
               double y = p.headY + this.rng.next() * 0.06;
               double z = p.headZ - fz * 0.15 + this.rng.signed() * 0.18;
               if (!(f.nearFade(x, y, z) < 0.4F)) {
                  float side = this.rng.signed();
                  switch (v) {
                     case EMBERS:
                        pool.spawn(
                              7,
                              x,
                              y,
                              z,
                              windX + this.rng.signed() * 0.35F,
                              (0.4F + this.rng.next() * 0.8F) * g + windY,
                              windZ + this.rng.signed() * 0.35F,
                              life * (0.8F + this.rng.next() * 0.8F),
                              sz * (0.6F + this.rng.next() * 0.6F)
                           )
                           .gravity(-0.5F * g)
                           .drag(0.7F)
                           .alpha(op)
                           .sway(0.0F, this.rng.next() * 10.0F)
                           .flags(twinkle ? 2 : 0);
                        this.colorPiece(pool, this.rng.next(), 0.0F, p.speed);
                        break;
                     case FLAMES:
                        EmbersStyle.Wisps ws = this.store(f);
                        int k = ws.add();
                        ws.x[k] = x;
                        ws.y[k] = p.pose.y + 0.02;
                        ws.z[k] = z;
                        ws.vx[k] = windX * 0.6F + this.rng.signed() * 0.12F;
                        ws.vz[k] = windZ * 0.6F + this.rng.signed() * 0.12F;
                        ws.vy[k] = 0.2F * g;
                        ws.rise[k] = (0.7F + this.rng.next() * 0.6F) * g + windY;
                        ws.born[k] = ws.clock;
                        ws.life[k] = Math.min(0.95F, life * (0.45F + this.rng.next() * 0.3F));
                        ws.size[k] = sz * (4.5F + this.rng.next() * 2.0F);
                        ws.alpha[k] = op;
                        ws.spot[k] = this.rng.next();
                        ws.speed[k] = p.speed;
                        ws.phase[k] = this.rng.next();
                        ws.lean[k] = this.rng.signed() * 0.18F;
                        ws.tint[k] = this.tint;
                        if (this.rng.next() < 0.25F) {
                           pool.spawn(
                                 7,
                                 x,
                                 y + 0.1,
                                 z,
                                 windX + this.rng.signed() * 0.4F,
                                 (1.0F + this.rng.next()) * g + windY,
                                 windZ + this.rng.signed() * 0.4F,
                                 life * (0.8F + this.rng.next() * 0.5F),
                                 sz * 0.6F
                              )
                              .gravity(-0.6F * g)
                              .drag(0.8F)
                              .alpha(op)
                              .sway(0.0F, this.rng.next() * 10.0F)
                              .flags(2);
                           this.colorPiece(pool, this.rng.next(), 0.0F, p.speed);
                        }
                        break;
                     default:
                        float back = 1.5F + this.rng.next() * 2.2F;
                        float up = 1.4F + this.rng.next() * 1.8F;
                        float vx = -fx * back + -fz * side * 1.3F;
                        float vz = -fz * back + fx * side * 1.3F;
                        pool.spawn(
                              2,
                              x,
                              p.pose.y + 0.05,
                              z,
                              vx,
                              up,
                              vz,
                              Math.min(1.2F, life * (0.35F + this.rng.next() * 0.35F)),
                              sz * (0.5F + this.rng.next() * 0.6F)
                           )
                           .gravity(13.0F * g)
                           .drag(0.4F)
                           .ground(p.pose.y, this.bounce.get() ? 1 : 0, 0.35F)
                           .alpha(op)
                           .hot(0.8F)
                           .sway(0.0F, this.rng.next() * 10.0F)
                           .flags(twinkle ? 2 : 0);
                        float spot = this.rng.next();
                        if (this.colorAlong != TrailStyleBase.ColorAlong.BY_SPEED && this.tint == 0) {
                           pool.color(this.color, spot);
                        } else {
                           this.colorPiece(pool, spot, 0.0F, p.speed);
                        }
                  }
               }
            }
         }
      }
   }

   public static enum Variant implements Displayable {
      SPARKS("Sparks", "Fast sparks scraped off behind your feet, bouncing once"),
      EMBERS("Embers", "Glowing specks floating up, flickering and cooling"),
      FLAMES("Flames", "Soft flame wisps licking up from the path");

      private final String name;
      private final String description;

      private Variant(String name, String description) {
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

   static final class Wisps implements TrailStyleBase.Store {
      static final int CAP = 160;
      final double[] x = new double[160];
      final double[] y = new double[160];
      final double[] z = new double[160];
      final float[] vx = new float[160];
      final float[] vy = new float[160];
      final float[] vz = new float[160];
      final float[] rise = new float[160];
      final float[] born = new float[160];
      final float[] life = new float[160];
      final float[] size = new float[160];
      final float[] alpha = new float[160];
      final float[] spot = new float[160];
      final float[] speed = new float[160];
      final float[] phase = new float[160];
      final float[] lean = new float[160];
      final int[] order = new int[160];
      final int[] tint = new int[160];
      final double[] dist = new double[160];
      int n;
      int oldest;
      float clock;

      int add() {
         if (this.n < 160) {
            return this.n++;
         } else {
            int i = this.oldest;
            this.oldest = (this.oldest + 1) % 160;
            return i;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         if (!(dt <= 0.0F)) {
            float damp = (float)Math.exp(-2.0 * dt);
            float toRise = (float)Math.exp(-4.0 * dt);
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               if (!(this.clock - this.born[i] >= this.life[i])) {
                  this.vx[i] = this.vx[i] * damp;
                  this.vz[i] = this.vz[i] * damp;
                  this.vy[i] = this.rise[i] + (this.vy[i] - this.rise[i]) * toRise;
                  this.x[i] = this.x[i] + this.vx[i] * dt;
                  this.y[i] = this.y[i] + this.vy[i] * dt;
                  this.z[i] = this.z[i] + this.vz[i] * dt;
                  if (w != i) {
                     this.copy(i, w);
                  }

                  w++;
               }
            }

            this.n = w;
            this.oldest = 0;
         }
      }

      private void copy(int a, int b) {
         this.x[b] = this.x[a];
         this.y[b] = this.y[a];
         this.z[b] = this.z[a];
         this.vx[b] = this.vx[a];
         this.vy[b] = this.vy[a];
         this.vz[b] = this.vz[a];
         this.rise[b] = this.rise[a];
         this.born[b] = this.born[a];
         this.life[b] = this.life[a];
         this.size[b] = this.size[a];
         this.alpha[b] = this.alpha[a];
         this.spot[b] = this.spot[a];
         this.speed[b] = this.speed[a];
         this.phase[b] = this.phase[a];
         this.lean[b] = this.lean[a];
         this.tint[b] = this.tint[a];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
         this.oldest = 0;
      }
   }
}
