package hopperOptimizations.mixins;

import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements Interfaces.BlockEntityInterface {
    //keep track how often the blockentity was removed from the world, e.g. by movable TE
    //the count can be used by caches to realize the blockentity might have changed position or something else
    private int removedCount;

    @Inject(method = "markRemoved()V", at = @At("HEAD"))
    private void increaseRemovedCount(CallbackInfo ci) {
        if (this.removedCount != -1) ++this.removedCount;
    }

    public int getRemovedCount() {
        return removedCount;
    }
}
