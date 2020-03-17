package hopperOptimizations.workarounds;

import hopperOptimizations.annotation.Feature;

@Feature("inventoryCheckOnBlockUpdate")
public interface INoExtractInventoryUntilBlockUpdate {
    //interface to group inventories that are useless for hoppers to extract items from
    //the inventory must update the hopper when it becomes no longer useless (Composters do this)
}
