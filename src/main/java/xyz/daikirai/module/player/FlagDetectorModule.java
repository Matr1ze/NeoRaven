package xyz.daikirai.module.player;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import xyz.daikirai.client.Chat;
import xyz.daikirai.gui.onyx.hud.NotificationLevel;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.render.NotificationsModule;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rise-style flag detector: reports anticheat setbacks (and cancelled knockback) in chat.
 *
 * <p>A setback arrives as {@code S08PacketPlayerPosLook} that yanks the player back to where the
 * server thinks they are. The detector compares the packet's position with the local one and, when the
 * jump is bigger than the configured threshold, prints {@code Flag Received} with the offset, the
 * look angles, ping and a running count - the debug line Rise shows when a check fires.</p>
 *
 * <p>Noise guards: the first ~2 seconds after spawning (join / respawn teleports are always S08),
 * repeat packets inside 120 ms, and anything under {@code Min distance} are ignored. A
 * {@code S12PacketEntityVelocity} that zeroes our own knockback is reported separately, because that is
 * the other half of "the anticheat cancelled my movement".</p>
 */
public class FlagDetectorModule extends Module {

    public static FlagDetectorModule instance;

    public static ButtonSetting chat;
    public static ButtonSetting notifications;
    public static ButtonSetting sound;
    public static ButtonSetting velocityResets;
    public static SliderSetting minDistance;
    public static SliderSetting details;

    /** Flags seen since the module was switched on; also shown as {@code #n} in the chat line. */
    private static final AtomicInteger FLAGS = new AtomicInteger();

    private static long lastFlagAt;
    private static String lastReason = "";

    public FlagDetectorModule(int key) {
        super("Flag Detector", Category.player, key);
        instance = this;
    }

    public void link() {
        chat = (ButtonSetting) ModuleManager.setting(this, "Chat");
        notifications = (ButtonSetting) ModuleManager.setting(this, "Notifications");
        sound = (ButtonSetting) ModuleManager.setting(this, "Sound");
        velocityResets = (ButtonSetting) ModuleManager.setting(this, "Velocity resets");
        minDistance = (SliderSetting) ModuleManager.setting(this, "Min distance");
        details = (SliderSetting) ModuleManager.setting(this, "Details");
    }

    @Override
    public void onEnable() {
        FLAGS.set(0);
        lastFlagAt = 0L;
    }

    @Override
    public void onDisable() {
        lastFlagAt = 0L;
        lastReason = "";
    }

    /** How many flags this session has seen (used by the probe and the chat counter). */
    public static int flags() {
        return FLAGS.get();
    }

    @Override
    public boolean onReceivePacket(Packet packet, INetHandler handler) {
        if (!isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return true;
        }
        if (packet instanceof S08PacketPlayerPosLook) {
            onPosLook((S08PacketPlayerPosLook) packet);
        } else if (packet instanceof S12PacketEntityVelocity) {
            onVelocity((S12PacketEntityVelocity) packet);
        }
        return true;
    }

    private void onPosLook(S08PacketPlayerPosLook packet) {
        // Join, respawn and dimension changes always use this packet; they are not flags.
        if (mc.thePlayer.ticksExisted < 40) {
            return;
        }
        double dx = packet.getX() - mc.thePlayer.posX;
        double dy = packet.getY() - mc.thePlayer.posY;
        double dz = packet.getZ() - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double minimum = minDistance == null ? 0.15D : minDistance.getInput();
        if (distance < minimum) {
            return;
        }
        String where = String.format(Locale.ROOT, "x%.2f y%.2f z%.2f", dx, dy, dz);
        report("setback", distance, where, packet.getYaw(), packet.getPitch());
    }

    private void onVelocity(S12PacketEntityVelocity packet) {
        if (velocityResets == null || !velocityResets.isToggled()) {
            return;
        }
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) {
            return;
        }
        if (packet.getMotionX() != 0 || packet.getMotionY() != 0 || packet.getMotionZ() != 0) {
            return;
        }
        if (mc.thePlayer.ticksExisted < 40) {
            return;
        }
        report("velocity cancelled", 0.0D, "0 0 0", 0.0F, 0.0F);
    }

    private void report(String reason, double distance, String where, float yaw, float pitch) {
        long now = System.currentTimeMillis();
        // A setback often arrives as a burst of packets; only the first one is interesting.
        if (now - lastFlagAt < 120L && reason.equals(lastReason)) {
            return;
        }
        lastFlagAt = now;
        lastReason = reason;
        int count = FLAGS.incrementAndGet();

        boolean full = details == null || Math.round(details.getInput()) == 0L;
        String line;
        if (reason.startsWith("velocity")) {
            line = String.format(Locale.ROOT,
                    "&cFlag Received&7 · &fvelocity cancelled &7(knockback zeroed) &7ping&f%dms &8#%d",
                    ping(), count);
        } else if (full) {
            line = String.format(Locale.ROOT,
                    "&cFlag Received&7 » &f%s &7Δ&f%.2fm&7 (&f%s&7) &7yaw&f%.1f &7pitch&f%.1f &7ping&f%dms &8#%d",
                    reason, distance, where, yaw, pitch, ping(), count);
        } else {
            line = String.format(Locale.ROOT, "&cFlag Received&7 » &f%s &7Δ&f%.2fm &8#%d",
                    reason, distance, count);
        }
        System.out.println("[flag] " + Chat.strip(Chat.color(line)));

        if (chat == null || chat.isToggled()) {
            Chat.print(line);
        }
        if (notifications != null && notifications.isToggled()) {
            NotificationsModule.post("Flag Received", Chat.strip(Chat.color(line)), NotificationLevel.WARNING);
        }
        if (sound != null && sound.isToggled()) {
            mc.thePlayer.playSound("random.orb", 0.5F, 1.6F);
        }
    }

    /** Ping of the local player, or -1 when the tab list has not filled in yet. */
    private static int ping() {
        NetHandlerPlayClient handler = mc.getNetHandler();
        if (handler == null || mc.thePlayer == null) {
            return -1;
        }
        NetworkPlayerInfo info = handler.getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? -1 : info.getResponseTime();
    }
}
