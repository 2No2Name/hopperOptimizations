package hopperOptimizations.utils;

import hopperOptimizations.settings.Settings;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public interface OptimizedInventory extends Inventory {
    @Nullable
    InventoryOptimizer getOptimizer(boolean create);

    default InventoryOptimizer getOptimizer(boolean tryCreate, InventoryListOptimized inventory) {
        return inventory.getCreateOrRemoveOptimizer(this, tryCreate);
    }

    default boolean mayHaveOptimizer() {
        return !(this instanceof SidedInventory) && Settings.optimizedInventories && this.getWorld() != null && !this.getWorld().isClient;
    }

    @Nullable
    World getWorld();

    @Deprecated
    void invalidateOptimizer(); //For player actions (probably many uncontrolled actions, can be fixed if neccessary)

    //Only used for chests, cached double inventories can check whether they are still up to date. e.g. both chest halfs still exist
    default boolean isStillValid() {
        return true;
    }

}
