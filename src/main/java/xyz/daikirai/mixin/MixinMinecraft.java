package xyz.daikirai.mixin;

import xyz.daikirai.hook.Hooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.opengl.Display;
import xyz.daikirai.gui.onyx.render.GuiFont;
import xyz.daikirai.gui.onyx.render.GuiOpenAnimation;
import xyz.daikirai.via.ViaOffhand;
import xyz.daikirai.via.ViaCompatibility;

import java.io.IOException;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    /** Frame rate used while the window is unfocused or minimized. */
    private static final int HIDDEN_FPS = 20;

    @Shadow public GuiScreen currentScreen;

    @Inject(method = "runTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/WorldClient;updateEntities()V"))
    private void onyx$pollOffhandSwap(CallbackInfo ci) {
        ViaOffhand.pollSwap((Minecraft) (Object) this);
    }

    @Inject(method = "clickMouse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;attackEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)V",
            shift = At.Shift.AFTER))
    private void onyx$flushViaAttackSwing(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        ViaCompatibility.flushAttackSwing(mc.thePlayer.sendQueue.getNetworkManager());
    }

    @Inject(method = "rightClickMouse", at = @At("TAIL"))
    private void onyx$useOffhand(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        MovingObjectPosition hit = mc.objectMouseOver;
        boolean untargeted = hit == null || hit.typeOfHit == MovingObjectType.MISS
                || (hit.typeOfHit == MovingObjectType.BLOCK && mc.theWorld.isAirBlock(hit.getBlockPos()));
        if (untargeted && mc.thePlayer.getHeldItem() == null) {
            ViaOffhand.useFallback(mc);
        }
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;sendUseItem(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z"))
    private boolean onyx$useItemAfterTarget(PlayerControllerMP controller, EntityPlayer player, World world, ItemStack main) {
        if (player instanceof EntityPlayerSP && ViaOffhand.supports((EntityPlayerSP) player) && !controller.isSpectator()) {
            Minecraft mc = (Minecraft) (Object) this;
            if (ViaOffhand.hasLocalSwordPose(player, main)) {
                if (ViaOffhand.useFallback(mc)) {
                    ViaOffhand.clearSwordPose();
                } else {
                    ViaOffhand.beginSwordPose((EntityPlayerSP) player, main);
                }
                return false;
            }
            if (main.getItem() instanceof ItemBlock && ViaOffhand.useFallback(mc)) {
                return false;
            }
        }
        return controller.sendUseItem(player, world, main);
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;onPlayerRightClick(Lnet/minecraft/client/entity/EntityPlayerSP;Lnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/Vec3;)Z"))
    private boolean onyx$useOffhandOnBlock(PlayerControllerMP controller, EntityPlayerSP player, WorldClient world,
                                          ItemStack main, BlockPos pos, EnumFacing face, Vec3 hit) {
        Minecraft mc = (Minecraft) (Object) this;
        if (main == null && player.isSneaking() && ViaOffhand.useFallback(mc)) {
            // Sneaking with an occupied offhand bypasses block activation on the modern server.
            return false;
        }
        boolean used = controller.onPlayerRightClick(player, world, main, pos, face, hit);
        // The legacy block interaction returns early when the main hand is empty.
        if (!used && main == null) {
            ViaOffhand.useFallback(mc);
        }
        if (used) {
            ViaOffhand.clearSwordPose();
        }
        return used;
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;isPlayerRightClickingOnEntity(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/MovingObjectPosition;)Z"))
    private boolean onyx$interactAtEntity(PlayerControllerMP controller, EntityPlayer player, Entity target,
                                          MovingObjectPosition hit) {
        boolean used = controller.isPlayerRightClickingOnEntity(player, target, hit);
        if (used) {
            ViaOffhand.clearSwordPose();
        }
        return used;
    }

    @Redirect(method = "rightClickMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;interactWithEntitySendPacket(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)Z"))
    private boolean onyx$useOffhandOnEntity(PlayerControllerMP controller, EntityPlayer player, Entity target) {
        boolean used = controller.interactWithEntitySendPacket(player, target);
        if (used) {
            ViaOffhand.clearSwordPose();
        } else if (player.getHeldItem() == null) {
            ViaOffhand.useFallback((Minecraft) (Object) this);
        }
        return used;
    }

    @Inject(method = "displayGuiScreen", at = @At("RETURN"))
    private void onyx$screenOpened(GuiScreen requested, CallbackInfo ci) {
        ViaOffhand.clearSwordPose();
        GuiOpenAnimation.opened(currentScreen);
    }

    /** Replaces the vanilla Mojang splash with the animated NeoRaven loading screen. */
    @Inject(method = "drawSplashScreen", at = @At("HEAD"), cancellable = true)
    private void neoraven$earlySplash(net.minecraft.client.renderer.texture.TextureManager textures,
                                      CallbackInfo ci) {
        xyz.daikirai.gui.onyx.menu.EarlySplash.draw(textures);
        ci.cancel();
    }

    /** Prepares the loading screen as soon as the display exists, before Forge's splash worker. */
    @Inject(method = "createDisplay", at = @At("RETURN"))
    private void neoraven$splashPrepare(CallbackInfo ci) {
        xyz.daikirai.gui.onyx.menu.StartupAnimation.prepare();
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleInput()V"))
    private void onyx$screenInput(GuiScreen screen) throws IOException {
        GuiFont.begin();
        try {
            screen.handleInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"))
    private void onyx$mouseInput(GuiScreen screen) throws IOException {
        GuiFont.begin();
        try {
            screen.handleMouseInput();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;updateScreen()V"))
    private void onyx$screenTick(GuiScreen screen) {
        GuiFont.begin();
        try {
            screen.updateScreen();
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = {"displayGuiScreen", "resize"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiScreen;setWorldAndResolution(Lnet/minecraft/client/Minecraft;II)V"))
    private void onyx$screenInit(GuiScreen screen, Minecraft mc, int width, int height) {
        GuiFont.begin();
        try {
            screen.setWorldAndResolution(mc, width, height);
        } finally {
            GuiFont.end();
        }
    }

    @Redirect(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/EntityRenderer;getMouseOver(F)V"))
    private void clickgui$getMouseOver(EntityRenderer renderer, float partialTicks) {
        Hooks.getMouseOver();
    }

    /** Caps animated menus at 120 FPS, respecting lower player limits. */
    @Inject(method = "getLimitFramerate", at = @At("HEAD"), cancellable = true)
    private void onyx$menuFramerate(CallbackInfoReturnable<Integer> cir) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!Display.isVisible()) {
            cir.setReturnValue(Integer.valueOf(HIDDEN_FPS));
            return;
        }
        int limit = minecraft.gameSettings == null ? 120 : minecraft.gameSettings.limitFramerate;
        // The title screen and GUI overlays must not run an unlimited glass/animation loop.
        if (minecraft.theWorld == null || minecraft.currentScreen != null) {
            limit = Math.min(limit, 120);
        }
        cir.setReturnValue(Integer.valueOf(limit));
    }
}
