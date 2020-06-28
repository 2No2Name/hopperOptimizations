package hopperOptimizations.mixins;


import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;


@Mixin(StorageMinecartEntity.class)
public abstract class StorageMinecartEntityMixin extends AbstractMinecartEntity implements OptimizedInventory {

    //Redirects and Injects to replace the inventory with an optimized Inventory
    @Shadow
    private final DefaultedList<ItemStack> inventory;

    protected StorageMinecartEntityMixin(EntityType<?> entityType_1, World world_1, int someInt, int someOtherInt) {
        super(entityType_1, world_1);
        throw new AssertionError();
    }

    @Redirect(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        DefaultedList<ItemStack> ret = InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
        ((InventoryListOptimized) ret).setSize(this.size()); //Storage Minecarts pretend to have smaller inventories
        return ret;
    }

    @Redirect(method = "<init>(Lnet/minecraft/entity/EntityType;DDDLnet/minecraft/world/World;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory1(int int_1, Object object_1) {
        DefaultedList<ItemStack> ret = InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
        ((InventoryListOptimized) ret).setSize(this.size()); //Storage Minecarts pretend to have smaller inventories
        return ret;
    }

    @Redirect(method = "readCustomDataFromTag(Lnet/minecraft/nbt/CompoundTag;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        DefaultedList<ItemStack> ret = InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
        ((InventoryListOptimized) ret).setSize(this.size());
        return ret;
    }

    @Nullable
    public InventoryOptimizer getOptimizer(boolean create) {
        return !(this instanceof SidedInventory) && this.world != null && !this.world.isClient && inventory instanceof InventoryListOptimized ? ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this, create) : null;
    }
}
