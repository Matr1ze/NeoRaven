package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.Render3D;

/**
 * ESP — the three Onyx see-through renderers merged into one module.
 *
 * <p>Onyx ships them separately ({@code BoxesModule}, {@code GlowESPModule}, {@code SkeletonESPModule},
 * all obfuscated with {@code ValueSettingSub*} plumbing); this port keeps the same option surface in
 * one place: outline/filled boxes, a soft glow shell and a skeleton, each independently toggleable and
 * sharing the target filter and colour.</p>
 *
 * <p>Drawing uses this client's {@link Render3D} primitives: boxes are camera-relative
 * {@code filledBox} calls plus immediate-mode line edges, the "glow" is a stack of expanded, low-alpha
 * shells (1.8.9 has no post-process outline pass), and the skeleton is a set of GL lines between the
 * body joints.</p>
 */
public final class EspModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public static EspModule instance;

    public static ButtonSetting boxes;
    public static ButtonSetting shape;
    public static ButtonSetting boxStyle;
    public static SliderSetting fillOpacity;
    public static SliderSetting lineWidth;
    public static ButtonSetting glow;
    public static SliderSetting glowWidth;
    public static SliderSetting glowOpacity;
    public static ButtonSetting skeleton;
    public static SliderSetting skeletonWidth;
    public static ButtonSetting players;
    public static ButtonSetting mobs;
    public static ButtonSetting animals;
    public static ButtonSetting self;
    public static ColorSetting color;

    public EspModule(int key) {
        super("ESP", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        boxes = (ButtonSetting) ModuleManager.setting(this, "Boxes");
        shape = (ButtonSetting) ModuleManager.setting(this, "Shape 3D");
        boxStyle = (ButtonSetting) ModuleManager.setting(this, "Filled");
        fillOpacity = (SliderSetting) ModuleManager.setting(this, "Fill opacity");
        lineWidth = (SliderSetting) ModuleManager.setting(this, "Line width");
        glow = (ButtonSetting) ModuleManager.setting(this, "Glow");
        glowWidth = (SliderSetting) ModuleManager.setting(this, "Glow width");
        glowOpacity = (SliderSetting) ModuleManager.setting(this, "Glow opacity");
        skeleton = (ButtonSetting) ModuleManager.setting(this, "Skeleton");
        skeletonWidth = (SliderSetting) ModuleManager.setting(this, "Skeleton width");
        players = (ButtonSetting) ModuleManager.setting(this, "Players");
        mobs = (ButtonSetting) ModuleManager.setting(this, "Mobs");
        animals = (ButtonSetting) ModuleManager.setting(this, "Animals");
        self = (ButtonSetting) ModuleManager.setting(this, "Self box");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
    }

    @Override
    public String getInfo() {
        int parts = 0;
        if (boxes != null && boxes.isToggled()) {
            ++parts;
        }
        if (glow != null && glow.isToggled()) {
            ++parts;
        }
        if (skeleton != null && skeleton.isToggled()) {
            ++parts;
        }
        return parts == 0 ? null : parts + " on";
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        RenderManager manager = mc.getRenderManager();
        // The colour row opens the palette; its "Follow theme" / "Custom" choice is the only switch.
        int rgb = color == null ? Theme.scheme().primary : color.color();
        float fill = fillOpacity == null ? 0.18F : (float) (fillOpacity.getInput() / 100.0);
        float width = lineWidth == null ? 1.5F : (float) lineWidth.getInput();
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!accepted(entity)) {
                continue;
            }
            AxisAlignedBB box = entity.getEntityBoundingBox();
            double minX = box.minX - manager.viewerPosX;
            double minY = box.minY - manager.viewerPosY;
            double minZ = box.minZ - manager.viewerPosZ;
            double maxX = box.maxX - manager.viewerPosX;
            double maxY = box.maxY - manager.viewerPosY;
            double maxZ = box.maxZ - manager.viewerPosZ;
            AxisAlignedBB relative = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
            if (boxes != null && boxes.isToggled()) {
                drawBoxes(relative, rgb, fill, width);
            }
            if (glow != null && glow.isToggled()) {
                drawGlow(relative, rgb);
            }
            if (skeleton != null && skeleton.isToggled()) {
                drawSkeleton(entity, minX, minY, minZ, maxX, maxY, maxZ, rgb);
            }
        }
    }

    /** 3D box: edges plus an optional translucent fill (2D mode keeps only the edges). */
    private void drawBoxes(AxisAlignedBB box, int rgb, float fill, float width) {
        boolean filled = boxStyle != null && boxStyle.isToggled();
        boolean threeD = shape == null || shape.isToggled();
        if (filled && threeD) {
            Render3D.filledBox(box, ((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                    (rgb & 255) / 255.0F, fill);
        }
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(width);
        GL11.glColor4f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                (rgb & 255) / 255.0F, 0.95F);
        GL11.glBegin(GL11.GL_LINES);
        if (threeD) {
            edges(box);
        } else {
            // Flat 2D-style rectangle projected at the entity's feet, like Onyx's TWO_D shape.
            GL11.glVertex3d(box.minX, box.minY, box.minZ);
            GL11.glVertex3d(box.maxX, box.minY, box.minZ);
            GL11.glVertex3d(box.maxX, box.minY, box.minZ);
            GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
            GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
            GL11.glVertex3d(box.minX, box.minY, box.maxZ);
            GL11.glVertex3d(box.minX, box.minY, box.maxZ);
            GL11.glVertex3d(box.minX, box.minY, box.minZ);
        }
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    /** Soft outline: a few expanded shells with a low alpha, cheapest stand-in for a glow pass. */
    private void drawGlow(AxisAlignedBB box, int rgb) {
        float width = glowWidth == null ? 1.2F : (float) (glowWidth.getInput() * 0.01);
        float base = glowOpacity == null ? 0.35F : (float) (glowOpacity.getInput() / 100.0);
        for (int i = 3; i >= 1; --i) {
            double grow = width * i;
            AxisAlignedBB shell = box.expand(grow, grow, grow);
            Render3D.filledBox(shell, ((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                    (rgb & 255) / 255.0F, base / (i * 2.2F));
        }
    }

    /** Skeleton: head, spine, arms and legs as lines between joints. */
    private void drawSkeleton(EntityLivingBase entity, double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ, int rgb) {
        double cx = (minX + maxX) / 2.0D;
        double cz = (minZ + maxZ) / 2.0D;
        double head = maxY - 0.15D;
        double neck = minY + 1.35D;
        double chest = minY + 1.10D;
        double pelvis = minY + 0.72D;
        double shoulder = 0.32D;
        double yaw = Math.toRadians(entity.renderYawOffset);
        double sideX = Math.cos(yaw) * shoulder;
        double sideZ = Math.sin(yaw) * shoulder;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glLineWidth(skeletonWidth == null ? 1.4F : (float) skeletonWidth.getInput());
        GL11.glColor4f(((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                (rgb & 255) / 255.0F, 0.9F);
        GL11.glBegin(GL11.GL_LINES);
        line(cx, head, cz, cx, neck, cz);
        line(cx, neck, cz, cx, pelvis, cz);
        line(cx - sideX, chest, cz - sideZ, cx + sideX, chest, cz + sideZ);
        line(cx - sideX, chest, cz - sideZ, cx - sideX * 1.6D, pelvis + 0.15D, cz - sideZ * 1.6D);
        line(cx + sideX, chest, cz + sideZ, cx + sideX * 1.6D, pelvis + 0.15D, cz + sideZ * 1.6D);
        line(cx - sideX * 0.5D, pelvis, cz - sideZ * 0.5D, cx - sideX * 0.7D, minY, cz - sideZ * 0.7D);
        line(cx + sideX * 0.5D, pelvis, cz + sideZ * 0.5D, cx + sideX * 0.7D, minY, cz + sideZ * 0.7D);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void edges(AxisAlignedBB b) {
        // bottom
        line(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ);
        line(b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ);
        line(b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ);
        line(b.minX, b.minY, b.maxZ, b.minX, b.minY, b.minZ);
        // top
        line(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ);
        line(b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ);
        line(b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ);
        // pillars
        line(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ);
        line(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ);
        line(b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ);
        line(b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ);
    }

    private static void line(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
    }

    private boolean accepted(Entity entity) {
        if (entity == mc.thePlayer) {
            return self != null && self.isToggled();
        }
        if (!(entity instanceof EntityLivingBase) || entity.isDead) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            return players != null && players.isToggled();
        }
        if (entity instanceof EntityMob) {
            return mobs != null && mobs.isToggled();
        }
        if (entity instanceof EntityAnimal) {
            return animals != null && animals.isToggled();
        }
        return false;
    }
}
