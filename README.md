# Hopper Optimizations Mod
This Mod optimizes hoppers interacting with inventories and item entities. All modifications are toggleable and
 off by default. The most performance improving settings are optimizedInventories and optimizedEntityHopperInteraction.
 All other features are also recommended.
 
 Be aware that optimizedEntityHopperInteraction, simplifiedHopperPickupShape and simplifiedItemElevatorCheck are 
 detectable with redstone contraptions and therefore might make your contraption work differently or stop working.
 In case you suspect one of these features to be the problem, turn it off to immediately restore vanilla behavior.
## Features
Use `/carpet <rulename> true` to enable:
### optimizedInventories
Optimized Inventory accesses - bloomfilters, cached BlockEntities and improved item transfers. All mechanics should work the same as in vanilla, just with less lag.

### optimizedEntityHopperInteraction
Reworked interaction between hoppers and entities. Entities look for hoppers instead of hoppers searching for entities. This change should be barely detectable - the order in which items are picked up might be *slightly different from vanilla*. (For stationary items the order is mostly "oldest first", which is very similar to vanilla)

### optimizedItemStackEmptyCheck
Speeds up checking whether an itemStack is empty by using cached information from vanilla. Mechanics like vanilla. 

### simplifiedHopperPickupShape
Simplified hopper box shape when picking up items. This box contains the ring around the hopper's bowl. This change is barely detectable in gameplay (requires entity moving into the collision box, e.g. in item elevators with a hopper inside, the item may be picked up slightly earlier) but still *non-vanilla behavior*.

### simplifiedItemElevatorCheck
Removes check whether item entities have to move out of another entity (boat, shulker), which causes item entities to lag less at the cost of no longer moving out of boats and shulkers. This change is easily detectable with redstone and therefore clearly *non-vanilla behavior*.

### failedTransferNoComparatorUpdates
Removes comparator updates when item transfers fail. This change is detectable with redstone and therefore clearly *non-vanilla behavior*.

## License

MIT
