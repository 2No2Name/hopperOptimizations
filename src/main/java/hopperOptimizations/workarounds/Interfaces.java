package hopperOptimizations.workarounds;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;

public class Interfaces {

    public interface BlockEntityInterface {
        int getRemovedCount();
    }

    public interface WorldInterface {
        BlockEntity getExistingBlockEntity(BlockPos pos);
    }

    public interface HopperWithInventoryCache {
        Inventory getOutputInventoryWithCache(HopperBlockEntity hopperBlockEntity);

        Inventory getInputInventoryWithCache(HopperBlockEntity hopperBlockEntity);
    }

    public interface HopperWithEntityInventoryCache {
        Inventory getOutputEntityInventoryWithCache(HopperBlockEntity hopperBlockEntity);

        Inventory getInputEntityInventoryWithCache(HopperBlockEntity hopperBlockEntity);
    }
}
