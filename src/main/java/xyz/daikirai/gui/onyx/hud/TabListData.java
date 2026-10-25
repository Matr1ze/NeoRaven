package xyz.daikirai.gui.onyx.hud;

import net.minecraft.util.IChatComponent;

/** Access to the actual packet-updated vanilla header/footer, including server resets. */
public interface TabListData {
    IChatComponent onyx$header();
    IChatComponent onyx$footer();
}
