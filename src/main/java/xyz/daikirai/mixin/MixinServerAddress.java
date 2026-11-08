package xyz.daikirai.mixin;

import net.minecraft.client.multiplayer.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.daikirai.client.ServerSrv;

/**
 * Adds SRV resolution to {@code ServerAddress.fromString}, so {@code hypixel.today} follows its
 * {@code _minecraft._tcp} record to the real proxy instead of dialling the domain's web IP on 25565.
 * Explicit ports and addresses without a record pass through untouched.
 */
@Mixin(ServerAddress.class)
public class MixinServerAddress {

    @Inject(method = "fromString", at = @At("RETURN"), cancellable = true)
    private static void onyx$srv(String address, CallbackInfoReturnable<ServerAddress> ci) {
        ServerAddress parsed = ci.getReturnValue();
        ServerAddress resolved = ServerSrv.apply(address, parsed);
        if (resolved != parsed) {
            ci.setReturnValue(resolved);
        }
    }
}
