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

import java.util.ArrayList;

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

    /** Cached visible option list: rebuilt only when the style or the debug master switch flips. */
    private ArrayList<Setting> visibleSettings;
    private int visibleKey = -1;

    public static ButtonSetting moduleToggles;
    public static ButtonSetting showLogo;
    public static ButtonSetting frostedGlass;
    public static ButtonSetting scaffoldBlocks;
    public static ButtonSetting editPosition;
    public static ButtonSetting nowPlaying;
    public static ButtonSetting bedProgress;
    public static ButtonSetting gappleProgress;
    public static ButtonSetting velocityDelay;
    public static ButtonSetting flyBps;
    public static ButtonSetting debug;
    public static ButtonSetting debugMove;
    public static ButtonSetting debugLanding;
    public static ButtonSetting debugAttack;
    public static ButtonSetting debugHurt;
    public static ButtonSetting infoPanel;
    public static ButtonSetting infoFps;
    public static ButtonSetting infoPing;
    public static ButtonSetting infoServer;

    public static ButtonSetting tablist;
    public static ButtonSetting tablistFrostedGlass;
    public static ButtonSetting tablistServerText;
    public static ButtonSetting tablistShowPing;

    /** Island style switch ({@code Island}); the Samsara style has no options of its own. */
    public static SliderSetting islandStyle;
    /** Where the drag editor parks the Samsara pill; deliberately not shown as rows. */
    public static SliderSetting samsaraX;
    public static SliderSetting samsaraY;

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
        islandStyle = slider("Island");
        samsaraX = slider("Samsara: Position X");
        samsaraY = slider("Samsara: Position Y");
        moduleToggles = button("Module toggles");
        showLogo = button("Logo");
        frostedGlass = button("Frosted glass");
        scaffoldBlocks = button("Scaffold blocks");
        editPosition = button("Edit position");
        nowPlaying = button("Now playing");
        bedProgress = button("Bed progress");
        gappleProgress = button("Gapple progress");
        velocityDelay = button("Velocity delay");
        flyBps = button("Fly bps");
        debug = button("Debug");
        debugMove = button("Debug: Move");
        debugLanding = button("Debug: Landing");
        debugAttack = button("Debug: Attack");
        debugHurt = button("Debug: Hurt");
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
        for (Setting setting : instance.settings) {
            if (name.equals(setting.getName())) {
                return setting instanceof ButtonSetting ? (ButtonSetting) setting : null;
            }
        }
        throw new IllegalStateException("Watermark has no setting " + name);
    }

    /**
     * Looks the row up in the full list, not {@link #getSettings()}: the style switch hides rows
     * ({@code Samsara: Position X/Y}) that {@code link()} still has to bind.
     */
    private static SliderSetting slider(String name) {
        for (Setting setting : instance.settings) {
            if (name.equals(setting.getName())) {
                return setting instanceof SliderSetting ? (SliderSetting) setting : null;
            }
        }
        throw new IllegalStateException("Watermark has no setting " + name);
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
                NowPlaying.setEnabled(instance.isEnabled() && instance.nowPlaying() || NotificationsModule.needsMedia());
                // The island draws vanilla FontRenderer text (the tab list's server header/footer),
                // and RenderTick listeners are not inside the per-frame font scope.
                renderHud(Overlay.canRender(), Overlay.canRender());
            }
        };
        MinecraftForge.EVENT_BUS.register(listener);
    }

    /**
     * Draws the island and/or the top-left info pill.
     *
     * <p>Also called from inside the morphing ClickGUI / music window ({@code HudPass}) so the
     * watermark does not blink out for the 280-420ms the shell takes to grow; the island artwork
     * itself is skipped there because the screen already draws the island as the morph endpoint.</p>
     */
    public static void renderHud(boolean island, boolean infoBar) {
        if (instance == null) {
            return;
        }
        xyz.daikirai.gui.onyx.render.GuiFont.begin();
        try {
            if (infoBar) {
                INFO_BAR.render(instance);
            }
            if (island) {
                ISLAND.render(instance, false);
            }
        } finally {
            xyz.daikirai.gui.onyx.render.GuiFont.end();
        }
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
        // The Samsara island has no chest card, so containers keep their vanilla screen.
        return !samsaraMode() && chestStealerEnabled() && mc.theWorld != null && !mc.gameSettings.hideGUI
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

    // ------------------------------------------------------------------ //
    // Island style (NeoRaven / Samsara)
    // ------------------------------------------------------------------ //

    /** Order matches the {@code Island} mode options in {@code modules.json}. */
    public static final int STYLE_NEORAVEN = 0;
    public static final int STYLE_SAMSARA = 1;

    public static int islandStyleIndex() {
        return islandStyle == null ? STYLE_NEORAVEN : Math.round((float) islandStyle.getInput());
    }

    /** True while the Samsara island owns the HUD; {@link Island} then delegates to it. */
    public static boolean samsaraMode() {
        return islandStyleIndex() == STYLE_SAMSARA;
    }

    public float samsaraPositionX() {
        return samsaraX == null ? 50.0F : (float) samsaraX.getInput();
    }

    public float samsaraPositionY() {
        return samsaraY == null ? 0.0F : (float) samsaraY.getInput();
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

    public boolean debug() {
        return debug != null && debug.isToggled();
    }

    public boolean debugMove() {
        return debugMove == null || debugMove.isToggled();
    }

    public boolean debugLanding() {
        return debugLanding == null || debugLanding.isToggled();
    }

    public boolean debugAttack() {
        return debugAttack == null || debugAttack.isToggled();
    }

    public boolean debugHurt() {
        return debugHurt == null || debugHurt.isToggled();
    }

    /**
     * Hides the per-feature debug switches while {@code Debug} itself is off, so the page only grows
     * the extra rows after the master switch is turned on. {@code DetailPage} rebuilds its rows as
     * soon as the list reference sequence changes, so this applies without reopening the GUI.
     *
     * <p>The same mechanism swaps the option page between the two island styles: upstream Samsara's
     * island has no settings at all, so {@code Island = Samsara} replaces every NeoRaven island row
     * (logo, frosted glass, gauges, music, tab list, debug rows, position, scale) with nothing and
     * leaves only the style switch and the shared "Edit position" button. The Samsara position sliders
     * stay in the profile but are never listed: the drag editor writes them.</p>
     */
    @Override
    public ArrayList<Setting> getSettings() {
        int key = (samsaraMode() ? 1 : 0) | (debug() ? 2 : 0);
        if (visibleSettings == null || key != visibleKey) {
            visibleKey = key;
            visibleSettings = buildVisible(key);
        }
        return visibleSettings;
    }

    private ArrayList<Setting> buildVisible(int key) {
        boolean samsara = (key & 1) != 0;
        boolean debugRows = (key & 2) != 0;
        ArrayList<Setting> visible = new ArrayList<Setting>(this.settings.size());
        for (Setting setting : this.settings) {
            String name = setting.getName();
            if (isSharedIslandSetting(name)) {
                visible.add(setting);
                continue;
            }
            if (samsara) {
                // The Samsara island exposes no options; only the rows above survive.
                continue;
            }
            if (isHiddenSamsaraSetting(name)) {
                continue;
            }
            if (isDebugSubSetting(name) && !debugRows) {
                continue;
            }
            visible.add(setting);
        }
        return visible;
    }

    /** Rows both styles keep: the style switch itself and the shared drag editor. */
    private static boolean isSharedIslandSetting(String name) {
        return "Island".equals(name) || "Edit position".equals(name);
    }

    /** Written by the drag editor only; the Samsara island deliberately has no option rows. */
    private static boolean isHiddenSamsaraSetting(String name) {
        return name != null && name.startsWith("Samsara:");
    }

    /** Hidden rows still belong to the profile, so they keep their toggled state across restarts. */
    @Override
    public ArrayList<Setting> persistedSettings() {
        return this.settings;
    }

    private static boolean isDebugSubSetting(String name) {
        return "Debug: Move".equals(name) || "Debug: Landing".equals(name)
                || "Debug: Attack".equals(name) || "Debug: Hurt".equals(name);
    }

    public boolean flyBps() {
        return flyBps == null || flyBps.isToggled();
    }

    /** True when the island is the one showing the Fly speed, so the module skips its own readout. */
    public static boolean islandOwnsFlyBps() {
        WatermarkModule module = instance;
        return module != null && module.isEnabled() && module.flyBps();
    }

    public boolean infoPanel() {
        // The top-left pill is a NeoRaven island option; its row is hidden in the Samsara style.
        return !samsaraMode() && (infoPanel == null || infoPanel.isToggled());
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

    /** Scale of the island surface; the Samsara style has no scale option (fixed 1x). */
    public float islandScale() {
        if (samsaraMode()) {
            return 1.0F;
        }
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

    /** Screen percentage of the island, per style: the Samsara style has its own X/Y rows. */
    public float positionX() {
        if (samsaraMode()) {
            return samsaraPositionX();
        }
        return positionX == null ? 50.0F : (float) positionX.getInput();
    }

    public float positionY() {
        if (samsaraMode()) {
            return samsaraPositionY();
        }
        return positionY == null ? 0.0F : (float) positionY.getInput();
    }

    public float lowBlockCount() {
        return lowBlocks == null ? 32.0F : (float) lowBlocks.getInput();
    }

    public void setPosition(float percentX, float percentY) {
        if (samsaraMode()) {
            if (samsaraX != null) {
                samsaraX.setValue(percentX);
            }
            if (samsaraY != null) {
                samsaraY.setValue(percentY);
            }
            return;
        }
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
        // The Samsara island has no tab list panel, so the vanilla list must keep drawing.
        return !samsaraMode() && instance != null && instance.isEnabled()
                && tablist != null && tablist.isToggled() && Overlay.canRender();
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
