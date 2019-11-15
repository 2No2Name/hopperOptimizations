package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.block.Block;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointerImpl;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Feature("dispensersPlaceBlocks")
@Mixin(DropperBlock.class)
public abstract class DropperBlockMixin extends DispenserBlock {
    public DropperBlockMixin(Block.Settings block$Settings_1) {
        super(block$Settings_1);
    }

    @Feature("optimizedInventories")
    @Inject(method = "dispense", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/dispenser/DispenserBehavior;dispense(Lnet/minecraft/util/math/BlockPointer;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private void notifyInventoryDecr1(World world_1, BlockPos blockPos_1, CallbackInfo ci, BlockPointerImpl blockPointerImpl_1, DispenserBlockEntity dispenserBlockEntity_1, int int_1, ItemStack itemStack_1, Direction direction_1, Inventory inventory_1) {
        if (hopperOptimizations.settings.Settings.optimizedInventories && dispenserBlockEntity_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) dispenserBlockEntity_1).getOptimizer();
            if (opt != null) opt.onItemStackCountChanged(int_1, -1);
        }
    }
}
