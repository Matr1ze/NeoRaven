package xyz.daikirai.module.render;

import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.TargetHudPositionEditor;
import xyz.daikirai.module.ClickGuiModule;
import xyz.daikirai.gui.onyx.hud.TargetHud;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.combat.KillAuraModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
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

    /** Shipped particle density; {@code 1.0F} is Rise's own rate. */
    public static final float DEFAULT_PARTICLE_AMOUNT = 1.2F;

    private static final TargetHud HUD = new TargetHud();
    private static Object listener;

    public static ButtonSetting editPosition;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting showHealth;
    public static ButtonSetting showDistance;
    public static ButtonSetting showArmor;
    public static ButtonSetting renderEsp;

    /** Rise-style hit feedback: the avatar squash/flash and the particle burst. */
    public static ButtonSetting hitAnimation;
    public static ButtonSetting hitParticles;

    public static SliderSetting scale;
    public static SliderSetting positionX;
    public static SliderSetting positionY;
    public static SliderSetting linger;

    /** Multiplies Rise's particle count; 1x is the original density. */
    public static SliderSetting particleAmount;
    /** Multiplies the launch speed of every dot: how hard the burst is thrown out. */
    public static SliderSetting burstForce;
    /** Particle tint. Follows the theme by default, which is what Rise did. */
    public static ColorSetting particleTint;

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
        particleAmount = slider("Particle amount");
        burstForce = slider("Burst force");
        particleTint = color("Particle color");
        frostedGlass = button("Frosted glass");
        showHealth = button("Show health");
        showDistance = button("Show distance");
        showArmor = button("Show armor");
        renderEsp = button("Render ESP");
        hitAnimation = button("Hit animation");
        hitParticles = button("Hit particles");
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

    private static ColorSetting color(String name) {
        Setting setting = ModuleManager.setting(instance, name);
        return setting instanceof ColorSetting ? (ColorSetting) setting : null;
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
                ClickGuiModule.sync();
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

    /** Draws the card once from inside a screen (see {@code HudPass}). */
    public static void renderHud(float partialTicks) {
        if (instance != null) {
            HUD.render(instance, false, partialTicks);
        }
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

    /** The avatar squash + red flash on every hit (Rise's {@code headAnimation}). */
    public boolean hitAnimation() {
        return hitAnimation == null || hitAnimation.isToggled();
    }

    /** The dots that burst out of the avatar on every hit (Rise's {@code ParticleComponent}). */
    public boolean hitParticles() {
        return hitParticles == null || hitParticles.isToggled();
    }

    /**
     * Particle density multiplier. {@code 1} is Rise's original rate (0–2.5 dots per tick); the
     * shipped default is higher because the original reads a little sparse on a big screen.
     */
    public float particleAmount() {
        return particleAmount == null ? DEFAULT_PARTICLE_AMOUNT : (float) particleAmount.getInput();
    }

    /**
     * Launch-speed multiplier for the burst. {@code 1} is the tuned default: a hard shove that is
     * mostly spent in ~150 ms, then a long slow drift.
     */
    public float burstForce() {
        return burstForce == null ? 1.0F : (float) burstForce.getInput();
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
