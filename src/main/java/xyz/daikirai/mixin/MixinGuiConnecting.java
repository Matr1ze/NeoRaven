package xyz.daikirai.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.I18n;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.daikirai.gui.onyx.LoadingOverlay;
import xyz.daikirai.gui.onyx.Text;

/**
 * Replaces the "Connecting to the server..." dirt screen with the client's loading overlay.
 *
 * <p>Only the drawing is replaced: {@code updateScreen}, the Cancel button ({@code buttonList} is
 * still drawn by {@code super.drawScreen}) and {@code keyTyped}'s ESC handling keep working.</p>
 */
@Mixin(GuiConnecting.class)
public class MixinGuiConnecting {

    @Shadow
    private NetworkManager networkManager;

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void clickgui$loadingOverlay(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        ServerData server = mc.getCurrentServerData();
        String title = I18n.format(this.networkManager == null ? "connect.connecting" : "connect.authorizing");
        // The address, like vanilla: the name is useless when the address was typed in directly
        // ("Direct Connect"), and the address is the thing the player is waiting on.
        String subtitle = server == null ? "" : Text.strip(server.serverIP == null ? server.serverName : server.serverIP);
        LoadingOverlay.render(resolution.getScaledWidth(), resolution.getScaledHeight(), title, subtitle);
    }

    /** The Material Cancel pill goes over the vanilla button, so it has to be drawn last. */
    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void clickgui$cancelButton(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc);
        LoadingOverlay.renderCancelButton(resolution.getScaledWidth(), resolution.getScaledHeight(),
                mouseX, mouseY);
    }

    /**
     * Skips {@code super.drawScreen} so the vanilla Cancel button never paints: the Material pill is
     * drawn at RETURN instead. The button itself stays in {@code buttonList}, so clicks still work.
     */
    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V"))
    private void clickgui$skipVanillaButtons(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
    }

    /** The overlay paints its own backdrop; vanilla's dirt texture must not go over it. */
    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/GuiConnecting;drawDefaultBackground()V"))
    private void clickgui$ownBackdrop(GuiConnecting screen) {
    }

    /** The overlay draws the status text with the Onyx typeface. */
    @Redirect(method = "drawScreen", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/GuiConnecting;drawCenteredString(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;III)V"))
    private void clickgui$ownStatus(GuiConnecting screen, FontRenderer font, String text, int x, int y, int color) {
    }
}
