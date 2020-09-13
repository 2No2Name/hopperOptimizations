package hopperOptimizations.utils;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.AbstractList;

public class ListInventoryPair extends AbstractList<ItemStack> {
    private final Inventory inventory1;
    private final Inventory inventory2;

    public ListInventoryPair(Inventory inventory1, Inventory inventory2) {
        this.inventory1 = inventory1;
        this.inventory2 = inventory2;
    }


    @Override
    @Nonnull
    public ItemStack get(int index) {
        return (index < this.inventory1.size()) ? this.inventory1.getStack(index) : this.inventory2.getStack(index - inventory1.size());
    }

    @Override
    public ItemStack set(int index, ItemStack element) {
        ItemStack prev;
        if ((index < this.inventory1.size())) {
            prev = this.inventory1.getStack(index);
            this.inventory1.setStack(index, element);
        } else {
            prev = this.inventory2.getStack(index - inventory1.size());
            this.inventory2.setStack(index - inventory1.size(), element);
        }
        return prev;
    }

    @Override
    public void add(int value, ItemStack element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ItemStack remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return this.inventory1.size() + this.inventory2.size();
    }


    @Override
    public void clear() {
        this.inventory1.clear();
        this.inventory2.clear();
    }

    @Override
    public boolean isEmpty() {
        return this.inventory1.isEmpty() && this.inventory2.isEmpty();
    }

}
