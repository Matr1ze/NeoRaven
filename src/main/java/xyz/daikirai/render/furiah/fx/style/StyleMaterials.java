package xyz.daikirai.render.furiah.fx.style;

import java.util.function.BooleanSupplier;
import xyz.daikirai.render.furiah.render.material.Finish;
import xyz.daikirai.render.furiah.render.material.Material;
import xyz.daikirai.render.furiah.setting.ModeSetting;

public final class StyleMaterials {
   private StyleMaterials() {
   }

   public static ModeSetting<Material> material(String key, Material defaultMaterial, String description) {
      return new ModeSetting<>(key + ".material", "Material", description, Material.class, defaultMaterial)
         .style(ModeSetting.Style.CHIPS)
         .keywords(new String[]{"material", "metal", "gold", "silver", "chrome", "brass", "bronze", "iron", "obsidian", "pearl", "jade", "custom"});
   }

   public static ModeSetting<Finish> finish(String key, Finish defaultFinish, final ModeSetting<Material> material) {
      return new ModeSetting<>(
            key + ".finish",
            "Finish",
            "How a Custom material takes the light: Metallic, Satin, Matte, Glass, Pearlescent or Toon (flat bands, like a comic).",
            Finish.class,
            defaultFinish
         )
         .<ModeSetting>keywords(new String[]{"shine", "gloss", "matte", "glass", "pearl", "metallic", "satin", "toon", "cartoon", "cel"})
         .visibleWhen(new BooleanSupplier() {
            @Override
            public boolean getAsBoolean() {
               return material.get() == Material.CUSTOM;
            }
         });
   }
}
