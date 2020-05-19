package hopperOptimizations.utils;

import net.minecraft.inventory.Inventory;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    InventoryOptimizer getOptimizer(boolean create);

    @Nullable
    World getWorld();

    //Only used for chests, cached double inventories can check whether they are still up to date. e.g. both chest halfs still exist
    default boolean isStillValid() {
        return true;
    }

}
