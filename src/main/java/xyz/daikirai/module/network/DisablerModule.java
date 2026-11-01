package xyz.daikirai.module.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C16PacketClientStatus;
import xyz.daikirai.client.Chat;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.PacketUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Port of Leader-Lite's {@code Disabler} ({@code leader.module.modules.misc.Disabler}).
 *
 * <p>Two things live here, exactly like upstream:</p>
 * <ul>
 *     <li><b>Prediction inventory</b> — outgoing inventory packets ({@code C16} status, {@code C0E}
 *     click, {@code C0D} close) are swallowed and replayed in one batch when the window closes or
 *     the player respawns, so the server never sees the intermediate inventory states. That is what
 *     makes Vanilla-InvWalk / Silent-InvManager safe to use.</li>
 *     <li><b>C09</b> — a held-item-swap packet is made to look like a normal hotbar shuffle by
 *     picking a second sword slot; {@link #getC09TargetSlot()}, {@link #getSwapSlot()} and
 *     {@link #getAltSlot(int)} are the hooks other modules call.</li>
 * </ul>
 *
 * <p>1.8.9 notes: {@code PacketEvent SEND} → {@link #onSendPacket(Packet)}; the replay goes through
 * {@link PacketUtils#sendNoEvent(Packet)}, which skips the event chain (so the module does not
 * re-swallow its own packets). The compass guard is kept because Hypixel's lobby compass means the
 * player is not in a match.</p>
 */
public final class DisablerModule extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random RANDOM = new Random();

    public static DisablerModule instance;

    private static SliderSetting mode;
    private static ButtonSetting inventory;
    private static ButtonSetting c09;
    private static SliderSetting secondSwordSlot;

    private final List<Packet<?>> inventoryPackets = new ArrayList<Packet<?>>();
    private static boolean c09Warned;

    public DisablerModule(int key) {
        super("Disabler", Category.other, key);
        instance = this;
    }

    @Override
    public void link() {
        mode = (SliderSetting) ModuleManager.setting(this, "Mode");
        inventory = (ButtonSetting) ModuleManager.setting(this, "Inventory");
        c09 = (ButtonSetting) ModuleManager.setting(this, "C09");
        secondSwordSlot = (SliderSetting) ModuleManager.setting(this, "SecondSwordSlot");
    }

    private static boolean prediction() {
        return mode == null || Math.round(mode.getInput()) == 0;
    }

    @Override
    public String getInfo() {
        return mode == null ? "Prediction" : mode.options[(int) Math.round(mode.getInput())];
    }

    @Override
    public void onEnable() {
        if (prediction() && inventory != null && inventory.isToggled()) {
            Chat.print("§dDisabler: §7inventory packets are now predicted; Vanilla InvWalk / Silent InvManager are safe to use.");
        }
        c09Warned = false;
        reset();
    }

    @Override
    public void onDisable() {
        flush();
        reset();
    }

    private void reset() {
        inventoryPackets.clear();
        c09Warned = false;
    }

    /** Lobby guard: a compass in the hotbar means this is not a match. */
    private static boolean hasCompass() {
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getUnlocalizedName() != null
                    && stack.getUnlocalizedName().toLowerCase().contains("compass")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onSendPacket(Packet<?> packet) {
        if (!isEnabled() || mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return true;
        }
        if (!prediction() || inventory == null || !inventory.isToggled() || hasCompass()) {
            return true;
        }
        if (packet instanceof C16PacketClientStatus) {
            C16PacketClientStatus status = (C16PacketClientStatus) packet;
            if (status.getStatus() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN) {
                flush();
                return true;
            }
            inventoryPackets.add(packet);
            return false;
        }
        if (packet instanceof C0DPacketCloseWindow) {
            flush();
            return true;
        }
        if (packet instanceof C0EPacketClickWindow) {
            inventoryPackets.add(packet);
            return false;
        }
        return true;
    }

    /** Replays the queued inventory packets outside the event chain. */
    private void flush() {
        if (inventoryPackets.isEmpty()) {
            return;
        }
        for (Packet<?> packet : inventoryPackets) {
            PacketUtils.sendNoEvent(packet);
        }
        inventoryPackets.clear();
    }

    // ------------------------------------------------------------------ //
    // C09 helpers used by other modules
    // ------------------------------------------------------------------ //

    /** The second-sword hotbar slot a C09 swap should use, or {@code -1} when it is not applicable. */
    public static int getC09TargetSlot() {
        if (mode == null) {
            return -1;
        }
        if (instance == null || !instance.isEnabled() || c09 == null || !c09.isToggled()) {
            c09Warned = false;
            return -1;
        }
        int preferredSlot = (int) Math.round(secondSwordSlot.getInput()) - 1;
        if (preferredSlot < 0 || preferredSlot > 8) {
            return -1;
        }
        if (mc.thePlayer == null || mc.thePlayer.inventory == null
                || preferredSlot == mc.thePlayer.inventory.currentItem) {
            return -1;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
        if (stack != null && stack.getItem() instanceof ItemSword) {
            c09Warned = false;
            return preferredSlot;
        }
        if (!c09Warned) {
            int mainSword = findMainSwordSlot();
            int secondSword = findSecondSwordSlot(mainSword);
            if (secondSword == -1) {
                c09Warned = true;
                Chat.print("§dDisabler: §7no second sword in the inventory, C09 swaps use the fallback slot.");
            }
        }
        return -1;
    }

    /** A random hotbar slot that is not the held one (upstream's fallback). */
    public static int getSwapSlot() {
        int target = getC09TargetSlot();
        if (target >= 0) {
            return target;
        }
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return 0;
        }
        int slot = RANDOM.nextInt(9);
        while (slot == mc.thePlayer.inventory.currentItem) {
            slot = RANDOM.nextInt(9);
        }
        return slot;
    }

    /** Upstream's {@code handle % 8 + 1} alternative slot. */
    public static int getAltSlot(int handle) {
        int target = getC09TargetSlot();
        if (target >= 0) {
            return target;
        }
        return handle % 8 + 1;
    }

    private static int findMainSwordSlot() {
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && ItemUtils.isSword(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Best-damage sword outside {@code excludeSlot}, ties broken randomly (upstream behaviour). */
    public static int findSecondSwordSlot(int excludeSlot) {
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            return -1;
        }
        double bestDamage = 0.0D;
        List<Integer> bestSlots = new ArrayList<Integer>();
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) {
                continue;
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !ItemUtils.isSword(stack)) {
                continue;
            }
            double damage = ItemUtils.attackDamage(stack);
            if (damage >= bestDamage && damage > 0.0D) {
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlots.clear();
                }
                bestSlots.add(Integer.valueOf(i));
            }
        }
        if (bestSlots.isEmpty()) {
            return -1;
        }
        if (bestSlots.size() == 1) {
            return bestSlots.get(0).intValue();
        }
        return bestSlots.get(RANDOM.nextInt(bestSlots.size())).intValue();
    }
}
