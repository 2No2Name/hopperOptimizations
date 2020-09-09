package hopperOptimizations.mixins.cacheInventories;

import hopperOptimizations.workarounds.Interfaces.RemovedCounter;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DoubleInventory.class)
public class DoubleInventoryMixin implements RemovedCounter {
    @Shadow
    @Final
    public Inventory first;
    @Shadow
    @Final
    public Inventory second;

    @Override
    public int getRemovedCount() {
        return ((RemovedCounter) first).getRemovedCount() + ((RemovedCounter) second).getRemovedCount();
    }
}
