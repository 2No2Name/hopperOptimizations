package hopperOptimizations.utils.entitycache;

import net.minecraft.entity.Entity;

/**
 *
 */
public interface ExactPositionListener extends NearbyEntityListener {

    /**
     * How many subchunks are in range.
     * Must be equal to the number of inputs to isSubchunkInRange that return true
     */
    int subchunksInRange();

    /**
     * Called by the entity tracker when an entity moves inside the range of this listener.
     */
    void onEntityMovedAnyDistance(double prevX, double prevY, double prevZ, Entity entity);

}
