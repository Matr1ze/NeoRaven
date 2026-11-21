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
 * <p>Instant instead shift-clicks every nonempty chest slot in one client call after the initial
 * contents packet is applied, without filter/start/click delays. Server acknowledgement is unchanged.</p>
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

    private static ButtonSetting instant, autoClose, ignoreTrash, chestIntegrity, armor, blocks, bow, food,
            potions, projectiles, sword, tools;
    private static SliderSetting startDelay, minDelay, maxDelay;

    private final List<Integer> queue = new ArrayList<Integer>();
    private long nextActionAt;
    private boolean stealing;
    private LootSession lootSession;
    private ContainerChest activeContainer;
    private ContainerChest contentsContainer, instantContainer;
    private ContainerChest predictionContainer;
    private ItemStack[] initialSlots, predictedSlots;
    private boolean[] predictionChanged;
    private long predictionUntil;

    /** Read-only HUD history; it survives closing the container for the exit animation. */
    public static final class LootSession {
        public final ContainerChest container;
        public final int total;
        public final List<LootTransfer> transfers = new ArrayList<LootTransfer>();
        public int processed;
        public int nextSlot;
        public boolean finished;
        public boolean completed;
        public boolean replacedScreen;
        public long endedAt;

        private LootSession(ContainerChest container, List<Integer> slots) {
            this.container = container;
            this.total = slots.size();
            this.nextSlot = slots.get(0).intValue();
        }
    }

    public static final class LootTransfer {
        public final int slot;
        public final ItemStack stack;
        public final long time = System.currentTimeMillis();

        private LootTransfer(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    /** Includes the first rendered frame, before the first player tick starts the delay. */
    public LootSession visualSession() {
        if (!isEnabled() || mc.thePlayer == null
                || !(mc.thePlayer.openContainer instanceof ContainerChest)) return null;
        ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        if (chestIntegrity.isToggled() && (inLobby() || blockedChest(container))) return null;
        if (lootSession == null || lootSession.container != container) {
            List<Integer> slots = slotsToSteal(container.getLowerChestInventory(), mc.thePlayer.inventory);
            if (slots.isEmpty()) return null;
            lootSession = new LootSession(container, slots);
        }
        return lootSession.finished ? null : lootSession;
    }

    public ChestStealerModule(int key) {
        super("ChestStealer", Category.player, key);
        instance = this;
    }

    @Override
    public void link() {
        instant = (ButtonSetting) ModuleManager.setting(this, "Instant");
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
    public void onEnable() {
        reset();
        lootSession = null;
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        if (lootSession != null && !lootSession.finished) {
            lootSession.finished = true;
            lootSession.endedAt = System.currentTimeMillis();
        }
        queue.clear();
        stealing = false;
        nextActionAt = 0L;
        activeContainer = null;
        contentsContainer = instantContainer = null;
        clearPrediction();
    }

    /** Runs after vanilla applied S30 on the client thread, without waiting for a player tick. */
    public static void contentsReceived(int windowId) {
        ChestStealerModule module = instance;
        if (module == null || !module.isEnabled() || mc.thePlayer == null
                || !(mc.thePlayer.openContainer instanceof ContainerChest)
                || mc.thePlayer.openContainer.windowId != windowId) return;
        module.contentsContainer = (ContainerChest) mc.thePlayer.openContainer;
        module.clearPrediction(); // A new full snapshot is authoritative, including rejection resyncs.
        if (instant.isToggled()) {
            ContainerChest container = module.contentsContainer;
            ItemStack[] before = copySlots(container);
            module.onPreUpdate();
            if (module.instantContainer == container && mc.thePlayer.openContainer == container)
                module.rememberPrediction(container, before);
        }
    }

    private static ItemStack[] copySlots(ContainerChest container) {
        ItemStack[] stacks = new ItemStack[container.inventorySlots.size()];
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = container.getSlot(i).getStack();
            stacks[i] = stack == null ? null : stack.copy();
        }
        return stacks;
    }

    private void clearPrediction() {
        predictionContainer = null; initialSlots = predictedSlots = null;
        predictionChanged = null; predictionUntil = 0;
    }

    private void rememberPrediction(ContainerChest container, ItemStack[] before) {
        predictionContainer = container; initialSlots = before; predictedSlots = copySlots(container);
        predictionChanged = new boolean[before.length]; predictionUntil = System.currentTimeMillis() + 5000;
        for (int i = 0; i < before.length; i++) predictionChanged[i] = !ItemStack.areItemStacksEqual(before[i], predictedSlots[i]);
    }

    /** addCraftingToCrafters sends S30, then initial S2F mirrors before click confirmations.
     * Keep only this batch's changed slots predicted while those exact old mirrors arrive.
     * A different server value, confirmation, or full snapshot immediately ends that protection. */
    public static void slotReceived(int windowId, int slot, ItemStack incoming) {
        ChestStealerModule module = instance;
        if (module == null || module.predictionContainer == null) return;
        if (mc.thePlayer == null || mc.thePlayer.openContainer != module.predictionContainer
                || System.currentTimeMillis() > module.predictionUntil) { module.clearPrediction(); return; }
        if (windowId != module.predictionContainer.windowId || slot < 0 || slot >= module.initialSlots.length
                || !module.predictionChanged[slot]) return;
        if (!ItemStack.areItemStacksEqual(incoming, module.initialSlots[slot])) {
            module.predictionChanged[slot] = false; return;
        }
        ItemStack expected = module.predictedSlots[slot];
        module.predictionContainer.putStackInSlot(slot, expected == null ? null : expected.copy());
    }

    public static void confirmationReceived(int windowId) {
        ChestStealerModule module = instance;
        if (module != null && module.predictionContainer != null && module.predictionContainer.windowId == windowId)
            module.clearPrediction();
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
        if (activeContainer != container) {
            queue.clear();
            stealing = false;
            nextActionAt = 0L;
            activeContainer = container;
            if (lootSession != null && lootSession.container != container) {
                lootSession.finished = true;
                lootSession.endedAt = System.currentTimeMillis();
                lootSession = null;
            }
        }
        long now = System.currentTimeMillis();
        if (instant.isToggled()) {
            takeInstant(container, now);
            return;
        }
        instantContainer = null;
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
            if (lootSession == null || lootSession.finished) lootSession = new LootSession(container, slots);
            stealing = true;
            nextActionAt = now + (long) startDelay.getInput();
            return;
        }
        if (now < nextActionAt) {
            return;
        }
        if (queue.isEmpty()) {
            lootSession.finished = true;
            lootSession.completed = true;
            lootSession.endedAt = now;
            // A replacement is a transient stealing view, so finish its real container as well.
            if (autoClose.isToggled() || lootSession.replacedScreen
                    && xyz.daikirai.module.render.WatermarkModule.chestStealerEnabled()) {
                mc.thePlayer.closeScreen();
            }
            reset();
            return;
        }
        int slot = queue.remove(0).intValue();
        takeSlot(container, slot);
        nextActionAt = now + jitter();
    }

    private void takeSlot(ContainerChest container, int slot) {
        ItemStack before = container.getLowerChestInventory().getStackInSlot(slot);
        before = before == null ? null : before.copy();
        ScriptProtected.guiClick(slot, 0, 1);
        ItemStack after = container.getLowerChestInventory().getStackInSlot(slot);
        int moved = before == null ? 0 : before.stackSize - (after == null ? 0 : after.stackSize);
        if (moved > 0) {
            before.stackSize = moved;
            lootSession.transfers.add(new LootTransfer(slot, before));
        }
        lootSession.processed++;
        lootSession.nextSlot = queue.isEmpty() ? slot : queue.get(0).intValue();
    }

    private void takeInstant(ContainerChest container, long now) {
        if (instantContainer == container || chestIntegrity.isToggled() && (inLobby() || blockedChest(container))) return;
        List<Integer> slots = slotsToSteal(container.getLowerChestInventory(), mc.thePlayer.inventory);
        // An open-window packet precedes its contents. Do not close that temporary empty shell.
        if (slots.isEmpty() && contentsContainer != container) return;
        instantContainer = container;
        queue.clear();
        queue.addAll(slots);
        stealing = false;
        if (!slots.isEmpty()) {
            lootSession = new LootSession(container, slots);
            // One bounded shift-click per nonempty chest slot, all in this same client call.
            while (!queue.isEmpty() && mc.thePlayer.openContainer == container)
                takeSlot(container, queue.remove(0).intValue());
            lootSession.finished = true;
            lootSession.completed = queue.isEmpty();
            lootSession.endedAt = now;
        }
        queue.clear();
        if (mc.thePlayer.openContainer == container && (autoClose.isToggled()
                || lootSession != null && lootSession.replacedScreen
                && xyz.daikirai.module.render.WatermarkModule.chestStealerEnabled())) mc.thePlayer.closeScreen();
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
        boolean all = instant != null && instant.isToggled();
        for (int i = 0; i < chest.getSizeInventory(); i++) {
            ItemStack stack = chest.getStackInSlot(i);
            if (stack == null || stack.getItem() == null) {
                continue;
            }
            if (!all && ignoreTrash.isToggled() && isTrash(stack, chest, inventory)) {
                continue;
            }
            if (all || wantedCategory(stack) || !ignoreTrash.isToggled()) {
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
