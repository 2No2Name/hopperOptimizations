package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.workarounds.EntityHopperInteraction;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.function.Consumer;

@Mixin(targets = "net.minecraft.world.CollisionView$1") //Spliterator Subclass
public class CollisionView$1Mixin {

    private boolean notifyHoppers; //every call newly created with false, temporary var to eliminate check in the loop

    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE_ASSIGN", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"), locals = LocalCapture.PRINT)
    private void getBlockState_rememberHoppers(Consumer<? super VoxelShape> consumer, CallbackInfoReturnable<Boolean> cir, int i, int j, int k, int l, int m, int n, BlockView blockView, BlockState blockState) {

//        BlockState blockState = blockView.getBlockState(blockPos);
//        if (!notifyHoppers) return blockState;
//
//        EntityHopperInteraction.searchedForHoppers = true;
//        if (blockState.getBlock() == Blocks.HOPPER)
//            EntityHopperInteraction.hopperLocationsToNotify.add(blockPos.toImmutable());
//
//        return blockState;
    }

    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;", ordinal = 0))
    private Box isClient_SpaghettiCall(Entity e) {
        //not have to check this condition on every advance -> only on the first
        //can only set notify hoppers when entity is not null, which is intended
        World world = e.getEntityWorld();
        this.notifyHoppers = Settings.optimizedEntityHopperInteraction && world != null && !world.isClient && EntityHopperInteraction.canInteractWithHopper(e);

        return e.getBoundingBox();
    }
}
