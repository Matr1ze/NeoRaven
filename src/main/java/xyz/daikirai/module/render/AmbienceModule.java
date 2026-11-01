package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

/**
 * Ambience — port of Moonlight's {@code Atmosphere} module ({@code wtf.moonlight.module.impl.visual}),
 * whose second mode is labelled "Ambience (Xylitol)" in its GUI.
 *
 * <p>Two modes, exactly like upstream:</p>
 * <ul>
 *     <li><b>Default</b> — pin the client-side time and/or weather.</li>
 *     <li><b>Ambience</b> — a static time or a running day/night cycle, plus a rain strength.</li>
 * </ul>
 * <p>While either time editor is active the server's {@code S03PacketTimeUpdate} is swallowed, which
 * is what keeps the server from snapping the client clock back.</p>
 */
public final class AmbienceModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static AmbienceModule instance;

    public static SliderSetting mode;
    public static ButtonSetting timeEditor;
    public static SliderSetting time;
    public static ButtonSetting weatherEditor;
    public static SliderSetting weather;
    public static SliderSetting timeMode;
    public static SliderSetting staticTime;
    public static SliderSetting cycleSpeed;
    public static ButtonSetting reverseCycle;
    public static SliderSetting weatherMode;
    public static SliderSetting rainStrength;

    private int timeCycle;

    public AmbienceModule(int key) {
        super("Ambience", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        mode = (SliderSetting) ModuleManager.setting(this, "Mode");
        timeEditor = (ButtonSetting) ModuleManager.setting(this, "Time editor");
        time = (SliderSetting) ModuleManager.setting(this, "Time");
        weatherEditor = (ButtonSetting) ModuleManager.setting(this, "Weather editor");
        weather = (SliderSetting) ModuleManager.setting(this, "Weather");
        timeMode = (SliderSetting) ModuleManager.setting(this, "Time mode");
        staticTime = (SliderSetting) ModuleManager.setting(this, "Static time");
        cycleSpeed = (SliderSetting) ModuleManager.setting(this, "Cycle speed");
        reverseCycle = (ButtonSetting) ModuleManager.setting(this, "Reverse cycle");
        weatherMode = (SliderSetting) ModuleManager.setting(this, "Weather mode");
        rainStrength = (SliderSetting) ModuleManager.setting(this, "Rain strength");
    }

    private static boolean xylitol() {
        return mode != null && Math.round(mode.getInput()) == 1;
    }

    @Override
    public String getInfo() {
        return xylitol() ? "Ambience" : "Default";
    }

    @Override
    public void onEnable() {
        timeCycle = 0;
    }

    @Override
    public void onDisable() {
        timeCycle = 0;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled() || mc.theWorld == null) {
            return;
        }
        if (xylitol()) {
            if (Math.round(timeMode.getInput()) == 0) {
                mc.theWorld.setWorldTime((long) staticTime.getInput());
            } else {
                mc.theWorld.setWorldTime(timeCycle);
                float speed = (float) cycleSpeed.getInput() * 10.0F;
                if (reverseCycle != null && reverseCycle.isToggled()) {
                    speed = -speed;
                }
                timeCycle += (int) speed;
                if (timeCycle > 24000) {
                    timeCycle = 0;
                } else if (timeCycle < 0) {
                    timeCycle = 24000;
                }
            }
            if (Math.round(weatherMode.getInput()) == 0) {
                mc.theWorld.setRainStrength(0.0F);
            } else {
                mc.theWorld.setRainStrength((float) rainStrength.getInput());
            }
            return;
        }
        if (timeEditor != null && timeEditor.isToggled()) {
            mc.theWorld.setWorldTime((long) time.getInput());
        }
        if (weatherEditor != null && weatherEditor.isToggled()) {
            int index = (int) Math.round(weather.getInput());
            if (index == 1) {
                mc.theWorld.setRainStrength(1.0F);
                mc.theWorld.setThunderStrength(0.0F);
            } else if (index == 2) {
                mc.theWorld.setRainStrength(1.0F);
                mc.theWorld.setThunderStrength(1.0F);
            } else {
                mc.theWorld.setRainStrength(0.0F);
                mc.theWorld.setThunderStrength(0.0F);
            }
        }
    }

    /** Keeps the server clock from overwriting the edited time. */
    @Override
    public boolean onReceivePacket(Packet packet, INetHandler handler) {
        if (!isEnabled() || !(packet instanceof S03PacketTimeUpdate)) {
            return true;
        }
        if (xylitol() || (timeEditor != null && timeEditor.isToggled())) {
            return false;
        }
        return true;
    }
}
