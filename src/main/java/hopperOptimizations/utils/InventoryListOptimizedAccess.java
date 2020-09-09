package hopperOptimizations.utils;

import hopperOptimizations.mixins.InventoryAccessor;
import hopperOptimizations.utils.inventoryOptimizer.BitSetOptimizedStackList;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedStackList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class InventoryListOptimizedAccess {
    public static OptimizedStackList getOptimizedInventoryListOrUpgrade(InventoryAccessor inventory) {
        DefaultedList<ItemStack> stackList = inventory.getInventory();
        if (stackList instanceof OptimizedStackList) {
            return (OptimizedStackList) stackList;
        }

        OptimizedStackList optimizedStackList = OptimizedStackList.convertFrom(stackList, inventory);
        inventory.setInventory(optimizedStackList);
        return optimizedStackList;
    }

    public static OptimizedStackList getOptimizedInventoryListOrNull(InventoryAccessor inventory) {
        DefaultedList<ItemStack> stackList = inventory.getInventory();
        if (stackList instanceof OptimizedStackList) {
            return (BitSetOptimizedStackList) stackList;
        }
        return null;
    }
}
