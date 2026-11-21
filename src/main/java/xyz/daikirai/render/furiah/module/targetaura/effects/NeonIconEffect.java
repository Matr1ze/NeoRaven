package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class NeonIconEffect extends AuraEffect {
   private static final int POWER_ON = 873;
   public final ModeSetting<NeonIconEffect.Symbol> symbol = this.setting(
      new ModeSetting<>("neon.symbol", "Symbol", "The neon sign.", NeonIconEffect.Symbol.class, NeonIconEffect.Symbol.DOLLAR)
   );
   public final ModeSetting<NeonIconEffect.Placement> placement = this.setting(
      new ModeSetting<>("neon.placement", "Position", "Where the sign hovers.", NeonIconEffect.Placement.class, NeonIconEffect.Placement.ABOVE)
   );
   public final SliderSetting flicker = this.setting(new SliderSetting("neon.flicker", "Flicker", "How much the tube flickers.", 0.0, 1.0, 0.4, 0.05));
   public final BooleanSetting footRing = this.setting(new BooleanSetting("neon.ring", "Foot Ring", "Thin neon ring around the feet.", true));

   public NeonIconEffect() {
      super(EffectType.NEON_ICON, "neon", new ThemeTint());
   }

   @Override
   public void render(EffectContext c) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float spd = this.speed();
      int index = this.symbol.get().ordinal();
      float grow = Easing.outBack(Easing.clamp01(c.intro / 0.7F), 1.6F);
      float lit = flicker(c, 0, this.flicker.getFloat()) * inten;
      double x = c.x;
      double z = c.z;
      double y;
      if (this.placement.get() == NeonIconEffect.Placement.ABOVE) {
         y = c.y + c.height + 0.62 + 0.42 * sz + 0.05 * sz * Math.sin(c.time * 1.8 * spd);
      } else {
         double dx = c.cam.posX - c.x;
         double dz = c.cam.posZ - c.z;
         double len = Math.sqrt(dx * dx + dz * dz);
         if (len > 1.0E-6) {
            double out = c.width * 0.5 + 0.35;
            x += dx / len * out;
            z += dz / len * out;
         }

         y = c.y + c.height * 0.55 + 0.03 * Math.sin(c.time * 1.8 * spd);
      }

      double quad = 0.62 * sz * grow / 0.64F;
      float sway = 0.05F * (float)Math.sin(c.time * 1.3 * spd);
      int col = this.color(c, 0.5F);
      if (grow > 0.001F && lit > 0.003F) {
         Vfx.sprite(Textures.GLOW, x, y, z, quad * 1.15, 0.0F, alpha(col, 0.2F * lit));
         Vfx.sprite(Textures.NEON_GLOW[index], x, y, z, quad, sway, alpha(col, 0.85F * lit));
         Vfx.sprite(Textures.NEON_BODY[index], x, y, z, quad, sway, alpha(col, 0.95F * lit));
         Vfx.sprite(Textures.NEON_CORE[index], x, y, z, quad, sway, core(col, 0.9F * lit));
      }

      if (this.footRing.get()) {
         float ringLit = flicker(c, 1, this.flicker.getFloat() * 0.6F) * inten * c.groundAlpha;
         double r = 0.62 * sz * grow;
         double gy = c.groundY + 0.02;
         if (ringLit > 0.003F && r > 0.001) {
            RenderState.decal(true);
            Vfx.flatRing(Textures.LINE_GLOW, c.x, gy, c.z, r, 0.24 * sz, 96, alpha(this.color(c, 0.0F), 0.55F * ringLit));
            Vfx.flatRing(Textures.LINE_CORE, c.x, gy, c.z, r, 0.035 * sz, 96, core(this.color(c, 0.0F), 0.95F * ringLit));
            RenderState.decal(false);
         }
      }
   }

   private static float flicker(EffectContext c, int channel, float amount) {
      double t = c.time;
      if (t < 0.45) {
         int step = (int)(t / 0.045);
         boolean on = (873 >> 9 - Math.min(9, step) & 1) == 1;
         return on ? 1.0F : 0.12F;
      } else if (amount <= 0.0F) {
         return 1.0F;
      } else {
         float hum = 0.93F + 0.07F * (float)Math.sin(t * 60.0 + channel * 2.1);
         int slot = (int)Math.floor(t * 11.0);
         float roll = hash(c.seed + channel * 7919, slot);
         float dip = roll > 1.0F - 0.14F * amount ? 0.2F + 0.5F * hash(c.seed + 31 + channel, slot) : 1.0F;
         return 1.0F - amount + amount * hum * dip;
      }
   }

   public static enum Placement implements Displayable {
      ABOVE("Above"),
      FRONT("In Front");

      private final String displayName;

      private Placement(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return "";
      }
   }

   public static enum Symbol implements Displayable {
      DOLLAR("$"),
      HEART("Heart"),
      SKULL("Skull"),
      CROSSHAIR("Crosshair"),
      STAR("Star");

      private final String displayName;

      private Symbol(String displayName) {
         this.displayName = displayName;
      }

      @Override
      public String displayName() {
         return this.displayName;
      }

      @Override
      public String description() {
         return "";
      }
   }
}
