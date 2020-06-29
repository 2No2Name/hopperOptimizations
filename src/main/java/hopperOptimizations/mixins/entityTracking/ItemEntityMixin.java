package hopperOptimizations.mixins.entityTracking;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    public ItemEntityMixin(EntityType<?> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }

    @Shadow
    @Final
    private static TrackedData<ItemStack> STACK;

    @Override
    public void remove() {
        super.remove();
        //TODO THIS IS PROBABLY NOT WORKING!! hopper will go into cooldown when killing this entity!
        //setting the stack on remove does not have an effect on teleporting to the nether/end/overworld dimension, as the entity data is copied before removing
        //doing it is useful to be able to skip filtering item entity lists for item entities that have just died
        //used in NearbyHopperItemsTracker
        this.getDataTracker().set(STACK, ItemStack.EMPTY);
    }
}
