package xyz.daikirai.module.render;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Typeface;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fireball impact prediction, ported from FireballPredict 2.5 ({@code com.naix.predict}).
 *
 * <p>Every second tick it looks for an {@link EntityFireball} (wither skulls excluded) and traces its
 * flight direction 500 blocks ahead; the nearest impact point to the player wins. As a second mode it
 * also traces the look ray of any player holding a fire charge, so the block a throw would land on is
 * marked before it leaves the hand. The estimated time of arrival comes from the remaining distance
 * and the fireball's speed.</p>
 *
 * <p>The landing point is drawn as a single block overlay in the same recipe as {@code BlockOverlay}:
 * theme-coloured fill plus an outline, instead of the upstream 5x5x5 translucent blast box. Its colour
 * follows the Material theme by default ({@code Color} is a palette row), and the blast radius a
 * fireball would cover is no longer painted at all - the marked block is the impact itself.</p>
 */
public final class FireballPredictModule extends VisualModule {

    /** Upstream warnings trigger inside the 5x5x5 box around the impact, i.e. 2.5 blocks per axis. */
    private static final double DEFAULT_WARN_RANGE = 2.5D;
    /** Chat warnings are rate limited to twice per second, like upstream's every-10-ticks message. */
    private static final long WARN_INTERVAL_MS = 500L;

    private final ButtonSetting fireballs = toggle("Fireballs", true);
    private final ButtonSetting heldCharge = toggle("Held fire charge", true);
    private final ButtonSetting trajectory = toggle("Trajectory", true);
    private final ButtonSetting eta = toggle("ETA", true);
    private final ButtonSetting warning = toggle("Chat warning", false);
    private final SliderSetting warnRange = slider("Warn range", DEFAULT_WARN_RANGE, 1.0D, 6.0D, 0.5D, " m");

    private final ColorSetting color = color("Color", 0xFFFFFF);
    private final SliderSetting blastRadius = slider("Blast radius", 2, 0, 3, 1, " b");
    private final SliderSetting fillOpacity = slider("Fill opacity", 22, 1, 80, 1, "%");
    private final SliderSetting outlineOpacity = slider("Outline opacity", 90, 5, 100, 5, "%");
    private final SliderSetting lineWidth = slider("Line width", 1.5D, 0.5D, 5.0D, 0.5D, " px");
    private final SliderSetting padding = slider("Padding", 0.6D, 0.0D, 5.0D, 0.1D, "%");
    private final ButtonSetting throughWalls = toggle("Through walls", true);

    private final Anim alpha = new Anim(0.0F);

    private BlockPos hit;
    private EntityFireball tracked;
    private Vec3 origin;
    private double travelSeconds = -1.0D;
    private int ticks;
    private long lastFrame;
    private long lastWarn;

    public FireballPredictModule(int key) {
        super("Fireball Predict", key);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        hit = null;
        tracked = null;
        origin = null;
        travelSeconds = -1.0D;
        ticks = 0;
        lastFrame = 0L;
        alpha.set(0.0F);
    }

    // ------------------------------------------------------------------ //
    // Prediction
    // ------------------------------------------------------------------ //

    @Override
    public void onUpdate() {
        // Upstream sampled every other client tick; the 10 Hz cadence is plenty for a 1.5 b/tick
        // fireball and keeps the entity/ray-trace walk off most ticks.
        if (++ticks % 2 != 0) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.theWorld;
        if (world == null || mc.thePlayer == null) {
            hit = null;
            tracked = null;
            origin = null;
            travelSeconds = -1.0D;
            return;
        }
        BlockPos best = null;
        EntityFireball bestFireball = null;
        double bestDistance = Double.MAX_VALUE;
        double seconds = -1.0D;
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        if (fireballs.isToggled()) {
            for (Entity entity : world.loadedEntityList) {
                if (!(entity instanceof EntityFireball) || entity instanceof EntityWitherSkull) {
                    continue;
                }
                EntityFireball fireball = (EntityFireball) entity;
                double speedSq = fireball.motionX * fireball.motionX
                        + fireball.motionY * fireball.motionY
                        + fireball.motionZ * fireball.motionZ;
                if (speedSq < 1.0E-4D) {
                    continue;
                }
                Vec3 start = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
                Vec3 dir = new Vec3(fireball.motionX, fireball.motionY, fireball.motionZ).normalize();
                MovingObjectPosition trace = world.rayTraceBlocks(start,
                        start.addVector(dir.xCoord * 500.0D, dir.yCoord * 500.0D, dir.zCoord * 500.0D));
                if (trace == null || trace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                    continue;
                }
                BlockPos pos = trace.getBlockPos();
                Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
                double distance = playerPos.squareDistanceTo(center);
                if (distance >= bestDistance) {
                    continue;
                }
                bestDistance = distance;
                best = pos;
                bestFireball = fireball;
                seconds = start.distanceTo(center) / (Math.sqrt(speedSq) * 20.0D);
            }
        }

        if (best == null && heldCharge.isToggled()) {
            for (EntityPlayer player : world.playerEntities) {
                if (player.getHeldItem() == null || player.getHeldItem().getItem() != Items.fire_charge) {
                    continue;
                }
                Vec3 eye = player.getPositionEyes(1.0F);
                Vec3 look = player.getLook(1.0F);
                MovingObjectPosition trace = world.rayTraceBlocks(eye,
                        eye.addVector(look.xCoord * 500.0D, look.yCoord * 500.0D, look.zCoord * 500.0D));
                if (trace != null && trace.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    best = trace.getBlockPos();
                    break;
                }
            }
        }

        // The trajectory line starts where the tracked fireball was first seen, not where it is now.
        if (bestFireball == null) {
            origin = null;
        } else if (bestFireball != tracked) {
            origin = new Vec3(bestFireball.posX, bestFireball.posY, bestFireball.posZ);
        }
        tracked = bestFireball;
        hit = best;
        travelSeconds = bestFireball == null ? -1.0D : seconds;

        // Upstream warns while the player stands in the 5x5x5 box around the impact; the thrown
        // charge mode never warned there, so neither do we.
        if (warning.isToggled() && best != null && bestFireball != null) {
            warn(mc, best);
        }
    }

    private void warn(Minecraft mc, BlockPos pos) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < WARN_INTERVAL_MS) {
            return;
        }
        double range = warnRange.getInput();
        double dx = Math.abs(mc.thePlayer.posX - (pos.getX() + 0.5D));
        double dy = Math.abs(mc.thePlayer.posY - (pos.getY() + 0.5D));
        double dz = Math.abs(mc.thePlayer.posZ - (pos.getZ() + 0.5D));
        if (dx > range || dy > range || dz > range) {
            return;
        }
        lastWarn = now;
        mc.thePlayer.addChatMessage(new ChatComponentText("§cFireball impact nearby!"));
    }

    // ------------------------------------------------------------------ //
    // Rendering (blast area, BlockOverlay recipe)
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) {
            return;
        }
        long now = System.nanoTime();
        float delta = lastFrame == 0L ? 0.0F : Math.min(100.0F, (now - lastFrame) / 1.0E6F);
        lastFrame = now;
        boolean active = hit != null;
        alpha.to(active ? 1.0F : 0.0F, active ? 90.0F : 140.0F, active ? Ease.EMPHASIZED : Ease.LINEAR);
        alpha.update(delta);
        if (hit == null || alpha.value() <= 0.004F) {
            return;
        }

        BlockPos target = hit;
        Vec3 camera = WorldRenderState.camera(event.partialTicks);
        float fade = alpha.value();
        List<AxisAlignedBB> overlays = blastArea(mc, target, camera);
        try (WorldRenderState state = new WorldRenderState(throughWalls.isToggled())) {
            if (!overlays.isEmpty()) {
                // Same fill + outline as BlockOverlay, applied to every solid block of the blast
                // area: one batched fill pass, then one batched outline pass.
                WorldRenderState.color(color.color(), fade * fillOpacity.getInput() / 100.0D);
                GL11.glBegin(GL11.GL_QUADS);
                for (AxisAlignedBB box : overlays) {
                    quad(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                }
                GL11.glEnd();
                WorldRenderState.color(color.color(), fade * outlineOpacity.getInput() / 100.0D);
                GL11.glLineWidth((float) lineWidth.getInput());
                GL11.glBegin(GL11.GL_LINES);
                for (AxisAlignedBB box : overlays) {
                    edges(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
                }
                GL11.glEnd();
            }
            if (trajectory.isToggled() && origin != null) {
                WorldRenderState.color(0xFFFFFF, fade * 0.75D);
                GL11.glLineWidth(2.5F);
                GL11.glBegin(GL11.GL_LINES);
                GL11.glVertex3d(origin.xCoord - camera.xCoord, origin.yCoord - camera.yCoord,
                        origin.zCoord - camera.zCoord);
                GL11.glVertex3d(target.getX() + 0.5D - camera.xCoord, target.getY() + 0.5D - camera.yCoord,
                        target.getZ() + 0.5D - camera.zCoord);
                GL11.glEnd();
            }
        }

        if (eta.isToggled() && travelSeconds >= 0.0D) {
            drawEta(mc, event.partialTicks, target, camera, fade);
        }
    }

    /**
     * Collects one camera-relative box per solid block of the blast area, keeping upstream's
     * footprint (a {@code 2r+1} cube around the impact, {@code r = 2} by default) and its
     * "full cubes only" filter. Blocks buried inside the solid mass are skipped the same way
     * upstream culled their faces: their overlay could not be seen, and skipping them keeps the
     * draw lists short.
     */
    private List<AxisAlignedBB> blastArea(Minecraft mc, BlockPos target, Vec3 camera) {
        List<AxisAlignedBB> boxes = new ArrayList<AxisAlignedBB>();
        int radius = (int) Math.round(blastRadius.getInput());
        double pad = padding.getInput() / 100.0D;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = target.add(dx, dy, dz);
                    if (mc.theWorld.isAirBlock(pos)) {
                        continue;
                    }
                    IBlockState state = mc.theWorld.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!block.isFullCube()) {
                        continue;
                    }
                    if (buried(mc, pos)) {
                        continue;
                    }
                    boxes.add(new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D)
                            .expand(pad, pad, pad)
                            .offset(-camera.xCoord, -camera.yCoord, -camera.zCoord));
                }
            }
        }
        return boxes;
    }

    /** True when every neighbour is an opaque full cube, i.e. the block is inside the solid mass. */
    private static boolean buried(Minecraft mc, BlockPos pos) {
        for (EnumFacing facing : EnumFacing.values()) {
            BlockPos neighbour = pos.offset(facing);
            // Air reports isFullCube() = true on the base Block class, so it has to be excluded
            // explicitly - upstream did the same with `world.isAirBlock`.
            if (mc.theWorld.isAirBlock(neighbour)) {
                return false;
            }
            Block block = mc.theWorld.getBlockState(neighbour).getBlock();
            if (!block.isOpaqueCube() && !block.isFullCube()) {
                return false;
            }
        }
        return true;
    }

    /** The six faces of one box, in the order {@code WorldRenderState.box} would emit them. */
    private static void quad(double x0, double y0, double z0, double x1, double y1, double z1) {
        // bottom
        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y0, z1);
        GL11.glVertex3d(x1, y0, z1); GL11.glVertex3d(x1, y0, z0);
        // top
        GL11.glVertex3d(x0, y1, z0); GL11.glVertex3d(x1, y1, z0);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x0, y1, z1);
        // north (-Z)
        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x1, y0, z0);
        GL11.glVertex3d(x1, y1, z0); GL11.glVertex3d(x0, y1, z0);
        // south (+Z)
        GL11.glVertex3d(x0, y0, z1); GL11.glVertex3d(x0, y1, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y0, z1);
        // west (-X)
        GL11.glVertex3d(x0, y0, z0); GL11.glVertex3d(x0, y1, z0);
        GL11.glVertex3d(x0, y1, z1); GL11.glVertex3d(x0, y0, z1);
        // east (+X)
        GL11.glVertex3d(x1, y0, z0); GL11.glVertex3d(x1, y0, z1);
        GL11.glVertex3d(x1, y1, z1); GL11.glVertex3d(x1, y1, z0);
    }

    /** The twelve edges of one box. */
    private static void edges(double x0, double y0, double z0, double x1, double y1, double z1) {
        line(x0, y0, z0, x1, y0, z0); line(x1, y0, z0, x1, y0, z1);
        line(x1, y0, z1, x0, y0, z1); line(x0, y0, z1, x0, y0, z0);
        line(x0, y1, z0, x1, y1, z0); line(x1, y1, z0, x1, y1, z1);
        line(x1, y1, z1, x0, y1, z1); line(x0, y1, z1, x0, y1, z0);
        line(x0, y0, z0, x0, y1, z0); line(x1, y0, z0, x1, y1, z0);
        line(x1, y0, z1, x1, y1, z1); line(x0, y0, z1, x0, y1, z1);
    }

    private static void line(double x0, double y0, double z0, double x1, double y1, double z1) {
        GL11.glVertex3d(x0, y0, z0);
        GL11.glVertex3d(x1, y1, z1);
    }

    /**
     * Draws the countdown above the impact block. The projection is done with the world matrices
     * pushed aside, and both matrices are restored afterwards so later world renderers (the 3D
     * lyrics listen at {@code LOWEST}) still see the camera transform.
     */
    private void drawEta(Minecraft mc, float partialTicks, BlockPos target, Vec3 camera, float fade) {
        ScaledResolution resolution = new ScaledResolution(mc);
        int factor = resolution.getScaleFactor();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        float screenX;
        float screenY;
        try {
            GlStateManager.translate(-camera.xCoord, -camera.yCoord, -camera.zCoord);
            FloatBuffer modelview = org.lwjgl.BufferUtils.createFloatBuffer(16);
            FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
            IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
            FloatBuffer screen = org.lwjgl.BufferUtils.createFloatBuffer(3);
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            if (!GLU.gluProject((float) (target.getX() + 0.5D), (float) (target.getY() + 1.4D),
                    (float) (target.getZ() + 0.5D), modelview, projection, viewport, screen)) {
                return;
            }
            if (screen.get(2) < 0.0F || screen.get(2) > 1.0F) {
                return;
            }
            screenX = screen.get(0) / factor;
            screenY = (viewport.get(3) - screen.get(1)) / factor - 6.0F;
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
        }

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0D, resolution.getScaledWidth(), resolution.getScaledHeight(), 0.0D, -1.0D, 1.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableFog();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            String text = String.format(Locale.ROOT, "%.1fs", travelSeconds);
            Typeface.Style style = Typeface.CAPTION;
            float width = style.width(text);
            int alphaByte = Math.round(Math.max(0.0F, Math.min(1.0F, fade)) * 255.0F) << 24;
            int shadow = alphaByte;
            int ink = (color.color() & 0xFFFFFF) | alphaByte;
            GlStateManager.pushMatrix();
            GlStateManager.translate(screenX, screenY, 0.0F);
            GlStateManager.scale(1.35F, 1.35F, 1.0F);
            // Four-way black outline first, then the ink, like upstream's ETA label.
            for (int ox = -1; ox <= 1; ox += 2) {
                for (int oy = -1; oy <= 1; oy += 2) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translate(-width / 2.0F + ox, oy, 0.0F);
                    style.draw(text, shadow);
                    GlStateManager.popMatrix();
                }
            }
            GlStateManager.pushMatrix();
            GlStateManager.translate(-width / 2.0F, 0.0F, 0.0F);
            style.draw(text, ink);
            GlStateManager.popMatrix();
            GlStateManager.popMatrix();
        } finally {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
