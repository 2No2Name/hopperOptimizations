package hopperOptimizations.settings;

@SuppressWarnings("CanBeFinal")
public class Settings {

    public static final boolean cacheInventories = true;

    //    @Rule(desc = "Can break contraptions: Removes check whether item entities have to move out of another entity (boat, shulker).",
//            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean simplifiedItemElevatorCheck = false;
    public static final boolean useEntityTrackerEngine = true;
    //    @Rule(desc = "Can break contraptions: Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl.",
//            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean simplifiedHopperPickupShape = false;
}
