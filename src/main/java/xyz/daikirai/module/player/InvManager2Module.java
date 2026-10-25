package xyz.daikirai.module.player;

import xyz.daikirai.module.Category;
import xyz.daikirai.module.Module;
import xyz.daikirai.module.ModuleManager;
import xyz.daikirai.module.setting.ButtonSetting;
import xyz.daikirai.module.setting.SliderSetting;
import xyz.daikirai.script.ScriptProtected;
import xyz.daikirai.util.BlockUtils;
import xyz.daikirai.util.ItemUtils;
import xyz.daikirai.util.MathUtils;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

import java.util.List;

/**
 * Port of OpenExpo's {@code Expo.module.impl.player.InvManager}, shipped as "InvManager2" because this
 * client already has an {@code InvManager}.
 *
 * <p>While the inventory screen is open it works through one click per delay: equip better armour,
 * drop the trash, then move the best item of each configured type into its hotbar slot. Compared to
 * the built-in {@code InvManager} it adds the per-category trash switches (projectiles / bow / normal
 * food / potions / tools), the block and arrow slot caps, a trash-throw budget, tool slots and an
 * "only sort once" latch - the setting list is the original's.</p>
 *
 * <p>Deliberate differences from the original: the clicks run from the client tick with a deadline
 * instead of a background thread (windowClick has to stay on the client thread); the {@code Silent}
 * mode is not ported (it opens the inventory by packet / on pickup / on a key, which needs a text
 * keybind setting this client does not have), so the module works in the original's default
 * {@code OPEN_INV} mode; and the original's obfuscated "best item" helpers are re-expressed with this
 * client's {@link ItemUtils} (attack damage, armour value, bow enchantments, tool efficiency).</p>
 */
public class InvManager2Module extends Module {

    /** Player-inventory container slots 5..8 are helmet, chestplate, leggings, boots. */
    private static final int ARMOR_FIRST = 5;
    /** Main inventory + hotbar inside the player container. */
    private static final int FIRST_SLOT = 9;
    private static final int LAST_SLOT = 45;

    public static InvManager2Module instance;

    private static ButtonSetting autoArmor, throwTrash, autoClose, onlySortOnce,
            onlyConfiguredIsTrash, projectilesIsTrash, normalFoodIsTrash, bowIsTrash, potionIsTrash,
            toolsAreTrash;
    private static SliderSetting swordSlot, projectilesSlot, blockSlot, foodSlot, bowSlot, pickaxeSlot,
            axeSlot, shovelSlot, potionSlot, fireballSlot, enderPearlSlot, shearsSlot,
            maxBlockSlots, maxArrowSlots, maxTrashThrows, startDelay, minDelay, maxDelay;

    private long nextActionAt;
    private boolean sorted;

    public InvManager2Module(int key) {
        super("InvManager2", Category.player, key);
        instance = this;
    }

    @Override
    public void link() {
        autoArmor = (ButtonSetting) ModuleManager.setting(this, "Auto armor");
        throwTrash = (ButtonSetting) ModuleManager.setting(this, "Throw trash");
        autoClose = (ButtonSetting) ModuleManager.setting(this, "Auto close");
        onlySortOnce = (ButtonSetting) ModuleManager.setting(this, "Only sort once");
        onlyConfiguredIsTrash = (ButtonSetting) ModuleManager.setting(this, "Only items configured are trash");
        projectilesIsTrash = (ButtonSetting) ModuleManager.setting(this, "Projectiles is trash");
        normalFoodIsTrash = (ButtonSetting) ModuleManager.setting(this, "Normal food is trash");
        bowIsTrash = (ButtonSetting) ModuleManager.setting(this, "Bow is trash");
        potionIsTrash = (ButtonSetting) ModuleManager.setting(this, "Potion is trash");
        toolsAreTrash = (ButtonSetting) ModuleManager.setting(this, "Tools are trash");
        swordSlot = (SliderSetting) ModuleManager.setting(this, "Sword slot");
        projectilesSlot = (SliderSetting) ModuleManager.setting(this, "Projectiles slot");
        blockSlot = (SliderSetting) ModuleManager.setting(this, "Block slot");
        foodSlot = (SliderSetting) ModuleManager.setting(this, "Food slot");
        bowSlot = (SliderSetting) ModuleManager.setting(this, "Bow slot");
        pickaxeSlot = (SliderSetting) ModuleManager.setting(this, "Pickaxe slot");
        axeSlot = (SliderSetting) ModuleManager.setting(this, "Axe slot");
        shovelSlot = (SliderSetting) ModuleManager.setting(this, "Shovel slot");
        potionSlot = (SliderSetting) ModuleManager.setting(this, "Potion slot");
        fireballSlot = (SliderSetting) ModuleManager.setting(this, "Fireball slot");
        enderPearlSlot = (SliderSetting) ModuleManager.setting(this, "Ender pearl slot");
        shearsSlot = (SliderSetting) ModuleManager.setting(this, "Shears slot");
        maxBlockSlots = (SliderSetting) ModuleManager.setting(this, "Max block slots");
        maxArrowSlots = (SliderSetting) ModuleManager.setting(this, "Max arrow slots");
        maxTrashThrows = (SliderSetting) ModuleManager.setting(this, "Max trash throws");
        startDelay = (SliderSetting) ModuleManager.setting(this, "Start delay");
        minDelay = (SliderSetting) ModuleManager.setting(this, "Min delay");
        maxDelay = (SliderSetting) ModuleManager.setting(this, "Max delay");
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        nextActionAt = 0L;
        sorted = false;
    }

    @Override
    public void onPreUpdate() {
        if (mc.thePlayer == null || mc.playerController == null) {
            reset();
            return;
        }
        if (!(mc.currentScreen instanceof GuiInventory)) {
            // Closing the screen re-arms "Only sort once".
            sorted = false;
            nextActionAt = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (nextActionAt == 0L) {
            nextActionAt = now + (long) startDelay.getInput();
            return;
        }
        if (now < nextActionAt) {
            return;
        }
        Click click = nextClick();
        if (click == null) {
            if (autoClose.isToggled()) {
                mc.thePlayer.closeScreen();
            }
            nextActionAt = 0L;
            return;
        }
        ScriptProtected.guiClick(click.slot, click.button, click.mode);
        nextActionAt = now + jitter();
    }

    private static long jitter() {
        double min = Math.min(minDelay.getInput(), maxDelay.getInput());
        double max = Math.max(minDelay.getInput(), maxDelay.getInput());
        double value = min >= max ? min : min + (max - min) * MathUtils.RANDOM.nextDouble();
        return Math.max(0L, Math.round(value));
    }

    /** One container click: slot, mouse button and the click mode (1 = shift, 2 = hotbar swap, 4 = drop). */
    private static final class Click {
        final int slot;
        final int button;
        final int mode;

        Click(int slot, int button, int mode) {
            this.slot = slot;
            this.button = button;
            this.mode = mode;
        }
    }

    /** Next click of this pass, or {@code null} when the inventory is clean and sorted. */
    private Click nextClick() {
        Container container = mc.thePlayer.openContainer;
        if (autoArmor.isToggled()) {
            Click armor = armorClick(container);
            if (armor != null) {
                return armor;
            }
        }
        if (throwTrash.isToggled()) {
            Click trash = trashClick(container);
            if (trash != null) {
                return trash;
            }
        }
        if (!(onlySortOnce.isToggled() && sorted)) {
            Click sort = sortClick(container);
            if (sort != null) {
                return sort;
            }
            sorted = true;
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    // Auto armor
    // ------------------------------------------------------------------ //

    /** Stack in a player-container slot (armour slots 5..8, inventory 9..44). */
    private static ItemStack stackAt(Container container, int slot) {
        return container.getSlot(slot).getStack();
    }

    /**
     * Two-step equip, like the original: drop the worse piece that is worn, then shift-click the
     * better one into the now empty slot.
     */
    private Click armorClick(Container container) {
        for (int type = 0; type < 4; type++) {
            int armorSlot = ARMOR_FIRST + type;
            ItemStack worn = stackAt(container, armorSlot);
            double wornValue = worn != null && worn.getItem() instanceof ItemArmor
                    ? ItemUtils.armorValue(worn) : -1.0;
            int bestSlot = -1;
            double bestValue = wornValue;
            for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
                ItemStack stack = stackAt(container, i);
                if (stack == null || !(stack.getItem() instanceof ItemArmor)) {
                    continue;
                }
                if (((ItemArmor) stack.getItem()).armorType != type) {
                    continue;
                }
                double value = ItemUtils.armorValue(stack);
                if (value > bestValue) {
                    bestValue = value;
                    bestSlot = i;
                }
            }
            if (bestSlot == -1) {
                continue;
            }
            if (worn != null) {
                return new Click(armorSlot, 1, 4);
            }
            return new Click(bestSlot, 0, 1);
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    // Trash
    // ------------------------------------------------------------------ //

    /** First slot whose stack the trash rules want gone, or {@code null}. */
    private Click trashClick(Container container) {
        int blocks = 0;
        int arrows = 0;
        int drops = 0;
        int blockLimit = (int) maxBlockSlots.getInput();
        int arrowLimit = (int) maxArrowSlots.getInput();
        int dropLimit = (int) maxTrashThrows.getInput();
        for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
            ItemStack stack = stackAt(container, i);
            if (stack == null) {
                continue;
            }
            Item item = stack.getItem();
            boolean drop = false;
            if (item instanceof ItemArmor) {
                drop = !isBestArmor(container, stack, (ItemArmor) item);
            } else if (item instanceof ItemSword) {
                drop = !isBestSword(container, stack);
            } else if (item instanceof ItemBow) {
                drop = bowIsTrash.isToggled();
            } else if (item == Items.arrow) {
                drop = arrows >= arrowLimit;
                arrows++;
            } else if (item instanceof ItemTool) {
                drop = toolsAreTrash.isToggled() || !isBestTool(container, stack);
            } else if (item instanceof ItemShears) {
                drop = !isBestShears(container, stack);
            } else if (item instanceof ItemFood) {
                drop = normalFoodIsTrash.isToggled() && item != Items.golden_apple;
            } else if (item instanceof ItemPotion) {
                drop = potionIsTrash.isToggled();
            } else if (isProjectile(item)) {
                drop = projectilesIsTrash.isToggled();
            } else if (item instanceof ItemBlock && BlockUtils.isPlaceable(((ItemBlock) item).getBlock())) {
                drop = blocks >= blockLimit;
                blocks++;
            } else if (!onlyConfiguredIsTrash.isToggled() && !isWhitelisted(item)) {
                drop = true;
            }
            if (!drop) {
                continue;
            }
            if (drops >= dropLimit) {
                return null;
            }
            return new Click(i, 1, 4);
        }
        return null;
    }

    private static boolean isBestArmor(Container container, ItemStack stack, ItemArmor armor) {
        double value = ItemUtils.armorValue(stack);
        for (int i = ARMOR_FIRST; i < LAST_SLOT; i++) {
            ItemStack other = stackAt(container, i);
            if (other == null || other == stack || !(other.getItem() instanceof ItemArmor)) {
                continue;
            }
            if (((ItemArmor) other.getItem()).armorType != armor.armorType) {
                continue;
            }
            if (ItemUtils.armorValue(other) > value) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBestSword(Container container, ItemStack stack) {
        double damage = ItemUtils.attackDamage(stack);
        for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
            ItemStack other = stackAt(container, i);
            if (other != null && other != stack && other.getItem() instanceof ItemSword
                    && ItemUtils.attackDamage(other) > damage) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBestTool(Container container, ItemStack stack) {
        double efficiency = efficiency(stack);
        for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
            ItemStack other = stackAt(container, i);
            if (other == null || other == stack || !(other.getItem() instanceof ItemTool)) {
                continue;
            }
            if (other.getItem().getClass() != stack.getItem().getClass()) {
                continue;
            }
            if (efficiency(other) > efficiency) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBestShears(Container container, ItemStack stack) {
        for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
            ItemStack other = stackAt(container, i);
            if (other != null && other != stack && other.getItem() instanceof ItemShears) {
                return false;
            }
        }
        return true;
    }

    private static double efficiency(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }
        return stack.getItem().getStrVsBlock(stack, net.minecraft.init.Blocks.stone);
    }

    private static boolean isProjectile(Item item) {
        return item instanceof ItemSnowball || item instanceof ItemEgg
                || item instanceof ItemFishingRod || item instanceof ItemEnderPearl
                || item instanceof ItemFireball;
    }

    /** Item kinds that are never treated as "unknown trash". */
    private static boolean isWhitelisted(Item item) {
        return item instanceof ItemPotion || item instanceof ItemTool || item instanceof ItemShears
                || item instanceof ItemSword || item instanceof ItemFood || item instanceof ItemBow
                || item instanceof ItemArmor || isProjectile(item) || item == Items.arrow
                || item instanceof ItemBlock;
    }

    // ------------------------------------------------------------------ //
    // Sorting
    // ------------------------------------------------------------------ //

    /** First configured hotbar slot that does not hold the best item of its type yet. */
    private Click sortClick(Container container) {
        Click click = moveBest(container, swordSlot, 0);
        if (click == null) click = moveBest(container, projectilesSlot, 1);
        if (click == null) click = moveBest(container, blockSlot, 2);
        if (click == null) click = moveBest(container, bowSlot, 3);
        if (click == null) click = moveBest(container, pickaxeSlot, 4);
        if (click == null) click = moveBest(container, axeSlot, 5);
        if (click == null) click = moveBest(container, shovelSlot, 6);
        if (click == null) click = moveBest(container, foodSlot, 7);
        if (click == null) click = moveBest(container, potionSlot, 8);
        if (click == null) click = moveBest(container, fireballSlot, 9);
        if (click == null) click = moveBest(container, enderPearlSlot, 10);
        if (click == null) click = moveBest(container, shearsSlot, 11);
        return click;
    }

    /** Best source slot for one configured type, as a hotbar swap into its slot. */
    private Click moveBest(Container container, SliderSetting slot, int kind) {
        int target = (int) slot.getInput() - 1;
        if (target < 0 || target > 8) {
            return null;
        }
        int bestSlot = -1;
        double bestScore = -1.0;
        for (int i = FIRST_SLOT; i < LAST_SLOT; i++) {
            ItemStack stack = stackAt(container, i);
            if (stack == null || !matchesKind(stack, kind)) {
                continue;
            }
            double score = score(stack, kind);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        if (bestSlot == -1 || bestSlot == 36 + target) {
            return null;
        }
        return new Click(bestSlot, target, 2);
    }

    private static boolean matchesKind(ItemStack stack, int kind) {
        Item item = stack.getItem();
        switch (kind) {
            case 0: return item instanceof ItemSword;
            case 1: return item instanceof ItemSnowball || item instanceof ItemEgg
                    || item instanceof ItemFishingRod;
            case 2: return item instanceof ItemBlock && BlockUtils.isPlaceable(((ItemBlock) item).getBlock());
            case 3: return item instanceof ItemBow;
            case 4: return item instanceof ItemPickaxe;
            case 5: return item instanceof ItemAxe;
            case 6: return item instanceof ItemSpade;
            case 7: return item instanceof ItemFood;
            case 8: return item instanceof ItemPotion && isSplashPotion(stack);
            case 9: return item instanceof ItemFireball;
            case 10: return item == Items.ender_pearl;
            case 11: return item instanceof ItemShears;
            default: return false;
        }
    }

    private static double score(ItemStack stack, int kind) {
        switch (kind) {
            case 0: return ItemUtils.attackDamage(stack);
            case 2: return stack.stackSize;
            case 3: return ItemUtils.bowEnchantLevels(stack);
            case 4:
            case 5:
            case 6: return efficiency(stack);
            case 7: // golden apples first, then the largest stack
                return (stack.getItem() == Items.golden_apple ? 1000.0 : 0.0) + stack.stackSize;
            case 1: // rods last, then the largest projectile stack
                return (stack.getItem() instanceof ItemFishingRod ? 0.0 : 100.0) + stack.stackSize;
            default: return stack.stackSize;
        }
    }

    private static boolean isSplashPotion(ItemStack stack) {
        List<?> effects = ((ItemPotion) stack.getItem()).getEffects(stack);
        return ItemPotion.isSplash(stack.getMetadata()) && effects != null;
    }
}
