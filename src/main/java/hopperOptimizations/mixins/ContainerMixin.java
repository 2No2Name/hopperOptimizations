package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.container.Slot;
import net.minecraft.container.SlotActionType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Feature("optimizedInventories")
@Mixin(net.minecraft.container.Container.class)
public abstract class ContainerMixin {
    @Shadow
    @Final
    public List<Slot> slotList;

    @Inject(method = "calculateComparatorOutput(Lnet/minecraft/inventory/Inventory;)I", at = @At(value = "HEAD"), cancellable = true)
    private static void getFastOutputStrength(Inventory inventory_1, CallbackInfoReturnable<Integer> cir) {
        if (Settings.optimizedInventories && inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer();
            if (opt != null)
                cir.setReturnValue(opt.getSignalStrength());
        }
    }

    //Code for player interaction handling.
    //Explicitly tell the Inventory when a stack is changed (setCount, incr, decr, splitStack), otherwise the optimizer
    //data will become outdated, which will lead to incorrect behavior (e.g. assuming all slots are occupied etc)
    //use InventoryOptimizer.DEBUG = true to check whether the data has become outdated due to unnoticed inventory changes.
    private int newCount = 0;
    private int prevCount = 0;
    private int changeCount = 0;
    private ItemStack curStack;
    private Inventory tmpInventory;

    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setCount(I)V", ordinal = 0))
    private void helpNotifyInventory(ItemStack itemStack, int count) {
        prevCount = itemStack.getCount();
        itemStack.setCount(count);
        newCount = count;
        curStack = itemStack;
    }

    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;setCount(I)V", ordinal = 0, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventory(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot, ItemStack itemStack, int j) {
        Inventory inventory;
        if (Settings.optimizedInventories && (inventory = this.slotList.get(0).inventory) instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(stack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) inventory).invalidateOptimizer();
                    return;
                }
                opt.onItemStackCountChanged(index, newCount - prevCount);
            }
        }
    }

    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void helpNotifyInventory2(ItemStack itemStack, int count) {
        itemStack.decrement(count);
        changeCount = -count;
        curStack = itemStack;
    }

    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventory2(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot, ItemStack itemStack, ItemStack var10, int var11) {
        Inventory inventory;
        if (Settings.optimizedInventories && (inventory = this.slotList.get(0).inventory) instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(stack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) inventory).invalidateOptimizer();
                    return;
                }
                opt.onItemStackCountChanged(index, changeCount);
            }
        }
    }

    //Injecting at both ordinal 0 and 1
    @Inject(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD)
    private void helpNotifyInventory3(ItemStack stack, int startIndex, int endIndex, boolean fromLast, CallbackInfoReturnable<Boolean> cir, boolean bl, int i, Slot slot2) {
        tmpInventory = this.slotList.get(0).inventory;
    }

    //Redirecting at both ordinal 0 and 1
    @Redirect(method = "insertItem(Lnet/minecraft/item/ItemStack;IIZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack notifyInventory3(ItemStack itemStack, int count) {
        ItemStack ret = itemStack.split(count);
        if (Settings.optimizedInventories && tmpInventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) tmpInventory).getOptimizer();
            if (opt != null) {
                int index = opt.indexOfObject(itemStack);
                if (index == -1) {
                    //throw new IllegalStateException(); //this should not happen, but still handle it
                    ((OptimizedInventory) tmpInventory).invalidateOptimizer();
                    return ret;
                }
                opt.onItemStackCountChanged(index, -count);
                return ret;
            }
        }
        return ret;
    }

    //ordinal 1 in decompiled code, but ordinal 0 in bytecode!!
    @Inject(method = "onSlotClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;increment(I)V", ordinal = 0, shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventory(int slotId, int clickData, SlotActionType actionType, PlayerEntity playerEntity, CallbackInfoReturnable<ItemStack> cir, ItemStack itemStack, PlayerInventory playerInventory, Slot slot4, ItemStack itemStack7, ItemStack itemStack8, int p) {
        Inventory inventory;
        if (Settings.optimizedInventories && (inventory = this.slotList.get(0).inventory) instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) {
                opt.onItemStackCountChanged(slotId, p);
            }
        }
    }
}
