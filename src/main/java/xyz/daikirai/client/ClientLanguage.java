package xyz.daikirai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;
import net.minecraft.client.resources.LanguageManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.util.Lang;

/**
 * Keeps the client's own language table ({@link Lang}) and the vanilla game language in step.
 *
 * <p>They used to be two independent switches: the ClickGUI's Language row only moved the client
 * and the vanilla language list only moved the game, so the first-run screen, the ClickGUI and the
 * vanilla screens could sit in different languages indefinitely. They are now bound both ways,
 * with the game language as the authority: it is mirrored into the client every client tick, and
 * a change made through the ClickGUI switch is pushed into the game on the next tick (never from
 * the middle of a render pass, because that reloads every resource).</p>
 *
 * <p>The client only ships English and Chinese. A game language that is neither is mirrored as
 * English, but it is never rewritten to English: the player asked for that language, so the game
 * side is left alone until one of the two client languages is picked in the ClickGUI.</p>
 */
public final class ClientLanguage {

    private static final String ENGLISH = "en_US";
    private static final String CHINESE = "zh_CN";

    /** Game language requested by the ClickGUI switch, applied on the next client tick. */
    private static String requested;

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        String queued = requested;
        requested = null;
        if (queued != null) {
            apply(queued);
        }
        follow();
    }

    /** The ClickGUI changed {@link Lang}; queue the matching game language for the next tick. */
    public static void requestGame(String locale) {
        requested = Lang.CHINESE.equals(locale) ? CHINESE : ENGLISH;
    }

    /** Game -> client. Cheap enough to run every tick; only touches disk on an actual change. */
    private static void follow() {
        Minecraft mc = Minecraft.getMinecraft();
        String wanted = isChinese(mc.gameSettings.language) ? Lang.CHINESE : Lang.ENGLISH;
        if (wanted.equals(Lang.locale())) {
            return;
        }
        Lang.setLocale(wanted);
        OnyxClickGuiModule.refreshLanguageSetting();
        ClientConfig.save();
    }

    /**
     * Client -> game. Mirrors what the vanilla language menu does: update the language manager and
     * the persisted option first, reload the resources, then move the font renderer's unicode/bidi
     * flags with it.
     */
    private static void apply(String code) {
        Minecraft mc = Minecraft.getMinecraft();
        LanguageManager manager = mc.getLanguageManager();
        Language target = null;
        for (Language language : manager.getLanguages()) {
            if (language.getLanguageCode().equalsIgnoreCase(code)) {
                target = language;
                break;
            }
        }
        if (target == null || target.getLanguageCode().equals(mc.gameSettings.language)) {
            return;
        }
        manager.setCurrentLanguage(target);
        mc.gameSettings.language = target.getLanguageCode();
        mc.refreshResources();
        mc.fontRendererObj.setUnicodeFlag(manager.isCurrentLocaleUnicode() || mc.gameSettings.forceUnicodeFont);
        mc.fontRendererObj.setBidiFlag(manager.isCurrentLanguageBidirectional());
        mc.gameSettings.saveOptions();
    }

    private static boolean isChinese(String code) {
        return code != null && code.regionMatches(true, 0, "zh", 0, 2);
    }
}
