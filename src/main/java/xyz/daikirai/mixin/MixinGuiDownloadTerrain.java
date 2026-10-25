package xyz.daikirai.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.LoadingOverlay;
import xyz.daikirai.gui.onyx.Text;

/**
 * Same loading overlay for the "Downloading terrain" screen that follows a successful login, so the
 * join sequence does not flash back to the vanilla dirt background after the connect screen.
 */
@Mixin(GuiDownloadTerrain.class)
public class MixinGuiDownloadTerrain {

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void clickgui$loadingOverlay(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        ServerData server = mc.getCurrentServerData();
        LoadingOverlay.render(resolution.getScaledWidth(), resolution.getScaledHeight(),
                I18n.format("multiplayer.downloadingTerrain"),
                server == null ? "" : Text.strip(server.serverName));
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiDownloadTerrain;drawBackground(I)V"))
    private void clickgui$ownBackdrop(GuiDownloadTerrain screen, int tint) {
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiDownloadTerrain;drawCenteredString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"))
    private void clickgui$ownStatus(GuiDownloadTerrain screen, FontRenderer font, String text, int x, int y, int color) {
    }
}
