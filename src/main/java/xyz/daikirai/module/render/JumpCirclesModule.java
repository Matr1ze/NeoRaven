package xyz.daikirai.module.render;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.gui.JumpCircleEditor;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.JumpCircleRenderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JumpCirclesModule extends Module {
    public static JumpCirclesModule instance;
    private final List<Circle> circles = new ArrayList<Circle>();
    private final Map<Integer, Vec3> grounded = new HashMap<Integer, Vec3>();
    private final JumpCircleRenderer renderer = new JumpCircleRenderer();
    private WorldClient world;
    private SliderSetting mode, radius, opacity, duration, spin, textSize, blur, warp;
    private ButtonSetting others, throughWalls, distortion, colorButton, textButton;
    private final ColorSetting color = new ColorSetting("Color", -10178561);
    private String text = "NeoRaven";

    public JumpCirclesModule(int key) {
        super("JumpCircles", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        mode = slider("Mode");
        radius = slider("Radius");
        opacity = slider("Opacity");
        duration = slider("Duration");
        spin = slider("Spin speed");
        textSize = slider("Text size");
        blur = slider("Distortion: Blur");
        warp = slider("Distortion: Warp");
        others = button("Other players");
        throughWalls = button("Through walls");
        distortion = button("Distortion");
        int colorIndex = getSettings().indexOf(button("Color"));
        getSettings().set(colorIndex, color);
        colorButton = color;
        textButton = button("Text");
    }

    private SliderSetting slider(String name) { return (SliderSetting) ModuleManager.setting(this, name); }
    private ButtonSetting button(String name) { return (ButtonSetting) ModuleManager.setting(this, name); }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == colorButton) mc.displayGuiScreen(new ColorEditor(mc.currentScreen, color));
        else if (setting == textButton) mc.displayGuiScreen(new JumpCircleEditor(mc.currentScreen, this, true));
    }

    public int color() { return color.color(); }
    public ColorSetting colorSetting() { return color; }
    public String text() { return text; }
    public void appearance(int color, String text) {
        this.color.setColor(color);
        setText(text);
    }
    public void setText(String text) {
        this.text = text == null ? "NeoRaven" : text.substring(0, Math.min(text.length(), 64));
    }

    public float radius() { return (float) radius.getInput(); }
    public float opacity() { return (float) opacity.getInput() / 100.0F; }
    public long lifetime() { return (long) (duration.getInput() * 1000); }
    public boolean textMode() { return mode.getInput() >= 1; }
    public float spin() { return (float) spin.getInput(); }
    public float textSize() { return (float) textSize.getInput(); }
    public boolean throughWalls() { return throughWalls.isToggled(); }
    public boolean distortion() { return distortion.isToggled(); }
    public float blur() { return (float) blur.getInput() / 100.0F; }
    public float warp() { return (float) warp.getInput() / 100.0F; }

    private void clear() {
        circles.clear();
        grounded.clear();
        world = mc.theWorld;
    }

    @Override
    public void onEnable() { clear(); renderer.reset(); }

    @Override
    public void onDisable() { clear(); renderer.dispose(); }

    private void prune(long now) { circles.removeIf(circle -> now - circle.time >= lifetime()); }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != mc.theWorld) clear();
        if (world == null || mc.thePlayer == null) { clear(); return; }
        long now = System.currentTimeMillis();
        prune(now);
        Set<Integer> tracked = new HashSet<Integer>();
        for (EntityPlayer player : world.playerEntities) {
            if (player.isDead || !player.isEntityAlive() || player != mc.thePlayer && !others.isToggled()) continue;
            int id = player.getEntityId();
            tracked.add(id);
            if (player.onGround) {
                grounded.put(id, new Vec3(player.posX, player.posY, player.posZ));
            } else {
                Vec3 position = grounded.remove(id);
                if (position != null && (player.motionY > 0 || player.posY > position.yCoord + 0.01))
                    circles.add(new Circle(position, now));
            }
        }
        grounded.keySet().retainAll(tracked);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (world != mc.theWorld) clear();
        if (world == null || mc.thePlayer == null) return;
        long now = System.currentTimeMillis();
        prune(now);
        renderer.render(circles, this, event.partialTicks, now);
    }

    public static final class Circle {
        public final Vec3 position;
        public final long time;
        public Circle(Vec3 position, long time) { this.position = position; this.time = time; }
        public float progress(long now, long lifetime) {
            return Math.max(0, Math.min(1, (float) (now - time) / lifetime));
        }
        public float radius(float maximum, long now, long lifetime) {
            return maximum * (1 - (float) Math.pow(1 - progress(now, lifetime), 4));
        }
    }
}
