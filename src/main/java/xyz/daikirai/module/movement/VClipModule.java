package xyz.daikirai.module.movement;

import xyz.daikirai.util.TeleportPath;
import xyz.daikirai.client.Chat;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public class VClipModule extends Module {
    public static SliderSetting distance;
    public static ButtonSetting sendMessage, sendPackets;

    public VClipModule(int key) {
        super("VClip", Category.movement, key);
    }

    public void link() {
        distance = (SliderSetting) ModuleManager.setting(this, "Distance");
        sendMessage = (ButtonSetting) ModuleManager.setting(this, "Send message");
        sendPackets = (ButtonSetting) ModuleManager.setting(this, "Send packets");
    }

    private static String format(double d) {
        return d == Math.floor(d) ? Long.toString((long) d) : Double.toString(d);
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getMinecraft();
        double d = distance.getInput();
        if (mc.thePlayer != null && mc.theWorld != null && d != 0.0) {
            boolean ceiling = headBlocked(mc);
            if (sendPackets.isToggled() || ceiling) {
                if (!TeleportPath.clipVertical(d, false)) {
                    mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + d, mc.thePlayer.posZ);
                }
            } else {
                mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + d, mc.thePlayer.posZ);
            }
            if (sendMessage.isToggled()) {
                Chat.print("&7Teleported you " + (d > 0.0 ? "upwards" : "downwards") + " &b" + format(d) + " &7blocks.");
            }
        }
        this.disable();
    }

    /** A solid block intersecting the player's head requires the direct packet clip. */
    private static boolean headBlocked(Minecraft mc) {
        BlockPos head = new BlockPos(mc.thePlayer.posX,
                mc.thePlayer.getEntityBoundingBox().maxY - 0.01D, mc.thePlayer.posZ);
        return !TeleportPath.isPassable(head);
    }
}
