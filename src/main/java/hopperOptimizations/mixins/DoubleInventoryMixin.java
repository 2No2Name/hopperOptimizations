package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.DoubleInventoryOptimizer;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Feature("optimizedInventories")
@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements OptimizedInventory {
    @Shadow
    @Final
    private Inventory first;
    @Shadow
    @Final
    private Inventory second;
    //Invalidate this DoubleInventory when one half is invalidated.
    //This wasn't necessary in vanilla, because the DoubleInventory object was recreated every time the doublechest was accessed.
    private int firstInvalidCount;
    private int secondInvalidCount;
    private boolean invalid; //If true, this inventory will not be cached and will not be reused from a cache.

    private DoubleInventoryOptimizer optimizer; //Make sure this is only used when both of its halfs have optimizers


    @Inject(method = "<init>(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;)V", at = @At(value = "RETURN"))
    private void initValidityCheck(Inventory inventory_1, Inventory inventory_2, CallbackInfo ci) {
        if (!Settings.optimizedInventories) {
            invalid = true;
            return;
        }
        if (inventory_1 == inventory_2) {
            invalid = true;
            return;
        }

        if (!(inventory_1 instanceof ChestBlockEntity) || !(inventory_2 instanceof ChestBlockEntity) ||
                !(inventory_1 instanceof OptimizedInventory) || !(inventory_2 instanceof OptimizedInventory)) {
            invalid = true;
            return;
        }
        firstInvalidCount = ((OptimizedInventory) inventory_1).getInvalidCount();
        secondInvalidCount = ((OptimizedInventory) inventory_2).getInvalidCount();
        invalid = (firstInvalidCount == -1 || secondInvalidCount == -1);
    }

    /*public void setInvalid(){
        this.invalid = true;
    }*/

    private DoubleInventoryOptimizer getCreateOrRemoveOptimizer() {
        if (!Settings.optimizedInventories) { //Remove first's and second's optimizers
            this.invalidateOptimizer();
            return this.optimizer;
        }

        if (this.optimizer == null) {
            if (((OptimizedInventory) first).getOptimizer() == null || ((OptimizedInventory) second).getOptimizer() == null) {
                System.out.println("Bad initialisation of OptimizedInventory's stacklist! Skipping optmizations!");
                return null;
            }
            this.optimizer = new DoubleInventoryOptimizer((OptimizedInventory) first, (OptimizedInventory) second);
        } else if (this.optimizer.isInvalid()) {
            this.invalidateOptimizer();
        }
        return this.optimizer;
    }

    @Override
    @Nullable
    public InventoryOptimizer getOptimizer() {
        return !(this instanceof SidedInventory) && Settings.optimizedInventories && mayHaveOptimizer() ? getCreateOrRemoveOptimizer() : null;
    }

    @Override
    public void invalidateOptimizer() {
        if (this.first == null) {
            System.out.println("Double Inventory with empty first half!");
        } else if (this.first instanceof OptimizedInventory) {
            ((OptimizedInventory) this.first).invalidateOptimizer();
        }
        if (this.second == null) {
            System.out.println("Double Inventory with empty second half!");
        } else if (this.second instanceof OptimizedInventory) {
            ((OptimizedInventory) this.second).invalidateOptimizer();
        }
        if (this.optimizer != null)
            this.optimizer.setInvalid();
        this.optimizer = null;
    }

    @Override
    public boolean mayHaveOptimizer() {
        return this.first instanceof OptimizedInventory && ((OptimizedInventory) this.first).mayHaveOptimizer()
                && this.second instanceof OptimizedInventory && ((OptimizedInventory) this.second).mayHaveOptimizer();
    }

    //This doesn't get called on the cached object, because opening an inventory creates a new Double Inventory Object.
    /*@Inject(method = "onInvOpen(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "HEAD"))
    private void onInventoryOpened(PlayerEntity playerEntity_1, CallbackInfo ci) {
        if (!playerEntity_1.isSpectator())
            invalidateOptimizer();
    }*/


    //Allows caching the inventory safely
    public boolean isStillValid() {
        return !this.invalid && !(this.invalid = firstInvalidCount != ((OptimizedInventory) first).getInvalidCount()) &&
                !(this.invalid = secondInvalidCount != ((OptimizedInventory) second).getInvalidCount());
    }

}
