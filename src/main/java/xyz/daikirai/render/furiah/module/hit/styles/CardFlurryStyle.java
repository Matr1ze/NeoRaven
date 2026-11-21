package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.kit.Glitch;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.kit.Cards;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Camera;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class CardFlurryStyle extends HitStyleBase {
   private static final float FAN = 0.16F;
   private static final float EXIT = 0.6666667F;
   private static final int MAX_CARDS = 7;
   private static final float RIM = 0.14F;
   private static final int EMBER = -15809;
   public final ThemeTint symbolColor;
   public final ModeSetting<Cards.Foil> foil;
   public final ThemeTint foilTint;
   public final SliderSetting cards;
   public final ModeSetting<Cards.Design> design;
   public final ModeSetting<Cards.Symbols> symbols;
   public final SliderSetting spin;
   public final ModeSetting<CardFlurryStyle.Exit> exit;
   private final Cards.Card[] deck = new Cards.Card[7];
   private int count;

   public CardFlurryStyle() {
      super(
         HitStyle.CARD_FLURRY,
         0.75F,
         new ThemeTint()
      );
      this.symbolColor = ThemeTint.detail(-1185802);
      this.foil = this.setting(
         new ModeSetting<>(
               "cardflurry.material", "Foil", "The borders' metal leaf: Gold, Silver, Rose gold, or Custom (the Foil Tint).", Cards.Foil.class, Cards.Foil.GOLD
            )
            .style(ModeSetting.Style.CHIPS)
            .keywords(new String[]{"foil", "gold", "silver", "rose gold", "material", "metal"})
      );
      this.foilTint = ThemeTint.detail(-1851025);
      this.cards = this.look(this.setting(new SliderSetting("cardflurry.cards", "Cards", "How many cards fan out.", 2.0, 6.0, 3.0, 1.0)));
      this.design = this.setting(
         new ModeSetting<>(
               "cardflurry.design", "Card Design", "The frames on the faces and the pattern on the backs.", Cards.Design.class, Cards.Design.CELESTIAL
            )
            .style(ModeSetting.Style.CHIPS)
      );
      this.symbols = this.setting(
         new ModeSetting<>("cardflurry.symbolSet", "Symbols", "Which symbols the faces show.", Cards.Symbols.class, Cards.Symbols.SKY)
            .style(ModeSetting.Style.CHIPS)
      );
      this.spin = this.setting(new SliderSetting("cardflurry.spin", "Spin", "How fast the cards spin and flip as they fly.", 0.0, 100.0, 60.0, 5.0).unit("%"));
      this.exit = this.setting(
         new ModeSetting<>(
               "cardflurry.exit",
               "Exit",
               "How the cards go: burning away from their edges, in sparkles, or fading.",
               CardFlurryStyle.Exit.class,
               CardFlurryStyle.Exit.BURN
            )
            .style(ModeSetting.Style.CHIPS)
      );

      for (int k = 0; k < 7; k++) {
         this.deck[k] = new Cards.Card();
      }
   }

   @Override
   public int passes(HitFx fx) {
      return FxPass.SOLID.bit() | FxPass.GLOW.bit();
   }

   private static float exitOf(float p) {
      return Easing.clamp01((p - 0.6666667F) / 0.3333333F);
   }

   private void layout(HitFx fx, FxFrame f, float p) {
      Basis b = fx.basis;
      double s = scale(fx);
      int n = this.cards.getInt();
      double half = 0.12 * s;
      double ox = fx.ix() - b.fx * 0.15;
      double oy = fx.iy() - b.fy * 0.15;
      double oz = fx.iz() - b.fz * 0.15;
      double reach = half * 3.2;
      double px = ox - b.ux * reach;
      double py = oy - b.uy * reach;
      double pz = oz - b.uz * reach;
      double spread = Math.toRadians(n <= 3 ? 24.0 : (n == 4 ? 18.0 : (n == 5 ? 15.0 : 13.0)));
      float fan = Easing.outBack(Easing.clamp01(p / 0.16F), 1.6F);
      double tau = Math.max(0.0, (double)((p - 0.16F) * this.baseSeconds()));
      double spinAmount = this.spin.getFloat() / 100.0;
      int[] set = this.symbols.get().set;
      float e = exitOf(p);
      CardFlurryStyle.Exit how = this.exit.get();
      this.count = 0;
      int total = fx.crit ? n + 1 : n;

      for (int k = 0; k < total; k++) {
         boolean big = fx.crit && k == n;
         int id = fx.seed + k * 37;
         double phi = big ? 0.0 : (k - (n - 1) * 0.5) * spread;
         double a = phi * fan;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double upx = b.ux * ca + b.rx * sa;
         double upy = b.uy * ca + b.ry * sa;
         double upz = b.uz * ca + b.rz * sa;
         double acx = b.rx * ca - b.ux * sa;
         double acy = b.ry * ca - b.uy * sa;
         double acz = b.rz * ca - b.uz * sa;
         double cx = px + upx * reach;
         double cy = py + upy * reach;
         double cz = pz + upz * reach;
         double along = big ? 1.6 : 0.9 + 0.5 * hash(id, 1);
         double out = big ? 1.5 : 1.7 + 0.6 * hash(id, 2);
         double travel = (1.0 - Math.exp(-2.4 * tau)) / 2.4;
         double wide = phi * 3.0;
         double fdx = Math.cos(wide) * b.ux + Math.sin(wide) * b.rx;
         double fdy = Math.cos(wide) * b.uy + Math.sin(wide) * b.ry;
         double fdz = Math.cos(wide) * b.uz + Math.sin(wide) * b.rz;
         cx += (b.fx * along + fdx * out) * travel;
         cy += (b.fy * along + fdy * out) * travel - 0.6 * tau * tau;
         cz += (b.fz * along + fdz * out) * travel;
         double psi = big ? 0.0 : (hash(id, 3) < 0.5 ? -1 : 1) * spinAmount * (5.0 + 4.0 * hash(id, 4)) * tau;
         double theta = big ? 0.0 : spinAmount * Math.PI * (2.0 + 1.5 * hash(id, 5)) * tau;
         double cps = Math.cos(psi);
         double sps = Math.sin(psi);
         double rx = acx * cps + upx * sps;
         double ry = acy * cps + upy * sps;
         double rz = acz * cps + upz * sps;
         double ux = upx * cps - acx * sps;
         double uy = upy * cps - acy * sps;
         double uz = upz * cps - acz * sps;
         double nx0 = -b.fx;
         double ny0 = -b.fy;
         double nz0 = -b.fz;
         double ct = Math.cos(theta);
         double st = Math.sin(theta);
         double fx2 = rx * ct + nx0 * st;
         double fy2 = ry * ct + ny0 * st;
         double fz2 = rz * ct + nz0 * st;
         double nx = nx0 * ct - rx * st;
         double ny = ny0 * ct - ry * st;
         double nz = nz0 * ct - rz * st;
         Cards.Card c = this.deck[this.count++];
         c.x = f.rx(cx);
         c.y = f.ry(cy);
         c.z = f.rz(cz);
         c.rx = fx2;
         c.ry = fy2;
         c.rz = fz2;
         c.ux = ux;
         c.uy = uy;
         c.uz = uz;
         c.nx = nx;
         c.ny = ny;
         c.nz = nz;
         double size = half * (big ? 1.5 : 1.0) * (0.55 + 0.45 * Easing.clamp01(p / 0.16F));
         if (how == CardFlurryStyle.Exit.SPARKLE && e > 0.0F) {
            size *= Math.pow(1.0 - e, 0.7);
         }

         c.half = size;
         c.symbol = big ? 3 : set[k % set.length];
         float a8 = fx.alpha() * fx.keep.cover(c.x, c.y, c.z);
         if (how == CardFlurryStyle.Exit.FADE || how == CardFlurryStyle.Exit.BURN && !Cards.burnWorks()) {
            a8 *= 1.0F - e;
         }

         c.alpha = a8;
      }
   }

   @Override
   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
      float p = fx.progress();
      if (!(fx.alpha() <= 0.003F)) {
         this.layout(fx, f, p);
         Camera cam = Vfx.camera();
         float e = exitOf(p);
         boolean burning = this.exit.get() == CardFlurryStyle.Exit.BURN && Cards.burnWorks() && e > 0.0F;
         if (pass == FxPass.SOLID) {
            if (burning) {
               for (int k = 0; k < this.count; k++) {
                  Cards.burnHoles(this.deck[k], e, k % 4, cam);
               }
            }

            MeshBatch.begin(Material.CUSTOM, Finish.SATIN, fx.light, 0.14F, -1, true, true);
            int body = this.color.getColor(0.5F);

            for (int k = 0; k < this.count; k++) {
               Cards.body(this.deck[k], Cards.withAlpha(body, this.deck[k].alpha));
            }

            MeshBatch.end();
            Cards.Foil fl = this.foil.get();
            MeshBatch.begin(fl.material, Finish.METALLIC, fx.light, 0.14F, -1, true, true);
            int tint = fl == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : -1;

            for (int k = 0; k < this.count; k++) {
               double sweep = (p * 2.2 + k * 0.37) % 1.8 - 0.4;
               Cards.foil(this.deck[k], Cards.withAlpha(tint, this.deck[k].alpha), sweep, true);
            }

            MeshBatch.end();
            int ink = ColorUtil.shade(this.symbolColor.getColor(0.5F), fx.light);
            RenderState.decal(true);
            RenderState.translucent(false);

            try {
               Cards.art(this.deck, this.count, this.design.get().ordinal(), ink, 0.9F);
            } finally {
               RenderState.decal(false);
               RenderState.translucent(true);
            }
         } else if (pass == FxPass.GLOW) {
            this.glow(fx, cam, p, e, burning);
         }
      }
   }

   private void glow(HitFx fx, Camera cam, float p, float e, boolean burning) {
      if (burning) {
         for (int k = 0; k < this.count; k++) {
            Cards.burnEdge(this.deck[k], e, 0.07F, k % 4, -15809, this.deck[k].alpha, cam);
         }
      } else if (e > 0.0F && this.exit.get() != CardFlurryStyle.Exit.FADE && !fx.lod) {
         int col = this.foil.get() == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : this.foil.get().material.swatch();

         for (int k = 0; k < this.count; k++) {
            Cards.Card c = this.deck[k];
            float k8 = (float)Math.sin(Math.PI * e) * fx.alpha();

            for (int i = 0; i < 5; i++) {
               double a = (i + k * 0.7) * 1.2566 + e * 3.0;
               double r = c.half * (1.2 + 0.8 * e);
               double x = c.x + (c.rx * Math.cos(a) + c.ux * Math.sin(a)) * r;
               double y = c.y + (c.ry * Math.cos(a) + c.uy * Math.sin(a)) * r;
               double z = c.z + (c.rz * Math.cos(a) + c.uz * Math.sin(a)) * r;
               Vfx.sprite(Textures.SPARK, x, y, z, c.half * 0.5, 0.0F, core(col, 0.8F * k8));
            }
         }
      }

      float flash;
      if (fx.crit && this.count > 0 && (flash = 1.0F - Easing.clamp01(Math.abs(p - 0.16F) / 0.08F)) > 0.0F && !Glitch.reduceFlashing()) {
         Cards.Card c = this.deck[this.count - 1];
         int col = this.foil.get() == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : this.foil.get().material.swatch();
         Vfx.sprite(Textures.FLARE, c.x, c.y, c.z, c.half * 4.0, 0.4F, core(col, 0.85F * flash * fx.alpha()));
         Vfx.sprite(Textures.GLOW, c.x, c.y, c.z, c.half * 5.0, 0.0F, alpha(col, 0.4F * flash * fx.alpha()));
      }
   }

   @Override
   public String stateForTest(HitFx fx) {
      float p = fx.progress();
      String phase = p < 0.16F ? "fanning out" : (p < 0.6666667F ? "flying" : this.exit.get().displayName().toLowerCase(Locale.ROOT));
      return String.format(
         Locale.ROOT,
         "%s: %d card(s)%s, %s foil, %s, %s, spin %d%%; exit %.2f, burn %s",
         phase,
         this.cards.getInt(),
         fx.crit ? " and the eye" : "",
         this.foil.get().displayName(),
         this.design.get().displayName(),
         this.symbols.get().displayName(),
         this.spin.getInt(),
         exitOf(p),
         Cards.burnWorks() ? "with holes" : "as a fade"
      );
   }

   private static double hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      return (h >>> 8 & 16777215) / 1.6777216E7;
   }

   public static enum Exit implements Displayable {
      BURN("Burn away", "Each card burns away from its edges"),
      SPARKLE("Sparkle out", "The cards shrink away in sparkles"),
      FADE("Fade", "The cards fade out");

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
