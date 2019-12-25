package hopperOptimizations.settings;

import carpet.settings.Rule;
import hopperOptimizations.utils.EntityHopperInteraction;

import static carpet.settings.RuleCategory.*;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
public class Settings {
    /**
     * Simple boolean Settings
     */
    @Rule(desc = "Optimized Inventory accesses - bloomfilters, cached BlockEntities and improved item transfers",
            category = {OPTIMIZATION, EXPERIMENTAL, "hopperoptimizations"})
    public static boolean optimizedInventories = false;

    @Rule(desc = "Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl.",
            category = {OPTIMIZATION, FEATURE, EXPERIMENTAL, "hopperoptimizations"})
    public static boolean simplifiedHopperPickupShape = false;

    @Rule(desc = "Reworked interaction between hoppers and entities. Entities look for hoppers instead of hoppers searching for entities.",
            category = {OPTIMIZATION, EXPERIMENTAL, "hopperoptimizations"}, validate = EntityHopperInteraction.class)
    public static boolean optimizedEntityHopperInteraction = false;

    @Rule(desc = "Removes check whether item entities have to move out of another entity (boat, shulker).",
            category = {OPTIMIZATION, FEATURE, EXPERIMENTAL, "hopperoptimizations"})
    public static boolean simplifiedItemElevatorCheck = false;

    @Rule(desc = "Speeds up checking whether an itemStack is empty by using cached information from vanilla.",
            category = {OPTIMIZATION, EXPERIMENTAL, "hopperoptimizations"})
    public static boolean optimizedItemStackEmptyCheck = false;

    @Rule(desc = "Doesn't do comparator updates when an item transfer fails unlike vanilla (when the transfer would have changed the signal strength)",
            category = {OPTIMIZATION, FEATURE, EXPERIMENTAL, "hopperoptimizations"})
    public static boolean failedTransferNoComparatorUpdates = false;

    /*
    @Rule(desc = "Don't disable optimized inventories when players interact with them.", category = {EXPERIMENTAL, "hopperoptimizations"})
    */ //This rule is causing problems when enabled. Inventories *should* panic when a player interacts with them until player handling code is added.
    public static boolean playerHopperOptimizations = false;


}
