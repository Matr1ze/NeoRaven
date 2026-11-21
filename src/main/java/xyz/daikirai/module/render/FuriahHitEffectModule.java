package xyz.daikirai.module.render;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.furiah.module.hit.FuriahHitEffects;
import xyz.daikirai.render.furiah.module.hit.HitContext;
import xyz.daikirai.render.furiah.module.hit.HitStyle;

public final class FuriahHitEffectModule extends VisualModule {
    private static final long CONFIRM_NANOS = 500_000_000L;
    private static final HitStyle[] STYLES = HitStyle.values();
    private static FuriahHitEffectModule instance;
    private final SliderSetting mode = mode("Style", 0, styleNames());
    private final SliderSetting trigger = mode("Hit trigger", 0, "Confirmed", "Every attack");
    private final ColorSetting tint = color("Color", 0xFF66A4FF);
    private final SliderSetting size = slider("Size", 1, 0.5, 2, 0.05, "x");
    private final SliderSetting speed = slider("Speed", 1, 0.25, 3, 0.05, "x");
    private final SliderSetting opacity = slider("Opacity", 85, 5, 100, 1, "%");
    private final ButtonSetting follow = toggle("Follow target", true);
    private final ButtonSetting throughWalls = toggle("Through walls", false);
    private final Map<Integer, Pending> pending = new HashMap<>();
    private FuriahHitEffects effects;
    private WorldClient world;

    public FuriahHitEffectModule(int key) {
        super("Hit Effect", key);
        instance = this;
    }

    private static String[] styleNames() {
        String[] result = new String[STYLES.length];
        for (int i = 0; i < STYLES.length; i++) result[i] = STYLES[i].displayName();
        return result;
    }

    @Override public String getInfo() { return mode.getDisplay(false); }
    @Override public void onEnable() { reset(); }
    @Override public void onDisable() { reset(); }

    private void reset() {
        world = mc.theWorld;
        pending.clear();
        if (effects != null) effects.clear();
    }

    public static void attackPacket(C02PacketUseEntity packet) {
        FuriahHitEffectModule module = instance;
        if (module == null || !module.isEnabled() || packet.getAction() != C02PacketUseEntity.Action.ATTACK) return;
        if (mc.isCallingFromMinecraftThread()) module.capture(packet);
        else mc.addScheduledTask(() -> module.capture(packet));
    }

    private void capture(C02PacketUseEntity packet) {
        if (!isEnabled() || mc.theWorld == null || mc.thePlayer == null) return;
        if (world != mc.theWorld) reset();
        Entity target = packet.getEntityFromWorld(world);
        if (!(target instanceof EntityLivingBase) || target == mc.thePlayer || target.isDead) return;
        EntityLivingBase living = (EntityLivingBase) target;
        if (effects == null) effects = new FuriahHitEffects();
        HitContext hit = effects.measure(living);
        if (trigger.getInput() == 1) spawn(hit);
        else pending.put(hit.targetId, new Pending(hit, living.hurtTime, System.nanoTime()));
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (world != mc.theWorld || mc.thePlayer == null) reset();
        if (pending.isEmpty() || world == null) return;
        long now = System.nanoTime();
        Iterator<Pending> entries = pending.values().iterator();
        while (entries.hasNext()) {
            Pending entry = entries.next();
            EntityLivingBase target = (EntityLivingBase) entry.hit.target;
            if (now - entry.time > CONFIRM_NANOS || world.getEntityByID(entry.hit.targetId) != target) {
                entries.remove();
            } else if (target.hurtTime > entry.hurt) {
                spawn(entry.hit);
                entries.remove();
            } else {
                entry.hurt = target.hurtTime;
            }
        }
    }

    private void spawn(HitContext hit) {
        effects.spawn(STYLES[(int) mode.getInput()], hit, tint.color(), (float) size.getInput(), (float) speed.getInput(),
                (float) opacity.getInput() / 100, follow.isToggled());
    }

    @SubscribeEvent public void render(RenderWorldLastEvent event) {
        if (world != mc.theWorld) reset();
        if (world != null && effects != null && mc.thePlayer != null && mc.getRenderViewEntity() != null) {
            effects.render(event.partialTicks, tint.color(), throughWalls.isToggled());
        }
    }

    private static final class Pending {
        private final HitContext hit;
        private final long time;
        private int hurt;

        private Pending(HitContext hit, int hurt, long time) {
            this.hit = hit;
            this.hurt = hurt;
            this.time = time;
        }
    }
}
