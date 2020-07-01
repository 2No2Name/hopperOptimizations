package hopperOptimizations.settings;

import carpet.settings.Rule;

import static carpet.settings.RuleCategory.FEATURE;
import static carpet.settings.RuleCategory.OPTIMIZATION;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
@SuppressWarnings("CanBeFinal")
public class Settings {

    @Rule(desc = "Can break contraptions: Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl.",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean simplifiedHopperPickupShape = false;

    @Rule(desc = "Can break contraptions: Removes check whether item entities have to move out of another entity (boat, shulker).",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean simplifiedItemElevatorCheck = false;

    @Rule(desc = "Can break contraptions: Doesn't do comparator updates when an item transfer fails unlike vanilla (when the transfer would have changed the signal strength)",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean failedTransferNoComparatorUpdates = false;

    @Rule(desc = "Search for errors in optimized inventories on every known inventory modification. Prints errors in chat and resets the optimization state in case any error is detected. Massive lag, only enable for debugging. Partially requires assertions.", category = {"hopperoptimizations"})
    public static boolean debugOptimizedInventories = false;

    @Rule(desc = "Search for errors in optimized entity-hopper-interaction on every entity search. Prints errors in chat and resets the optimization state in case any error is detected. Massive lag, only enable for debugging.", category = {"hopperoptimizations"})
    public static boolean debugOptimizedEntityHopperInteraction = false;
}
