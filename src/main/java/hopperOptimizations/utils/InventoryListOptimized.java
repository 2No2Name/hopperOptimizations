package hopperOptimizations.utils;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class InventoryListOptimized extends DefaultedList<ItemStack> {
    private InventoryOptimizer optimizer = null;
    private int sizeOverride = -1; //used for Minecart Inventories to pretend to be small, just like they do

    private InventoryListOptimized() {
        super();
    }

    public InventoryListOptimized(List<ItemStack> list_1, @Nullable ItemStack object_1) {
        super(list_1, object_1);
    }

    public static DefaultedList<ItemStack> of() {
        return new InventoryListOptimized();
    }

    public static DefaultedList<ItemStack> ofSize(int int_1, ItemStack object_1) {
        Validate.notNull(object_1);
        ItemStack[] objects_1 = new ItemStack[int_1];
        Arrays.fill(objects_1, object_1);
        return new InventoryListOptimized(Arrays.asList(objects_1), object_1);
    }

    public InventoryOptimizer getCreateOrRemoveOptimizer(Inventory inventory, boolean create) {
        if (this.optimizer == null) {
            if (!create || inventory.size() > InventoryOptimizer.MAX_INV_SIZE) {
                return null;
            }
            this.optimizer = new InventoryOptimizer(this, inventory);
        }
        return this.optimizer;
    }

    @Override
    public ItemStack set(int slotIndex, ItemStack newStack) {
        InventoryOptimizer opt = this.getCreateOrRemoveOptimizer(null, false);
        if (opt != null) opt.onStackChange(slotIndex, newStack, -1);

        ItemStack prevStack = super.set(slotIndex, newStack);
        if (prevStack != newStack) {
            //noinspection ConstantConditions
            ((IItemStackCaller) (Object) prevStack).removeFromInventory(this, slotIndex);
            //noinspection ConstantConditions
            ((IItemStackCaller) (Object) newStack).setInInventory(this, slotIndex);
        }
        return prevStack;
    }

    /*
    //Debug code to see who causes trouble with item stacks. Item stacks are sadly mutable, so tracking them is hell.
    public E get(int int_1) {
        if(printStackTrace)
            new UnsupportedOperationException().printStackTrace();
        Object e = super.get(int_1);
        if(printStackTrace)
            System.out.println(e.toString());
        return (E)e;
    }
    */

    @Override
    public void add(int int_1, ItemStack itemStack) {
        throw new UnsupportedOperationException("Won't resize optimized inventory!");
    }

    @Override
    public ItemStack remove(int int_1) {
        throw new UnsupportedOperationException("Won't resize optimized inventory!");
    }

    @Override
    public void clear() {
        this.optimizer = null;
        super.clear();
    }

    public void setSize(int size) {
        sizeOverride = size;
    }

    @Override
    public int size() {
        if (sizeOverride >= 0)
            return sizeOverride;
        return super.size();
    }

    public void removeOptimizer(InventoryOptimizer inventoryOptimizer) {
        if (inventoryOptimizer == this.optimizer)
            this.optimizer = null;
    }

    public boolean optimizerIs(InventoryOptimizer inventoryOptimizer) {
        return this.optimizer == inventoryOptimizer;
    }

    public void itemStackChangesCount(ItemStack itemStack, int slotIndex, int oldCount, int newCount) {
        if (this.optimizer != null) {
            this.optimizer.onItemStackCountChange(itemStack, slotIndex, oldCount, newCount);
        }
    }

    public interface IItemStackCaller {
        void setInInventory(InventoryListOptimized myInventoryList, int slotIndex);

        void removeFromInventory(InventoryListOptimized myInventoryList, int slotIndex);
    }
}
