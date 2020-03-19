package hopperOptimizations.utils.entitycache;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import org.apache.logging.log4j.LogManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyHopperItemsTracker extends NearbyEntityTrackerBox<ItemEntity> {
    private static List<ItemEntity> EMPTY_LIST = new ArrayList<>(0);
    private static boolean detectedOtherModdedChange = false;
    private static VoxelShape inputAreaShape;
    private static List<Box> boxes;

    static {
        inputAreaShape = new HopperBlockEntity().getInputAreaShape();
        boxes = inputAreaShape.getBoundingBoxes();
    }


    private HopperBlockEntity myHopper;
    private Box[] collectionArea;

    private Int2ObjectAVLTreeMap<ItemEntity> withinAreaSorted;
    private Object2IntOpenHashMap<ItemEntity> withinAreaObjectToKey;

    private int boxBits;
    private int chunkXZYBits;
    private int entityCounterMaxValue;
    private int entityCounter;

    private boolean hasToInitializeEntities = false;
    private boolean initialized = false;


    //Wrap our iterator in a list object, so we can return it in a mixin to the hopper.
    //The object is only used in an for(ItemEntity e : list) call, so we only need the iterator.
    //Other mods that want to use the list differently than vanilla might be surprised and will lead to a crash
    private ListIteratorWrapper<ItemEntity> iteratorWrapperList = new ListIteratorWrapper<>();

    public NearbyHopperItemsTracker(BlockPos hopperPos, HopperBlockEntity hopper) {
        super(ItemEntity.class);
        this.myHopper = hopper;
        this.init(hopperPos);
    }

    public List<ItemEntity> getUseOnceIteratorWrapperList() {

        //Either uncomment the following code or keep MixinItemEntity that sets the stack of dead item entities to EMPTY
        /*
        //Filter out non alive entities, no way to not have to do it, as another hopper could have just killed some entity
        //The alternative is making removed item entites set themselves to have an empty stack
        ObjectIterator<Object2IntMap.Entry<ItemEntity>> it = this.withinAreaObjectToKey.object2IntEntrySet().iterator();
        while (it.hasNext()) {
            Object2IntMap.Entry<ItemEntity> e = it.next();
            if (!e.getKey().isAlive()){
                this.withinAreaSorted.remove(e.getIntValue());
                it.remove();
            }
        }
        */

        this.iteratorWrapperList.setWrappedIterator(this.withinAreaSorted.values().iterator());
        return this.iteratorWrapperList;
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

        this.chunkXZYBits = 32 - Integer.numberOfLeadingZeros(Math.max(chunkDx, Math.max(chunkDy, chunkDz)) - 1);
        this.numSubchunks = (chunkDx + 1) * (chunkDy + 1) * (chunkDz + 1);


        if (this.chunkXZYBits > 1 || this.boxBits != 1) {
            //if (!detectedOtherModdedChange) {
            //    //System.out.println("Unexpected hopper pickup area, different from vanilla, but likely manageable.");
            //    detectedOtherModdedChange = true;
            //}
            if (this.chunkXZYBits * 3 + this.boxBits > 22) {
                System.out.println("Hopper pickup area very complex or huge. This can lead to problems.");
            }
        }

        this.entityCounterMaxValue = (1 << (30 - this.boxBits - 3 * this.chunkXZYBits)) - 1; //using 30 instead of 31 to not have to deal with negative
        this.entityCounter = 0; //any entity counter, unused until it is reset at initialization

    }

    private void createPositionAndEntitySizeAdjustedBoxes(BlockPos pos) {
        EntityDimensions entityDimensions = EntityType.ITEM.getDimensions();
        List<Box> boxes = this.myHopper.getInputAreaShape() == NearbyHopperItemsTracker.inputAreaShape
                ? NearbyHopperItemsTracker.boxes : this.myHopper.getInputAreaShape().getBoundingBoxes();

        int widthHalfCeil = MathHelper.ceil(entityDimensions.width / 2D + 1e-7);
        this.collectionArea = new Box[boxes.size()];
        int i = 0;
        for (Box box : boxes) {
            double tmp;
            //Offset box to the hopper position like vanilla. Keep +0.5D-0.5D to preserve vanilla rounding errors
            //keep the box (vanilla creates it from scratch every time)
            double x1 = box.x1 + (tmp = (pos.getX() + 0.5D - 0.5D));
            double x2 = box.x2 + tmp;
            double y1 = box.y1 + (tmp = (pos.getY() + 0.5D - 0.5D));
            double y2 = box.y2 + tmp;
            double z1 = box.z1 + (tmp = (pos.getZ() + 0.5D - 0.5D));
            double z2 = box.z2 + tmp;
            this.collectionArea[i] = new Box(x1, y1, z1, x2, y2, z2);
            ++i;

            this.chunkX1 = Math.min(this.chunkX1, (MathHelper.floor(box.x1) - widthHalfCeil) >> 4);
            this.chunkX2 = Math.max(this.chunkX2, (MathHelper.floor(box.x2) + widthHalfCeil) >> 4);
            this.chunkY1 = Math.min(this.chunkY1, (MathHelper.floor(box.y1) - MathHelper.ceil(entityDimensions.height + 1e-7)) >> 4);
            this.chunkY2 = Math.max(this.chunkY2, (MathHelper.floor(box.y2)) >> 4);
            this.chunkZ1 = Math.min(this.chunkZ1, (MathHelper.floor(box.z1) - widthHalfCeil) >> 4);
            this.chunkZ2 = Math.max(this.chunkZ2, (MathHelper.floor(box.z2) + widthHalfCeil) >> 4);
        }

        this.boxBits = 1;
        if (this.collectionArea.length != 2)
            this.boxBits = 32 - Integer.numberOfLeadingZeros(collectionArea.length - 1);
    }

    private void initCollection(boolean searchForEntities) {
        if (this.withinAreaObjectToKey == null) {
            this.withinAreaObjectToKey = new Object2IntOpenHashMap<>();
            this.withinAreaObjectToKey.defaultReturnValue(Integer.MAX_VALUE);
            this.withinAreaSorted = new Int2ObjectAVLTreeMap<>();
        } else {
            withinAreaSorted.clear();
            withinAreaObjectToKey.clear();
        }
        this.entityCounter = 0;

        if (searchForEntities) {
            List<ItemEntity> entityList = HopperBlockEntity.getInputItemEntities(this.myHopper);
            int previousPriority = Integer.MAX_VALUE;
            for (ItemEntity entity : entityList) {
                int priority = this.getPriorityNumber(entity, true, Integer.MAX_VALUE);
                if (priority >= previousPriority) {
                    LogManager.getLogger().warn("[Lithium/2No2Name] Hopper item pickup order is different from vanilla.");
                }
                if (priority != Integer.MAX_VALUE) {
                    this.addEntity(entity, priority);
                }
            }
        }

    }

    @Override
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!(entity instanceof ItemEntity) || (!this.initialized && this.hasToInitializeEntities)) {
            return;
        }
        int b = this.withinAreaObjectToKey == null ? Integer.MAX_VALUE : this.withinAreaObjectToKey.getInt(entity);
        if (b != Integer.MAX_VALUE) {
            this.withinAreaSorted.remove(b);
            this.withinAreaObjectToKey.remove(entity, b);
        }
        int priority = getPriorityNumber((ItemEntity) entity, true, b);

        if (priority != Integer.MAX_VALUE) {
            if (this.withinAreaObjectToKey == null) {
                if (this.initialized) {
                    this.initCollection(false);
                } else {
                    this.hasToInitializeEntities = true;
                    return; //don't accept hashmap iterator ordered item entities, use our own initialization later
                }
            }
            this.addEntity((ItemEntity) entity, b);
        }
    }

    @Override
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (!(entity instanceof ItemEntity) || this.withinAreaObjectToKey == null) {
            return;
        }
        int b = this.withinAreaObjectToKey.getInt(entity);
        if (b != Integer.MAX_VALUE) {
            this.withinAreaSorted.remove(b);
            this.withinAreaObjectToKey.remove(entity, b);
        }
    }

    @Override
    public void onEntityMovedAnyDistance(double prevX, double prevY, double prevZ, Entity entity) {
        if (!(entity instanceof ItemEntity) || this.withinAreaObjectToKey == null) {
            return;
        }
        int p = this.withinAreaObjectToKey.getInt(entity);
        if (p != Integer.MAX_VALUE) {
            int q = this.getPriorityNumber((ItemEntity) entity, false, p);
            if (p != q) {
                this.withinAreaSorted.remove(p);
                if (q == Integer.MAX_VALUE) {
                    this.withinAreaObjectToKey.remove(entity, q);
                } else {
                    this.withinAreaSorted.put(q, (ItemEntity) entity);
                    this.withinAreaObjectToKey.put((ItemEntity) entity, q);
                }
            }
        }
    }

    @Override
    protected void addEntity(ItemEntity entity) {
        throw new UnsupportedOperationException();
    }

    private void addEntity(ItemEntity entity, int priority) {
        this.withinAreaSorted.put(priority, entity);
        this.withinAreaObjectToKey.put(entity, priority);
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
     * Generate a number for each entity so that when the entities are sorted by their number, the sorting is
     * like the order in which hoppers pick up items in vanilla.
     *
     * @param entity    the entity we need an index of
     * @param forceNew  if we need to get a new index, for example when the entity changed the chunk section
     * @param oldNumber previous number of the entity, can possibly be reused
     * @return priority number, Integer.MAX_VALUE if no priority can be assigned because the entity is not in the tracked area
     */
    private int getPriorityNumber(ItemEntity entity, boolean forceNew, int oldNumber) {
        if (!forceNew && this.withinAreaObjectToKey != null && this.withinAreaObjectToKey.containsKey(entity)) {
            return this.withinAreaObjectToKey.getInt(entity);
        }

        int oldBoxIndexShifted;
        oldNumber = -(oldNumber - Integer.MAX_VALUE);
        //                                |---mask for boxBits----|    shift pos like in loop
        oldBoxIndexShifted = oldNumber & (((1 << this.boxBits) - 1) << (31 - this.boxBits));


        int priority = 0;
        final Box entityBox = entity.getBoundingBox();
        //get the highest bits of the priority for the box the entity collides with, as this is the primary sorting key
        boolean inArea = false;
        for (int i = 0; i < this.collectionArea.length; ++i) {
            if (this.collectionArea[i].intersects(entityBox)) {
                priority = i << (31 - this.boxBits); //using 31 instead of 32 to not have to deal with negative numbers
                inArea = true;
                break;
            }
        }
        if (!inArea) {
            return Integer.MAX_VALUE;
        } //MAX_VALUE: code for "not in area", won't be returned otherwise
        //if (this.boxBits == 0) { priority = 0; } //redundant as i in for-loop is 0 when boxBits is 0

        if (!this.initialized) {
            return 1; //return anything but not MAX_VALUE. The entity is not going to be put in the list yet, but at initialization time
        }


        if (oldNumber != 0 && oldBoxIndexShifted != priority) {
            forceNew = true;
        }

        //get the next bits for x,z,y chunk position
        if (this.chunkXZYBits > 0) {
            int b = (this.chunkX2 - MathHelper.floor(entity.getX()) >> 4);
            priority |= b << (31 - this.boxBits - this.chunkXZYBits);
            b = (this.chunkY2 - MathHelper.floor(entity.getY()) >> 4);
            priority |= b << (31 - this.boxBits - (2 * this.chunkXZYBits));
            b = (this.chunkZ2 - MathHelper.floor(entity.getZ()) >> 4);
            priority |= b << (31 - this.boxBits - (3 * this.chunkXZYBits));
        }

        if (oldNumber != Integer.MAX_VALUE && !forceNew) {
            //keep the old entitycounter value for the entity,
            return Integer.MAX_VALUE - ((oldNumber & (-1 >>> (1 + this.boxBits + 3 * this.chunkXZYBits))) | priority);
        }

        this.entityCounter++;
        if (this.entityCounter >= this.entityCounterMaxValue) {
            this.initCollection(true); //resets the collection
            if (this.withinAreaObjectToKey.containsKey(entity)) {
                return this.withinAreaObjectToKey.getInt(entity);
            } else {
                return Integer.MAX_VALUE;
            }
        }

        priority += this.entityCounter; //priority >= 1, as entityCounter++ before

        return Integer.MAX_VALUE - priority; //backwards, as the smallest number is first in our datastructure
    }

    @Override
    public void onInitialEntitiesReceived() {
        this.initialized = true;
        if (this.hasToInitializeEntities) {
            this.initCollection(true);
        }
    }
}
