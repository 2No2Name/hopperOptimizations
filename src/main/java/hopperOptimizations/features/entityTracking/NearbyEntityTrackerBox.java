package hopperOptimizations.features.entityTracking;

import me.jellysquid.mods.lithium.common.entity.tracker.nearby.ExactPositionListener;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.Collection;
import java.util.HashSet;

/**
 * Maintains a collection of all entities of a given type that collide with the box of this listener.
 * This allows hoppers and end gateways to quickly
 * assess nearby entities which match the provided class.
 *
 * @author 2No2Name
 */
public class NearbyEntityTrackerBox<T> implements ExactPositionListener {
    protected final Box box;
    final Class<T> clazz;
    private final Collection<T> withinBox;
    final int chunkX1, chunkY1, chunkZ1, chunkX2, chunkY2, chunkZ2;


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

        this.withinBox = this.createCollection();
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

    @Override
    @SuppressWarnings("unchecked")
    public void onEntityEnteredTrackedSubchunk(Entity entity) {
        if (!this.clazz.isInstance(entity) || this.subchunkContains((T) entity)) {
            return;
        }
        if (this.box.intersects(entity.getBoundingBox())) {
            this.addEntity((T) entity);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onEntityLeftTrackedSubchunk(Entity entity) {
        if (this.withinBox.isEmpty() || !this.clazz.isInstance(entity)) {
            return;
        }

        this.removeEntity((T) entity);
    }

    @Override
    @SuppressWarnings("unchecked")
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
}
