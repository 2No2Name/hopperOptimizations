package hopperOptimizations.mixins.inventoryCheckOnBlockUpdate;

import hopperOptimizations.workarounds.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.workarounds.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

//@Feature("optimizedEntityHopperInteraction")
@Mixin(targets = "net.minecraft.block.ComposterBlock$ComposterInventory")
public class ComposterBlockComposterInventoryMixin implements INoExtractInventoryUntilBlockUpdate, IValidInventoryUntilBlockUpdate {

    @Shadow
    private boolean dirty;

    public boolean isValid() {
        return !dirty;
    }
}