package hopperOptimizations.workarounds;

import hopperOptimizations.utils.IHopper;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class Fixes {

    public static final Direction[] directions = Direction.values();

    public static void onInventoryBlockChangedWithoutBlockUpdate(World world, BlockPos pos) {
        for (Direction direction : directions) {
            BlockEntity hopper = ((Interfaces.WorldInterface) world).getExistingBlockEntity(pos.offset(direction));
            if (hopper instanceof IHopper) {
                ((IHopper) hopper).onBlockUpdate();
            }
        }
    }
}
