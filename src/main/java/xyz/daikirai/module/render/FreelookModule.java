package xyz.daikirai.module.render;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.SliderSetting;

/** Onyx's detached camera. Only camera reads use these angles; the player is never rotated. */
public final class FreelookModule extends Module {
    public static FreelookModule instance;
    private SliderSetting perspective;
    private SliderSetting sensitivity;
    private WorldClient world;
    private Entity player;
    private boolean active;
    private int savedPerspective;
    private float yaw, pitch, previousYaw, previousPitch;

    public FreelookModule(int key) {
        super("Freelook", Category.render, key);
        instance = this;
    }

    @Override
    public void link() {
        perspective = (SliderSetting) ModuleManager.setting(this, "Perspective");
        sensitivity = (SliderSetting) ModuleManager.setting(this, "Sensitivity");
    }

    @Override
    public void onEnable() {
        restore();
    }

    @Override
    public void onDisable() {
        restore();
    }

    private void restore() {
        if (active) mc.gameSettings.thirdPersonView = savedPerspective;
        active = false;
        world = null;
        player = null;
    }

    public void prepareCamera() {
        if (world != mc.theWorld || player != mc.thePlayer) restore();
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (!active) {
            yaw = previousYaw = mc.thePlayer.rotationYaw;
            pitch = previousPitch = mc.thePlayer.rotationPitch;
            savedPerspective = mc.gameSettings.thirdPersonView;
            world = mc.theWorld;
            player = mc.thePlayer;
            active = true;
        }
        mc.gameSettings.thirdPersonView = (int) perspective.getInput() + 1;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        prepareCamera();
        previousYaw = yaw;
        previousPitch = pitch;
    }

    public boolean mouse(float deltaYaw, float deltaPitch) {
        prepareCamera();
        if (!active || mc.getRenderViewEntity() != mc.thePlayer) return false;
        float factor = 0.15F * (float) sensitivity.getInput() / 100.0F;
        yaw += deltaYaw * factor;
        pitch = MathHelper.clamp_float(pitch - deltaPitch * factor, -90.0F, 90.0F);
        return true;
    }

    public static float cameraAngle(Entity entity, float vanilla, boolean vertical, boolean previous) {
        FreelookModule module = instance;
        if (module == null || !module.isEnabled() || !module.active || entity != module.player) return vanilla;
        return vertical ? (previous ? module.previousPitch : module.pitch)
                : (previous ? module.previousYaw : module.yaw);
    }
}
