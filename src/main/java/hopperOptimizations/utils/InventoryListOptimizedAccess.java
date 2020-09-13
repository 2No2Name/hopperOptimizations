package hopperOptimizations.utils;

import hopperOptimizations.feature.inventory_optimization.BitSetOptimizedStackList;
import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
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
