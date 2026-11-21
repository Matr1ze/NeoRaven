package xyz.daikirai.module.render;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.gui.IslandTextEditor;
import xyz.daikirai.gui.ColorEditor;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.JumpCircleRenderer;
import xyz.daikirai.render.furiah.module.landing.FuriahJumpEffects;
import xyz.daikirai.render.furiah.module.landing.LandingStyle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JumpCirclesModule extends Module {
    private static final int FIRST_FURIAH_MODE = 4;
    private static final LandingStyle[] FURIAH_STYLES = LandingStyle.values();
    public static JumpCirclesModule instance;
    private final List<Circle> circles = new ArrayList<Circle>();
    private final Set<Integer> airborne = new HashSet<Integer>();
    private final Map<Integer, Vec3> grounded = new HashMap<Integer, Vec3>();
    private final Map<Integer, Flight> flights = new HashMap<>();
    private final JumpCircleRenderer renderer = new JumpCircleRenderer();
    private FuriahJumpEffects furiah;
    private WorldClient world;
    private int activeMode;
    private SliderSetting mode, radius, opacity, duration, spin, textSize, blur, warp, furiahTrigger;
    private ButtonSetting others, throughWalls, distortion, colorButton, textButton;
    private final ColorSetting color = new ColorSetting("Color", -10178561);
    private String text = "ONYX";

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
        furiahTrigger = slider("Furiah trigger");
        int colorIndex = getSettings().indexOf(button("Color"));
        getSettings().set(colorIndex, color);
        colorButton = color;
        textButton = button("Text");
    }

    @Override
    public ArrayList<Setting> getSettings() {
        if (textButton == null) return super.getSettings();
        ArrayList<Setting> visible = new ArrayList<>(super.getSettings());
        if (furiahStyle() == null) {
            visible.remove(furiahTrigger);
        } else {
            visible.remove(spin);
            visible.remove(textButton);
            visible.remove(textSize);
            visible.remove(distortion);
            visible.remove(blur);
            visible.remove(warp);
        }
        return visible;
    }

    @Override
    public ArrayList<Setting> persistedSettings() {
        return super.getSettings();
    }

    private SliderSetting slider(String name) { return (SliderSetting) ModuleManager.setting(this, name); }
    private ButtonSetting button(String name) { return (ButtonSetting) ModuleManager.setting(this, name); }

    @Override
    public void guiButtonToggled(ButtonSetting setting) {
        if (setting == colorButton) mc.displayGuiScreen(new ColorEditor(mc.currentScreen, color));
        else if (setting == textButton) {
            mc.displayGuiScreen(new IslandTextEditor(mc.currentScreen, text(), value -> {
                if (!value.equals(text())) {
                    setText(value);
                    Profiles.markModified();
                }
            }));
        }
    }

    public int color() { return color.color(); }
    public ColorSetting colorSetting() { return color; }
    public String text() { return text; }
    public void appearance(int color, String text) {
        this.color.setColor(color);
        setText(text);
    }
    public void setText(String text) {
        this.text = text == null ? "ONYX" : text.substring(0, Math.min(text.length(), 64));
    }

    public float radius() { return (float) radius.getInput(); }
    public float opacity() { return (float) opacity.getInput() / 100.0F; }
    public long lifetime() { return (long) (duration.getInput() * 1000); }
    public boolean textMode() { return mode.getInput() == 1; }
    public boolean shockwave() { return mode.getInput() == 2; }
    public boolean tileWave() { return mode.getInput() == 3; }
    public boolean landingMode() { return shockwave() || tileWave(); }
    public float spin() { return (float) spin.getInput(); }
    public float textSize() { return (float) textSize.getInput(); }
    public boolean throughWalls() { return throughWalls.isToggled(); }
    public boolean distortion() { return distortion.isToggled(); }
    public float blur() { return (float) blur.getInput() / 100.0F; }
    public float warp() { return (float) warp.getInput() / 100.0F; }

    private LandingStyle furiahStyle() {
        int index = (int) mode.getInput() - FIRST_FURIAH_MODE;
        return index >= 0 && index < FURIAH_STYLES.length ? FURIAH_STYLES[index] : null;
    }

    private void syncMode() {
        int selected = (int) mode.getInput();
        if (selected != activeMode) {
            if (selected >= FIRST_FURIAH_MODE || activeMode >= FIRST_FURIAH_MODE) clearEffects();
            activeMode = selected;
        }
    }

    private void clear() {
        clearEffects();
        grounded.clear();
        airborne.clear();
        world = mc.theWorld;
    }

    private void clearEffects() {
        circles.clear();
        flights.clear();
        if (furiah != null) furiah.clear();
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
        syncMode();
        LandingStyle style = furiahStyle();
        long now = System.currentTimeMillis();
        prune(now);
        Set<Integer> tracked = new HashSet<Integer>();
        for (EntityPlayer player : world.playerEntities) {
            if (player.isDead || !player.isEntityAlive() || player != mc.thePlayer && !others.isToggled()) continue;
            int id = player.getEntityId();
            tracked.add(id);
            Vec3 current = new Vec3(player.posX, player.posY, player.posZ);
            if (player.onGround) {
                if (airborne.remove(id)) {
                    if (style == null && landingMode()) circles.add(new Circle(current, now));
                    Flight flight = flights.remove(id);
                    if (style != null && furiahTrigger.getInput() != 0 && flight != null && flight.ticks >= 4) {
                        spawn(style, player, current, false, (float) Math.max(0, flight.highestY - player.posY));
                    }
                }
                grounded.put(id, current);
            } else {
                airborne.add(id);
                Vec3 position = grounded.remove(id);
                if (style != null && furiahTrigger.getInput() != 0) {
                    Flight flight = flights.computeIfAbsent(id, ignored -> new Flight());
                    flight.highestY = Math.max(flight.highestY, position == null ? player.posY : Math.max(player.posY, position.yCoord));
                    flight.ticks++;
                }
                if (position == null || (player.motionY <= 0 && player.posY <= position.yCoord + 0.01)) continue;
                if (style != null && furiahTrigger.getInput() != 1) spawn(style, player, position, true, 0);
                else if (style == null && !landingMode()) circles.add(new Circle(position, now));
            }
        }
        grounded.keySet().retainAll(tracked);
        airborne.retainAll(tracked);
        flights.keySet().retainAll(tracked);
    }

    private void spawn(LandingStyle style, EntityPlayer player, Vec3 position, boolean jump, float fallHeight) {
        if (furiah == null) furiah = new FuriahJumpEffects();
        furiah.spawn(style, player, position, jump, fallHeight, this);
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        if (world != mc.theWorld) clear();
        if (world == null || mc.thePlayer == null) return;
        syncMode();
        if (furiahStyle() != null) {
            if (furiah != null) furiah.render(this, event.partialTicks);
            return;
        }
        long now = System.currentTimeMillis();
        prune(now);
        renderer.render(circles, this, event.partialTicks, now);
    }

    private static final class Flight {
        private double highestY = Double.NEGATIVE_INFINITY;
        private int ticks;
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
