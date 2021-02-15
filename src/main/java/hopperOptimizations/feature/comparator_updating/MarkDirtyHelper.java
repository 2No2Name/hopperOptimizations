package hopperOptimizations.feature.comparator_updating;

import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;

public class MarkDirtyHelper {
    public static ComparatorUpdateFakeMode markDirtyOnHopperInteraction(Inventory inventory, int inventoryScanStart, int inventoryScanInclusiveEnd, boolean inventoryChanged, Inventory invForSignalStrength) {
        if (inventoryScanStart > inventoryScanInclusiveEnd) {
            return ComparatorUpdateFakeMode.NO_UPDATE;
        }

        if (invForSignalStrength == null) {
            invForSignalStrength = inventory;
        }

        ComparatorUpdateFakeMode fakeMode;
        if (inventory instanceof DoubleInventory) {
            fakeMode = markDirtyOnHopperInteraction_DoubleInventory((DoubleInventory) inventory, inventoryScanStart, inventoryScanInclusiveEnd, inventoryChanged, invForSignalStrength);
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

            if (OptimizedStackList.SMALL_POWER_OF_TWO_STACKSIZES_ONLY) {
                fakeMode = getDecreaseBehavior(opt, inventory, inventoryScanInclusiveEnd);
            } else {
                fakeMode = ComparatorUpdateFakeMode.UNDETERMINED;
            }
            //Actually do the updates
            fakeMode.apply(inventory, opt, inventoryScanInclusiveEnd);

            if (inventoryChanged) {
                //as vanilla stopped scanning all inventory slots when the transfer was successful, the next unsuccessful transfer will scan all and be different
                fakeMode = ComparatorUpdateFakeMode.UNDETERMINED;
            }
        } else {
            if (inventoryChanged) {
                inventory.markDirty();
                //Even though the inventory got an update, next time it doesn't need an update. Unless the transfer is successful again, which is handled by other code.
            }
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
        return ComparatorUpdateFakeMode.DoubleFakeMode.of(firstFakeMode, secondFakeMode);
    }

    public static ComparatorUpdateFakeMode markDirtyOnUnchangedHopperInteraction(Inventory inventory, ComparatorUpdateFakeMode fakeMode, Inventory invForSignalStrength) {
        if (fakeMode == ComparatorUpdateFakeMode.UNDETERMINED) {
            fakeMode = markDirtyOnHopperInteraction(inventory, 0, inventory.size(), false, invForSignalStrength);
            return fakeMode;
        }
        if (!(fakeMode instanceof ComparatorUpdateFakeMode.DoubleFakeMode)) {
            OptimizedStackList opt = ((OptimizedInventory) invForSignalStrength).getOptimizedStackList();
            fakeMode.apply(inventory, opt, Integer.MAX_VALUE);
        } else {
            if (!(inventory instanceof DoubleInventory)) {
                throw new IllegalStateException("Expected double inventory but got different inventory!");
            }
            ComparatorUpdateFakeMode a = markDirtyOnUnchangedHopperInteraction(((DoubleInventory) inventory).first, ((ComparatorUpdateFakeMode.DoubleFakeMode) fakeMode).getFirst(), inventory);
            ComparatorUpdateFakeMode b = markDirtyOnUnchangedHopperInteraction(((DoubleInventory) inventory).second, ((ComparatorUpdateFakeMode.DoubleFakeMode) fakeMode).getSecond(), inventory);
            if (!((ComparatorUpdateFakeMode.DoubleFakeMode) fakeMode).is(a, b)) {
                fakeMode = ComparatorUpdateFakeMode.DoubleFakeMode.of(a, b);
            }
        }
        return fakeMode;
    }

    private static ComparatorUpdateFakeMode getDecreaseBehavior(OptimizedStackList opt, Inventory inventory, int inventoryScanEndIndex) {
        int offset = 0;
        if (opt.parent instanceof DoubleInventory) {
            if (((DoubleInventory) opt.parent).second == inventory) {
                offset = ((DoubleInventory) opt.parent).first.size();
            } else if (((DoubleInventory) opt.parent).first != inventory) {
                throw new IllegalStateException("Inventory must be a child of the given double inventory!");
            }
        }
        if (opt.isEmpty()) {
            return ComparatorUpdateFakeMode.NO_UPDATE;
        }

        int firstUpdateSlot = Integer.MAX_VALUE;
        int minStackSize = Integer.MAX_VALUE;

        if (inventory instanceof SidedInventory) {
            int[] availableSlots = ((SidedInventory) inventory).getAvailableSlots(Direction.DOWN);
            for (int slotIndex : availableSlots) {
                ItemStack stack;
                if (!opt.isSlotEmpty(slotIndex + offset) && (stack = inventory.getStack(slotIndex)).getMaxCount() < minStackSize &&
                        ((SidedInventory) inventory).canExtract(slotIndex, stack, Direction.DOWN)) {
                    minStackSize = Math.min(minStackSize, stack.getMaxCount());
                    if (firstUpdateSlot == Integer.MAX_VALUE) {
                        firstUpdateSlot = slotIndex;
                    }
                    if (opt.getSignalStrength() != opt.getSignalStrengthSimulateDecrementAt(slotIndex + offset)) {
                        return firstUpdateSlot < slotIndex ?
                                ComparatorUpdateFakeMode.UPDATE_DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE :
                                ComparatorUpdateFakeMode.DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE;
                    }
                }
                if (slotIndex == inventoryScanEndIndex) {
                    break;
                }
            }
        } else {
            for (int slotIndex = 0; slotIndex < inventory.size() && slotIndex <= inventoryScanEndIndex; slotIndex++) {
                ItemStack stack;
                if (!opt.isSlotEmpty(slotIndex + offset) && (stack = inventory.getStack(slotIndex)).getMaxCount() < minStackSize) {
                    minStackSize = Math.min(minStackSize, stack.getMaxCount());
                    if (firstUpdateSlot == Integer.MAX_VALUE) {
                        firstUpdateSlot = slotIndex;
                    }
                    if (opt.getSignalStrength() != opt.getSignalStrengthSimulateDecrementAt(slotIndex + offset)) {
                        return firstUpdateSlot < slotIndex ?
                                ComparatorUpdateFakeMode.UPDATE_DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE :
                                ComparatorUpdateFakeMode.DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE;
                    }
                }
            }

        }
        return firstUpdateSlot == Integer.MAX_VALUE ? ComparatorUpdateFakeMode.NO_UPDATE : ComparatorUpdateFakeMode.UPDATE;
    }
}
