package hopperOptimizations.utils;

import hopperOptimizations.utils.inventoryOptimizer.BitSetOptimizedStackList;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedStackList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class InventoryListOptimizedAccess {
    public static OptimizedStackList getOptimizedInventoryListOrUpgrade(OptimizedInventory inventory) {
        DefaultedList<ItemStack> stackList = inventory.getInventory();
        if (stackList instanceof OptimizedStackList) {
            return (OptimizedStackList) stackList;
        }

        OptimizedStackList optimizedStackList = OptimizedStackList.convertFrom(stackList, inventory);
        inventory.setInventory(optimizedStackList);
        return optimizedStackList;
    }

    public static OptimizedStackList getOptimizedInventoryListOrNull(OptimizedInventory inventory) {
        DefaultedList<ItemStack> stackList = inventory.getInventory();
        if (stackList instanceof OptimizedStackList) {
            return (BitSetOptimizedStackList) stackList;
        }
        return null;
    }
}
