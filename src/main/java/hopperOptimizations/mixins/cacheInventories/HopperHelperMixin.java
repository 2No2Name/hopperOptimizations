package hopperOptimizations.mixins.cacheInventories;

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
    public static Inventory getOutputBlockInventory(HopperBlockEntity hopperBlockEntity) {
        return ((Interfaces.HopperWithInventoryCache) hopperBlockEntity).getOutputInventoryWithCache(hopperBlockEntity);
    }

    /**
     * @author 2No2Name
     */
    @Overwrite
    public static Inventory getInputBlockInventory(HopperBlockEntity hopperBlockEntity) {
        return ((Interfaces.HopperWithInventoryCache) hopperBlockEntity).getInputInventoryWithCache(hopperBlockEntity);
    }
}
