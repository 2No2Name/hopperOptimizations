package hopperOptimizations.mixins.fastSignalStrength;

import hopperOptimizations.utils.inventoryOptimizer.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.screen.ScreenHandler.class)
public abstract class ScreenHandler {
    @Inject(method = "calculateComparatorOutput(Lnet/minecraft/inventory/Inventory;)I", at = @At(value = "HEAD"), cancellable = true)
    private static void getFastOutputStrength(Inventory inventory_1, CallbackInfoReturnable<Integer> cir) {
        if (inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer(true);
            if (opt != null)
                cir.setReturnValue(opt.getSignalStrength());
        }
    }
}
