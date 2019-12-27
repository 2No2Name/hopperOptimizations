# Hopper Optimizations Mod
Using Fabric Carpet Example Mod template.
## Features
Use /carpet to enable:
### optimizedInventories
Optimized Inventory accesses - bloomfilters, cached BlockEntities and improved item transfers. All mechanics should work the same as in vanilla, just with less lag.

### optimizedItemStackEmptyCheck
Speeds up checking whether an itemStack is empty by using cached information from vanilla. Mechanics like vanilla. 

### optimizedEntityHopperInteraction
Reworked interaction between hoppers and entities. Entities look for hoppers instead of hoppers searching for entities. This change should be barely detectable - the order in which items are picked up might be *slightly different from vanilla*. (For stationary items the order is mostly "oldest first", which is very similar to vanilla)

### simplifiedHopperPickupShape
Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl. This change is barely detectable in gameplay (requires entity moving into the collision box, e.g. in item elevators with a hopper inside, the item may be picked up slightly earlier) but still *non-vanilla behavior*.

### simplifiedItemElevatorCheck
Removes check whether item entities have to move out of another entity (boat, shulker), which causes item entities to lag less at the cost of no longer moving out of boats and shulkers. This change is easily detectable with redstone and therefore clearly *non-vanilla behavior*.

### failedTransferNoComparatorUpdates
Removes comparator updates when item transfers fail. This change is detectable with redstone and therefore clearly *non-vanilla behavior*.

## License

MIT
