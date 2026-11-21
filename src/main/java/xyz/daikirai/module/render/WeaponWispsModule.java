package xyz.daikirai.module.render;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ColorSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.render.HandOutlineRenderer;
import xyz.daikirai.render.WeaponWispRenderer;
import xyz.daikirai.render.WispMotion;
import xyz.daikirai.render.WispMotion.Point;

/** Three particles that orbit the player or drift along the held sword. */
public final class WeaponWispsModule extends Module {
    public static final String NAME = "Orbit Particles";
    public static final String LEGACY_NAME = "Weapon Wisps";
    private static final long SWORD_HOME = 1, PLAYER_HOME = 2;
    private static final long MAX_HAND_AGE = 250_000_000L;

    public static WeaponWispsModule instance;
    private final WispMotion motion=new WispMotion();
    private final WeaponWispRenderer renderer=new WeaponWispRenderer();
    private ColorSetting color;
    private SliderSetting size,heldRadius,attackRadius,speed,trail,attackDuration;
    private WorldClient world;
    private Entity owner;
    private EntityLivingBase target;
    private long began,attackUntil,handTime,driftSeed;
    private Point[] handHome;
    private Point previousOwner;
    private boolean handDraw;

    public WeaponWispsModule(int key){super(NAME,Category.render,key);instance=this;}

    // Keep the persisted key so profiles work with either release.
    @Override public String saveName() { return LEGACY_NAME; }

    @Override public void link(){
        color=(ColorSetting)ModuleManager.setting(this,"Color");
        size=(SliderSetting)ModuleManager.setting(this,"Size");
        heldRadius=(SliderSetting)ModuleManager.setting(this,"Held radius");
        attackRadius=(SliderSetting)ModuleManager.setting(this,"Attack radius");
        speed=(SliderSetting)ModuleManager.setting(this,"Speed");
        trail=(SliderSetting)ModuleManager.setting(this,"Trail length");
        attackDuration=(SliderSetting)ModuleManager.setting(this,"Attack duration");
    }
    @Override public void onEnable(){clear();}
    @Override public void onDisable(){clear();renderer.dispose();}
    private void clear(){motion.clear();renderer.resetView();world=mc.theWorld;owner=mc.thePlayer;target=null;handHome=null;previousOwner=null;handDraw=false;began=System.nanoTime();driftSeed=ThreadLocalRandom.current().nextLong();attackUntil=handTime=0;}
    private static boolean sword(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private boolean ready() {
        return isEnabled() && mc.theWorld != null && mc.thePlayer != null
                && mc.thePlayer.isEntityAlive() && !mc.thePlayer.isSpectator();
    }

    private boolean firstPerson() {
        return mc.gameSettings.thirdPersonView == 0 && mc.getRenderViewEntity() == mc.thePlayer;
    }

    private void sync(){if(world!=mc.theWorld||owner!=mc.thePlayer)clear();}

    /** Called only after the outbound checks accepted the packet; never edits/cancels any packet. */
    public static void attackPacket(C02PacketUseEntity packet){
        WeaponWispsModule module=instance;
        if(module==null||!module.isEnabled()||packet.getAction()!=C02PacketUseEntity.Action.ATTACK)return;
        final WorldClient sentWorld=mc.theWorld;
        if(sentWorld==null)return;
        mc.addScheduledTask(()->{if(mc.theWorld==sentWorld)module.attacked(packet.getEntityFromWorld(sentWorld));});
    }
    private void attacked(Entity entity){
        sync();if(!ready()||!sword(mc.thePlayer.getHeldItem())||!(entity instanceof EntityLivingBase)
                ||entity==mc.thePlayer||entity.isDead||entity.worldObj!=world)return;
        target=(EntityLivingBase)entity;attackUntil=System.nanoTime()+(long)(attackDuration.getInput()*1e9);
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event){if(event.world==world)clear();}

    @SubscribeEvent(priority=EventPriority.LOW) public void world(RenderWorldLastEvent event){
        handDraw=false;
        sync();if(!ready()){motion.clear();target=null;handHome=null;return;}
        if(mc.isGamePaused())return;
        long now=System.nanoTime();double seconds=(now-began)/1e9;
        renderer.captureWorld(event.partialTicks);
        Point player=position(mc.thePlayer,event.partialTicks);
        if(previousOwner!=null&&player.minus(previousOwner).length()>12){clear();renderer.captureWorld(event.partialTicks);seconds=0;}
        previousOwner=player;
        boolean holdingSword = sword(mc.thePlayer.getHeldItem());
        if (target != null && (!holdingSword || now >= attackUntil || target.worldObj != world
                || !target.isEntityAlive() || mc.thePlayer.getDistanceToEntity(target) > 24)) {
            target = null;
            driftSeed = ThreadLocalRandom.current().nextLong();
        }
        boolean wantsSwordHome = holdingSword && firstPerson();
        if (!wantsSwordHome) {
            handHome = null;
            handTime = 0;
        }
        boolean useSwordHome = target == null && wantsSwordHome && handHome != null
                && now - handTime < MAX_HAND_AGE;

        Point[] destination;
        long key;
        if (target != null) {
            key = ((long) target.getEntityId() << 2) | 3;
            destination = new Point[WispMotion.COUNT];
            Point center = position(target, event.partialTicks);
            double radius = Math.max(.4, target.width * 1.35) * attackRadius.getInput() / .8;
            for (int i = 0; i < destination.length; i++) {
                destination[i] = center.plus(WispMotion.strike(i, seconds, speed.getInput(), radius, target.height));
            }
        } else if (useSwordHome) {
            key = SWORD_HOME;
            // When already on the sword, advance only in the real held-item pass.
            if (motion.at(key) && motion.settled(now)) {
                handDraw = true;
                return;
            }
            Point[] mapped = renderer.mapHand(seconds, speed.getInput(), heldRadius.getInput() / .8, driftSeed);
            if (mapped != null) handHome = mapped;
            destination = handHome;
        } else {
            key = PLAYER_HOME;
            destination = new Point[WispMotion.COUNT];
            for (int i = 0; i < destination.length; i++) {
                destination[i] = player.plus(WispMotion.home(
                        i, seconds, speed.getInput(), heldRadius.getInput(), mc.thePlayer.height));
            }
        }
        motion.update(now, key, destination, trail.getInput());
        // Choose exactly one pass even if the flight settles during this frame.
        if (useSwordHome && motion.settled(now)) {
            handDraw = true;
            return;
        }
        // A visible victim's interior crossing stays luminous; walls are not generally revealed.
        boolean crossBody=target!=null&&mc.thePlayer.canEntityBeSeen(target);
        float fade=(float)Math.min(1,(now-began)/180000000.0);
        double drawSize=size.getInput();
        if(useSwordHome)drawSize+=(renderer.handWorldSize(drawSize)-drawSize)*motion.progress(now);
        renderer.drawWorld(motion,event.partialTicks,now,trail.getInput(),drawSize,color.color(),fade,crossBody);
    }

    /** Invoked at the actual held-item transform, after the normal weapon draw, outside its mask replay. */
    public static void held(ItemStack stack){
        WeaponWispsModule module=instance;
        if(module!=null)module.drawHeld(stack);
    }
    private void drawHeld(ItemStack stack){
        sync();
        if (!ready() || !firstPerson() || !sword(mc.thePlayer.getHeldItem()) || !sword(stack)
                || HandOutlineRenderer.capturing() || mc.isGamePaused()) return;
        long now=System.nanoTime();double seconds=(now-began)/1e9;
        Point[] mapped=renderer.captureBlade(stack,seconds,speed.getInput(),heldRadius.getInput()/.8,driftSeed);
        if(mapped!=null){handHome=mapped;handTime=now;}
        if(target!=null||!handDraw)return;
        // Advance the same history with the current hand transform; retain the returning tail.
        if(handHome!=null)motion.update(now,SWORD_HOME,handHome,trail.getInput());
        renderer.drawHand(motion,now,trail.getInput(),size.getInput(),color.color(),(float)Math.min(1,(now-began)/180000000.0));
    }
    private static Point position(Entity entity,float partial){return new Point(entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial,entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial,entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial);}
}
