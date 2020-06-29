package hopperOptimizations.features.entityTracking;

import it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyHopperItemsTracker extends NearbyEntityTrackerBox<ItemEntity> {
    private static final List<ItemEntity> EMPTY_LIST = new ArrayList<>(0);
    private static final VoxelShape inputAreaShape;
    private static final List<Box> boxes;

    static {
        inputAreaShape = new HopperBlockEntity().getInputAreaShape();
        boxes = inputAreaShape.getBoundingBoxes();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //Fields that are practically final after their first initialization
    private final HopperBlockEntity myHopper;
    private Box[] collectionArea;
    //Wrap our iterator in a list object, so we can return it in a mixin to the hopper.
    //The object is only used in an for(ItemEntity e : list) call, so we only need the iterator.
    //Other mods that want to use the list differently than vanilla might be surprised and will lead to a crash
    private int boxBits;
    private int chunkXZYBits;
    private long entitySubchunkCounterMaxValue;

    //Other fields

    //Set that contains all entities within the pickup area of the hopper
    //Entities sorted by integer keys so that the order of the values iterator is the order in which vanilla picks up items
    private Long2ObjectAVLTreeMap<ItemEntity> withinAreaSorted;
    //Keys are created so that a higher key means that the hopper will try to pick the item up earlier in vanilla
    //Negative keys mean that the entity does not collide with the pickup area.
    //Keys saved: - MSB (bit 1 << 31): whether the entity is outside the pickup area (1 for outside, 0 for inside)
    //            - Next bits for the box: 1 bit for which hopper pickup area box we are inside (different bit amount possible if box modded by other mod)
    //            - Bits after: 3 bits for the subchunk number, equivalent to the priority (different bit amount possible if box size modded by other mod)
    //            - All other bits for increasing counter to remember order in which entities entered their subchunk
    private Object2LongOpenHashMap<ItemEntity> withinSubchunksObjectToKey;
    private long entityChangedSubchunkCounter;

    //counter to be able to shortcut item transfer attempts when both hopper and possible items to pick up haven't changed
    private int newEntityCount;

    private boolean initialized = false;
    private boolean searchEntitiesAfterInitialization = false;
    private Box collectionAreaEnclosingBox;

    public NearbyHopperItemsTracker(BlockPos hopperPos, Hopper hopper) {
        super(ItemEntity.class);
        assert hopper instanceof HopperBlockEntity;
        this.myHopper = (HopperBlockEntity) hopper;
        this.init(hopperPos);
    }

    public Iterator<ItemEntity> getItemEntityIterator() {
        if (this.withinSubchunksObjectToKey == null || this.withinSubchunksObjectToKey.isEmpty()) {
            return EMPTY_LIST.iterator();
        }
        return this.withinAreaSorted.values().iterator();
    }

    private void init(BlockPos hopperPos) {
        //Calculate the box that the center of the entity is within exactly when it collides with the tracked box.
        //The only advantage this gives us is that we might not have to listen for extra chunks.
        //The values are also used in the collision checks but this only saves the addition of the entity size to the entity coordinates

        //prepare for later max/min operations
        this.chunkX1 = Integer.MAX_VALUE;
        this.chunkX2 = Integer.MIN_VALUE;
        this.chunkY1 = Integer.MAX_VALUE;
        this.chunkY2 = Integer.MIN_VALUE;
        this.chunkZ1 = Integer.MAX_VALUE;
        this.chunkZ2 = Integer.MIN_VALUE;
        //max/min operations in this call
        this.createPositionAndEntitySizeAdjustedBoxes(hopperPos);

        int chunkDx = this.chunkX2 - this.chunkX1;
        int chunkDy = this.chunkY2 - this.chunkY1;
        int chunkDz = this.chunkZ2 - this.chunkZ1;

        this.chunkXZYBits = 32 - Integer.numberOfLeadingZeros(Math.max(chunkDx, Math.max(chunkDy, chunkDz)));
        this.numSubchunks = (chunkDx + 1) * (chunkDy + 1) * (chunkDz + 1);


        if (this.chunkXZYBits > 1 || this.boxBits != 1) {
            if (this.chunkXZYBits * 3 + this.boxBits > 30) {
                //30+ bits for entity counter recommended. billions of items should be indexable at once, vanilla probably has a high or no limit besides lag!
                System.out.println("Hopper pickup area very complex or huge. This can lead to problems when many items gather on top of a hopper.");
            }
        }

        this.entitySubchunkCounterMaxValue = (1L << (63 - 1 - this.boxBits - 3 * this.chunkXZYBits)) - 1;
        this.entityChangedSubchunkCounter = 0; //any entity counter, unused until it is reset at initialization

    }

    private void createPositionAndEntitySizeAdjustedBoxes(BlockPos pos) {
        List<Box> boxes = this.myHopper.getInputAreaShape() == NearbyHopperItemsTracker.inputAreaShape
                ? NearbyHopperItemsTracker.boxes : this.myHopper.getInputAreaShape().getBoundingBoxes();

        //int widthHalfCeil = MathHelper.ceil(entityDimensions.width / 2D + 1e-7);
        this.collectionArea = new Box[boxes.size()];
        int i = 0;
        for (Box box : boxes) {
            double tmp;
            //Offset box to the hopper position like vanilla. Keep +0.5D-0.5D to preserve vanilla rounding errors
            //keep the box (vanilla creates it from scratch every time)
            double x1 = box.minX + (tmp = (pos.getX() + 0.5D - 0.5D));
            double x2 = box.maxX + tmp;
            double y1 = box.minY + (tmp = (pos.getY() + 0.5D - 0.5D));
            double y2 = box.maxY + tmp;
            double z1 = box.minZ + (tmp = (pos.getZ() + 0.5D - 0.5D));
            double z2 = box.maxZ + tmp;
            this.collectionArea[i] = new Box(x1, y1, z1, x2, y2, z2);
            ++i;

            //use vanilla listening box size, as otherwise lazy pushed entities might behave differently!
            this.chunkX1 = Math.min(this.chunkX1, (MathHelper.floor((x1 - 2.0D) / 16.0D)));
            this.chunkX2 = Math.max(this.chunkX2, (MathHelper.floor((x2 + 2.0D) / 16.0D)));
            this.chunkY1 = Math.min(this.chunkY1, (MathHelper.floor((y1 - 2.0D) / 16.0D)));
            this.chunkY2 = Math.max(this.chunkY2, (MathHelper.floor((y2 + 2.0D) / 16.0D)));
            this.chunkZ1 = Math.min(this.chunkZ1, (MathHelper.floor((z1 - 2.0D) / 16.0D)));
            this.chunkZ2 = Math.max(this.chunkZ2, (MathHelper.floor((z2 + 2.0D) / 16.0D)));
        }

        this.boxBits = 1;
        if (this.collectionArea.length != 2)
            this.boxBits = 32 - Integer.numberOfLeadingZeros(collectionArea.length - 1);
    }

    private void initCollection(boolean searchForEntities) {
        if (this.withinSubchunksObjectToKey == null) {
            this.withinSubchunksObjectToKey = new Object2LongOpenHashMap<>();
            this.withinSubchunksObjectToKey.defaultReturnValue(Long.MAX_VALUE);
            this.withinAreaSorted = new Long2ObjectAVLTreeMap<>();
        } else {
            withinAreaSorted.clear();
            withinSubchunksObjectToKey.clear();
        }
        this.entityChangedSubchunkCounter = 0;

        if (searchForEntities) {
            List<ItemEntity> entityList = HopperBlockEntity.getInputItemEntities(this.myHopper);
            for (ItemEntity entity : entityList) {
                this.onEntityEnteredTrackedSubchunk(entity);
            }
        }

    }

    @Override
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!(entity instanceof ItemEntity)) {
            return;
        } else if (!this.initialized) {
            this.searchEntitiesAfterInitialization = true;
            return;
        }
        if (this.withinSubchunksObjectToKey == null) {
            this.initCollection(false);
        } else if (this.entityChangedSubchunkCounter > this.entitySubchunkCounterMaxValue) {
            //this.initCollection(true);
            //todo all kinds of stuff to make sure this.withinSubchunksObjectToKey is consistent
        }
        long totalIndex = 0;
        long boxIndex = this.getBoxIndex(entity);
        if (boxIndex <= -1) {
            totalIndex |= 0x8000000000000000L; //set MSB because entity is not inside area
        } else {
            totalIndex |= boxIndex << (63 - this.boxBits);
        }
        long subChunkIndex = this.getSubchunkIndex(entity);
        totalIndex |= subChunkIndex << (63 - this.boxBits - 3 * this.chunkXZYBits);
        totalIndex |= this.entityChangedSubchunkCounter;
        this.entityChangedSubchunkCounter++;

        this.withinSubchunksObjectToKey.put((ItemEntity) entity, totalIndex);
        if (boxIndex >= 0) {
            this.withinAreaSorted.put(totalIndex, (ItemEntity) entity);
            this.newEntityCount++;
        }
    }

    @Override
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (!(entity instanceof ItemEntity) || this.withinSubchunksObjectToKey == null) {
            return;
        }
        long b = this.withinSubchunksObjectToKey.getLong(entity);
        this.removeEntity((ItemEntity) entity, b);
    }

    @Override
    public void onEntityMovedAnyDistance(Entity entity) {
        if (!(entity instanceof ItemEntity) || this.withinSubchunksObjectToKey == null) {
            return;
        }
        final long oldPriority = this.withinSubchunksObjectToKey.getLong(entity);
        boolean wasInside = (oldPriority & (1L << 63)) == 0;
        long newBoxIndex = this.getBoxIndex(entity);
        if (newBoxIndex >= 0) {
            long prevBoxIndex = (oldPriority & ~(1L << 63)) >> (63 - this.boxBits);
            if (wasInside && prevBoxIndex == newBoxIndex) {
                return; //was inside same box already
            }
            //entity newly entered a pickup area box
            //we only change the inside pickup area bit and the box index
            long newPriority = oldPriority & ((1L << (63 - this.boxBits)) - 1); //cut off inside bit and boxBits
            newPriority |= newBoxIndex << (1L << (63 - this.boxBits)); //attach new inside bit (0) and boxBits
            this.withinSubchunksObjectToKey.put((ItemEntity) entity, newPriority);
            this.withinAreaSorted.put(newPriority, (ItemEntity) entity);
            if (oldPriority < 0) {
                this.newEntityCount++;
            }
            return;
        }

        if (oldPriority < 0) {
            return; //is outside and was outside area
        }
        //entity left the pickup area
        //we only change the inside pickup area bit and the box index
        long newPriority = oldPriority & ((1L << (63 - this.boxBits)) - 1); //cut off inside bit and boxBits
        newPriority |= 1L << 63; //attach inside bit (1 / not inside)
        //no box bit to attach, as the value is not valid anyways

        this.withinSubchunksObjectToKey.put((ItemEntity) entity, newPriority);
        this.withinAreaSorted.remove(oldPriority);
    }

    @Override
    protected void addEntity(ItemEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    private void addEntity(ItemEntity entity, long priority, boolean isNew) {
        this.withinAreaSorted.put(priority, entity);
        this.withinSubchunksObjectToKey.put(entity, priority);
        if (isNew) {
            ++this.newEntityCount;
        }
    }

    private void removeEntity(ItemEntity entity, long priority) {
        if ((priority & 0x8000000000000000L) == 0) {
            this.withinAreaSorted.remove(priority);
        }
        this.withinSubchunksObjectToKey.remove(entity, priority);
    }



    @Override
    protected void removeEntity(ItemEntity entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    Collection<ItemEntity> createCollection() {
        return null;
    }

    /**
     * The RETURN VALUE of this method is ONLY VALID WHEN onEntityEnteredTrackedSubchunk was just called!
     * If it wasn't just called, use Entity.chunkX/Y/Z instead of entity.getX/Y/Z >> 4
     *
     * @param entity the entity
     * @return index for the subchunk ordering. expected to be a 3 bit or 0 bit number unless another mod changed the collection area of the hopper
     */
    //sorted by LOWEST X, then LOWEST Z, then LOWEST Y first
    //return higher number for first / lower ones
    private int getSubchunkIndex(Entity entity) {
        int index = 0;
        //get the bits for x,z,y chunk position
        if (this.chunkXZYBits > 0) {
            int b = this.chunkX2 - (MathHelper.floor(entity.getX()) >> 4); //high for low chunkX index
            index |= b << (2 * this.chunkXZYBits);                         //stored in highest bits
            b = this.chunkZ2 - (MathHelper.floor(entity.getZ()) >> 4);     //high for low chunkZ index
            index |= b << (this.chunkXZYBits);                             //stored in the next bits
            b = this.chunkY2 - MathHelper.clamp(MathHelper.floor(entity.getY()) >> 4, 0, 15); //high for low chunkY index
            index |= b;                                                    //stored in the lowest bits
        }
        if (index < 0)
            throw new AssertionError();
        assert index < (1 << 3 * this.chunkXZYBits);

        return index;
    }

    /**
     * Gets the index of the first box the given entity collides with
     *
     * @param entity the entity
     * @return the index or -1 when not colliding with any box
     */
    private int getBoxIndex(Entity entity) {
        final Box entityBox = entity.getBoundingBox();
        for (int i = 0; i < this.collectionArea.length; ++i) {
            if (this.collectionArea[i].intersects(entityBox)) {
                return i;
            }
        }
        return -1;
    }

    //@Override
    public void onInitialEntitiesReceived() {
        this.initialized = true;
        if (this.searchEntitiesAfterInitialization) {
            this.initCollection(true);
        }
    }

    public int getNewEntityCounter() {
        return this.newEntityCount;
    }

    public Object[] getAllForDebug() {
        if (this.withinAreaSorted != null) {
            return this.withinAreaSorted.values().toArray();
        }
        return new Object[0];
    }
}
