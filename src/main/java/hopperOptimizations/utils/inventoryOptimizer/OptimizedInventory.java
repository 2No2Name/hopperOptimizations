package hopperOptimizations.utils.inventoryOptimizer;

import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    InventoryOptimizer getOptimizer(boolean create);

    default InventoryOptimizer getOptimizer(World world, DefaultedList<ItemStack> inventory, boolean create) {
        return !(this instanceof SidedInventory) && world != null && !world.isClient && inventory instanceof InventoryListOptimized ?
                ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this, create) :
                null;
    }

    @Nullable
    World getWorld();

    //Only used for chests, cached double inventories can check whether they are still up to date. e.g. both chest halfs still exist
    default boolean isStillValid() {
        return true;
    }
}
