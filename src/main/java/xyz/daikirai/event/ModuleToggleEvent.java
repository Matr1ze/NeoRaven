package xyz.daikirai.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import xyz.daikirai.client.Profiles;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;

/** A completed module transition, shared by every notification presentation. */
public final class ModuleToggleEvent extends Event {
    public final Module module;
    public final boolean enabled;

    public ModuleToggleEvent(Module module, boolean enabled) {
        this.module = module;
        this.enabled = enabled;
    }

    /** Called only by Module's lifecycle; HUD visibility and key bindings do not filter events. */
    public static void publish(Module module, boolean enabled) {
        Minecraft mc = Minecraft.getMinecraft();
        if (Profiles.loading || module.noToggle || module.isProfile()
                || module == ModuleManager.gui || module == ModuleManager.commandLine
                || module.isEnabled() != enabled || mc.theWorld == null || mc.thePlayer == null) return;
        WorldClient world = mc.theWorld;
        mc.addScheduledTask(() -> {
            // Never carry an event into another session, or announce a profile's batch updates.
            if (!Profiles.loading && mc.theWorld == world && mc.thePlayer != null) {
                MinecraftForge.EVENT_BUS.post(new ModuleToggleEvent(module, enabled));
            }
        });
    }
}
