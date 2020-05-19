package hopperOptimizations.utils;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.entitycache.NearbyHopperInventoriesTracker;
import hopperOptimizations.workarounds.BlockEntityInterface;
import hopperOptimizations.workarounds.IValidInventoryUntilBlockUpdate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public abstract class HopperHelper {

    /**
     * Picks up items exactly like vanilla does.
     *
     * @param hopper       the hopper that is picking up items
     * @param itemEntities the list of itemEntities to pick from
     * @return the itemEntity that was picked from
     */
    @Nullable
    public static ItemEntity vanillaPickupItem(Hopper hopper, Iterator<ItemEntity> itemEntities) {
        ItemEntity itemEntity;
        while (itemEntities.hasNext()) {
            itemEntity = itemEntities.next();
            if (HopperBlockEntity.extract(hopper, itemEntity))
                return itemEntity;
        }
        return null;
    }

    /**
     * Gets the block inventory at the given position, exactly like vanilla gets it.
     *
     * @param world_1    world we are searching in
     * @param blockPos_1 position of the block inventory
     * @return the block inventory at the given position
     */
    @Nullable
    public static Inventory vanillaGetBlockInventory(World world_1, BlockPos blockPos_1) {
        //code copied from vanilla
        Inventory inventory_1 = null;
        BlockState blockState_1 = world_1.getBlockState(blockPos_1);
        Block block_1 = blockState_1.getBlock();
        if (block_1 instanceof InventoryProvider) {
            inventory_1 = ((InventoryProvider) block_1).getInventory(blockState_1, world_1, blockPos_1);
        } else if (block_1.hasBlockEntity()) {
            BlockEntity blockEntity_1 = world_1.getBlockEntity(blockPos_1);
            if (blockEntity_1 instanceof Inventory) {
                inventory_1 = (Inventory) blockEntity_1;
                if (inventory_1 instanceof ChestBlockEntity && block_1 instanceof ChestBlock) {
                    inventory_1 = ChestBlock.getInventory((ChestBlock) block_1, blockState_1, world_1, blockPos_1, true);
                }
            }
        }
        return inventory_1;
    }

    /**
     * Randomly choose an inventory entity at the position in the given world, like a hopper would do it.
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
        return world.getEntities((Entity) null, new Box(x, y, z, x + 1D, y + 1D, z + 1D), EntityPredicates.VALID_INVENTORIES);
    }


    /**
     * Transfers one item without any checks.
     * Only call this method when it is known that the item transfer of one item is possible.
     * This method also updates the InventoryOptimizers accordingly.
     *
     * @param to       target inventory
     * @param toSlot   target slot
     * @param from     original inventory
     * @param fromSlot original slot
     */
    public static void transferOneItem_knownSuccessful(Inventory to, int toSlot, Inventory from, int fromSlot) {
        //assume stack sizes were checked, assume item types were already compared

        ItemStack fromStack = from.getInvStack(fromSlot);
        ItemStack toStack = to.getInvStack(toSlot);

        if (Settings.debugOptimizedInventories) {
            if (!InventoryOptimizer.areItemsAndTagsEqual(fromStack, toStack) && !toStack.isEmpty() || fromStack.isEmpty()) {
                throw new IllegalArgumentException("Item transfer with non matching items");
            } else if (toStack.getCount() >= toStack.getMaxCount()) {
                throw new IllegalArgumentException("Item transfer to already full item stack");
            }
        }

        boolean replacedToStack = false;
        boolean replacedFromStack = false;

        if (toStack.isEmpty()) {
            if (fromStack.getCount() == 1) {
                toStack = fromStack;
                fromStack = ItemStack.EMPTY;
                from.setInvStack(fromSlot, fromStack);
                replacedFromStack = true;
            } else {
                toStack = fromStack.copy();
                toStack.setCount(1);
                fromStack.decrement(1);
            }
            to.setInvStack(toSlot, toStack);
            replacedToStack = true;
        } else {
            to.getInvStack(toSlot).increment(1);
            fromStack.decrement(1);
        }

        //Notify optimizers of change, if neccessary
        if (!replacedFromStack) {
            InventoryOptimizer opt = from instanceof OptimizedInventory ? ((OptimizedInventory) from).getOptimizer(false) : null;
            if (opt != null)
                opt.onItemStackCountChanged(fromSlot, -1);
        }

        if (!replacedToStack) {
            InventoryOptimizer opt = to instanceof OptimizedInventory ? ((OptimizedInventory) to).getOptimizer(false) : null;
            if (opt != null)
                opt.onItemStackCountChanged(toSlot, 1);
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
        if (Settings.inventoryCheckOnBlockUpdate) {
            if (cachedInv == null) {
                return hasToCheckForInventoryBlock;
            } else if (cachedInv instanceof IValidInventoryUntilBlockUpdate) {
                return hasToCheckForInventoryBlock
                        && ((IValidInventoryUntilBlockUpdate) cachedInv).isValid();
            }
        }

        if (cachedInv instanceof BlockEntity) {
            if (((BlockEntityInterface) cachedInv).getRemovedCount() == cachedRemovedCount) {
                if (cachedInv instanceof ChestBlockEntity)
                    return ChestType.SINGLE != ((ChestBlockEntity) cachedInv).getCachedState().get(ChestBlock.CHEST_TYPE);
                return false;
            }
            return true;
        }

        if (cachedInv instanceof DoubleInventory && cachedInv instanceof OptimizedInventory) {
            return !((OptimizedInventory) cachedInv).isStillValid();
        }
        return true; //never cache Entity Inventories, the entity might have moved away or a Blockentity might have been placed
    }

    /**
     * Marks inventories dirty like a hopper would do it.
     *
     * @param inv       inventory in question
     * @param opt       optimizer of the inventory in question
     * @param masterOpt optimizer of the most parent inventory
     */
    public static void markDirtyLikeHopperWould(Inventory inv, InventoryOptimizer opt, InventoryOptimizer masterOpt) {
        //Vanilla is super ridiculous here:
        //If taking out any item of the inventory (half (!) for double inventories) would lead to
        //the signal strength of the whole (!) inventory decreasing:
        //Actually decrease the signal strength, update comparators, increase it again, update comparators again.
        //Otherwise: If there is at least one item in the inventory (half), update comparators

        if (masterOpt == null) masterOpt = opt;
        if (opt instanceof DoubleInventoryOptimizer) {
            //Double Inventories are handled like two seperate inventories in vanilla when sending useless comparator updates
            markDirtyLikeHopperWould(((DoubleInventoryOptimizer) opt).getFirstInventory(), ((DoubleInventoryOptimizer) opt).getFirstOptimizer(), masterOpt);
            markDirtyLikeHopperWould(((DoubleInventoryOptimizer) opt).getSecondInventory(), ((DoubleInventoryOptimizer) opt).getSecondOptimizer(), masterOpt);
            return;
        }
        if (opt.getFirstOccupiedSlot_extractable() == -1)
            return; //empty inventory halfs don't send updates

        boolean fakeSignalStrengthChange = masterOpt.canOneExtractDecreaseSignalStrength(opt);
        if (fakeSignalStrengthChange) {
            //crazy workaround to send stupid comparator updates to comparators and make the comparators send updates to even more redstone components
            //also required for comparator to schedule useless but detectable updates on themselves
            masterOpt.setFakeReducedSignalStrength();
            inv.setInvStack(0, inv.getInvStack(0));
            masterOpt.clearFakeChangedSignalStrength();
        }

        inv.setInvStack(0, inv.getInvStack(0));
    }

    public static void debugCompareInventoryEntities(NearbyHopperInventoriesTracker tracker, World world, double x, double y, double z) {
        try {
            List<Entity> inventoryEntities = tracker.getAllForDebug();
            inventoryEntities.removeIf((Entity inv) -> inv.removed);

            List<Entity> inventoriesVanilla = world.getEntities((Entity) null, new Box(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 0.5D, z + 0.5D), EntityPredicates.VALID_INVENTORIES);
            if (!inventoryEntities.containsAll(inventoriesVanilla)) {
                throw new IllegalStateException("HopperOptimizations did not find inventory entity/entities that vanilla found.");
            }
            if (!inventoriesVanilla.containsAll(inventoryEntities)) {
                throw new IllegalStateException("HopperOptimizations found inventory entity/entities that vanilla did not find.");
            }
            if (inventoriesVanilla.size() != inventoryEntities.size()) {
                throw new IllegalStateException("HopperOptimizations did not find the same number of entities as vanilla."); //duplicate entries!
            }
        } catch (IllegalStateException e) {
            Text text = new LiteralText("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
//            CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
            e.printStackTrace();
        }
    }
}
