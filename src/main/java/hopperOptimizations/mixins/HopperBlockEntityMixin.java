package hopperOptimizations.mixins;

import carpet.CarpetServer;
import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static net.minecraft.block.entity.HopperBlockEntity.getInputItemEntities;

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
    private LinkedHashSet<ItemEntity> reachableItems;
    //separate set for entities that have an item type that matches an inventory slot. It becomes invalid when not all hopper slots occupied or hopper slot item types change
    //intended to improve performance when a lot of non-fitting item entities are on top of a hopper
    private LinkedHashSet<ItemEntity> fittingItems;
    //countdown after fittingItems becomes invalid: when the hopper is constantly changing, the overhead of recalculating fitting items doesn't result in any speedup, so don't reinitialize then
    private int transferAttemptsBeforeReinitFittingItems;
    //value of the counter when fittingItems was initialized. a change of the item types means that fitting items is outdated
    private int lastItemTypeChangeCount; //when this.getOptimizer().itemTypeChanges increments, invalidate reachableFittingItems

    //lists (not set, random choosing is required) for inventory entities that are in range
    private List<Entity> reachableInputInventoryEntities;
    private List<Entity> reachableOutputInventoryEntities;
    private boolean itemEntityCacheInvalid = true;
    private boolean inputInventoryEntityCacheInvalid = true;
    private boolean outputInventoryEntityCacheInvalid = true;
    private long lastTickTime_usedItemEntityCache;
    private long lastTickTime_usedInputInventoryEntityCache;
    private long lastTickTime_usedOutputInventoryEntityCache;


    private int ruleUpdates = -1;
    private long lastLazyChunkCheckTick = -1;
    //-----------------------------------------------
    private Box inputBox = null;
    private List<Box> inputArea = null;
    private Box outputBox = null;
    //-----------------------------------------------
    //Fields for optimizedInventories
    private int this_lastChangeCount_Insert;
    private InventoryOptimizer previousInsert;
    private Inventory prevInsertInventory;
    private BlockPos prevInsertInventoryPos;
    private int previousInsert_lastChangeCount;
    private int this_lastChangeCount_Extract;
    private InventoryOptimizer previousExtract;
    private Inventory prevExtractInventory;
    private BlockPos prevExtractInventoryPos;
    private int previousExtract_lastChangeCount;
    //-----------------------------------------------
    //Fields for inventoryCheckOnBlockUpdate
    private boolean hasToCheckForInputInventory = true;
    private boolean hasToCheckForOutputInventory = true;

    //-----------------------------------------------
    private boolean previousExtract_causeMarkDirty;
    @Shadow
    private long lastTickTime;
    @Shadow
    private DefaultedList<ItemStack> inventory;
    private int viewerCount = 0;

    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType_1) {
        super(blockEntityType_1);
    }

    @Shadow
    private static boolean extract(Hopper hopper_1, Inventory inventory_1, int int_1, Direction direction_1) {
        throw new AssertionError();
    }

    @Shadow
    public static boolean extract(Inventory inventory, ItemEntity itemEntity) {
        throw new AssertionError();
    }

    @Shadow
    private static ItemStack transfer(@Nullable Inventory from, Inventory to, ItemStack stack, int index, @Nullable Direction fromDirection) {
        throw new AssertionError();
    }

    @Shadow
    private static IntStream getAvailableSlots(Inventory inventory_1, Direction direction_1) {
        throw new AssertionError();
    }

    @Shadow
    public static Inventory getInputInventory(Hopper hopper) {
        throw new AssertionError();
    }


    /**
     * Transfers as much of the given ItemStack stack into the to Inventory as possible.
     * <p>
     * Optimized replacement for the transfer method. Only used rarely, as optimizeExtract and optimizeInsert only use vanilla code as fallback.
     * Shall behave exactly like the vanilla implementation, just using the advantages of optimizedInventories.
     */
    @Feature("optimizedInventories")
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void optimizedTransfer(Inventory from, Inventory to, ItemStack stack, Direction fromDirection, CallbackInfoReturnable<ItemStack> cir) {
        if (Settings.optimizedInventories && to instanceof OptimizedInventory) {
            InventoryOptimizer toOpt = ((OptimizedInventory) to).getOptimizer();
            if (toOpt == null) return;
            while (!stack.isEmpty()) {
                int toSlot = toOpt.findInsertSlot(stack, fromDirection);
                if (toSlot == -1) break;
                int count = stack.getCount();
                stack = transfer(from, to, stack, toSlot, fromDirection);
                if (stack.getCount() == count) break;
            }
            cir.setReturnValue(stack);
        }
    }

    /**
     * Inject to remove a useless iteration over each interacted inventory on every item transfer attempt.
     * @param inventory Inventory that is either empty or not
     * @return false when the value is unused in vanilla, otherwise inventory.isInvEmpty() or optimized equivalent
     */
    @Feature("optimizedInventories")
    @Redirect(require = 0, method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;isInvEmpty()Z"))
    private static boolean isInvEmptyOpt(Inventory inventory) {
        if (Settings.optimizedInventories && !(inventory instanceof HopperBlockEntity))
            return false; //return anything, value unused

        if (Settings.optimizedInventories && inventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) return opt.getOccupiedSlots() == 0;
        }

        //vanilla call
        return inventory.isInvEmpty();
    }

    /**
     * Notifies the InventoryOptimizer of its inventory being changed.
     * @param destination Inventory that was changed
     * @param index Inventory slot index that was changed
     * @param countChanged ItemStack count by which the inventory slot's stack was incremented
     */
    @Feature("optimizedInventories")
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;increment(I)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void notifyOptimizedInventoryAboutChangedItemStack(Inventory inventory_1, Inventory destination, ItemStack itemStack_1, int index, Direction direction_1, CallbackInfoReturnable<ItemStack> cir, ItemStack itemStack_2, boolean boolean_1, boolean boolean_2, int int_2, int countChanged) {
        if (!Settings.optimizedInventories) return;
        InventoryOptimizer opt = destination instanceof OptimizedInventory ? ((OptimizedInventory) destination).getOptimizer() : null;
        if (opt != null)
            opt.onItemStackCountChanged(index, countChanged);
    }

    /**
     * Inject to use the optimizedInventories empty check
     * @param inventory Inventory that is either empty or not
     * @param direction Expected constant Direction.DOWN
     */
    @Feature("optimizedInventories")
    @Inject(require = 0, method = "isInventoryEmpty", at = @At(value = "HEAD"), cancellable = true)
    private static void isInventoryEmptyOpt(Inventory inventory, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Settings.optimizedInventories && inventory instanceof OptimizedInventory && direction == Direction.DOWN) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) cir.setReturnValue(opt.getFirstOccupiedSlot_extractable() == -1);
        }
    }

    /**
     * Inject to replace the extract method (take items from inventory above) with an optimized but equivalent replacement.
     * Uses the vanilla method as fallback for non-optimized Inventories.
     * @param to Hopper or Hopper Minecart that is extracting
     * @param from Inventory the hopper is extracting from
     */
    @Feature("optimizedInventories")
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/util/math/Direction;DOWN:Lnet/minecraft/util/math/Direction;", shift = At.Shift.AFTER), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private static void optimizeExtract(Hopper to, CallbackInfoReturnable<Boolean> cir, Inventory from) {
        if (Settings.optimizedInventories) {
            InventoryOptimizer toOpt, fromOpt;
            if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer()) != null) {
                boolean isFull = toOpt.isFull_insertable(null);
                if (isFull) { //full hoppers cannot extract more
                    cir.setReturnValue(false);
                    return;
                }
                if (from instanceof OptimizedInventory && (fromOpt = ((OptimizedInventory) from).getOptimizer()) != null) {
                    if (to instanceof IHopper && ((IHopper) to).tryShortcutFailedExtract(toOpt, from, fromOpt)) {
                        cir.setReturnValue(false);
                        return;
                    }

                    int firstOccupiedSlot = fromOpt.getFirstOccupiedSlot_extractable();
                    if (firstOccupiedSlot == -1) {
                        cir.setReturnValue(false);
                        return;
                    }

                    if (toOpt.hasFreeSlots_insertable()) {
                        //When the hopper has any empty slot, just get the first item from the source
                        //fromSlot (= firstOccupiedSlot) already set

                        //Find the slot in the hopper, which might be before the first empty slot due to stacking items
                        ItemStack stack = from.getInvStack(firstOccupiedSlot);
                        int toSlot = toOpt.findInsertSlot(stack, null);
                        //if (toSlot == -1) throw new ThisNeverHappensException(); //empty slot always exists
                        transferOneItem_knownSuccessful(to, toSlot, from, firstOccupiedSlot);
                        from.markDirty();
                        cir.setReturnValue(true);
                        return;
                    } else { //!isFull
                        //When the hopper has no empty slots, try to pull items instead of pushing, because the hopper
                        //inventory is small, and therefore the for loop is shorter.

                        //Assume that all hopper slots are allowed to be filled by the hopper pulling items
                        //(Hopper is not SidedInventory, incompatible with mods that change this)
                        //Make sure that the minimal possible fromSlot is chosen (vanilla behavior)
                        int firstFromSlot = -1;
                        int correspondingToSlot = -1;
                        for (int toSlot = 0; toSlot < to.getInvSize(); toSlot++) {
                            ItemStack stack = to.getInvStack(toSlot);
                            if (stack.getMaxCount() > stack.getCount()) {
                                int fromSlot = fromOpt.indexOf_extractable_endIndex(stack, firstFromSlot);
                                if (fromSlot == -1 || (fromSlot >= firstFromSlot && firstFromSlot != -1)) continue;
                                //Lower fromSlot found, remember corresponding slot
                                firstFromSlot = fromSlot;
                                correspondingToSlot = toSlot;
                            }
                        }
                        if (firstFromSlot != -1) {
                            transferOneItem_knownSuccessful(to, correspondingToSlot, from, firstFromSlot);
                            from.markDirty();
                            cir.setReturnValue(true);
                            return;
                        }
                    }

                    //If someone adds an Inventory Block that calls markDirty on setInvStack, but also implements canExtract behaviors this might be incorrect
                    //Todo test whether barrels, composters, brewing stands, furnaces and shulkerboxes behave like vanilla here! Use comparator update detectors
                    if (getAvailableSlots(from, Direction.DOWN).anyMatch((int i) -> true)) {
                        if (!Settings.failedTransferNoComparatorUpdates)
                            IHopper.markDirtyLikeHopperWould(from, fromOpt);
                        ((IHopper) to).setMarkOtherDirty();
                    }

                    cir.setReturnValue(false);
                }
                //else use vanilla (with optimized transfer) implementation
            }
        }
    }

    @Feature("optimizedInventories")
    private static void transferOneItem_knownSuccessful(Inventory to, int toSlot, Inventory from, int fromSlot) {
        //assume stack sizes were checked, assume item types were already compared
        //todo validate inputs if debugOptimizedInventories

        ItemStack fromStack = from.getInvStack(fromSlot);
        ItemStack toStack = to.getInvStack(toSlot);

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

        if (!Settings.optimizedInventories) return;
        //Notify optimizers of change, if neccessary
        if (!replacedFromStack) {
            InventoryOptimizer opt = from instanceof OptimizedInventory ? ((OptimizedInventory) from).getOptimizer() : null;
            if (opt != null)
                opt.onItemStackCountChanged(fromSlot, -1);
        }

        if (!replacedToStack) {
            InventoryOptimizer opt = to instanceof OptimizedInventory ? ((OptimizedInventory) to).getOptimizer() : null;
            if (opt != null)
                opt.onItemStackCountChanged(toSlot, 1);
        }
    }

    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputItemEntities(Lnet/minecraft/block/entity/Hopper;)Ljava/util/List;", shift = At.Shift.BEFORE), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private static void optimizeItemPickupMixin(Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!Settings.optimizedEntityHopperInteraction || !(hopper instanceof HopperBlockEntity)) {
            return; //use vanilla code when optimization is off or this is a hopper minecart
        }
        assert hopper instanceof HopperBlockEntityMixin; //at runtime: assert hopper instanceof HopperBlockEntity;

        if (!Settings.debugOptimizedEntityHopperInteraction)
            ((HopperBlockEntityMixin) hopper).optimizeItemPickup(cir);
        else {
            try {
                List<ItemEntity> itemEntities = HopperBlockEntity.getInputItemEntities(hopper);
                ItemEntity pickedUp = ((HopperBlockEntityMixin) hopper).optimizeItemPickup(cir);
                if (pickedUp == null) {
                    pickedUp = conventionalItemPickup(hopper, itemEntities.iterator());
                    if (pickedUp != null)
                        throw new IllegalStateException("HopperOptimizations picked up no item even though vanilla could pick up: " + pickedUp.toString());
                } else if (!itemEntities.contains(pickedUp))
                    throw new IllegalStateException("HopperOptimizations picked up an item that vanilla couldn't pick up: " + pickedUp.toString());

                if (!itemEntities.containsAll(((HopperBlockEntityMixin) hopper).reachableItems)) {
                    throw new IllegalStateException("HopperOptimizations found item(s) that vanilla did not find.");
                }

                if (!((HopperBlockEntityMixin) hopper).reachableItems.containsAll(itemEntities)) {
                    throw new IllegalStateException("HopperOptimizations did not find item(s) that vanilla found.");
                }
            } catch (IllegalStateException e) {
                ((HopperBlockEntityMixin) hopper).invalidateEntityHopperInteractionCache();
                Text text = new LiteralText("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
                CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
                e.printStackTrace();
            }
        }
    }

    private static ItemEntity conventionalItemPickup(Hopper hopper, Iterator<ItemEntity> itemEntities) {
        ItemEntity itemEntity;
        while (itemEntities.hasNext()) {
            itemEntity = itemEntities.next();
            if (extract(hopper, itemEntity))
                return itemEntity;
        }
        return null;
    }

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"))
    private static Inventory getInputInventoryFromCache(Hopper hopper) {

        if (!(hopper instanceof HopperBlockEntity))
            return getInputInventory(hopper); //Hopper Minecarts do not cache Inventories

        assert hopper instanceof HopperBlockEntityMixin; //at runtime: assert hopper instanceof HopperBlockEntity;
        boolean skipGetBlockInventoryAt = false;
        if (Settings.optimizedInventories) {
            Inventory ret = ((HopperBlockEntityMixin) hopper).getCachedBlockInputInventory(); //Blockentities cached?
            //this hopper as "no blockupdate since the last time there was no inventory"
            //would need two different types of special return codes besides actual inventories for nothing cached, and null cached
            if (Settings.inventoryCheckOnBlockUpdate && ret == hopper) {
                skipGetBlockInventoryAt = true;
            } else if (ret != null) { //null as "no valid inventory cached, or no inventory found"
                return ret;
            }
        }

        if (!Settings.optimizedEntityHopperInteraction)
            return getInputInventory(hopper);

        World world = ((HopperBlockEntityMixin) hopper).getWorld();
        Inventory inventory;
        if (skipGetBlockInventoryAt) inventory = null;
        else {
            inventory = getBlockInventoryAt(world, ((HopperBlockEntityMixin) hopper).getPos().up());
            ((HopperBlockEntityMixin) hopper).hasToCheckForInputInventory = false;
        }

        if (inventory == null) {
            //Use the entity cache to find minecarts
            ((HopperBlockEntityMixin) hopper).invalidateEntityCacheIfNecessary();
            if (((HopperBlockEntityMixin) hopper).inputInventoryEntityCacheInvalid) {
                double double_1 = hopper.getHopperX();
                double double_2 = hopper.getHopperY() + 1.0D;
                double double_3 = hopper.getHopperZ();
                ((HopperBlockEntityMixin) hopper).reachableInputInventoryEntities = world.getEntities((Entity) null, new Box(double_1 - 0.5D, double_2 - 0.5D, double_3 - 0.5D, double_1 + 0.5D, double_2 + 0.5D, double_3 + 0.5D), EntityPredicates.VALID_INVENTORIES);
                ((HopperBlockEntityMixin) hopper).inputInventoryEntityCacheInvalid = false;
            }
            ((HopperBlockEntityMixin) hopper).lastTickTime_usedInputInventoryEntityCache = ((HopperBlockEntityMixin) hopper).lastTickTime;

            List<Entity> list_1 = ((HopperBlockEntityMixin) hopper).reachableInputInventoryEntities;
            if (list_1.size() == 0)
                return null;
            list_1.removeIf((Entity inv) -> inv.removed || !inv.getBoundingBox().intersects(((HopperBlockEntityMixin) hopper).inputBox()));

            if (Settings.debugOptimizedEntityHopperInteraction) {
                try {
                    List<Entity> inventoriesVanilla = world.getEntities((Entity) null, new Box(hopper.getHopperX() - 0.5D, hopper.getHopperY() + 1.0D - 0.5D, hopper.getHopperZ() - 0.5D, hopper.getHopperX() + 0.5D, hopper.getHopperY() + 1.0D + 0.5D, hopper.getHopperZ() + 0.5D), EntityPredicates.VALID_INVENTORIES);
                    if (!list_1.containsAll(inventoriesVanilla)) {
                        throw new IllegalStateException("HopperOptimizations did not find inventory entity/entities that vanilla found.");
                    }
                    if (!inventoriesVanilla.containsAll(list_1)) {
                        throw new IllegalStateException("HopperOptimizations found inventory entity/entities that vanilla did not find.");
                    }
                    if (inventoriesVanilla.size() != list_1.size()) {
                        throw new IllegalStateException("HopperOptimizations did not find the same number of entities as vanilla."); //duplicate entries!
                    }
                } catch (IllegalStateException e) {
                    ((HopperBlockEntityMixin) hopper).invalidateEntityHopperInteractionCache();
                    Text text = new LiteralText("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
                    CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
                    e.printStackTrace();
                }
            }

            if (!list_1.isEmpty()) {
                ((HopperBlockEntityMixin) hopper).clearInputItemEntityCache();
                return (Inventory) list_1.get(world.random.nextInt(list_1.size()));
            }
            return null;
        }
        //When an inventory block is found, stop using entity caches.
        ((HopperBlockEntityMixin) hopper).clearInputInventoryEntityCache();
        ((HopperBlockEntityMixin) hopper).clearInputItemEntityCache();
        return inventory;
    }

    private ItemEntity optimizeItemPickup(CallbackInfoReturnable<Boolean> cir) {

        this.invalidateEntityCacheIfNecessary();


        if (this.inputArea == null) {
            VoxelShape inputArea = this.getInputAreaShape();
            this.inputArea = inputArea.getBoundingBoxes().stream().map(box -> box.offset(this.getHopperX() - 0.5D, this.getHopperY() - 0.5D, this.getHopperZ() - 0.5D)).collect(Collectors.toList());
        }

        //get the optimizer of this hopper
        final InventoryOptimizer opt;
        if (Settings.optimizedInventories)
            opt = this.getOptimizer();
        else {
            opt = null;
            this.fittingItems = null;
        }

        //fix the entity cache in case it is not initialized
        if (this.itemEntityCacheInvalid) {
            //get entities like vanilla
            List<ItemEntity> nearbyItems = getInputItemEntities(this);
            int size;
            //keep a set of reachable items to be faster next time
            this.reachableItems = new LinkedHashSet<>(size = nearbyItems.size());
            this.reachableItems.addAll(nearbyItems);

            if (Settings.optimizedInventories) {
                //when using optimizedInventories also keep a set of items that the hopper already has
                //only do this when there is no empty slot, because then the set is equivalent to reachableItems
                if (opt != null && !opt.hasFreeSlots_insertable()) {
                    this.fittingItems = new LinkedHashSet<>(size);
                    //Assume hoppers are no SidedInventory due to some other mod!
                    assert !(this instanceof SidedInventory);
                    nearbyItems.removeIf((ItemEntity itemEntity) -> {
                        //If the item cannot be taken out of the inventory, it is not inside the inventory.
                        //explicitly not checking whether those stacks are full, because that changes very often in sorters etc.
                        //the set is supposed to be only recalculated when the item types the hopper can pick up change
                        return -1 == opt.indexOf(itemEntity.getStack());
                    });
                    this.fittingItems.addAll(nearbyItems);

                    //keep the current counter value to be able to detect changes to the item types the hopper can pick up later
                    this.lastItemTypeChangeCount = opt.getItemTypeChanges();
                }
            }
            this.itemEntityCacheInvalid = false;
        } else {
            //invalidate the fitting items set when the item types the hopper can pick up changed
            if (Settings.optimizedInventories && this.lastItemTypeChangeCount != opt.getItemTypeChanges()) {
                this.fittingItems = null;
                this.transferAttemptsBeforeReinitFittingItems = 5;
                this.lastItemTypeChangeCount = opt.getItemTypeChanges();
            }

            if (this.reachableItems.size() > 0) {
                //remove item entities that have died or have moved away from the reachable items set
                //when any were removed, also fittingItems needs to be filtered.
                List<ItemEntity> listRemovedItems = new ArrayList<>();
                boolean removedSome = this.reachableItems.removeIf((ItemEntity entity) -> {
                    if (entity.removed) {
                        listRemovedItems.add(entity);
                        return true;
                    }
                    Box entityBoundingBox = entity.getBoundingBox();
                    for (Box box : this.inputArea) {
                        if (entityBoundingBox.intersects(box))
                            return false;
                    }
                    listRemovedItems.add(entity);
                    return true;
                });
                //remove item entities that have died or have moved away from the reachable items set. fitting items must be a subset of reachable items
                if (removedSome && this.fittingItems != null && this.fittingItems.size() > 0)
                    this.fittingItems.removeAll(listRemovedItems);
            }
        }
        //item entity cache up to date and valid
        this.lastTickTime_usedItemEntityCache = this.lastTickTime;

        //reinitialize fitting items if not invalidated during this transfer attempt (would happen every time when transferring nonstackable items)
        if (opt != null && this.lastItemTypeChangeCount == opt.getItemTypeChanges() && this.transferAttemptsBeforeReinitFittingItems == 0 && this.fittingItems == null && !opt.hasFreeSlots_insertable()) {
            this.fittingItems = new LinkedHashSet<>(this.reachableItems);
            //Assume hoppers are no SidedInventory due to some other mod!
            assert !(this instanceof SidedInventory);
            this.fittingItems.removeIf((ItemEntity itemEntity) -> {
                //If the item cannot be taken out of the inventory, it is not inside the inventory. (no SidedInventory!)
                //explicitly not checking whether those stacks are full, because that changes very often in sorters etc.
                //the set is supposed to be only recalculated when the item types the hopper can pick up change
                return -1 == opt.indexOf(itemEntity.getStack());
            });
        } else if (this.transferAttemptsBeforeReinitFittingItems > 0)
            --this.transferAttemptsBeforeReinitFittingItems;


        LinkedHashSet<ItemEntity> itemsToTransfer = this.fittingItems == null ? this.reachableItems : this.fittingItems;
        for (ItemEntity itemEntity : itemsToTransfer) {
            if (HopperBlockEntity.extract(this, itemEntity)) {
                cir.setReturnValue(true);
                return itemEntity;
            }
        }
        cir.setReturnValue(false);
        return null;
    }


    @Feature("optimizedEntityHopperInteraction")
    private static Inventory getBlockInventoryAt(World world_1, BlockPos blockPos_1) {
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
                    inventory_1 = ChestBlock.getInventory(blockState_1, world_1, blockPos_1, true);
                }
            }
        }
        return inventory_1;
    }

    @Shadow
    public abstract double getHopperX();

    @Shadow
    public abstract double getHopperY();

    @Shadow
    public abstract double getHopperZ();

    @Shadow
    public abstract void setInvStack(int int_1, ItemStack itemStack_1);

    @Shadow
    public abstract int getInvSize();

    @Shadow
    private boolean isDisabled() {
        throw new AssertionError();
    }

    @Shadow
    private void setCooldown(int count) {
        throw new AssertionError();
    }

    //@Shadow
    //protected abstract boolean isEmpty();

    @Shadow
    protected abstract boolean isFull();

    //@Shadow
    //public abstract boolean isInvEmpty();

    @Shadow
    private Inventory getOutputInventory() {
        throw new AssertionError();
    }

    @Shadow
    protected abstract boolean isEmpty();

    @Feature("optimizedHopperPickupShape")
    public VoxelShape getInputAreaShape() {
        if (Settings.simplifiedHopperPickupShape) return INPUT_AREA_SHAPE_SIMPLIFIED;
        return INPUT_AREA_SHAPE;
    }

    @Feature("optimizedInventories")
    private void invalidateOptimizedInventoryCache() {
        previousInsert = null;
        prevInsertInventory = null;
        previousExtract = null;
        prevExtractInventory = null;
        this.invalidateOptimizer();
    }

    @Feature("optimizedInventories")
    private boolean inventoryCacheValid(Inventory cachedInv, BlockPos cachedInvPos, boolean extracting) {
        if (cachedInv instanceof BlockEntity) {
            if (!((BlockEntity) cachedInv).isInvalid() &&
                    ((BlockEntity) cachedInv).getPos().equals(cachedInvPos)) {
                if (cachedInv instanceof ChestBlockEntity)
                    return ChestType.SINGLE == ((ChestBlockEntity) cachedInv).getCachedState().get(ChestBlock.CHEST_TYPE);
                return true;
            }
        }
        if (cachedInv instanceof DoubleInventory && cachedInv instanceof OptimizedInventory) {
            return ((OptimizedInventory) cachedInv).isStillValid();
        }
        if (Settings.inventoryCheckOnBlockUpdate && cachedInv == null)
            return extracting ? !hasToCheckForInputInventory : !hasToCheckForOutputInventory;
        return false; //never cache Entity Inventories, the entity might have moved away or a Blockentity might have been placed
    }

    /**
     * Gets the cached block entity input inventory if it is still valid.
     * Requires optimizedInventories
     * Note: In vanilla hoppers getBlockState the location, which makes them load chunks in some versions.
     *
     * @return cached output inventory, if found and valid. this if null is still valid (inventoryCheckOnBlockupdate)
     */
    @Feature("optimizedInventories")
    private Inventory getCachedBlockInputInventory() {
        //maybe use optional or some static NULL inv
        if (!Settings.optimizedInventories) return null;
        //cached inventory alive && position checks
        if (inventoryCacheValid(prevExtractInventory, prevExtractInventoryPos, true)) {
            return prevExtractInventory == null ? this : prevExtractInventory; //this hopper as "no blockupdate since the last time there was no inventory"
        }
        //previousExtract = null; //invalidate cache
        prevExtractInventory = null;
        return null;


    }

    /**
     * Gets the cached block entity output inventory if it is still valid.
     * Requires optimizedInventories
     * Note: In vanilla hoppers getBlockState the location, which makes them load chunks in some versions.
     *
     * @return cached output inventory, if found and valid
     */
    @Feature("optimizedInventories")
    private Inventory getCachedBlockOutputInventory() {
        if (!Settings.optimizedInventories) return null;
        //cached inventory alive && position checks
        if (inventoryCacheValid(prevInsertInventory, prevInsertInventoryPos, false)) {
            return prevInsertInventory;
        }
        //previousInsert = null; //invalidate cache
        prevInsertInventory = null;
        return null;

    }

    /**
     * Checks whether the last item insert attempt was with the same inventory as the current one AND
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
    @Feature("optimizedInventories")
    public boolean tryShortcutFailedInsert(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (this_lastChangeCount_Insert != thisChangeCount || otherOpt != previousInsert || previousInsert_lastChangeCount != otherChangeCount) {
            this_lastChangeCount_Insert = thisChangeCount;
            previousInsert = otherOpt;
            if (other instanceof BlockEntity) {
                prevInsertInventory = other;
                prevInsertInventoryPos = ((BlockEntity) other).getPos().toImmutable();
            } else if (other instanceof DoubleInventory) {
                prevInsertInventory = other;
                prevInsertInventoryPos = null;
            }
            previousInsert_lastChangeCount = otherChangeCount;
            return false;
        }
        return true;
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
    @Feature("optimizedInventories")
    public boolean tryShortcutFailedExtract(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (this_lastChangeCount_Extract != thisChangeCount || otherOpt != previousExtract || previousExtract_lastChangeCount != otherChangeCount) {
            this_lastChangeCount_Extract = thisChangeCount;
            previousExtract = otherOpt;
            if (other instanceof BlockEntity) {
                prevExtractInventory = other;
                prevExtractInventoryPos = ((BlockEntity) other).getPos().toImmutable();
            } else if (other instanceof DoubleInventory) {
                prevExtractInventory = other;
                prevExtractInventoryPos = null;
            }
            previousExtract_lastChangeCount = otherChangeCount;
            previousExtract_causeMarkDirty = false;
            return false;
        }
        if (previousExtract_causeMarkDirty && !Settings.failedTransferNoComparatorUpdates)
            IHopper.markDirtyLikeHopperWould(other, otherOpt); //failed transfers sometimes cause comparator updates

        return true;
    }

    @Feature("optimizedInventories")
    public void setMarkOtherDirty() {
        this.previousExtract_causeMarkDirty = true;
    }

    @Feature("optimizedInventories")
    @Redirect(method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isEmpty()Z"))
    private boolean isEmptyOpt(HopperBlockEntity hopperBlockEntity) {
        if (Settings.optimizedInventories) {
            InventoryOptimizer opt = this.getOptimizer();
            if (opt != null) return opt.getFirstOccupiedSlot_extractable() == -1;
        }
        return isEmpty();
    }

    @Feature("optimizedInventories")
    @Redirect(method = "insertAndExtract", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isFull()Z"))
    private boolean isFullOpt(HopperBlockEntity hopperBlockEntity) {
        if (Settings.optimizedInventories) {
            InventoryOptimizer opt = this.getOptimizer();
            if (opt != null) return opt.isFull_insertable(null);
        }
        return isFull();
    }

    @Feature("optimizedInventories")
    @Inject(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;isInventoryFull(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/util/math/Direction;)Z", shift = At.Shift.BEFORE), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private void optimizeInsert(CallbackInfoReturnable<Boolean> cir, Inventory to, Direction insertFromDirection) {
        if (Settings.optimizedInventories) {
            InventoryOptimizer toOpt, fromOpt;
            if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer()) != null) {
                fromOpt = ((OptimizedInventory) this).getOptimizer();
                if (fromOpt != null && ((IHopper) this).tryShortcutFailedInsert(fromOpt, to, toOpt)) {
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
                    int invSize = this.getInvSize();
                    int transferAttempts = fromOpt.getOccupiedSlots();
                    for (int fromSlot = firstOccupiedSlot; transferAttempts > 0 && fromSlot < invSize; fromSlot++) {
                        ItemStack stack = this.getInvStack(fromSlot);
                        if (!stack.isEmpty()) {
                            --transferAttempts;
                            int toSlot = toOpt.findInsertSlot(stack, insertFromDirection);
                            if (toSlot == -1) continue;

                            boolean wasEmpty = toOpt.getFirstOccupiedSlot_extractable() == -1;
                            transferOneItem_knownSuccessful(to, toSlot, this, fromSlot);
                            setReceiverCooldown(to, wasEmpty);
                            to.markDirty();
                            cir.setReturnValue(true);
                            return;
                        }
                    }
                    cir.setReturnValue(false);
                }
            }
        }
    }

    @Feature("optimizedInventories")
    @Inject(method = "isInventoryFull", at = @At(value = "HEAD"), cancellable = true)
    private void isInventoryFullOpt(Inventory inventory_1, Direction direction_1, CallbackInfoReturnable<Boolean> cir) {
        if (Settings.optimizedInventories && inventory_1 instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer();
            if (opt != null) cir.setReturnValue(opt.isFull_insertable(direction_1));
        }
    }

    @Feature("optimizedInventories")
    private void setReceiverCooldown(Inventory receiver, boolean wasEmpty) {
        if (wasEmpty && receiver instanceof HopperBlockEntity) {
            HopperBlockEntityMixin hopperBlockEntity_1 = (HopperBlockEntityMixin) receiver;
            if (!hopperBlockEntity_1.isDisabled()) {
                int int_4 = 0;
                if (hopperBlockEntity_1.lastTickTime >= this.lastTickTime) {
                    int_4 = 1;
                }
                hopperBlockEntity_1.setCooldown(8 - int_4);
            }
        }

        receiver.markDirty();
    }

    @Feature("optimizedInventories")
    @Redirect(method = "<init>()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Feature("optimizedInventories")
    @Inject(method = "setInvStackList", at = @At("RETURN"))
    private void onSetStackList(DefaultedList<ItemStack> stackList, CallbackInfo ci) {
        if (!(inventory instanceof InventoryListOptimized))
            inventory = new InventoryListOptimized(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
    }

    @Feature("optimizedInventories")
    @Redirect(method = "fromTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Nullable
    public InventoryOptimizer getOptimizer() {
        return mayHaveOptimizer() && inventory instanceof InventoryListOptimized ? ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this) : null;
    }

    @Override
    public void invalidateOptimizer() {
        if (inventory instanceof InventoryListOptimized) ((InventoryListOptimized) inventory).invalidateOptimizer();
    }

    //@Inject(method = "onInvOpen(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "HEAD"))
    public void onInvOpen(PlayerEntity playerEntity_1) {
        if (!playerEntity_1.isSpectator()) {
            viewerCount++;
            if (Settings.playerInventoryDeoptimization && !playerEntity_1.isSpectator())
                invalidateOptimizer();
        }
    }

    public void onInvClose(PlayerEntity playerEntity_1) {
        if (!playerEntity_1.isSpectator()) {
            viewerCount--;
            if (Settings.playerInventoryDeoptimization && viewerCount < 0) {
                System.out.println("Hopper viewer count inconsistency, might affect performance of optimizedInventories!");
                viewerCount = 0;
            }
        }
    }

    @Override
    public boolean mayHaveOptimizer() {
        return !this.world.isClient && (!Settings.playerInventoryDeoptimization || viewerCount <= 0);
    }

    private Box inputBox() {
        if (inputBox == null) return inputBox = new Box(this.pos.up());
        return inputBox;
    }

    private Box outputBox() {
        if (outputBox != null) return outputBox;
        Direction direction_1 = this.getCachedState().get(HopperBlock.FACING);
        return outputBox = new Box(this.pos.offset(direction_1));
    }

    @Feature("optimizedEntityHopperInteraction")
    public void notifyOfNearbyEntity(Entity entity) {
        invalidateOldUnusedCaches();

        if (!itemEntityCacheInvalid && entity instanceof ItemEntity && !reachableItems.contains(entity)) {
            reachableItems.add((ItemEntity) entity);
            if (fittingItems != null && Settings.optimizedInventories) {
                InventoryOptimizer opt = this.getOptimizer();
                if (opt == null) clearInputItemEntityCache();
                else if (-1 != opt.indexOf(((ItemEntity) entity).getStack()))
                    fittingItems.add((ItemEntity) entity);
            }
        }
        if (!inputInventoryEntityCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(inputBox()) && !reachableInputInventoryEntities.contains(entity))
            reachableInputInventoryEntities.add(entity);
        if (!outputInventoryEntityCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(outputBox()) && !reachableOutputInventoryEntities.contains(entity))
            reachableOutputInventoryEntities.add(entity);
    }

    private void invalidateOldUnusedCaches() {
        long timeLimit = this.world.getTime() - 200;
        //200 ticks is an arbitrary number, probably doesn't matter in practice anyways, when it is bigger than 1 hopper cooldown
        //not invalidating the cache may lead to a memory leak with a lot of dead entities in the cache that never get GCed because of it.
        if (!itemEntityCacheInvalid && lastTickTime_usedItemEntityCache < timeLimit) clearInputItemEntityCache();
        if (!inputInventoryEntityCacheInvalid && lastTickTime_usedInputInventoryEntityCache < timeLimit)
            clearInputInventoryEntityCache();
        if (!outputInventoryEntityCacheInvalid && lastTickTime_usedOutputInventoryEntityCache < timeLimit)
            clearOutputInventoryEntityCache();

    }

    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "onEntityCollided", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;insertAndExtract(Ljava/util/function/Supplier;)Z", shift = At.Shift.BEFORE))
    private void onEntityCollided1(Entity entity_1, CallbackInfo ci) {
        this.notifyOfNearbyEntity(entity_1);
    }

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory()Lnet/minecraft/inventory/Inventory;"))
    private Inventory getOutputInventoryFromCache(HopperBlockEntity hopper) {

        boolean skipGetBlockInventoryAt = false;
        if (Settings.optimizedInventories) {
            Inventory ret = getCachedBlockOutputInventory();
            if (Settings.inventoryCheckOnBlockUpdate && ret == this) { //this hopper as "no blockupdate since the last time there was no inventory"
                skipGetBlockInventoryAt = true;
            } else if (ret != null) return ret;
        }

        if (!Settings.optimizedEntityHopperInteraction) return this.getOutputInventory();

        World world = hopper.getWorld();
        if (world == null) return null;

        Direction outputDirection = hopper.getCachedState().get(HopperBlock.FACING);
        Inventory inventory;
        if (skipGetBlockInventoryAt) inventory = null;
        else {
            inventory = getBlockInventoryAt(world, hopper.getPos().offset(outputDirection));
            hasToCheckForOutputInventory = false;
        }
        if (inventory == null) {
            this.invalidateEntityCacheIfNecessary();
            if (this.outputInventoryEntityCacheInvalid) {
                BlockPos pos = this.pos.offset(outputDirection);
                double double_1 = pos.getX() + 0.5D;
                double double_2 = pos.getY() + 0.5D;
                double double_3 = pos.getZ() + 0.5D;
                this.reachableOutputInventoryEntities = world.getEntities((Entity) null, new Box(double_1 - 0.5D, double_2 - 0.5D, double_3 - 0.5D, double_1 + 0.5D, double_2 + 0.5D, double_3 + 0.5D), EntityPredicates.VALID_INVENTORIES);
                this.outputInventoryEntityCacheInvalid = false;
            }
            lastTickTime_usedOutputInventoryEntityCache = lastTickTime;


            List<Entity> list_1 = this.reachableOutputInventoryEntities;
            if (list_1.isEmpty())
                return null;
            list_1.removeIf((Entity inv) -> inv.removed || !inv.getBoundingBox().intersects(this.outputBox()));

            if (Settings.debugOptimizedEntityHopperInteraction) {
                try {
                    BlockPos pos = this.pos.offset(outputDirection);
                    double double_1 = pos.getX() + 0.5D;
                    double double_2 = pos.getY() + 0.5D;
                    double double_3 = pos.getZ() + 0.5D;
                    List<Entity> inventoriesVanilla = world.getEntities((Entity) null, new Box(double_1 - 0.5D, double_2 - 0.5D, double_3 - 0.5D, double_1 + 0.5D, double_2 + 0.5D, double_3 + 0.5D), EntityPredicates.VALID_INVENTORIES);

                    if (!list_1.containsAll(inventoriesVanilla)) {
                        throw new IllegalStateException("HopperOptimizations did not find inventory entity/entities that vanilla found.");
                    }
                    if (!inventoriesVanilla.containsAll(list_1)) {
                        throw new IllegalStateException("HopperOptimizations found inventory entity/entities that vanilla did not find.");
                    }
                    if (inventoriesVanilla.size() != list_1.size()) {
                        throw new IllegalStateException("HopperOptimizations did not find the same number of entities as vanilla."); //duplicate entries!
                    }
                } catch (IllegalStateException e) {
                    this.invalidateEntityHopperInteractionCache();
                    Text text = new LiteralText("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
                    CarpetServer.minecraft_server.getPlayerManager().broadcastChatMessage(text, false);
                    e.printStackTrace();
                }
            }

            if (!list_1.isEmpty()) {
                return (Inventory) list_1.get(world.random.nextInt(list_1.size()));
            }
            return null;
        }
        clearOutputInventoryEntityCache();
        return inventory;
    }

    private boolean hasToInvalidateEntityCache() {
        return false;
        /*
        //Replaced with WorldChunkMixin telling the hopper even about new lazy entities.
        if (lastLazyChunkCheckTick == this.world.getTime() && lastLazyChunkCheckTick != -1) return false;
        lastLazyChunkCheckTick = this.world.getTime();
        return !doAllNearbyEntitiesTick();
        */
    }

    //When the hopper is in lazy chunks, caching doesn't work when entities suddenly can appear from dispensers, destroyed blocks etc.
    //Chunks should maybe cache whether they are ticking, this will cost some lag otherwise
    private boolean doAllNearbyEntitiesTick() {
        if (this.world == null || this.world.isClient()) throw new UnsupportedOperationException();

        int x = this.getPos().getX() - 2;
        int z = this.getPos().getZ() - 2;
        int x2 = x + 4;
        int z2 = z + 4;
        x = x >> 4;
        z = z >> 4;
        x2 = x2 >> 4;
        z2 = z2 >> 4;
        ChunkManager chunkManager = this.world.getChunkManager();
        for (int i = x; i <= x2; i++)
            for (int j = z; j <= z2; j++) {
                if (!chunkManager.shouldTickChunk(new ChunkPos(x, z))) {
                    return false;
                }
            }
        return true;
    }

    public void invalidate() {
        super.invalidate();
        invalidateEntityHopperInteractionCache();
        invalidateOptimizedInventoryCache();
    }

    private void invalidateEntityCacheIfNecessary() {
        if (EntityHopperInteraction.ruleUpdates != this.ruleUpdates || ruleUpdates == -1 || hasToInvalidateEntityCache()) {
            invalidateEntityHopperInteractionCache();
            this.ruleUpdates = EntityHopperInteraction.ruleUpdates;
        }
    }

    private void clearInputItemEntityCache() {
        itemEntityCacheInvalid = true;
        reachableItems = null;
        fittingItems = null;
        transferAttemptsBeforeReinitFittingItems = 5;
    }

    private void clearInputInventoryEntityCache() {
        inputInventoryEntityCacheInvalid = true;
        reachableInputInventoryEntities = null;
    }

    private void clearOutputInventoryEntityCache() {
        outputInventoryEntityCacheInvalid = true;
        reachableOutputInventoryEntities = null;
    }

    private void invalidateEntityHopperInteractionCache() {
        clearInputItemEntityCache();
        clearInputInventoryEntityCache();
        clearOutputInventoryEntityCache();
        ruleUpdates = -1;

        hasToCheckForInputInventory = true;
        hasToCheckForOutputInventory = true;

        inputArea = null;
        inputBox = null;
        outputBox = null;
    }

    public void onBlockUpdate() {
        hasToCheckForInputInventory = true;
        hasToCheckForOutputInventory = true;
    }

}
