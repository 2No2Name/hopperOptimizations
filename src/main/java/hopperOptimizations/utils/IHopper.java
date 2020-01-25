package hopperOptimizations.utils;

import hopperOptimizations.annotation.Feature;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;

public interface IHopper {

    @Feature("optimizedInventories")
    static void markDirtyLikeHopperWould(Inventory inv, InventoryOptimizer opt, InventoryOptimizer masterOpt) {
        if (masterOpt == null) masterOpt = opt;
        if (opt instanceof DoubleInventoryOptimizer) {
            //Double Inventories are handled like two seperate inventories in vanilla when sending useless comparator updates
            markDirtyLikeHopperWould(((DoubleInventoryOptimizer) opt).getFirstInventory(), ((DoubleInventoryOptimizer) opt).getFirstOptimizer(), opt);
            markDirtyLikeHopperWould(((DoubleInventoryOptimizer) opt).getSecondInventory(), ((DoubleInventoryOptimizer) opt).getSecondOptimizer(), opt);
            return;
        }
        if (opt.getFirstOccupiedSlot_extractable() == -1)
            return; //empty inventory halfs don't send updates

        boolean fakeSignalStrengthChange = masterOpt.canOneExtractDecreaseSignalStrength();
        if (fakeSignalStrengthChange) {
            //crazy workaround to send stupid comparator updates to comparators and make the comparators send updates to even more redstone components
            //also required for comparator to schedule useless but detectable updates on themselves
            masterOpt.setFakeReducedSignalStrength();
            inv.setInvStack(0, inv.getInvStack(0));
            masterOpt.clearFakeChangedSignalStrength();
        }

        inv.setInvStack(0, inv.getInvStack(0));
    }

    @Feature("optimizedInventories")
    void setMarkOtherDirty();

    /**
     * Checks whether the last item extract attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param other    Inventory interacted with
     * @param otherOpt InventoryOptimizer of other
     *                 <p>
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Feature("optimizedInventories")
    boolean tryShortcutFailedExtract(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt);

    /**
     * Checks whether the last item insert attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param other    Inventory interacted with
     * @param otherOpt InventoryOptimizer of other
     *                 <p>
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Feature("optimizedInventories")
    boolean tryShortcutFailedInsert(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt);

    @Feature("optimizedEntityHopperInteraction")
    default void notifyOfNearbyEntity(Entity entity) {
    }

    default void onBlockUpdate() {
    }
}
