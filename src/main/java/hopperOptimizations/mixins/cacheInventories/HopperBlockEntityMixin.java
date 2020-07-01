package hopperOptimizations.mixins.cacheInventories;


import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.utils.inventoryOptimizer.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.workarounds.HopperWithClearableCaches;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;


@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends LootableContainerBlockEntity implements IHopper, HopperWithClearableCaches, Interfaces.HopperWithInventoryCache {
    private Direction direction;

    private int this_lastChangeCount_Insert; //last change count of this hopper

    private InventoryOptimizer prevInsert;
    private Inventory prevInsertInventory;
    private int prevInsertRemovedCount;
    private int prevInsertChangeCount;


    private int this_lastChangeCount_Extract; //last change count of this hopper

    private InventoryOptimizer previousExtract;
    private Inventory cachedExtractInventory;
    private int cachedExtractInventoryRemovedCount;
    private int prevExtractChangeCount;

    private boolean previousExtract_causeMarkDirty;

    private boolean cachedExtractInventoryAfterLastBlockUpdate = false;
    private boolean cachedInsertInventoryAfterLastBlockUpdate = false;

    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType) {
        super(blockEntityType);
    }


    @Override
    public void clearInventoryCache() {
        prevInsert = null;
        prevInsertInventory = null;
        previousExtract = null;
        cachedExtractInventory = null;

        cachedExtractInventoryAfterLastBlockUpdate = false;
        cachedInsertInventoryAfterLastBlockUpdate = false;
    }

    /**
     * Gets the cached block entity input inventory.
     * Requires optimizedInventories
     * Note: In vanilla hoppers getBlockState the location, which makes them load chunks in some versions.
     * Note: Call hasCachedBlockInputInventory before to check validity!
     *
     * @return cached output inventory
     */
    private Inventory getCachedBlockInputInventory() {
        return cachedExtractInventory;
    }

    /**
     * Gets the cached block entity output inventory if it is still valid.
     * Requires optimizedInventories
     * Note: In vanilla hoppers getBlockState the location, which makes them load chunks in some versions.
     *
     * @return cached output inventory, if found and valid
     */
    private Inventory getCachedBlockOutputInventory() {
        return this.prevInsertInventory;
    }

    private boolean isInputBlockInventoryCacheValid() {
        if (HopperHelper.inventoryCacheInvalid(cachedExtractInventory, cachedExtractInventoryRemovedCount, !cachedExtractInventoryAfterLastBlockUpdate)) {
            cachedExtractInventory = null;
            previousExtract = null;
            return false;
        }
        return true;
    }

    private boolean hasCachedBlockOutputInventory() {
        if (HopperHelper.inventoryCacheInvalid(prevInsertInventory, prevInsertRemovedCount, !cachedInsertInventoryAfterLastBlockUpdate)) {
            prevInsertInventory = null;
            prevInsert = null;
            return false;
        }
        return true;
    }

    /**
     * Checks whether the last item insert attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param otherOpt InventoryOptimizer of other
     *                 <p>
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Override
    public boolean tryShortcutFailedInsert(InventoryOptimizer thisOpt, InventoryOptimizer otherOpt) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (this_lastChangeCount_Insert != thisChangeCount || otherOpt != prevInsert || prevInsertChangeCount != otherChangeCount) {
            this_lastChangeCount_Insert = thisChangeCount;
            prevInsert = otherOpt;
            prevInsertChangeCount = otherChangeCount;
            return false;
        }
        return true;
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param other Block inventory to be remembered, NO ENTITIES
     */
    private void cacheInputInventoryBlock(Inventory other) {
        assert !(other instanceof Entity);

        this.cachedExtractInventory = other;
        if (other instanceof BlockEntity) {
            this.cachedExtractInventoryRemovedCount = ((Interfaces.BlockEntityInterface) other).getRemovedCount();
        }

        this.cachedExtractInventoryAfterLastBlockUpdate = true;

        if (other instanceof OptimizedInventory) {
            this.previousExtract = ((OptimizedInventory) other).getOptimizer(true);
            this.prevExtractChangeCount = this.previousExtract == null ? 0 : this.previousExtract.getInventoryChangeCount() - 1;
        } else {
            this.previousExtract = null;
            this.prevExtractChangeCount = 0;
        }
    }

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param other Block inventory to be remembered, NO ENTITIES
     */
    private void cacheOutputInventoryBlock(Inventory other) {
        assert !(other instanceof Entity);

        this.prevInsertInventory = other;
        if (other instanceof BlockEntity) {
            this.prevInsertRemovedCount = ((Interfaces.BlockEntityInterface) other).getRemovedCount();
        }
        this.cachedInsertInventoryAfterLastBlockUpdate = true;

        if (other instanceof OptimizedInventory) {
            this.prevInsert = ((OptimizedInventory) other).getOptimizer(true);
            this.prevInsertChangeCount = this.prevInsert == null ? 0 : this.prevInsert.getInventoryChangeCount() - 1;
        } else {
            this.prevInsert = null;
            this.prevInsertChangeCount = 0;
        }
    }

    /**
     * Checks whether the last item extract attempt was with the same inventory as the current one AND
     * since before the last item transfer attempt the hopper's inventory and the other inventory did not change.
     * Requires optimizedInventories.
     *
     * @param thisOpt  InventoryOptimizer of this hopper
     * @param other    Inventory interacted with
     * @param otherOpt InventoryOptimizer of other
     *                 <p>
     *                 Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Override
    public boolean tryShortcutFailedExtract(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (this.this_lastChangeCount_Extract != thisChangeCount || otherOpt != this.previousExtract || this.prevExtractChangeCount != otherChangeCount) {
            this.this_lastChangeCount_Extract = thisChangeCount;
            this.previousExtract = otherOpt;
            this.prevExtractChangeCount = otherChangeCount;
            this.previousExtract_causeMarkDirty = false;
            return false;
        }

        if (this.previousExtract_causeMarkDirty && !Settings.failedTransferNoComparatorUpdates)
            HopperHelper.markDirtyLikeHopperWould(other, otherOpt, null); //failed transfers sometimes cause comparator updates
        return true;
    }

    @Override
    public Inventory getOutputInventoryWithCache(HopperBlockEntity hopper) {
        Inventory inventory;
        if (hasCachedBlockOutputInventory()) {
            inventory = getCachedBlockOutputInventory();
        } else {
            //noinspection ConstantConditions
            inventory = HopperHelper.vanillaGetBlockInventory(this.world, hopper.getPos().offset(this.getDirection()));
            this.cacheOutputInventoryBlock(inventory);
        }

        return inventory;
    }


    @Override
    public Inventory getInputInventoryWithCache(HopperBlockEntity hopperBlockEntity) {
        Inventory inventory;
        World world = hopperBlockEntity.getWorld();
        if (world == null) return null;

        if (this.isInputBlockInventoryCacheValid()) {
            inventory = this.getCachedBlockInputInventory();
        } else {
            inventory = HopperHelper.vanillaGetBlockInventory(world, hopperBlockEntity.getPos().up());
            this.cacheInputInventoryBlock(inventory);
        }
        return inventory;
    }

    @Override
    public void setMarkOtherDirty() {
        this.previousExtract_causeMarkDirty = true;
    }


    @Override
    public void onBlockUpdate() {
        this.cachedExtractInventoryAfterLastBlockUpdate = false;
        this.cachedInsertInventoryAfterLastBlockUpdate = false;
    }

    @Override
    public void resetBlock() {
        super.resetBlock();
        this.direction = null;
    }

    private Direction getDirection() {
        if (this.direction == null) {
            this.direction = this.getCachedState().get(HopperBlock.FACING);
        }
        return this.direction;
    }
}
