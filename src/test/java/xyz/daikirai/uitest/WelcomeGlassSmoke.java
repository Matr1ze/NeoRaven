package xyz.daikirai.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.FirstRun;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.gui.onyx.menu.WelcomeScreen;
import xyz.daikirai.gui.onyx.render.Glass;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;

/**
 * Test-only mod: drives the real first-run welcome flow (fresh game directory, no setup marker) and
 * checks that the glass page's choices survive {@code finish()} into the ClickGUI settings, the live
 * theme and the persisted preferences.
 */
@Mod(modid = "neoraven_welcome_test", name = "NeoRaven welcome test", version = "1", dependencies = "after:clickgui")
public final class WelcomeGlassSmoke {

    private static final long GAP = 700_000_000L;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final StringBuilder report = new StringBuilder();
    private GuiScreen welcome;
    private int stage;
    private int retries;
    private long next;
    private long startedAt;
    private long worldAt;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("neoraven.welcomeSmoke")) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || System.nanoTime() < next) {
            return;
        }
        try {
            step();
        } catch (Throwable failure) {
            failure.printStackTrace();
            finishTest(false, "FAILED: " + failure);
        }
    }

    private void step() throws Exception {
        long now = System.nanoTime();
        switch (stage) {
            case 0: {
                if (startedAt == 0L) {
                    startedAt = now;
                }
                if (Minecraft.getMinecraft().currentScreen instanceof WelcomeScreen) {
                    welcome = Minecraft.getMinecraft().currentScreen;
                    log("welcome shown; FirstRun.pending=" + FirstRun.pending());
                    screenshot("01-welcome");
                    log("welcome state: " + welcomeState());
                    stage = 1;
                    next = now + GAP;
                    return;
                }
                if (now - startedAt > 180_000_000_000L) {
                    throw new AssertionError("welcome never appeared; screen=" + simple(mc.currentScreen));
                }
                next = now + 50_000_000L;
                return;
            }
            case 1:
                clickNext();
                stage = 2;
                next = now + GAP;
                return;
            case 2:
                require(screenStep() == 1, "expected theme page, step=" + screenStep());
                screenshot("02-theme-page");
                clickNext();
                stage = 3;
                next = now + GAP;
                return;
            case 3:
                require(screenStep() == 2, "expected glass page, step=" + screenStep());
                screenshot("03-glass-page");
                log("glass page state: " + welcomeState());
                require(!truthy(field("frostOn")), "seeded preferences did not start with frosted glass off");
                require(!truthy(field("liquidOn")), "seeded preferences did not start with liquid glass off");
                clickLiquidToggle();
                log("clicked the liquid glass switch");
                stage = 4;
                next = now + GAP;
                return;
            case 4:
                require(truthy(field("liquidOn")), "liquid glass switch did not turn on");
                require(!truthy(field("frostOn")), "the liquid glass click flipped the frosted switch instead");
                screenshot("04-glass-on");
                stage = 5;
                next = now + GAP;
                return;
            case 5: {
                if (mc.currentScreen instanceof WelcomeScreen) {
                    int step = screenStep();
                    if (step < 5) {
                        clickNext();
                    } else {
                        log("pressing Start");
                        clickNext();
                        stage = 6;
                    }
                    next = now + GAP;
                    return;
                }
                stage = 6;
                next = now + GAP;
                return;
            }
            case 6: {
                require(!(mc.currentScreen instanceof WelcomeScreen), "welcome still open after Start");
                require(!FirstRun.pending(), "FirstRun still pending after Start");
                log("after Start: screen=" + simple(mc.currentScreen));
                log("settings: frosted=" + OnyxClickGuiModule.frostedGlass.isToggled()
                        + " liquid=" + OnyxClickGuiModule.liquidGlass.isToggled()
                        + " frostOpacity=" + OnyxClickGuiModule.frostOpacity.getInput()
                        + " liquidOpacity=" + OnyxClickGuiModule.liquidOpacity.getInput());
                log("theme: frost=" + Theme.frost() + " liquidGlass=" + Theme.liquidGlass()
                        + " mode=" + Glass.mode());
                log("prefs: " + prefs());
                screenshot("05-after-start");
                require(OnyxClickGuiModule.liquidGlass.isToggled(),
                        "the welcome flow did not save the ClickGUI liquid-glass switch");
                require(!OnyxClickGuiModule.frostedGlass.isToggled(),
                        "the welcome flow turned on frosted glass behind the player's back");
                require(Theme.liquidGlass(), "the live theme is not liquid after the welcome flow");
                require(Glass.enabled() && Glass.liquid(), "glass mode is not LIQUID after the welcome flow");
                require(prefs().contains("liquidEnabled=true"), "persisted preferences lost liquidEnabled=true");
                require(prefs().contains("frostEnabled=false"), "persisted preferences changed frosted glass");
                stage = 7;
                next = now + GAP;
                return;
            }
            case 7: {
                // The first in-world client tick runs the default profile load path.
                Profiles.loadDefault();
                OnyxClickGuiModule.sync();
                log("after world-join loadDefault/sync: liquid=" + OnyxClickGuiModule.liquidGlass.isToggled()
                        + " themeLiquid=" + Theme.liquidGlass() + " mode=" + Glass.mode());
                require(OnyxClickGuiModule.liquidGlass.isToggled() && Theme.liquidGlass(),
                        "the world-join path wiped the welcome glass choice");
                // Enter a real world so the in-game HUD glass path runs end to end.
                mc.gameSettings.pauseOnLostFocus = false;
                worldAt = now;
                mc.launchIntegratedServer("welcome-glass-world", "Welcome glass", new WorldSettings(42,
                        WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
                stage = 8;
                next = now + 1_000_000_000L;
                return;
            }
            case 8: {
                if (mc.theWorld == null || mc.thePlayer == null) {
                    if (now - worldAt > 240_000_000_000L) {
                        throw new AssertionError("world did not start");
                    }
                    next = now + 100_000_000L;
                    return;
                }
                mc.displayGuiScreen(null);
                stage = 9;
                retries = 0;
                next = now + 1_000_000_000L;
                return;
            }
            case 9: {
                require(mc.theWorld != null && mc.thePlayer != null, "world disappeared");
                // The first world tick loads the default profile, and a profile load panics every
                // module first; enable a glass HUD after that so the pass has something to draw,
                // then let a few frames render before sampling.
                enableGlassHud();
                if (retries++ < 6) {
                    next = now + 500_000_000L;
                    return;
                }
                boolean ready = Overlay.canRender() && Glass.enabled() && Glass.liquid()
                        && xyz.daikirai.gui.onyx.render.Blur.hudGlassActive()
                        && xyz.daikirai.gui.onyx.render.Blur.ready();
                log("in world: screen=" + simple(mc.currentScreen)
                        + " liquidSetting=" + OnyxClickGuiModule.liquidGlass.isToggled()
                        + " themeLiquid=" + Theme.liquidGlass()
                        + " mode=" + Glass.mode()
                        + " hudGlassActive=" + xyz.daikirai.gui.onyx.render.Blur.hudGlassActive()
                        + " blurReady=" + xyz.daikirai.gui.onyx.render.Blur.ready());
                screenshot("06-in-world-hud");
                require(Overlay.canRender(), "in-world HUD is not renderable");
                require(Theme.liquidGlass() && Glass.mode() == Glass.Mode.LIQUID,
                        "in-world theme lost liquid glass");
                require(ready, "in-world glass HUD never rendered its liquid backdrop");
                finishTest(true, "welcome glass choices reach the ClickGUI, theme and the in-world HUD");
                return;
            }
            default:
                throw new AssertionError("unknown stage " + stage);
        }
    }

    private void enableGlassHud() {
        for (String name : new String[]{"Watermark", "Keybinds", "CustomGui", "Notifications", "TargetHUD"}) {
            xyz.daikirai.module.Module module = xyz.daikirai.module.ModuleManager.get(name);
            if (module != null && !module.isEnabled()) {
                module.enable();
            }
        }
    }

    private void clickNext() throws Exception {
        if (!(mc.currentScreen instanceof WelcomeScreen)) {
            throw new AssertionError("clickNext with no welcome screen");
        }
        click(welcome.width - 24 - 96 + 48, welcome.height - 40 + 12);
    }

    private void clickLiquidToggle() throws Exception {
        float cx = welcome.width / 2F, cy = welcome.height / 2F - 16;
        float rx = cx - 14, ry = cy - 40;
        // Switch index 1 ("Liquid glass") on the glass page.
        click((int) (rx + 108 + 52), (int) (ry + 9));
    }

    private void click(int x, int y) throws Exception {
        Method method = WelcomeScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class);
        method.setAccessible(true);
        method.invoke(welcome, x, y, 0);
        log("click (" + x + ", " + y + ")");
    }

    private int screenStep() throws Exception {
        return (Integer) field("step");
    }

    private String welcomeState() throws Exception {
        return "step=" + field("step") + " frostOn=" + field("frostOn") + " liquidOn=" + field("liquidOn")
                + " coloured=" + field("coloured") + " matteOn=" + field("matteOn")
                + " frost=" + field("frost") + " liquid=" + field("liquid");
    }

    private Object field(String name) throws Exception {
        for (Class<?> type = WelcomeScreen.class; type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(welcome);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private String prefs() {
        try {
            String lines = new String(Files.readAllBytes(new File("keystrokes/glass-theme.properties").toPath()), "UTF-8");
            return lines.replace("\r", "").replace("\n", " | ");
        } catch (Exception e) {
            return "<missing: " + e + ">";
        }
    }

    private void screenshot(String name) {
        try {
            ScreenShotHelper.saveScreenshot(new File("."), name + ".png", mc.displayWidth, mc.displayHeight,
                    mc.getFramebuffer());
            log("screenshot " + name + ".png");
        } catch (Throwable failure) {
            log("screenshot " + name + " failed: " + failure);
        }
    }

    private void log(String line) {
        report.append(line).append('\n');
        System.out.println("[WELCOME TEST] " + line);
    }

    private void finishTest(boolean pass, String message) {
        log((pass ? "PASS: " : "FAIL: ") + message);
        try {
            Files.write(new File(pass ? "PASS.txt" : "FAIL.txt").toPath(),
                    (message + "\n\n" + report).getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
        MinecraftForge.EVENT_BUS.unregister(this);
        Minecraft.getMinecraft().shutdown();
    }

    private static boolean truthy(Object value) {
        return Boolean.TRUE.equals(value);
    }

    private static String simple(GuiScreen screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
