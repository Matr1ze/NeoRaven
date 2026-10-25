package xyz.daikirai.module.render;

import java.awt.Color;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;

public final class ChinaHatModule extends VisualModule {
    private final SliderSetting radius = slider("Radius", .65, .2, 3, .05, "");
    private final SliderSetting height = slider("Height", .35, .05, 1.5, .05, "");
    private final SliderSetting offset = slider("Offset", -.02, -.5, 1.5, .02, "");
    private final SliderSetting tilt = slider("Tilt", 100, 0, 100, 5, "%");
    private final SliderSetting colors = mode("Colors", 0, "Static", "Gradient", "Rainbow");
    private final ColorSetting color = color("Color", -10178561);
    private final ColorSetting second = color("Second color", -4949761, 36);
    private final SliderSetting colorSpeed = slider("Color speed", 40, 0, 360, 5, " deg/s");
    private final SliderSetting opacity = slider("Opacity", 90, 5, 100, 5, "%");
    private final SliderSetting spinSpeed = slider("Spin speed", 65, 0, 360, 5, " deg/s");
    private final ButtonSetting glow = toggle("Glow", true);
    private final ButtonSetting trail = toggle("Trail", true);
    private final SliderSetting trailLength = slider("Trail length", 350, 20, 360, 10, " deg");
    private final ButtonSetting throughWalls = toggle("Through walls", false);
    private long last;
    private double spinPhase, colorPhase;

    public ChinaHatModule(int key) { super("ChinaHat", key); }
    @Override public void onEnable() { last = 0; spinPhase = colorPhase = 0; }
    @Override public void onDisable() { last = 0; spinPhase = colorPhase = 0; }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        EntityPlayer player = mc.thePlayer;
        if (mc.theWorld == null || player == null || mc.getRenderViewEntity() == null) { last = 0; return; }
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(.25, (now - last) / 1E9);
        last = now;
        spinPhase = (spinPhase + Math.toRadians(spinSpeed.getInput()) * dt) % (Math.PI * 2);
        colorPhase = (colorPhase + Math.toRadians(colorSpeed.getInput()) * dt) % (Math.PI * 2);
        if (mc.gameSettings.thirdPersonView == 0 || !player.isEntityAlive()) return;
        Vec3 p = WorldRenderState.position(player, event.partialTicks).subtract(WorldRenderState.camera(event.partialTicks));
        float yaw = player.prevRotationYawHead + MathHelper.wrapAngleTo180_float(player.rotationYawHead - player.prevRotationYawHead) * event.partialTicks;
        float pitch = player.prevRotationPitch + MathHelper.wrapAngleTo180_float(player.rotationPitch - player.prevRotationPitch) * event.partialTicks;
        try (WorldRenderState state = new WorldRenderState(throughWalls.isToggled())) {
            GL11.glTranslated(p.xCoord, p.yCoord + 1.5 - (player.isSneaking() ? .22 : 0), p.zCoord);
            GL11.glRotatef(-yaw, 0, 1, 0);
            GL11.glRotated(pitch * tilt.getInput() / 100, 1, 0, 0);
            GL11.glTranslated(0, player.height + offset.getInput() - 1.5, 0);
            surface(false);
            if (trail.isToggled()) surface(true);
            if (glow.isToggled()) halo();
            GL11.glLineWidth(2);
            GL11.glBegin(GL11.GL_LINE_STRIP);
            for (int i = 0; i <= 48; i++) vertex(i * Math.PI / 24, 0, 1, 0, 1, false);
            GL11.glEnd();
            if (trail.isToggled()) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (int band = 0; band <= 8; band++) vertex(spinPhase, band / 8.0, 1.01, .012, 1, false);
                GL11.glEnd();
            }
        }
    }

    private void surface(boolean wake) {
        double span = Math.toRadians(trailLength.getInput());
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 48; i++) {
            double a = wake ? spinPhase - span * i / 48 : i * Math.PI / 24;
            double b = wake ? spinPhase - span * (i + 1) / 48 : (i + 1) * Math.PI / 24;
            double alphaA = wake ? smooth(1 - i / 48.0) * .8 : .7;
            double alphaB = wake ? smooth(1 - (i + 1) / 48.0) * .8 : .7;
            for (int band = 0; band < 8; band++) {
                double t = band / 8.0, next = (band + 1) / 8.0;
                double scale = wake ? 1.01 : 1, lift = wake ? .012 : 0;
                vertex(a, t, scale, lift, alphaA, wake);
                vertex(a, next, scale, lift, alphaA, wake);
                vertex(b, next, scale, lift, alphaB, wake);
                vertex(b, t, scale, lift, alphaB, wake);
            }
        }
        GL11.glEnd();
    }

    private void halo() {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24, b = (i + 1) * Math.PI / 24;
            vertex(a, 0, 1, 0, .22, false); vertex(b, 0, 1, 0, .22, false);
            vertex(b, 0, 1.07, 0, 0, false); vertex(a, 0, 1.07, 0, 0, false);
            vertex(a, 0, 1, 0, .22, false); vertex(b, 0, 1, 0, .22, false);
            vertex(b, .14, 1, 0, 0, false); vertex(a, .14, 1, 0, 0, false);
        }
        GL11.glEnd();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    private int angularColor(double angle) {
        if (colors.getInput() == 1) return WorldRenderState.mix(color.color(), second.color(), .5 - .5 * Math.cos(angle + colorPhase));
        if (colors.getInput() == 2) {
            int c = color.color();
            float[] hsv = Color.RGBtoHSB(c >> 16 & 255, c >> 8 & 255, c & 255, null);
            return Color.HSBtoRGB(hsv[0] + (float)((angle + colorPhase) / (Math.PI * 2)), hsv[1], hsv[2]);
        }
        return color.color();
    }

    private void vertex(double angle, double t, double scale, double lift, double alpha, boolean wake) {
        int c = angularColor(angle);
        if (wake) c = WorldRenderState.mix(c, -1, .35 * alpha / .8);
        WorldRenderState.color(c, opacity.getInput() / 100 * alpha);
        double r = radius.getInput() * (1 - t) * scale;
        double y = height.getInput() * (Math.pow(t, 1.35) + .12 * Math.pow(1 - t, 5) + lift);
        GL11.glVertex3d(r * Math.cos(angle), y, r * Math.sin(angle));
    }
}
