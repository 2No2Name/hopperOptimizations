package hopperOptimizations.utils.inventoryOptimizer;

import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Direction;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.List;

public class BitSetOptimizedStackList extends OptimizedStackList {

    private final BitSet slotFullMask;
    private final BitSet slotOccupiedMask;
    private final Reference2ReferenceOpenHashMap<Item, BitSet> itemToSlotMask;
    private Int2ReferenceOpenHashMap<BitSet> stackSizeToSlotMask;

    //only used for sided inventories (including shulker boxes):
    private int sidedOnly_firstExtractableSlot;
    private long sidedOnly_firstExtractableSlot_ContentChangeCount;


    public BitSetOptimizedStackList(List<ItemStack> delegate, @Nullable ItemStack initialElement, Inventory containedIn) {
        super(delegate, initialElement, containedIn);
        this.sidedOnly_firstExtractableSlot_ContentChangeCount = this.contentChangeCount - 1;

        this.slotFullMask = new BitSet(this.size());
        this.slotOccupiedMask = new BitSet(this.size());

//        if (this.size() > 5) {
        this.itemToSlotMask = new Reference2ReferenceOpenHashMap<>();
//        } else {
//            this.itemToSlotMask = null;
//        }
        this.stackSizeToSlotMask = null;

        int maxStackSize = this.parent.getMaxCountPerStack();
        for (int slot = 0; slot < this.size(); slot++) {
            ItemStack stack = this.get(slot);
            if (!stack.isEmpty()) {
                int stackMaxCount = Math.min(stack.getMaxCount(), maxStackSize);
                this.contentWeight += stack.getCount() * (64 / stackMaxCount);

                this.slotOccupiedMask.set(slot);
                if (stack.getCount() >= stackMaxCount) {
                    this.slotFullMask.set(slot);
                }

                Item item = stack.getItem();
                BitSet slotMask = this.itemToSlotMask.get(item);
                if (slotMask == null) {
                    slotMask = new BitSet(this.size());
                    this.itemToSlotMask.put(item, slotMask);
                }
                slotMask.set(slot);
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return this.slotOccupiedMask.isEmpty();
    }

    @Override
    public boolean isAnyExtractableSlotOccupied() {
        return this.getFirstOccupiedSlot_extractable() != -1;
    }

    @Override
    public boolean isSlotEmpty(int slot) {
        return !this.slotOccupiedMask.get(slot);
    }

    @Override
    public int getNumOccupiedSlots() {
        return this.slotOccupiedMask.cardinality();
    }

    @Override
    public int getFirstOccupiedSlot_extractable() {
        if (this.isEmpty()) {
            return -1;
        } else if (this.isSided) {
            if (this.sidedOnly_firstExtractableSlot_ContentChangeCount == this.getContentChangeCount()) {
                return this.sidedOnly_firstExtractableSlot;
            }
            SidedInventory thisSided = ((SidedInventory) this.parent);
            this.sidedOnly_firstExtractableSlot_ContentChangeCount = this.getContentChangeCount();
            for (int i : thisSided.getAvailableSlots(Direction.DOWN)) {
                if ((this.slotOccupiedMask.get(i)) && thisSided.canExtract(i, this.get(i), Direction.DOWN)) {
                    this.sidedOnly_firstExtractableSlot = i;
                    return this.sidedOnly_firstExtractableSlot;
                }
            }
            this.sidedOnly_firstExtractableSlot = -1;
            return this.sidedOnly_firstExtractableSlot;
        } else {
            return this.slotOccupiedMask.nextSetBit(0);
        }
    }

    @Override
    public boolean isFull_insertable(Direction fromDirection) {
        boolean retval;
        if ((retval = this.slotFullMask.nextClearBit(0) >= this.size()) || !this.isSided) {
            return retval;
        }
        SidedInventory thisSided = ((SidedInventory) this.parent);
        for (int i : thisSided.getAvailableSlots(fromDirection)) {
            if (!this.slotFullMask.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int indexOfInAvailableSlots_extractable_maxIndex(ItemStack stack, int maxExclusive) {
        if (maxExclusive > this.size()) maxExclusive = this.size();
        if (stack.isEmpty()) {
            assert false;
            return -1;
        }
        BitSet slotMask = this.itemToSlotMask.get(stack.getItem());
        if (slotMask == null || slotMask.isEmpty()) {
            return -1;
        }
        if (this.isSided) {
            int[] availableSlots = ((SidedInventory) this.parent).getAvailableSlots(Direction.DOWN);
            for (int i = 0; i < availableSlots.length; i++) {
                int slotIndex = availableSlots[i];
                if (slotMask.get(slotIndex) &&
                        areItemsAndTagsEqual(get(slotIndex), stack) &&
                        ((SidedInventory) this.parent).canExtract(slotIndex, this.get(slotIndex), Direction.DOWN)) {
                    //return index in available slots instead of the slot index itself. It will be converted
                    //later, but the index is required for determining which slot is "first"
                    return i;
                }
            }
        } else {
            int slotIndex = slotMask.nextSetBit(0);
            while (slotIndex != -1 && slotIndex < maxExclusive) {
                if (areItemsAndTagsEqual(get(slotIndex), stack)) {
                    return slotIndex;
                }
                slotIndex = slotMask.nextSetBit(slotIndex + 1);
            }
        }
        return -1;
    }

    @Override
    public int getInsertSlot(ItemStack stack, Direction fromDirection) {
        BitSet slotMask = this.itemToSlotMask.get(stack.getItem());

        if (this.isSided) {
            SidedInventory thisSided = ((SidedInventory) this.parent);
            for (int slotIndex : thisSided.getAvailableSlots(fromDirection)) {
                if (!this.slotOccupiedMask.get(slotIndex)) {
                    if (this.parent.isValid(slotIndex, stack) && thisSided.canInsert(slotIndex, stack, fromDirection)) {
                        return slotIndex;
                    }
                } else if (slotMask != null && slotMask.get(slotIndex) && !this.slotFullMask.get(slotIndex) &&
                        this.parent.isValid(slotIndex, stack) && thisSided.canInsert(slotIndex, stack, fromDirection) &&
                        areItemsAndTagsEqual(this.get(slotIndex), stack)) {
                    return slotIndex;
                }
            }
            return -1;
        } else {
            int emptySlot = this.slotOccupiedMask.nextClearBit(0);
            while (emptySlot < this.size() && !this.parent.isValid(emptySlot, stack)) {
                emptySlot = this.slotOccupiedMask.nextClearBit(emptySlot + 1);
            }
            //emptySlot is in the inclusive range(0..this.size())
            int slotIndex = slotMask != null ? slotMask.nextSetBit(0) : -1;
            while (slotIndex != -1 && slotIndex < emptySlot) {
                if (!this.slotFullMask.get(slotIndex) && this.parent.isValid(slotIndex, stack) && areItemsAndTagsEqual(get(slotIndex), stack)) {
                    return slotIndex;
                }
                slotIndex = slotMask.nextSetBit(slotIndex + 1);
            }
            if (emptySlot == this.size()) {
                return -1;
            }
            return emptySlot;
        }
    }

    @Override
    public int getIndexForMaximumSignalStrengthDecrease(int inventoryScanStart, int inventoryScanExclusiveEnd) {
        if (this.stackSizeToSlotMask == null) {
            this.stackSizeToSlotMask = new Int2ReferenceOpenHashMap<>();

            int maxStackSize = this.parent.getMaxCountPerStack();
            for (int slotIndex = 0; slotIndex < this.size(); slotIndex++) {
                ItemStack stack = this.get(slotIndex);
                if (stack.isEmpty()) {
                    continue;
                }
                int stackMaxCount = Math.min(stack.getMaxCount(), maxStackSize);
                BitSet slotMask = this.stackSizeToSlotMask.get(stackMaxCount);
                if (slotMask == null) {
                    this.stackSizeToSlotMask.put(stackMaxCount, (slotMask = new BitSet(this.size())));
                }
                slotMask.set(slotIndex);
            }
        }

        int maxStackSizeInsideRange = -1;
        int slotWithMaxStackSizeInsideRange = -1;
        for (int maxStackSize : this.stackSizeToSlotMask.keySet()) {
            if (maxStackSize > maxStackSizeInsideRange) {
                BitSet bitSet = this.stackSizeToSlotMask.get(maxStackSize);
                int slotWithStackSize = bitSet.nextSetBit(inventoryScanStart);
                while (this.isSided && slotWithStackSize >= 0 && slotWithStackSize < inventoryScanExclusiveEnd &&
                        !((SidedInventory) this.parent).canExtract(slotWithStackSize, this.get(slotWithStackSize), Direction.DOWN)) {
                    slotWithStackSize = bitSet.nextSetBit(inventoryScanStart + 1);
                }
                if (slotWithStackSize >= 0 && slotWithStackSize < inventoryScanExclusiveEnd) {
                    maxStackSizeInsideRange = maxStackSize;
                    slotWithMaxStackSizeInsideRange = slotWithStackSize;
                }
            }
        }
        return slotWithMaxStackSizeInsideRange;
    }

    @Override
    public void onStackChange(final int slot, ItemStack newStack, final int newCount) {
        super.onStackChange(slot, newStack, newCount);
        ItemStack prevStack = this.get(slot);
        if (prevStack == newStack) {
            return;
        } else if (newStack == null) {
            newStack = prevStack;
        }
        final int prevCount = prevStack.getCount();

        Item newItem = newCount <= 0 ? Items.AIR : newStack.getItem();
        Item prevItem = prevStack.getItem();

        int maxStackSize = this.parent.getMaxCountPerStack();
        final int prevMaxC = Math.min(prevStack.getMaxCount(), maxStackSize);
        final int newMaxC = Math.min(newStack.getMaxCount(), maxStackSize);

        if (prevItem != Items.AIR) {
            if (prevItem != newItem) {
                BitSet itemSlotMask = this.itemToSlotMask.get(prevItem);
                if (itemSlotMask != null) {
                    itemSlotMask.clear(slot);
                    if (itemSlotMask.isEmpty()) {
                        this.itemToSlotMask.remove(prevItem);
                    }
                } else {
                    throw new IllegalStateException("Removing item from inventory that wasn't in its optimizer!");
                }

                if (this.stackSizeToSlotMask != null && (prevMaxC != newMaxC || newItem == Items.AIR)) {
                    BitSet sizeSlotMask = this.stackSizeToSlotMask.get(prevMaxC);
                    sizeSlotMask.clear(slot);
                    if (sizeSlotMask.isEmpty()) {
                        this.stackSizeToSlotMask.remove(prevMaxC);
                    }
                }
            }
            if (prevCount >= prevMaxC) {
                this.slotFullMask.clear(slot);
            }
        }

        if (newItem != Items.AIR) {
            if (prevItem != newItem) {
                BitSet itemSlotMask = this.itemToSlotMask.get(newItem);
                if (itemSlotMask == null) {
                    this.itemToSlotMask.put(newItem, (itemSlotMask = new BitSet(this.size())));
                }
                itemSlotMask.set(slot);

                if (this.stackSizeToSlotMask != null && (prevMaxC != newMaxC || prevItem == Items.AIR)) {
                    BitSet sizeSlotMask = this.stackSizeToSlotMask.get(newMaxC);
                    if (sizeSlotMask == null) {
                        this.stackSizeToSlotMask.put(newMaxC, (sizeSlotMask = new BitSet(this.size())));
                    }
                    sizeSlotMask.set(slot);
                }
            }
            if (newCount >= newMaxC) {
                this.slotFullMask.set(slot);
            }
            this.slotOccupiedMask.set(slot);
        } else {
            this.slotOccupiedMask.clear(slot);
        }
    }
}
