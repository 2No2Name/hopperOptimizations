package hopperOptimizations.mixins.cache_inventories.cacheInventoryBlocks;

import hopperOptimizations.feature.cache_inventories.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.feature.cache_inventories.IValidInventoryUntilBlockUpdate;
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