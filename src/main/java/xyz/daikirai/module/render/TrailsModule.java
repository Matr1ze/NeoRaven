package xyz.daikirai.module.render;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;

public final class TrailsModule extends VisualModule {
    private final SliderSetting mode = mode("Mode", 0, "Wall", "Points");
    private final ColorSetting color = color("Color", -10178561);
    private final SliderSetting length = slider("Length", 1, .25, 5, .05, " s");
    private final SliderSetting opacity = slider("Opacity", 60, 5, 100, 5, "%");
    private final SliderSetting lineWidth = slider("Line width", 1.5, .5, 5, .5, " px");
    private final SliderSetting pointSize = slider("Point size", 4, 1, 12, .5, " px");
    private final ButtonSetting firstPerson = toggle("First person", false);
    private final ButtonSetting throughWalls = toggle("Through walls", false);
    private final List<Sample> samples = new ArrayList<Sample>();
    private WorldClient world;

    public TrailsModule(int key) { super("Trails", key); }
    private void clear() { samples.clear(); world = mc.theWorld; }
    @Override public void onEnable() { clear(); }
    @Override public void onDisable() { clear(); }

    private void prune(long now) { samples.removeIf(sample -> now - sample.time >= length.getInput() * 1000); }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != mc.theWorld || mc.thePlayer == null) clear();
        if (world == null || mc.thePlayer == null) return;
        long now = System.currentTimeMillis();
        prune(now);
        Vec3 p = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        if (samples.isEmpty() || samples.get(samples.size() - 1).position.squareDistanceTo(p) > 1E-8)
            samples.add(new Sample(p, now));
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if (world != mc.theWorld) clear();
        if (mc.thePlayer == null || world == null || mc.getRenderViewEntity() == null) return;
        long now = System.currentTimeMillis();
        prune(now);
        if (samples.isEmpty() || mc.gameSettings.thirdPersonView == 0 && !firstPerson.isToggled()) return;
        List<Sample> path = new ArrayList<Sample>(samples);
        path.add(new Sample(WorldRenderState.position(mc.thePlayer, event.partialTicks), now));
        Vec3 origin = WorldRenderState.camera(event.partialTicks);
        double h = mc.thePlayer.height * (mc.thePlayer.isSneaking() ? .8 : 1);
        try (WorldRenderState state = new WorldRenderState(throughWalls.isToggled())) {
            GL11.glTranslated(-origin.xCoord, -origin.yCoord, -origin.zCoord);
            if (mode.getInput() == 1) {
                GL11.glEnable(GL11.GL_POINT_SMOOTH);
                GL11.glPointSize((float) pointSize.getInput());
                GL11.glBegin(GL11.GL_POINTS);
                for (Sample sample : path) vertex(sample, .8, now, true);
                GL11.glEnd();
            } else {
                GL11.glBegin(GL11.GL_QUADS);
                for (int i = 1; i < path.size(); i++) {
                    Sample a = path.get(i - 1), b = path.get(i);
                    vertex(a, 0, now, false); vertex(a, h, now, false);
                    vertex(b, h, now, false); vertex(b, 0, now, false);
                }
                GL11.glEnd();
                GL11.glLineWidth((float) lineWidth.getInput());
                for (double y : new double[]{.004, h - .004}) {
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    for (Sample sample : path) vertex(sample, y, now, true);
                    GL11.glEnd();
                }
            }
        }
    }

    private void vertex(Sample s, double y, long now, boolean edge) {
        double remaining = Math.max(0, 1 - (now - s.time) / (length.getInput() * 1000));
        WorldRenderState.color(color.color(), Math.min(1, opacity.getInput() / 100 * (edge ? 2.5 : 1)) * remaining);
        GL11.glVertex3d(s.position.xCoord, s.position.yCoord + y, s.position.zCoord);
    }

    private static final class Sample {
        final Vec3 position;
        final long time;
        Sample(Vec3 position, long time) { this.position = position; this.time = time; }
    }
}
