package hopperOptimizations.mixins.inventoryCheckOnBlockUpdate;

import hopperOptimizations.workarounds.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

//@Feature("optimizedEntityHopperInteraction")
@Mixin(targets = "net.minecraft.block.ComposterBlock$FullComposterInventory")
public class ComposterBlockFullComposterInventoryMixin implements IValidInventoryUntilBlockUpdate {
    @Shadow
    private boolean dirty;

    public boolean isValid() {
        return !dirty;
    }
}