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
     * @reason implement optimized variant
     */
    @Overwrite(remap = false)
    public static Inventory getOutputBlockInventory(HopperBlockEntity hopperBlockEntity) {
        return ((Interfaces.HopperWithInventoryCache) hopperBlockEntity).getOutputInventoryWithCache(hopperBlockEntity);
    }

    /**
     * @author 2No2Name
     * @reason implement optimized variant
     */
    @Overwrite(remap = false)
    public static Inventory getInputBlockInventory(Object hopperBlockEntity) {
        return ((Interfaces.HopperWithInventoryCache) hopperBlockEntity).getInputInventoryWithCache(((HopperBlockEntity) hopperBlockEntity));
    }
}
