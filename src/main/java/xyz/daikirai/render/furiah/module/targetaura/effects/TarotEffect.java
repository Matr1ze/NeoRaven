package xyz.daikirai.render.furiah.module.targetaura.effects;

import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.fx.kit.CardArt;
import xyz.daikirai.render.furiah.fx.kit.Cards;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
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

public final class TarotEffect extends AuraEffect {
   private static final double FLIP = 0.7;
   private static final float RIM = 0.14F;
   public final ThemeTint symbolColor;
   public final ThemeTint foilTint;
   public final ModeSetting<Cards.Foil> foil;
   public final SliderSetting cards;
   public final ModeSetting<TarotEffect.Arrangement> arrangement;
   public final ModeSetting<Cards.Design> design;
   public final ModeSetting<Cards.Symbols> symbols;
   public final BooleanSetting flip;
   public final SliderSetting flipEvery;
   public final BooleanSetting glint;
   private static final int MAX_CARDS = 5;
   private final Cards.Card[] cardsNow = new Cards.Card[5];
   private final float[] cardAlpha = new float[5];
   private int count;
   private double deckX;
   private double deckY;
   private double deckZ;
   private double puff;

   public TarotEffect() {
      super(
         EffectType.TAROT,
         "tarot",
         new ThemeTint(),
         true
      );
      this.symbolColor = ThemeTint.detail(-1185802);
      this.foil = this.setting(
         new ModeSetting<>(
               "tarot.material", "Foil", "The borders' metal leaf: Gold, Silver, Rose gold, or Custom (the Foil Tint).", Cards.Foil.class, Cards.Foil.GOLD
            )
            .style(ModeSetting.Style.CHIPS)
            .keywords(new String[]{"foil", "gold", "silver", "rose gold", "material", "metal"})
      );
      this.foilTint = ThemeTint.detail(-1851025);
      this.cards = this.look(this.setting(new SliderSetting("tarot.cards", "Cards", "How many cards float round the target.", 3.0, 5.0, 3.0, 1.0)));
      this.arrangement = this.setting(
         new ModeSetting<>(
            "tarot.arrangement",
            "Arrangement",
            "An arc round the chest, all the way round, or a small crown of cards above the head.",
            TarotEffect.Arrangement.class,
            TarotEffect.Arrangement.ARC
         )
      );
      this.design = this.setting(
         new ModeSetting<>("tarot.design", "Card Design", "The frames on the faces and the pattern on the backs.", Cards.Design.class, Cards.Design.CELESTIAL)
            .style(ModeSetting.Style.CHIPS)
      );
      this.symbols = this.setting(
         new ModeSetting<>("tarot.symbolSet", "Symbols", "Which symbols the faces show.", Cards.Symbols.class, Cards.Symbols.SKY)
            .style(ModeSetting.Style.CHIPS)
      );
      this.flip = this.setting(new BooleanSetting("tarot.flip", "Flip", "Now and then a card flips over to show a new symbol; off: never.", true));
      this.flipEvery = this.look(
         this.setting(
            new SliderSetting("tarot.flipEvery", "Flip Every", "Seconds between flips.", 3.0, 15.0, 5.0, 1.0).unit("s").visibleWhen(new BooleanSupplier() {
               @Override
               public boolean getAsBoolean() {
                  return TarotEffect.this.flip.get();
               }
            })
         )
      );
      this.glint = this.setting(new BooleanSetting("tarot.glint", "Glint", "A glint sweeps over the foil now and then.", true));

      for (int k = 0; k < 5; k++) {
         this.cardsNow[k] = new Cards.Card();
      }
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.SOLID) {
         float alpha = this.intensity(c);
         if (alpha <= 0.003F) {
            return;
         }

         this.layout(c);
         if (this.count == 0) {
            return;
         }

         this.drawBodies(c);
         this.drawFoil(c);
         this.drawArt(c);
      } else if (pass == FxPass.GLOW) {
         this.layout(c);
         if (this.puff > 0.0) {
            this.drawPuff(c);
         }
      }
   }

   private void layout(EffectContext c) {
      double s = this.size(c);
      double t = c.time;
      double spd = this.speed();
      int n = this.cards.getInt();
      TarotEffect.Arrangement arr = this.arrangement.get();
      boolean crown = arr == TarotEffect.Arrangement.CROWN;
      double scale = crown ? 0.55 : 1.0;
      double cardHalf = 0.15 * s * scale;
      double radius = crown ? (0.3 + 0.03 * n) * s : 0.8 * (0.8 + 0.2 * s);
      double cy = crown ? c.y + c.nameTop + 0.1 + cardHalf * 1.5 + 0.05 : c.y + c.height * 0.64;
      this.deckX = c.x;
      this.deckY = c.y + c.nameTop + 0.32;
      this.deckZ = c.z;
      float out = c.outro;
      float fan = Easing.outCubic(Easing.clamp01(t / 0.3));
      float gather = Easing.inOutCubic(Easing.clamp01(out / 0.55F));
      float vanish = Easing.smoothstep(0.55F, 0.85F, out);
      this.puff = out > 0.75F ? Easing.clamp01((out - 0.75F) / 0.25F) : 0.0;
      double deckSpin = Easing.inCubic(vanish) * Math.PI * 6.0;
      double turnRate = Math.toRadians(arr == TarotEffect.Arrangement.ARC ? 8.0 : (arr == TarotEffect.Arrangement.ORBIT ? 12.0 : 10.0)) * spd;
      double base = t * turnRate;
      double spread = n <= 3 ? Math.toRadians(48.0) : (n == 4 ? Math.toRadians(40.0) : Math.toRadians(32.0));
      this.count = n;
      Cards.Symbols set = this.symbols.get();

      for (int k = 0; k < n; k++) {
         double a = arr == TarotEffect.Arrangement.ARC ? base + (k - (n - 1) / 2.0) * spread : base + (Math.PI * 2) * k / n;
         double ca = Math.cos(a);
         double sa = Math.sin(a);
         double bob = 0.035 * s * Math.sin(t * 1.6 * spd + k * 1.9);
         double x = c.x + ca * radius;
         double y = cy + bob;
         double z = c.z + sa * radius;
         double lean = crown ? Math.toRadians(18.0) : Math.toRadians(5.0);
         double outx = ca * Math.cos(lean);
         double outy = -Math.sin(lean);
         double outz = sa * Math.cos(lean);
         double rgx = -sa;
         double rgy = 0.0;
         double upx = outy * ca - outz * rgy;
         double upy = outz * rgx - outx * ca;
         double upz = outx * rgy - outy * rgx;
         if (upy < 0.0) {
            upx = -upx;
            upy = -upy;
            upz = -upz;
         }

         double roll = Math.toRadians(4.0) * Math.sin(t * 0.9 * spd + k * 2.3);
         double cr = Math.cos(roll);
         double sr = Math.sin(roll);
         double r1x = rgx * cr + upx * sr;
         double r1y = rgy * cr + upy * sr;
         double r1z = ca * cr + upz * sr;
         double u1x = upx * cr - rgx * sr;
         double u1y = upy * cr - rgy * sr;
         double u1z = upz * cr - ca * sr;
         double turn = 0.0;
         double introFlip = Easing.inOutCubic(Easing.clamp01((t - 0.2 - 0.07 * k) / 0.22));
         turn += Math.PI * (1.0 - introFlip);
         int flips = 0;
         if (this.flip.get() && t > 1.0) {
            double every = this.flipEvery.getFloat() / spd;
            long started = (long)Math.floor(t / every);
            long passed = (long)Math.floor((t - 0.35) / every);
            if (passed >= 1L && passed - 1L >= k) {
               flips = (int)((passed - 1L - k) / n + 1L);
            }

            double f;
            if (started >= 1L && (started - 1L) % n == k && (f = (t - started * every) / 0.7) < 1.0) {
               turn += (Math.PI * 2) * Easing.inOutCubic((float)f);
            }
         }

         double var127;
         double ct = Math.cos(var127 = turn + Math.PI * gather);
         double st = Math.sin(var127);
         double fx = r1x * ct + outx * st;
         double fy = r1y * ct + outy * st;
         double fz = r1z * ct + outz * st;
         double gx = outx * ct - r1x * st;
         double gy = outy * ct - r1y * st;
         double gz = outz * ct - r1z * st;
         double from = 1.0 - fan * (1.0 - gather);
         double qx = x + (this.deckX - x) * from;
         double qy = y + (this.deckY - y) * from;
         double qz = z + (this.deckZ - z) * from;
         if (vanish > 0.0F) {
            double cs = Math.cos(deckSpin);
            double ss = Math.sin(deckSpin);
            double tx2 = fx * cs - gx * ss;
            double tz2 = fz * cs - gz * ss;
            double gx2 = gx * cs + fx * ss;
            double gz2 = gz * cs + fz * ss;
            fx = tx2;
            fz = tz2;
            gx = gx2;
            gz = gz2;
         }

         Cards.Card card = this.cardsNow[k];
         double var132;
         card.x = var132 = qx + gx * 0.004 * k * from;
         card.y = qy;
         double var133;
         card.z = var133 = qz + gz * 0.004 * k * from;
         card.rx = fx;
         card.ry = fy;
         card.rz = fz;
         card.ux = u1x;
         card.uy = u1y;
         card.uz = u1z;
         card.nx = gx;
         card.ny = gy;
         card.nz = gz;
         card.half = cardHalf * (0.7 + 0.3 * fan) * (1.0 - 0.3 * gather) * (1.0 - vanish);
         this.cardAlpha[k] = c.keep.solid(var132, qy, var133);
         int[] symbolsOf = set.set;
         int step = symbolsOf.length > n ? n : 1;
         card.symbol = symbolsOf[(k + flips * step) % symbolsOf.length];
      }
   }

   private void drawBodies(EffectContext c) {
      float alpha = this.intensity(c);
      MeshBatch.begin(Material.CUSTOM, Finish.SATIN, c.light, 0.14F, -1, true, true);
      int body = this.color(c, 0.5F);

      for (int k = 0; k < this.count; k++) {
         Cards.body(this.cardsNow[k], withAlpha(body, alpha * this.cardAlpha[k]));
      }

      MeshBatch.end();
   }

   private void drawFoil(EffectContext c) {
      float alpha = this.intensity(c);
      Cards.Foil f = this.foil.get();
      boolean custom = f == Cards.Foil.CUSTOM;
      MeshBatch.begin(f.material, Finish.METALLIC, c.light, 0.14F, -1, true, true);
      int tint = custom ? this.foilTint.getColor(0.5F) : -1;
      boolean shine = this.glint.get();

      for (int k = 0; k < this.count; k++) {
         double sweep = (c.time * 0.45 + k * 0.37) % 1.8 - 0.4;
         Cards.foil(this.cardsNow[k], withAlpha(tint, alpha * this.cardAlpha[k]), sweep, shine);
      }

      MeshBatch.end();
   }

   private void drawArt(EffectContext c) {
      float alpha = this.intensity(c);
      int ink = this.symbolColor.getColor(0.5F);
      ink = ColorUtil.shade(ink, c.light);

      for (int k = 0; k < this.count; k++) {
         this.cardsNow[k].alpha = alpha * this.cardAlpha[k];
      }

      RenderState.decal(true);

      try {
         Cards.art(this.cardsNow, this.count, this.design.get().ordinal(), ink, 0.9F);
      } finally {
         RenderState.decal(false);
      }
   }

   private void drawPuff(EffectContext c) {
      float a = this.intensity.getFloat() / 100.0F * (float)(1.0 - this.puff) * Easing.smoothstep(0.0F, 0.15F, (float)this.puff * 4.0F);
      int light = ColorUtil.lerp(this.symbolColor.getColor(0.5F), this.foil.get() == Cards.Foil.CUSTOM ? this.foilTint.getColor(0.5F) : -6488, 0.5F);
      float size = (float)(0.2 + 0.5 * this.puff) * this.size(c);
      Vfx.sprite(Textures.GLOW, this.deckX, this.deckY, this.deckZ, size, 0.0F, alpha(light, 0.7F * a));
      Vfx.sprite(Textures.SPARK, this.deckX, this.deckY, this.deckZ, size * 0.4F, 0.0F, core(light, a));

      for (int i = 0; i < 6; i++) {
         double ang = i * (Math.PI * 2) / 6.0 + 0.3;
         double r = 0.08 + 0.35 * this.puff;
         Vfx.sprite(
            Textures.SPARK,
            this.deckX + Math.cos(ang) * r,
            this.deckY + Math.sin(ang * 2.0) * 0.05 + 0.1 * this.puff,
            this.deckZ + Math.sin(ang) * r,
            0.06F,
            0.0F,
            core(light, a * 0.8F)
         );
      }
   }

   private static int withAlpha(int color, float a) {
      return Cards.withAlpha(color, a);
   }

   public String stateForTest() {
      StringBuilder sb = new StringBuilder("art ").append(CardArt.stateForTest()).append(", symbols");

      for (int k = 0; k < this.count; k++) {
         sb.append(' ').append(this.cardsNow[k].symbol);
      }

      return sb.toString();
   }

   public static void paintArtForTest() {
      CardArt.paintAllForTest();
   }

   public static enum Arrangement implements Displayable {
      ARC("Arc", "An arc round the chest"),
      ORBIT("Orbit", "All the way round"),
      CROWN("Crown above head", "A small ring above the head");

      private final String displayName;
      private final String description;

      private Arrangement(String displayName, String description) {
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
