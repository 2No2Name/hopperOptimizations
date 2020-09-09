package hopperOptimizations.mixins;


import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedStackList;
import hopperOptimizations.workarounds.ComparatorUpdateFakeMode;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(HopperMinecartEntity.class)
public class HopperMinecartEntityMixin implements IHopper {
    private long this_lastChangeCount_Extract;
    private OptimizedStackList previousExtract;
    private long prevExtractChangeCount;
    private ComparatorUpdateFakeMode previousMarkDirtyMode;

    /**
     * Checks whether the last item extract attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param other Inventory interacted with
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Override
    public boolean tryShortcutFailedExtract(OptimizedStackList thisOpt, OptimizedInventory other, OptimizedStackList otherOpt) {
        long thisChangeCount = thisOpt.getContentChangeCount();
        long otherChangeCount = otherOpt.getContentChangeCount();
        if (this.this_lastChangeCount_Extract != thisChangeCount || otherOpt != this.previousExtract || this.prevExtractChangeCount != otherChangeCount) {
            this.this_lastChangeCount_Extract = thisChangeCount;
            this.previousExtract = otherOpt;
            this.prevExtractChangeCount = otherChangeCount;
            this.previousMarkDirtyMode = ComparatorUpdateFakeMode.UNDETERMINED;
            return false;
        }

        this.previousMarkDirtyMode = HopperHelper.markDirtyOnUnchangedHopperInteraction(other, this.previousMarkDirtyMode, null);
        return true;
    }

    @Override
    public void setComparatorUpdateFakeMode(ComparatorUpdateFakeMode fakeMode) {
        this.previousMarkDirtyMode = fakeMode;
    }
}
