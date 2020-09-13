package hopperOptimizations.mixins.fast_signal_strength;

import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Item.class)
public class ItemMixin {
    @Shadow
    @Final
    private int maxCount;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void detectUnusualItemStackSize(Item.Settings settings, CallbackInfo ci) {
        if (this.maxCount <= 0 || this.maxCount > 64 || Integer.bitCount(this.maxCount) != 1) {
            OptimizedStackList.SMALL_POWER_OF_TWO_STACKSIZES_ONLY = false;
        }
    }
}
