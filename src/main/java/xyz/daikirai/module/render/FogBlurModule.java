package xyz.daikirai.module.render;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.FogBlurRenderer;
import xyz.daikirai.util.Zoom;

public final class FogBlurModule extends VisualModule {
    public final SliderSetting distance = slider("Distance", 24, 4, 256, 1, " m");
    public final SliderSetting fade = slider("Fade", 48, 4, 256, 1, " m");
    public final ButtonSetting clientColor = toggle("Client Color", true);
    public final SliderSetting tint = slider("Tint", .5, .05, .95, .05, "");
    public final ButtonSetting disableOnZoom = toggle("Disable on zoom", true);
    private final FogBlurRenderer renderer = new FogBlurRenderer();

    public FogBlurModule(int key) { super("Fog Blur", key); }
    @Override public void onEnable() { renderer.reset(); }
    @Override public void onDisable() { renderer.dispose(); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void render(RenderWorldLastEvent event) {
        if (mc.theWorld != null && mc.thePlayer != null && !(disableOnZoom.isToggled() && Zoom.isZooming())) renderer.render(this);
    }
}
