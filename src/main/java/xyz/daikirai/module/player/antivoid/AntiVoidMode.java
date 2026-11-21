package xyz.daikirai.module.player.antivoid;

import xyz.daikirai.event.MotionEvent;
import xyz.daikirai.module.player.AntiVoidModule;
import xyz.daikirai.module.setting.Setting;
import xyz.daikirai.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One "Mode" of Rise 6.9.5's AntiVoid module.
 *
 * <p>Every mode owns its own {@code Distance} row (same name, same per-mode default/step as
 * upstream), so only the row of the selected mode is visible in the ClickGUI. The base class only
 * carries the plumbing; the interesting behaviour lives in the subclasses.
 */
public abstract class AntiVoidMode {
    protected final AntiVoidModule module;
    protected final Minecraft mc = Minecraft.getMinecraft();
    protected final SliderSetting distance;

    protected AntiVoidMode(AntiVoidModule module, double value, double min, double max, double step) {
        this.module = module;
        this.distance = value < 0.0 ? null : new SliderSetting("Distance", "", "", value, min, max, step, null, null);
    }

    /** Rows that this mode contributes to the module's settings list. */
    public List<Setting> rows() {
        if (this.distance == null) {
            return Collections.emptyList();
        }
        List<Setting> list = new ArrayList<Setting>(1);
        list.add(this.distance);
        return list;
    }

    public void onEnable() {
    }

    public void onDisable() {
    }

    /** Once per client tick, before the motion packet is built (Rise: PreMotion ordering helper). */
    public void onPreUpdate() {
    }

    /** Before the C03 is sent; may edit {@code e} (position / onGround). */
    public void onPreMotion(MotionEvent e) {
    }

    /** After the C03 was sent (Rise: PostMotionEvent). */
    public void onPostMotion() {
    }

    /** After the vanilla movement input was rebuilt (Rise: StrafeEvent / JumpEvent). */
    public void onPostPlayerInput() {
    }

    public void onReceive(Packet<?> packet) {
    }

    protected final double dist() {
        return this.distance == null ? 0.0 : this.distance.getInput();
    }

    /** Rise {@code PlayerUtil.vh()}: is there any collision surface within 10 blocks below? */
    protected final boolean groundBelow() {
        return xyz.daikirai.util.PlayerUtils.groundWithin(10.0);
    }

    /** Rise {@code PlayerUtil.a(distance, true)}: collision surface within {@code d} blocks below. */
    protected final boolean groundWithin(double d) {
        return xyz.daikirai.util.PlayerUtils.groundWithin(d);
    }
}
