package hopperOptimizations.utils;

import hopperOptimizations.annotation.Feature;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;

public interface IHopper {

    @Feature("optimizedInventories")
    static void markDirtyIfHopperWould(Inventory inv) {
        inv.setInvStack(0, inv.getInvStack(0)); //todo check if this any nonvanilla sideeffects
        // if yes, use many instanceofs instead. This is also what causes the autocrafting table to craft too early.
    }

    boolean tryShortcutFailedTransfer(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt, boolean extracting);

    void setMarkOtherDirty();

    default void notifyOfNearbyEntity(Entity entity) {
    }
}
