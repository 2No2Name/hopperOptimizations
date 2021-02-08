package hopperOptimizations.feature.inventory_optimization;

import hopperOptimizations.utils.InventoryListOptimizedAccess;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    default OptimizedStackList getOptimizedStackList() {
        DefaultedList<ItemStack> stackList = this.getInventory_HopperOptimizations();
        if (stackList instanceof OptimizedStackList) {
            return (OptimizedStackList) stackList;
        }
        return InventoryListOptimizedAccess.upgrade(this, stackList);
    }

    default DefaultedList<ItemStack> getDoubleInventoryHalfStackList(Object parent, int indexOffset) {
        DefaultedList<ItemStack> stackList = this.getInventory_HopperOptimizations();
        if (!(stackList instanceof DoubleInventoryHalfStackList) || ((DoubleInventoryHalfStackList) stackList).parent != parent) {
            if (stackList instanceof OptimizedStackList) {
                ((OptimizedStackList) stackList).unregisterStacks();
            } else if (stackList instanceof DoubleInventoryHalfStackList) {
                ((DoubleInventoryHalfStackList) stackList).unregisterStacks();
            }
            stackList = new DoubleInventoryHalfStackList(stackList.delegate, stackList.initialElement, (DoubleInventory) parent, indexOffset);
            this.setInventory_HopperOptimizations(stackList);
        }
        return stackList;
    }

    //avoid name conflicts with other mods: getInventory() by appending _HopperOptimizations
    DefaultedList<ItemStack> getInventory_HopperOptimizations();

    void setInventory_HopperOptimizations(DefaultedList<ItemStack> inventory);

}
