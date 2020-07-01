package hopperOptimizations.mixins.cacheInventories.cacheInventoryBlocks;

import hopperOptimizations.features.cacheInventories.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.features.cacheInventories.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.block.ComposterBlock$ComposterInventory")
public class ComposterBlockComposterInventoryMixin implements INoExtractInventoryUntilBlockUpdate, IValidInventoryUntilBlockUpdate {

    @Shadow
    private boolean dirty;

    @Override
    public boolean isValid() {
        return !dirty;
    }
}