package hopperOptimizations.utils.inventoryOptimizer;

import hopperOptimizations.utils.InventoryListOptimizedAccess;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    default OptimizedStackList getOptimizedStackList() {
        return InventoryListOptimizedAccess.getOptimizedInventoryListOrUpgrade(this);
    }

    default DefaultedList<ItemStack> getDowngradedStackList() {
        DefaultedList<ItemStack> inventory = this.getInventory();
        if (inventory instanceof OptimizedStackList) {
            this.setInventory(inventory = (((OptimizedStackList) inventory).getDowngraded()));
        }
        return inventory;
    }

    DefaultedList<ItemStack> getInventory();

    void setInventory(DefaultedList<ItemStack> inventory);

}
