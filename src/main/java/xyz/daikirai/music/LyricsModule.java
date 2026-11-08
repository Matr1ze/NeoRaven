package xyz.daikirai.music;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * World-anchored lyric lines.
 *
 * <p>Every new line spawns a fixed point in the world relative to the player's yaw at the moment
 * the line begins. The point does not follow the camera or the player; walking forward changes the
 * distance and eventually lets the player stand in front of the text. The next line spawns at a
 * new point.</p>
 */
public final class LyricsModule extends Module {

    public static LyricsModule instance;

    public static SliderSetting distance;
    public static SliderSetting sideOffset;
    public static SliderSetting height;
    public static SliderSetting scale;
    public static ButtonSetting showTranslation;

    private static final LyricsWorld WORLD = new LyricsWorld();
    private static Object listener;

    public LyricsModule(int key) {
        super("Lyrics", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        distance = slider("Distance");
        sideOffset = slider("Side offset");
        height = slider("Height");
        scale = slider("Scale");
        showTranslation = button("Show translation");
        registerRenderer();
    }

    private static SliderSetting slider(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    private static ButtonSetting button(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
    }

    private static void registerRenderer() {
        if (listener != null) {
            return;
        }
        listener = new Object() {
            @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOW)
            public void onRenderWorld(RenderWorldLastEvent event) {
                if (instance == null || !instance.isEnabled()) {
                    return;
                }
                WORLD.render(instance, event.partialTicks);
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @Override
    public void onEnable() {
        WORLD.reset();
    }

    public float distance() {
        return distance == null ? 10.0F : (float) distance.getInput();
    }

    public float sideOffset() {
        return sideOffset == null ? 4.0F : (float) sideOffset.getInput();
    }

    public float height() {
        return height == null ? 1.5F : (float) height.getInput();
    }

    public float scale() {
        return scale == null ? 1.0F : (float) scale.getInput();
    }

    public boolean showTranslation() {
        return showTranslation == null || showTranslation.isToggled();
    }
}
