package hopperOptimizations.mixins.inventoryOptimizer;

import hopperOptimizations.utils.InventoryListOptimized;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements InventoryListOptimized.IItemStackCaller {
    @Nullable
    private InventoryListOptimized myInventoryList;
    private int slotIndex;

    @Shadow
    private int count;

    @Override
    public void setInInventory(InventoryListOptimized myInventoryList, int slotIndex) {
        this.myInventoryList = myInventoryList;
        this.slotIndex = slotIndex;
    }

    @Override
    public void removeFromInventory(InventoryListOptimized myInventoryList, int slotIndex) {
        if (this.myInventoryList == myInventoryList && this.slotIndex == slotIndex) {
            this.myInventoryList = null;
            this.slotIndex = 0;
        }
    }

    @Inject(method = "setCount", at = @At(value = "HEAD"))
    private void updateOptimizerData(int count, CallbackInfo ci) {
        if (this.myInventoryList != null) {
            this.myInventoryList.itemStackChangesCount((ItemStack) (Object) this, this.slotIndex, this.count, count);
        }
    }
}

