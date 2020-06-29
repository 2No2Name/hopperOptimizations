package hopperOptimizations.mixins.cacheInventories;

import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements OptimizedInventory {
    @Shadow
    @Final
    private Inventory first;
    @Shadow
    @Final
    private Inventory second;
    //Invalidate this DoubleInventory when one half is invalidated.
    //This wasn't necessary in vanilla, because the DoubleInventory object was recreated every time the doublechest was accessed.
    private int firstRemovedCount;
    private int secondRemovedCount;
    private boolean invalid; //If true, this inventory will not be cached and will not be reused from a cache.

    @Inject(method = "<init>(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;)V", at = @At(value = "RETURN"))
    private void initValidityCheck(Inventory inventory_1, Inventory inventory_2, CallbackInfo ci) {
        if (inventory_1 == inventory_2) {
            invalid = true;
            return;
        }

        if (!(inventory_1 instanceof ChestBlockEntity) || !(inventory_2 instanceof ChestBlockEntity) ||
                !(inventory_1 instanceof OptimizedInventory) || !(inventory_2 instanceof OptimizedInventory)) {
            invalid = true;
            return;
        }
        firstRemovedCount = ((Interfaces.BlockEntityInterface) inventory_1).getRemovedCount();
        secondRemovedCount = ((Interfaces.BlockEntityInterface) inventory_2).getRemovedCount();
        invalid = (firstRemovedCount == -1 || secondRemovedCount == -1);
    }

    //Allows caching the inventory safely
    @Override
    public boolean isStillValid() {
        return !this.invalid && !(this.invalid = firstRemovedCount != ((Interfaces.BlockEntityInterface) first).getRemovedCount()) &&
                !(this.invalid = secondRemovedCount != ((Interfaces.BlockEntityInterface) second).getRemovedCount());
    }

}
