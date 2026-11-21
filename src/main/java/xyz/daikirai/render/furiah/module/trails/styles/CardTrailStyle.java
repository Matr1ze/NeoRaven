package xyz.daikirai.render.furiah.module.trails.styles;

import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxParticles;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.fx.kit.Cards;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.trail.TrailAnchor;
import xyz.daikirai.render.furiah.module.trails.PlayerTrail;
import xyz.daikirai.render.furiah.module.trails.TrailFrame;
import xyz.daikirai.render.furiah.module.trails.TrailStyle;
import xyz.daikirai.render.furiah.module.trails.TrailStyleBase;
import xyz.daikirai.render.furiah.module.trails.FuriahTrails;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class CardTrailStyle extends TrailStyleBase {
   private static final int MAX_CARDS = 96;
   private static final float PEEL = 0.28F;
   private static final float EXIT = 0.6F;
   private static final float FLIP_SECONDS = 1.2F;
   private static final int EMBER = -24512;
   private static final float RIM = 0.2F;
   public final ThemeTint symbolColor;
   public final ThemeTint foilTint;
   public final ModeSetting<Cards.Foil> foil;
   public final SliderSetting spacing;
   public final BooleanSetting flip;
   public final ModeSetting<Cards.Design> design;
   public final ModeSetting<Cards.Symbols> symbols;
   public final ModeSetting<CardTrailStyle.Exit> exit;
   private final Rng rng = new Rng();
   private final Cards.Card[] drawn = new Cards.Card[96];
   private final int[] drawnIndex = new int[96];

   public CardTrailStyle(FuriahTrails module) {
      super(
         module,
         TrailStyle.CARD_TRAIL,
         spec().size(0.15, 0.6, 0.34, 0.01).length(2.4).anchor(TrailAnchor.BACK, false)
      );
      this.symbolColor = this.signatureColor("symbols", "Symbols", "The line art on the faces and the backs.", -1185802, -1);
      this.foil = this.setting(
         new ModeSetting<>(
               "card_trail.foil", "Foil", "The borders' metal leaf: Gold, Silver, Rose gold, or Custom (the Foil Tint).", Cards.Foil.class, Cards.Foil.GOLD
            )
            .style(ModeSetting.Style.CHIPS)
            .keywords(new String[]{"foil", "gold", "silver", "rose gold", "material", "metal"})
      );
      this.foil.group("Colors");
      this.foilTint = ThemeTint.detail(-1851025);
      this.doesNotApply(new Setting[]{this.density, this.glow});
      this.spacing = this.setting(
         new SliderSetting("card_trail.spacing", "Spacing", "How far you travel between two cards.", 1.0, 4.0, 2.0, 0.25).unit(" blocks")
      );
      this.flip = this.setting(new BooleanSetting("card_trail.flip", "Flip", "The cards turn over and over, their backs and faces coming round in turn.", true));
      this.design = this.setting(
         new ModeSetting<>(
               "card_trail.design", "Card Design", "The frames on the faces and the pattern on the backs.", Cards.Design.class, Cards.Design.CELESTIAL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.symbols = this.setting(
         new ModeSetting<>("card_trail.symbolSet", "Symbols", "Which symbols the faces show.", Cards.Symbols.class, Cards.Symbols.SKY)
            .style(ModeSetting.Style.CHIPS)
      );
      this.exit = this.setting(
         new ModeSetting<>("card_trail.exit", "Exit", "How each card leaves.", CardTrailStyle.Exit.class, CardTrailStyle.Exit.BURN)
            .style(ModeSetting.Style.CHIPS)
      );

      for (int k = 0; k < 96; k++) {
         this.drawn[k] = new Cards.Card();
      }
   }

   @Override
   protected void addLookTargets(List<SliderSetting> out) {
      out.add(this.spacing);
   }

   @Override
   protected TrailStyleBase.Store newStore() {
      return new CardTrailStyle.Deck();
   }

   @Override
   public int passes() {
      return FxPass.SOLID.bit() | FxPass.GLOW.bit();
   }

   @Override
   public void renderPass(PlayerTrail p, TrailFrame f, FxPass pass) {
      if (!p.ghost && this.spawnPass(pass)) {
         int due = this.piecesDue(p, 1.0 / this.spacing.getFloat(), 2);
         if (due != 0) {
            CardTrailStyle.Deck d = this.store(f);
            double yawR = Math.toRadians(p.pose.bodyYaw);
            float fx = (float)(-Math.sin(yawR));
            float fz = (float)Math.cos(yawR);
            int[] set = this.symbols.get().set;
            float life = this.lengthNow(p.speed);

            for (int i = 0; i < due && !this.module.piecesFull(f); i++) {
               this.rng.seed((int)(f.now * 3803.0) + i * 151 + p.entityId * 17);
               double x = p.headX - fx * 0.15;
               double y = p.headY + 0.05;
               double z = p.headZ - fz * 0.15;
               if (!(f.nearFade(x, y, z) < 0.3F)) {
                  int k = d.add();
                  d.x[k] = x;
                  d.y[k] = y;
                  d.z[k] = z;
                  float side = this.rng.signed() * 0.35F;
                  d.vx[k] = -fx * (1.3F + this.rng.next() * 0.5F) + fz * side;
                  d.vz[k] = -fz * (1.3F + this.rng.next() * 0.5F) - fx * side;
                  d.vy[k] = 0.45F + this.rng.next() * 0.35F;
                  d.born[k] = d.clock;
                  d.life[k] = life * (0.85F + this.rng.next() * 0.3F);
                  d.half[k] = this.size.getFloat() * 0.5F * p.widthScale;
                  d.alpha[k] = this.opacity.getFloat() / 100.0F * p.alpha;
                  d.yaw[k] = (float)(yawR + (this.rng.next() < 0.5F ? 0.5 : -0.5) * Math.PI + this.rng.signed() * 0.4);
                  d.spin[k] = this.rng.signed() * 0.45F;
                  d.flipAt[k] = this.rng.next();
                  d.bob[k] = this.rng.next() * 6.283F;
                  d.tumble[k] = this.rng.signed();
                  d.symbol[k] = set[d.dealt++ % set.length];
                  d.tint[k] = this.tint;
                  d.far[k] = p.reduced;
                  d.burst[k] = false;
               }
            }
         }
      }
   }

   private static float exitOf(CardTrailStyle.Deck d, int i) {
      float age = d.clock - d.born[i];
      return Easing.clamp01((age - (d.life[i] - 0.6F)) / 0.6F);
   }

   private int layout(TrailFrame f, CardTrailStyle.Deck d, Camera cam) {
      int m = 0;
      CardTrailStyle.Exit how = this.exit.get();

      for (int i = 0; i < d.n && m < 96; i++) {
         float age = d.clock - d.born[i];
         float e = exitOf(d, i);
         boolean far = d.far[i];
         float peel = Easing.outCubic(Easing.clamp01(age / 0.28F));
         double x = d.x[i];
         double y = d.y[i] + Math.sin(age * 2.2F + d.bob[i]) * 0.035 * peel;
         double z = d.z[i];
         double half = d.half[i] * (0.4 + 0.6 * peel);
         float a = d.alpha[i] * Easing.clamp01(age / 0.08F) * f.nearFade(x, y, z) * f.fog(x, y, z);
         double tilt = 0.0;
         if (!far && (how != CardTrailStyle.Exit.BURN || Cards.burnWorks())) {
            if (how == CardTrailStyle.Exit.SPARKLE) {
               half *= 1.0 - 0.75 * Easing.inCubic(e);
               a *= 1.0F - Easing.smoothstep(0.6F, 1.0F, e);
            } else if (how == CardTrailStyle.Exit.FALL) {
               double drop = 1.4 * e * e;
               y -= drop;
               tilt = d.tumble[i] * e * 2.2;
               a *= 1.0F - e;
            }
         } else {
            a *= 1.0F - e;
         }

         if (!(a <= 0.01F) && !(half <= 1.0E-4)) {
            double turn = d.yaw[i];
            if (this.flip.get() && !far) {
               turn += (age / 1.2F + d.flipAt[i]) * Math.PI;
            }

            double nx = Math.cos(turn);
            double nz = Math.sin(turn);
            Cards.Card c = this.drawn[m];
            double ct = Math.cos(tilt);
            double st = Math.sin(tilt);
            double rx0 = -nz;
            c.ux = rx0 * st;
            c.uy = ct;
            c.uz = nx * st;
            c.nx = nx;
            c.ny = 0.0;
            c.nz = nz;
            c.rx = c.uy * c.nz - c.uz * c.ny;
            c.ry = c.uz * c.nx - c.ux * c.nz;
            c.rz = c.ux * c.ny - c.uy * c.nx;
            c.x = x - f.viewX;
            c.y = y - f.viewY;
            c.z = z - f.viewZ;
            c.half = half;
            c.alpha = a;
            c.symbol = d.symbol[i];
            c.uncut();
            this.drawnIndex[m] = i;
            m++;
         }
      }

      return m;
   }

   @Override
   public void renderPiecesPass(TrailFrame f, FxPass pass) {
      CardTrailStyle.Deck d = (CardTrailStyle.Deck)f.store(this.type);
      if (d != null && d.n != 0) {
         Camera cam = Vfx.camera();
         int m = this.layout(f, d, cam);
         if (m != 0) {
            boolean burning = this.exit.get() == CardTrailStyle.Exit.BURN && Cards.burnWorks();
            if (pass == FxPass.GLOW) {
               this.glow(f, d, m, cam, burning);
            } else {
               if (burning) {
                  for (int k = 0; k < m; k++) {
                     int i = this.drawnIndex[k];
                     float e = exitOf(d, i);
                     if (e > 0.0F && !d.far[i]) {
                        Cards.burnHoles(this.drawn[k], e, i % 4, cam);
                     }
                  }
               }

               MeshBatch.begin(Material.CUSTOM, Finish.SATIN, this.matLight, 0.2F + 0.3F * this.night, -1, true, true);

               try {
                  int body = this.color.getColor(0.5F);

                  for (int kx = 0; kx < m; kx++) {
                     int i = this.drawnIndex[kx];
                     Cards.body(this.drawn[kx], Cards.withAlpha(d.tint[i] != 0 ? d.tint[i] : body, this.drawn[kx].alpha));
                  }
               } finally {
                  MeshBatch.end();
               }

               Cards.Foil fl = this.foil.get();
               MeshBatch.begin(fl.material, Finish.METALLIC, this.matLight, 0.2F, -1, true, true);

               try {
                  int tintCol = fl == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : -1;

                  for (int kx = 0; kx < m; kx++) {
                     int i = this.drawnIndex[kx];
                     double sweep = ((d.clock - d.born[i]) * 0.7 + d.flipAt[i]) % 2.2 * 1.8 - 0.4;
                     Cards.foil(this.drawn[kx], Cards.withAlpha(tintCol, this.drawn[kx].alpha), sweep, true);
                  }
               } finally {
                  MeshBatch.end();
               }

               int var32 = ColorUtil.shade(this.symbolColor.getColor(0.5F), Math.max(0.45F, this.matLight)) | 0xFF000000;
               RenderState.decal(true);
               RenderState.translucent(false);

               try {
                  Cards.art(this.drawn, m, this.design.get().ordinal(), var32, 0.9F);
               } finally {
                  RenderState.decal(false);
                  RenderState.translucent(true);
               }
            }
         }
      }
   }

   private void glow(TrailFrame f, CardTrailStyle.Deck d, int m, Camera cam, boolean burning) {
      CardTrailStyle.Exit how = this.exit.get();

      for (int k = 0; k < m; k++) {
         int i = this.drawnIndex[k];
         float e = exitOf(d, i);
         if (!(e <= 0.0F) && !d.far[i]) {
            Cards.Card c = this.drawn[k];
            if (how == CardTrailStyle.Exit.BURN && burning) {
               Cards.burnEdge(c, e, 0.07F, i % 4, -24512, c.alpha, cam);
            } else if ((how == CardTrailStyle.Exit.SPARKLE || how == CardTrailStyle.Exit.BURN && !burning) && !d.burst[i]) {
               d.burst[i] = true;
               this.sparkles(f, d, i);
            }
         }
      }

      FxParticles pool = f.piecesIfAny(this.type);
      if (pool != null) {
         pool.render(f.viewX, f.viewY, f.viewZ);
      }
   }

   private void sparkles(TrailFrame f, CardTrailStyle.Deck d, int i) {
      FxParticles pool = f.pieces(this.type);
      Cards.Foil fl = this.foil.get();
      int col = fl == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : fl.material.swatch();

      for (int s = 0; s < 7 && !this.module.piecesFull(f); s++) {
         this.rng.seed(i * 97 + s * 13 + d.dealt);
         pool.spawn(
               s % 3 == 0 ? 5 : 1,
               d.x[i] + this.rng.signed() * d.half[i],
               d.y[i] + this.rng.signed() * d.half[i] * 1.5,
               d.z[i] + this.rng.signed() * d.half[i],
               this.rng.signed() * 0.6F,
               0.2F + this.rng.next() * 0.6F,
               this.rng.signed() * 0.6F,
               0.45F + this.rng.next() * 0.3F,
               0.03F + this.rng.next() * 0.025F
            )
            .drag(1.5F)
            .color(col)
            .alpha(d.alpha[i])
            .end(0.01F);
      }
   }

   @Override
   public String stateForTest() {
      return String.format(
         Locale.ROOT,
         "spacing %.2f, flip %s, %s, %s, %s foil, exit %s (burn %s)",
         this.spacing.getFloat(),
         this.flip.get() ? "on" : "off",
         this.design.get().displayName(),
         this.symbols.get().displayName(),
         this.foil.get().displayName(),
         this.exit.get().displayName(),
         Cards.burnWorks() ? "with holes" : "as a fade"
      );
   }

   static final class Deck implements TrailStyleBase.Store {
      final double[] x = new double[96];
      final double[] y = new double[96];
      final double[] z = new double[96];
      final float[] vx = new float[96];
      final float[] vy = new float[96];
      final float[] vz = new float[96];
      final float[] born = new float[96];
      final float[] life = new float[96];
      final float[] half = new float[96];
      final float[] alpha = new float[96];
      final float[] yaw = new float[96];
      final float[] spin = new float[96];
      final float[] flipAt = new float[96];
      final float[] bob = new float[96];
      final float[] tumble = new float[96];
      final int[] symbol = new int[96];
      final int[] tint = new int[96];
      final boolean[] far = new boolean[96];
      final boolean[] burst = new boolean[96];
      int n;
      int dealt;
      float clock;

      int add() {
         if (this.n < 96) {
            return this.n++;
         } else {
            int oldest = 0;

            for (int i = 1; i < this.n; i++) {
               if (this.born[i] < this.born[oldest]) {
                  oldest = i;
               }
            }

            return oldest;
         }
      }

      @Override
      public void update(float dt) {
         this.clock += dt;
         if (!(dt <= 0.0F)) {
            float damp = (float)Math.exp(-2.4 * dt);
            int w = 0;

            for (int i = 0; i < this.n; i++) {
               float age = this.clock - this.born[i];
               if (!(age >= this.life[i])) {
                  this.vx[i] = this.vx[i] * damp;
                  this.vz[i] = this.vz[i] * damp;
                  this.vy[i] = this.vy[i] * damp;
                  this.x[i] = this.x[i] + this.vx[i] * dt;
                  this.y[i] = this.y[i] + this.vy[i] * dt;
                  this.z[i] = this.z[i] + this.vz[i] * dt;
                  this.yaw[i] = this.yaw[i] + this.spin[i] * dt;
                  if (w != i) {
                     this.copy(i, w);
                  }

                  w++;
               }
            }

            this.n = w;
         }
      }

      private void copy(int a, int b) {
         this.x[b] = this.x[a];
         this.y[b] = this.y[a];
         this.z[b] = this.z[a];
         this.vx[b] = this.vx[a];
         this.vy[b] = this.vy[a];
         this.vz[b] = this.vz[a];
         this.born[b] = this.born[a];
         this.life[b] = this.life[a];
         this.half[b] = this.half[a];
         this.alpha[b] = this.alpha[a];
         this.yaw[b] = this.yaw[a];
         this.spin[b] = this.spin[a];
         this.flipAt[b] = this.flipAt[a];
         this.bob[b] = this.bob[a];
         this.tumble[b] = this.tumble[a];
         this.symbol[b] = this.symbol[a];
         this.tint[b] = this.tint[a];
         this.far[b] = this.far[a];
         this.burst[b] = this.burst[a];
      }

      @Override
      public int size() {
         return this.n;
      }

      @Override
      public void clear() {
         this.n = 0;
      }
   }

   public static enum Exit implements Displayable {
      BURN("Burn away", "Eaten away from its edges with a thin ember line"),
      SPARKLE("Sparkle out", "A burst of gold sparkles as it shrinks away"),
      FALL("Fall and fade", "Tumbling down as it fades");

      private final String displayName;
      private final String description;

      private Exit(String displayName, String description) {
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
