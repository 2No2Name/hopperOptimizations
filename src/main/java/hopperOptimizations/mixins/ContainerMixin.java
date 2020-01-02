package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.container.Slot;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Feature("optimizedInventories")
@Mixin(net.minecraft.container.Container.class)
public abstract class ContainerMixin {
    @Inject(method = "calculateComparatorOutput(Lnet/minecraft/inventory/Inventory;)I", at = @At(value = "HEAD"), cancellable = true)
    private static void getFastOutputStrength(Inventory inventory_1, CallbackInfoReturnable<Integer> cir) {
        if (inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer();
            if (opt != null)
                cir.setReturnValue(opt.getSignalStrength());
        }
    }

    //Code for player interaction handling.
    //Explicitly tell the Inventory when a stack is changed (setCount, incr, decr, splitStack), otherwise the optimizer
    //data will become outdated, which will lead to incorrect behavior (e.g. assuming all slots are occupied etc)
    //use InventoryOptimizer.DEBUG = true to check whether the data has become outdated due to unnoticed inventory changes.
    private int tmpCount = 0;
    private Inventory tmpInventory;

    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setCount(I)V", ordinal = 0))
    private void helpNotifyInventory(ItemStack itemStack, int count) {
        tmpCount = count;
    }

    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setCount(I)V", ordinal = 0, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventory(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot, ItemStack itemStack, int j) {
        if (slot.inventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) slot.inventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(stack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) slot.inventory).invalidateOptimizer();
                    stack.setCount(tmpCount);
                    return;
                }
                int prevStackSize = stack.getCount();
                stack.setCount(tmpCount);
                opt.onItemStackCountChanged(index, tmpCount - prevStackSize);
            }
        } else {
            stack.setCount(tmpCount);
        }
    }

    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void helpNotifyInventory2(ItemStack itemStack, int count) {
        tmpCount = count;
    }

    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventory2(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot, ItemStack itemStack, ItemStack var10, int var11) {
        if (slot.inventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) slot.inventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(stack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) slot.inventory).invalidateOptimizer();
                    stack.decrement(tmpCount);
                    return;
                }
                stack.decrement(tmpCount);
                opt.onItemStackCountChanged(index, -tmpCount);
                return;
            }
        }
        stack.decrement(tmpCount);
    }

    //Injecting at both ordinal 0 and 1
    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void helpNotifyInventory3(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot2) {
        tmpInventory = slot2.inventory;
    }

    //Redirecting at both ordinal 0 and 1
    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack notifyInventory3(ItemStack itemStack, int count) {
        if (tmpInventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) tmpInventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(itemStack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) tmpInventory).invalidateOptimizer();
                    return itemStack.split(count);
                }
                ItemStack ret = itemStack.split(count);
                opt.onItemStackCountChanged(index, -count);
                return ret;
            }
        }
        return itemStack.split(count);
    }
}
