package hopperOptimizations.mixins.cache_inventories.cacheInventoryBlocks;

import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import javax.annotation.Nullable;

@Mixin(World.class)
public abstract class WorldMixin implements Interfaces.WorldInterface {
    @Shadow
    @Final
    public boolean isClient;
    @Shadow
    protected boolean iteratingTickingBlockEntities;
    @Shadow
    @Final
    private Thread thread;

    @Shadow
    @Nullable
    protected abstract BlockEntity getPendingBlockEntity(BlockPos blockPos);

    @Shadow
    public abstract WorldChunk getWorldChunk(BlockPos blockPos);

    @Override
    @Nullable
    public BlockEntity getExistingBlockEntity(BlockPos pos) {
        if (World.isOutOfBuildLimitVertically(pos.getY())) {
            return null;
        } else if (!this.isClient && Thread.currentThread() != this.thread) {
            return null;
        } else {
            BlockEntity blockEntity = null;
            if (this.iteratingTickingBlockEntities) {
                blockEntity = this.getPendingBlockEntity(pos);
            }

            if (blockEntity == null) {
                blockEntity = this.getWorldChunk(pos).getBlockEntity(pos, WorldChunk.CreationType.CHECK);
            }

            if (blockEntity == null) {
                blockEntity = this.getPendingBlockEntity(pos);
            }

            return blockEntity;
        }
    }

}
