package hopperOptimizations.mixins;


import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.utils.InventoryOptimizer;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;


@Feature("optimizedInventories")
@Mixin(HopperMinecartEntity.class)
public class HopperMinecartEntityMixin implements IHopper {

    //Duplicated code from HopperBlockEntityMixin, don't know where else to store those fields:
    //Fields for optimizedInventories
    private int this_lastChangeCount_Insert;
    private InventoryOptimizer previousInsert;
    private int previousInsert_lastChangeCount;

    private int this_lastChangeCount_Extract;
    private InventoryOptimizer previousExtract;
    private int previousExtract_lastChangeCount;
    private boolean previousExtract_causeMarkDirty;

    @Feature("optimizedInventories")
    public boolean tryShortcutFailedTransfer(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt, boolean extracting) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (extracting) {
            if (this_lastChangeCount_Extract != thisChangeCount || !otherOpt.equals(previousExtract) || previousExtract_lastChangeCount != otherChangeCount) {
                this_lastChangeCount_Extract = thisChangeCount;
                previousExtract = otherOpt;
                previousExtract_lastChangeCount = otherChangeCount;
                previousExtract_causeMarkDirty = false;
                return false;
            }
            if (previousExtract_causeMarkDirty && !Settings.failedTransferNoComparatorUpdates)
                IHopper.markDirtyLikeHopperWould(other, otherOpt); //failed transfers sometimes cause comparator updates

            return true;
        } else {
            if (this_lastChangeCount_Insert != thisChangeCount || !otherOpt.equals(previousInsert) || previousInsert_lastChangeCount != otherChangeCount) {
                this_lastChangeCount_Insert = thisChangeCount;
                previousInsert = otherOpt;
                previousInsert_lastChangeCount = otherChangeCount;
                return false;
            }
            return true;
        }
    }

    @Feature("optimizedInventories")
    public void setMarkOtherDirty() {
        this.previousExtract_causeMarkDirty = true;
    }
}
