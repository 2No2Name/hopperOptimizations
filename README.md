# Hopper Optimizations Mod
This Mod optimizes hoppers interacting with inventories and item entities. It implements optimizations _without changing
 any observable behavior_ (besides the better performance).
 This branch of hopper optimizations **requires** installing my fork of Lithium.
 
 Performance measurements are welcome. If you find a bug, please report it on the issue tracker.
 You can support me on [patreon](https://www.patreon.com/2No2Name)

## Installation instructions
- Install the fabric mod loader. https://fabricmc.net/use/
- download both hopperOptimizations and my fork of Lithium
- put both hopperOptimizations[...].jar and lithium-fabric-fork-by-2no2name[...].jar into the `mods` folder.
- start the game (no configuration required)

## Features 
These features are speeding up minecraft hoppers, effectively fixing hopper lag. Besides the lag reduction the 
behavior is exactly like vanilla.
### Inventory Optimizations
Inventories keep extra data such as a _modification counter_, an _item type -> slot mask map_, and _empty & full slot masks_.
This allows replacing vanilla's linear search though the whole inventory with a few mask operations to find slots that
the hopper can transfer from or to. If the previous transfer attempt failed, and both the hopper and the other
inventory haven't increased their modification counter, the hopper can skip the next transfer attempt after mimicking
the comparator updates the failed transfer of the naive vanilla implementation sends.
### Entity Tracking
Hoppers register their entity search area to a modified version of Lithium's entity tracker engine (available in my
fork of Lithium).
Now entities notify hoppers about their arrival at hoppers when they move into their interaction area, instead of each
hopper searching for entities to interact with in every tick.
Only when entities move collisions with the hopper interaction areas need to be calculated.
### Cache Inventories
Hoppers keep a reference to inventory blocks (block entities, double chests, or composters) until they are removed or
otherwise become invalid. This is faster than looking up the inventory all the time.
### Fast Signal Strength
Comparators use extra data from Inventories (item counter) to determine the signal strength of an inventory without
accessing all slots.
### Cached empty state of Item stacks
Item stacks cache whether they are empty in vanilla, but at the same time the value is recalculated often for no reason.
Instead, we always use the cached value.

## License

This project uses the MIT license.
The Lithium Mod by JellySquid3 and my fork use a different license!
