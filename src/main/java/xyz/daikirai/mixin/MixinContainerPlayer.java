package xyz.daikirai.mixin;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import xyz.daikirai.via.ViaOffhandContainer;

@Mixin(ContainerPlayer.class)
public abstract class MixinContainerPlayer extends Container implements ViaOffhandContainer {
    @Override
    public void onyx$enableOffhand() {
        if (inventorySlots.size() == 45) {
            addSlotToContainer(new Slot(new InventoryBasic("Offhand", false, 1), 0, 77, 62));
        }
    }
}
