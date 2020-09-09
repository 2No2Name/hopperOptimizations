package hopperOptimizations.utils.inventoryOptimizer;

import hopperOptimizations.utils.ListInventoryPair;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;

import javax.annotation.Nullable;
import java.util.List;

public abstract class OptimizedStackList extends DefaultedList<ItemStack> {
    public static boolean SMALL_POWER_OF_TWO_STACKSIZES_ONLY = true;

    final Inventory parent;
    final boolean isSided;
    long contentChangeCount;
    int contentWeight; //unstackable weigh 64, 16-stackable 4 and 64-stackable 1

    int cachedSignalStrength;


    public OptimizedStackList(List<ItemStack> delegate, @Nullable ItemStack initialElement, Inventory containedIn) {
        super(delegate, initialElement);
        assert !(containedIn instanceof DoubleInventory);
        this.parent = containedIn;
        this.isSided = this.parent instanceof SidedInventory;
        this.contentChangeCount = 0;
        this.contentWeight = 0;
        this.cachedSignalStrength = -1;


        int maxStackSize = this.parent.getMaxCountPerStack();
        for (int i = 0; i < this.size(); i++) {
            ItemStack stack = this.get(i);
            if (!stack.isEmpty()) {
                int stackMaxCount = Math.min(stack.getMaxCount(), maxStackSize);
                this.contentWeight += stack.getCount() * (64 / stackMaxCount);

                //noinspection ConstantConditions
                ((IItemStackCaller) (Object) stack).setInInventory(this, i);
            }
        }
    }

    public static OptimizedStackList convertFrom(DefaultedList<ItemStack> convertFrom, Inventory parent) {
        //reuse the arraylist from the defaulted list to avoid another copy
        return new BitSetOptimizedStackList(convertFrom.delegate, convertFrom.initialElement, parent);
    }

    public static OptimizedStackList convertFrom(DoubleInventory parent) {
        ListInventoryPair convertFrom = new ListInventoryPair(parent.first, parent.second);
        //reuse the arraylist from the defaulted list to avoid another copy
        return new BitSetOptimizedStackList(convertFrom, ItemStack.EMPTY, parent);
    }

    public static boolean areItemsAndTagsEqual(ItemStack a, ItemStack b) {
        if (!ItemStack.areItemsEqual(a, b)) return false;
        return ItemStack.areTagsEqual(a, b);
    }

    public DefaultedList<ItemStack> getDowngraded() {
        for (int i = 0; i < this.size(); i++) {
            ItemStack stack = this.get(i);
            //noinspection ConstantConditions
            ((IItemStackCaller) (Object) stack).removeFromInventory(this, i);
        }
        //now this OptimizedStackList is invalid and needs to be garbage collected. It is essential that it is no longer touched
        return new DefaultedList<>(this.delegate, this.initialElement);
    }

    public long getContentChangeCount() {
        return this.contentChangeCount;
    }

    public int getContentWeight() {
        return this.contentWeight;
    }

    @Override
    public ItemStack set(int slotIndex, ItemStack newStack) {
        this.onStackChange(slotIndex, newStack, newStack.getCount());

        ItemStack prevStack = super.set(slotIndex, newStack);
        if (prevStack != newStack) {
            //noinspection ConstantConditions
            ((IItemStackCaller) (Object) prevStack).removeFromInventory(this, slotIndex);
            //noinspection ConstantConditions
            ((IItemStackCaller) (Object) newStack).setInInventory(this, slotIndex);
        }
        return prevStack;
    }

    public void onStackChange(final int slot, ItemStack newStack, final int newCount) {
        ItemStack prevStack = this.get(slot);
        if (prevStack == newStack) {
            return;
        } else if (newStack == null) {
            newStack = prevStack;
        }
        final int prevCount = prevStack.getCount();

        int maxStackSize = this.parent.getMaxCountPerStack();
        final int prevMaxC = Math.min(prevStack.getMaxCount(), maxStackSize);
        final int newMaxC = Math.min(newStack.getMaxCount(), maxStackSize);

        this.contentWeight -= prevCount * (int) (64F / prevMaxC) - newCount * (int) (64F / newMaxC);
        this.contentChangeCount++;
    }

    public int getSignalStrength() {
        if (this.cachedSignalStrength == -1) {
            this.cachedSignalStrength = (int) ((this.getContentWeight() / ((float) this.size() * 64)) * 14) + (this.isEmpty() ? 0 : 1);
        }
        return Math.max(0, this.cachedSignalStrength);
    }

    public int getSignalStrengthSimulateDecrementAt(int decrementIndex) {
        return (int) (((this.getContentWeight() - Math.min(this.parent.getMaxCountPerStack(), this.get(decrementIndex).getMaxCount())) / ((float) this.size() * 64)) * 14) + (this.isEmpty() ? 0 : 1);
    }

    @Override
    public abstract boolean isEmpty();

    public abstract boolean isAnyExtractableSlotOccupied();

    public abstract boolean isSlotEmpty(int slot);

    public abstract int getNumOccupiedSlots();

    public abstract int getFirstOccupiedSlot_extractable(); //furnaces have a reversed extraction order!

    public abstract boolean isFull_insertable(Direction fromDirection);

    public abstract int indexOfInAvailableSlots_extractable_maxIndex(ItemStack stack, int maxExclusive);

    public abstract int getInsertSlot(ItemStack stack, Direction fromDirection);

    public abstract int getIndexForMaximumSignalStrengthDecrease(int inventoryScanStart, int inventoryScanExclusiveEnd);

    public int getAvailableSlotsEntry(int index, Direction fromDirection) {
        if (this.isSided) {
            return ((SidedInventory) this.parent).getAvailableSlots(fromDirection)[index];
        } else {
            return index;
        }
    }

    public void decreaseSignalStrength() {
        this.cachedSignalStrength = this.getSignalStrength() - 1;
    }

    public void increaseSignalStrength() {
        this.cachedSignalStrength++;
    }

    public boolean hasFreeSlotsInsertable_NonSidedInventory() {
        if (this.isSided) {
            throw new IllegalStateException("Expected Inventory to not be sided!");
        }
        return this.getNumOccupiedSlots() != this.size();
    }

    public boolean cannotExtractFrom(int slot) {
        return this.isSlotEmpty(slot) || (this.isSided && ((SidedInventory) this.parent).canExtract(slot, this.get(slot), Direction.DOWN));
    }

    @Override
    public void add(int int_1, ItemStack itemStack) {
        throw new UnsupportedOperationException("Won't resize optimized inventory!");
    }

    @Override
    public ItemStack remove(int int_1) {
        throw new UnsupportedOperationException("Won't resize optimized inventory!");
    }

    @Override
    public void clear() {
        if (this.parent instanceof DoubleInventory) {
            this.delegate.clear();
        } else {
            super.clear();
        }
    }

    public interface IItemStackCaller {
        void setInInventory(OptimizedStackList myInventoryList, int slotIndex);

        void removeFromInventory(OptimizedStackList myInventoryList, int slotIndex);
    }
}
