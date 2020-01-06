package hopperOptimizations.mixins;

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
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Feature("hopperCounters")
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends LootableContainerBlockEntity implements OptimizedInventory, IHopper, Hopper {

    //-----------------------------------------------
    //Fields for optimizedHopperPickupShape
    //Two pixels larger in horizontal directions than the inside of the "bowl" of the hopper. But entities normally don't clip into hoppers
    private static final VoxelShape INPUT_AREA_SHAPE_SIMPLIFIED = Block.createCuboidShape(0.0D, 11.0D, 0.0D, 16.0D, 32.0D, 16.0D);
    //-----------------------------------------------
    //Fields for OptimizedEntityHopperInteraction
    //todo check whether ArrayLists are a good choice, otherwise find something faster, maybe a set class //also asymptotically bad contains call, use some set instead
    private List<ItemEntity> nearbyItems = new ArrayList<>(0); //Order will be different from vanilla
    private List<Entity> nearbyInputInventoryEntities = new ArrayList<>(0);
    private List<Entity> nearbyOutputInventoryEntities = new ArrayList<>(0);
    private boolean entityCacheInvalid = true;
    private boolean inputInventoryCacheInvalid = true;
    private boolean outputInventoryCacheInvalid = true;
    private int ruleUpdates = -1;
    private List<Box> boxes = null;
    private long lastLazyChunkCheckTick = -1;
    //-----------------------------------------------
    private Box inputBox = null;
    //-----------------------------------------------
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

    @Feature("optimizedInventories")
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private static void optimizedTransfer(Inventory from, Inventory to, ItemStack stack, Direction fromDirection, CallbackInfoReturnable<ItemStack> cir) {
        if (Settings.optimizedInventories && to instanceof OptimizedInventory) {
            InventoryOptimizer optimizer = ((OptimizedInventory) to).getOptimizer();
            if (optimizer == null) return;
            while (!stack.isEmpty()) {
                int toSlot = optimizer.findInsertSlot(stack, fromDirection);
                if (toSlot == -1) break;
                int count = stack.getCount();
                stack = transfer(from, to, stack, toSlot, fromDirection);
                if (stack.getCount() == count) break;
            }
            cir.setReturnValue(stack);
        }
    }

    @Feature("optimizedInventories")
    @Redirect(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/Inventory;isInvEmpty()Z"))
    private static boolean isInvEmptyOpt(Inventory inventory) {
        if (Settings.optimizedInventories && !(inventory instanceof HopperBlockEntity))
            return false; //return anything, value unused

        if (Settings.optimizedInventories && inventory instanceof OptimizedInventory) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory).getOptimizer();
            if (opt != null) return opt.getFirstOccupiedSlot_extractable() == -1;
        }
        return inventory.isInvEmpty();
    }

    @Feature("optimizedInventories")
    @Inject(method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;increment(I)V", shift = At.Shift.AFTER), locals = LocalCapture.CAPTURE_FAILHARD)
    private static void notifyOptimizedInventoryAboutChangedItemStack(Inventory inventory_1, Inventory destination, ItemStack itemStack_1, int int_1, Direction direction_1, CallbackInfoReturnable<ItemStack> cir, ItemStack itemStack_2, boolean boolean_1, boolean boolean_2, int int_2, int int_3) {
        if (!Settings.optimizedInventories) return;
        InventoryOptimizer opt = destination instanceof OptimizedInventory ? ((OptimizedInventory) destination).getOptimizer() : null;
        if (opt != null)
            opt.onItemStackCountChanged(int_1, int_3);
    }

    @Feature("optimizedInventories")
    @Inject(method = "isInventoryEmpty", at = @At(value = "HEAD"), cancellable = true)
    private static void isInventoryEmptyOpt(Inventory inventory_1, Direction direction_1, CallbackInfoReturnable<Boolean> cir) {
        if (Settings.optimizedInventories && inventory_1 instanceof OptimizedInventory && direction_1 == Direction.DOWN) {
            InventoryOptimizer opt = ((OptimizedInventory) inventory_1).getOptimizer();
            if (opt != null) cir.setReturnValue(opt.getFirstOccupiedSlot_extractable() == -1);
        }
    }

    @Feature("optimizedInventories")
    @Inject(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "FIELD", target = "Lnet/minecraft/util/math/Direction;DOWN:Lnet/minecraft/util/math/Direction;", shift = At.Shift.AFTER), cancellable = true, locals = LocalCapture.CAPTURE_FAILHARD)
    private static void optimizeExtract(Hopper to, CallbackInfoReturnable<Boolean> cir, Inventory from) {
        if (Settings.optimizedInventories) {
            InventoryOptimizer toOpt, fromOpt;

            if (to instanceof OptimizedInventory && (toOpt = ((OptimizedInventory) to).getOptimizer()) != null) {
                boolean isFull = toOpt.isFull_insertable(null);
                if (isFull) {
                    cir.setReturnValue(false);
                    return;
                }
                if (from instanceof OptimizedInventory && (fromOpt = ((OptimizedInventory) from).getOptimizer()) != null) {
                    if (to instanceof IHopper && ((IHopper) to).tryShortcutFailedTransfer(toOpt, from, fromOpt, true)) {
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
        //todo validate inputs if debug flag is set

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

    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputItemEntities(Lnet/minecraft/block/entity/Hopper;)Ljava/util/List;"))
    private static List<ItemEntity> getInputItemEntitiesFromCache(Hopper hopper) {
        if (!Settings.optimizedEntityHopperInteraction || !(hopper instanceof HopperBlockEntity)) {
            return HopperBlockEntity.getInputItemEntities(hopper);
        }
        ((HopperBlockEntityMixin) hopper).invalidateEntityCacheIfNeccessary();

        if (((HopperBlockEntityMixin) hopper).entityCacheInvalid) {
            ((HopperBlockEntityMixin) hopper).nearbyItems = HopperBlockEntity.getInputItemEntities(hopper);
            ((HopperBlockEntityMixin) hopper).entityCacheInvalid = false;
            return ((HopperBlockEntityMixin) hopper).nearbyItems;
        }

        if (((HopperBlockEntityMixin) hopper).nearbyItems.size() == 0)
            return ((HopperBlockEntityMixin) hopper).nearbyItems;

        VoxelShape inputArea = hopper.getInputAreaShape();
        if (((HopperBlockEntityMixin) hopper).boxes == null)
            ((HopperBlockEntityMixin) hopper).boxes = inputArea.getBoundingBoxes().stream().map(box -> box.offset(hopper.getHopperX() - 0.5D, hopper.getHopperY() - 0.5D, hopper.getHopperZ() - 0.5D)).collect(Collectors.toList());

        ((HopperBlockEntityMixin) hopper).nearbyItems.removeIf((ItemEntity entity) -> {
            if (entity.removed) return true;
            for (Box box : ((HopperBlockEntityMixin) hopper).boxes) {
                if (box.intersects(entity.getBoundingBox()))
                    return false;
            }
            return true;
        });
        return ((HopperBlockEntityMixin) hopper).nearbyItems;
    }

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "extract(Lnet/minecraft/block/entity/Hopper;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getInputInventory(Lnet/minecraft/block/entity/Hopper;)Lnet/minecraft/inventory/Inventory;"))
    private static Inventory getInputInventoryFromCache(Hopper hopper) {

        if (!(hopper instanceof HopperBlockEntity))
            return getInputInventory(hopper); //Hopper Minecarts do not cache Inventories

        Inventory ret = ((HopperBlockEntityMixin) hopper).getCachedBlockInventory(true); //Blockentities cached?
        if (ret != null) return ret;

        if (!Settings.optimizedEntityHopperInteraction)
            return getInputInventory(hopper);

        World world = ((HopperBlockEntityMixin) hopper).getWorld();
        Inventory inventory = getBlockInventoryAt(world, ((HopperBlockEntityMixin) hopper).getPos().up());
        if (inventory == null) {
            //Use the entity cache to find minecarts
            ((HopperBlockEntityMixin) hopper).invalidateEntityCacheIfNeccessary();
            if (((HopperBlockEntityMixin) hopper).inputInventoryCacheInvalid) {
                double double_1 = hopper.getHopperX();
                double double_2 = hopper.getHopperY() + 1.0D;
                double double_3 = hopper.getHopperZ();
                ((HopperBlockEntityMixin) hopper).nearbyInputInventoryEntities = world.getEntities((Entity) null, new Box(double_1 - 0.5D, double_2 - 0.5D, double_3 - 0.5D, double_1 + 0.5D, double_2 + 0.5D, double_3 + 0.5D), EntityPredicates.VALID_INVENTORIES);
                ((HopperBlockEntityMixin) hopper).inputInventoryCacheInvalid = false;
            }

            List<Entity> list_1 = ((HopperBlockEntityMixin) hopper).nearbyInputInventoryEntities;
            if (list_1.size() == 0)
                return null;
            list_1.removeIf((Entity inv) -> inv.removed || !inv.getBoundingBox().intersects(((HopperBlockEntityMixin) hopper).inputBox()));

            if (!list_1.isEmpty()) {
                return (Inventory) list_1.get(world.random.nextInt(list_1.size()));
            }
            return null;
        }
        return inventory;
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

    @Shadow
    protected abstract boolean isEmpty();

    @Shadow
    protected abstract boolean isFull();

    @Shadow
    public abstract boolean isInvEmpty();

    @Shadow
    private Inventory getOutputInventory() {
        throw new AssertionError();
    }

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
    private boolean inventoryCacheValid(Inventory cachedInv, BlockPos cachedInvPos) {
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
        return false; //never cache Entity Inventories, the entity might have moved away or a Blockentity might have been placed
    }

    @Feature("optimizedInventories")
    private Inventory getCachedBlockInventory(boolean extracting) {
        if (!Settings.optimizedInventories) return null;
        //cached inventory alive && position checks
        if (extracting) {
            if (inventoryCacheValid(prevExtractInventory, prevExtractInventoryPos)) {
                return prevExtractInventory;
            }
            previousExtract = null; //invalidate cache
            prevExtractInventory = null;
            return null;
        } else {
            if (inventoryCacheValid(prevInsertInventory, prevInsertInventoryPos)) {
                return prevInsertInventory;
            }
            previousInsert = null; //invalidate cache
            prevInsertInventory = null;
            return null;
        }
    }

    @Feature("optimizedInventories")
    public boolean tryShortcutFailedTransfer(InventoryOptimizer thisOpt, Inventory other, InventoryOptimizer otherOpt, boolean extracting) {
        int thisChangeCount = thisOpt.getInventoryChangeCount();
        int otherChangeCount = otherOpt.getInventoryChangeCount();
        if (extracting) {
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
        } else {
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
                if (fromOpt != null && ((IHopper) this).tryShortcutFailedTransfer(fromOpt, to, toOpt, false)) {
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
            inventory = new InventoryListOptimized<>(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
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
            if (!Settings.playerHopperOptimizations && !playerEntity_1.isSpectator())
                invalidateOptimizer();
        }
    }

    public void onInvClose(PlayerEntity playerEntity_1) {
        if (!playerEntity_1.isSpectator()) {
            viewerCount--;
            if (!Settings.playerHopperOptimizations && viewerCount < 0) {
                System.out.println("Hopper viewer count inconsistency, might affect performance of optimizedInventories!");
                viewerCount = 0;
            }
        }
    }

    @Override
    public boolean mayHaveOptimizer() {
        return !this.world.isClient && (Settings.playerHopperOptimizations || viewerCount <= 0);
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
        if (!entityCacheInvalid && entity instanceof ItemEntity && !nearbyItems.contains(entity))
            nearbyItems.add((ItemEntity) entity);
        if (!inputInventoryCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(inputBox()) && !nearbyInputInventoryEntities.contains(entity))
            nearbyInputInventoryEntities.add(entity);
        if (!outputInventoryCacheInvalid && entity instanceof Inventory && entity.getBoundingBox().intersects(outputBox()) && !nearbyOutputInventoryEntities.contains(entity))
            nearbyOutputInventoryEntities.add(entity);
    }

    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "onEntityCollided", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;insertAndExtract(Ljava/util/function/Supplier;)Z", shift = At.Shift.BEFORE))
    private void onEntityCollided1(Entity entity_1, CallbackInfo ci) {
        this.notifyOfNearbyEntity(entity_1);
    }

    @Feature("optimizedEntityHopperInteraction")
    @Redirect(method = "insert()Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/entity/HopperBlockEntity;getOutputInventory()Lnet/minecraft/inventory/Inventory;"))
    private Inventory getOutputInventoryFromCache(HopperBlockEntity hopper) {
        Inventory ret = getCachedBlockInventory(false);
        if (ret != null) return ret;

        if (!Settings.optimizedEntityHopperInteraction) return this.getOutputInventory();

        World world = hopper.getWorld();
        if (world == null) return null;

        Direction outputDirection = hopper.getCachedState().get(HopperBlock.FACING);
        Inventory inventory = getBlockInventoryAt(world, hopper.getPos().offset(outputDirection));
        if (inventory == null) {
            this.invalidateEntityCacheIfNeccessary();
            if (this.outputInventoryCacheInvalid) {
                BlockPos pos = this.pos.offset(outputDirection);
                double double_1 = pos.getX() + 0.5D;
                double double_2 = pos.getY() + 0.5D;
                double double_3 = pos.getZ() + 0.5D;
                this.nearbyOutputInventoryEntities = world.getEntities((Entity) null, new Box(double_1 - 0.5D, double_2 - 0.5D, double_3 - 0.5D, double_1 + 0.5D, double_2 + 0.5D, double_3 + 0.5D), EntityPredicates.VALID_INVENTORIES);
                this.outputInventoryCacheInvalid = false;
            }


            List<Entity> list_1 = this.nearbyOutputInventoryEntities;
            if (list_1.size() == 0)
                return null;
            list_1.removeIf((Entity inv) -> inv.removed || !inv.getBoundingBox().intersects(this.outputBox()));

            if (!list_1.isEmpty()) {
                return (Inventory) list_1.get(world.random.nextInt(list_1.size()));
            }
            return null;
        }
        return inventory;
    }

    private boolean hasToInvalidateEntityCache() {
        if (lastLazyChunkCheckTick == this.world.getTime() && lastLazyChunkCheckTick != -1) return false;
        lastLazyChunkCheckTick = this.world.getTime();
        return !doAllNearbyEntitiesTick();
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

    private void invalidateEntityCacheIfNeccessary() {
        if (EntityHopperInteraction.ruleUpdates != this.ruleUpdates || ruleUpdates == -1 || hasToInvalidateEntityCache()) {
            invalidateEntityHopperInteractionCache();
            this.ruleUpdates = EntityHopperInteraction.ruleUpdates;
        }
    }

    private void invalidateEntityHopperInteractionCache() {
        nearbyItems.clear();
        nearbyInputInventoryEntities.clear();
        nearbyOutputInventoryEntities.clear();

        entityCacheInvalid = true;
        inputInventoryCacheInvalid = true;
        outputInventoryCacheInvalid = true;
        ruleUpdates = -1;

        boxes = null;
        inputBox = null;
        outputBox = null;
    }
}
