package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.client.Overlay;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.HaloSprites;
import xyz.daikirai.render.WorldRenderState;

import java.util.List;

/**
 * Blue Archive style halo above the player's head, ported from {@code blue-archive-halo}
 * (IzumiiKonata's Opai extension).
 *
 * <p>Each style is a stack of glowing ring sprites: the first layer lies flat over the head, the
 * spiked layers are stacked above it with a fixed spacing and rotated ninety degrees so the whole
 * stack reads as one ring assembly. The assembly floats up and down by 0.1 blocks on a two second
 * ease-in-out-cubic ping-pong, which is upstream's {@code floatingAnimation}.</p>
 *
 * <p>Upstream rendered in third person by default ("Render In First Person" opt-in), let the halo
 * follow the head pitch on demand, and exposed two extra tilt axes; all of that is preserved. The
 * only translation is that this client has to move the matrix to the player itself, because
 * {@code RenderWorldLastEvent} hands over a camera-relative matrix instead of a player-local one.</p>
 */
public final class HaloModule extends VisualModule {

    /** Height of the head anchor above the player's feet, then the configurable spacing on top. */
    private static final double HEAD_HEIGHT = 1.4D;
    /** Upstream's float: 0.1 blocks over two seconds, reversed at both ends. */
    private static final double FLOAT_HEIGHT = 0.1D;
    private static final long FLOAT_MS = 2000L;

    private final SliderSetting style = mode("Style", 0, "Shiroko", "Serika", "Hoshino");
    private final SliderSetting size = slider("Size", 1.5D, 0.1D, 3.0D, 0.1D, "");
    private final SliderSetting spacing = slider("Spacing", 0.65D, 0.5D, 2.0D, 0.05D, "");
    private final SliderSetting xRot = slider("X Rot", 0.0D, -90.0D, 90.0D, 1.0D, " deg");
    private final SliderSetting yRot = slider("Y Rot", 0.0D, -90.0D, 90.0D, 1.0D, " deg");
    private final ButtonSetting inFirstPerson = toggle("Render In First Person", false);
    private final ButtonSetting followPitch = toggle("Follow Pitch", false);

    public HaloModule(int key) {
        super("Halo", key);
    }

    /** Upstream's ping-pong: 0 -> 0.1 -> 0, eased in and out over two seconds each way. */
    private double floatOffset() {
        long period = FLOAT_MS * 2L;
        long phase = System.currentTimeMillis() % period;
        double t = phase < FLOAT_MS ? phase / (double) FLOAT_MS : (period - phase) / (double) FLOAT_MS;
        double eased = t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
        return FLOAT_HEIGHT * eased;
    }

    private String styleName() {
        switch ((int) Math.round(style.getInput())) {
            case 1: return "serika";
            case 2: return "hoshino";
            default: return "shiroko";
        }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null || mc.theWorld == null || !Overlay.canRender()) {
            return;
        }
        if (mc.gameSettings.thirdPersonView == 0 && !inFirstPerson.isToggled()) {
            return;
        }
        String style = styleName();
        List<Integer> layers = HaloSprites.layers(style);
        if (layers.isEmpty()) {
            return;
        }

        float partial = event.partialTicks;
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * partial;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * partial;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partial;
        Vec3 camera = WorldRenderState.camera(partial);
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partial;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partial;
        float width = (float) size.getInput();
        double layerSpacing = HaloSprites.LAYER_SPACING * (width / 1.5D)
                * (style.equals("hoshino") ? HaloSprites.LAYER_SPACING_NARROW / HaloSprites.LAYER_SPACING : 1.0D);

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManagerShim.begin();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x - camera.xCoord, y + HEAD_HEIGHT - camera.yCoord, z - camera.zCoord);
            GL11.glRotated(-yaw, 0.0D, 1.0D, 0.0D);
            if (followPitch.isToggled()) {
                GL11.glRotated(pitch, 1.0D, 0.0D, 0.0D);
            }
            GL11.glRotated(xRot.getInput(), 0.0D, 0.0D, 1.0D);
            GL11.glRotated(yRot.getInput(), 1.0D, 0.0D, 0.0D);
            GL11.glTranslated(0.0D, spacing.getInput(), 0.0D);
            // The halo floats: the same lift upstream applies around the lay-flat rotation.
            GL11.glRotated(-90.0D, 1.0D, 0.0D, 0.0D);
            GL11.glTranslated(0.0D, floatOffset(), 0.0D);
            GL11.glRotated(90.0D, 1.0D, 0.0D, 0.0D);

            for (int texture : layers) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                quad(-width * 0.5F, -width * 0.5F, width, width);
                GL11.glRotated(-90.0D, 1.0D, 0.0D, 0.0D);
                GL11.glTranslated(0.0D, layerSpacing, 0.0D);
                GL11.glRotated(90.0D, 1.0D, 0.0D, 0.0D);
            }
        } finally {
            GL11.glPopMatrix();
            GlStateManagerShim.end();
            GL11.glPopAttrib();
        }
    }

    private static void quad(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2f(x, y + height);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2f(x + width, y + height);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2f(x + width, y);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2f(x, y);
        GL11.glEnd();
    }

    /** Raw state setup/teardown for the sprite pass (attrib push/pop restores it anyway). */
    private static final class GlStateManagerShim {
        static void begin() {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        static void end() {
            GL11.glDepthMask(true);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
