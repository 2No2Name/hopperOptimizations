package hopperOptimizations.mixins;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Inventories.class)
public class InventoriesMixin {
    @Inject(method = "splitStack(Ljava/util/List;II)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;split(I)Lnet/minecraft/item/ItemStack;", shift = At.Shift.AFTER))
    private static void notifyOptimizedInventoryAboutChangedItemStack(List<ItemStack> list_1, int int_1, int int_2, CallbackInfoReturnable<ItemStack> cir) {
        if (Settings.optimizedInventories && list_1 instanceof InventoryListOptimized) {
            InventoryOptimizer opt = ((InventoryListOptimized) list_1).getOrRemoveOptimizer();
            if (opt != null) {
                opt.onItemStackCountChanged(int_1, -int_2);
            }
        }
    }
}
