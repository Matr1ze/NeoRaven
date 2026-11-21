package xyz.daikirai.via;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ViaMenuButton {
    @SubscribeEvent
    public void addButton(GuiScreenEvent.InitGuiEvent.Post event) {
        if (event.gui instanceof GuiMultiplayer) {
            event.buttonList.add(new ProtocolButton(event.gui.width));
        }
    }

    @SubscribeEvent
    public void openSelector(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.button instanceof ProtocolButton) {
            event.setCanceled(true);
            ViaCompatibility.openSelector(event.gui);
        }
    }

    private static final class ProtocolButton extends GuiButton {
        ProtocolButton(int screenWidth) {
            super(39218, screenWidth - 142, 5, 136, 20, ViaCompatibility.protocolLabel());
        }
    }
}
