package hopperOptimizations.mixins;

import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = {
        //Mixin into BlockEntities that hoppers can interact with. Access the field "inventory"
        "net.minecraft.block.entity.AbstractFurnaceBlockEntity",
        "net.minecraft.block.entity.BarrelBlockEntity",
        "net.minecraft.block.entity.BrewingStandBlockEntity",
        "net.minecraft.block.entity.ChestBlockEntity",
        "net.minecraft.block.entity.DispenserBlockEntity",
        "net.minecraft.block.entity.HopperBlockEntity",
        "net.minecraft.block.entity.ShulkerBoxBlockEntity",
        //Mixin into Entities that hoppers can interact with. Access the field "inventory"
        "net.minecraft.entity.vehicle.StorageMinecartEntity"
})
public interface InventoryAccessor extends OptimizedInventory {

    @Accessor
    DefaultedList<ItemStack> getInventory();

    @Accessor
    void setInventory(DefaultedList<ItemStack> inventory);
}
