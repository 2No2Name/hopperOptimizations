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
    public void registerToInventory(OptimizedStackList myInventoryList, int slotIndex) {
        if (this.isEmpty()) {
            return;
        }

        if (this.myInventoryList != null) {
            //currently upgrading to double inventories and downgrading double inventories
            Logger.getLogger("HopperOptimizations").warning(
                    String.format("Registering stack %s to inventory: %s even though stacks thinks it is still in: %s!",
                            this,
                            myInventoryList.parent,
                            this.myInventoryList != null ? this.myInventoryList.parent : "no inventory")
            );
        }
        this.myInventoryList = myInventoryList;
        this.slotIndex = slotIndex;
    }

    @Override
    public void unregisterFromInventory(@Nullable OptimizedStackList myInventoryList, int slotIndex) {
        if (!this.isEmpty() && (this.slotIndex != slotIndex || (myInventoryList != this.myInventoryList && myInventoryList != null))) {
            Logger.getLogger("HopperOptimizations").warning(
                    String.format("Unregistering stack %s from inventory: %s but stack says it is in: %s!",
                            this,
                            myInventoryList == null ? "unknown inventory" : myInventoryList.parent,
                            this.myInventoryList != null ? this.myInventoryList.parent : "no inventory")
            );
        }
        this.myInventoryList = null;
        this.slotIndex = 0;
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

