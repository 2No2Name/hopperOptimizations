package hopperOptimizations.utils.inventoryOptimizer;

import hopperOptimizations.settings.Settings;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

//Don't store instances of InventoryOptimizer, unless you sync with the corresponding inventory!
//Instances may suddenly become invalid due to unloading or turning the setting off
public class InventoryOptimizer {
    public static final int MAX_INV_SIZE = 32;

    private final InventoryListOptimized stackList;

    //Slot mask format: one bit for each slot, 1 if slot has this item, 0 if slot doesn't have the item
    //upper bits unused if inventory smaller than 32 slots
    //not useful for renamed items of the same type
    private final Reference2IntOpenHashMap<Item> itemToSlotMask;
    //one bit for each slot, 1 if slot is completely full, 0 if slot is not completely full
    private final int slotMask;
    private int slotFullMask;
    //number of inventory slots are occupied with stacks of a given size: e.g. 64->6, 16->1, 1->3
    private final Map<Integer, Integer> stackSizeToSlotCount;

    private final SidedInventory sidedInventory; //only use when required, inventory handling should be mostly independent from the container
    //number of slots
    private final int totalSlots;
    private int slotOccupiedMask;
    //number of changes to the inventory contents - useful for realizing inventory hasn't changed
    private int inventoryChanges;
    //item count for comparators, items with <64 max stack size already weighted higher
    private int weightedItemCount;
    //lower signal strength override for a short moment to make comparators send updates at their outputs
    private int fakeSignalStrength;

    public InventoryOptimizer(InventoryListOptimized stackList, Inventory inventory) {
        this.stackList = stackList;
        this.stackSizeToSlotCount = new HashMap<>();
        this.itemToSlotMask = new Reference2IntOpenHashMap<>();
        this.slotMask = (1 << stackList.size()) - 1;
        this.sidedInventory = inventory instanceof SidedInventory ? (SidedInventory) inventory : null;
        this.totalSlots = size();

        if (Settings.debugOptimizedInventories && this.sidedInventory != null && !(this.sidedInventory instanceof ShulkerBoxBlockEntity)) {
            //Shulkerbox restrictions are slot independent.
            //Slot dependent restrictions aren't checked atm, since there is no large inventory that has those.
            //OptimizedInventory doesn't seem viable for small inventories. - maybe add a version without complex datastructures for those
            throw new UnsupportedOperationException("Implement OptimizedInventory with more complex item insert conditions before using those.");
        }

        this.fakeSignalStrength = -1;
        this.inventoryChanges = 0;
        this.recalculate();

    }

    public InventoryOptimizer() {
        assert this instanceof DoubleInventoryOptimizer;
        this.stackList = null;
        this.itemToSlotMask = null;
        this.slotMask = 0;
        this.sidedInventory = null;
        this.stackSizeToSlotCount = null;
        this.totalSlots = 0;
    }


    public static boolean areItemsAndTagsEqual(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsEqual(a, b)) return false;
        return ItemStack.areTagsEqual(a, b);
    }

    //todo overwrite in DoubleInventoryOptimizer
    public void remove() {
        this.stackList.removeOptimizer(this);
    }

    //todo overwrite in DoubleInventoryOptimizer
    public boolean isRemoved() {
        return !this.stackList.optimizerIs(this);
    }

    private void consistencyCheck() {
        assert !(this instanceof DoubleInventoryOptimizer);
        //this is code from recalculate, but instead of changing anything, we just check if the results are conflicting
        try {
            int occupiedSlots = 0;
            int firstFreeSlot = this.totalSlots;
            int firstOccupiedSlot = this.totalSlots;
            int totalSlots = size();
            int weightedItemCount = 0;
            Map<Integer, Integer> stackSizeToSlotCount = new HashMap<>();


            for (int i = 0; i < totalSlots; i++) {
                ItemStack stack = getSlot(i);
                if (!stack.isEmpty()) {
                    weightedItemCount += stack.getCount() * (int) (64F / stack.getMaxCount());
                    stackSizeToSlotCount.put(stack.getMaxCount(), stackSizeToSlotCount.getOrDefault(stack.getMaxCount(), 0) + 1);

                    if (firstOccupiedSlot >= this.totalSlots)
                        firstOccupiedSlot = i;
                    occupiedSlots++;
                    if (stack.getCount() >= stack.getMaxCount()) ;

                } else if (firstFreeSlot >= this.totalSlots) {
                    firstFreeSlot = i;
                }
            }
            int signal1 = MathHelper.floor(this.weightedItemCount / ((float) this.totalSlots * 64) * 14 + (occupiedSlots == 0 ? 0 : 1));
            int signal2 = calculateComparatorOutput();
            if (this.weightedItemCount != weightedItemCount || signal1 != signal2)
                throw new IllegalStateException("comparator output wrong");
            if (!this.stackSizeToSlotCount.equals(stackSizeToSlotCount))
                throw new IllegalStateException("stacksize slot counts wrong");

        } catch (IllegalStateException e) {
            this.remove();
            Text text = new LiteralText("Detected broken optimizer ( " + e.getMessage() + ") at " + Arrays.toString(e.getStackTrace()));
        }
    }

    private int calculateComparatorOutput() {
        assert !(this instanceof DoubleInventoryOptimizer);
        int int_1 = 0;
        float float_1 = 0.0F;

        for (ItemStack itemStack_1 : this.stackList) {
            if (!itemStack_1.isEmpty()) {
                float_1 += (float) itemStack_1.getCount() / (float) Math.min(64, itemStack_1.getMaxCount());
                ++int_1;
            }
        }

        float_1 /= (float) this.stackList.size();
        return MathHelper.floor(float_1 * 14.0F) + (int_1 > 0 ? 1 : 0);
    }

    public void onItemStackCountChange(ItemStack itemStack, int slotIndex, int oldCount, int newCount) {
        assert !Settings.debugOptimizedInventories || this.getSlot(slotIndex) == itemStack;
        this.onStackChange(slotIndex, null, newCount);
    }

    protected ItemStack getSlot(int index) {
        return this.stackList.get(index);
    }

    protected int size() {
        return this.stackList.size();
    }

    public int getInventoryChangeCount() {
        return inventoryChanges;
    }

    public boolean isEmpty() {
        return this.slotOccupiedMask == 0;
    }

    public int getOccupiedSlots() {
        return Integer.bitCount(this.slotOccupiedMask);
    }

    /**
     * Find the first slot that a hopper can take items from.
     *
     * @return index of the first occupied slot a hopper can take from, -1 if none
     */
    public int getFirstOccupiedSlot_extractable() {
        if (this.slotOccupiedMask == 0) return -1;
        return Integer.numberOfTrailingZeros(this.slotOccupiedMask);
    }


    //assume that nothing besides players and hoppers/droppers etc. change inventory contents
    //control their inventory accesses, notify of the inventory of hidden stacksize changes (see HopperBlockEntityMixin and InventoriesMixin)

    /**
     * Update the data just before a slot is modified.
     *
     * @param slot Index of the modified slot
     */
    void onStackChange(int slot, @Nullable ItemStack newStack, int newCount) {
        assert !(this instanceof DoubleInventoryOptimizer);

        ItemStack prevStack = stackList.get(slot);
        if (prevStack == newStack) return;

        if (newStack == null) {
            newStack = prevStack;
        } else {
            newCount = newStack.getCount();
        }
        int prevCount = prevStack.getCount();

        Item newItem = newCount <= 0 ? Items.AIR : newStack.getItem();
        Item prevItem = prevStack.getItem();

        int prevMaxC = prevStack.getMaxCount();
        int newMaxC = newStack.getMaxCount();

        this.inventoryChanges++;

        if (prevItem != Items.AIR) {
            if (prevItem != newItem) {
                final int itemSlotMask = this.itemToSlotMask.getInt(prevItem);
                final int newItemSlotMask = itemSlotMask & ~(1 << slot);
                if (newItemSlotMask == 0)
                    this.itemToSlotMask.remove(prevItem, itemSlotMask);
                else
                    this.itemToSlotMask.put(prevItem, newItemSlotMask);

                if (prevMaxC != newMaxC || newItem == Items.AIR) {
                    int c = this.stackSizeToSlotCount.getOrDefault(prevMaxC, 0) - 1;
                    if (c != 0)
                        this.stackSizeToSlotCount.put(prevMaxC, c);
                    else
                        this.stackSizeToSlotCount.remove(prevMaxC);
                }
            }
            if (prevCount >= prevMaxC) {
                this.slotFullMask &= ~(1 << slot);
            }
        }

        if (newItem != Items.AIR) {
            if (prevItem != newItem) {
                final int itemSlotMask = this.itemToSlotMask.getInt(newItem);
                final int newItemSlotMask = itemSlotMask | (1 << slot);
                this.itemToSlotMask.put(newItem, newItemSlotMask);

                if (prevMaxC != newMaxC || prevItem == Items.AIR) {
                    this.stackSizeToSlotCount.put(newMaxC, this.stackSizeToSlotCount.getOrDefault(newMaxC, 0) + 1);
                }
            }
            if (newCount >= newMaxC) {
                this.slotFullMask |= 1 << slot;
            }
            this.slotOccupiedMask |= 1 << slot;
        } else {
            this.slotOccupiedMask &= ~(1 << slot);
        }
        this.weightedItemCount -= prevCount * (int) (64F / prevMaxC) - newCount * (int) (64F / newMaxC);

        if (Settings.debugOptimizedInventories) consistencyCheck();
    }

    public boolean hasFakeSignalStrength() {
        return fakeSignalStrength != -1;
    }

    public int getFakeSignalStrength() {
        return fakeSignalStrength;
    }

    //does not need override in DoubleInventoryOptimizer
    public int getSignalStrength() {

        if (hasFakeSignalStrength()) {
            return getFakeSignalStrength();
        }
        return (int) ((this.getWeightedItemCount() / ((float) this.getTotalSlots() * 64)) * 14) + (this.isEmpty() ? 0 : 1);
    }

    int getWeightedItemCount() {
        return weightedItemCount;
    }

    int getTotalSlots() {
        return totalSlots;
    }

    //Used to trick comparators into sending block updates like in vanilla.
    public void setFakeReducedSignalStrength() {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.fakeSignalStrength = this.getSignalStrength() - 1;
        if (fakeSignalStrength == -1) fakeSignalStrength = 0;
    }

    void setFakeReducedSignalStrength(int i) {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.fakeSignalStrength = i;
    }

    public void clearFakeChangedSignalStrength() {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.fakeSignalStrength = -1;
    }

    public int getMinExtractableItemStackSize(InventoryOptimizer pulledFrom) {
        //Override in DoubleInventoryOptimizer
        if (Settings.debugOptimizedInventories && pulledFrom != this)
            throw new IllegalArgumentException("InventoryOptimizer must be this.");

        int minExtractableItemStackSize = 2147483647;

        int stackSize;
        for (Map.Entry<Integer, Integer> entry : this.stackSizeToSlotCount.entrySet()) {
            if (entry.getValue() > 0 && (stackSize = entry.getKey()) < minExtractableItemStackSize)
                minExtractableItemStackSize = stackSize;
        }
        return minExtractableItemStackSize;
    }

    //No override in DoubleInventoryOptimizer required
    public boolean canOneExtractDecreaseSignalStrength(InventoryOptimizer pulledFrom) {
        if (isEmpty()) return false;

        int maxExtractableItemWeight = (int) (64F / getMinExtractableItemStackSize(pulledFrom));

        //calculate signal strength as if one most heavy item was taken out
        int weightedItemCount = getWeightedItemCount();
        boolean wouldBeEmpty = weightedItemCount <= maxExtractableItemWeight;
        int minOneLessItemSignalStrength = (int) (((weightedItemCount - maxExtractableItemWeight) / ((float) getTotalSlots() * 64)) * 14) + (wouldBeEmpty ? 0 : 1);

        return minOneLessItemSignalStrength != this.getSignalStrength();
    }

    private void recalculate() {
        assert !(this instanceof DoubleInventoryOptimizer);

        this.stackSizeToSlotCount.clear();
        this.itemToSlotMask.clear();
        this.slotOccupiedMask = 0;
        this.slotFullMask = 0;
        this.weightedItemCount = 0;

        for (int slot = 0; slot < totalSlots; slot++) {
            ItemStack stack = getSlot(slot);
            if (!stack.isEmpty()) {
                this.slotOccupiedMask |= 1 << slot;
                Item item = stack.getItem();
                int slotMask = this.itemToSlotMask.getOrDefault(item, 0);
                slotMask |= (1 << slot);
                this.itemToSlotMask.put(item, slotMask);

                int stackMaxCount = stack.getMaxCount();
                this.weightedItemCount += stack.getCount() * (64/*F float?*/ / stackMaxCount);
                this.stackSizeToSlotCount.put(stackMaxCount, stackSizeToSlotCount.getOrDefault(stackMaxCount, 0) + 1);

                if (stack.getCount() >= stackMaxCount) {
                    this.slotFullMask |= 1 << slot;
                }
            }
        }

    }

    public boolean isFull_insertable(Direction fromDirection) {
        return this.slotFullMask + 1 == 1 << this.totalSlots;
    }

    //Used for player interactions which sometimes just modify itemstacks without knowing where else they are used.
    //Pray that itemstacks will be immutable one day

    /**
     * Finds the slot that contains the given itemstack object (object comparison, no equals)
     * Must work while the state of the optimizer is inconsistent.
     *
     * @return index of the stack object, -1 if none found.
     */
    public int indexOfObject(ItemStack stack) {
        assert !(this instanceof DoubleInventoryOptimizer);

        for (int i = 0; i < this.totalSlots; i++) {
            if (stack == this.stackList.get(i))
                return i;
        }
        return -1;
    }

    /**
     * Finds the first slot that matches stack.
     * Not for use with SidedInventories
     * Does not support unstackable items!
     *
     * @param stack to find a matching item for
     * @return index of the matching item, -1 if none found.
     */
    public int indexOf_extractable_endIndex(ItemStack stack, int maxExclusive) {
        if (maxExclusive > this.totalSlots) maxExclusive = this.totalSlots;
        if (stack.isEmpty()) {
            assert false;
            return -1;
        }
        int slotMask = this.itemToSlotMask.getOrDefault(stack.getItem(), 0);
        if (slotMask == 0) return -1;
        int firstIndex = Integer.numberOfTrailingZeros(slotMask);
        int slotIndex = firstIndex;
        slotMask = slotMask >>> firstIndex;

        while (slotMask != 0 && slotIndex < maxExclusive) {
            assert ((slotMask & 1) == 1);
            if (areItemsAndTagsEqual(getSlot(slotIndex), stack) && (this.sidedInventory == null ||
                    this.sidedInventory.canExtract(slotIndex, getSlot(slotIndex), Direction.DOWN))) {
                return slotIndex;
            }

            int shift = Integer.numberOfTrailingZeros(slotMask & 0xFFFFFFFE);
            slotIndex += shift;
            slotMask = slotMask >>> shift;
        }
        return -1;
    }

    public boolean hasFreeSlots_insertable_ignoreSidedInventory() {
        return this.slotOccupiedMask != this.slotMask;
    }

    /**
     * @param stack         the item stack to be transferred (one item transfer possible at least)
     * @param fromDirection direction the item is coming from
     * @param thisInventory the inventory this is the optimizer of
     * @return first slot the stack can be transferred to, -1 if none found, -2 if inventory is filled with different item types only
     */
    public int findInsertSlot(ItemStack stack, Direction fromDirection, Inventory thisInventory) {

        int slotMask = this.itemToSlotMask.getOrDefault(stack.getItem(), 0);
        slotMask = slotMask & ~this.slotFullMask;
        slotMask = ~this.slotOccupiedMask & this.slotMask | slotMask;
        int firstIndex = Integer.numberOfTrailingZeros(slotMask);
        int slotIndex = firstIndex;
        slotMask = slotMask >>> firstIndex;

        while (slotMask != 0) {
            assert ((slotMask & 1) == 1);
            if ((0 == (this.slotOccupiedMask & (1 << slotIndex)) || areItemsAndTagsEqual(getSlot(slotIndex), stack)) &&
                    thisInventory.isValid(slotIndex, stack) &&
                    (this.sidedInventory == null || this.sidedInventory.canInsert(slotIndex, stack, fromDirection))) {
                return slotIndex;
            }

            int shift = Integer.numberOfTrailingZeros(slotMask & 0xFFFFFFFE);
            slotIndex += shift;
            if (shift >= 32)
                slotMask = 0;
            else {
                slotMask = slotMask >>> shift;
            }
        }
        return -1;
    }
}
