package hopperOptimizations.mixins.cache_inventories.cacheInventoryBlocks;

import hopperOptimizations.feature.cache_inventories.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.feature.cache_inventories.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.block.ComposterBlock$DummyInventory")
public class ComposterBlockDummyInventoryMixin implements INoExtractInventoryUntilBlockUpdate, IValidInventoryUntilBlockUpdate {

    @Override
    public boolean isValid() {
        return true;
    }
}