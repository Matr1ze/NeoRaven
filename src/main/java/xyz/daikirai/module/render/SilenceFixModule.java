package xyz.daikirai.module.render;

import net.minecraft.client.resources.Language;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;

public final class SilenceFixModule extends Module {

    public static final String ARRAY_LIST_LABEL = "-----空岛高性能模式-----";
    private static SilenceFixModule instance;

    public SilenceFixModule(int key) {
        super("SilenceFix", Category.render, key);
        instance = this;
    }

    public static String islandText(String fallback) {
        if (instance == null || !instance.isEnabled()) {
            return fallback;
        }
        Language language = mc.getLanguageManager().getCurrentLanguage();
        return language != null && language.getLanguageCode().startsWith("zh_") ? "欣欣公益" : "SilenceFix";
    }
}
