package hopperOptimizations.settings;

@SuppressWarnings("CanBeFinal")
public class Settings {

    public static final boolean cacheInventories = true;
    public static final boolean useEntityTrackerEngine;

    static {
        boolean present = true;
        try {
            Class.forName("me.jellysquid.mods.lithium.common.entity.tracker.nearby.ExactPositionListener");
        } catch (ClassNotFoundException e) {
            System.out.println("Hopper Optimizations did not find Lithium with ExactPositionListeners. Disabling optimization!");
            present = false;
        }
        useEntityTrackerEngine = present;
    }
}
