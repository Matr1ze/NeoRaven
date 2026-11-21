package xyz.daikirai.module.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.HitAfterimageRenderer;
import xyz.daikirai.render.HitAfterimageSnapshot;

/** HitAfterimage 1.0: confirmed hurt animation/health loss, frozen pose and ten soft shells. */
public final class HitAfterimageModule extends Module {
    private final List<HitAfterimageSnapshot> images = new ArrayList<HitAfterimageSnapshot>();
    private final Map<EntityLivingBase, Observation> observed = new IdentityHashMap<EntityLivingBase, Observation>();
    private final HitAfterimageRenderer renderer = new HitAfterimageRenderer();
    private SliderSetting duration, opacity, selfInterval;
    private ButtonSetting players, mobs, self, selfTimed, selfJump, selfLand;
    private ColorSetting color;
    private WorldClient world;
    private EntityPlayer selfPlayer;
    private boolean wasGrounded, timerRunning;
    private long nextSelfTime, timerPeriod;
    private int lastSelfTick = Integer.MIN_VALUE;

    public HitAfterimageModule(int key) { super("Hit Afterimage", Category.render, key); }

    @Override public void link() {
        duration = (SliderSetting) ModuleManager.setting(this, "Duration");
        opacity = (SliderSetting) ModuleManager.setting(this, "Opacity");
        players = (ButtonSetting) ModuleManager.setting(this, "Players");
        mobs = (ButtonSetting) ModuleManager.setting(this, "Mobs");
        color = (ColorSetting) ModuleManager.setting(this, "Color");
        // ColorSetting follows the live client theme by default; Module's standard handler opens
        // ColorEditor, and profiles retain any colour/theme choice the user has already saved.
        self = (ButtonSetting) ModuleManager.setting(this, "Self");
        selfTimed = (ButtonSetting) ModuleManager.setting(this, "Self: Timed");
        selfInterval = (SliderSetting) ModuleManager.setting(this, "Self: Interval");
        selfJump = (ButtonSetting) ModuleManager.setting(this, "Self: On jump");
        selfLand = (ButtonSetting) ModuleManager.setting(this, "Self: On land");
    }

    private void clear() { images.clear(); observed.clear(); resetSelf(); world = mc.theWorld; }
    @Override public void onEnable() { clear(); }
    @Override public void onDisable() { clear(); }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world == world) { clear(); world = null; }
    }

    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isEnabled()) return;
        if (world != mc.theWorld) clear();
        if (world == null || mc.thePlayer == null) { clear(); return; }
        long now = System.nanoTime();
        images.removeIf(image -> image.alpha(now, lifetime()) <= 0);
        for (Observation observation : observed.values()) observation.present = false;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer || entity.isDead) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            boolean allowed = living instanceof EntityPlayer ? players.isToggled() : mobs.isToggled();
            if (!allowed) continue;
            Observation old = observed.get(living);
            int hurt = living.hurtTime;
            float health = living.getHealth();
            boolean hit = hurt > (old == null ? 0 : old.hurt)
                    || old != null && health < old.health - .01F;
            if (old == null) { old = new Observation(); observed.put(living, old); }
            old.present = true; old.hurt = hurt; old.health = health;
            if (hit && (old.lastImage == 0 || now - old.lastImage >= 50000000L)) {
                old.lastImage = now;
                if (images.size() >= 24) images.remove(0);
                images.add(new HitAfterimageSnapshot(living, now));
            }
        }
        Iterator<Observation> it = observed.values().iterator();
        while (it.hasNext()) if (!it.next().present) it.remove();
        sampleSelf(now);
    }

    private void resetSelf() {
        selfPlayer = null; wasGrounded = false; timerRunning = false;
        nextSelfTime = timerPeriod = 0; lastSelfTick = Integer.MIN_VALUE;
    }

    private boolean prepareSelf(long now) {
        if (!self.isToggled() || mc.thePlayer == null || mc.thePlayer.isDead
                || !mc.thePlayer.isEntityAlive() || mc.thePlayer.isSpectator() || mc.isGamePaused()) {
            if (selfPlayer != null) images.removeIf(image -> image.entity == selfPlayer);
            resetSelf();
            return false;
        }
        if (selfPlayer != mc.thePlayer) {
            resetSelf(); selfPlayer = mc.thePlayer; wasGrounded = selfPlayer.onGround;
        }
        long period = Math.max(50000000L, (long) (selfInterval.getInput() * 1000000000L));
        if (selfTimed.isToggled()) {
            if (!timerRunning || timerPeriod != period) nextSelfTime = now + period;
            timerRunning = true; timerPeriod = period;
        } else { timerRunning = false; nextSelfTime = 0; }
        return true;
    }

    private void sampleSelf(long now) {
        if (!prepareSelf(now)) return;
        boolean landed = !wasGrounded && selfPlayer.onGround;
        wasGrounded = selfPlayer.onGround;
        boolean due = timerRunning && now >= nextSelfTime;
        // Jump/land emissions do not restart the independent interval timer. After a stall, emit
        // at most once rather than replaying a backlog of expired timer deadlines.
        if (due) nextSelfTime = now + timerPeriod;
        if (due || landed && selfLand.isToggled()) addSelf(now);
    }

    @SubscribeEvent public void jump(LivingEvent.LivingJumpEvent event) {
        if (!isEnabled() || event.entityLiving != mc.thePlayer || mc.theWorld == null) return;
        if (world != mc.theWorld) clear();
        long now = System.nanoTime();
        if (prepareSelf(now) && selfJump.isToggled()) addSelf(now);
    }

    private void addSelf(long now) {
        if (lastSelfTick == selfPlayer.ticksExisted) return;
        lastSelfTick = selfPlayer.ticksExisted;
        if (images.size() >= 24) images.remove(0);
        images.add(new HitAfterimageSnapshot(selfPlayer, now));
    }

    // After Fog Blur and ordinary world effects, before the LOWEST world-lyrics overlay.
    @SubscribeEvent(priority = EventPriority.LOW) public void render(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (world != mc.theWorld) clear();
        if (world == null || mc.thePlayer == null || mc.getRenderViewEntity() == null) return;
        long now = System.nanoTime();
        images.removeIf(image -> image.alpha(now, lifetime()) <= 0);
        renderer.render(images, event.partialTicks, now, lifetime(), color.color(), (float) opacity.getInput() / 100);
    }

    private long lifetime() { return (long) (duration.getInput() * 1000000000L); }

    private static final class Observation {
        float health;
        int hurt;
        long lastImage;
        boolean present;
    }
}
