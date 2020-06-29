package hopperOptimizations.utils;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

//Don't store instances of InventoryOptimizer, unless you sync with the corresponding inventory!
//DoubleInventoryOptimizer actually handles that in DoubleInventoryMixin

//DO NOT STORE STATE IN DoubleInventoryOptimizer fields (also those inherited from InventoryOptimizer)
//DoubleInventoryOptimizer objects may be discarded and recreated at any time!

public class DoubleInventoryOptimizer extends InventoryOptimizer {
    private final OptimizedInventory first;
    private final InventoryOptimizer firstOpt;
    private final OptimizedInventory second;
    private final InventoryOptimizer secondOpt;

    public DoubleInventoryOptimizer(OptimizedInventory first, OptimizedInventory second) {
        super();
        this.first = first;
        this.second = second;
        this.firstOpt = first.getOptimizer(true);
        this.secondOpt = second.getOptimizer(true);
    }

    @Override
    public boolean isRemoved() {
        return firstOpt == null || firstOpt.isRemoved() || secondOpt == null || secondOpt.isRemoved();
    }

    Inventory getFirstInventory() {
        return first;
    }

    Inventory getSecondInventory() {
        return second;
    }

    InventoryOptimizer getFirstOptimizer() {
        return firstOpt;
    }

    InventoryOptimizer getSecondOptimizer() {
        return secondOpt;
    }

    @Override
    public int indexOf_extractable_endIndex(ItemStack stack, int maxExclusive) {
        int ret = firstOpt.indexOf_extractable_endIndex(stack, maxExclusive);
        if (ret == -1 && first.size() < maxExclusive) {
            ret = secondOpt.indexOf_extractable_endIndex(stack, maxExclusive - first.size());
            if (ret != -1)
                ret += first.size();
        }
        return ret;
    }

    @Override
    public boolean hasFreeSlots_insertable_ignoreSidedInventory() {
        return firstOpt.hasFreeSlots_insertable_ignoreSidedInventory() || secondOpt.hasFreeSlots_insertable_ignoreSidedInventory();
    }

    @Override
    public int findInsertSlot(ItemStack stack, Direction fromDirection, Inventory thisInventory) {
        int ret = firstOpt.findInsertSlot(stack, fromDirection, first);
        if (ret < 0) {
            ret = Math.max(ret, secondOpt.findInsertSlot(stack, fromDirection, second));
            if (ret >= 0)
                ret += first.size();
        }
        return ret;
    }

    @Override
    public boolean isFull_insertable(Direction fromDirection) {

        return firstOpt.isFull_insertable(fromDirection) && secondOpt.isFull_insertable(fromDirection);
    }

    @Override
    protected ItemStack getSlot(int index) {
        if (index < 0) return ItemStack.EMPTY;
        int firstSize = first.size();
        if (index < firstSize) return first.getStack(index);
        return second.getStack(index - firstSize);
    }

    @Override
    protected int size() {
        return first.size() + second.size();
    }

    @Override
    public int getOccupiedSlots() {
        return firstOpt.getOccupiedSlots() + secondOpt.getOccupiedSlots();
    }

    @Override
    public int getFirstOccupiedSlot_extractable() {
        int ret = firstOpt.getFirstOccupiedSlot_extractable();
        if (ret == -1) {
            ret = secondOpt.getFirstOccupiedSlot_extractable();
            if (ret != -1)
                ret += first.size();
        }
        return ret;
    }

    @Override
    public int getInventoryChangeCount() {
        return firstOpt.getInventoryChangeCount() + secondOpt.getInventoryChangeCount();
    }

    @Override
    int getWeightedItemCount() {
        return firstOpt.getWeightedItemCount() + secondOpt.getWeightedItemCount();
    }

    @Override
    int getTotalSlots() {
        return firstOpt.getTotalSlots() + secondOpt.getTotalSlots();
    }

    /**
     * Finds the slot that contains the given itemstack object (object comparison, no equals)
     *
     * @return index of the stack object, -1 if none found.
     */
    @Override
    public int indexOfObject(ItemStack stack) {
        int ret = firstOpt.indexOfObject(stack);
        if (ret == -1) {
            ret = secondOpt.indexOfObject(stack);
            if (ret != -1)
                ret += first.size();
        }
        return ret;
    }

    @Override
    public int getMinExtractableItemStackSize(InventoryOptimizer pulledFrom) {
        if (firstOpt == pulledFrom && !firstOpt.isEmpty())
            return firstOpt.getMinExtractableItemStackSize(pulledFrom);
        if (secondOpt == pulledFrom && !secondOpt.isEmpty())
            return secondOpt.getMinExtractableItemStackSize(pulledFrom);

        if (Settings.debugOptimizedInventories)
            throw new IllegalArgumentException("InventoryOptimizer must be child of this.");
        else
            return 64;
    }

    //store state of take signal strength in first opt, as the double inventory optimizer should be stateless
    @Override
    public boolean hasFakeSignalStrength() {
        return firstOpt.hasFakeSignalStrength();
    }

    @Override
    public int getFakeSignalStrength() {
        return firstOpt.getFakeSignalStrength();
    }

    //Used to trick comparators into sending block updates like in vanilla.
    @Override
    void setFakeReducedSignalStrength() {
        if (Settings.debugOptimizedInventories && this.hasFakeSignalStrength())
            throw new IllegalStateException("Already using fake signal strength");

        firstOpt.setFakeReducedSignalStrength(this.getSignalStrength() - 1);
    }

    @Override
    void clearFakeChangedSignalStrength() {
        firstOpt.clearFakeChangedSignalStrength();
    }

    @Override
    public boolean isEmpty() {
        return firstOpt.isEmpty() && secondOpt.isEmpty();
    }
}
