package hopperOptimizations.utils.entitycache;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.HashSet;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyEntityTrackerBox<T> /*implements ExactPositionListener*/ {
    protected final Box box;
    final Class<T> clazz;
    private final Collection<T> withinBox;
    int chunkX1, chunkY1, chunkZ1, chunkX2, chunkY2, chunkZ2;
    int numSubchunks;
    private int rangeC;


    /**
     * @param clazz            class of relevant entities
     * @param box              hitbox of the tracked area
     * @param entityDimensions hitbox of the tracked entity type
     */
    public NearbyEntityTrackerBox(Class<T> clazz, Box box, EntityDimensions entityDimensions) {
        this.clazz = clazz;
        this.box = box;

        int widthHalfCeil = MathHelper.ceil(entityDimensions.width / 2D + 1e-7);
        this.chunkX1 = (MathHelper.floor(this.box.minX) - widthHalfCeil) >> 4;
        this.chunkX2 = (MathHelper.floor(this.box.maxX) + widthHalfCeil) >> 4;
        this.chunkY1 = (MathHelper.floor(this.box.minY) - MathHelper.ceil(entityDimensions.height + 1e-7)) >> 4;
        this.chunkY2 = (MathHelper.floor(this.box.maxY)) >> 4;
        this.chunkZ1 = (MathHelper.floor(this.box.minZ) - widthHalfCeil) >> 4;
        this.chunkZ2 = (MathHelper.floor(this.box.maxZ) + widthHalfCeil) >> 4;

        int chunkDx = this.chunkX2 - this.chunkX1;
        int chunkDy = this.chunkY2 - this.chunkY1;
        int chunkDz = this.chunkZ2 - this.chunkZ1;

        this.rangeC = Math.max(chunkDx, Math.max(chunkDy, chunkDz)) / 2; //Integer division intended
        this.numSubchunks = (chunkDx + 1) * (chunkDy + 1) * (chunkDz + 1);

        this.withinBox = this.createCollection();
    }

    public NearbyEntityTrackerBox(Class<T> clazz) {
        this.clazz = clazz;
        this.box = null;
        this.withinBox = null;
    }

    /**
     * Given c, find the double c2 (cutoff) so that x + d > c if and only if x >= c2 (in floating point arithmetic)
     *
     * @param c any finite double
     * @param d any finite float
     * @return the cutoff value c2 with x + d > c if and only if x > c2
     * @author 2No2Name
     */
    @Deprecated
    protected static double calculateCutoffSubtracted(double c, float d) {
        if (!Double.isFinite(c) || !Float.isFinite(d)) {
            throw new ArithmeticException();
        }

        double c2 = c - d;
        double step;

        if (c2 + d <= c) {
            double cutoffIncr = Math.nextUp(c2);
            if (cutoffIncr + d > c) {
                return c2;
            }
            step = Math.max(Double.MIN_VALUE, cutoffIncr - c2);
        } else {
            double cutoffDecr = Math.nextDown(c2);
            if (cutoffDecr + d <= c) {
                return cutoffDecr;
            }
            step = Math.min(-Double.MIN_VALUE, cutoffDecr - c2);

        }

        //do larger and larger steps towards the exact c2
        //less than 3000 iterations due to step reaching infinity at some point
        //usually 0 iterations
        while ((c2 + step) + d <= c) {
            c2 += step;
            if (Double.isFinite(step * 2)) {
                step *= 2;
            }
        }

        step /= 2;

        //less than 3000 iterations due to step reaching Double.MIN_VALUE and then 0 at some point
        //usually 0-400 iterations
        while (step != 0 && Math.nextAfter(c2, c) + d <= c) {
            if ((c2 + step) + d <= c) {
                c2 += step;
            }
            step /= 2;
        }

        if (c2 + d <= c && Math.nextUp(c2) + d > c) {
            return c2;
        }

        //never happens, unless the code above has a bug
        assert false;
        throw new ArithmeticException("[HopperOptimizations/2No2Name] Bug in calculateCutoffSubtracted with input " + Double.doubleToRawLongBits(c) + " " + Float.floatToRawIntBits(d));
    }

    Collection<T> createCollection() {
        return new HashSet<>();
    }

    protected void addEntity(T entity) {
        this.withinBox.add(entity);
    }

    protected void removeEntity(T entity) {
        this.withinBox.remove(entity);
    }

    protected boolean subchunkContains(T entity) {
        return this.withinBox.contains(entity);
    }

//    @Override
//    public boolean isSubchunkInRange(int x, int y, int z) {
//        return x >= this.chunkX1 && x <= this.chunkX2 && y >= this.chunkY1 && y <= this.chunkY2 && z >= this.chunkZ1 && z <= this.chunkZ2;
//    }

    //@Override
    public int getChunkRange() {
        //this is a workaround for the remove code not working if the radius is 0
        return Math.max(1, this.rangeC);
    }

    @SuppressWarnings("unchecked")
    //@Override
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!this.clazz.isInstance(entity) || this.subchunkContains((T) entity)) {
            return;
        }
        if (this.box.intersects(entity.getBoundingBox())) {
            this.addEntity((T) entity);
        }
    }

    @SuppressWarnings("unchecked")
    //@Override
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (this.withinBox.isEmpty() || !this.clazz.isInstance(entity)) {
            return;
        }

        this.removeEntity((T) entity);
    }

    @SuppressWarnings("unchecked")
    //@Override
    public void onEntityMovedAnyDistance(Entity entity) {
        if (!this.clazz.isInstance(entity)) {
            return;
        }

        if (this.box.intersects(entity.getBoundingBox())) {
            withinBox.add((T) entity);
        } else if (!withinBox.isEmpty()) {
            //noinspection RedundantCast
            withinBox.remove((T) entity);
        }
    }

//    @Override
//    public int subchunksInRange() {
//        return numSubchunks;
//    }

    public void registerToEntityTracker(World world) {
        int[] xs = new int[this.numSubchunks];
        int[] ys = new int[this.numSubchunks];
        int[] zs = new int[this.numSubchunks];

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
        //this.registerToEntityTrackerEngine(world, xs, ys, zs);
    }

    public void removeFromEntityTracker(World world) {
        //this.deregisterFromEntityTrackerEngine(world);
    }
}
