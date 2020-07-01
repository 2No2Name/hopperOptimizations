package hopperOptimizations.mixins.inventoryOptimizer.inventories;

import hopperOptimizations.utils.inventoryOptimizer.DoubleInventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements OptimizedInventory {
    @Shadow
    @Final
    private Inventory first;
    @Shadow
    @Final
    private Inventory second;

    private DoubleInventoryOptimizer optimizer; //Make sure this is only used when both of its halfs have optimizers

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
}
