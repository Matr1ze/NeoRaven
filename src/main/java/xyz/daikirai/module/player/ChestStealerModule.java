package xyz.daikirai.module.player;

import xyz.daikirai.client.Hypixel;
import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.script.ScriptProtected;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MathUtils;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of OpenExpo's {@code Expo.module.impl.player.ChestStealer}.
 *
 * <p>Takes the useful stacks out of an open chest with shift-clicks, one click per delay, and closes
 * the chest when there is nothing left to take. Which stacks count as useful is the category switch
 * list from the original (armour / blocks / bow / food / potions / projectiles / sword / tools);
 * {@code Ignore-trash} additionally drops the candidates that are no better than what the chest or
 * the inventory already offers - a worse sword, a second rod, normal food once a gap is in the bag,
 * and so on.</p>
 *
 * <p>The original ran the clicks on a background thread with {@code Thread.sleep}; this port runs
 * them from the client tick with a deadline, which keeps {@code windowClick} on the client thread
 * (the same reason the client's own InvManager does it that way). {@code Silent} only controlled a
 * "Stealing..." label in the original and is not ported. {@code Chest-integrity-check} still refuses
 * ender/team chests and the lobby, which is what it guarded upstream.</p>
 */
public class ChestStealerModule extends Module {

    /** Chest names that must not be looted (ender chests, Bed Wars team chests, ...). */
    private static final String[] BLOCKED_CHESTS = {"ender", "team", "island", "末影", "队伍"};

    public static ChestStealerModule instance;

    private static ButtonSetting autoClose, ignoreTrash, chestIntegrity, armor, blocks, bow, food,
            potions, projectiles, sword, tools;
    private static SliderSetting startDelay, minDelay, maxDelay;

    private final List<Integer> queue = new ArrayList<Integer>();
    private long nextActionAt;
    private boolean stealing;

    public ChestStealerModule(int key) {
        super("ChestStealer", Category.player, key);
        instance = this;
    }

    @Override
    public void link() {
        autoClose = (ButtonSetting) ModuleManager.setting(this, "Auto close");
        ignoreTrash = (ButtonSetting) ModuleManager.setting(this, "Ignore trash");
        chestIntegrity = (ButtonSetting) ModuleManager.setting(this, "Chest integrity");
        armor = (ButtonSetting) ModuleManager.setting(this, "Pick armor");
        blocks = (ButtonSetting) ModuleManager.setting(this, "Pick blocks");
        bow = (ButtonSetting) ModuleManager.setting(this, "Pick bow");
        food = (ButtonSetting) ModuleManager.setting(this, "Pick food");
        potions = (ButtonSetting) ModuleManager.setting(this, "Pick potions");
        projectiles = (ButtonSetting) ModuleManager.setting(this, "Pick projectiles");
        sword = (ButtonSetting) ModuleManager.setting(this, "Pick sword");
        tools = (ButtonSetting) ModuleManager.setting(this, "Pick tools");
        startDelay = (SliderSetting) ModuleManager.setting(this, "Start delay");
        minDelay = (SliderSetting) ModuleManager.setting(this, "Min delay");
        maxDelay = (SliderSetting) ModuleManager.setting(this, "Max delay");
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        queue.clear();
        stealing = false;
        nextActionAt = 0L;
    }

    @Override
    public void onPreUpdate() {
        if (mc.thePlayer == null || mc.playerController == null) {
            reset();
            return;
        }
        if (!(mc.thePlayer.openContainer instanceof ContainerChest)) {
            reset();
            return;
        }
        ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        long now = System.currentTimeMillis();
        if (!stealing) {
            if (chestIntegrity.isToggled() && (inLobby() || blockedChest(container))) {
                return;
            }
            List<Integer> slots = slotsToSteal(container.getLowerChestInventory(), mc.thePlayer.inventory);
            if (slots.isEmpty()) {
                if (autoClose.isToggled()) {
                    mc.thePlayer.closeScreen();
                }
                return;
            }
            queue.clear();
            queue.addAll(slots);
            stealing = true;
            nextActionAt = now + (long) startDelay.getInput();
            return;
        }
        if (now < nextActionAt) {
            return;
        }
        if (queue.isEmpty()) {
            if (autoClose.isToggled()) {
                mc.thePlayer.closeScreen();
            }
            reset();
            return;
        }
        int slot = queue.remove(0).intValue();
        ScriptProtected.guiClick(slot, 0, 1);
        nextActionAt = now + jitter();
    }

    private static long jitter() {
        double min = Math.min(minDelay.getInput(), maxDelay.getInput());
        double max = Math.max(minDelay.getInput(), maxDelay.getInput());
        double value = min >= max ? min : min + (max - min) * MathUtils.RANDOM.nextDouble();
        return Math.max(0L, Math.round(value));
    }

    private static boolean inLobby() {
        return Hypixel.state == Hypixel.State.LOBBY || Hypixel.skywars(1);
    }

    private static boolean blockedChest(ContainerChest container) {
        String name = container.getLowerChestInventory().getName();
        if (name == null || name.isEmpty()) {
            return false;
        }
        String lower = name.toLowerCase();
        for (String blocked : BLOCKED_CHESTS) {
            if (lower.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ //
    // What to take
    // ------------------------------------------------------------------ //

    /** Chest slots worth shift-clicking, in chest order. */
    public static List<Integer> slotsToSteal(IInventory chest, IInventory inventory) {
        List<Integer> slots = new ArrayList<Integer>();
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack stack = chest.getStackInSlot(i);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (ignoreTrash.isToggled() && isTrash(stack, chest, inventory)) {
                continue;
            }
            if (wantedCategory(stack) || !ignoreTrash.isToggled()) {
                slots.add(Integer.valueOf(i));
            }
        }
        return slots;
    }

    /** Whether the stack belongs to one of the enabled categories. */
    private static boolean wantedCategory(ItemStack stack) {
        Item item = stack.getItem();
        if (armor.isToggled() && item instanceof ItemArmor) {
            return true;
        }
        if (blocks.isToggled() && item instanceof ItemBlock
                && BlockUtils.isPlaceable(((ItemBlock) item).getBlock())) {
            return true;
        }
        if (bow.isToggled() && (item instanceof ItemBow || item == Items.arrow)) {
            return true;
        }
        if (food.isToggled() && item instanceof ItemFood) {
            return true;
        }
        if (potions.isToggled() && item instanceof ItemPotion) {
            return true;
        }
        if (projectiles.isToggled() && (item instanceof ItemEnderPearl || item instanceof ItemEgg
                || item instanceof ItemSnowball || item instanceof ItemFishingRod)) {
            return true;
        }
        if (sword.isToggled() && item instanceof ItemSword) {
            return true;
        }
        return tools.isToggled() && (item instanceof ItemPickaxe || item instanceof ItemAxe
                || item instanceof ItemSpade);
    }

    /**
     * {@code Ignore-trash}: skip a stack that is no better than what the inventory already has, and
     * skip the weaker duplicates inside the chest itself so only the best one of a kind is clicked.
     */
    private static boolean isTrash(ItemStack stack, IInventory chest, IInventory inventory) {
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            int type = ((ItemArmor) item).armorType;
            double value = ItemUtils.armorValue(stack);
            return value <= bestArmor(type, inventory) || value < bestArmor(type, chest);
        }
        if (item instanceof ItemSword) {
            double value = ItemUtils.attackDamage(stack);
            return value <= bestSword(inventory) || value < bestSword(chest);
        }
        if (item instanceof ItemBow) {
            double value = ItemUtils.bowEnchantLevels(stack);
            return value <= bestBow(inventory) || value < bestBow(chest);
        }
        if (item instanceof ItemFood) {
            // Expo's rule: once the inventory holds a golden apple, every other food is trash.
            return item != Items.golden_apple && count(inventory, Items.golden_apple) > 0;
        }
        if (item instanceof ItemPickaxe || item instanceof ItemAxe || item instanceof ItemSpade) {
            double value = toolEfficiency(stack);
            return value <= bestTool(stack, inventory) || value < bestTool(stack, chest);
        }
        if (item instanceof ItemFishingRod) {
            return count(inventory, Items.fishing_rod) > 0;
        }
        return false;
    }

    private static double toolEfficiency(ItemStack stack) {
        if (stack == null || !ItemUtils.hasDurability(stack, 0.3)) {
            return 0.0;
        }
        return stack.getItem().getStrVsBlock(stack, net.minecraft.init.Blocks.stone);
    }

    private static double bestArmor(int armorType, IInventory inventory) {
        double best = -1.0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            best = Math.max(best, armorValue(armorType, inventory.getStackInSlot(i)));
        }
        if (inventory instanceof net.minecraft.entity.player.InventoryPlayer) {
            net.minecraft.entity.player.InventoryPlayer player =
                    (net.minecraft.entity.player.InventoryPlayer) inventory;
            for (int i = 0; i < player.armorInventory.length; i++) {
                best = Math.max(best, armorValue(armorType, player.armorInventory[i]));
            }
        }
        return best;
    }

    private static double armorValue(int armorType, ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
            return -1.0;
        }
        if (((ItemArmor) stack.getItem()).armorType != armorType) {
            return -1.0;
        }
        return ItemUtils.armorValue(stack);
    }

    private static double bestSword(IInventory inventory) {
        double best = -1.0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemSword) {
                best = Math.max(best, ItemUtils.attackDamage(stack));
            }
        }
        return best;
    }

    private static double bestBow(IInventory inventory) {
        double best = -1.0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBow) {
                best = Math.max(best, ItemUtils.bowEnchantLevels(stack));
            }
        }
        return best;
    }

    /** Best efficiency among the tools of the same class as {@code like}. */
    private static double bestTool(ItemStack like, IInventory inventory) {
        double best = -1.0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack == null || stack.getItem().getClass() != like.getItem().getClass()) {
                continue;
            }
            best = Math.max(best, toolEfficiency(stack));
        }
        return best;
    }

    private static int count(IInventory inventory, Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() == item) {
                total++;
            }
        }
        return total;
    }
}
