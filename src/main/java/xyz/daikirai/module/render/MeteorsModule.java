package xyz.daikirai.module.render;

import java.util.Random;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;

/** World-space shooting stars with a luminous head and a tapered, camera-facing trail. */
public final class MeteorsModule extends Module {
    private static final int MAX_COUNT = 48;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final int SIDES = 16;
    private static final double[] COS = new double[SIDES + 1];
    private static final double[] SIN = new double[SIDES + 1];
    static {
        for (int i = 0; i <= SIDES; i++) {
            double angle = 2.0 * Math.PI * i / SIDES;
            COS[i] = Math.cos(angle);
            SIN[i] = Math.sin(angle);
        }
    }

    private final Random random = new Random();
    private final Meteor[] meteors = new Meteor[MAX_COUNT];
    private SliderSetting count, size, distance;
    private ColorSetting color;
    private WorldClient world;
    private Vec3 previousCamera;
    private double previousDistance;
    private int activeCount;

    public MeteorsModule(int key) {
        super("Meteors", Category.render, key);
    }

    @Override public void link() {
        count = (SliderSetting) ModuleManager.setting(this, "Meteor count");
        size = (SliderSetting) ModuleManager.setting(this, "Size");
        distance = (SliderSetting) ModuleManager.setting(this, "Distance");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
    }

    @Override public void onDisable() {
        activeCount = 0;
        world = null;
        previousCamera = null;
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world == world) onDisable();
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        Entity viewer = mc.getRenderViewEntity();
        if (viewer == null || mc.theWorld == null) return;
        Vec3 camera = WorldRenderState.camera(event.partialTicks);
        double range = distance.getInput();
        if (world != mc.theWorld || range != previousDistance || previousCamera == null
                || previousCamera.squareDistanceTo(camera) > range * range) {
            activeCount = 0;
        }
        world = mc.theWorld;
        previousCamera = camera;
        previousDistance = range;
        double now = world.getTotalWorldTime() + (double) event.partialTicks;
        int wanted = Math.max(1, Math.min(MAX_COUNT, (int) count.getInput()));
        double radius = size.getInput();
        int rgb = color.color();
        int core = WorldRenderState.mix(rgb, 0xFFFFFFFF, 0.70);
        try (WorldRenderState state = new WorldRenderState(false)) {
            GL11.glBegin(GL11.GL_TRIANGLES);
            try {
                for (int i = 0; i < wanted; i++) {
                    Meteor m = meteors[i];
                    boolean initial = i >= activeCount;
                    if (initial || now < m.born || now >= m.born + m.life) {
                        // Distribute the initial shower around the horizon and stagger its ages.
                        double angle = initial ? Math.toRadians(viewer.rotationYaw) + i * GOLDEN_ANGLE
                                + (random.nextDouble() - 0.5) * 0.4 : random.nextDouble() * Math.PI * 2.0;
                        m = meteors[i] = spawn(camera, viewer.getEyeHeight(), angle, range, now, initial);
                    }
                    m.draw(camera, viewer.getEyeHeight(), now, radius, rgb, core);
                }
            } finally {
                GL11.glEnd();
            }
        }
        activeCount = wanted;
    }

    private Meteor spawn(Vec3 camera, float eyeHeight, double angle, double range, double now, boolean initial) {
        double depth = range * (0.85 + 0.30 * random.nextDouble());
        double altitude = depth * (0.45 + 0.58 * random.nextDouble());
        double travel = depth * (0.52 + 0.46 * random.nextDouble());
        if (random.nextBoolean()) travel = -travel;
        double life = 24.0 + random.nextDouble() * 24.0;
        Vec3 displacement = new Vec3(Math.cos(angle) * travel, -altitude * 0.45, Math.sin(angle) * travel);
        Vec3 start = camera.addVector(-Math.sin(angle) * depth - displacement.xCoord * 0.5,
                eyeHeight + altitude, Math.cos(angle) * depth - displacement.zCoord * 0.5);
        double age = initial ? life * (0.12 + random.nextDouble() * 0.70) : 0.0;
        return new Meteor(start, displacement, now - age, life);
    }

    private static final class Meteor {
        final Vec3 start, path;
        final double born, life;

        Meteor(Vec3 start, Vec3 path, double born, double life) {
            this.start = start;
            this.path = path;
            this.born = born;
            this.life = life;
        }

        void draw(Vec3 camera, float eyeHeight, double now, double radius, int rgb, int core) {
            double t = (now - born) / life;
            double fade = Math.min(1.0, Math.min(t * 9.0, (1.0 - t) * 6.0));
            Vec3 head = start.subtract(camera).addVector(path.xCoord * t, path.yCoord * t, path.zCoord * t);
            Vec3 view = head.addVector(0, -eyeHeight, 0).normalize();
            Vec3 side = view.crossProduct(path).normalize();
            Vec3 up = view.crossProduct(side).normalize();
            double trail = Math.min(t, 0.25 * Math.sqrt(radius / 0.24));
            Vec3 tail = head.addVector(-path.xCoord * trail, -path.yCoord * trail, -path.zCoord * trail);
            for (int sign = -1; sign <= 1; sign += 2) {
                double width = radius * 1.5 * sign;
                WorldRenderState.color(rgb, 0.0);
                GL11.glVertex3d(tail.xCoord, tail.yCoord, tail.zCoord);
                GL11.glVertex3d(head.xCoord + side.xCoord * width, head.yCoord + side.yCoord * width,
                        head.zCoord + side.zCoord * width);
                WorldRenderState.color(rgb, fade);
                GL11.glVertex3d(head.xCoord, head.yCoord, head.zCoord);
            }
            glow(head, side, up, radius * 2.8, rgb, fade * 0.6);
            glow(head, side, up, radius, core, fade);
        }

        private static void glow(Vec3 head, Vec3 side, Vec3 up, double radius, int rgb, double alpha) {
            for (int i = 0; i < SIDES; i++) {
                WorldRenderState.color(rgb, alpha);
                GL11.glVertex3d(head.xCoord, head.yCoord, head.zCoord);
                WorldRenderState.color(rgb, 0.0);
                for (int j = i; j <= i + 1; j++) {
                    double x = radius * COS[j], y = radius * SIN[j];
                    GL11.glVertex3d(head.xCoord + side.xCoord * x + up.xCoord * y,
                            head.yCoord + side.yCoord * x + up.yCoord * y,
                            head.zCoord + side.zCoord * x + up.zCoord * y);
                }
            }
        }
    }
}
