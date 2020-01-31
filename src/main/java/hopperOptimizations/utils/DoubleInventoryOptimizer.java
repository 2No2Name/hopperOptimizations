package hopperOptimizations.utils;

import hopperOptimizations.settings.Settings;
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
        super(null, null);
        this.first = first;
        this.second = second;
        this.firstOpt = first.getOptimizer();
        this.secondOpt = second.getOptimizer();
    }

    public boolean isInvalid() {
        return super.isInvalid() || firstOpt == null || firstOpt.isInvalid() || secondOpt == null || secondOpt.isInvalid();
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
    public void onItemStackCountChanged(int index, int countChange) {
        int firstSize = first.getInvSize();
        if (index >= firstSize) {
            if (secondOpt != null) secondOpt.onItemStackCountChanged(index - firstSize, countChange);
        } else {
            if (firstOpt != null) firstOpt.onItemStackCountChanged(index, countChange);
        }
    }

    public int indexOf_extractable_endIndex(ItemStack stack, int stop) {
        ensureInitialized();
        int ret = firstOpt.indexOf_extractable_endIndex(stack, stop);
        if (ret == -1) {
            ret = secondOpt.indexOf_extractable_endIndex(stack, stop);
            if (ret != -1)
                ret += first.getInvSize();
        }
        return ret;
    }

    public boolean hasFreeSlots_insertable() {
        ensureInitialized();
        return firstOpt.hasFreeSlots_insertable() || secondOpt.hasFreeSlots_insertable();
    }

    public int findInsertSlot(ItemStack stack, Direction fromDirection) {
        ensureInitialized();
        int ret = firstOpt.findInsertSlot(stack, fromDirection);
        if (ret == -1) {
            ret = secondOpt.findInsertSlot(stack, fromDirection);
            if (ret != -1)
                ret += first.getInvSize();
        }
        return ret;
    }

    @Override
    public int getFirstFreeSlot() {
        ensureInitialized();

        int ret = firstOpt.getFirstFreeSlot();
        if (ret == -1) {
            ret = secondOpt.getFirstFreeSlot();
            if (ret != -1)
                ret += first.getInvSize();
        }
        return ret;
    }

    @Override
    public boolean isFull_insertable(Direction fromDirection) {
        ensureInitialized();

        return firstOpt.isFull_insertable(fromDirection) && secondOpt.isFull_insertable(fromDirection);
    }

    @Override
    public boolean isInitialized() {
        return firstOpt.isInitialized() && secondOpt.isInitialized();
    }

    @Override
    protected ItemStack getSlot(int index) {
        if (index < 0) return ItemStack.EMPTY;
        int firstSize = first.getInvSize();
        if (index < firstSize) return first.getInvStack(index);
        return second.getInvStack(index - firstSize);
    }

    @Override
    protected int size() {
        return first.getInvSize() + second.getInvSize();
    }

    @Override
    public int getOccupiedSlots() {
        ensureInitialized();
        return firstOpt.getOccupiedSlots() + secondOpt.getOccupiedSlots();
    }

    @Override
    public int getItemTypeChanges() {
        ensureInitialized();
        return firstOpt.getItemTypeChanges() + secondOpt.getItemTypeChanges();
    }

    public int getFirstOccupiedSlot_extractable() {
        ensureInitialized();
        int ret = firstOpt.getFirstOccupiedSlot_extractable();
        if (ret == -1) {
            ret = secondOpt.getFirstOccupiedSlot_extractable();
            if (ret != -1)
                ret += first.getInvSize();
        }
        return ret;
    }

    /*public boolean equals(Object other) {
        return other == this;
        //if(!(other instanceof DoubleInventoryOptimizer)) return false;
        //return this.first == ((DoubleInventoryOptimizer) other).first && this.second == ((DoubleInventoryOptimizer) other).second;
    }*/

    public int getInventoryChangeCount() {
        ensureInitialized();
        return firstOpt.getInventoryChangeCount() + secondOpt.getInventoryChangeCount();
    }

    void ensureInitialized() {
        firstOpt.ensureInitialized();
        secondOpt.ensureInitialized();
    }

    int getWeightedItemCount() {
        ensureInitialized();
        return firstOpt.getWeightedItemCount() + secondOpt.getWeightedItemCount();
    }

    int getTotalSlots() {
        ensureInitialized();
        return firstOpt.getTotalSlots() + secondOpt.getTotalSlots();
    }

    /**
     * Finds the slot that contains the given itemstack object (object comparison, no equals)
     *
     * @return index of the stack object, -1 if none found.
     */
    public int indexOfObject(ItemStack stack) {
        ensureInitialized();
        int ret = firstOpt.indexOfObject(stack);
        if (ret == -1) {
            ret = secondOpt.indexOfObject(stack);
            if (ret != -1)
                ret += first.getInvSize();
        }
        return ret;
    }

    public int getMinExtractableItemStackSize(InventoryOptimizer pulledFrom) {
        if (firstOpt == pulledFrom && !firstOpt.isInvEmpty_Extractable())
            return firstOpt.getMinExtractableItemStackSize(pulledFrom);
        if (secondOpt == pulledFrom && !secondOpt.isInvEmpty_Extractable())
            return secondOpt.getMinExtractableItemStackSize(pulledFrom);

        if (Settings.debugOptimizedInventories)
            throw new IllegalArgumentException("InventoryOptimizer must be child of this.");
        else
            return 64;
    }

    public void setInvalid() {
        super.setInvalid();
        firstOpt.setInvalid();
        secondOpt.setInvalid();
    }


    //store state of take signal strength in first opt, as the double inventory optimizer should be stateless
    public boolean hasFakeSignalStrength() {
        return firstOpt.hasFakeSignalStrength();
    }

    public int getFakeSignalStrength() {
        return firstOpt.getFakeSignalStrength();
    }

    //Used to trick comparators into sending block updates like in vanilla.
    void setFakeReducedSignalStrength() {
        this.ensureInitialized();
        if (Settings.debugOptimizedInventories && this.hasFakeSignalStrength())
            throw new IllegalStateException("Already using fake signal strength");

        firstOpt.setFakeReducedSignalStrength(this.getSignalStrength() - 1);
    }

    void clearFakeChangedSignalStrength() {
        firstOpt.clearFakeChangedSignalStrength();
    }

    boolean isInvEmpty_Extractable() {
        return firstOpt.isInvEmpty_Extractable() && secondOpt.isInvEmpty_Extractable();
    }
}
