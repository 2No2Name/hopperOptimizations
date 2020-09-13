package hopperOptimizations.utils;

import hopperOptimizations.feature.comparator_updating.ComparatorUpdateFakeMode;
import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;

public interface IHopper {


    void setComparatorUpdateFakeMode(ComparatorUpdateFakeMode fakeMode);

    /**
     * Checks whether the last item extract attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     *
     * @param other Inventory interacted with
     * @return Whether the current item transfer attempt is known to fail.
     */
    default boolean tryShortcutFailedExtract(OptimizedStackList thisOpt, OptimizedInventory other, OptimizedStackList otherOpt) {
        return false;
    }


    default void onBlockUpdate() {
    }
}
