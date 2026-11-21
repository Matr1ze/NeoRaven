package xyz.daikirai.render.furiah.module.hit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import xyz.daikirai.render.furiah.fx.FxFrame;
import xyz.daikirai.render.furiah.fx.Lifecycle;
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

public abstract class HitStyleBase implements SettingStyle {
   protected static final double TWO_PI = Math.PI * 2;
   protected final HitStyle type;
   public final ThemeTint color;
   private final float baseSeconds;
   private final List<Setting> settings = new ArrayList<>();
   private final List<SliderSetting> lookTargets = new ArrayList<>();

   protected HitStyleBase(HitStyle type, float baseSeconds, ThemeTint color) {
      this.type = type;
      this.baseSeconds = baseSeconds;
      this.color = color;
   }

   protected final <S extends Setting> S setting(S setting) {
      setting.group(this.type.displayName());
      setting.ownedBy(this);
      this.settings.add(setting);
      return setting;
   }

   protected final <S extends SliderSetting> S look(S slider) {
      this.lookTargets.add(slider);
      return slider;
   }

   public final List<SliderSetting> lookTargets() {
      return Collections.unmodifiableList(this.lookTargets);
   }

   protected final ModeSetting<Material> materialSetting(String key, Material made, String description) {
      ModeSetting<Material> m = this.setting(StyleMaterials.material(key, made, description));
      return m;
   }

   protected final ModeSetting<Finish> finishSetting(String key, Finish finish, ModeSetting<Material> material) {
      return this.setting(StyleMaterials.finish(key, finish, material));
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
      ArrayList<Setting> all = new ArrayList<>();
      all.addAll(this.settings);
      return Collections.unmodifiableList(all);
   }

   public final HitStyle getType() {
      return this.type;
   }

   public final Lifecycle lifecycle() {
      return this.type.lifecycle();
   }

   public final float baseSeconds() {
      return this.baseSeconds;
   }

   public void start(HitFx fx) {
   }

   public void finish(HitFx fx) {
   }

   public void render(HitFx fx, FxFrame f) {
   }

   public int passes(HitFx fx) {
      return 0;
   }

   public void renderPass(HitFx fx, FxFrame f, FxPass pass) {
   }

   public String stateForTest(HitFx fx) {
      return null;
   }

   protected static double scale(HitFx fx) {
      return fx.size * Math.max(0.5, Math.min(2.5, fx.hit.height / 1.8));
   }

   protected static int alpha(int color, float alpha) {
      return ColorUtil.mulAlpha(color, alpha);
   }

   protected static int core(int color, float alpha) {
      return ColorUtil.mulAlpha(ColorUtil.core(color), alpha);
   }

   protected static float loop(float p) {
      p -= (float)Math.floor(p);
      return 1.0F - Math.abs(2.0F * p - 1.0F);
   }
}
