package xyz.daikirai.render.furiah.module.landing.styles;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Ground;
import xyz.daikirai.render.furiah.fx.kit.CardArt;
import xyz.daikirai.render.furiah.fx.kit.Cards;
import xyz.daikirai.render.furiah.fx.look.NearCamera;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.landing.LandingFx;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;
import xyz.daikirai.render.furiah.module.landing.LandingStyleBase;
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

public final class CardSpreadStyle extends LandingStyleBase {
   private static final float SLID = 0.3F;
   private static final float FLIPPED = 0.8F;
   private static final float HELD = 1.1F;
   private static final float END = 1.7F;
   private static final float JUMP_OUT = 0.25F;
   private static final float JUMP_IN = 0.6F;
   private static final int MAX_CARDS = 16;
   private static final double HALF = 0.17;
   private static final double RADIUS = 1.15;
   private static final float RIM = 0.14F;
   private static final float DUST_WIND = 0.3F;
   public final ThemeTint symbolColor;
   public final ModeSetting<Cards.Foil> foil;
   public final ThemeTint foilTint;
   public final SliderSetting cards;
   public final ModeSetting<CardSpreadStyle.Spread> spread;
   public final ModeSetting<Cards.Design> design;
   public final ModeSetting<Cards.Symbols> symbols;
   public final BooleanSetting flip;
   private final Cards.Card[] deck = new Cards.Card[16];

   public CardSpreadStyle() {
      super(
         LandingStyle.CARD_SPREAD,
         1.7F,
         2.2F,
         new ThemeTint()
      );
      this.symbolColor = ThemeTint.detail(-1185802);
      this.foil = this.setting(
         new ModeSetting<>(
               "cardspread.material", "Foil", "The borders' metal leaf: Gold, Silver, Rose gold, or Custom (the Foil Tint).", Cards.Foil.class, Cards.Foil.GOLD
            )
            .style(ModeSetting.Style.CHIPS)
            .keywords(new String[]{"foil", "gold", "silver", "rose gold", "material", "metal"})
      );
      this.foilTint = ThemeTint.detail(-1851025);
      this.cards = this.look(this.setting(new SliderSetting("cardspread.cards", "Cards", "How many cards are dealt.", 4.0, 12.0, 8.0, 1.0)));
      this.spread = this.setting(
         new ModeSetting<>("cardspread.spread", "Spread", "How the cards are laid out.", CardSpreadStyle.Spread.class, CardSpreadStyle.Spread.CIRCLE)
            .style(ModeSetting.Style.CHIPS)
      );
      this.design = this.setting(
         new ModeSetting<>(
               "cardspread.design", "Card Design", "The frames on the faces and the pattern on the backs.", Cards.Design.class, Cards.Design.CELESTIAL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.symbols = this.setting(
         new ModeSetting<>("cardspread.symbolSet", "Symbols", "Which symbols the faces show.", Cards.Symbols.class, Cards.Symbols.SKY)
            .style(ModeSetting.Style.CHIPS)
      );
      this.flip = this.setting(new BooleanSetting("cardspread.flip", "Flip", "The cards turn face up in a wave round the spread.", true));

      for (int k = 0; k < 16; k++) {
         this.deck[k] = new Cards.Card();
      }
   }

   @Override
   public int passes(LandingFx fx) {
      return FxPass.SOLID.bit() | (fx.lod ? 0 : FxPass.GLOW.bit());
   }

   @Override
   public void start(LandingFx fx) {
      CardSpreadStyle.State s = new CardSpreadStyle.State();
      s.jump = fx.land.jump;
      s.half = 0.17 * Math.sqrt(fx.size) * (s.jump ? 0.8 : 1.0);
      int n = this.cards.getInt() + (s.jump ? 0 : Math.round(Math.max(0.0F, fx.land.fallScale - 1.0F) * 4.0F));
      if (fx.lod) {
         n = Math.max(4, n / 2);
      }

      s.n = Math.max(2, Math.min(16, n));
      double r = 1.15 * fx.size * (s.jump ? 0.55 : 1.0);
      double yaw = Math.toRadians(fx.land.yaw);
      double fwdX = -Math.sin(yaw);
      double fwdZ = Math.cos(yaw);
      double facing = Math.atan2(fwdZ, fwdX);
      double turn = fx.rand(1) * (Math.PI * 2);
      int[] set = this.symbols.get().set;
      CardSpreadStyle.Spread sp = this.spread.get();

      for (int k = 0; k < s.n; k++) {
         double dist;
         double ang;
         if (sp == CardSpreadStyle.Spread.ARC) {
            ang = facing - (Math.PI / 2) + Math.PI * (k + 0.5) / s.n;
            dist = r;
         } else if (sp == CardSpreadStyle.Spread.CROSS) {
            int arm = k % 4;
            int step = k / 4;
            int perArm = (s.n + 3) / 4;
            ang = facing + arm * Math.PI / 2.0;
            dist = r * (0.55 + 0.75 * step / Math.max(1, perArm - 1 + (perArm == 1 ? 1 : 0)));
         } else {
            ang = turn + (Math.PI * 2) * k / s.n;
            dist = r;
         }

         s.ax[k] = Math.cos(ang);
         s.az[k] = Math.sin(ang);
         s.px[k] = s.ax[k] * dist;
         s.pz[k] = s.az[k] * dist;
         s.delay[k] = (s.jump ? 0.05F : 0.09F) * k / Math.max(1, s.n - 1);
         s.spinIn[k] = fx.srand(20 + k) * 1.6F;
         s.symbol[k] = set[k % set.length];
      }

      fx.data = s;
   }

   private static float slid(LandingFx fx, CardSpreadStyle.State s, int k) {
      float t = fx.styleTime() - s.delay[k];
      float out = Easing.outCubic(Easing.clamp01(t / (s.jump ? 0.25F : 0.21000001F)));
      if (s.jump) {
         out *= 1.0F - Easing.inCubic(Easing.clamp01((fx.styleTime() - 0.25F - 0.08F) / 0.27000004F));
      }

      return out;
   }

   private float flipped(LandingFx fx, CardSpreadStyle.State s, int k) {
      if (!s.jump && this.flip.get()) {
         float at = 0.3F + 0.34F * k / Math.max(1, s.n - 1);
         return Easing.inOutCubic(Easing.clamp01((fx.styleTime() - at) / 0.16F));
      } else {
         return 0.0F;
      }
   }

   private static float sunk(LandingFx fx, CardSpreadStyle.State s) {
      return s.jump ? 0.0F : Easing.clamp01((fx.styleTime() - 1.1F) / 0.6F);
   }

   private int layout(LandingFx fx, FxFrame f, CardSpreadStyle.State s, Camera cam) {
      float sink = sunk(fx, s);

      for (int k = 0; k < s.n; k++) {
         Cards.Card c = this.deck[k];
         float out = slid(fx, s, k);
         if (out <= 0.0F) {
            c.half = 0.0;
         } else {
            double x = fx.x() + s.px[k] * out;
            double z = fx.z() + s.pz[k] * out;
            double spin = s.spinIn[k] * (1.0 - out);
            double ux0 = s.ax[k] * Math.cos(spin) - s.az[k] * Math.sin(spin);
            double uz0;
            double sx = uz0 = s.ax[k] * Math.sin(spin) + s.az[k] * Math.cos(spin);
            double sz = -ux0;
            double phi = Math.PI * this.flipped(fx, s, k);
            double cp = Math.cos(phi);
            double sp = Math.sin(phi);
            c.nx = sx * sp;
            c.ny = -cp;
            c.nz = sz * sp;
            c.ux = ux0;
            c.uy = 0.0;
            c.uz = uz0;
            c.rx = c.uy * c.nz - c.uz * c.ny;
            c.ry = c.uz * c.nx - c.ux * c.nz;
            c.rz = c.ux * c.ny - c.uy * c.nx;
            double ground = Ground.y(fx.ground, x, z);
            boolean on = Ground.lastValid();
            double lift = s.half * 1.05 * sp + s.half * 0.05;
            double down = Easing.inCubic(sink) * s.half * 0.6;
            c.x = f.rx(x);
            c.y = f.ry(ground + lift - down);
            c.z = f.rz(z);
            c.half = s.half * (0.55 + 0.45 * out);
            c.alpha = fx.alpha() * (on ? 1.0F : 0.0F) * (1.0F - Easing.smoothstep(0.35F, 1.0F, sink)) * NearCamera.fade(cam, c.x, c.y, c.z);
            c.symbol = s.symbol[k];
            c.uncut();
         }
      }

      return s.n;
   }

   @Override
   public void renderPass(LandingFx fx, FxFrame f, FxPass pass) {
      CardSpreadStyle.State s = (CardSpreadStyle.State)fx.data;
      if (s != null) {
         Camera cam = Vfx.camera();
         int n = this.layout(fx, f, s, cam);
         if (pass == FxPass.GLOW) {
            this.glow(fx, f, s, n);
         } else {
            MeshBatch.begin(Material.CUSTOM, Finish.SATIN, fx.light, 0.14F, -1, true, true);
            int body = this.color.getColor(0.5F);

            for (int k = 0; k < n; k++) {
               Cards.body(this.deck[k], Cards.withAlpha(body, this.deck[k].alpha));
            }

            MeshBatch.end();
            Cards.Foil fl = this.foil.get();
            MeshBatch.begin(fl.material, Finish.METALLIC, fx.light, 0.14F, -1, true, true);
            int tint = fl == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : -1;

            for (int k = 0; k < n; k++) {
               double sweep = (fx.styleTime() - (0.3F + 0.34F * k / Math.max(1, s.n - 1))) * 6.0 - 0.4;
               Cards.foil(this.deck[k], Cards.withAlpha(tint, this.deck[k].alpha), sweep, true);
            }

            MeshBatch.end();
            int ink = ColorUtil.shade(this.symbolColor.getColor(0.5F), Math.max(0.45F, fx.light)) | 0xFF000000;
            RenderState.decal(true);
            RenderState.translucent(false);

            try {
               Cards.art(this.deck, n, this.design.get().ordinal(), ink, 0.9F);
            } finally {
               RenderState.decal(false);
               RenderState.translucent(true);
            }

            this.dust(fx, s);
         }
      }
   }

   private void dust(LandingFx fx, CardSpreadStyle.State s) {
      if (!s.dusted && !s.jump && !fx.lod && !(sunk(fx, s) <= 0.0F)) {
         s.dusted = true;
         int col = this.foil.get() == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : this.foil.get().material.swatch();
         int per = Math.max(2, fx.count(5.0F));

         for (int k = 0; k < s.n; k++) {
            for (int i = 0; i < per; i++) {
               double x = fx.x() + s.px[k] + (fx.rand(300 + k * 13 + i) - 0.5) * s.half * 2.0;
               double z = fx.z() + s.pz[k] + (fx.rand(400 + k * 13 + i) - 0.5) * s.half * 2.0;
               double y = Ground.y(fx.ground, x, z) + 0.05;
               fx.particles
                  .spawn(
                     i % 3 == 0 ? 5 : 1,
                     x,
                     y,
                     z,
                     (fx.rand(500 + k * 13 + i) - 0.5F) * 0.3F,
                     0.5F + fx.rand(600 + k * 13 + i) * 0.7F,
                     (fx.rand(700 + k * 13 + i) - 0.5F) * 0.3F,
                     fx.seconds(0.7F + fx.rand(800 + i) * 0.4F),
                     (0.035F + fx.rand(900 + i) * 0.03F) * (float)Math.sqrt(fx.size)
                  )
                  .drag(1.5F)
                  .color(col)
                  .alpha(fx.alpha())
                  .end(0.01F)
                  .wind(0.3F);
            }
         }
      }
   }

   private void glow(LandingFx fx, FxFrame f, CardSpreadStyle.State s, int n) {
      int col = this.foil.get() == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : this.foil.get().material.swatch();

      for (int k = 0; k < n; k++) {
         Cards.Card c = this.deck[k];
         float fl = this.flipped(fx, s, k);
         float k2 = (float)Math.sin(Math.PI * fl) * c.alpha;
         if (!(k2 <= 0.01F) && !(c.half <= 1.0E-4)) {
            Vfx.sprite(Textures.GLOW, c.x, c.y + c.half * 0.3, c.z, c.half * 3.5, 0.0F, alpha(col, 0.35F * k2));
         }
      }
   }

   public static enum Spread implements Displayable {
      CIRCLE("Circle", "All round the feet"),
      ARC("Arc", "A half circle in front"),
      CROSS("Cross", "Four arms");

      private final String displayName;
      private final String description;

      private Spread(String displayName, String description) {
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

   private static final class State {
      int n;
      boolean jump;
      double half;
      final double[] px = new double[16];
      final double[] pz = new double[16];
      final double[] ax = new double[16];
      final double[] az = new double[16];
      final float[] delay = new float[16];
      final float[] spinIn = new float[16];
      final int[] symbol = new int[16];
      boolean dusted;

      private State() {
      }
   }
}
