package hopperOptimizations.mixins.inventoryOptimizer;

import hopperOptimizations.utils.inventoryOptimizer.OptimizedStackList;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

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
            System.out.println("Invalid state!");
        }
        this.myInventoryList = myInventoryList;
        this.slotIndex = slotIndex;
    }

    @Override
    public void removeFromInventory(OptimizedStackList myInventoryList, int slotIndex) {
        if (this.myInventoryList == myInventoryList && this.slotIndex == slotIndex) {
            this.myInventoryList = null;
            this.slotIndex = 0;
        }
    }

    @Inject(method = "setCount", at = @At(value = "HEAD"))
    private void updateOptimizerData(int count, CallbackInfo ci) {
        if (count > 64 || count < 0) {
            System.out.println("invalid count!");
        }
        if (this.myInventoryList != null && count != this.count) {
            this.myInventoryList.onStackChange(this.slotIndex, null, count);
        }
    }
}

