package hopperOptimizations.utils.entitycache;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyHopperInventoriesTracker extends NearbyEntityTrackerBox<Inventory> {

    //redundant datastructures to get the possibility of random access but also fast contains and remove
    private HashMap<Inventory, Integer> withinBox1;
    private ArrayList<Entity> withinBox2;

    public NearbyHopperInventoriesTracker(Class<Inventory> clazz, Box box, EntityDimensions entityDimensions) {
        super(clazz, box, entityDimensions);
    }

    public Entity getRandomInventoryEntity(Random random) {
        while (withinBox2.size() > 0) {
            Entity e = withinBox2.get(random.nextInt(withinBox2.size()));
            if (!e.isAlive()) {
                this.removeEntity((Inventory) e);
            } else {
                return e;
            }
        }
        return null;
    }

    @Override
    Collection<Inventory> createCollection() {
        withinBox2 = new ArrayList<>();
        withinBox1 = new HashMap<>();
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!(entity instanceof Inventory) || withinBox1.containsKey(entity)) {
            return;
        }
        if (this.box.intersects(entity.getBoundingBox())) {
            this.addEntity((Inventory) entity);
        }
    }

    @Override
    protected void addEntity(Inventory entity) {
        this.withinBox1.put(entity, this.withinBox2.size());
        this.withinBox2.add((Entity) entity);
    }

    @Override
    protected void removeEntity(Inventory entity) {
        int i = this.withinBox1.remove(entity);
        //swap around to get fast remove
        int arrayEndIndex = this.withinBox2.size() - 1;
        Inventory swapped = (Inventory) this.withinBox2.get(arrayEndIndex);
        this.withinBox2.set(i, (Entity) swapped);
        this.withinBox2.remove(arrayEndIndex);
        this.withinBox1.put(swapped, i);
    }

    protected boolean subchunkContains(Inventory entity) {
        return this.withinBox1.containsKey(entity);
    }

    @Override
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (this.withinBox1.isEmpty() || !(entity instanceof Inventory) || !withinBox1.containsKey(entity)) {
            return;
        }

        this.removeEntity((Inventory) entity);
    }

    @Override
    public void onEntityMovedAnyDistance(double prevX, double prevY, double prevZ, Entity entity) {
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
}
