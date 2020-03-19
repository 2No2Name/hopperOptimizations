package hopperOptimizations.utils.entitycache;

import net.minecraft.entity.Entity;

/**
 * The main interface used to receive events from the
 * {@link me.jellysquid.mods.lithium.common.entity.tracker.EntityTrackerEngine} of a world.
 */
public interface NearbyEntityListener {
    /**
     * Returns the range (in blocks) of this listener. This must never change during the lifetime of the listener.
     * TODO: Allow entity listeners to change the radius they receive updates within
     */
    int getChunkRange();

    /**
     * Whether this Listener listens to the given subchunk if it is already within getChunkRange of this listener.
     * This function must never change during the lifetime of the listener.
     */
    default boolean isSubchunkInRange(int x, int y, int z) {
        return true;
    }

    /**
     * Called by the entity tracker when an entity enters the range of this listener.
     */
    void onEntityEnteredTrackedSubchunk(Entity entity);

    /**
     * Called by the entity tracker when an entity leaves the range of this listener or is removed from the world.
     */
    void onEntityLeftTrackedSubchunk(Entity entity);

    /**
     * Called by the Entity tracker engine when it added all the initial entities to the tracker when it is new or moved.
     */
    default void onInitialEntitiesReceived() {
    }
}
