package xyz.daikirai.gui.onyx;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/** Tracks the vanilla language so the ClickGUI can switch between English and Chinese. */
final class GuiLanguage {

    private GuiLanguage() {
    }

    static boolean simplifiedChinese() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null || mc.gameSettings.language == null) {
            return false;
        }
        return mc.gameSettings.language.toLowerCase(Locale.ROOT).startsWith("zh_cn");
    }
}
