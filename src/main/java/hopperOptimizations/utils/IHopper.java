package hopperOptimizations.utils;

import hopperOptimizations.annotation.Feature;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;

public interface IHopper {

    @Feature("optimizedInventories")
    static void markDirtyLikeHopperWould(Inventory inv, InventoryOptimizer opt) {
        boolean fakeSignalStrengthChange = opt.isOneItemAboveSignalStrength();
        if (fakeSignalStrengthChange) {
            //crazy workaround to send stupid comparator updates to comparators and make the comparators send updates to even more redstone components
            //also required for comparator to schedule useless but detectable updates on themselves
            opt.setFakeReducedSignalStrength();
            inv.setInvStack(0, inv.getInvStack(0));
            opt.clearFakeChangedSignalStrength();
        }

        inv.setInvStack(0, inv.getInvStack(0));
    }

    boolean tryShortcutFailedTransfer(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt, boolean extracting);

    void setMarkOtherDirty();

    default void notifyOfNearbyEntity(Entity entity) {
    }
}
