package hopperOptimizations.utils;

import hopperOptimizations.features.cacheInventories.IValidInventoryUntilBlockUpdate;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedStackList;
import hopperOptimizations.workarounds.ComparatorUpdateFakeMode;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;

public abstract class HopperHelper {

    /**
     * Gets the block inventory at the given position, exactly like vanilla gets it.
     * Needed because we don't want to search for entity inventories like the vanilla method does.
     *
     * @param world    world we are searching in
     * @param blockPos position of the block inventory
     * @return the block inventory at the given position
     */
    @Nullable
    public static Inventory vanillaGetBlockInventory(World world, BlockPos blockPos) {
        //[VanillaCopy]
        Inventory inventory = null;
        BlockState blockstate = world.getBlockState(blockPos);
        Block block = blockstate.getBlock();
        if (block instanceof InventoryProvider) {
            inventory = ((InventoryProvider) block).getInventory(blockstate, world, blockPos);
        } else if (block.hasBlockEntity()) {
            BlockEntity blockEntity = world.getBlockEntity(blockPos);
            if (blockEntity instanceof Inventory) {
                inventory = (Inventory) blockEntity;
                if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    inventory = ChestBlock.getInventory((ChestBlock) block, blockstate, world, blockPos, true);
                }
            }
        }
        return inventory;
    }

    /**
     * Randomly choose an inventory entity at the position in the given world, like a hopper would do it.
     * Needed because we don't want to search for block inventories like the vanilla method does.
     *
     * @param world the world
     * @param pos   the position the hopper is searching entities at
     * @return the randomly chosen inventory entity at the position
     */
    public static Inventory vanillaGetEntityInventory(World world, BlockPos pos) {
        List<Entity> inventoriesVanilla = HopperHelper.vanillaGetEntityInventories(world, pos);
        if (!inventoriesVanilla.isEmpty())
            return (Inventory) inventoriesVanilla.get(world.random.nextInt(inventoriesVanilla.size()));
        return null;
    }

    /**
     * Get the inventory entities at the position in the given world, like a hopper would.
     *
     * @param world the world
     * @param pos   the position the hopper is searching entities at
     * @return the list of inventory entities at the position
     */
    public static List<Entity> vanillaGetEntityInventories(World world, BlockPos pos) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        return world.getOtherEntities(null, new Box(x, y, z, x + 1D, y + 1D, z + 1D), EntityPredicates.VALID_INVENTORIES);
    }


    /**
     * Transfers one item without any checks.
     * Only call this method when it is known that the item transfer of one item is possible.
     *
     * @param to       target inventory
     * @param toSlot   target slot
     * @param from     original inventory
     * @param fromSlot original slot
     */
    public static void transferOneItem_knownSuccessful(Inventory to, int toSlot, Inventory from, int fromSlot) {
        //assume stack sizes were checked, assume item types were already compared

        ItemStack fromStack = from.getStack(fromSlot);
        ItemStack toStack = to.getStack(toSlot);

        if (toStack.isEmpty()) {
            if (fromStack.getCount() == 1) {
                toStack = fromStack;
                fromStack = ItemStack.EMPTY;
                from.setStack(fromSlot, fromStack);
            } else {
                toStack = fromStack.copy();
                toStack.setCount(1);
                fromStack.decrement(1);
            }
            to.setStack(toSlot, toStack);
        } else {
            fromStack.decrement(1);
            to.getStack(toSlot).increment(1);
        }
    }

    /**
     * Checks whether the given cached Inventory can no longer be used.
     *
     * @param cachedInv                   the inventory in question
     * @param cachedRemovedCount          number of times inventory was removed from the world when it was cached
     * @param hasToCheckForInventoryBlock whether the hopper has received a block update after caching the cachedInventory
     * @return whether the inventory should still be used
     */
    public static boolean inventoryCacheInvalid(Inventory cachedInv, int cachedRemovedCount, boolean hasToCheckForInventoryBlock) {
        if (cachedInv == null) {
            return hasToCheckForInventoryBlock;
        } else if (cachedInv instanceof IValidInventoryUntilBlockUpdate) {
            return hasToCheckForInventoryBlock
                    || !((IValidInventoryUntilBlockUpdate) cachedInv).isValid();
        }

        if (cachedInv instanceof Interfaces.RemovedCounter) { //BlockEntities and DoubleInventories implement RemovedCounter
            if (((Interfaces.RemovedCounter) cachedInv).getRemovedCount() != cachedRemovedCount) {
                return true;
            }
            if (cachedInv instanceof ChestBlockEntity) {
                return ChestType.SINGLE != ((ChestBlockEntity) cachedInv).getCachedState().get(ChestBlock.CHEST_TYPE);
            }
            return false;
        }
        return true; //never cache Entity Inventories, the entity might have moved away or a Blockentity might have been placed
    }

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
