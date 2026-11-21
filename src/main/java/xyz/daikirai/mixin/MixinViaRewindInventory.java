package xyz.daikirai.mixin;

import com.viaversion.viarewind.protocol.v1_9to1_8.rewriter.BlockItemPacketRewriter1_9;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = BlockItemPacketRewriter1_9.class, remap = false)
public abstract class MixinViaRewindInventory {
    @ModifyConstant(method = "lambda$registerPackets$2", constant = @Constant(intValue = 46), require = 1, remap = false)
    private int onyx$keepOffhandInWindowContents(int legacySlotCount, PacketWrapper wrapper) {
        // ViaRewind has already translated every stack here. Keep the full inventory for the added client slot.
        return wrapper.user().getProtocolInfo().serverProtocolVersion() == ProtocolVersion.v1_20_5
                ? -1 : legacySlotCount;
    }
}
