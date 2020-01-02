package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DispenserBlock.class)
public abstract class DispenserBlockMixin {
    @Feature("optimizedInventories")
    @Redirect(method = "dispense", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/DispenserBlockEntity;getInvStack(I)Lnet/minecraft/item/ItemStack;"))
    private ItemStack getItemStackCopy(DispenserBlockEntity dispenserBlockEntity, int slot) {
        //Sometimes dispense behaviors change items, sometimes they don't.
        //When the stack is placed in the inventory, the optimizer is notified of any change.
        //Using a copied stack makes sure that a dispense behavior changing the stack isn't going unnoticed.
        return dispenserBlockEntity.getInvStack(slot).copy();
    }
}
