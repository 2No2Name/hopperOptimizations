package hopperOptimizations.mixins.inventory_optimization;

import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.logging.Logger;

/**
 * Mixin to track item stack counts changing, which is relevant for updating the inventory optimizers.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements OptimizedStackList.IItemStackCaller {
    @Nullable
    private OptimizedStackList myInventoryList;
    private int slotIndex;

    @Shadow
    private int count;

    @Shadow
    public abstract boolean isEmpty();

    @Override
    public void setInInventory(OptimizedStackList myInventoryList, int slotIndex) {
        if (this.isEmpty()) {
            return;
        }
        if (this.myInventoryList != null) {
            Logger.getLogger("HopperOptimizations").warning(
                    String.format("Adding stack to inventory: %s even though stacks thinks it is still in: %s!",
                            myInventoryList.parent,
                            this.myInventoryList != null ? this.myInventoryList.parent : "no inventory")
            );
        }
        this.myInventoryList = myInventoryList;
        this.slotIndex = slotIndex;
    }

    @Override
    public void removeFromInventory(OptimizedStackList myInventoryList, int slotIndex) {
        if (this.myInventoryList == myInventoryList && this.slotIndex == slotIndex) {
            this.myInventoryList = null;
            this.slotIndex = 0;
        } else {
            Logger.getLogger("HopperOptimizations").warning(
                    String.format("Removing stack from inventory: %s but stack says it is in: %s!",
                            myInventoryList.parent,
                            this.myInventoryList != null ? this.myInventoryList.parent : "no inventory")
            );
        }
    }

    @Inject(method = "setCount", at = @At(value = "HEAD"))
    private void updateOptimizerData(int count, CallbackInfo ci) {
        if (this.myInventoryList != null && count != this.count) {
            this.myInventoryList.onStackChange(this.slotIndex, null, count);

            if (count > 64 || count < 0) {
                Logger.getLogger("HopperOptimizations").warning(
                        String.format("Setting ItemStack %s to invalid count: %d", this, count)
                );
            }
        }
    }
}

