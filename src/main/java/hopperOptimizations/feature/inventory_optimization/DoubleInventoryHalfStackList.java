package hopperOptimizations.feature.inventory_optimization;

import net.minecraft.inventory.DoubleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;

public class DoubleInventoryHalfStackList extends DefaultedList<ItemStack> {
    public final DoubleInventory parent;
    public final int indexOffset;

    public DoubleInventoryHalfStackList(List<ItemStack> delegate, ItemStack initialElement, DoubleInventory parent, int indexOffset) {
        super(delegate, initialElement);
        this.parent = parent;
        this.indexOffset = indexOffset;
    }

    public void unregisterStacks() {
        for (int i = 0; i < this.size(); i++) {
            ItemStack itemStack = this.get(i);
            //noinspection ConstantConditions
            ((OptimizedStackList.IItemStackCaller) (Object) itemStack).unregisterFromInventory(null, i + indexOffset);
        }
    }
}
