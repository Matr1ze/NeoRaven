package xyz.daikirai.module.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.gui.onyx.material.Theme;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.TargetEspRenderer;
import xyz.daikirai.util.Render3D;

/**
 * Target ESP — Onyx's {@code TargetESPModule} ported.
 *
 * <p>Upstream offers two styles. {@code Crystals} is the one that matters visually: a ring of
 * eighteen faceted shards orbiting the target, each with an additive bloom sprite and a short lunge
 * towards the target whenever it takes a hit (see {@link TargetEspRenderer}). {@code Ring} is a soft
 * hoop around the target, and {@code Box} is the translucent bounding box.</p>
 *
 * <p>The target fades in and out over 250ms, exactly like the original's {@code Cls3} helper, so
 * switching targets does not pop. Colours come from the module's {@link ColorSetting}, which follows
 * the ClickGUI theme until the palette says otherwise.</p>
 */
public final class TargetEspModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    /** Fade time for a new target, in milliseconds (the original's 250ms). */
    private static final float FADE_MS = 250.0F;
    /** Shards that lunge in on one hit, and how fast the wave runs. */
    private static final int STRIKE_SHARDS = 3;
    private static final float STRIKE_SPEED = 1.0F;

    public static TargetEspModule instance;

    public static SliderSetting style;
    public static SliderSetting start;
    public static SliderSetting size;
    public static SliderSetting speed;
    public static SliderSetting glow;
    public static ColorSetting color;

    private final TargetEspRenderer renderer = new TargetEspRenderer();

    private EntityLivingBase shown;
    private EntityLivingBase struck;
    private float fade;
    /** Scan phase of the Ring style, in degrees (0 = mid body, -90 = feet). */
    private float ringPhase = -90.0F;
    private int lastHurtTime;
    private long frameNanos;

    public TargetEspModule(int key) {
        super("Target ESP", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        style = (SliderSetting) ModuleManager.setting(this, "Style");
        start = (SliderSetting) ModuleManager.setting(this, "Start");
        size = (SliderSetting) ModuleManager.setting(this, "Size");
        speed = (SliderSetting) ModuleManager.setting(this, "Speed");
        glow = (SliderSetting) ModuleManager.setting(this, "Glow");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
    }

    @Override
    public String getInfo() {
        if (style == null) {
            return null;
        }
        String[] names = {"Crystals", "Ring", "Box"};
        int index = Math.max(0, Math.min(names.length - 1, (int) Math.round(style.getInput())));
        return names[index];
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
        shown = null;
        struck = null;
        fade = 0.0F;
        ringPhase = -90.0F;
        lastHurtTime = 0;
        frameNanos = 0L;
        renderer.reset();
    }

    /** KillAura2 first, then the legacy KillAura — both expose a public {@code getTarget()}. */
    private static EntityLivingBase resolveTarget() {
        Module second = ModuleManager.get("KillAura2");
        if (second != null && second.isEnabled() && second.getTarget() instanceof EntityLivingBase) {
            return (EntityLivingBase) second.getTarget();
        }
        Module first = ModuleManager.get("KillAura");
        if (first != null && first.isEnabled() && first.getTarget() instanceof EntityLivingBase) {
            return (EntityLivingBase) first.getTarget();
        }
        return null;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        long now = System.nanoTime();
        float delta = frameNanos == 0L ? 16.0F : Math.min(100.0F, (now - frameNanos) / 1000000.0F);
        frameNanos = now;
        renderer.tick(delta);

        EntityLivingBase target = resolveTarget();
        updateFade(target, delta);
        EntityLivingBase marked = shown;
        if (marked == null || fade <= 0.0F || marked.worldObj != mc.theWorld || marked.isDead) {
            return;
        }
        watchHits(marked);

        double seconds = now / 1.0E9D;
        float spin = (float) (seconds * 120.0D * (speed == null ? 1.0 : speed.getInput() / 4.0));
        float scale = size == null ? 1.0F : (float) size.getInput();
        float glowAmount = glow == null ? 0.75F : (float) (glow.getInput() / 100.0);
        int rgb = color == null ? Theme.scheme().primary : color.color();
        int styleIndex = style == null ? 0 : (int) Math.round(style.getInput());
        boolean middle = start != null && Math.round(start.getInput()) == 1;
        // The Ring scans one body height per 180 degrees, so the phase is kept in degrees:
        // Speed 4 is the original's 4 * 120 deg/s, i.e. a little over one sweep per second.
        ringPhase += delta * 0.001F * 120.0F * (speed == null ? 1.0F : (float) (speed.getInput() / 4.0));
        if (ringPhase > 100000.0F) {
            ringPhase -= 100000.0F;
        }
        double startOffset = middle ? marked.height * 0.5D - marked.height * 0.95D * 0.5D : 0.0D;

        if (styleIndex == 1) {
            renderer.renderRing(marked, event.partialTicks, fade, ringPhase + (middle ? 0.0F : -90.0F),
                    scale, glowAmount, rgb);
        } else if (styleIndex == 2) {
            box(marked, rgb, scale);
        } else {
            // 1.35 * width is the original's 1.5 * width * (1.25 - 0.5 * size) at its default size.
            float radius = 1.35F * marked.width * scale;
            renderer.render(marked, event.partialTicks, fade, seconds, radius, scale, glowAmount, rgb,
                    startOffset);
        }
    }

    /** 250ms fade in for a new target, 250ms fade out of the old one before it is replaced. */
    private void updateFade(EntityLivingBase target, float delta) {
        float step = Math.max(0.0F, delta) / FADE_MS;
        if (target == shown) {
            fade = shown == null ? Math.max(0.0F, fade - step) : Math.min(1.0F, fade + step);
        } else if (shown != null && fade > 0.0F) {
            fade = Math.max(0.0F, fade - step);
        } else {
            shown = target;
            fade = 0.0F;
            lastHurtTime = target == null ? 0 : target.hurtTime;
        }
    }

    /** Rising hurt time means the target was hit this frame: send the shards in. */
    private void watchHits(EntityLivingBase target) {
        if (struck != target) {
            struck = target;
            lastHurtTime = target.hurtTime;
            return;
        }
        if (target.hurtTime > lastHurtTime) {
            renderer.strike(STRIKE_SHARDS, STRIKE_SPEED);
        }
        lastHurtTime = target.hurtTime;
    }

    /** Translucent bounding box, the original's {@code Box} style. */
    private void box(EntityLivingBase target, int rgb, float scale) {
        RenderManager manager = mc.getRenderManager();
        AxisAlignedBB box = target.getEntityBoundingBox();
        AxisAlignedBB relative = new AxisAlignedBB(box.minX - manager.viewerPosX, box.minY - manager.viewerPosY,
                box.minZ - manager.viewerPosZ, box.maxX - manager.viewerPosX, box.maxY - manager.viewerPosY,
                box.maxZ - manager.viewerPosZ).expand(0.05D * scale, 0.05D * scale, 0.05D * scale);
        Render3D.filledBox(relative, ((rgb >> 16) & 255) / 255.0F, ((rgb >> 8) & 255) / 255.0F,
                (rgb & 255) / 255.0F, 0.16F * fade);
    }
}
