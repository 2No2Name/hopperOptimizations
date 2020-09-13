package hopperOptimizations.mixins.fast_signal_strength;

import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static hopperOptimizations.feature.inventory_optimization.OptimizedStackList.SMALL_POWER_OF_TWO_STACKSIZES_ONLY;

@Mixin(net.minecraft.screen.ScreenHandler.class)
public abstract class ScreenHandlerMixin {
    @Inject(method = "calculateComparatorOutput(Lnet/minecraft/inventory/Inventory;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;size()I", shift = At.Shift.BEFORE, ordinal = 0), cancellable = true)
    private static void getFastOutputStrength(Inventory inventory, CallbackInfoReturnable<Integer> cir) {
        OptimizedStackList opt;
        if (SMALL_POWER_OF_TWO_STACKSIZES_ONLY &&
                Integer.bitCount(inventory.getMaxCountPerStack()) == 1 &&
                inventory instanceof OptimizedInventory &&
                null != (opt = ((OptimizedInventory) inventory).getOptimizedStackList())) {
            cir.setReturnValue(opt.getSignalStrength());
        }
    }
}
