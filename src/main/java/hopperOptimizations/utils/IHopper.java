package hopperOptimizations.utils;

import hopperOptimizations.utils.inventoryOptimizer.InventoryOptimizer;
import net.minecraft.inventory.Inventory;

public interface IHopper {


    void setMarkOtherDirty();

    /**
     * Checks whether the last item extract attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param other    Inventory interacted with
     * @param otherOpt InventoryOptimizer of other
     *
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    default boolean tryShortcutFailedExtract(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt) {
        return false;
    }

    /**
     * Checks whether the last item insert attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param otherOpt InventoryOptimizer of other
     *
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    default boolean tryShortcutFailedInsert(InventoryOptimizer thisOpt, InventoryOptimizer otherOpt) {
        return false;
    }


    default void onBlockUpdate() {
    }
}
