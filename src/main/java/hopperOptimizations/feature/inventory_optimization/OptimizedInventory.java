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
        DefaultedList<ItemStack> inventory = this.getInventory();
        if (!(inventory instanceof DoubleInventoryHalfStackList) || ((DoubleInventoryHalfStackList) inventory).parent != parent) {
            if (inventory instanceof OptimizedStackList) {
                inventory = ((OptimizedStackList) inventory).getDoubleInventoryHalfStackList((DoubleInventory) parent);
            } else {
                inventory = new DoubleInventoryHalfStackList(inventory.delegate, inventory.initialElement, (DoubleInventory) parent);
            }
            this.setInventory(inventory);
        }
        return inventory;
    }

    DefaultedList<ItemStack> getInventory();

    void setInventory(DefaultedList<ItemStack> inventory);

}
