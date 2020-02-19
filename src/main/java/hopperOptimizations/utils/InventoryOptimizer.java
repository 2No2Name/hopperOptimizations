package hopperOptimizations.utils;

import carpet.CarpetServer;
import hopperOptimizations.settings.Settings;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

//Don't store instances of InventoryOptimizer, unless you sync with the corresponding inventory!
//Instances may suddenly become invalid due to unloading or turning the setting off
public class InventoryOptimizer {

    //256 Bit bloom filter //false positive rates estimated with https://hur.st/bloomfilter/?n=27&p=&m=256&k=
    //Decided on going for a high estimate, as a larger bloom filter probably won't be a lag causer, but can still help
    //Estimate useful for 27 slot inventory
    private static final int hashBits = 6;
    private static final int maskLength = 8; // 8 -> 256 bits in filter -> 4 longs or 6 -> 64 bits -> 1 long
    private static final long mask = (1L << maskLength) - 1;
    private static final int filterLongCount = Math.max(1, (1 << maskLength) / 64); //256 bit total, 8 bits can address it

    //usage stats
    private static int filterPositives;
    private static int filterNegatives;
    private static int filterTruePositives;
    private static int filterEdits;

    //todo(unneccesary) recalculate Filters when 180+ Bits are set -> ~12% chance for false positive
    //private static boolean DEBUG = false; //nonfinal to be able to change with debugger

    private final InventoryListOptimized stackList;

    private final SidedInventory sidedInventory; //only use when required, inventory handling should be mostly independent from the container
    //do any item restrictions exist e.g. cannot insert shulker boxes into this inventory
    private final boolean itemRestrictions;
    //number of inventory slots are occupied with stacks of a given size: e.g. 64->6, 16->1, 1->3
    private final Map<Integer, Integer> stackSizeToSlotCount = new HashMap<>();

    private final long[] bloomFilter = new long[filterLongCount];
    private final long[] nonFullStackBloomFilter = new long[filterLongCount];
    private final long[] preEmptyNonFullStackBloomFilter = new long[filterLongCount];
    //number of slots
    private int totalSlots;
    //number of changes to the inventory contents - useful for realizing inventory hasn't changed
    private int inventoryChanges;
    //number of changes of the item types in the inventory - useful for realizing item entities still don't fit
    private int itemTypeChanges;
    //number of slots that are occupied = not empty
    private int occupiedSlots;
    //number of slots that are completely full
    private int fullSlots;
    //index of the first slot that is empty
    private int firstFreeSlot;
    //index of the first slot that is not empty
    private int firstOccupiedSlot;
    //item count for comparators, items with <64 max stack size already weighted higher
    private int weightedItemCount;
    //lower signal strength override for a short moment to make comparators send updates at their outputs
    private int fakeSignalStrength;

    //whether all the variables above are initialized
    private boolean initialized;
    //whether this optimizer shall no longer be used
    private boolean invalid;
    //counter to detect that the optimizedInventory rule changed - for invalidating cached data
    private int optimizedInventoryRuleChangeCounter;

    public InventoryOptimizer(InventoryListOptimized stackList, Inventory inventory) {
        this.stackList = stackList;
        this.sidedInventory = inventory instanceof SidedInventory ? (SidedInventory) inventory : null;
        this.itemRestrictions = this.sidedInventory != null;

        if (Settings.debugOptimizedInventories && this.itemRestrictions && !(this.sidedInventory instanceof ShulkerBoxBlockEntity))
            //Shulkerbox restrictions are slot independent.
            //Slot dependent restrictions aren't checked atm, since there is no large inventory that has those.
            //OptimizedInventory doesn't seem viable for small inventories. - maybe add a version without bloom filters for those
            throw new NotImplementedException("Implement OptimizedInventory with more complex item insert conditions before using those.");

        this.initialized = false;
        this.optimizedInventoryRuleChangeCounter = OptimizedInventoriesRule.ruleUpdates;

        this.invalid = false;
        fakeSignalStrength = -1;
        if (stackList == null) return;
        inventoryChanges = 0;
        itemTypeChanges = 0;
    }

    private static long hash(ItemStack stack) {
        //itemstacks caching their hash might be useful

        //Empty and Unstackables don't go into the bloom filter
        if (stack.isEmpty() || stack.getMaxCount() <= 1) return 0;
        long hash = HashCommon.mix((long) stack.getItem().hashCode());
        hash ^= HashCommon.mix((long) stack.getDamage());
        hash ^= HashCommon.mix((long) Objects.hashCode(stack.getTag()));
        return hash == 0 ? 1 : hash;
    }

    public static boolean areItemsAndTagsEqual(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsEqual(a, b)) return false;
        return ItemStack.areTagsEqual(a, b);
    }

    public void setInvalid() {
        this.invalid = true;
    }

    public boolean isInvalid() {
        return this.invalid;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    private void consistencyCheck() {
        assert !(this instanceof DoubleInventoryOptimizer);
        //this is code from recalculate, but instead of changing anything, we just check if the results are conflicting
        if (!initialized || this.optimizedInventoryRuleChangeCounter != OptimizedInventoriesRule.ruleUpdates) return;
        try {
        int occupiedSlots = 0;
        int fullSlots = 0;
        int firstFreeSlot = -1;
        int firstOccupiedSlot = -1;
        int totalSlots = size();
            int weightedItemCount = 0;
            Map<Integer, Integer> stackSizeToSlotCount = new HashMap<>();


        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = getSlot(i);
            long hash = hash(stack);
            if (hash != 0 && !filterContains(bloomFilter, hash))
                throw new IllegalStateException("Itemstack not in bloom filter: " + stack.toString());

            if (!stack.isEmpty()) {
                weightedItemCount += stack.getCount() * (int) (64F / stack.getMaxCount());
                stackSizeToSlotCount.put(stack.getMaxCount(), stackSizeToSlotCount.getOrDefault(stack.getMaxCount(), 0) + 1);

                if (firstOccupiedSlot < 0)
                    firstOccupiedSlot = i;
                occupiedSlots++;
                if (stack.getCount() >= stack.getMaxCount()) fullSlots++;
                else if (hash != 0 && !filterContains(nonFullStackBloomFilter, hash))
                    throw new IllegalStateException("Itemstack not in nonFull bloom filter: " + stack.toString());

            } else if (firstFreeSlot < 0) {
                firstFreeSlot = i;
                for (int j = 0; j < firstFreeSlot; j++) {
                    ItemStack stack1 = getSlot(j);
                    if (stack1.getCount() < stack1.getMaxCount()) {
                        long hash1 = hash(stack1);
                        if (hash1 != 0 && !filterContains(preEmptyNonFullStackBloomFilter, hash1))
                            throw new IllegalStateException("Itemstack not in preEmptyNonFull bloom filter: " + stack1.toString());
                    }
                }
            }
        }
        if (this.occupiedSlots != occupiedSlots)
            throw new IllegalStateException("occupied slots wrong");
        if (this.fullSlots != fullSlots)
            throw new IllegalStateException("full slots wrong");
        if (this.firstFreeSlot != firstFreeSlot)
            throw new IllegalStateException("first free slot wrong");
        if (this.firstOccupiedSlot != firstOccupiedSlot)
            throw new IllegalStateException("first occupied slot wrong");

            int signal1 = MathHelper.floor(this.weightedItemCount / ((float) this.totalSlots * 64) * 14 + (occupiedSlots == 0 ? 0 : 1));
            int signal2 = calculateComparatorOutput();
            if (this.weightedItemCount != weightedItemCount || signal1 != signal2)
                throw new IllegalStateException("comparator output wrong");
            if (!this.stackSizeToSlotCount.equals(stackSizeToSlotCount))
                throw new IllegalStateException("stacksize slot counts wrong");

        } catch (IllegalStateException e) {
            initialized = false;
            Text text = new LiteralText("Detected broken optimizer ( " + e.getMessage() + ") at " + Arrays.toString(e.getStackTrace()));
            CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
        }
    }

    private int calculateComparatorOutput() {
        assert !(this instanceof DoubleInventoryOptimizer);
        int int_1 = 0;
        float float_1 = 0.0F;

        for (ItemStack itemStack_1 : stackList) {
            if (!itemStack_1.isEmpty()) {
                float_1 += (float) itemStack_1.getCount() / (float) Math.min(64, itemStack_1.getMaxCount());
                ++int_1;
            }
        }

        float_1 /= (float) stackList.size();
        return MathHelper.floor(float_1 * 14.0F) + (int_1 > 0 ? 1 : 0);
    }

    public void onItemStackCountChanged(int index, int countChange) {
        if (!initialized || this.optimizedInventoryRuleChangeCounter != OptimizedInventoriesRule.ruleUpdates) return;

        //should never increase count of empty stacks

        ItemStack itemStack = getSlot(index);
        int count = itemStack.getCount();

        itemStack.setCount(1);
        ItemStack prevStack = itemStack.copy(); //copy fails to create a real copy when count is 0
        itemStack.setCount(count);
        prevStack.setCount(count - countChange);

        boolean wasEmpty = prevStack.isEmpty();
        //assert !wasEmpty;
        boolean isEmpty = itemStack.isEmpty();

        if (!wasEmpty || !isEmpty) update(index, prevStack);
        else { //wasEmpty && isEmpty //todo why even do this?
            int max = itemStack.getMaxCount();
            weightedItemCount += countChange * (int) (64F / max);
            inventoryChanges++;
            if (Settings.debugOptimizedInventories) consistencyCheck();
        }
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

    public int getOccupiedSlots() {
        this.ensureInitialized();
        return occupiedSlots;
    }

    boolean isInvEmpty_Extractable() {
        this.ensureInitialized();
        return occupiedSlots == 0;
    }

    public int getItemTypeChanges() {
        return itemTypeChanges;
    }

    /**
     * Find the first slot that a hopper can take items from.
     *
     * @return index of the first occupied slot a hopper can take from, -1 if none
     */
    public int getFirstOccupiedSlot_extractable() {
        this.ensureInitialized();
        return firstOccupiedSlot;
    }

    //assume that nothing besides players and hoppers/droppers etc. change inventory contents
    //control their inventory accesses, notify of the inventory of hidden stacksize changes (see HopperBlockEntityMixin and InventoriesMixin)


    /**
     * Update the bloom filter after a slot has been modified.
     *
     * @param slot Index of the modified slot
     */
    void update(int slot, ItemStack prevStack) {
        assert !(this instanceof DoubleInventoryOptimizer);
        if (!initialized || this.optimizedInventoryRuleChangeCounter != OptimizedInventoriesRule.ruleUpdates) return;

        ItemStack newStack = stackList.get(slot);
        if (prevStack == newStack) return;

        if (prevStack.getItem() != newStack.getItem())
            itemTypeChanges++;

        inventoryChanges++;
        int oldFirstFreeSlot = firstFreeSlot;

        long hash = hash(newStack);
        filterAdd(bloomFilter, hash);
        boolean flagRecalcFree = false;
        boolean flagRecalcOccupied = false;

        int prevC, prevMaxC, newC, newMaxC;

        //Update fullSlots
        if ((prevC = prevStack.getCount()) >= (prevMaxC = prevStack.getMaxCount())) {
            --fullSlots;
        }
        if ((newC = newStack.getCount()) >= (newMaxC = newStack.getMaxCount())) {
            ++fullSlots;
        } else {
            //In case of empty stack, filters are unchanged, otherwise add to according filters
            filterAdd(nonFullStackBloomFilter, hash);
            if (slot < firstFreeSlot)
                filterAdd(preEmptyNonFullStackBloomFilter, hash);
        }
        this.weightedItemCount -= prevC * (int) (64F / prevMaxC) - newC * (int) (64F / newMaxC);


        if (!prevStack.isEmpty()) {
            --occupiedSlots;
            int c = stackSizeToSlotCount.getOrDefault(prevMaxC, 0);
            if (c != 1)
                stackSizeToSlotCount.replace(prevMaxC, c - 1);
            else
                stackSizeToSlotCount.remove(prevMaxC);
        }
        if (!newStack.isEmpty()) {
            ++occupiedSlots;
            stackSizeToSlotCount.put(newMaxC, stackSizeToSlotCount.getOrDefault(newMaxC, 0) + 1);

            firstOccupiedSlot = firstOccupiedSlot > slot || firstOccupiedSlot == -1 ? slot : firstOccupiedSlot;
            if (firstFreeSlot == slot)
                flagRecalcFree = true;
        } else {
            firstFreeSlot = firstFreeSlot > slot || firstFreeSlot == -1 ? slot : firstFreeSlot;
            if (slot == firstOccupiedSlot)
                flagRecalcOccupied = true;
        }
        if (oldFirstFreeSlot > firstFreeSlot || flagRecalcFree || flagRecalcOccupied)
            recalcFirstFreeAndOccupiedSlots(oldFirstFreeSlot, flagRecalcFree, flagRecalcOccupied);


        if (Settings.debugOptimizedInventories) consistencyCheck();
    }

    private void recalcFirstFreeAndOccupiedSlots(int oldFirstFreeSlot, boolean flagRecalcFree, boolean flagRecalcOccupied) {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.totalSlots = size();
        if (flagRecalcOccupied)
            firstOccupiedSlot = -1;
        if (flagRecalcFree)
            firstFreeSlot = -1;

        for (int i = 0; i < totalSlots && ((firstFreeSlot == -1 && flagRecalcFree) || (firstOccupiedSlot == -1 && flagRecalcOccupied)); i++) {
            ItemStack stack = getSlot(i);
            if (!stack.isEmpty()) {
                if (firstOccupiedSlot < 0)
                    firstOccupiedSlot = i;
            } else if (firstFreeSlot < 0)
                firstFreeSlot = i;
        }

        if (oldFirstFreeSlot < firstFreeSlot) {// || firstFreeSlot == -1){
            if (oldFirstFreeSlot == -1) oldFirstFreeSlot = 0;
            for (int i = oldFirstFreeSlot; i < totalSlots && (i < firstFreeSlot /*|| firstFreeSlot == -1*/); i++) {
                ItemStack itemStack = getSlot(i);
                if (!itemStack.isEmpty() && !(itemStack.getMaxCount() <= itemStack.getCount()))
                    filterAdd(preEmptyNonFullStackBloomFilter, hash(itemStack));
            }
        }
    }

    public boolean hasFakeSignalStrength() {
        return fakeSignalStrength != -1;
    }

    public int getFakeSignalStrength() {
        return fakeSignalStrength;
    }

    //does not need override in DoubleInventoryOptimizer
    public int getSignalStrength() {
        this.ensureInitialized();

        if (hasFakeSignalStrength()) {
            return getFakeSignalStrength();
        }
        return (int) ((this.getWeightedItemCount() / ((float) this.getTotalSlots() * 64)) * 14) + (this.getOccupiedSlots() == 0 ? 0 : 1);
    }

    void ensureInitialized() {
        if (!initialized || this.optimizedInventoryRuleChangeCounter != OptimizedInventoriesRule.ruleUpdates)
            recalculate();
    }

    int getWeightedItemCount() {
        return weightedItemCount;
    }

    int getTotalSlots() {
        return totalSlots;
    }

    //Used to trick comparators into sending block updates like in vanilla.
    void setFakeReducedSignalStrength() {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.ensureInitialized();
        this.fakeSignalStrength = this.getSignalStrength() - 1;
        if (fakeSignalStrength == -1) fakeSignalStrength = 0;
    }

    void setFakeReducedSignalStrength(int i) {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.ensureInitialized();
        this.fakeSignalStrength = i;
    }

    void clearFakeChangedSignalStrength() {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.fakeSignalStrength = -1;
    }

    public int getMinExtractableItemStackSize(InventoryOptimizer pulledFrom) {
        //Override in DoubleInventoryOptimizer
        if (Settings.debugOptimizedInventories && pulledFrom != this)
            throw new IllegalArgumentException("InventoryOptimizer must be this.");

        int minExtractableItemStackSize = 2147483647;

        int stackSize;
        for (Map.Entry<Integer, Integer> entry : stackSizeToSlotCount.entrySet()) {
            if (entry.getValue() > 0 && (stackSize = entry.getKey()) < minExtractableItemStackSize)
                minExtractableItemStackSize = stackSize;
        }
        return minExtractableItemStackSize;
    }

    //No override in DoubleInventoryOptimizer required
    boolean canOneExtractDecreaseSignalStrength(InventoryOptimizer pulledFrom) {
        if (getOccupiedSlots() == 0) return false;

        int maxExtractableItemWeight = (int) (64F / getMinExtractableItemStackSize(pulledFrom));

        //calculate signal strength as if one most heavy item was taken out
        int weightedItemCount = getWeightedItemCount();
        boolean wouldBeEmpty = weightedItemCount <= maxExtractableItemWeight;
        int minOneLessItemSignalStrength = (int) (((weightedItemCount - maxExtractableItemWeight) / ((float) getTotalSlots() * 64)) * 14) + (wouldBeEmpty ? 0 : 1);

        return minOneLessItemSignalStrength != this.getSignalStrength();
    }

    private void clearFilters() {
        assert !(this instanceof DoubleInventoryOptimizer);
        for (int i = 0; i < filterLongCount; i++) {
            bloomFilter[i] = 0;
            nonFullStackBloomFilter[i] = 0;
            preEmptyNonFullStackBloomFilter[i] = 0;
        }
    }

    private void recalculate() {
        assert !(this instanceof DoubleInventoryOptimizer);
        clearFilters();

        int occupiedSlots = 0;
        int fullSlots = 0;
        int firstFreeSlot = -1;
        int firstOccupiedSlot = -1;
        this.weightedItemCount = 0;
        this.totalSlots = size();
        stackSizeToSlotCount.clear();

        for (int i = 0; i < totalSlots; i++) {
            ItemStack stack = getSlot(i);
            long hash = hash(stack);
            filterAdd(bloomFilter, hash);
            if (!stack.isEmpty()) {
                this.weightedItemCount += stack.getCount() * (int) (64F / stack.getMaxCount());
                stackSizeToSlotCount.put(stack.getMaxCount(), stackSizeToSlotCount.getOrDefault(stack.getMaxCount(), 0) + 1);

                if (firstOccupiedSlot < 0)
                    firstOccupiedSlot = i;
                occupiedSlots++;
                if (stack.getCount() >= stack.getMaxCount()) fullSlots++;
                else filterAdd(nonFullStackBloomFilter, hash);
            } else if (firstFreeSlot < 0) {
                firstFreeSlot = i;
                for (int j = 0; j < firstFreeSlot; j++) {
                    ItemStack stack1 = getSlot(j);
                    if (stack1.getCount() < stack1.getMaxCount()) {
                        long hash1 = hash(stack1);
                        filterAdd(preEmptyNonFullStackBloomFilter, hash1);
                    }
                }
            }
        }
        this.occupiedSlots = occupiedSlots;
        this.fullSlots = fullSlots;
        this.firstFreeSlot = firstFreeSlot;
        this.firstOccupiedSlot = firstOccupiedSlot;

        this.initialized = true;
        this.optimizedInventoryRuleChangeCounter = OptimizedInventoriesRule.ruleUpdates;
    }

    public int getFirstFreeSlot() {
        this.ensureInitialized();
        return firstFreeSlot;
    }

    public boolean isFull_insertable(Direction fromDirection) {
        this.ensureInitialized();
        return fullSlots >= totalSlots;
    }

    //Does not support unstackable items!
    private boolean maybeContains(ItemStack stack) {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.ensureInitialized();

        if (stack.isEmpty()) return getFirstFreeSlot() >= 0;
        if (occupiedSlots == 0) return false;
        long hash = hash(stack);

        return filterContains(bloomFilter, hash);
    }

    private boolean maybeContainsNonFullStack_insertable(ItemStack stack, Direction fromDirection) {
        this.ensureInitialized();

        if (stack.isEmpty()) return getFirstFreeSlot() >= 0;
        if (occupiedSlots == 0) return false;
        if (isFull_insertable(fromDirection)) return false;
        return filterContains(nonFullStackBloomFilter, hash(stack));
    }

    /*
    private boolean canMaybeInsert(ItemStack stack, Direction fromDirection) {
        this.ensureInitialized();

        if (hasFreeSlots_insertable()) return true;
        if (isFull_insertable(fromDirection)) return false;
        return maybeContainsNonFullStack_insertable(stack, fromDirection);
    }
    */

    private boolean filterContains(long[] bloomFilter, long hash) {
        if (hash == 0) return false;

        boolean ret = true; //becomes false if any of the corresponding bits is not set
        //Use the lowest maskLength bits of the hash, hashBits times checking a bit in the filter
        for (int i = 0; i < hashBits && ret; ++i) {
            long hIndex = (hash & (mask << (i * maskLength))) >> (i * maskLength);
            //use the bits of the hash as number as index in the bloom filter
            ret = (bloomFilter[((int) hIndex) / 64] & (1L << (hIndex % 64))) != 0;
        }
        if (ret) filterPositives++;
        else filterNegatives++;

        return ret;
    }

    private void filterAdd(long[] bloomFilter, long hash) {
        if (hash == 0) return;
        filterEdits++;
        //Use the lowest maskLength bits of the hash hashBits times to set a bit in the filter
        for (int i = 0; i < hashBits; ++i) {
            long hIndex = (hash & (mask << (i * maskLength))) >> (i * maskLength);
            bloomFilter[((int) hIndex) / 64] |= (1L << (hIndex % 64));
        }
    }

    //Used for player interactions which sometimes just modify itemstacks without knowing where else they are used.
    //Pray that itemstacks will be immutable one day

    /**
     * Finds the slot that contains the given itemstack object (object comparison, no equals)
     *
     * @return index of the stack object, -1 if none found.
     */
    public int indexOfObject(ItemStack stack) {
        assert !(this instanceof DoubleInventoryOptimizer);
        this.ensureInitialized();

        for (int i = 0; i < this.totalSlots; i++) {
            if (stack == this.stackList.get(i))
                return i;
        }
        return -1;
    }

    /**
     * Finds the first slot that matches stack.
     * Not for use with SidedInventories
     *
     * @param stack to find a matching item for
     * @return index of the matching item, -1 if none found.
     */
    //does not require override in DoubleInventoryOptimizer
    //Does not support unstackable items!
    public int indexOf(ItemStack stack) {
        this.ensureInitialized();
        return indexOf_extractable_endIndex(stack, getTotalSlots());
    }

    //Does not support unstackable items!
    public int indexOf_extractable_endIndex(ItemStack stack, int stop) {
        this.ensureInitialized();
        if (stop == -1) stop = this.totalSlots;
        if (stack.isEmpty()) return -1;
        if (!maybeContains(stack)) return -1;
        for (int i = firstOccupiedSlot; i < stop; i++) {
            ItemStack slot = getSlot(i);
            if (areItemsAndTagsEqual(stack, slot)) {
                filterTruePositives++;
                return i;
            }
        }
        return -1;
    }

    public boolean hasFreeSlots_insertable() {
        this.ensureInitialized();
        return getFirstFreeSlot() >= 0;
    }

    public int findInsertSlot(ItemStack stack, Direction fromDirection) {
        this.ensureInitialized();

        int firstFreeSlot = getFirstFreeSlot();
        if ((firstFreeSlot == 0 || stack.getMaxCount() == 1))
            //return the first free slot when the item is not stackable. (edge case: shulker box into shulker box)
            return this.itemRestrictions && !this.sidedInventory.canInsertInvStack(firstFreeSlot, stack, fromDirection) ? -1 : firstFreeSlot;
        else if (firstFreeSlot > 0) {
            //Check for matching non full stacks before the empty slot.
            long hash = hash(stack);
            if (filterContains(preEmptyNonFullStackBloomFilter, hash)) {
                for (int i = 0; i < firstFreeSlot; i++) {
                    ItemStack slot = getSlot(i);
                    if (slot.getCount() >= slot.getMaxCount()) continue;
                    if (areItemsAndTagsEqual(stack, slot)) {
                        filterTruePositives++;
                        return i;
                    }
                }
            }
            return firstFreeSlot;
        }

        //No empty Slot, search everything if there may be a fitting non full stack
        if (!maybeContainsNonFullStack_insertable(stack, fromDirection)) return -1;

        int start = 0;
        for (int i = start; i < totalSlots; i++) {
            ItemStack slot = getSlot(i);
            if (slot.getCount() >= slot.getMaxCount()) continue;
            if (areItemsAndTagsEqual(stack, slot)) {
                filterTruePositives++;
                return i;
            }
        }
        return -1;
    }
}
