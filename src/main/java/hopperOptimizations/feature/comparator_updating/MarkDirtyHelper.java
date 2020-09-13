package hopperOptimizations.feature.comparator_updating;

import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;

public class MarkDirtyHelper {
    public static ComparatorUpdateFakeMode markDirtyOnHopperInteraction(Inventory inventory, int inventoryScanStart, int inventoryScanExclusiveEnd, boolean inventoryChanged, Inventory invForSignalStrength) {
        if (inventoryScanStart >= inventoryScanExclusiveEnd) {
            return ComparatorUpdateFakeMode.NO_UPDATE;
        }

        if (invForSignalStrength == null) {
            invForSignalStrength = inventory;
        }

        ComparatorUpdateFakeMode fakeMode;
        if (inventory instanceof DoubleInventory) {
            fakeMode = markDirtyOnHopperInteraction_DoubleInventory((DoubleInventory) inventory, inventoryScanStart, inventoryScanExclusiveEnd, inventoryChanged, invForSignalStrength);
        } else if (inventory instanceof LootableContainerBlockEntity && !(inventory instanceof HopperBlockEntity)) {
            //LootableContainerBlockEntity has a special setStack behavior that calls markDirty
            //We need to determine whether setStack was called and whether it was called with a reduced signal strength in vanilla
            OptimizedStackList opt = ((OptimizedInventory) invForSignalStrength).getOptimizedStackList();
            if (opt == null) {
                //this should never happen
                return ComparatorUpdateFakeMode.UNDETERMINED;
            }
            if (!opt.isAnyExtractableSlotOccupied()) {
                return ComparatorUpdateFakeMode.NO_UPDATE;
            }

            int mostHeavyItemIndex = opt.getIndexForMaximumSignalStrengthDecrease(inventoryScanStart, Math.min(inventoryScanExclusiveEnd, inventoryScanStart + inventory.size()));
            if (mostHeavyItemIndex <= -1) {
                fakeMode = ComparatorUpdateFakeMode.UNDETERMINED;
            } else if (!OptimizedStackList.SMALL_POWER_OF_TWO_STACKSIZES_ONLY) {
                ItemStack stack = invForSignalStrength.getStack(mostHeavyItemIndex);
                ItemStack stackDecr = stack.copy();
                stackDecr.decrement(1);
                invForSignalStrength.setStack(mostHeavyItemIndex, stackDecr);
                invForSignalStrength.setStack(mostHeavyItemIndex, stack);
                fakeMode = ComparatorUpdateFakeMode.DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE;
            } else
                //noinspection IfStatementWithIdenticalBranches
                if (opt.getSignalStrengthSimulateDecrementAt(mostHeavyItemIndex) < opt.getSignalStrength()) {
                    fakeMode = ComparatorUpdateFakeMode.DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE;
                    opt.decreaseSignalStrength();
                    inventory.markDirty();
                    opt.increaseSignalStrength();
                    inventory.markDirty();
                } else {
                    fakeMode = ComparatorUpdateFakeMode.UPDATE;
                    inventory.markDirty();
                }

            if (inventoryChanged) {
                //as vanilla stopped scanning all inventory slots when the transfer was successful, the next unsuccessful transfer will scan all and be different
                fakeMode = ComparatorUpdateFakeMode.UNDETERMINED;
            }
        } else if (inventoryChanged) {
            inventory.markDirty();
            //Even though the inventory got an update, next time it doesn't need an update. Unless the transfer is successful again, which is handled by other code.
            fakeMode = ComparatorUpdateFakeMode.NO_UPDATE;
        } else {
            fakeMode = ComparatorUpdateFakeMode.NO_UPDATE;
        }
        return fakeMode;
    }

    public static ComparatorUpdateFakeMode markDirtyOnHopperInteraction_DoubleInventory(DoubleInventory inventory, int inventoryScanStart, int inventoryScanEnd, boolean inventoryChanged, Inventory invForSignalStrength) {
        //Vanilla is ridiculous:
        //If taking out any item of the inventory (half (!) for double inventories) would lead to
        //the signal strength of the whole (!) inventory decreasing:
        //Actually decrease the signal strength, update comparators (around the half), increase it again, update comparators again. (might be repeated, but not detectable)
        //Otherwise: If there is at least one item in the inventory (half) that could have been extracted, update comparators
        ComparatorUpdateFakeMode firstFakeMode = markDirtyOnHopperInteraction(inventory.first, inventoryScanStart, inventoryScanEnd, inventoryChanged, inventory);
        ComparatorUpdateFakeMode secondFakeMode = markDirtyOnHopperInteraction(inventory.second, inventoryScanStart + inventory.first.size(), inventoryScanEnd, inventoryChanged, invForSignalStrength);
        return ComparatorUpdateFakeMode.of(firstFakeMode, secondFakeMode);
    }

    public static ComparatorUpdateFakeMode markDirtyOnUnchangedHopperInteraction(Inventory inventory, ComparatorUpdateFakeMode fakeMode, Inventory invForSignalStrength) {
        if (fakeMode == ComparatorUpdateFakeMode.UNDETERMINED) {
            fakeMode = markDirtyOnHopperInteraction(inventory, 0, inventory.size(), false, inventory);
            return fakeMode;
        }
        if (fakeMode.isSimple()) {
            if (fakeMode == ComparatorUpdateFakeMode.UPDATE) {
                inventory.markDirty();
            } else if (fakeMode == ComparatorUpdateFakeMode.DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE) {
                OptimizedStackList opt = ((OptimizedInventory) invForSignalStrength).getOptimizedStackList();
                if (opt == null) {
                    //this should never happen. At least under reasonable conditions. So just don't send useless updates
                    return fakeMode;
                }
                opt.getSignalStrength();
                opt.decreaseSignalStrength();
                inventory.markDirty();
                opt.increaseSignalStrength();
                inventory.markDirty();
            }
        } else {
            if (!(inventory instanceof DoubleInventory)) {
                throw new IllegalStateException("Expected double inventory but got different inventory!");
            }
            ComparatorUpdateFakeMode a = markDirtyOnUnchangedHopperInteraction(((DoubleInventory) inventory).first, fakeMode.getFirst(), inventory);
            ComparatorUpdateFakeMode b = markDirtyOnUnchangedHopperInteraction(((DoubleInventory) inventory).second, fakeMode.getSecond(), inventory);
            if (!fakeMode.is(a, b)) {
                fakeMode = ComparatorUpdateFakeMode.of(a, b);
            }
        }
        return fakeMode;
    }
}
