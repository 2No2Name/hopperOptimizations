package hopperOptimizations.utils;

import hopperOptimizations.settings.Settings;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

//import static hopperOptimizations.utils.InventoryOptimizer.printStackTrace;

public class InventoryListOptimized<E> extends DefaultedList<E> {
    private InventoryOptimizer optimizer = null;
    private int sizeOverride = -1; //used for Minecart Inventories to pretend to be small, just like they do

    private InventoryListOptimized() {
        super();
    }

    public InventoryListOptimized(List<E> list_1, @Nullable E object_1) {
        super(list_1, object_1);
    }

    public static <E> DefaultedList<E> of() {
        return new InventoryListOptimized<>();
    }

    public static <E> DefaultedList<E> ofSize(int int_1, E object_1) {
        Validate.notNull(object_1);
        Object[] objects_1 = new Object[int_1];
        Arrays.fill(objects_1, object_1);
        return new InventoryListOptimized<>((List<E>) Arrays.asList(objects_1), object_1);
    }

    public InventoryOptimizer getCreateOrRemoveOptimizer(Inventory inventory) {
        if (!Settings.optimizedInventories) return this.optimizer = null;

        if (this.optimizer == null) {
            this.optimizer = new InventoryOptimizer((InventoryListOptimized<ItemStack>) this, inventory);
        }
        if (this.optimizer.isInvalid()) {
            System.out.println("Invalid Optimizer! BAD");
            this.optimizer = null;
        }
        return this.optimizer;
    }

    public InventoryOptimizer getOrRemoveOptimizer() {
        if (!Settings.optimizedInventories) return this.optimizer = null;
        return optimizer;
    }

    public void invalidateOptimizer() {
        if (this.optimizer != null)
            this.optimizer.setInvalid();
        this.optimizer = null;
    }

    public E set(int int_1, E object_1) {
        E ret = super.set(int_1, object_1);
        if (Settings.optimizedInventories) {
            InventoryOptimizer opt = this.getOrRemoveOptimizer();
            if (opt != null) opt.update(int_1, (ItemStack) ret);
        } else this.optimizer = null;
        return ret;
    }

    /*
    public E get(int int_1) {
        if(printStackTrace)
            new UnsupportedOperationException().printStackTrace();
        Object e = super.get(int_1);
        if(printStackTrace)
            System.out.println(e.toString());
        return (E)e;
    }*/

    public void add(int int_1, E object_1) {
        if (Settings.optimizedInventories)
            throw new UnsupportedOperationException("Won't resize optimized inventory!");
        else
            super.add(int_1, object_1);
    }

    public E remove(int int_1) {
        if (Settings.optimizedInventories)
            throw new UnsupportedOperationException("Won't resize optimized inventory!");
        else
            return super.remove(int_1);
    }

    public void clear() {
        this.invalidateOptimizer(); //idk if this call is neccessary. (clear is usually called when closing the world)
        super.clear();
    }

    public void setSize(int size) {
        sizeOverride = size;
    }

    @Override
    public int size() {
        if (sizeOverride >= 0 && Settings.optimizedInventories)
            return sizeOverride;
        return super.size();
    }
}
