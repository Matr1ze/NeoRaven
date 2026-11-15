package xyz.daikirai.uitest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.Language;
import net.minecraft.util.ScreenShotHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.menu.*;
import xyz.daikirai.gui.onyx.OnyxClickGuiModule;
import xyz.daikirai.util.Lang;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** In-process integration test: real Forge routing, mixins, resources and framebuffer rendering. */
@Mod(modid = "neoraven_ui_test", name = "NeoRaven UI test", version = "1", dependencies = "after:clickgui")
public final class AndroidUiSmoke {
    private final List<Runnable> steps = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private int stage = -1, frames;
    private boolean started;
    private final Minecraft mc = Minecraft.getMinecraft();
    private GuiScreen home;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        if (Boolean.getBoolean("neoraven.uiSmoke")) MinecraftForge.EVENT_BUS.register(this);
    }
    private void add(String name, Runnable run) { names.add(name); steps.add(run); }
    private void show(GuiScreen screen, Class<?> expected) {
        mc.displayGuiScreen(screen);
        if (!expected.isInstance(mc.currentScreen)) throw new AssertionError("Expected " + expected + ", got " + mc.currentScreen);
    }
    private void setup() {
        MenuStyle.select(MenuStyle.ANDROID);
        mc.gameSettings.guiScale = 2;
        MenuTheme.setDark(false);
        home = new AndroidMainMenu();
        add("01-settings", () -> show(new GuiOptions(home, mc.gameSettings), AndroidSettingsScreen.class));
        add("02-language", () -> show(new GuiLanguage(home, mc.gameSettings, mc.getLanguageManager()), AndroidLanguageScreen.class));
        add("03-language-zh", () -> {
            AndroidLanguageScreen screen = (AndroidLanguageScreen) mc.currentScreen;
            int index = 0;
            for (Language l : mc.getLanguageManager().getLanguages()) {
                if (l.getLanguageCode().equals("zh_CN")) break;
                index++;
            }
            invoke(screen, "activate", new Class[]{int.class}, index);
            if (!"zh_CN".equals(mc.gameSettings.language)) throw new AssertionError("Language selection failed");
        });
        add("04-controls", () -> {
            if (!Lang.chinese() || OnyxClickGuiModule.language.getInput() != 0) throw new AssertionError("Client language failed to follow");
            show(new GuiControls(home, mc.gameSettings), AndroidControlsScreen.class);
        });
        add("05-skin", () -> show(new GuiCustomizeSkin(home), AndroidSkinScreen.class));
        add("05b-skin-toggle", () -> {
            net.minecraft.entity.player.EnumPlayerModelParts part = net.minecraft.entity.player.EnumPlayerModelParts.values()[0];
            boolean before = mc.gameSettings.getModelParts().contains(part);
            invoke(mc.currentScreen, "activate", new Class[]{int.class}, 0);
            if (mc.gameSettings.getModelParts().contains(part) == before) throw new AssertionError("Skin toggle did not change");
            invoke(mc.currentScreen, "activate", new Class[]{int.class}, 0);
        });
        add("06-resource-packs", () -> show(new GuiScreenResourcePacks(home), GuiScreenResourcePacks.class));
        add("07-create-world", () -> show(new GuiCreateWorld(home), GuiCreateWorld.class));
        add("07b-world-name-input", () -> {
            invoke(mc.currentScreen, "keyTyped", new Class[]{char.class, int.class}, 'X', org.lwjgl.input.Keyboard.KEY_X);
            try {
                boolean found = false;
                for (java.lang.reflect.Field f : GuiCreateWorld.class.getDeclaredFields()) {
                    if (f.getType() == GuiTextField.class) {
                        f.setAccessible(true); GuiTextField field = (GuiTextField) f.get(mc.currentScreen);
                        if (field != null && field.getText().contains("X")) found = true;
                    }
                }
                if (!found) throw new AssertionError("World name input failed");
            } catch (IllegalAccessException e) { throw new RuntimeException(e); }
        });
        add("08-video-options", () -> show(new GuiVideoSettings(home, mc.gameSettings), GuiVideoSettings.class));
        add("09-sound-options", () -> show(new GuiScreenOptionsSounds(home, mc.gameSettings), GuiScreenOptionsSounds.class));
        add("10-confirm", () -> show(new GuiYesNo((answer, id) -> {}, "NeoRaven", "MD3 dialog / 确认对话框", 1), GuiYesNo.class));
        add("11-forge-mods", () -> show(new net.minecraftforge.fml.client.GuiModList(home), net.minecraftforge.fml.client.GuiModList.class));
        add("11b-flat-world", () -> show(new GuiCreateFlatWorld(new GuiCreateWorld(home), ""), GuiCreateFlatWorld.class));
        add("11c-flat-presets", () -> show(new GuiFlatPresets((GuiCreateFlatWorld) mc.currentScreen), GuiFlatPresets.class));
        add("11d-custom-world", () -> show(new GuiCustomizeWorldScreen(new GuiCreateWorld(home), ""), GuiCustomizeWorldScreen.class));
        add("11d-slider-values", () -> {
            final float[] result = {0};
            GuiPageButtonList.GuiResponder responder = new GuiPageButtonList.GuiResponder() {
                public void func_175321_a(int id, boolean value) { }
                public void onTick(int id, float value) { result[0] = value; }
                public void func_175319_a(int id, String value) { }
            };
            GuiSlider slider = new GuiSlider(responder, 1, 0, 0, "test", 10, 110, 10, null);
            slider.isMouseDown = true;
            invoke(slider, "mouseDragged", new Class[]{Minecraft.class, int.class, int.class}, mc, 75, 10);
            if (Math.abs(result[0] - 60) > 1) throw new AssertionError("Slider range lost: " + result[0]);
            slider.isMouseDown = false;
        });
        add("11e-controls-input", () -> {
            show(new GuiControls(home, mc.gameSettings), AndroidControlsScreen.class);
            net.minecraft.client.settings.KeyBinding[] keys = mc.gameSettings.keyBindings.clone();
            java.util.Arrays.sort(keys);
            int before = keys[0].getKeyCode();
            invoke(mc.currentScreen, "activate", new Class[]{int.class}, 0);
            invoke(mc.currentScreen, "keyTyped", new Class[]{char.class, int.class}, 'p', org.lwjgl.input.Keyboard.KEY_P);
            if (keys[0].getKeyCode() != org.lwjgl.input.Keyboard.KEY_P) throw new AssertionError("Key assignment failed");
            invoke(mc.currentScreen, "activate", new Class[]{int.class}, 0);
            invoke(mc.currentScreen, "mouseClicked", new Class[]{int.class, int.class, int.class}, 0, 0, 1);
            if (keys[0].getKeyCode() != -99) throw new AssertionError("Mouse assignment failed");
            keys[0].setKeyCode(before);
            net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
            mc.gameSettings.saveOptions();
        });
        add("11f-return-parent", () -> {
            GuiScreen preferences = new AndroidSettingsScreen(home);
            show(new GuiLanguage(preferences, mc.gameSettings, mc.getLanguageManager()), AndroidLanguageScreen.class);
            invoke(mc.currentScreen, "keyTyped", new Class[]{char.class, int.class}, '\0', org.lwjgl.input.Keyboard.KEY_ESCAPE);
            if (mc.currentScreen != preferences) throw new AssertionError("Language lost its parent");
        });
        add("12-vanilla-settings", () -> {
            MenuStyle.select(MenuStyle.MINECRAFT);
            show(new GuiOptions(new GuiMainMenu(), mc.gameSettings), GuiOptions.class);
            if (AndroidUi.active()) throw new AssertionError("Vanilla style not restored");
        });
        add("13-vanilla-language", () -> show(new GuiLanguage(home, mc.gameSettings, mc.getLanguageManager()), GuiLanguage.class));
        add("14-dark-settings", () -> {
            MenuStyle.select(MenuStyle.ANDROID);
            MenuTheme.setDark(true);
            show(new GuiOptions(home, mc.gameSettings), AndroidSettingsScreen.class);
        });
        add("15-dark-packs", () -> show(new GuiScreenResourcePacks(home), GuiScreenResourcePacks.class));
        add("16-dark-create", () -> show(new GuiCreateWorld(home), GuiCreateWorld.class));
    }
    private static void invoke(Object target, String method, Class<?>[] types, Object... args) {
        try {
            Class<?> cls = target.getClass();
            Method found = null;
            while (cls != null) {
                try { found = cls.getDeclaredMethod(method, types); break; }
                catch (NoSuchMethodException e) { cls = cls.getSuperclass(); }
            }
            if (found == null) throw new NoSuchMethodException(method);
            found.setAccessible(true); found.invoke(target, args);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || mc.currentScreen == null) return;
        try {
            if (!started) { setup(); started = true; }
            if (++frames < 50) return;
            frames = 0;
            if (stage >= 0) {
                int error = GL11.glGetError();
                if (error != GL11.GL_NO_ERROR) throw new AssertionError("GL error " + error + " on " + names.get(stage));
                GlStateManager.bindTexture(0);
                ScreenShotHelper.saveScreenshot(new File("."), names.get(stage) + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
                System.out.println("[MD3 TEST] PASS " + names.get(stage));
            }
            if (++stage == steps.size()) {
                java.nio.file.Files.write(new File("PASS.txt").toPath(), ("Passed " + stage + " real rendered screens\n").getBytes("UTF-8"));
                MinecraftForge.EVENT_BUS.unregister(this);
                mc.shutdown(); return;
            }
            steps.get(stage).run();
        } catch (Throwable e) {
            e.printStackTrace();
            try { java.nio.file.Files.write(new File("FAIL.txt").toPath(), e.toString().getBytes("UTF-8")); } catch (Exception ignored) { }
            MinecraftForge.EVENT_BUS.unregister(this);
            mc.shutdown();
        }
    }
}
