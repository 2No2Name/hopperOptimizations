package hopperOptimizations.mixins.inventory_optimization;

import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements OptimizedInventory {
    @Shadow
    @Final
    public Inventory first;
    @Shadow
    @Final
    public Inventory second;

    private OptimizedStackList stackList;
    private DefaultedList<ItemStack> firstList, secondList;

    @Override
    public OptimizedStackList getOptimizedStackList() {
        if (this.stackList == null) {
            if (!(this.first instanceof OptimizedInventory) || !(this.second instanceof OptimizedInventory)) {
                return null;
            }
            this.initOptimizedStackList();
        } else {
            //if the inventory's stacklist was replaced, discard the OptimizedStackList, which references the outdated object in its fields
            if (this.firstList != ((OptimizedInventory) this.first).getDowngradedStackList() ||
                    this.secondList != ((OptimizedInventory) this.first).getDowngradedStackList()) {
                this.stackList = null;

                this.initOptimizedStackList();
            }
        }
        return this.stackList;
    }

    private void initOptimizedStackList() {
        this.firstList = ((OptimizedInventory) this.first).getDowngradedStackList();
        this.secondList = ((OptimizedInventory) this.second).getDowngradedStackList();
        this.stackList = OptimizedStackList.convertFrom((DoubleInventory) (Object) this);
    }

    @Override
    public DefaultedList<ItemStack> getDowngradedStackList() {
        throw new UnsupportedOperationException("Nested double inventories are cursed. Uninstall and run.");
    }
}
