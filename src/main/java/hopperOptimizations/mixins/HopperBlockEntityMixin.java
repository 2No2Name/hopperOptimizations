package hopperOptimizations.mixins;

import hopperOptimizations.feature.cache_inventories.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.feature.comparator_updating.ComparatorUpdateFakeMode;
import hopperOptimizations.feature.comparator_updating.MarkDirtyHelper;
import hopperOptimizations.feature.entity_tracking.NearbyHopperInventoriesTracker;
import hopperOptimizations.feature.entity_tracking.NearbyHopperItemsTracker;
import hopperOptimizations.feature.inventory_optimization.OptimizedInventory;
import hopperOptimizations.feature.inventory_optimization.OptimizedStackList;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.workarounds.Cast;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.Iterator;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends LootableContainerBlockEntity implements IHopper, Hopper, OptimizedInventory {
    @Shadow
    private long lastTickTime;

    @Shadow
    private DefaultedList<ItemStack> inventory;

    private Direction direction;


    //information about the inventory last inserted to
    private OptimizedStackList prevInsert;
    private long prevInsertChangeCount;
    private long this_lastChangeCount_Extract; //last change count of this hopper
    private long this_lastChangeCount_Insert; //last change count of this hopper
    //information about the inventory last extracted from
    private OptimizedStackList previousExtract;
    private long prevExtractChangeCount;
    //whether extracing causes markDirty to be called, used when skipping extraction to provide equivalent side effects
    private ComparatorUpdateFakeMode previousMarkDirtyMode;
    //counter of this hopper
    private long this_lastChangeCount_Pickup;
    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //It would be nice to have this in different classes, but invoking methods is only possible with Interfaces then
    //Code for Caching Inventories
    private Inventory prevInsertInventory;
    private int prevInsertRemovedCount;
    private Inventory cachedExtractInventory;
    private int cachedExtractInventoryRemovedCount;
    private boolean cachedExtractInventoryAfterLastBlockUpdate = false;
    private boolean cachedInsertInventoryAfterLastBlockUpdate = false;
    private NearbyHopperInventoriesTracker inputInventoryEntities;
    private NearbyHopperInventoriesTracker outputInventoryEntities;
    private NearbyHopperItemsTracker inputItemEntities;
    private long lastTickTime_used_InputInventoryEntityCache;
    private long lastTickTime_used_OutputInventoryEntityCache;
    //change counter value at the last time the input area was checked
    //counter of the input area
    private int inputItemEntities_changeCount;
    private long lastTickTime_used_ItemEntityCache;

    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType) {
        super(blockEntityType);
    }

    @Shadow
    protected abstract boolean isDisabled();

    @Shadow
    protected abstract void setCooldown(int count);

    @Override
    @Shadow
    public abstract double getHopperZ();

    @Shadow
    protected abstract boolean isFull();

    /**
     * Transfers as much of the given ItemStack stack into the to Inventory as possible.
     * <p>
     * Optimized replacement for the transfer method. Only used rarely, as optimizeExtract and optimizeInsert only use vanilla code as fallback.
     * Shall behave exactly like the vanilla implementation, just using the advantages of optimizedInventories.
     */
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void optimizedTransfer(Inventory from, Inventory to, ItemStack stack, Direction fromDirection, CallbackInfoReturnable<ItemStack> cir) {
        if (to instanceof OptimizedInventory) {
            OptimizedStackList toOpt = ((OptimizedInventory) to).getOptimizedStackList();
            if (toOpt == null) {
                return;
            }
            while (!stack.isEmpty()) {
                int toSlot = toOpt.getInsertSlot(stack, fromDirection);
                if (toSlot < 0) break;

                int count = stack.getCount();
                stack = transfer(from, to, stack, toSlot, fromDirection);
                if (stack.getCount() == count) break;
            }
            cir.setReturnValue(stack);
        }
    }

    /**
     * Inject to replace the extract method (take items from inventory above) with an optimized but equivalent replacement.
     * Uses the vanilla method as fallback for non-optimized Inventories.
     * @param to Hopper or Hopper Minecart that is extracting
     * @param from Inventory the hopper is extracting from
     */
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/util/math/Direction;DOWN:Lnet/minecraft/util/math/Direction;", shift = At.Shift.AFTER), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private static void optimizeExtract(Hopper to, CallbackInfoReturnable<Boolean> cir, Inventory from) {
        if (to instanceof OptimizedInventory && from instanceof OptimizedInventory) {
            OptimizedStackList toOpt = ((OptimizedInventory) to).getOptimizedStackList();
            OptimizedStackList fromOpt;
            if (toOpt == null) {
                return;
            }

            boolean isFull = toOpt.isFull_insertable(null);

            if (isFull) { //full hoppers cannot extract more, but hopper minecarts try anyways
                if (to instanceof HopperMinecartEntity) {
                    fromOpt = ((OptimizedInventory) from).getOptimizedStackList();
                    if (fromOpt == null) {
                        return;
                    }
                    if (fromOpt.isAnyExtractableSlotOccupied()) {
                        MarkDirtyHelper.markDirtyOnHopperInteraction(from, 0, from.size(), false, null);
                    }
                } else {
                    System.out.println("Hopper is full even though it wasn't");
                }
                cir.setReturnValue(false);
                return;
            }

            fromOpt = ((OptimizedInventory) from).getOptimizedStackList();
            if (fromOpt == null) {
                return;
            }
            if (to instanceof IHopper && ((IHopper) to).tryShortcutFailedExtract(toOpt, (OptimizedInventory) from, fromOpt)) {
                cir.setReturnValue(false);
                return;
            }

            int firstOccupiedSlot = fromOpt.getFirstOccupiedSlot_extractable();
            if (firstOccupiedSlot == -1) {
                cir.setReturnValue(false);
                return;
            }

            if (toOpt.hasFreeSlotsInsertable_NonSidedInventory()) {
                //When the hopper has any empty slot, just get the first item from the source
                //fromSlot (= firstOccupiedSlot) already set

                //Find the slot in the hopper, which might be before the first empty slot due to stacking items
                ItemStack stack = from.getStack(firstOccupiedSlot);
                int toSlot = toOpt.getInsertSlot(stack, null);
                //if (toSlot < 0) throw new ThisNeverHappensException(); //empty slot always exists
                ComparatorUpdateFakeMode fakeMode = MarkDirtyHelper.markDirtyOnHopperInteraction(from, 0, firstOccupiedSlot, true, null);
                HopperHelper.transferOneItem_knownSuccessful(to, toSlot, from, firstOccupiedSlot);
                ((IHopper) to).setComparatorUpdateFakeMode(fakeMode);

                cir.setReturnValue(true);
                return;
            } else if (fromOpt.getNumOccupiedSlots() > 5) { //&& !to.isFull
                //When the hopper has no empty slots, try to pull items instead of pushing, because the hopper
                //inventory is small, and therefore the for loop is shorter.

                //Assume that all hopper slots are allowed to be filled by the hopper pulling items
                //(Hopper is not SidedInventory, incompatible with mods that change this)
                //Make sure that the minimal possible fromSlot is chosen (vanilla behavior)
                int firstFromSlot = Integer.MAX_VALUE;
                int correspondingToSlot = 0; //init with 0 to prevent "might not have been initialized" error
                for (int toSlot = 0; firstFromSlot > 0 && toSlot < to.size(); toSlot++) {
                    ItemStack stack = to.getStack(toSlot);
                    if (stack.getMaxCount() > stack.getCount()) {
                        int fromSlot = fromOpt.indexOfInAvailableSlots_extractable_maxIndex(stack, firstFromSlot - 1);
                        if (fromSlot != -1 && (fromSlot < firstFromSlot)) {//Lower fromSlot found, remember corresponding slot
                            firstFromSlot = fromSlot;
                            correspondingToSlot = toSlot;
                        }
                    }
                }
                if (firstFromSlot != Integer.MAX_VALUE) {
                    firstFromSlot = fromOpt.getAvailableSlotsEntry(firstFromSlot, Direction.DOWN);
                    ComparatorUpdateFakeMode fakeMode = MarkDirtyHelper.markDirtyOnHopperInteraction(from, 0, firstFromSlot, true, null);
                    HopperHelper.transferOneItem_knownSuccessful(to, correspondingToSlot, from, firstFromSlot);
                    ((IHopper) to).setComparatorUpdateFakeMode(fakeMode);
                    cir.setReturnValue(true);
                    return;
                }
            } else {
                int i = from instanceof SidedInventory ? 0 : firstOccupiedSlot;
                int[] availableSlots = from instanceof SidedInventory ? ((SidedInventory) from).getAvailableSlots(Direction.DOWN) : null;
                for (; availableSlots != null ? i < availableSlots.length : i < from.size(); i++) {
                    int fromSlot = availableSlots != null ? availableSlots[i] : i;
                    if (fromOpt.cannotExtractFrom(fromSlot)) {
                        continue;
                    }
                    ItemStack stack = from.getStack(fromSlot);
                    int toSlot = toOpt.getInsertSlot(stack, null);
                    if (toSlot == -1) {
                        continue;
                    }
                    ComparatorUpdateFakeMode fakeMode = MarkDirtyHelper.markDirtyOnHopperInteraction(from, 0, fromSlot, true, null);
                    HopperHelper.transferOneItem_knownSuccessful(to, toSlot, from, fromSlot);
                    ((IHopper) to).setComparatorUpdateFakeMode(fakeMode);
                    cir.setReturnValue(true);
                    return;
                }
            }
            //transfer not successful
            ComparatorUpdateFakeMode fakeMode = ComparatorUpdateFakeMode.NO_UPDATE;
            if (fromOpt.isAnyExtractableSlotOccupied()) {
                fakeMode = MarkDirtyHelper.markDirtyOnHopperInteraction(from, 0, from.size(), false, null);
            }
            ((IHopper) to).setComparatorUpdateFakeMode(fakeMode);
            cir.setReturnValue(false);
        }
        if (from instanceof INoExtractInventoryUntilBlockUpdate) {
            ((IHopper) to).setComparatorUpdateFakeMode(ComparatorUpdateFakeMode.NO_UPDATE);
            cir.setReturnValue(false);
        }
        //vanilla fallback when non optimized inventories are used
    }

    /**
     * Set the cooldown of the receiving hopper like in vanilla
     *
     * @param receiver     the receiving hopper
     * @param wasEmpty     whether the hopper was empty before the transfer
     * @param lastTickTime the tick time the transferring hopper was last ticked
     */
    private static void setReceiverCooldown(Inventory receiver, boolean wasEmpty, long lastTickTime) {
        if (wasEmpty && receiver instanceof HopperBlockEntityMixin) {
            HopperBlockEntityMixin receiverHopper = (HopperBlockEntityMixin) receiver;
            if (!receiverHopper.isDisabled()) {
                int orderCooldownCorrection = 0;
                if (receiverHopper.lastTickTime >= lastTickTime) {
                    orderCooldownCorrection = 1;
                }
                receiverHopper.setCooldown(8 - orderCooldownCorrection);
            }
        }
        receiver.markDirty();
    }

    /**
     * Inject to remove a useless iteration over each interacted inventory on every item transfer attempt.
     *
     * @param inventory Inventory that is either empty or not
     * @return false when the value is unused in vanilla, otherwise inventory.isInvEmpty() or optimized equivalent
     */
    @Redirect(require = 0, method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;isEmpty()Z"))
    private static boolean isInvEmptyOpt(Inventory inventory) {
        if (!(inventory instanceof HopperBlockEntity))
            return false; //return anything, value unused

        if (inventory instanceof OptimizedInventory) {
            OptimizedStackList opt = ((OptimizedInventory) inventory).getOptimizedStackList();
            if (opt != null) return opt.isEmpty();
        }

        //vanilla call
        return inventory.isEmpty();
    }

    /**
     * Inject to use the optimizedInventories empty check
     *
     * @param inventory Inventory that is either empty or not
     * @param direction Expected constant Direction.DOWN
     */
    @Inject(require = 0, method = "isInventoryEmpty", at = @At(value = "HEAD"), cancellable = true)
    private static void isInventoryEmptyOpt(Inventory inventory, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof OptimizedInventory && direction == Direction.DOWN) {
            OptimizedStackList opt = ((OptimizedInventory) inventory).getOptimizedStackList();
            if (opt != null) {
                cir.setReturnValue(opt.getFirstOccupiedSlot_extractable() == -1);
            }
        }
    }

    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"))
    private static Inventory getInputInventory_optimized(Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntityMixin))
            return HopperBlockEntity.getInputInventory(hopper); //Hopper Minecarts do not cache Inventories
        //noinspection ConstantConditions
        Inventory ret = Settings.cacheInventories ?
                ((HopperBlockEntityMixin) hopper).getInputInventoryWithCache() :
                HopperHelper.vanillaGetBlockInventory(Cast.toHopperBlockEntity(hopper).getWorld(), Cast.toHopperBlockEntity(hopper).getPos().up());
        if (ret != null)
            return ret;
        return Settings.useEntityTrackerEngine ?
                ((HopperBlockEntityMixin) hopper).getInputEntityInventoryWithCache() :
                HopperHelper.vanillaGetEntityInventory(Cast.toHopperBlockEntity(hopper).getWorld(), Cast.toHopperBlockEntity(hopper).getPos().up());
    }

    @Shadow
    private native static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int index, @Nullable Direction fromDirection);

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;setCooldown(I)V"))
    private void invalidateOldCaches(CallbackInfo ci) {
        if (this.lastTickTime % 300 == 0) {
            long timeLimit = this.lastTickTime - 600;
            //600 ticks is an arbitrary number, probably doesn't matter in practice anyways, when it is a bit bigger than 1 hopper cooldown
            this.clearInputItemEntityCache(timeLimit);
            this.clearInputInventoryEntityCache(timeLimit);
            this.clearOutputInventoryEntityCache(timeLimit);

        }
    }

    @Override
    @Shadow
    public abstract int size();

    @Override
    public void markRemoved() {
        super.markRemoved();
        clearInputItemEntityCache(Long.MAX_VALUE);
        clearInputInventoryEntityCache(Long.MAX_VALUE);
        clearOutputInventoryEntityCache(Long.MAX_VALUE);
        clearInventoryCache();
    }

    @Override
    @Shadow
    public abstract double getHopperX();

    @Override
    @Shadow
    public abstract double getHopperY();

    @Redirect(require = 0, allow = 1, method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isEmpty()Z"))
    private boolean isEmptyOpt(HopperBlockEntity hopperBlockEntity) {
        OptimizedStackList opt = this.getOptimizedStackList();
        if (opt != null) {
            return opt.getFirstOccupiedSlot_extractable() == -1;
        }
        return isEmpty();
    }

    @Inject(method = "isInventoryFull", at = @At(value = "HEAD"), cancellable = true)
    private void isInventoryFullOpt(Inventory inventory_1, Direction direction_1, CallbackInfoReturnable<Boolean> cir) {
        if (inventory_1 instanceof OptimizedInventory) {
            OptimizedStackList opt = ((OptimizedInventory) inventory_1).getOptimizedStackList();
            if (opt != null) {
                cir.setReturnValue(opt.isFull_insertable(direction_1));
            }
        }
    }

    /**
     * Inject to replace item pickup with the optimized version
     *
     * @param hopper the hopper
     */
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputItemEntities(Lnet/minecraft/block/entity/Hopper;)Ljava/util/List;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void optimizeItemPickupMixin(Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!(hopper instanceof HopperBlockEntityMixin)) {
            return; //use vanilla code when optimization is off or this is a hopper minecart
        }

        ((HopperBlockEntityMixin) hopper).optimizeItemPickup(hopper, cir);
    }

    @Redirect(require = 0, allow = 1, method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isFull()Z"))
    private boolean isFullOpt(HopperBlockEntity hopperBlockEntity) {
        OptimizedStackList opt = this.getOptimizedStackList();
        if (opt != null) return opt.isFull_insertable(null);
        return isFull();
    }

    @Inject(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isInventoryFull(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/util/math/Direction;)Z", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void optimizeInsert(CallbackInfoReturnable<Boolean> cir, Inventory to, Direction insertFromDirection) {
        OptimizedStackList toOpt, fromOpt;
        if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizedStackList()) != null) {
            fromOpt = this.getOptimizedStackList();
            if (fromOpt != null && this.tryShortcutFailedInsert(fromOpt, toOpt)) {
                cir.setReturnValue(false);
                return;
            }

            if (toOpt.isFull_insertable(insertFromDirection)) {
                cir.setReturnValue(false);
                return;
            }
            if (fromOpt != null) {
                int firstOccupiedSlot = fromOpt.getFirstOccupiedSlot_extractable();
                if (firstOccupiedSlot == -1) {
                    cir.setReturnValue(false);
                    return;
                }

                //Try to push each item into the destination. As the hopper is usually smaller than the destination
                int invSize = this.size();
                int transferAttempts = fromOpt.getNumOccupiedSlots();
                for (int fromSlot = firstOccupiedSlot; transferAttempts > 0 && fromSlot < invSize; fromSlot++) {
                    ItemStack stack = this.getStack(fromSlot);
                    if (!stack.isEmpty()) {
                        --transferAttempts;
                        int toSlot = toOpt.getInsertSlot(stack, insertFromDirection);
                        if (toSlot < 0) continue;

                        boolean wasEmpty = to.isEmpty();
                        HopperHelper.transferOneItem_knownSuccessful(to, toSlot, this, fromSlot);
                        setReceiverCooldown(to, wasEmpty, this.lastTickTime);
                        to.markDirty();
                        cir.setReturnValue(true);
                        return;
                    }
                }
                cir.setReturnValue(false);
            }
        }
    }

    @Redirect(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory()Lnet/minecraft/inventory/Inventory;"))
    private Inventory getOutputInventoryFromCache(HopperBlockEntity hopper) {
        Direction direction = hopper.getCachedState().get(HopperBlock.FACING);
        //noinspection ConstantConditions
        Inventory ret = Settings.cacheInventories ?
                this.getOutputInventoryWithCache(hopper) :
                HopperHelper.vanillaGetBlockInventory(hopper.getWorld(), hopper.getPos().offset(direction));
        if (ret != null)
            return ret;

        return Settings.useEntityTrackerEngine ?
                this.getOutputEntityInventoryWithCache() :
                HopperHelper.vanillaGetEntityInventory(hopper.getWorld(), hopper.getPos().offset(hopper.getCachedState().get(HopperBlock.FACING)));
    }

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

    public boolean tryShortcutFailedInsert(OptimizedStackList thisOpt, OptimizedStackList otherOpt) {
        long thisChangeCount = thisOpt.getContentChangeCount();
        long otherChangeCount = otherOpt.getContentChangeCount();
        if (this_lastChangeCount_Insert != thisChangeCount || otherOpt != prevInsert || prevInsertChangeCount != otherChangeCount) {
            this_lastChangeCount_Insert = thisChangeCount;
            prevInsert = otherOpt;
            prevInsertChangeCount = otherChangeCount;
            return false;
        }
        return true;
    }

    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //==================================================================================================================
    //entity tracker engine instead of polling entities

    /**
     * Makes this hopper remember the given inventory.
     *
     * @param other Block inventory to be remembered, NO ENTITIES
     */
    private void cacheInputInventoryBlock(Inventory other) {
        assert !(other instanceof Entity);

        this.cachedExtractInventory = other;
        if (other instanceof BlockEntity) {
            this.cachedExtractInventoryRemovedCount = ((Interfaces.RemovedCounter) other).getRemovedCount();
        }

        this.cachedExtractInventoryAfterLastBlockUpdate = true;

        OptimizedStackList otherOpt;
        if (other instanceof OptimizedInventory && null != (otherOpt = ((OptimizedInventory) other).getOptimizedStackList())) {
            this.previousExtract = otherOpt;
            this.prevExtractChangeCount = otherOpt.getContentChangeCount() - 1;
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
            this.prevInsertRemovedCount = ((Interfaces.RemovedCounter) other).getRemovedCount();
        }
        this.cachedInsertInventoryAfterLastBlockUpdate = true;

        OptimizedStackList otherOpt;
        if (other instanceof OptimizedInventory && null != (otherOpt = ((OptimizedInventory) other).getOptimizedStackList())) {
            this.prevInsert = otherOpt;
            this.prevInsertChangeCount = otherOpt.getContentChangeCount() - 1;
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
     * @return Whether the current item transfer attempt is known to fail.
     */
    @Override
    public boolean tryShortcutFailedExtract(OptimizedStackList thisOpt, OptimizedInventory other, OptimizedStackList otherOpt) {
        long thisChangeCount = thisOpt.getContentChangeCount();
        long otherChangeCount = otherOpt.getContentChangeCount();
        if (this.this_lastChangeCount_Extract != thisChangeCount || otherOpt != this.previousExtract || this.prevExtractChangeCount != otherChangeCount) {
            this.this_lastChangeCount_Extract = thisChangeCount;
            this.previousExtract = otherOpt;
            this.prevExtractChangeCount = otherChangeCount;
            this.previousMarkDirtyMode = ComparatorUpdateFakeMode.UNDETERMINED;
            return false;
        }

        this.previousMarkDirtyMode = MarkDirtyHelper.markDirtyOnUnchangedHopperInteraction(other, this.previousMarkDirtyMode, other);
        return true;
    }

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

    //Changing argument type to HopperBlockEntity crashes
    public Inventory getInputInventoryWithCache() {
        Inventory inventory;
        HopperBlockEntity hopperBlockEntity = (HopperBlockEntity) (Object) this;
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
    public void setComparatorUpdateFakeMode(ComparatorUpdateFakeMode fakeMode) {
        this.previousMarkDirtyMode = fakeMode;
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

    public void clearOutputInventoryEntityCache(long timelimit) {
        if (this.lastTickTime_used_OutputInventoryEntityCache <= timelimit && this.outputInventoryEntities != null) {
            outputInventoryEntities.removeFromEntityTracker(this.world);
            outputInventoryEntities = null;
        }
    }

    public void clearInputInventoryEntityCache(long timelimit) {
        if (this.lastTickTime_used_InputInventoryEntityCache <= timelimit && this.inputInventoryEntities != null) {
            this.inputInventoryEntities.removeFromEntityTracker(this.world);
            this.inputInventoryEntities = null;
        }
    }

    public void clearInputItemEntityCache(long timeLimit) {
        if (this.inputItemEntities != null && this.lastTickTime_used_ItemEntityCache < timeLimit) {
            this.inputItemEntities.removeFromEntityTracker(this.world);
            this.inputItemEntities = null;
        }
    }

    public Inventory getOutputEntityInventoryWithCache() {
        if (this.outputInventoryEntities == null) {
            this.outputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class, this.outputBox(), EntityType.CHEST_MINECART.getDimensions());
            this.outputInventoryEntities.registerToEntityTracker(this.world);
        }
        this.lastTickTime_used_OutputInventoryEntityCache = this.lastTickTime;

        //noinspection ConstantConditions
        return this.outputInventoryEntities.getRandomInventoryEntity(this.world.random);
    }

    public Inventory getInputEntityInventoryWithCache() {
        if (this.inputInventoryEntities == null) {
            this.inputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class, this.inputBox(), EntityType.CHEST_MINECART.getDimensions());
            this.inputInventoryEntities.registerToEntityTracker(this.world);
        }
        this.lastTickTime_used_InputInventoryEntityCache = this.lastTickTime;

        //noinspection ConstantConditions
        return this.inputInventoryEntities.getRandomInventoryEntity(this.world.random);
    }

    private Box outputBox() {
        Direction direction = this.getDirection();
        return new Box(this.pos.offset(direction));
    }

    private Box inputBox() {
        return new Box(this.pos.up());
    }

    private void optimizeItemPickup(Object thisCast, CallbackInfoReturnable<Boolean> cir) {
        final OptimizedStackList opt = this.getOptimizedStackList();
        if (opt == null) {
            return; //fallback to vanilla
        }

        //fix the entity cache in case it is not initialized
        if (this.inputItemEntities == null) {
            //keep a set of reachable items to be faster next time
            this.inputItemEntities = new NearbyHopperItemsTracker(this.pos, (Hopper) thisCast);
            this.inputItemEntities.registerToEntityTracker(this.world);
            this.this_lastChangeCount_Pickup = 0;
            this.inputItemEntities_changeCount = 0;
        }
        //item entity cache up to date and valid
        this.lastTickTime_used_ItemEntityCache = this.lastTickTime;

        long tmp1 = opt.getContentChangeCount();
        //todo deal with same counter because not initialized / overflown in both cases
        if (this.this_lastChangeCount_Pickup == tmp1) {
            int inputAreaChangeCount = this.inputItemEntities.getNewEntityCounter();
            if (this.inputItemEntities_changeCount == inputAreaChangeCount) {
                //nothing changed after the last transfer attempt
                //so we don't try to transfer at all
                cir.setReturnValue(false);
                return;
            }
            this.inputItemEntities_changeCount = inputAreaChangeCount;
        }
        this.this_lastChangeCount_Pickup = tmp1;

        Iterator<ItemEntity> itemEntityIterator = this.inputItemEntities.getItemEntityIterator();
        while (itemEntityIterator.hasNext()) {
            ItemEntity itemEntity = itemEntityIterator.next();
            ItemStack itemEntityStack = itemEntity.getStack();
            int receivingSlot;

            while (!itemEntityStack.isEmpty() && 0 <= (receivingSlot = opt.getInsertSlot(itemEntityStack, null))) {
                ItemStack receivingStack = this.inventory.get(receivingSlot);
                if (receivingStack.isEmpty()) {
                    this.inventory.set(receivingSlot, itemEntityStack);
                    itemEntity.setStack(ItemStack.EMPTY);
                    itemEntity.remove();
                    cir.setReturnValue(true);
                    this.markDirty();
                    return;
                } else {
                    int transferCount = receivingStack.getMaxCount() - receivingStack.getCount();
                    final int itemEntityStackCount = itemEntityStack.getCount();
                    if (itemEntityStackCount <= transferCount) {
                        transferCount = itemEntityStackCount;
                        itemEntity.setStack(ItemStack.EMPTY);
                        itemEntity.remove();
                        receivingStack.increment(transferCount);
                        cir.setReturnValue(true);
                        this.markDirty();
                        return;
                    } else {
                        itemEntityStack.decrement(transferCount);
                        receivingStack.increment(transferCount);
                    }
                }
            }
        }
        //return false when nothing was picked up
        //also return false when no item entity was removed, but we still picked up items (like vanilla!)
        cir.setReturnValue(false);
    }
}
