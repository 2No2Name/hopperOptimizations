package hopperOptimizations.workarounds;

import net.minecraft.block.entity.HopperBlockEntity;

public class Cast {
    public static HopperBlockEntity toHopperBlockEntity(Object hopper) {
        return (HopperBlockEntity) hopper;
    }
}
