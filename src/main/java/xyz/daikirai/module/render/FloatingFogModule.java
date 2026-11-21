package xyz.daikirai.module.render;

import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.world.WorldEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.FloatingFogRenderer;

public final class FloatingFogModule extends Module {
    private final FloatingFogRenderer renderer = new FloatingFogRenderer();
    private SliderSetting density, range, speed;
    private ColorSetting color;
    private ButtonSetting particles;

    public FloatingFogModule(int key) {
        super("Floating Fog", Category.render, key);
    }

    @Override public void link() {
        density = (SliderSetting) ModuleManager.setting(this, "Fog density");
        range = (SliderSetting) ModuleManager.setting(this, "Fog range");
        speed = (SliderSetting) ModuleManager.setting(this, "Drift speed");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
        color.setColor(color.customColor());
        particles = (ButtonSetting) ModuleManager.setting(this, "Floating particles");
    }

    @Override public void onEnable() {
        renderer.reset();
    }

    @Override public void onDisable() {
        renderer.dispose();
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) renderer.dispose();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void render(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) return;
        Material material = ActiveRenderInfo.getBlockAtEntityViewpoint(
                mc.theWorld, mc.getRenderViewEntity(), event.partialTicks).getMaterial();
        if (material.isLiquid()) return;
        renderer.render(event.partialTicks, (float) (density.getInput() / 100), (float) range.getInput(),
                (float) (speed.getInput() / 100), color.color(), particles.isToggled());
    }
}
