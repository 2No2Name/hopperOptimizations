package hopperOptimizations.mixins.inventoryCheckOnBlockUpdate;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.workarounds.Fixes;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//@Feature("inventoryCheckOnBlockupdate")
@Mixin(HopperBlock.class)
public class HopperBlockMixin {
    @Inject(method = "neighborUpdate(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/Block;Lnet/minecraft/util/math/BlockPos;Z)V", at = @At(value = "HEAD"))
    private void updateBlockEntity(BlockState myBlockState, World world, BlockPos myPos, Block block, BlockPos neighborPos, boolean moved, CallbackInfo ci) {
        if (!Settings.inventoryCheckOnBlockUpdate) return;
        Direction facing = myBlockState.get(HopperBlock.FACING);
        if (neighborPos.getY() == myPos.getY() + 1 || neighborPos.getX() == myPos.getX() + facing.getOffsetX() && neighborPos.getY() == myPos.getY() + facing.getOffsetY() && neighborPos.getZ() == myPos.getZ() + facing.getOffsetZ()) {
            BlockEntity hopper = ((Interfaces.WorldInterface) world).getExistingBlockEntity(myPos);
            if (hopper instanceof IHopper)
                ((IHopper) hopper).onBlockUpdate();
        }
    }

    @Inject(method = "onBlockAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/HopperBlock;updateEnabled(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;)V", shift = At.Shift.AFTER))
    private void hotfixVanillaUpdateSupression(BlockState state, World world, BlockPos pos, BlockState oldState, boolean moved, CallbackInfo ci) {
        if (Settings.inventoryCheckOnBlockUpdate && world.getBlockState(pos) != state) {
            Fixes.onInventoryBlockChangedWithoutBlockUpdate(world, pos);
        }
    }
}
