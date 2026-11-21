package xyz.daikirai.render.furiah.module.targetaura;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.fx.style.StyleMaterials;
import xyz.daikirai.render.furiah.render.ColorUtil;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.Setting;
import xyz.daikirai.render.furiah.setting.SettingStyle;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public abstract class AuraEffect implements SettingStyle {
   protected static final double TWO_PI = Math.PI * 2;
   protected final EffectType type;
   public final SliderSetting size;
   public final SliderSetting speed;
   public final SliderSetting intensity;
   public final ThemeTint color;
   private final List<Setting> settings = new ArrayList<>();
   private final List<SliderSetting> lookTargets = new ArrayList<>();
   private final boolean percent;

   protected AuraEffect(EffectType type, String key, ThemeTint color) {
      this(type, key, color, false);
   }

   protected AuraEffect(EffectType type, String key, ThemeTint color, boolean percent) {
      this.type = type;
      this.percent = percent;
      if (percent) {
         this.size = this.setting(new SliderSetting(key + ".size", "Size", "Scales the whole effect.", 50.0, 150.0, 100.0, 5.0).unit("%"));
         this.speed = this.setting(new SliderSetting(key + ".speed", "Speed", "How fast it moves.", 50.0, 200.0, 100.0, 5.0).unit("%"));
         this.intensity = this.setting(new SliderSetting(key + ".intensity", "Intensity", "Brightness and opacity.", 10.0, 100.0, 100.0, 5.0).unit("%"));
      } else {
         this.size = this.setting(new SliderSetting(key + ".size", "Size", "Scales the whole effect.", 0.5, 2.0, 1.0, 0.05).unit("x"));
         this.speed = this.setting(new SliderSetting(key + ".speed", "Speed", "How fast the effect animates.", 0.25, 3.0, 1.0, 0.05).unit("x"));
         this.intensity = this.setting(new SliderSetting(key + ".intensity", "Intensity", "Brightness and opacity.", 0.1, 1.0, 0.85, 0.05));
      }

      this.color = color;
      this.lookTargets.add(this.size);
      this.lookTargets.add(this.intensity);
      this.lookTargets.add(this.speed);
   }

   protected final <S extends SliderSetting> S look(S slider) {
      this.lookTargets.add(slider);
      return slider;
   }

   public final List<SliderSetting> lookTargets() {
      return Collections.unmodifiableList(this.lookTargets);
   }

   protected final ModeSetting<Material> materialSetting(String key, Material defaultMaterial, String description) {
      ModeSetting<Material> m = this.setting(StyleMaterials.material(key, defaultMaterial, description));
      return m;
   }

   protected final ModeSetting<Finish> finishSetting(String key, Finish defaultFinish, ModeSetting<Material> material) {
      return this.setting(StyleMaterials.finish(key, defaultFinish, material));
   }

   protected final <S extends Setting> S setting(S setting) {
      setting.group(this.type.displayName());
      setting.ownedBy(this);
      this.settings.add(setting);
      return setting;
   }

   @Override
   public final String styleName() {
      return this.type.displayName();
   }

   @Override
   public final boolean isShown() {
      return true;
   }

   @Override
   public final boolean isActive() {
      return this.isShown();
   }

   @Override
   public final boolean isMultiple() {
      return false;
   }

   @Override
   public final void show() {
   }

   public final List<Setting> getSettings() {
      ArrayList<Setting> all = new ArrayList<>(this.settings);
      return Collections.unmodifiableList(all);
   }

   public final EffectType getType() {
      return this.type;
   }

   public void render(EffectContext c, FxPass pass) {
      if (pass == FxPass.GLOW) {
         this.render(c);
      }
   }

   public void render(EffectContext c) {
   }

   protected final float size(EffectContext c) {
      return (this.percent ? this.size.getFloat() / 100.0F : this.size.getFloat()) * c.scale;
   }

   protected final float sizeNoShrink() {
      return this.percent ? this.size.getFloat() / 100.0F : this.size.getFloat();
   }

   protected final float speed() {
      return this.percent ? this.speed.getFloat() / 100.0F : this.speed.getFloat();
   }

   protected final float intensity(EffectContext c) {
      return (this.percent ? this.intensity.getFloat() / 100.0F : this.intensity.getFloat()) * c.alpha;
   }

   protected final int color(EffectContext c, float position) {
      return this.color.getColor(position);
   }

   protected static float loop(float p) {
      p -= (float)Math.floor(p);
      return 1.0F - Math.abs(2.0F * p - 1.0F);
   }

   protected static int alpha(int color, float alpha) {
      return ColorUtil.mulAlpha(color, alpha);
   }

   protected static int core(int color, float alpha) {
      return ColorUtil.mulAlpha(ColorUtil.core(color), alpha);
   }

   protected static float hash(int a, int b) {
      int h = a * 668265261 ^ (b + 374761393) * -1640531535;
      h ^= h >>> 15;
      h *= -2048144789;
      h ^= h >>> 13;
      h *= -1028477387;
      h ^= h >>> 16;
      return (h >>> 8) / 1.6777216E7F;
   }
}
