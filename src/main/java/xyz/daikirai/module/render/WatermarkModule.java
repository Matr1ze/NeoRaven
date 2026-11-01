package xyz.daikirai.module.render;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraft.client.gui.inventory.GuiChest;
import org.lwjgl.input.Keyboard;
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
import xyz.daikirai.module.player.ChestStealerModule;
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
    public static ButtonSetting velocityDelay;
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
    public static SliderSetting miniSize;
    public static SliderSetting musicDisplay;
    public static ButtonSetting chestStealer;
    public static SliderSetting chestStealerScale;

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
        velocityDelay = button("Velocity delay");
        infoPanel = button("Info panel");
        infoFps = button("Info: fps");
        infoPing = button("Info: ping");
        infoServer = button("Info: server");

        hold = slider("Hold");
        islandScale = slider("Scale");
        miniSize = slider("Mini size");
        musicDisplay = slider("Music: Display");
        positionX = slider("Position X");
        positionY = slider("Position Y");
        lowBlocks = slider("Low blocks");

        tablist = button("Tablist");
        tablistScale = slider("Tablist: Scale");
        tablistFrostedGlass = button("Tablist: Frosted glass");
        tablistServerText = button("Tablist: Server text");
        tablistShowPing = button("Tablist: Show ping");
        chestStealer = button("ChestStealer");
        chestStealerScale = slider("ChestStealer: Scale");

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
            public void onChestDraw(GuiScreenEvent.DrawScreenEvent.Pre event) {
                if (event.gui == mc.currentScreen && replacesChestScreen()) {
                    ChestStealerModule.instance.visualSession().replacedScreen = true;
                    event.setCanceled(true);
                }
            }

            @SubscribeEvent
            public void onChestMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
                if (event.gui == mc.currentScreen && replacesChestScreen()) event.setCanceled(true);
            }

            @SubscribeEvent
            public void onChestKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
                // Keep Escape and the inventory key available; hidden vanilla slots cannot be used.
                if (event.gui == mc.currentScreen && replacesChestScreen()
                        && Keyboard.getEventKey() != Keyboard.KEY_ESCAPE
                        && Keyboard.getEventKey() != mc.gameSettings.keyBindInventory.getKeyCode()) {
                    event.setCanceled(true);
                }
            }

            @SubscribeEvent
            public void onMusicMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
                if (!(event.gui instanceof net.minecraft.client.gui.GuiChat)
                        || !org.lwjgl.input.Mouse.getEventButtonState()
                        || org.lwjgl.input.Mouse.getEventButton() != 0 || !musicChatVisible()) return;
                net.minecraft.client.gui.ScaledResolution resolution = new net.minecraft.client.gui.ScaledResolution(mc);
                float x = org.lwjgl.input.Mouse.getEventX() * resolution.getScaledWidth() / (float) mc.displayWidth;
                float y = resolution.getScaledHeight() - org.lwjgl.input.Mouse.getEventY()
                        * resolution.getScaledHeight() / (float) mc.displayHeight - 1.0F;
                if (ISLAND.musicClick(x, y)) event.setCanceled(true);
            }

            @SubscribeEvent
            public void onRenderTick(TickEvent.RenderTickEvent event) {
                if (event.phase != TickEvent.Phase.END || instance == null) {
                    return;
                }
                if (!Overlay.canRender() && !musicChatVisible()) {
                    ISLAND.hideTabList();
                    return;
                }
                xyz.daikirai.gui.onyx.OnyxClickGuiModule.sync();
                NowPlaying.setEnabled(instance.isEnabled() && instance.nowPlaying());
                // The island draws vanilla FontRenderer text (the tab list's server header/footer),
                // and RenderTick listeners are not inside the per-frame font scope.
                xyz.daikirai.gui.onyx.render.GuiFont.begin();
                try {
                    if (Overlay.canRender()) INFO_BAR.render(instance);
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

    private static boolean musicChatVisible() {
        return mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.hideGUI
                && !mc.gameSettings.showDebugInfo && mc.currentScreen instanceof net.minecraft.client.gui.GuiChat
                && xyz.daikirai.music.MusicIsland.hasTrack();
    }

    public static Island island() {
        return ISLAND;
    }

    public static boolean chestStealerEnabled() {
        return instance != null && instance.isEnabled() && chestStealer != null && chestStealer.isToggled();
    }

    public static boolean replacesChestScreen() {
        return chestStealerEnabled() && mc.theWorld != null && !mc.gameSettings.hideGUI
                && !mc.gameSettings.showDebugInfo && mc.currentScreen instanceof GuiChest
                && ChestStealerModule.instance != null && ChestStealerModule.instance.visualSession() != null;
    }

    public static float chestStealerScaleFactor() {
        return chestStealerScale == null ? 1.0F : (float) chestStealerScale.getInput();
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

    public boolean velocityDelay() {
        return velocityDelay == null || velocityDelay.isToggled();
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

    /** Scale of the compact music card on the island; the player window morph starts/ends there. */
    public static float miniSizeFactor() {
        return miniSize == null ? 1.0F : (float) miniSize.getInput();
    }

    /** Whether the collapsed island row shows the synced lyric instead of the song name. */
    public static boolean musicShowsLyric() {
        return musicDisplay != null && Math.round(musicDisplay.getInput()) == 1;
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
