package xyz.daikirai.module.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.Render3D;
import xyz.daikirai.util.RenderUtils;

public final class GlowEspModule extends Module {
    private ColorSetting color;
    private ButtonSetting players, mobs, renderSelf;
    private SliderSetting radius;
    private Shader halo;

    public GlowEspModule(int key) { super("Glow ESP", Category.render, key); }

    @Override
    public void link() {
        color = (ColorSetting) ModuleManager.setting(this, "Color");
        players = (ButtonSetting) ModuleManager.setting(this, "Players");
        mobs = (ButtonSetting) ModuleManager.setting(this, "Mobs");
        renderSelf = (ButtonSetting) ModuleManager.setting(this, "Render self");
        radius = (SliderSetting) ModuleManager.setting(this, "Glow radius");
    }

    private boolean accepts(Entity entity) {
        if (entity == mc.thePlayer) return Render3D.showSelf(renderSelf);
        return entity instanceof EntityLivingBase && entity != mc.getRenderViewEntity()
                && !(entity instanceof EntityArmorStand) && entity.isEntityAlive() && !entity.isInvisible()
                && (entity instanceof EntityPlayer ? players.isToggled() : mobs.isToggled())
                && Render3D.isVisible(entity);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !OpenGlHelper.isFramebufferEnabled()
                || !OpenGlHelper.shadersSupported
                || (!players.isToggled() && !mobs.isToggled() && !Render3D.showSelf(renderSelf))) return;
        if (halo == null) halo = Shader.load("onyx_entity_glow", "onyx_jump_glass");
        if (!halo.valid()) return;
        int rgb = color.color();
        PlayerESPModule.renderMask(event.partialTicks, mc.theWorld.loadedEntityList, this::accepts, entity -> rgb, mask -> {
            GlStateManager.disableAlpha();
            halo.use();
            halo.uniform1i("mask", 0);
            halo.uniform2f("texel", 1F / mask.framebufferTextureWidth, 1F / mask.framebufferTextureHeight);
            halo.uniform1f("radius", (float) radius.getInput());
            halo.uniform3f("tint", (rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
            GlStateManager.bindTexture(mask.framebufferTexture);
            RenderUtils.screenQuad();
            GlStateManager.bindTexture(0);
        });
    }
}
