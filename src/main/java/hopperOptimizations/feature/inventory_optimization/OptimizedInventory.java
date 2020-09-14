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
        return InventoryListOptimizedAccess.getOptimizedInventoryListOrUpgrade(this);
    }

    default DefaultedList<ItemStack> getDoubleInventoryHalfStackList(Object parent) {
        DefaultedList<ItemStack> stackList = this.getInventory();
        if (!(stackList instanceof DoubleInventoryHalfStackList) || ((DoubleInventoryHalfStackList) stackList).parent != parent) {
            if (stackList instanceof OptimizedStackList) {
                stackList = ((OptimizedStackList) stackList).getDoubleInventoryHalfStackList((DoubleInventory) parent);
            } else if (stackList instanceof DoubleInventoryHalfStackList) {
                ((DoubleInventoryHalfStackList) stackList).unregisterStacks();
            } else {
                stackList = new DoubleInventoryHalfStackList(stackList.delegate, stackList.initialElement, (DoubleInventory) parent);
            }
            this.setInventory(stackList);
        }
        return stackList;
    }

    DefaultedList<ItemStack> getInventory();

    void setInventory(DefaultedList<ItemStack> inventory);

}
