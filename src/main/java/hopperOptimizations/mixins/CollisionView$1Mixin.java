package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.workarounds.EntityHopperInteraction;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.CollisionView$1") //Spliterator Subclass
public class CollisionView$1Mixin {

    private boolean notifyHoppers; //every call newly created with false, temporary var to eliminate check in the loop

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState getBlockState_rememberHoppers(BlockView blockView, BlockPos blockPos) {
        BlockState blockState = blockView.getBlockState(blockPos);
        if (!notifyHoppers) return blockState;

        EntityHopperInteraction.searchedForHoppers = true;
        if (blockState.getBlock() == Blocks.HOPPER)
            EntityHopperInteraction.hopperLocationsToNotify.add(blockPos.toImmutable());

        return blockState;
    }

    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;", ordinal = 0))
    private Box isClient_SpaghettiCall(Entity entity) {
        //not have to check this condition on every advance -> only on the first
        //can only set notify hoppers when entity is not null, which is intended
        World world = entity.getEntityWorld();
        notifyHoppers = Settings.optimizedEntityHopperInteraction && EntityHopperInteraction.findHoppers && world != null && !world.isClient;

        return entity.getBoundingBox();
    }
}
