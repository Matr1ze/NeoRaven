package xyz.daikirai.render.furiah.module.hit.styles;

import xyz.daikirai.render.furiah.fx.Basis;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Neon;
import xyz.daikirai.render.furiah.fx.Rng;
import xyz.daikirai.render.furiah.module.hit.HitFx;
import xyz.daikirai.render.furiah.module.hit.HitStyle;
import xyz.daikirai.render.furiah.module.hit.HitStyleBase;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.RenderState;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.Displayable;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class IconBurstStyle extends HitStyleBase {
   public final ModeSetting<IconBurstStyle.Icon> icon;
   public final SliderSetting count;
   private final Rng rng = new Rng();

   public IconBurstStyle() {
      super(HitStyle.ICON_BURST, 0.8F, new ThemeTint());
      this.icon = this.setting(new ModeSetting<>("icons.icon", "Icon", "Which icons burst out.", IconBurstStyle.Icon.class, IconBurstStyle.Icon.BROKEN_HEARTS));
      this.count = this.setting(new SliderSetting("icons.count", "Icons", "How many icons pop out.", 4.0, 14.0, 7.0, 1.0));
   }

   private int texture(IconBurstStyle.Icon kind, float r) {
      switch (kind) {
         case HEARTS:
            return Textures.HEART;
         case BROKEN_HEARTS:
            return Textures.BROKEN_HEART;
         case STARS:
            return Textures.STAR;
         case SPARKLES:
            return Textures.SPARKLE;
         case SKULLS:
            return Textures.SKULL;
         case PETALS:
            return Textures.PETAL;
         default:
            int[] all = new int[]{Textures.HEART, Textures.BROKEN_HEART, Textures.STAR, Textures.SPARKLE, Textures.SKULL, Textures.PETAL};
            return all[Math.min(all.length - 1, (int)(r * all.length))];
      }
   }

   @Override
   public void start(HitFx fx) {
      Basis b = fx.basis;
      this.rng.seed(fx.seed);
      int n = fx.count(this.count.getFloat() * (fx.lod ? 0.5F : 1.0F), true);

      for (int i = 0; i < n; i++) {
         double theta = Math.toRadians(this.rng.range(25.0F, 62.0F));
         double cosT = Math.cos(theta);
         double sinT = Math.sin(theta);
         double phi = this.rng.next() * Math.PI * 2.0;
         double dx = b.fx * cosT + (b.rx * Math.cos(phi) + b.ux * Math.sin(phi)) * sinT;
         double dy = b.fy * cosT + (b.ry * Math.cos(phi) + b.uy * Math.sin(phi)) * sinT;
         double dz = b.fz * cosT + (b.rz * Math.cos(phi) + b.uz * Math.sin(phi)) * sinT;
         float speed = this.rng.range(1.6F, 2.8F) * (float)Math.sqrt(fx.size);
         float s = this.rng.range(0.3F, 0.42F) * fx.size * (fx.crit ? 1.3F : 1.0F);
         double ox = fx.ix() - b.fx * 0.15;
         double oy = fx.iy() - b.fy * 0.15;
         double oz = fx.iz() - b.fz * 0.15;
         fx.particles
            .spawn(4, ox, oy, oz, (float)dx * speed, (float)dy * speed + 1.3F, (float)dz * speed, fx.seconds(this.rng.range(0.7F, 0.85F)), s)
            .end(s * 0.8F)
            .gravity(-0.35F)
            .drag(2.4F)
            .spin(this.rng.signed() * 0.4F, this.rng.signed() * 1.3F)
            .texture(this.texture(this.icon.get(), this.rng.next()))
            .flags(1)
            .fade(0.0F, 0.6F)
            .color(this.color, this.rng.next())
            .alpha(fx.opacity)
            .follow(fx.anchor, 0.5F);
      }
   }

   @Override
   public void render(HitFx fx, FxFrame f) {
      float p = fx.progress();
      float flash = 1.0F - Easing.smoothstep(0.0F, 0.18F, p);
      if (!(flash <= 0.003F)) {
         float a = fx.alpha() * flash;
         double x = f.rx(fx.ix() - fx.basis.fx * 0.15);
         double y = f.ry(fx.iy() - fx.basis.fy * 0.15);
         double z = f.rz(fx.iz() - fx.basis.fz * 0.15);
         int c = this.color.getColor(0.5F);
         RenderState.translucent(false);
         Vfx.sprite(Textures.SPARKLE, x, y, z, 0.6 * fx.size * (1.3F - 0.3F * flash), 0.0F, alpha(c, 0.8F * a));
         RenderState.additive();
         Vfx.sprite(Textures.GLOW, x, y, z, 0.9 * fx.size, 0.0F, alpha(c, 0.4F * a));
         Vfx.sprite(Textures.SPARKLE, x, y, z, 0.35 * fx.size * (1.3F - 0.3F * flash), 0.0F, alpha(Neon.tint(c), a));
      }
   }

   public static enum Icon implements Displayable {
      HEARTS("Hearts", "Hearts"),
      BROKEN_HEARTS("Broken Hearts", "Hearts split in two"),
      STARS("Stars", "Five-point stars"),
      SPARKLES("Sparkles", "Four-point sparkles"),
      SKULLS("Skulls", "Skulls"),
      PETALS("Petals", "Blossom petals"),
      MIXED("Mixed", "A bit of everything");

      private final String displayName;
      private final String description;

      private Icon(String displayName, String description) {
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
