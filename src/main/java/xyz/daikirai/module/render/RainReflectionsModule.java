package xyz.daikirai.module.render;

import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.render.RainReflectionRenderer;
import xyz.daikirai.render.furiah.render.OptiFineCompat;

public final class RainReflectionsModule extends Module {
    public static final String NAME = "Rain Reflections";
    private final RainReflectionRenderer renderer = new RainReflectionRenderer();

    public RainReflectionsModule(int key) {
        super(NAME, Category.render, key);
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void render(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.theWorld.provider.getHasNoSky() || mc.getRenderViewEntity() == null) return;
        float rain = mc.theWorld.getRainStrength(event.partialTicks);
        if (rain <= 0.01F || OptiFineCompat.shadersActive()) return;
        if (ActiveRenderInfo.getBlockAtEntityViewpoint(mc.theWorld, mc.getRenderViewEntity(), event.partialTicks)
                .getMaterial().isLiquid()) return;
        renderer.render(event.partialTicks, rain);
    }
}
