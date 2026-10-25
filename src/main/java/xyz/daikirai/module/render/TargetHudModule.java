package xyz.daikirai.module.render;

import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.TargetHudPositionEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.TargetHud;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.combat.KillAuraModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.Render3D;

/**
 * Material 3 Expressive target card, ported from OpenOnyx's {@code TargetHUDModule}.
 *
 * <p>Onyx's target HUD was a frosted card with the avatar, name, distance and a health bar, and a
 * "world" mode that anchored it above the target while the target was on screen. This port wires
 * the same card to Raven's KillAura: it follows the current target, holds the last position for
 * the configured linger time after the target disappears, shows the new info before moving when
 * the target switches, and falls back to the user's home position when the target is off screen.</p>
 */
public final class TargetHudModule extends Module {

    public static TargetHudModule instance;

    private static final TargetHud HUD = new TargetHud();
    private static Object listener;

    public static ButtonSetting editPosition;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting showHealth;
    public static ButtonSetting showDistance;
    public static ButtonSetting showArmor;
    public static ButtonSetting renderEsp;

    public static SliderSetting scale;
    public static SliderSetting positionX;
    public static SliderSetting positionY;
    public static SliderSetting linger;

    public TargetHudModule(int key) {
        super("TargetHUD", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        scale = slider("Scale");
        positionX = slider("Position X");
        positionY = slider("Position Y");
        linger = slider("Linger");
        frostedGlass = button("Frosted glass");
        showHealth = button("Show health");
        showDistance = button("Show distance");
        showArmor = button("Show armor");
        renderEsp = button("Render ESP");
        editPosition = button("Edit position");

        registerRenderer();
    }

    private static ButtonSetting button(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
    }

    private static SliderSetting slider(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof SliderSetting ? (SliderSetting) setting : null;
    }

    /** Registered once so the card can keep fading out after the module is disabled. */
    private static void registerRenderer() {
        if (listener != null) {
            return;
        }
        listener = new Object() {
            @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.LOW)
            public void onRenderTick(TickEvent.RenderTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) {
                    return;
                }
                if (!Overlay.canRender()) {
                    return;
                }
                OnyxClickGuiModule.sync();
                HUD.render(instance, false, event.renderTickTime);
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (renderEsp == null || !renderEsp.isToggled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        KillAuraModule.Target target = KillAuraModule.target;
        if (target == null || target.entity == null) {
            return;
        }
        Entity entity = target.entity;
        if (entity == mc.thePlayer) {
            return;
        }
        Render3D.entityBox(entity, 2, xyz.daikirai.gui.onyx.material.Theme.scheme().primary,
                event.partialTicks);
    }

    @Override
    public void guiButtonToggled(ButtonSetting button) {
        if (button == editPosition) {
            mc.displayGuiScreen(new TargetHudPositionEditor());
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
    }

    @Override
    public void onDisable() {
        // Keep the last frame so the card can fade out; the next enable resets the state.
    }

    public static TargetHud hud() {
        return HUD;
    }

    public boolean frosted() {
        return frostedGlass == null || frostedGlass.isToggled();
    }

    public boolean showHealth() {
        return showHealth == null || showHealth.isToggled();
    }

    public boolean showDistance() {
        return showDistance == null || showDistance.isToggled();
    }

    public boolean showArmor() {
        return showArmor == null || showArmor.isToggled();
    }

    public float scale() {
        return scale == null ? 1.0F : (float) scale.getInput();
    }

    public float positionX() {
        return positionX == null ? 50.0F : (float) positionX.getInput();
    }

    public float positionY() {
        return positionY == null ? 35.0F : (float) positionY.getInput();
    }

    /** How long the card keeps the last target (and its position) before fading out. */
    public float linger() {
        return linger == null ? 0.3F : (float) linger.getInput();
    }

    public void setPosition(float percentX, float percentY) {
        if (positionX != null) {
            positionX.setValue(percentX);
        }
        if (positionY != null) {
            positionY.setValue(percentY);
        }
    }
}
