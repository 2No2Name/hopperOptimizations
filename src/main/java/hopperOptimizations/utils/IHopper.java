package hopperOptimizations.utils;

import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;

public interface IHopper {


//    @Feature("optimizedInventories")
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
//    @Feature("optimizedInventories")
    boolean tryShortcutFailedExtract(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt);

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
//    @Feature("optimizedInventories")
    boolean tryShortcutFailedInsert(InventoryOptimizer thisOpt, InventoryOptimizer otherOpt);

    //    @Feature("optimizedEntityHopperInteraction")
    default void notifyOfNearbyEntity(Entity entity) {
    }

    default void onBlockUpdate() {
    }
}
