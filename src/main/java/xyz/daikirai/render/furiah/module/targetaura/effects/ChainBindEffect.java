package xyz.daikirai.render.furiah.module.targetaura.effects;

import xyz.daikirai.render.furiah.fx.kit.Chains;
import xyz.daikirai.render.furiah.fx.pass.FxPass;
import xyz.daikirai.render.furiah.module.targetaura.AuraEffect;
import xyz.daikirai.render.furiah.module.targetaura.EffectContext;
import xyz.daikirai.render.furiah.module.targetaura.EffectType;
import xyz.daikirai.render.furiah.render.Easing;
import xyz.daikirai.render.furiah.render.Textures;
import xyz.daikirai.render.furiah.render.Vfx;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.render.material.MeshBatch;
import xyz.daikirai.render.furiah.setting.BooleanSetting;
import xyz.daikirai.render.furiah.setting.ThemeTint;
import xyz.daikirai.render.furiah.setting.ModeSetting;
import xyz.daikirai.render.furiah.setting.SliderSetting;

public final class ChainBindEffect extends AuraEffect {
   private static final double PITCH = 0.72;
   private static final double[] CHAIN_TILT = new double[]{0.5, -0.5, 0.1};
   private static final double[] CHAIN_AZIMUTH = new double[]{0.0, 0.0, 1.3};
   private static final double[] CHAIN_HEIGHT = new double[]{0.52, 0.52, 0.3};
   private static final double[] CHAIN_RADIUS = new double[]{1.0, 1.07, 0.96};
   private static final float RIM = 0.16F;
   public final SliderSetting chains = this.setting(new SliderSetting("chains.count", "Chains", "Number of chains.", 1.0, 3.0, 2.0, 1.0));
   public final BooleanSetting glint = this.setting(new BooleanSetting("chains.glint", "Glint", "A shine sweeping along the chains.", true));
   public final ModeSetting<Material> material = this.materialSetting(
      "chains", Material.CUSTOM, "What the chains are made of. Custom takes the chain color above, with a finish."
   );
   public final ModeSetting<Finish> finish = this.finishSetting("chains", Finish.METALLIC, this.material);

   public ChainBindEffect() {
      super(EffectType.CHAIN_BIND, "chains", new ThemeTint());
   }

   @Override
   public void render(EffectContext c, FxPass pass) {
      Material m = this.material.get();
      Finish f;
      FxPass linkPass = m.translucent(f = this.finish.get()) ? FxPass.TRANSLUCENT : FxPass.SOLID;
      if (pass == linkPass) {
         this.links(c, m, f, pass == FxPass.SOLID);
      } else if (pass == FxPass.GLOW) {
         this.sparkles(c);
      }
   }

   private void links(EffectContext c, Material m, Finish f, boolean solid) {
      float sz = this.size(c);
      float inten = this.intensity(c);
      float spd = this.speed();
      int n = this.chains.getInt();
      double link = 0.17 * sz;
      float reveal = Easing.outCubic(Easing.clamp01(c.intro / 0.9F));
      double tighten = 1.0 + 0.6 * (1.0 - Easing.outCubic(Easing.clamp01(c.intro / 0.7F)));
      double glintPos = c.time * 0.3 * spd % 1.0;
      boolean shine = this.glint.get();
      boolean custom = m == Material.CUSTOM;
      MeshBatch.begin(m, f, c.light, 0.16F, -1, true, solid);

      for (int ch = 0; ch < n; ch++) {
         double az = CHAIN_AZIMUTH[ch] + c.time * 0.3 * spd;
         double tilt = CHAIN_TILT[ch];
         double hx = -Math.sin(az);
         double hz = Math.cos(az);
         double dx = Math.cos(az) * Math.cos(tilt);
         double dy = Math.sin(tilt);
         double dz = Math.sin(az) * Math.cos(tilt);
         double nx = dy * hz;
         double ny = dz * hx - dx * hz;
         double nz = -dy * hx;
         double radius = (c.width * 0.5 + 0.22) * sz * CHAIN_RADIUS[ch] * tighten;
         double cx = c.x;
         double cy = c.y + c.height * CHAIN_HEIGHT[ch];
         double cz = c.z;
         int links = (int)Math.max(8L, Math.round((Math.PI * 2) * radius / (0.72 * link)));
         links += links & 1;
         double step = (Math.PI * 2) / links;
         double slide = c.time * 0.25 * spd + ch * 0.37;

         float kf;
         for (int k = 0; k < links && !((kf = (float)k / links) > reveal); k++) {
            float frontier = Easing.clamp01((reveal - kf) * links);
            double th = slide + k * step;
            double cos = Math.cos(th);
            double sin = Math.sin(th);
            double px = cx + radius * (hx * cos + dx * sin);
            double py = cy + radius * (dy * sin);
            double pz = cz + radius * (hz * cos + dz * sin);
            double ax = -hx * sin + dx * cos;
            double ay = dy * cos;
            double az2 = -hz * sin + dz * cos;
            double bz;
            double by;
            double bx;
            if ((k & 1) == 0) {
               bx = nx;
               by = ny;
               bz = nz;
            } else {
               bx = hx * cos + dx * sin;
               by = dy * sin;
               bz = hz * cos + dz * sin;
            }

            double qx = ay * bz - az2 * by;
            double qy = az2 * bx - ax * bz;
            double qz = ax * by - ay * bx;
            float g = 0.0F;
            if (shine) {
               double d = Math.abs(kf - glintPos);
               d = Math.min(d, 1.0 - d);
               g = (float)Math.exp(-(d / 0.05) * (d / 0.05));
            }

            float alpha;
            if (!((alpha = inten * frontier * c.keep.solid(px, py, pz)) <= 0.003F)) {
               int tint = custom ? this.color(c, loop(kf + ch * 0.25F)) : -1;
               int a = Math.round(alpha * (custom ? tint >>> 24 : 255));
               Chains.link(px, py, pz, ax, ay, az2, bx, by, bz, qx, qy, qz, link, a << 24 | tint & 16777215, g * 0.9F);
            }
         }
      }

      MeshBatch.end();
   }

   private void sparkles(EffectContext c) {
      if (this.glint.get()) {
         float sz = this.size(c);
         float inten = this.intensity(c);
         float spd = this.speed();
         int n = this.chains.getInt();
         float reveal = Easing.outCubic(Easing.clamp01(c.intro / 0.9F));
         double tighten = 1.0 + 0.6 * (1.0 - Easing.outCubic(Easing.clamp01(c.intro / 0.7F)));
         double glintPos = c.time * 0.3 * spd % 1.0;
         if (!(glintPos > reveal)) {
            for (int ch = 0; ch < n; ch++) {
               double az = CHAIN_AZIMUTH[ch] + c.time * 0.3 * spd;
               double tilt = CHAIN_TILT[ch];
               double hx = -Math.sin(az);
               double hz = Math.cos(az);
               double dx = Math.cos(az) * Math.cos(tilt);
               double dy = Math.sin(tilt);
               double dz = Math.sin(az) * Math.cos(tilt);
               double radius = (c.width * 0.5 + 0.22) * sz * CHAIN_RADIUS[ch] * tighten;
               double th = c.time * 0.25 * spd + ch * 0.37 + glintPos * (Math.PI * 2);
               double px = c.x + radius * (hx * Math.cos(th) + dx * Math.sin(th));
               double py = c.y + c.height * CHAIN_HEIGHT[ch] + radius * (dy * Math.sin(th));
               double pz = c.z + radius * (hz * Math.cos(th) + dz * Math.sin(th));
               int col = this.material.get() == Material.CUSTOM ? this.color(c, loop((float)glintPos + ch * 0.25F)) : this.material.get().swatch();
               Vfx.sprite(Textures.GLOW, px, py, pz, 0.35F * sz, 0.0F, alpha(col, 0.35F * inten));
               Vfx.sprite(Textures.FLARE, px, py, pz, 0.45F * sz, (float)(c.time * 1.2 + ch), core(col, 0.8F * inten));
            }
         }
      }
   }
}
