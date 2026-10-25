package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.gui.IslandPositionEditor;
import xyz.daikirai.gui.onyx.hud.InfoBar;
import xyz.daikirai.gui.onyx.hud.Island;
import xyz.daikirai.gui.onyx.hud.NowPlaying;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Dynamic Island watermark, ported from OpenOnyx's {@code WatermarkModule}.
 *
 * <p>The island sits on the HUD and morphs between the idle client name, module toggle
 * announcements and a live Scaffold block gauge. Drag it while chat is open (the same gesture the
 * Onyx client used), or press "Edit position" to drag it on a dedicated screen.</p>
 */
public final class WatermarkModule extends Module {

    public static WatermarkModule instance;

    private static final Island ISLAND = new Island();
    private static final InfoBar INFO_BAR = new InfoBar();
    private static Object listener;

    public static ButtonSetting moduleToggles;
    public static ButtonSetting showLogo;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting scaffoldBlocks;
    public static ButtonSetting editPosition;
    public static ButtonSetting nowPlaying;
    public static ButtonSetting bedProgress;
    public static ButtonSetting gappleProgress;
    public static ButtonSetting infoPanel;
    public static ButtonSetting infoFps;
    public static ButtonSetting infoPing;
    public static ButtonSetting infoServer;

    public static ButtonSetting tablist;
    public static ButtonSetting tablistFrostedGlass;
    public static ButtonSetting tablistServerText;
    public static ButtonSetting tablistShowPing;

    public static SliderSetting hold;
    public static SliderSetting islandScale;
    public static SliderSetting positionX;
    public static SliderSetting positionY;
    public static SliderSetting lowBlocks;
    public static SliderSetting tablistScale;

    public WatermarkModule(int key) {
        super("Watermark", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        moduleToggles = button("Module toggles");
        showLogo = button("Logo");
        frostedGlass = button("Frosted glass");
        scaffoldBlocks = button("Scaffold blocks");
        editPosition = button("Edit position");
        nowPlaying = button("Now playing");
        bedProgress = button("Bed progress");
        gappleProgress = button("Gapple progress");
        infoPanel = button("Info panel");
        infoFps = button("Info: fps");
        infoPing = button("Info: ping");
        infoServer = button("Info: server");

        hold = slider("Hold");
        islandScale = slider("Scale");
        positionX = slider("Position X");
        positionY = slider("Position Y");
        lowBlocks = slider("Low blocks");

        tablist = button("Tablist");
        tablistScale = slider("Tablist: Scale");
        tablistFrostedGlass = button("Tablist: Frosted glass");
        tablistServerText = button("Tablist: Server text");
        tablistShowPing = button("Tablist: Show ping");

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

    /**
     * The island needs to outlive the module's enabled state so it can fade out when disabled, so
     * the render hook is registered once and is not tied to the module's event-bus subscription.
     */
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
                    ISLAND.hideTabList();
                    return;
                }
                xyz.daikirai.gui.onyx.OnyxClickGuiModule.sync();
                NowPlaying.setEnabled(instance.isEnabled() && instance.nowPlaying());
                // The island draws vanilla FontRenderer text (the tab list's server header/footer),
                // and RenderTick listeners are not inside the per-frame font scope.
                xyz.daikirai.gui.onyx.render.GuiFont.begin();
                try {
                    INFO_BAR.render(instance);
                    ISLAND.render(instance, false);
                } finally {
                    xyz.daikirai.gui.onyx.render.GuiFont.end();
                }
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    @Override
    public void guiButtonToggled(ButtonSetting button) {
        if (button == editPosition) {
            mc.displayGuiScreen(new IslandPositionEditor());
        }
    }

    @Override
    public void onEnable() {
        ISLAND.reset();
        INFO_BAR.reset();
    }

    @Override
    public void onDisable() {
        ISLAND.reset();
        INFO_BAR.reset();
        NowPlaying.setEnabled(false);
    }

    public static Island island() {
        return ISLAND;
    }

    public boolean announceToggles() {
        return moduleToggles == null || moduleToggles.isToggled();
    }

    public boolean logo() {
        return showLogo == null || showLogo.isToggled();
    }

    public boolean frosted() {
        return frostedGlass == null || frostedGlass.isToggled();
    }

    public boolean scaffoldGauge() {
        return scaffoldBlocks == null || scaffoldBlocks.isToggled();
    }

    public boolean nowPlaying() {
        return nowPlaying == null || nowPlaying.isToggled();
    }

    public boolean bedProgress() {
        return bedProgress == null || bedProgress.isToggled();
    }

    public boolean gappleProgress() {
        return gappleProgress == null || gappleProgress.isToggled();
    }

    public boolean infoPanel() {
        return infoPanel == null || infoPanel.isToggled();
    }

    public boolean infoFps() {
        return infoFps == null || infoFps.isToggled();
    }

    public boolean infoPing() {
        return infoPing == null || infoPing.isToggled();
    }

    public boolean infoServer() {
        return infoServer == null || infoServer.isToggled();
    }

    public float holdSeconds() {
        return hold == null ? 0.8F : (float) hold.getInput();
    }

    public float islandScale() {
        return islandScale == null ? 1.0F : (float) islandScale.getInput();
    }

    public float positionX() {
        return positionX == null ? 50.0F : (float) positionX.getInput();
    }

    public float positionY() {
        return positionY == null ? 0.0F : (float) positionY.getInput();
    }

    public float lowBlockCount() {
        return lowBlocks == null ? 32.0F : (float) lowBlocks.getInput();
    }

    public void setPosition(float percentX, float percentY) {
        if (positionX != null) {
            positionX.setValue(percentX);
        }
        if (positionY != null) {
            positionY.setValue(percentY);
        }
    }

    // ------------------------------------------------------------------ //
    // Tab list (moved here from CustomGui: it is drawn by the island)
    // ------------------------------------------------------------------ //

    /** The player list replaces the vanilla one while the island owns it. */
    public static boolean replacesTablist() {
        return instance != null && instance.isEnabled() && tablist != null && tablist.isToggled()
                && Overlay.canRender();
    }

    /** Held-down TAB inside a world, with no screen open. */
    public static boolean tablistHeld() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
        return replacesTablist() && mc.currentScreen == null && mc.getNetHandler() != null
                && mc.gameSettings.keyBindPlayerList.isKeyDown();
    }

    public static boolean tablistFrosted() {
        return tablistFrostedGlass == null || tablistFrostedGlass.isToggled();
    }

    public static boolean tablistShowsServerText() {
        return tablistServerText == null || tablistServerText.isToggled();
    }

    public static boolean tablistShowsPing() {
        return tablistShowPing == null || tablistShowPing.isToggled();
    }

    public static float tablistScaleFactor() {
        return tablistScale == null ? 1.0F : (float) tablistScale.getInput();
    }
}
