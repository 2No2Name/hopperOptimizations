package hopperOptimizations.utils;

import hopperOptimizations.feature.inventory_optimization.BitSetOptimizedStackList;
import hopperOptimizations.feature.inventory_optimization.DoubleInventoryHalfStackList;
import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

public class InventoryListOptimizedAccess {

    public static OptimizedStackList castOptimizedInventoryListOrUpgrade(OptimizedInventory inventory, DefaultedList<ItemStack> stackList) {
        if (stackList instanceof OptimizedStackList) {
            return (OptimizedStackList) stackList;
        }
        return upgrade(inventory, stackList);
    }

    public static OptimizedStackList upgrade(OptimizedInventory inventory, DefaultedList<ItemStack> stackList) {
        if (stackList instanceof DoubleInventoryHalfStackList) {
            ((DoubleInventoryHalfStackList) stackList).unregisterStacks();
        }

        OptimizedStackList optimizedStackList = OptimizedStackList.convertFrom(stackList, inventory);
        inventory.setInventory_HopperOptimizations(optimizedStackList);
        return optimizedStackList;
    }

    public static OptimizedStackList getOptimizedInventoryListOrNull(OptimizedInventory inventory) {
        DefaultedList<ItemStack> stackList = inventory.getInventory_HopperOptimizations();
        if (stackList instanceof OptimizedStackList) {
            return (BitSetOptimizedStackList) stackList;
        }
        return null;
    }
}
