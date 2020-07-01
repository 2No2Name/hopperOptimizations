# Hopper Optimizations Mod
This Mod optimizes hoppers interacting with inventories and item entities. Only modifications that only optimize the game without
 changing any behavior ingame are enabled by default. This branch of hopper optimizations requires installing 2No2Name's fork of Lithium.
 
## Enabled Features (partially toggleable in the future)
### Inventory Optimizer
Inventories keep extra data (modification counter, item type -> slot mask lookup table, empty & full slot masks) to be able to skip searching though the whole inventory
### Entity Tracking
Hoppers use Lithium's entity tracker engine (adapted in 2No2Name's fork of Lithium) to avoid searching for entities (items + inventory minecarts)
### Cache Inventories
Hoppers keep a reference to the inventory blocks (block entities or composters) they interact with to avoid retrieving them from the world all the time.
### Fast Signal Strength
Comparators use the extra data from Inventory Optimizer (weighted item counter) to determine the signal strength of an inventory without accessing its slots.
### Use cached empty state of Item stacks
Item stacks cache whether they are empty in vanilla, but at the same time the value is recalculated often for no reason. Instead always use the cached value.

## Disabled Features (toggleable in the future)
These optimizations have effects that are detectable ingame, so they might break contraptions and are disabled until a toggle system is added.
### simplifiedHopperPickupShape
Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl. This change is barely detectable in gameplay (requires entity moving into the collision box, e.g. in item elevators with a hopper inside, the item may be picked up slightly earlier) but still *non-vanilla behavior*.

### simplifiedItemElevatorCheck
Removes check whether item entities have to move out of another entity (boat, shulker), which causes item entities to lag less at the cost of no longer moving out of boats and shulkers. This change is easily detectable with redstone and therefore clearly *non-vanilla behavior*.

### failedTransferNoComparatorUpdates
Removes comparator updates when item transfers fail. This change is detectable with redstone and therefore clearly *non-vanilla behavior*.

## License

MIT
