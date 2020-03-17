package hopperOptimizations.workarounds;

import hopperOptimizations.annotation.Feature;

@Feature("inventoryCheckOnBlockUpdate")
public interface IValidInventoryUntilBlockUpdate {
    //interface to group inventories that are inventories of blocks and not blockentities
    //the inventory must update the hopper when it is no longer valid (Composters do this)
}
