package xyz.daikirai.module.render;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WingShape;
import xyz.daikirai.render.WorldRenderState;
import xyz.daikirai.render.XYRecord;

public final class WingsModule extends VisualModule {
    private final SliderSetting type = mode("Wing type", 0, "Angelic", "Dragon", "Butterfly", "Phoenix", "Crystal", "Mechanical", "Fairy", "Demon");
    private final SliderSetting fill = mode("Fill", 1, "Solid", "Shader");
    private final ButtonSetting self = toggle("Targets: Self", true);
    private final ButtonSetting players = toggle("Targets: Players", false);
    private final SliderSetting scale = slider("Scale", 1, .3, 3, .1, "x");
    private final SliderSetting angle = slider("Angle", 20, 0, 90, 1, " deg");
    private final SliderSetting height = slider("Height", 1.5, .8, 2.5, .05, "");
    private final SliderSetting depth = slider("Depth", .15, 0, .5, .01, "");
    private final ButtonSetting flapping = toggle("Flapping", true);
    private final SliderSetting strength = slider("Flap strength", 30, 5, 60, 1, "");
    private final SliderSetting speed = slider("Flap speed", 3, .5, 8, .5, "");
    private final ButtonSetting throughWalls = toggle("Through walls", false);
    private final ColorSetting color = color("Color", -1);
    private final SliderSetting opacity = slider("Opacity", 100, 5, 100, 1, "%");
    private Shader aurora;

    public WingsModule(int key) { super("Wings", key); }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) return;
        Vec3 origin = WorldRenderState.camera(event.partialTicks);
        try (WorldRenderState state = new WorldRenderState(throughWalls.isToggled())) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (!player.isEntityAlive() || player.isInWater()) continue;
                if (player == mc.thePlayer ? !self.isToggled() || mc.gameSettings.thirdPersonView == 0 : !players.isToggled()) continue;
                drawPlayer(player, origin, event.partialTicks);
            }
        }
    }

    private void drawPlayer(EntityPlayer player, Vec3 origin, float partial) {
        Vec3 p = WorldRenderState.position(player, partial).subtract(origin);
        float yaw = player.prevRenderYawOffset + MathHelper.wrapAngleTo180_float(player.renderYawOffset - player.prevRenderYawOffset) * partial;
        boolean sneak = player.isSneaking();
        double flap = flapping.isToggled() ? Math.sin((player.ticksExisted + partial) * .12 * speed.getInput() / 3) * 4.5 * strength.getInput() / 30 : 0;
        double motion = Math.min(1, Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ) * 10);
        double open = flap + motion * .18 - angle.getInput();
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(p.xCoord, p.yCoord, p.zCoord);
            GL11.glRotatef(180 - yaw, 0, 1, 0);
            if (sneak) GL11.glRotatef(18, 1, 0, 0);
            GL11.glTranslated(0, (sneak ? .96 : 1.38) + height.getInput() - 1.5, .1 + depth.getInput() - .15);
            GL11.glScaled(scale.getInput(), scale.getInput(), scale.getInput());
            for (int side : new int[]{-1, 1}) {
                GL11.glPushMatrix();
                GL11.glTranslated(side * .06, 0, .02);
                GL11.glRotated(side * open, 0, 1, 0);
                GL11.glRotatef(side * -11, 0, 0, 1);
                GL11.glRotatef(-4, 1, 0, 0);
                drawWing(side);
                GL11.glPopMatrix();
            }
        } finally { GL11.glPopMatrix(); }
    }

    private void drawWing(int side) {
        WingShape shape = WingShape.values()[(int) type.getInput()];
        XYRecord[] vertices = shape.getXYRecordArray();
        int base = color.color();
        double alpha = opacity.getInput() / 100;
        membrane(vertices, side, 1.22, WorldRenderState.mix(base, -1, .28), alpha * .22, 0);
        membrane(vertices, side, .84, WorldRenderState.mix(base, -1, .55), alpha * .26, 0);
        if (fill.getInput() == 1) {
            if (aurora == null) aurora = Shader.load("onyx_wings_aurora");
            if (aurora.valid()) {
                aurora.use();
                aurora.uniform1f("uTime", (System.nanoTime() % 180000000000L) / 1E9F);
                aurora.uniform2f("uResolution", mc.displayWidth, mc.displayHeight);
            }
        }
        membrane(vertices, side, 1, base, alpha, alpha * .45);
        Shader.unbind();
        GL11.glLineWidth(2.2F);
        WorldRenderState.color(base, alpha * .95);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        for (XYRecord vertex : vertices) GL11.glVertex3f(side * vertex.x(), vertex.y(), 0);
        GL11.glEnd();
        GL11.glBegin(GL11.GL_LINES);
        for (int index : shape.getIntArray()) {
            if (index >= vertices.length) continue;
            XYRecord vertex = vertices[index];
            WorldRenderState.color(WorldRenderState.mix(base, -1, .28), Math.max(8 / 255.0 * alpha, alpha * .15));
            GL11.glVertex3f(0, 0, 0);
            WorldRenderState.color(WorldRenderState.mix(base, -1, .28), alpha * .2 * vertex.alphaMul());
            GL11.glVertex3f(side * vertex.x() * .96F, vertex.y() * .96F, 0);
        }
        GL11.glEnd();
    }

    private static void membrane(XYRecord[] vertices, int side, double size, int color, double root, double edge) {
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < vertices.length; i++) {
            WorldRenderState.color(color, root);
            GL11.glVertex3f(0, 0, 0);
            for (XYRecord vertex : new XYRecord[]{vertices[i], vertices[(i + 1) % vertices.length]}) {
                WorldRenderState.color(color, edge * vertex.alphaMul());
                GL11.glVertex3d(side * vertex.x() * size, vertex.y() * size, 0);
            }
        }
        GL11.glEnd();
    }
}
