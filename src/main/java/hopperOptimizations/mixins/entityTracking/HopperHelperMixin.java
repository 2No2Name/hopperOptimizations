package hopperOptimizations.mixins.entityTracking;

import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(HopperHelper.class)
public class HopperHelperMixin {
    /**
     * @author 2No2Name
     */
    @Overwrite
    public static Inventory getOutputEntityInventory(HopperBlockEntity hopperBlockEntity) {
        return ((Interfaces.HopperWithEntityInventoryCache) hopperBlockEntity).getOutputEntityInventoryWithCache(hopperBlockEntity);
    }

    /**
     * @author 2No2Name
     */
    @Overwrite
    public static Inventory getInputEntityInventory(HopperBlockEntity hopperBlockEntity) {
        return ((Interfaces.HopperWithEntityInventoryCache) hopperBlockEntity).getInputEntityInventoryWithCache(hopperBlockEntity);
    }
}
