package xyz.daikirai.module.player;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.player.antivoid.AntiVoidBlink;
import xyz.daikirai.module.player.antivoid.AntiVoidCollision;
import xyz.daikirai.module.player.antivoid.AntiVoidMode;
import xyz.daikirai.module.player.antivoid.AntiVoidPacket;
import xyz.daikirai.module.player.antivoid.AntiVoidPosition;
import xyz.daikirai.module.player.antivoid.AntiVoidVulcan;
import xyz.daikirai.module.player.antivoid.AntiVoidWatchdog;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Rise 6.9.5 {@code com.alan.clients.module.impl.player.AntiVoid} ported to this client.
 *
 * <p>Modes (1:1 with upstream, each keeps its own {@code Distance} default/range/step):
 * <ul>
 *     <li>{@code Packet} – extra position packet while falling (Rise: {@code PacketAntiVoid})</li>
 *     <li>{@code Position} – report {@code posY + fallDistance} (Rise: {@code PositionAntiVoid})</li>
 *     <li>{@code Vulcan} – fake void floor + forced strafe + Speed hand-off ({@code VulcanAntiVoid})</li>
 *     <li>{@code Collision} – clamp motionY to the block floor ({@code CollisionAntiVoid})</li>
 *     <li>{@code Watchdog} – projectile column scan + blink queue warp ({@code WatchdogAntiVoid})</li>
 *     <li>{@code Blink} – snap back to the last sampled safe spot ({@code BlinkAntiVoid})</li>
 * </ul>
 *
 * <p>{@link #serverFall} is the port of Rise's {@code FallDistanceComponent.cY}: a client-side fall
 * distance that keeps accumulating while the player drops and only resets on reported ground, used
 * by Collision / Watchdog / Blink.
 */
public class AntiVoidModule extends Module {
    public static final int PACKET = 0;
    public static final int POSITION = 1;
    public static final int VULCAN = 2;
    public static final int COLLISION = 3;
    public static final int WATCHDOG = 4;
    public static final int BLINK = 5;

    private static final String[] MODE_NAMES = {"Packet", "Position", "Vulcan", "Collision", "Watchdog", "Blink"};

    public static AntiVoidModule instance;

    /** Rise FallDistanceComponent.cY. */
    public static float serverFall;

    private final SliderSetting modeRow;
    private final AntiVoidMode[] modes;
    private final ArrayList<Setting> moduleRows = new ArrayList<Setting>();
    private int activeMode = -1;

    public AntiVoidModule(int key) {
        super("AntiVoid", Category.player, key);
        instance = this;
        this.modeRow = new SliderSetting("Mode", "", "", PACKET, 0, MODE_NAMES.length - 1, 1, MODE_NAMES, null);
        this.modes = new AntiVoidMode[]{
                new AntiVoidPacket(this),
                new AntiVoidPosition(this),
                new AntiVoidVulcan(this),
                new AntiVoidCollision(this),
                new AntiVoidWatchdog(this),
                new AntiVoidBlink(this)
        };
        this.activeMode = PACKET;
        rebuild();
    }

    // ------------------------------------------------------------------ //
    // Settings
    // ------------------------------------------------------------------ //

    @Override
    public ArrayList<Setting> getSettings() {
        syncMode();
        return super.getSettings();
    }

    private void rebuild() {
        ArrayList<Setting> external = new ArrayList<Setting>();
        for (Setting setting : this.settings) {
            if (setting != this.modeRow && !this.moduleRows.contains(setting)) {
                external.add(setting);
            }
        }
        this.settings.clear();
        this.moduleRows.clear();
        this.moduleRows.add(this.modeRow);
        this.settings.add(this.modeRow);
        List<Setting> rows = this.modes[this.activeMode].rows();
        this.moduleRows.addAll(rows);
        this.settings.addAll(rows);
        this.settings.addAll(external);
    }

    private void syncMode() {
        int wanted = (int) this.modeRow.getInput();
        if (wanted < 0) {
            wanted = 0;
        } else if (wanted >= this.modes.length) {
            wanted = this.modes.length - 1;
        }
        if (wanted == this.activeMode) {
            return;
        }
        if (this.activeMode >= 0) {
            this.modes[this.activeMode].onDisable();
        }
        this.activeMode = wanted;
        rebuild();
        if (isEnabled()) {
            this.modes[this.activeMode].onEnable();
        }
    }

    public int activeIndex() {
        return this.activeMode;
    }

    @Override
    public String getInfo() {
        int index = this.activeMode < 0 ? 0 : this.activeMode;
        return MODE_NAMES[index];
    }

    // ------------------------------------------------------------------ //
    // Lifecycle
    // ------------------------------------------------------------------ //

    @Override
    public void onEnable() {
        syncMode();
        serverFall = 0.0F;
        this.modes[this.activeMode].onEnable();
    }

    @Override
    public void onDisable() {
        if (this.activeMode >= 0) {
            this.modes[this.activeMode].onDisable();
        }
        serverFall = 0.0F;
    }

    // ------------------------------------------------------------------ //
    // Tick hooks
    // ------------------------------------------------------------------ //

    @Override
    public void onPreUpdate() {
        syncMode();
        this.modes[this.activeMode].onPreUpdate();
    }

    @Override
    public void onPreMotion(MotionEvent e) {
        if (Minecraft.getMinecraft().thePlayer == null) {
            return;
        }
        syncMode();
        // Rise FallDistanceComponent: accumulate only while the player drops, reset on ground.
        double dy = Minecraft.getMinecraft().thePlayer.lastTickPosY - Minecraft.getMinecraft().thePlayer.posY;
        if (dy > 0.0) {
            serverFall = (float) (serverFall + dy);
        }
        if (e.onGround) {
            serverFall = 0.0F;
        }
        this.modes[this.activeMode].onPreMotion(e);
    }

    @Override
    public void onPostMotion() {
        this.modes[this.activeMode].onPostMotion();
    }

    @Override
    public void onPostPlayerInput() {
        this.modes[this.activeMode].onPostPlayerInput();
    }

    @Override
    public boolean onReceivePacket(Packet packet, net.minecraft.network.INetHandler handler) {
        this.modes[this.activeMode].onReceive(packet);
        return true;
    }
}
