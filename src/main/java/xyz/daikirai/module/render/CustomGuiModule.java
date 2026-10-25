package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.ScoreboardPositionEditor;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.hud.Hotbar;
import xyz.daikirai.gui.onyx.hud.Scoreboard;
import xyz.daikirai.gui.onyx.render.GuiFont;
import xyz.daikirai.gui.onyx.render.GuiOpenAnimation;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/** Onyx's CustomGui container for independently controlled vanilla GUI replacements. */
public final class CustomGuiModule extends Module {

    public static CustomGuiModule instance;

    private static final Scoreboard HUD = new Scoreboard();
    private static final Hotbar HOTBAR = new Hotbar();
    private static Object listener;

    private static ButtonSetting scoreboard;
    private static ButtonSetting scoreboardEditPosition;
    public static ButtonSetting scoreboardFrostedGlass;
    private static ButtonSetting scoreboardShowScores;
    public static SliderSetting scoreboardScale;
    private static SliderSetting scoreboardPositionX;
    private static SliderSetting scoreboardPositionY;

    private static ButtonSetting font;
    private static SliderSetting fontWeight;
    private static SliderSetting fontSize;
    private static SliderSetting fontSpacing;
    private static ButtonSetting fontShadow;

    private static ButtonSetting openAnimation;
    private static SliderSetting openDuration;
    private static SliderSetting openScale;
    private static ButtonSetting openFade;
    private static ButtonSetting centeredContainers;

    private static ButtonSetting hotbar;
    private static SliderSetting hotbarScale;
    private static ButtonSetting hotbarFrostedGlass;
    private static ButtonSetting hotbarSlotGlide;
    private static ButtonSetting hotbarItemPop;

    public CustomGuiModule(int key) {
        super("CustomGui", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        scoreboard = button("Scoreboard");
        scoreboardScale = slider("Scoreboard: Scale");
        scoreboardPositionX = slider("Scoreboard: Position X");
        scoreboardPositionY = slider("Scoreboard: Position Y");
        scoreboardFrostedGlass = button("Scoreboard: Frosted glass");
        scoreboardShowScores = button("Scoreboard: Show scores");
        scoreboardEditPosition = button("Scoreboard: Edit position");

        font = button("Font");
        fontWeight = slider("Font: Weight");
        fontSize = slider("Font: Size");
        fontSpacing = slider("Font: Letter spacing");
        fontShadow = button("Font: Shadow");

        openAnimation = button("Open animation");
        openDuration = slider("Open animation: Duration");
        openScale = slider("Open animation: Scale from");
        openFade = button("Open animation: Fade");
        centeredContainers = button("Open animation: Centered containers");

        hotbar = button("Hotbar");
        hotbarScale = slider("Hotbar: Scale");
        hotbarFrostedGlass = button("Hotbar: Frosted glass");
        hotbarSlotGlide = button("Hotbar: Slot glide");
        hotbarItemPop = button("Hotbar: Item pop");

        registerRenderer();
    }

    private static ButtonSetting button(String name) {
        return (ButtonSetting) ModuleManager.setting(instance, name);
    }

    private static SliderSetting slider(String name) {
        return (SliderSetting) ModuleManager.setting(instance, name);
    }

    /** Registered once so the card can keep fading out after the module is disabled. */
    private static void registerRenderer() {
        if (listener != null) {
            return;
        }
        listener = new Object() {
            @SubscribeEvent
            public void onRenderTick(TickEvent.RenderTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) {
                    return;
                }
                if (!Overlay.canRender()) {
                    return;
                }
                OnyxClickGuiModule.sync();
                // HUD modules run in a RenderTick listener, outside the per-frame font scope.
                xyz.daikirai.gui.onyx.render.GuiFont.begin();
                try {
                    HUD.render(instance, false);
                    HOTBAR.render(instance);
                } finally {
                    xyz.daikirai.gui.onyx.render.GuiFont.end();
                }
            }

            /** The ported bar replaces the vanilla hotbar and experience bar. */
            @SubscribeEvent
            public void onOverlay(RenderGameOverlayEvent.Pre event) {
                if (!replacesHotbar()) {
                    return;
                }
                if (event.type == RenderGameOverlayEvent.ElementType.HOTBAR
                        || event.type == RenderGameOverlayEvent.ElementType.EXPERIENCE) {
                    event.setCanceled(true);
                }
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    /** Called by the overlay hook: the ported bar replaces the vanilla hotbar. */
    public static boolean replacesHotbar() {
        return instance != null && instance.hotbarEnabled();
    }

    private static int statsDepth;

    /**
     * Wraps one of the vanilla status rows (health, food, armour, air, mount) so it is drawn in the
     * ported bar's frame. Called from {@code MixinGuiIngameForge} around each of those methods;
     * {@link #popStatsTransform()} is the matching tail.
     */
    public static void pushStatsTransform() {
        if (instance != null && HOTBAR.pushStatusTransform(instance)) {
            statsDepth++;
        }
    }

    public static void popStatsTransform() {
        if (statsDepth > 0) {
            statsDepth--;
            net.minecraft.client.renderer.GlStateManager.popMatrix();
        }
    }

    public boolean hotbarEnabled() {
        return isEnabled() && hotbar != null && hotbar.isToggled();
    }

    public float hotbarScale() {
        return hotbarScale == null ? 1.0F : (float) hotbarScale.getInput();
    }

    public boolean hotbarFrosted() {
        return hotbarFrostedGlass == null || hotbarFrostedGlass.isToggled();
    }

    public boolean hotbarSlotGlide() {
        return hotbarSlotGlide == null || hotbarSlotGlide.isToggled();
    }

    public boolean hotbarItemPop() {
        return hotbarItemPop == null || hotbarItemPop.isToggled();
    }

    public static Hotbar hotbarHud() {
        return HOTBAR;
    }

    /** Called by {@code MixinGuiIngame}: the ported card replaces the vanilla sidebar. */
    public static boolean replacesScoreboard() {
        return instance != null && instance.scoreboardEnabled();
    }

    public boolean scoreboardEnabled() {
        return isEnabled() && scoreboard != null && scoreboard.isToggled();
    }

    @Override
    public void guiButtonToggled(ButtonSetting button) {
        if (button == scoreboardEditPosition) {
            mc.displayGuiScreen(new ScoreboardPositionEditor());
        } else if (button == scoreboard && scoreboard.isToggled()) {
            HUD.reset();
        } else if (button == hotbar && hotbar.isToggled()) {
            HOTBAR.reset();
        } else if (button == font) {
            GuiFont.reset();
        } else if (button == openAnimation && !openAnimation.isToggled()) {
            GuiOpenAnimation.reset();
        }
    }

    @Override
    public void onEnable() {
        HUD.reset();
        HOTBAR.reset();
        GuiFont.reset();
    }

    @Override
    public void onDisable() {
        // Keep the last frame while the card fades out; the next enable resets the animation state.
        GuiOpenAnimation.reset();
    }

    public static Scoreboard scoreboardHud() {
        return HUD;
    }

    public boolean scoreboardFrosted() {
        return scoreboardFrostedGlass == null || scoreboardFrostedGlass.isToggled();
    }

    public boolean scoreboardShowScores() {
        return scoreboardShowScores != null && scoreboardShowScores.isToggled();
    }

    public float scoreboardScale() {
        return scoreboardScale == null ? 1.0F : (float) scoreboardScale.getInput();
    }

    public float scoreboardPositionX() {
        return scoreboardPositionX == null ? 100.0F : (float) scoreboardPositionX.getInput();
    }

    public float scoreboardPositionY() {
        return scoreboardPositionY == null ? 50.0F : (float) scoreboardPositionY.getInput();
    }

    public void setScoreboardPosition(float percentX, float percentY) {
        if (scoreboardPositionX != null) {
            scoreboardPositionX.setValue(percentX);
        }
        if (scoreboardPositionY != null) {
            scoreboardPositionY.setValue(percentY);
        }
    }

    public static boolean replacesFont() {
        return instance != null && instance.isEnabled() && font != null && font.isToggled();
    }

    public static boolean mediumFont() {
        return fontWeight == null || fontWeight.getInput() >= 1.0;
    }

    public static float fontSize() {
        return fontSize == null ? 1.0F : (float) fontSize.getInput();
    }

    public static float fontSpacing() {
        return fontSpacing == null ? 0.0F : (float) fontSpacing.getInput();
    }

    public static boolean fontShadow() {
        return fontShadow == null || fontShadow.isToggled();
    }

    public static boolean animatesScreens() {
        return instance != null && instance.isEnabled() && openAnimation != null && openAnimation.isToggled();
    }

    public static float openDuration() {
        return openDuration == null ? 300.0F : (float) openDuration.getInput();
    }

    public static float openScale() {
        return openScale == null ? 0.92F : (float) openScale.getInput();
    }

    public static boolean openFade() {
        return openFade == null || openFade.isToggled();
    }

    public static boolean centeredContainers() {
        return animatesScreens() && centeredContainers != null && centeredContainers.isToggled();
    }
}
