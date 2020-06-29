package hopperOptimizations.mixins;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.*;
import hopperOptimizations.utils.entitycache.NearbyHopperInventoriesTracker;
import hopperOptimizations.utils.entitycache.NearbyHopperItemsTracker;
import hopperOptimizations.workarounds.EntityHopperInteraction;
import hopperOptimizations.workarounds.INoExtractInventoryUntilBlockUpdate;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.Block;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends LootableContainerBlockEntity implements OptimizedInventory, IHopper, Hopper {

    //-----------------------------------------------
    //Fields for optimizedHopperPickupShape
    //Two pixels larger in horizontal directions than the inside of the "bowl" of the hopper. But entities normally don't clip into hoppers
    private static final VoxelShape INPUT_AREA_SHAPE_SIMPLIFIED = Block.createCuboidShape(0.0D, 11.0D, 0.0D, 16.0D, 32.0D, 16.0D);
    //-----------------------------------------------
    //Fields for OptimizedEntityHopperInteraction

    //set for item entities that are above the hopper. item entities add themselves to the set when they move, but
    //when they move away or die the hopper has to remove them again
    //the set only exists/is valid when the hopper is not interacting with an inventory instead
    //getting entities from this set instead of the world improves performance by a lot
    //private LinkedHashSet<ItemEntity> inputItemEntities;
    //separate set for entities that have an item type that matches an inventory slot. It becomes invalid when not all hopper slots occupied or hopper slot item types change
    //intended to improve performance when a lot of non-fitting item entities are on top of a hopper
    //private LinkedHashSet<ItemEntity> fittingItems;

    //value of the counter when fittingItems was initialized. a change of the item types means that fitting items is outdated
    //private int lastItemTypeChangeCount; //when this.getOptimizer().itemTypeChanges increments, invalidate reachableFittingItems


    private NearbyHopperItemsTracker inputItemEntities;
    //change counter value at the last time the input area was checked
    //counter of the input area
    private int inputItemEntities_changeCount;
    //counter of this hopper
    private int this_lastChangeCount_Pickup;

    private NearbyHopperInventoriesTracker inputInventoryEntities;
    private NearbyHopperInventoriesTracker outputInventoryEntities;
    //private boolean itemEntityCacheInvalid = true;
    //private boolean inputInventoryEntityCacheInvalid = true;
    //private boolean outputInventoryEntityCacheInvalid = true;
    //used to invalidate caches that are unused for a longer time
    private long lastTickTime_used_ItemEntityCache;
    private long lastTickTime_used_InputInventoryEntityCache;
    private long lastTickTime_used_OutputInventoryEntityCache;


    private int ruleUpdates = -1;
    //-----------------------------------------------
    //-----------------------------------------------
    //Fields for optimizedInventories
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
    //-----------------------------------------------
    //Fields for inventoryCheckOnBlockUpdate
    private boolean cachedExtractInventoryAfterLastBlockUpdate = false;
    private boolean cachedInsertInventoryAfterLastBlockUpdate = false;

    //-----------------------------------------------
    private boolean previousExtract_causeMarkDirty;

    private Direction direction;

    @Shadow
    private long lastTickTime;
    @Shadow
    private DefaultedList<ItemStack> inventory;
    @Shadow
    private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int index, @Nullable Direction fromDirection) {
        throw new AssertionError();
    }

    public HopperBlockEntityMixin() {
        super(BlockEntityType.HOPPER);
    }

    /**
     * Transfers as much of the given ItemStack stack into the to Inventory as possible.
     *
     * Optimized replacement for the transfer method. Only used rarely, as optimizeExtract and optimizeInsert only use vanilla code as fallback.
     * Shall behave exactly like the vanilla implementation, just using the advantages of optimizedInventories.
     *
     */
//@Feature("optimizedInventories")
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void optimizedTransfer(Inventory from, Inventory to, ItemStack stack, Direction fromDirection, CallbackInfoReturnable<ItemStack> cir) {
        if (to instanceof OptimizedInventory) {
            InventoryOptimizer toOpt = ((OptimizedInventory) to).getOptimizer(true);
            if (toOpt == null) return;
            while (!stack.isEmpty()) {
                int toSlot = toOpt.findInsertSlot(stack, fromDirection, to);
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
//@Feature("optimizedInventories")
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/util/math/Direction;DOWN:Lnet/minecraft/util/math/Direction;", shift = At.Shift.AFTER), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private static void optimizeExtract(Hopper to, CallbackInfoReturnable<Boolean> cir, Inventory from) {
        InventoryOptimizer toOpt, fromOpt;
        if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer(true)) != null) {
            boolean isFull = toOpt.isFull_insertable(null);
            if (isFull) { //full hoppers cannot extract more
                if (to instanceof HopperMinecartEntity) {
                    if (!Settings.failedTransferNoComparatorUpdates) {
                        if (!(from instanceof OptimizedInventory)) {
                            return; //vanilla fallback, should never happen
                        }
                        InventoryOptimizer opt = ((OptimizedInventory) from).getOptimizer(true);
                        if (opt == null) {
                            return; //vanilla fallback
                        }
                        HopperHelper.markDirtyLikeHopperWould(from, opt, null);
                    }
                } else {
                    System.out.println("Hopper is full even though it wasn't");
                }
                cir.setReturnValue(false);
                return;
            }
            if (from instanceof OptimizedInventory && (fromOpt = ((OptimizedInventory) from).getOptimizer(true)) != null) {
                if (to instanceof IHopper && ((IHopper) to).tryShortcutFailedExtract(toOpt, from, fromOpt)) {
                    cir.setReturnValue(false);
                    return;
                }

                int firstOccupiedSlot = fromOpt.getFirstOccupiedSlot_extractable();
                if (firstOccupiedSlot == -1) {
                    cir.setReturnValue(false);
                    return;
                }

                if (toOpt.hasFreeSlots_insertable_ignoreSidedInventory()) {
                    //When the hopper has any empty slot, just get the first item from the source
                    //fromSlot (= firstOccupiedSlot) already set

                    //Find the slot in the hopper, which might be before the first empty slot due to stacking items
                    ItemStack stack = from.getStack(firstOccupiedSlot);
                    int toSlot = toOpt.findInsertSlot(stack, null, to);
                    //if (toSlot < 0) throw new ThisNeverHappensException(); //empty slot always exists
                    HopperHelper.transferOneItem_knownSuccessful(to, toSlot, from, firstOccupiedSlot);
                    from.markDirty();
                    cir.setReturnValue(true);
                    return;
                } else { //!isFull
                    //When the hopper has no empty slots, try to pull items instead of pushing, because the hopper
                    //inventory is small, and therefore the for loop is shorter.

                    //Assume that all hopper slots are allowed to be filled by the hopper pulling items
                    //(Hopper is not SidedInventory, incompatible with mods that change this)
                    //Make sure that the minimal possible fromSlot is chosen (vanilla behavior)
                    int firstFromSlot = Integer.MAX_VALUE;
                    int correspondingToSlot = 0; //init with 0 to prevent "might not have been initialized" error
                    for (int toSlot = 0; toSlot < to.size(); toSlot++) {
                        ItemStack stack = to.getStack(toSlot);
                        if (stack.getMaxCount() > stack.getCount()) {
                            int fromSlot = fromOpt.indexOf_extractable_endIndex(stack, firstFromSlot);
                            if (fromSlot != -1 && (fromSlot < firstFromSlot)) {//Lower fromSlot found, remember corresponding slot
                                firstFromSlot = fromSlot;
                                correspondingToSlot = toSlot;
                            }
                        }
                    }
                    if (firstFromSlot != Integer.MAX_VALUE) {
                        HopperHelper.transferOneItem_knownSuccessful(to, correspondingToSlot, from, firstFromSlot);
                        from.markDirty();
                        cir.setReturnValue(true);
                        return;
                    }
                }

                //For Inventory Blocks that calls markDirty on setInvStack, but also implements canExtract behaviors this might be incorrect
                //if (getAvailableSlots(from, Direction.DOWN).anyMatch((int i) -> true)) { //this is true for any optimized inventory, no sided inventories besides shulkerboxes
                if (!Settings.failedTransferNoComparatorUpdates)
                    HopperHelper.markDirtyLikeHopperWould(from, fromOpt, null);
                ((IHopper) to).setMarkOtherDirty();
                //}

                cir.setReturnValue(false);
            }

            if (from instanceof INoExtractInventoryUntilBlockUpdate)
                cir.setReturnValue(false);

            //else use vanilla (with optimized transfer) implementation
        }
    }

    /**
     * Inject to replace item pickup with the optimized version
     *
     * @param hopper the hopper
     */
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputItemEntities(Lnet/minecraft/block/entity/Hopper;)Ljava/util/List;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void optimizeItemPickupMixin(Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!Settings.optimizedEntityHopperInteraction || !(hopper instanceof HopperBlockEntityMixin)) {
            return; //use vanilla code when optimization is off or this is a hopper minecart
        }

        if (Settings.debugOptimizedEntityHopperInteraction) {
            try {
                List<ItemEntity> itemEntities = HopperBlockEntity.getInputItemEntities(hopper);

                ItemEntity pickedUp = ((HopperBlockEntityMixin) hopper).optimizeItemPickup(cir);
                if (pickedUp == null) {
                    pickedUp = HopperHelper.vanillaPickupItem(hopper, itemEntities.iterator());
                    if (pickedUp != null)
                        throw new IllegalStateException("HopperOptimizations picked up no item even though vanilla could pick up: " + pickedUp.toString());
                } else if (!itemEntities.contains(pickedUp))
                    throw new IllegalStateException("HopperOptimizations picked up an item that vanilla couldn't pick up: " + pickedUp.toString());
//
//                if (!itemEntities.containsAll(Arrays.asList(itemEntities2))) {
//                    throw new IllegalStateException("HopperOptimizations found item(s) that vanilla did not find.");
//                }
//                if (!(Arrays.asList(((HopperBlockEntityMixin) hopper).inputItemEntities.getAllForDebug()).containsAll(itemEntities))) {
//                    throw new IllegalStateException("HopperOptimizations did not find item(s) that vanilla found.");
//                }
            } catch (IllegalStateException e) {
                ((HopperBlockEntityMixin) hopper).invalidateEntityHopperInteractionCache();
                Text text = new LiteralText("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
                //CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
                e.printStackTrace();
            }
        } else {
            ((HopperBlockEntityMixin) hopper).optimizeItemPickup(cir);
        }
    }

    //@Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"))
    private static Inventory getInputInventoryFromCache(Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntityMixin))
            return HopperBlockEntity.getInputInventory(hopper); //Hopper Minecarts do not cache Inventories

        Inventory inventory;
        World world = ((HopperBlockEntityMixin) hopper).getWorld();
        if (world == null) return null;

        if (((HopperBlockEntityMixin) hopper).isInputBlockInventoryCacheValid()) {
            inventory = ((HopperBlockEntityMixin) hopper).getCachedBlockInputInventory();
        } else {
            inventory = HopperHelper.vanillaGetBlockInventory(world, ((HopperBlockEntityMixin) hopper).getPos().up());
            ((HopperBlockEntityMixin) hopper).cacheInputInventoryBlock(inventory);
            if (inventory != null && Settings.optimizedEntityHopperInteraction) {
                ((HopperBlockEntityMixin) hopper).clearInputInventoryEntityCache();
                ((HopperBlockEntityMixin) hopper).clearInputItemEntityCache();
            }
        }

        if (inventory != null)
            return inventory;
        //if the inventory is null, we cached that there is no inventory block until the next block update
        //get inventory entities (minecarts)

        if (Settings.optimizedEntityHopperInteraction) { //Use the entity cache to find minecarts
            ((HopperBlockEntityMixin) hopper).invalidateEntityCacheIfNecessary();
            if (((HopperBlockEntityMixin) hopper).inputInventoryEntities == null) {
                ((HopperBlockEntityMixin) hopper).inputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class,
                        ((HopperBlockEntityMixin) hopper).inputBox(), EntityType.CHEST_MINECART.getDimensions());
                ((HopperBlockEntityMixin) hopper).inputInventoryEntities.registerToEntityTracker(((HopperBlockEntityMixin) hopper).world);
            }
            ((HopperBlockEntityMixin) hopper).lastTickTime_used_InputInventoryEntityCache = ((HopperBlockEntityMixin) hopper).lastTickTime;

            if (Settings.debugOptimizedEntityHopperInteraction) {
                HopperHelper.debugCompareInventoryEntities(((HopperBlockEntityMixin) hopper).inputInventoryEntities, world, hopper.getHopperX(), hopper.getHopperY() + 1.0D, hopper.getHopperZ());
            }

            inventory = ((HopperBlockEntityMixin) hopper).inputInventoryEntities.getRandomInventoryEntity(world.random);
            return inventory;
        } else {
            return HopperHelper.vanillaGetEntityInventory(hopper.getWorld(), ((HopperBlockEntityMixin) hopper).pos.up());
        }
    }

    /**
     * Set the cooldown of the receiving hopper like in vanilla
     *
     * @param receiver     the receiving hopper
     * @param wasEmpty     whether the hopper was empty before the transfer
     * @param lastTickTime the tick time the transferring hopper was last ticked
     */
//@Feature("optimizedInventories")
    private static void setReceiverCooldown(Inventory receiver, boolean wasEmpty, long lastTickTime) {
        if (wasEmpty && receiver instanceof HopperBlockEntityMixin) {
            HopperBlockEntityMixin hopperBlockEntity_1 = (HopperBlockEntityMixin) receiver;
            if (!hopperBlockEntity_1.isDisabled()) {
                int int_4 = 0;
                if (hopperBlockEntity_1.lastTickTime >= lastTickTime) {
                    int_4 = 1;
                }
                hopperBlockEntity_1.setCooldown(8 - int_4);
            }
        }
        receiver.markDirty();
    }

    @Shadow
    public abstract double getHopperX();

    @Shadow
    public abstract double getHopperY();

    @Shadow
    public abstract double getHopperZ();

    /**
     * Inject to remove a useless iteration over each interacted inventory on every item transfer attempt.
     *
     * @param inventory Inventory that is either empty or not
     * @return false when the value is unused in vanilla, otherwise inventory.isInvEmpty() or optimized equivalent
     */
//@Feature("optimizedInventories")
    @Redirect(require = 0, method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;isEmpty()Z"))
    private static boolean isInvEmptyOpt(Inventory inventory) {
        if (!(inventory instanceof HopperBlockEntity))
            return false; //return anything, value unused

        if (inventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer(true);
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
//@Feature("optimizedInventories")
    @Inject(require = 0, method = "isInventoryEmpty", at = @At(value = "HEAD"), cancellable = true)
    private static void isInventoryEmptyOpt(Inventory inventory, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (inventory instanceof OptimizedInventory && direction == Direction.DOWN) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer(true);
            if (opt != null) cir.setReturnValue(opt.getFirstOccupiedSlot_extractable() == -1);
        }
    }

    @Shadow
    protected abstract boolean isDisabled();

    @Shadow
    protected abstract void setCooldown(int count);

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;setCooldown(I)V"))
    private void invalidateOldCaches(CallbackInfo ci) {
        if (this.lastTickTime % 300 == 0) {
            long timeLimit = this.lastTickTime - 600;
            //600 ticks is an arbitrary number, probably doesn't matter in practice anyways, when it is a bit bigger than 1 hopper cooldown
            if (this.inputItemEntities != null && this.lastTickTime_used_ItemEntityCache < timeLimit) {
                clearInputItemEntityCache();
            }
            if (this.inputInventoryEntities != null && this.lastTickTime_used_InputInventoryEntityCache < timeLimit) {
                clearInputInventoryEntityCache();
            }
            if (this.outputInventoryEntities != null && this.lastTickTime_used_OutputInventoryEntityCache < timeLimit) {
                clearOutputInventoryEntityCache();
            }
        }
    }

    @Shadow
    protected abstract boolean isFull();

    @Shadow
    public abstract int size();

    private ItemEntity optimizeItemPickup(CallbackInfoReturnable<Boolean> cir) {
        this.invalidateEntityCacheIfNecessary();

        //get the optimizer of this hopper
        final InventoryOptimizer opt = this.getOptimizer(true);
        if (opt == null) {
            return null; //fallback to vanilla
        }

        //fix the entity cache in case it is not initialized
        if (this.inputItemEntities == null) {
            //keep a set of reachable items to be faster next time
            this.inputItemEntities = new NearbyHopperItemsTracker(this.pos, this);
            this.inputItemEntities.registerToEntityTracker(this.world);
            this.this_lastChangeCount_Pickup = 0;
        }  //invalidate the (non) fitting items set when the item types the hopper can pick up changed
        //            if (Settings.optimizedInventories && this.lastItemTypeChangeCount != opt.getItemTypeChanges()) {
        //                this.inputItemEntities.resetNonFittingItems(5);
        //                this.lastItemTypeChangeCount = opt.getItemTypeChanges();
        //            }

        //item entity cache up to date and valid
        this.lastTickTime_used_ItemEntityCache = this.lastTickTime;

        int tmp1 = opt.getInventoryChangeCount();
        //todo deal with same counter because not initialized / overflown in both cases
        if (this.this_lastChangeCount_Pickup == tmp1) {
            int tmp2 = this.inputItemEntities.getNewEntityCounter();
            if (this.inputItemEntities_changeCount == tmp2) {
                //nothing changed after the last transfer attempt
                //so we don't try to transfer at all
                cir.setReturnValue(false);
                return null;
            }
            this.inputItemEntities_changeCount = tmp2;
        }
        this.this_lastChangeCount_Pickup = tmp1;

        Iterator<ItemEntity> itemEntityIterator = this.inputItemEntities.getItemEntityIterator();
        while (itemEntityIterator.hasNext()) {
            ItemEntity itemEntity = itemEntityIterator.next();
            ItemStack itemEntityStack = itemEntity.getStack();
            int receivingSlot;

            while (!itemEntityStack.isEmpty() && 0 <= (receivingSlot = opt.findInsertSlot(itemEntityStack, null, this))) {
                ItemStack receivingStack = this.inventory.get(receivingSlot);
                if (receivingStack.isEmpty()) {
                    this.inventory.set(receivingSlot, itemEntityStack);
                    itemEntity.setStack(ItemStack.EMPTY);
                    itemEntity.remove();
                    cir.setReturnValue(true);
                    this.markDirty();
                    return itemEntity;
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
                        return itemEntity;
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
        return null;
    }

    //@Feature("optimizedHopperPickupShape")
    @Override
    public VoxelShape getInputAreaShape() {
        if (Settings.simplifiedHopperPickupShape) return INPUT_AREA_SHAPE_SIMPLIFIED;
        return INPUT_AREA_SHAPE;
    }

    //@Feature("optimizedInventories")
    private void invalidateOptimizedInventoryCache() {
        prevInsert = null;
        prevInsertInventory = null;
        previousExtract = null;
        cachedExtractInventory = null;
        //this.invalidateOptimizer(); //todo why is this necessary, probably isn't.
    }

    /**
     * Gets the cached block entity input inventory.
     * Requires optimizedInventories
     * Note: In vanilla hoppers getBlockState the location, which makes them load chunks in some versions.
     * Note: Call hasCachedBlockInputInventory before to check validity!
     *
     * @return cached output inventory
     */
//@Feature("optimizedInventories")
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
//@Feature("optimizedInventories")
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
//@Feature("optimizedInventories")
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
     * @param thisOpt InventoryOptimizer of this hopper
     * @param other Inventory interacted with
     * @param otherOpt InventoryOptimizer of other
     *
     * Side effect: Sends comparator updates that would be sent on normal failed transfers.
     * @return Whether the current item transfer attempt is known to fail.
     */
//@Feature("optimizedInventories")
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

    //@Feature("optimizedInventories")
    @Redirect(require = 0, allow = 1, method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isEmpty()Z"))
    private boolean isEmptyOpt(HopperBlockEntity hopperBlockEntity) {
        InventoryOptimizer opt = this.getOptimizer(true);
        if (opt != null) return opt.getFirstOccupiedSlot_extractable() == -1;
        return isEmpty();
    }

    //@Feature("optimizedInventories")
    @Redirect(require = 0, allow = 1, method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isFull()Z"))
    private boolean isFullOpt(HopperBlockEntity hopperBlockEntity) {
        InventoryOptimizer opt = this.getOptimizer(true);
        if (opt != null) return opt.isFull_insertable(null);
        return isFull();
    }

    //@Feature("optimizedInventories")
    @Override
    public void setMarkOtherDirty() {
        this.previousExtract_causeMarkDirty = true;
    }

    //@Feature("optimizedInventories")
    @Inject(method = "isInventoryFull", at = @At(value = "HEAD"), cancellable = true)
    private void isInventoryFullOpt(Inventory inventory_1, Direction direction_1, CallbackInfoReturnable<Boolean> cir) {
        if (inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer(true);
            if (opt != null) cir.setReturnValue(opt.isFull_insertable(direction_1));
        }
    }

    //@Feature("optimizedInventories")
    @Inject(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isInventoryFull(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/util/math/Direction;)Z", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void optimizeInsert(CallbackInfoReturnable<Boolean> cir, Inventory to, Direction insertFromDirection) {
        InventoryOptimizer toOpt, fromOpt;
        if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer(true)) != null) {
            fromOpt = ((OptimizedInventory) this).getOptimizer(true);
            if (fromOpt != null && ((IHopper) this).tryShortcutFailedInsert(fromOpt, toOpt)) {
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
                int transferAttempts = fromOpt.getOccupiedSlots();
                for (int fromSlot = firstOccupiedSlot; transferAttempts > 0 && fromSlot < invSize; fromSlot++) {
                    ItemStack stack = this.getStack(fromSlot);
                    if (!stack.isEmpty()) {
                        --transferAttempts;
                        int toSlot = toOpt.findInsertSlot(stack, insertFromDirection, to);
                        if (toSlot < 0) continue;

                        boolean wasEmpty = toOpt.getFirstOccupiedSlot_extractable() == -1;
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

    //@Feature("optimizedInventories")
    @Redirect(method = "<init>()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    //@Feature("optimizedInventories")
    @Inject(method = "setInvStackList", at = @At("RETURN"))
    private void onSetStackList(DefaultedList<ItemStack> stackList, CallbackInfo ci) {
        if (!(inventory instanceof InventoryListOptimized))
            inventory = new InventoryListOptimized(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
    }

    //@Feature("optimizedInventories")
    @Redirect(method = "fromTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/collection/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/collection/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Override
    @Nullable
    public InventoryOptimizer getOptimizer(boolean create) {
        return !(this instanceof SidedInventory) && this.world != null && !this.world.isClient && this.inventory instanceof InventoryListOptimized ? ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this, create) : null;
    }

    private Box inputBox() {
        return new Box(this.pos.up());
    }

    private Box outputBox() {
        Direction direction_1 = this.getDirection();
        return new Box(this.pos.offset(direction_1));
    }

    //@Feature("optimizedEntityHopperInteraction")
    public void notifyOfNearbyEntity(Entity entity) {
//        invalidateOldUnusedCaches(); //todo move this somewhere else... e.g. start of ticking
//
//        if (this.inputItemEntities != null && entity instanceof ItemEntity && !inputItemEntities.contains(entity)) {
//            inputItemEntities.add((ItemEntity) entity);
//            if (fittingItems != null && Settings.optimizedInventories) {
//                InventoryOptimizer opt = this.getOptimizer();
//                if (opt == null) clearInputItemEntityCache();
//                else if (-1 != opt.indexOf(((ItemEntity) entity).getStack()))
//                    fittingItems.add((ItemEntity) entity);
//            }
//        }
//        if (!inputInventoryEntityCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(inputBox()) && !inputInventoryEntities.contains(entity))
//            inputInventoryEntities.add(entity);
//        if (!outputInventoryEntityCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(outputBox()) && !outputInventoryEntities.contains(entity))
//            outputInventoryEntities.add(entity);
    }

    //@Feature("optimizedEntityHopperInteraction")
    @Inject(method = "onEntityCollided", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;insertAndExtract(Ljava/util/function/Supplier;)Z", shift = At.Shift.BEFORE))
    private void onEntityCollided1(Entity entity_1, CallbackInfo ci) {
        this.notifyOfNearbyEntity(entity_1);
    }

    //@Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory()Lnet/minecraft/inventory/Inventory;"))
    private Inventory getOutputInventoryFromCache(HopperBlockEntity hopper) {
        Inventory inventory;
        assert this.world != null;

        if (hasCachedBlockOutputInventory()) {
            inventory = getCachedBlockOutputInventory();
        } else {
            inventory = HopperHelper.vanillaGetBlockInventory(this.world, hopper.getPos().offset(this.getDirection()));
            this.cacheOutputInventoryBlock(inventory);
            if (inventory != null && Settings.optimizedEntityHopperInteraction) {
                this.clearOutputInventoryEntityCache();
            }
        }

        if (inventory != null)
            return inventory;
        //if the inventory is null, we cached that there is no inventory block until the next block update
        //get inventory entities (minecarts)

        if (!Settings.optimizedEntityHopperInteraction)
            return HopperHelper.vanillaGetEntityInventory(this.world, hopper.getPos().offset(this.getDirection()));

        this.invalidateEntityCacheIfNecessary();
        if (this.outputInventoryEntities == null) {
            this.outputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class, this.outputBox(), EntityType.CHEST_MINECART.getDimensions());
            this.outputInventoryEntities.registerToEntityTracker(this.world);
        }
        lastTickTime_used_OutputInventoryEntityCache = lastTickTime;


        if (Settings.debugOptimizedEntityHopperInteraction) {
            BlockPos pos = this.pos.offset(this.getDirection());
            double double_1 = pos.getX() + 0.5D;
            double double_2 = pos.getY() + 0.5D;
            double double_3 = pos.getZ() + 0.5D;
            HopperHelper.debugCompareInventoryEntities(this.outputInventoryEntities, world, double_1, double_2, double_3);
        }
        inventory = this.outputInventoryEntities.getRandomInventoryEntity(world.random);
        return inventory;
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        invalidateEntityHopperInteractionCache();
        invalidateOptimizedInventoryCache();
    }

    private void invalidateEntityCacheIfNecessary() {
        if (EntityHopperInteraction.ruleUpdates != this.ruleUpdates || this.ruleUpdates == -1) {
            invalidateEntityHopperInteractionCache();
            this.ruleUpdates = EntityHopperInteraction.ruleUpdates;
        }
    }

    private void clearInputItemEntityCache() {
        if (this.inputItemEntities != null) {
            this.inputItemEntities.removeFromEntityTracker(this.world);
            this.inputItemEntities = null;
        }
    }

    private void clearInputInventoryEntityCache() {
        if (this.inputInventoryEntities != null) {
            this.inputInventoryEntities.removeFromEntityTracker(this.world);
            this.inputInventoryEntities = null;
        }
    }

    private void clearOutputInventoryEntityCache() {
        if (this.outputInventoryEntities != null) {
            outputInventoryEntities.removeFromEntityTracker(this.world);
            outputInventoryEntities = null;
        }
    }

    private void invalidateEntityHopperInteractionCache() {
        clearInputItemEntityCache();
        clearInputInventoryEntityCache();
        clearOutputInventoryEntityCache();
        ruleUpdates = -1;

        cachedExtractInventoryAfterLastBlockUpdate = false;
        cachedInsertInventoryAfterLastBlockUpdate = false;
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
