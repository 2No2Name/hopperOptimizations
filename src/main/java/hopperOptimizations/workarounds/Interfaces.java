package hopperOptimizations.workarounds;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class Interfaces {

    public interface BlockEntityInterface {
        int getRemovedCount();
    }

    public interface WorldInterface {

        BlockEntity getExistingBlockEntity(BlockPos pos);
    }
}
