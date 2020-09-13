package hopperOptimizations.mixins.inventory_optimization;

import hopperOptimizations.feature.inventory_optimization.DoubleInventoryHalfStackList;
import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
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
            if (this.firstList != ((OptimizedInventory) this.first).getInventory() ||
                    this.secondList != ((OptimizedInventory) this.second).getInventory()) {
                this.stackList = null;

                this.initOptimizedStackList();
            }
        }
        return this.stackList;
    }

    private void initOptimizedStackList() {
        DefaultedList<ItemStack> firstList = ((OptimizedInventory) this.first).getInventory();
        DefaultedList<ItemStack> secondList = ((OptimizedInventory) this.second).getInventory();
        if (firstList instanceof DoubleInventoryHalfStackList && secondList instanceof DoubleInventoryHalfStackList) {
            if ((this.isEqual(((DoubleInventoryHalfStackList) firstList).parent)) &&
                    (this.isEqual(((DoubleInventoryHalfStackList) secondList).parent))) {
                this.firstList = firstList;
                this.secondList = secondList;
                this.stackList = ((OptimizedInventory) ((DoubleInventoryHalfStackList) firstList).parent).getOptimizedStackList();
                return;
            }
        }

        this.firstList = ((OptimizedInventory) this.first).getDoubleInventoryHalfStackList(this);
        this.secondList = ((OptimizedInventory) this.second).getDoubleInventoryHalfStackList(this);

        this.stackList = OptimizedStackList.convertFrom((DoubleInventory) (Object) this);
    }

    @Override
    public DefaultedList<ItemStack> getDoubleInventoryHalfStackList(Object parent) {
        throw new UnsupportedOperationException("Nested double inventories are cursed. Uninstall and run.");
    }

    private boolean isEqual(DoubleInventory other) {
        return this.first == other.first && this.second == other.second;
    }

    /**
     * @author 2No2Name
     */
    @Override
    @Overwrite
    public boolean isEmpty() {
        OptimizedStackList stackList = this.getOptimizedStackList();
        if (stackList != null) {
            return stackList.isEmpty();
        }
        return this.first.isEmpty() && this.second.isEmpty();
    }

    /**
     * @author 2No2Name
     */
    @Override
    @Overwrite
    public ItemStack getStack(int slot) {
        OptimizedStackList stackList = this.getOptimizedStackList();
        if (stackList != null) {
            return stackList.get(slot);
        }
        return slot >= this.first.size() ? this.second.getStack(slot - this.first.size()) : this.first.getStack(slot);
    }

    /**
     * @author 2No2Name
     */
    @Override
    @Overwrite
    public ItemStack removeStack(int slot, int amount) {
        OptimizedStackList stackList = this.getOptimizedStackList();
        if (stackList != null) {
            ItemStack itemStack = Inventories.splitStack(stackList, slot, amount);
            if (!itemStack.isEmpty()) {
                if (slot >= this.first.size()) {
                    this.second.markDirty();
                } else {
                    this.first.markDirty();
                }
            }
            return itemStack;
        }
        return slot >= this.first.size() ? this.second.removeStack(slot - this.first.size(), amount) : this.first.removeStack(slot, amount);
    }

    /**
     * @author 2No2Name
     */
    @Override
    @Overwrite
    public ItemStack removeStack(int slot) {
        OptimizedStackList stackList = this.getOptimizedStackList();
        if (stackList != null) {
            return Inventories.removeStack(stackList, slot);
        }
        return slot >= this.first.size() ? this.second.removeStack(slot - this.first.size()) : this.first.removeStack(slot);
    }

    /**
     * @author 2No2Name
     */
    @Override
    @Overwrite
    public void setStack(int slot, ItemStack stack) {
        OptimizedStackList stackList = this.getOptimizedStackList();
        if (stackList != null) {
            stackList.set(slot, stack);
            return;
        }
        if (slot >= this.first.size()) {
            this.second.setStack(slot - this.first.size(), stack);
        } else {
            this.first.setStack(slot, stack);
        }

    }
}
