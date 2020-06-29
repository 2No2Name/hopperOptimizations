package hopperOptimizations.mixins.cacheInventories.cacheInventoryBlocks;

import hopperOptimizations.workarounds.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.workarounds.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.block.ComposterBlock$DummyInventory")
public class ComposterBlockDummyInventoryMixin implements INoExtractInventoryUntilBlockUpdate, IValidInventoryUntilBlockUpdate {

    @Override
    public boolean isValid() {
        return true;
    }
}