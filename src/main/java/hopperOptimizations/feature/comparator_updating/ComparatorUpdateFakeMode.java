package hopperOptimizations.feature.comparator_updating;

import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

public abstract class ComparatorUpdateFakeMode {
    public static final ComparatorUpdateFakeMode UNDETERMINED = new ComparatorUpdateFakeMode(0) {
        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {
            if (inventory instanceof SidedInventory) {
                int[] slots = ((SidedInventory) inventory).getAvailableSlots(Direction.DOWN);
                for (int slot : slots) {
                    ItemStack stack = inventory.getStack(slot);
                    ItemStack stackDecr = stack.copy();
                    stackDecr.decrement(1);
                    inventory.setStack(slot, stackDecr);
                    inventory.setStack(slot, stack);

                    if (slot == inventoryScanInclusiveEnd) {
                        break;
                    }
                }
            } else {
                for (int slot = 0; slot < inventory.size() && slot <= inventoryScanInclusiveEnd; slot++) {
                    ItemStack stack = inventory.getStack(slot);
                    ItemStack stackDecr = stack.copy();
                    stackDecr.decrement(1);
                    inventory.setStack(slot, stackDecr);
                    inventory.setStack(slot, stack);
                }
            }
        }
    };
    public static final ComparatorUpdateFakeMode NO_UPDATE = new ComparatorUpdateFakeMode(1) {
        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {

        }
    };
    public static final ComparatorUpdateFakeMode UPDATE = new ComparatorUpdateFakeMode(2) {
        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {
            inventory.markDirty();
        }
    };

    //crazy workaround to send stupid comparator updates to comparators and make the comparators send updates to even more redstone components
    //required for comparator to schedule useless but detectable updates on themselves
    public static final ComparatorUpdateFakeMode DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE = new ComparatorUpdateFakeMode(3) {
        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {
            optForSignalStrength.getSignalStrength();
            optForSignalStrength.decreaseSignalStrength();
            inventory.markDirty();
            optForSignalStrength.increaseSignalStrength();
            inventory.markDirty();
        }
    };
    public static final ComparatorUpdateFakeMode UPDATE_DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE = new ComparatorUpdateFakeMode(4) {
        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {
            inventory.markDirty();
            optForSignalStrength.getSignalStrength();
            optForSignalStrength.decreaseSignalStrength();
            inventory.markDirty();
            optForSignalStrength.increaseSignalStrength();
            inventory.markDirty();
        }
    };


    private final int index;

    private ComparatorUpdateFakeMode(int index) {
        this.index = index;
    }

    public abstract void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd);

    public static class DoubleFakeMode extends ComparatorUpdateFakeMode {
        public static final ComparatorUpdateFakeMode[] DOUBLE_CHEST_MODES;
        public static final int DEFAULT_MODE_COUNT;

        static {
            ComparatorUpdateFakeMode[] defaultModes = {UNDETERMINED, NO_UPDATE, UPDATE, DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE, UPDATE_DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE};
            DEFAULT_MODE_COUNT = defaultModes.length;
            DOUBLE_CHEST_MODES = new ComparatorUpdateFakeMode[DEFAULT_MODE_COUNT * DEFAULT_MODE_COUNT];
            for (ComparatorUpdateFakeMode mode1 : defaultModes) {
                for (ComparatorUpdateFakeMode mode2 : defaultModes) {
                    DOUBLE_CHEST_MODES[mode1.index + DEFAULT_MODE_COUNT * mode2.index] = new DoubleFakeMode(mode1, mode2);
                }
            }
        }


        private final ComparatorUpdateFakeMode first;
        private final ComparatorUpdateFakeMode second;

        public DoubleFakeMode(ComparatorUpdateFakeMode mode1, ComparatorUpdateFakeMode mode2) {
            super(-1);
            this.first = mode1;
            this.second = mode2;
        }

        public static ComparatorUpdateFakeMode of(ComparatorUpdateFakeMode first, ComparatorUpdateFakeMode second) {
            if (first.index >= 0 && second.index >= 0) {
                return DoubleFakeMode.DOUBLE_CHEST_MODES[first.index + DoubleFakeMode.DEFAULT_MODE_COUNT * second.index];
            }
            //only used when some other mod adds nested double inventories
            return new DoubleFakeMode(first, second);
        }

        public boolean is(ComparatorUpdateFakeMode a, ComparatorUpdateFakeMode b) {
            return this.first == a && this.second == b;
        }

        public ComparatorUpdateFakeMode getFirst() {
            return this.first;
        }

        public ComparatorUpdateFakeMode getSecond() {
            return this.second;
        }

        @Override
        public void apply(Inventory inventory, OptimizedStackList optForSignalStrength, int inventoryScanInclusiveEnd) {
            throw new UnsupportedOperationException();
        }
    }
}
