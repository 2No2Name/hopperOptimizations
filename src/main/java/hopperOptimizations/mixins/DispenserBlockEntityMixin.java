package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Arrays;

@Feature("optimizedInventories")
@Mixin(DispenserBlockEntity.class)
public abstract class DispenserBlockEntityMixin extends LootableContainerBlockEntity implements OptimizedInventory {

    protected DispenserBlockEntityMixin(BlockEntityType<?> blockEntityType, int someInt, int someOtherInt) {
        super(blockEntityType);
    }

    @Shadow
    private DefaultedList<ItemStack> inventory;


    //Redirects and Injects to replace the inventory with an optimized Inventory
    @Redirect(method = "<init>(Lnet/minecraft/block/entity/BlockEntityType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Inject(method = "setInvStackList", at = @At("RETURN"))
    private void onSetStackList(DefaultedList<ItemStack> stackList, CallbackInfo ci) {
        if (!(inventory instanceof InventoryListOptimized))
            inventory = new InventoryListOptimized(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
    }

    @Redirect(method = "fromTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Nullable
    public InventoryOptimizer getOptimizer() {
        return !(this instanceof SidedInventory) && Settings.optimizedInventories && this.world != null && !this.world.isClient && this.inventory instanceof InventoryListOptimized ? ((InventoryListOptimized) this.inventory).getCreateOrRemoveOptimizer(this) : null;
    }

    @Override
    public void invalidateOptimizer() {
        if (this.inventory instanceof InventoryListOptimized)
            ((InventoryListOptimized) this.inventory).invalidateOptimizer();
    }

}
