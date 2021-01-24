package hopperOptimizations.mixins;

import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(World.class)
public abstract class WorldMixin {
    @Shadow
    @Final
    protected List<BlockEntity> unloadedBlockEntities;

    @Inject(method = "tickBlockEntities",
            at = @At(value = "INVOKE", target = "Ljava/util/List;removeAll(Ljava/util/Collection;)Z", ordinal = 0))
    private void removeHoppersFromTrackerEngine(CallbackInfo ci) {
        for (BlockEntity be : this.unloadedBlockEntities) {
            if (be instanceof HopperBlockEntity) {
                be.markRemoved(); //invalidate caches of hoppers
            }
            if (be instanceof Interfaces.RemovedCounter) {
                ((Interfaces.RemovedCounter) be).increaseRemoveCounter(); //invalidate the blockentity being cached by hoppers
            }
        }
    }
}
