package hopperOptimizations.utils;

import net.minecraft.inventory.Inventory;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    InventoryOptimizer getOptimizer();

    void invalidateOptimizer(); //For player actions (probably many uncontrolled actions, can be fixed if neccessary)

    //Only used for chests, cached double inventories can check whether they are still up to date. e.g. both chest halfs still exist
    default boolean isStillValid() {
        return true;
    }

}
