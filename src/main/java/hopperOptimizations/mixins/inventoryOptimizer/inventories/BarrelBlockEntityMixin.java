package hopperOptimizations.mixins.inventoryOptimizer.inventories;

import hopperOptimizations.utils.inventoryOptimizer.InventoryListOptimized;
import hopperOptimizations.utils.inventoryOptimizer.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Arrays;

@Mixin(BarrelBlockEntity.class)
public abstract class BarrelBlockEntityMixin extends LootableContainerBlockEntity implements OptimizedInventory {
    @Shadow
    private DefaultedList<ItemStack> inventory;

    protected BarrelBlockEntityMixin(BlockEntityType<?> beType) {
        super(beType);
    }

    //Redirects and Injects to replace the inventory with an optimized Inventory
    @Redirect(method = "<init>(Lnet/minecraft/block/entity/BlockEntityType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Redirect(method = "fromTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Inject(method = "setInvStackList", at = @At("RETURN"))
    private void onSetStackList(DefaultedList<ItemStack> stackList, CallbackInfo ci) {
        if (!(inventory instanceof InventoryListOptimized))
            inventory = new InventoryListOptimized(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
    }


    @Override
    @Nullable
    public InventoryOptimizer getOptimizer(boolean create) {
        return this.getOptimizer(this.world, this.inventory, create);
    }
}
