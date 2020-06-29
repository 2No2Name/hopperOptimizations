package hopperOptimizations.mixins.nonVanillaLagReduction;

import hopperOptimizations.settings.Settings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {

    public ItemEntityMixin(EntityType<?> entityType_1, World world_1) {
        super(entityType_1, world_1);
    }


    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;doesNotCollide(Lnet/minecraft/entity/Entity;)Z"))
    private boolean doNotCheckEntities(World world, Entity itemEntity) {
        if (!Settings.simplifiedItemElevatorCheck)
            return itemEntity.world.doesNotCollide(itemEntity);
        //only do block collisions, shulkers, minecarts and boats no push out items or have the "item elevator" effect
        //2No2Name's fork of lithium contains a more general optimization for this problem, so don't enable this when using it!
        return itemEntity.world.getBlockCollisions(itemEntity, itemEntity.getBoundingBox()).allMatch(VoxelShape::isEmpty);
    }

}
