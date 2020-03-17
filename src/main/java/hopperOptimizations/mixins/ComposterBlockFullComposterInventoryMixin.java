package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.workarounds.IValidInventoryUntilBlockUpdate;
import org.spongepowered.asm.mixin.Mixin;

@Feature("optimizedEntityHopperInteraction")
@Mixin(targets = "net.minecraft.block.ComposterBlock$FullComposterInventory")
public class ComposterBlockFullComposterInventoryMixin implements IValidInventoryUntilBlockUpdate {

}