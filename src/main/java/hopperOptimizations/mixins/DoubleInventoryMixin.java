package hopperOptimizations.mixins;

import hopperOptimizations.utils.DoubleInventoryOptimizer;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import hopperOptimizations.workarounds.BlockEntityInterface;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

//@Feature("optimizedInventories")
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

    private DoubleInventoryOptimizer optimizer; //Make sure this is only used when both of its halfs have optimizers


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
        firstRemovedCount = ((BlockEntityInterface) inventory_1).getRemovedCount();
        secondRemovedCount = ((BlockEntityInterface) inventory_2).getRemovedCount();
        invalid = (firstRemovedCount == -1 || secondRemovedCount == -1);
    }

    @Override
    @Nullable
    public InventoryOptimizer getOptimizer(boolean create) {
        if (this.optimizer == null) {
            if (!create || (this instanceof SidedInventory) ||
                    !(this.first instanceof OptimizedInventory) || !(this.second instanceof OptimizedInventory) ||
                    ((OptimizedInventory) first).getOptimizer(true) == null || ((OptimizedInventory) second).getOptimizer(true) == null) {
                return null;
            }
            this.optimizer = new DoubleInventoryOptimizer((OptimizedInventory) first, (OptimizedInventory) second);
        }
        return this.optimizer;
    }

    //Allows caching the inventory safely
    public boolean isStillValid() {
        return !this.invalid && !(this.invalid = firstRemovedCount != ((BlockEntityInterface) first).getRemovedCount()) &&
                !(this.invalid = secondRemovedCount != ((BlockEntityInterface) second).getRemovedCount());
    }

}
