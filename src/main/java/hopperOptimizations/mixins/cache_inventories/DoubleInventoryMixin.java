package hopperOptimizations.mixins.cache_inventories;

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
        int i = ((RemovedCounter) first).getRemovedCount();
        int j = ((RemovedCounter) second).getRemovedCount();
        return i == -1 ? -1 : j == -1 ? -1 : i + j;
    }

    @Override
    public void increaseRemoveCounter() {
        //It isn't expected that this is called on Double Inventories
        ((RemovedCounter) first).increaseRemoveCounter();
        ((RemovedCounter) second).increaseRemoveCounter();
    }
}
