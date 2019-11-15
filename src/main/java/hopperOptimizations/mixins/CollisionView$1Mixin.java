package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.EntityHopperInteraction;
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

    private boolean notifyHoppers; //every call newly created with false, temporary var to elimite check in the loop

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"))
    private BlockState getBlockState_rememberHoppers(BlockView blockView, BlockPos var1) {
        BlockState blockState = blockView.getBlockState(var1);
        if (!notifyHoppers) return blockState;

        EntityHopperInteraction.checked = true;
        if (blockState.getBlock() == Blocks.HOPPER)
            EntityHopperInteraction.hopperLocationsToNotify.add(var1.toImmutable());

        return blockState;
    }

    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;", ordinal = 0))
    private Box isClient_SpaghettiCall(Entity entity) {
        World world = entity.getEntityWorld();
        notifyHoppers = Settings.optimizedEntityHopperInteraction && EntityHopperInteraction.rememberHoppers && world != null && !world.isClient;

        return entity.getBoundingBox();
    }
}
