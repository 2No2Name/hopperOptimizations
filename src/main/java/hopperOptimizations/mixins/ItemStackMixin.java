package hopperOptimizations.mixins;

import hopperOptimizations.settings.Settings;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    //Optimization: As ItemStack already caches whether it is empty, actually use the cached value.
    @Shadow
    private boolean empty;
    @Shadow
    private int count;
    @Shadow
    @Final
    private Item item;

    @Inject(method = "isEmpty", at = @At(value = "HEAD"), cancellable = true)
    private void returnCachedEmpty(CallbackInfoReturnable<Boolean> cir) {
        if (Settings.optimizedItemStackEmptyCheck)
            cir.setReturnValue(this.empty);
    }

    @Redirect(method = "updateEmptyState", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"))
    private boolean isEmptyRecalculate(ItemStack itemStack) {
        return (this.item == null || this.item == Items.AIR || this.count <= 0);
    }
}

