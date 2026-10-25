package xyz.daikirai.module.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;
import net.minecraft.block.material.Material;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;
import xyz.daikirai.gui.onyx.render.Anim;
import xyz.daikirai.gui.onyx.render.Ease;
import xyz.daikirai.gui.onyx.render.Shader;
import xyz.daikirai.module.player.BedAuraModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.WorldRenderState;

public final class BlockOverlayModule extends VisualModule {
    private final SliderSetting theme = mode("Theme", 1, "Outline", "Outline + fill", "Outline + glint");
    private final ColorSetting color = color("Color", -10178561);
    private final ColorSetting second = color("Second color", -7055617, 48);
    private final SliderSetting lineWidth = slider("Line width", 1.5, .5, 5, .5, " px");
    private final SliderSetting outlineOpacity = slider("Outline opacity", 90, 5, 100, 5, "%");
    private final SliderSetting fillOpacity = slider("Fill opacity", 22, 1, 80, 1, "%");
    private final SliderSetting padding = slider("Padding", .6, 0, 5, .1, "%");
    private final SliderSetting glintSpeed = slider("Glint speed", 1, .1, 4, .1, "");
    private final SliderSetting glintScale = slider("Glint scale", 1.5, .5, 4, .1, "");
    private final SliderSetting glintGlow = slider("Glint glow", 60, 0, 100, 5, "%");
    private final SliderSetting appear = slider("Animation: Appear", 140, 0, 600, 10, " ms");
    private final SliderSetting move = slider("Animation: Move", 110, 0, 600, 10, " ms");
    private final SliderSetting fade = slider("Animation: Fade", 180, 0, 600, 10, " ms");
    private final SliderSetting appearance = mode("Animation: Appearance", 1, "Fade", "Pop", "Grow");
    private final ButtonSetting breakProgress = toggle("Break progress", true);
    private final SliderSetting progressMode = mode("Break progress: Mode", 0, "Fill", "Grow from centre");
    private final SliderSetting direction = mode("Break progress: Direction", 0, "Top to bottom", "Bottom to top");
    private final SliderSetting progressOpacity = slider("Progress opacity", 45, 1, 100, 1, "%");
    private final ButtonSetting onlyBed = toggle("Only on bed break", false);
    private final ButtonSetting throughWalls = toggle("Through walls", true);
    private final ButtonSetting hideVanilla = toggle("Hide vanilla outline", true);
    private final Anim alpha = new Anim(0), scale = new Anim(1), progress = new Anim(0);
    private final Anim[] bounds = {new Anim(0), new Anim(0), new Anim(0), new Anim(0), new Anim(0), new Anim(0)};
    private final Field damage = ReflectionHelper.findField(PlayerControllerMP.class, "curBlockDamageMP", "field_78770_f");
    private BlockPos target, candidate, anchor;
    private boolean visible;
    private long last;
    private float debounce;
    private WorldClient world;
    private Shader glint;
    private static final Method SHADER_PACK_CHECK = shaderPackCheck();

    public BlockOverlayModule(int key) { super("BlockOverlay", key); }
    @Override public void onEnable() { clear(); }
    @Override public void onDisable() { clear(); }

    private void clear() {
        target = candidate = anchor = null;
        visible = false;
        last = 0;
        debounce = 0;
        alpha.set(0); scale.set(1); progress.set(0);
        world = mc.theWorld;
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (world != mc.theWorld) clear();
    }

    private boolean bedTarget() { return BedAuraModule.instance != null && BedAuraModule.instance.isEnabled() && BedAuraModule.target != null; }

    private BlockPos selected() {
        if (bedTarget()) return BedAuraModule.target;
        MovingObjectPosition hit = mc.objectMouseOver;
        return !onlyBed.isToggled() && hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK ? hit.getBlockPos() : null;
    }

    @SubscribeEvent public void highlight(DrawBlockHighlightEvent event) {
        if (hideVanilla.isToggled() && mc.theWorld != null && valid(selected())) event.setCanceled(true);
    }

    private boolean valid(BlockPos p) { return p != null && mc.theWorld.getBlockState(p).getBlock().getMaterial() != Material.air; }

    private float mining() {
        if (!breakProgress.isToggled()) return 0;
        if (bedTarget()) return Math.max(0, Math.min(1, BedAuraModule.progress));
        if (mc.playerController == null || !mc.playerController.getIsHittingBlock()) return 0;
        try { return Math.max(0, Math.min(1, damage.getFloat(mc.playerController))); }
        catch (IllegalAccessException ignored) { return 0; }
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if (world != mc.theWorld) clear();
        if (mc.theWorld == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0 : Math.min(100, (now - last) / 1E6F);
        last = now;
        BlockPos selected = selected();
        if (target != null && !Objects.equals(selected, target)) {
            if (!Objects.equals(candidate, selected)) { candidate = selected; debounce = 0; }
            debounce += dt;
            if (debounce < 60) selected = target;
        } else { candidate = null; debounce = 0; }
        boolean active = valid(selected);
        float initial = appearance.getInput() == 0 ? 1 : appearance.getInput() == 1 ? .62F : 0;
        if (active) {
            boolean fresh = target == null;
            boolean distant = !fresh && selected.distanceSq(target.getX(), target.getY(), target.getZ()) > 16;
            if (!selected.equals(target)) progress.set(0);
            target = selected;
            if (fresh || distant) anchor = target;
            double[] coordinates = {target.getX(), target.getY(), target.getZ(), target.getX()+1, target.getY()+1, target.getZ()+1};
            for (int i = 0; i < 6; i++) {
                float local = (float)(coordinates[i] - (i % 3 == 0 ? anchor.getX() : i % 3 == 1 ? anchor.getY() : anchor.getZ()));
                if (fresh || distant) bounds[i].set(local);
                else bounds[i].to(local, (float) move.getInput(), Ease.EMPHASIZED_DECELERATE);
            }
            if (fresh) { alpha.set(0); scale.set(initial); }
            alpha.to(1, (float) appear.getInput(), Ease.EMPHASIZED);
            scale.to(1, (float) appear.getInput(), Ease.EMPHASIZED);
            progress.to(mining(), 70, Ease.LINEAR);
            visible = true;
        } else if (visible) {
            visible = false;
            alpha.to(0, (float) fade.getInput(), Ease.LINEAR);
            scale.to(initial, (float) fade.getInput(), Ease.LINEAR);
            progress.set(0);
        }
        alpha.update(dt); scale.update(dt); progress.update(dt);
        for (Anim bound : bounds) bound.update(dt);
        if (target == null || alpha.value() <= .004) { if (!visible) target = anchor = null; return; }
        Vec3 camera = WorldRenderState.camera(event.partialTicks);
        AxisAlignedBB full = new AxisAlignedBB(anchor.getX() + bounds[0].value(), anchor.getY() + bounds[1].value(), anchor.getZ() + bounds[2].value(),
                anchor.getX() + bounds[3].value(), anchor.getY() + bounds[4].value(), anchor.getZ() + bounds[5].value());
        full = scaled(full, scale.value()).expand(padding.getInput()/100, padding.getInput()/100, padding.getInput()/100)
                .offset(-camera.xCoord, -camera.yCoord, -camera.zCoord);
        boolean mining = breakProgress.isToggled() && progress.value() > .001;
        AxisAlignedBB fill = mining ? progressBox(full, progress.value()) : full;
        try (WorldRenderState state = new WorldRenderState(throughWalls.isToggled())) {
            boolean shader = theme.getInput() == 2 && glint(full, fill, mining, camera);
            if (!shader && (mining || theme.getInput() != 0)) {
                WorldRenderState.color(color.color(), alpha.value() * (mining ? progressOpacity.getInput() : fillOpacity.getInput()) / 100);
                WorldRenderState.box(fill, false);
            }
            WorldRenderState.color(color.color(), alpha.value() * outlineOpacity.getInput() / 100);
            GL11.glLineWidth((float) lineWidth.getInput());
            WorldRenderState.box(full, true);
        }
    }

    private AxisAlignedBB progressBox(AxisAlignedBB b, float value) {
        if (progressMode.getInput() == 1) return scaled(b, value);
        double y = (b.maxY - b.minY) * value;
        return direction.getInput() == 1 ? new AxisAlignedBB(b.minX,b.minY,b.minZ,b.maxX,b.minY+y,b.maxZ)
                : new AxisAlignedBB(b.minX,b.maxY-y,b.minZ,b.maxX,b.maxY,b.maxZ);
    }

    private static AxisAlignedBB scaled(AxisAlignedBB b, double scale) {
        double x = (b.minX+b.maxX)/2, y = (b.minY+b.maxY)/2, z = (b.minZ+b.maxZ)/2;
        return new AxisAlignedBB(x+(b.minX-x)*scale,y+(b.minY-y)*scale,z+(b.minZ-z)*scale,
                x+(b.maxX-x)*scale,y+(b.maxY-y)*scale,z+(b.maxZ-z)*scale);
    }

    private boolean glint(AxisAlignedBB full, AxisAlignedBB fill, boolean mining, Vec3 camera) {
        if (mc.theWorld.getBlockState(target).getBlock().getRenderType() != 3) return false;
        try {
            if (SHADER_PACK_CHECK != null && Boolean.TRUE.equals(SHADER_PACK_CHECK.invoke(null))) return false;
        } catch (ReflectiveOperationException ignored) { return false; }
        if (glint == null) glint = Shader.load("onyx_block_glint");
        if (!glint.valid()) return false;
        glint.use();
        try {
            int c = color.color(), s = second.color();
            glint.uniform4f("uColorPrimary", (c>>16&255)/255F, (c>>8&255)/255F, (c&255)/255F, alpha.value());
            glint.uniform4f("uColorSecondary", (s>>16&255)/255F, (s>>8&255)/255F, (s&255)/255F, (float)Math.min(1.6, fillOpacity.getInput()/100*3));
            glint.uniform4f("uMotion", (System.nanoTime()%180000000000L)/1E9F, (float)glintSpeed.getInput(), (float)glintScale.getInput(), (float)glintGlow.getInput()/100);
            glint.uniform4f("uOrigin", (float)(target.getX()-camera.xCoord), (float)(target.getY()-camera.yCoord), (float)(target.getZ()-camera.zCoord), 0);
            glint.uniform4f("uWorld", Math.floorMod(target.getX(),256), Math.floorMod(target.getY(),256), Math.floorMod(target.getZ(),256), 0);
            glint.uniform4f("uCrop", 0, 0, 0, 0);
            glint.uniform1f("uFogEnabled", 0);
            WorldRenderState.box(mining ? fill : full, false);
        } finally { Shader.unbind(); }
        return true;
    }

    private static Method shaderPackCheck() {
        try { return Class.forName("net.minecraft.src.Config").getMethod("isShaders"); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
}
