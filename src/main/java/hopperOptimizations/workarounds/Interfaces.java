package hopperOptimizations.workarounds;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public class Interfaces {

    public interface RemovedCounter {
        int getRemovedCount();

        void increaseRemoveCounter();
    }

    public interface WorldInterface {
        BlockEntity getExistingBlockEntity(BlockPos pos);
    }
}
