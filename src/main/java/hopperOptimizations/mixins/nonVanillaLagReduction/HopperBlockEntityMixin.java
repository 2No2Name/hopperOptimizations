package hopperOptimizations.mixins.nonVanillaLagReduction;


import hopperOptimizations.settings.Settings;
import net.minecraft.block.Block;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin implements Hopper {
    //Two pixels larger in horizontal directions than the inside of the "bowl" of the hopper. But entities normally don't clip into hoppers
    private static final VoxelShape INPUT_AREA_SHAPE_SIMPLIFIED = Block.createCuboidShape(0.0D, 11.0D, 0.0D, 16.0D, 32.0D, 16.0D);

    @Override
    public VoxelShape getInputAreaShape() {
        if (Settings.simplifiedHopperPickupShape) return INPUT_AREA_SHAPE_SIMPLIFIED;
        return Hopper.INPUT_AREA_SHAPE;
    }
}
