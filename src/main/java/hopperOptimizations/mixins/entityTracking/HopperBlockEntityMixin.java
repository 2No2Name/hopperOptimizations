package hopperOptimizations.mixins.entityTracking;

import hopperOptimizations.features.entityTracking.NearbyHopperInventoriesTracker;
import hopperOptimizations.features.entityTracking.NearbyHopperItemsTracker;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.HopperHelper;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.inventoryOptimizer.OptimizedInventory;
import hopperOptimizations.workarounds.HopperWithClearableCaches;
import hopperOptimizations.workarounds.Interfaces;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Iterator;
import java.util.List;

@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin extends LootableContainerBlockEntity implements Interfaces.HopperWithEntityInventoryCache, HopperWithClearableCaches {
    @Shadow
    private long lastTickTime;
    @Shadow
    private DefaultedList<ItemStack> inventory;
    private NearbyHopperInventoriesTracker inputInventoryEntities;
    private NearbyHopperInventoriesTracker outputInventoryEntities;
    private NearbyHopperItemsTracker inputItemEntities;

    private long lastTickTime_used_InputInventoryEntityCache;
    private long lastTickTime_used_OutputInventoryEntityCache;


    //change counter value at the last time the input area was checked
    //counter of the input area
    private int inputItemEntities_changeCount;
    //counter of this hopper
    private int this_lastChangeCount_Pickup;
    private long lastTickTime_used_ItemEntityCache;


    protected HopperBlockEntityMixin(BlockEntityType<?> blockEntityType) {
        super(blockEntityType);
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

        if (!Settings.debugOptimizedEntityHopperInteraction) {
            ((HopperBlockEntityMixin) hopper).optimizeItemPickup(hopper, cir);
        } else {
            try {
                List<ItemEntity> itemEntities = HopperBlockEntity.getInputItemEntities(hopper);
                ItemEntity pickedUp = ((HopperBlockEntityMixin) hopper).optimizeItemPickup(hopper, cir);
                if (pickedUp == null) {
                    pickedUp = HopperHelper.vanillaPickupItem(hopper, itemEntities.iterator());
                    if (pickedUp != null)
                        throw new IllegalStateException("HopperOptimizations picked up no item even though vanilla could pick up: " + pickedUp.toString());
                } else if (!itemEntities.contains(pickedUp))
                    throw new IllegalStateException("HopperOptimizations picked up an item that vanilla couldn't pick up: " + pickedUp.toString());
            } catch (IllegalStateException e) {
                System.out.println("Detected wrong entity hopper interaction ( " + e.getMessage() + ")!");
            }
        }
    }

    @Override
    public void clearOutputInventoryEntityCache(long timelimit) {
        if (this.lastTickTime_used_OutputInventoryEntityCache <= timelimit && this.outputInventoryEntities != null) {
            outputInventoryEntities.removeFromEntityTracker(this.world);
            outputInventoryEntities = null;
        }
    }

    @Override
    public void clearInputInventoryEntityCache(long timelimit) {
        if (this.lastTickTime_used_InputInventoryEntityCache <= timelimit && this.inputInventoryEntities != null) {
            this.inputInventoryEntities.removeFromEntityTracker(this.world);
            this.inputInventoryEntities = null;
        }
    }

    @Override
    public void clearInputItemEntityCache(long timeLimit) {
        if (this.inputItemEntities != null && this.lastTickTime_used_ItemEntityCache < timeLimit) {
            this.inputItemEntities.removeFromEntityTracker(this.world);
            this.inputItemEntities = null;
        }
    }

    @Override
    public Inventory getOutputEntityInventoryWithCache(HopperBlockEntity hopperBlockEntity) {
        if (this.outputInventoryEntities == null) {
            this.outputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class, this.outputBox(), EntityType.CHEST_MINECART.getDimensions());
            this.outputInventoryEntities.registerToEntityTracker(this.world);
        }
        this.lastTickTime_used_OutputInventoryEntityCache = this.lastTickTime;

        if (Settings.debugOptimizedEntityHopperInteraction) {
            BlockPos pos = this.pos.offset(this.getDir());
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + 0.5D;
            double z = pos.getZ() + 0.5D;
            //noinspection ConstantConditions
            HopperHelper.debugCompareInventoryEntities(this.outputInventoryEntities, this.world, x, y, z);
        }
        //noinspection ConstantConditions
        return this.outputInventoryEntities.getRandomInventoryEntity(this.world.random);
    }

    @Override
    public Inventory getInputEntityInventoryWithCache(HopperBlockEntity hopperBlockEntity) {
        if (this.inputInventoryEntities == null) {
            this.inputInventoryEntities = new NearbyHopperInventoriesTracker(Inventory.class, this.inputBox(), EntityType.CHEST_MINECART.getDimensions());
            this.inputInventoryEntities.registerToEntityTracker(this.world);
        }
        this.lastTickTime_used_InputInventoryEntityCache = this.lastTickTime;

        if (Settings.debugOptimizedEntityHopperInteraction) {
            //noinspection ConstantConditions
            HopperHelper.debugCompareInventoryEntities(this.inputInventoryEntities, this.world, hopperBlockEntity.getHopperX(), hopperBlockEntity.getHopperY() + 1.0D, hopperBlockEntity.getHopperZ());
        }

        //noinspection ConstantConditions
        return this.inputInventoryEntities.getRandomInventoryEntity(this.world.random);
    }

    //todo replace with cached version
    private Direction getDir() {
        return this.getCachedState().get(HopperBlock.FACING);
    }

    private Box outputBox() {
        Direction direction = this.getDir();
        return new Box(this.pos.offset(direction));
    }

    private Box inputBox() {
        return new Box(this.pos.up());
    }

    private ItemEntity optimizeItemPickup(Object hopperBlockEntity, CallbackInfoReturnable<Boolean> cir) {
        final InventoryOptimizer opt = ((OptimizedInventory) hopperBlockEntity).getOptimizer(true);
        if (opt == null) {
            return null; //fallback to vanilla
        }

        //fix the entity cache in case it is not initialized
        if (this.inputItemEntities == null) {
            //keep a set of reachable items to be faster next time
            this.inputItemEntities = new NearbyHopperItemsTracker(this.pos, (Hopper) hopperBlockEntity);
            this.inputItemEntities.registerToEntityTracker(this.world);
            this.this_lastChangeCount_Pickup = 0;
        }
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
}
