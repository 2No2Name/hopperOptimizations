package hopperOptimizations.mixins;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.world.CollisionView$1") //Spliterator Subclass
public class CollisionView$1Mixin {
//    private boolean notifyHoppers; //every call newly created with false, temporary var to eliminate check in the loop
//    private boolean searchedForHoppers;
//
//    @Feature("optimizedEntityHopperInteraction")
//    @Inject(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE_ASSIGN", shift = At.Shift.AFTER, target = "Lnet/minecraft/world/BlockView;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;"), locals = LocalCapture.CAPTURE_FAILHARD)
//    private void getBlockState_rememberHoppers(Consumer<? super VoxelShape> consumer, CallbackInfoReturnable<Boolean> cir, int i, int j, int k, int l, int m, int n, BlockView blockView, BlockState blockState) {
//        if (!this.notifyHoppers) return;
//        if (!this.searchedForHoppers)
//            this.searchedForHoppers = true;
//        if (blockState.getBlock() == Blocks.HOPPER) {
//            blockView.
//        }
////            EntityHopperInteraction.hopperLocationsToNotify.add(blockPos.toImmutable());
////
////        return blockState;
//    }
//
//    @Redirect(method = "tryAdvance(Ljava/util/function/Consumer;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getBoundingBox()Lnet/minecraft/util/math/Box;", ordinal = 0))
//    private Box isClient_SpaghettiCall(Entity e) {
//        //not have to check this condition on every advance -> only on the first
//        //can only set notify hoppers when entity is not null, which is intended
//        World world = e.getEntityWorld();
//        this.notifyHoppers = Settings.optimizedEntityHopperInteraction && world != null && !world.isClient && EntityHopperInteraction.canInteractWithHopper(e);
//
//        return e.getBoundingBox();
//    }
}
