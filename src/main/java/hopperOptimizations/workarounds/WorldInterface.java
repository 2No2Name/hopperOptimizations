package hopperOptimizations.workarounds;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public interface WorldInterface {

    BlockEntity getExistingBlockEntity(BlockPos pos);
}
