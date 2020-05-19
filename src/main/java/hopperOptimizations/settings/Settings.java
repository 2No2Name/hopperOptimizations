package hopperOptimizations.settings;

import carpet.settings.Rule;
import hopperOptimizations.workarounds.EntityHopperInteraction;

import static carpet.settings.RuleCategory.FEATURE;
import static carpet.settings.RuleCategory.OPTIMIZATION;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
@SuppressWarnings("CanBeFinal")
public class Settings {

    @Rule(desc = "Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl.",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"}, validate = EntityHopperInteraction.class)
    //Using the EntityHopperInteraction counter to invalidate entity caches on pickup box change.
    public static boolean simplifiedHopperPickupShape = false;

    //@Rule(desc = "Reworked interaction between hoppers and entities. Entities look for hoppers instead of hoppers searching for entities.",
    //        category = {OPTIMIZATION, EXPERIMENTAL, "hopperoptimizations"}, validate = EntityHopperInteraction.class)
    public static final boolean optimizedEntityHopperInteraction = true;

    @Rule(desc = "Removes check whether item entities have to move out of another entity (boat, shulker).",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean simplifiedItemElevatorCheck = false;

    @Rule(desc = "Speeds up checking whether an itemStack is empty by using cached information from vanilla.",
            category = {OPTIMIZATION, "hopperoptimizations"})
    public static boolean optimizedItemStackEmptyCheck = false;

    @Rule(desc = "Doesn't do comparator updates when an item transfer fails unlike vanilla (when the transfer would have changed the signal strength)",
            category = {OPTIMIZATION, FEATURE, "hopperoptimizations"})
    public static boolean failedTransferNoComparatorUpdates = false;

    //@Rule(desc = "Disable optimized inventories when players interact with them. Not an optimization, for debugging only.", category = {"hopperoptimizations"})
    //public static final boolean playerInventoryDeoptimization = false;

    @Rule(desc = "Search for errors in optimized inventories on every known inventory modification. Prints errors in chat and resets the optimization state in case any error is detected. Massive lag, only enable for debugging. Partially requires assertions.", category = {"hopperoptimizations"})
    public static boolean debugOptimizedInventories = false;

    @Rule(desc = "Search for errors in optimized entity-hopper-interaction on every entity search. Prints errors in chat and resets the optimization state in case any error is detected. Massive lag, only enable for debugging.", category = {"hopperoptimizations"})
    public static boolean debugOptimizedEntityHopperInteraction = false;

    //@Rule(desc = "Make hoppers only check for newly created blockentities when receiving a block update. Requires optimizedEntityHopperInteraction.",
    //        category = {OPTIMIZATION, "hopperoptimizations"}, validate = EntityHopperInteraction.class)
    //Using the EntityHopperInteraction counter to invalidate entity caches on pickup box change.
    public static final boolean inventoryCheckOnBlockUpdate = true;


}
