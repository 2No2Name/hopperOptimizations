package hopperOptimizations.mixins;

import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.utils.IHopper;
import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.workarounds.HopperWithClearableCaches;
import hopperOptimizations.workarounds.INoExtractInventoryUntilBlockUpdate;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.Direction;
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

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin_Main extends LootableContainerBlockEntity implements OptimizedInventory, IHopper, Hopper, HopperWithClearableCaches {

    //-----------------------------------------------
    //Fields for optimizedHopperPickupShape
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


    //private boolean itemEntityCacheInvalid = true;
    //private boolean inputInventoryEntityCacheInvalid = true;
    //private boolean outputInventoryEntityCacheInvalid = true;
    //used to invalidate caches that are unused for a longer time



    //-----------------------------------------------
    //-----------------------------------------------
    //Fields for optimizedInventories

    //-----------------------------------------------
    //Fields for inventoryCheckOnBlockUpdate

    //-----------------------------------------------

    @Shadow
    private long lastTickTime;
    @Shadow
    private DefaultedList<ItemStack> inventory;
    @Shadow
    private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int index, @Nullable Direction fromDirection) {
        throw new AssertionError();
    }

    public HopperBlockEntityMixin_Main() {
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
     * Set the cooldown of the receiving hopper like in vanilla
     *
     * @param receiver     the receiving hopper
     * @param wasEmpty     whether the hopper was empty before the transfer
     * @param lastTickTime the tick time the transferring hopper was last ticked
     */
//@Feature("optimizedInventories")
    private static void setReceiverCooldown(Inventory receiver, boolean wasEmpty, long lastTickTime) {
        if (wasEmpty && receiver instanceof HopperBlockEntityMixin_Main) {
            HopperBlockEntityMixin_Main hopperBlockEntity_1 = (HopperBlockEntityMixin_Main) receiver;
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

    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"))
    private static Inventory getInputInventoryFromCache(Hopper hopper) {
        if (!(hopper instanceof HopperBlockEntity))
            return HopperBlockEntity.getInputInventory(hopper); //Hopper Minecarts do not cache Inventories

        Inventory ret = HopperHelper.getInputBlockInventory((HopperBlockEntity) hopper);
        if (ret != null)
            return ret;
        return HopperHelper.getInputEntityInventory((HopperBlockEntity) hopper);
    }

    @Override
    @Shadow
    public abstract double getHopperX();

    @Override
    @Shadow
    public abstract double getHopperY();

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

    @Override
    @Shadow
    public abstract double getHopperZ();

    @Shadow
    protected abstract boolean isFull();

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

    @Inject(method = "isInventoryFull", at = @At(value = "HEAD"), cancellable = true)
    private void isInventoryFullOpt(Inventory inventory_1, Direction direction_1, CallbackInfoReturnable<Boolean> cir) {
        if (inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer(true);
            if (opt != null) cir.setReturnValue(opt.isFull_insertable(direction_1));
        }
    }

    @Override
    @Shadow
    public abstract int size();

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

    @Inject(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isInventoryFull(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/util/math/Direction;)Z", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void optimizeInsert(CallbackInfoReturnable<Boolean> cir, Inventory to, Direction insertFromDirection) {
        InventoryOptimizer toOpt, fromOpt;
        if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer(true)) != null) {
            fromOpt = ((OptimizedInventory) this).getOptimizer(true);
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

    @Override
    public void markRemoved() {
        super.markRemoved();
        clearInputItemEntityCache(Long.MAX_VALUE);
        clearInputInventoryEntityCache(Long.MAX_VALUE);
        clearOutputInventoryEntityCache(Long.MAX_VALUE);
        clearInventoryCache();
    }

    @Redirect(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory()Lnet/minecraft/inventory/Inventory;"))
    private Inventory getOutputInventoryFromCache(HopperBlockEntity hopper) {
        Inventory ret = HopperHelper.getOutputBlockInventory(hopper);
        if (ret != null)
            return ret;
        return HopperHelper.getOutputEntityInventory(hopper);
    }
}
