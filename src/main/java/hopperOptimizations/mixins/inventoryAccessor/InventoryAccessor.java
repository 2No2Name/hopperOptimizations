package hopperOptimizations.mixins.inventoryAccessor;

import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.block.entity.*;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;


public class InventoryAccessor {
    @Mixin(AbstractFurnaceBlockEntity.class)
    public abstract static class InventoryAccessorAbstractFurnaceBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(BarrelBlockEntity.class)
    public abstract static class InventoryAccessorBarrelBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(BrewingStandBlockEntity.class)
    public abstract static class InventoryAccessorBrewingStandBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(ChestBlockEntity.class)
    public abstract static class InventoryAccessorChestBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(DispenserBlockEntity.class)
    public abstract static class InventoryAccessorDispenserBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(HopperBlockEntity.class)
    public abstract static class InventoryAccessorHopperBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(ShulkerBoxBlockEntity.class)
    public abstract static class InventoryAccessorShulkerBoxBlockEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }

    @Mixin(StorageMinecartEntity.class)
    public abstract static class InventoryAccessorStorageMinecartEntity implements OptimizedInventory {
        @Override
        @Accessor
        public abstract DefaultedList<ItemStack> getInventory();

        @Override
        @Accessor
        public abstract void setInventory(DefaultedList<ItemStack> inventory);
    }
}