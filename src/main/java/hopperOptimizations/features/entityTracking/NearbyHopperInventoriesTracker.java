package hopperOptimizations.features.entityTracking;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.ExactPositionListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Random;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyHopperInventoriesTracker implements ExactPositionListener {
    protected final Box box;
    final Class<Inventory> clazz;
    //redundant datastructures to get the possibility of random access but also fast contains and remove
    private final Reference2IntOpenHashMap<Inventory> withinBox1;
    private final ArrayList<Entity> withinBoxArrayList;
    int chunkX1, chunkY1, chunkZ1, chunkX2, chunkY2, chunkZ2;

    public NearbyHopperInventoriesTracker(Class<Inventory> clazz, Box box, EntityDimensions maxEntityDimensions) {
        this.clazz = clazz;
        this.box = box;

        int widthHalfCeil = MathHelper.ceil(maxEntityDimensions.width / 2D + 1e-7);
        this.chunkX1 = (MathHelper.floor(this.box.minX) - widthHalfCeil) >> 4;
        this.chunkX2 = (MathHelper.floor(this.box.maxX) + widthHalfCeil) >> 4;
        this.chunkY1 = (MathHelper.floor(this.box.minY) - MathHelper.ceil(maxEntityDimensions.height + 1e-7)) >> 4;
        this.chunkY2 = (MathHelper.floor(this.box.maxY)) >> 4;
        this.chunkZ1 = (MathHelper.floor(this.box.minZ) - widthHalfCeil) >> 4;
        this.chunkZ2 = (MathHelper.floor(this.box.maxZ) + widthHalfCeil) >> 4;

        this.withinBoxArrayList = new ArrayList<>();
        this.withinBox1 = new Reference2IntOpenHashMap<>();
    }

    public Inventory getRandomInventoryEntity(Random random) {
        if (this.withinBoxArrayList == null) {
            return null;
        }
        while (this.withinBoxArrayList.size() > 0) {
            Entity e = this.withinBoxArrayList.get(random.nextInt(this.withinBoxArrayList.size()));
            if (!e.isAlive()) {
                this.removeEntity((Inventory) e);
            } else {
                return (Inventory) e;
            }
        }
        return null;
    }

    @Override
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!(entity instanceof Inventory) || this.withinBox1.containsKey(entity)) {
            return;
        }
        if (this.box.intersects(entity.getBoundingBox())) {
            this.addEntity((Inventory) entity);
        }
    }

    protected void addEntity(Inventory entity) {
        this.withinBox1.put(entity, this.withinBoxArrayList.size());
        this.withinBoxArrayList.add((Entity) entity);
    }

    protected void removeEntity(Inventory entity) {
        int i = this.withinBox1.removeInt(entity);
        //swap around to get constant time remove in arraylist. Order in list does not matter
        int arrayEndIndex = this.withinBoxArrayList.size() - 1;
        Inventory swapped = (Inventory) this.withinBoxArrayList.remove(arrayEndIndex);
        if (i < arrayEndIndex) {
            this.withinBoxArrayList.set(i, (Entity) swapped);
            this.withinBox1.put(swapped, i);
        }
    }

    @Override
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (this.withinBox1.isEmpty() || !(entity instanceof Inventory) || !withinBox1.containsKey(entity)) {
            return;
        }

        this.removeEntity((Inventory) entity);
    }

    @Override
    public void onEntityMovedAnyDistance(Entity entity) {
        if (!(entity instanceof Inventory)) {
            return;
        }
        boolean contains = this.withinBox1.containsKey(entity);

        if (this.box.intersects(entity.getBoundingBox())) {
            if (!contains) {
                this.addEntity((Inventory) entity);
            }
        } else if (contains) {
            this.removeEntity((Inventory) entity);
        }
    }


    public void registerToEntityTracker(World world) {
        int chunkDx = this.chunkX2 - this.chunkX1;
        int chunkDy = this.chunkY2 - this.chunkY1;
        int chunkDz = this.chunkZ2 - this.chunkZ1;
        int numSubchunks = (chunkDx + 1) * (chunkDy + 1) * (chunkDz + 1);

        int[] xs = new int[numSubchunks];
        int[] ys = new int[numSubchunks];
        int[] zs = new int[numSubchunks];

        int i = 0;
        for (int x = this.chunkX1; x <= this.chunkX2; x++) {
            for (int y = this.chunkY1; y <= this.chunkY2; y++) {
                for (int z = this.chunkZ1; z <= this.chunkZ2; z++) {
                    xs[i] = x;
                    ys[i] = y;
                    zs[i] = z;
                    i++;
                }
            }
        }
        this.registerToEntityTrackerEngine(world, xs, ys, zs);
    }

    public void removeFromEntityTracker(World world) {
        this.deregisterFromEntityTrackerEngine(world);
    }
}
