package hopperOptimizations.utils;

import hopperOptimizations.feature.cache_inventories.IValidInventoryUntilBlockUpdate;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
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

}
