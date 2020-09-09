package hopperOptimizations.utils.inventoryOptimizer;

import hopperOptimizations.mixins.InventoryAccessor;
import hopperOptimizations.utils.InventoryListOptimizedAccess;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    default OptimizedStackList getOptimizedStackList() {
        return InventoryListOptimizedAccess.getOptimizedInventoryListOrUpgrade((InventoryAccessor) this);
    }

    default DefaultedList<ItemStack> getDowngradedStackList() {
        DefaultedList<ItemStack> inventory = ((InventoryAccessor) this).getInventory();
        if (inventory instanceof OptimizedStackList) {
            ((InventoryAccessor) this).setInventory(((OptimizedStackList) inventory).getDowngraded());
        }
        return inventory;
    }
}
