package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
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

    /* //replaced with code in EntityMixin
    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.BEFORE))
    private void rememberNearbyHoppers(CallbackInfo ci) {
        if (this.world.isClient) return;
        EntityHopperInteraction.findHoppers = Settings.optimizedEntityHopperInteraction;
    }*/

    /* //replaced with code in EntityMixin
    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.AFTER))
    private void notifyHoppersOfExistence(CallbackInfo ci) {
        if (this.world.isClient || !Settings.optimizedEntityHopperInteraction) return;
        EntityHopperInteraction.notifyHoppers(this);
    }*/

    /* //replaced with code in WorldChunkMixin, also handles lazy entities.
    @Feature("optimizedEntityHopperInteraction")
    @Inject(method = "tick()V", at = @At(value = "HEAD"))
    private void notifyHoppersOfExistenceOnFirstTick(CallbackInfo ci) {
        if (!this.world.isClient && firstUpdate && Settings.optimizedEntityHopperInteraction) //if this doesn't happen and the item never moves, a hopper won't find it
            EntityHopperInteraction.findAndNotifyHoppers(this);
    }*/


    @Feature("simplifyItemElevatorCheck")
    @Redirect(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;doesNotCollide(Lnet/minecraft/entity/Entity;)Z"))
    private boolean doNotCheckEntities(World world, Entity itemEntity) {
        if (!Settings.simplifiedItemElevatorCheck)
            return itemEntity.world.doesNotCollide(itemEntity);
        //only do block collisions, shulkers, minecarts and boats no push out items or have the "item elevator" effect
        return itemEntity.world.getBlockCollisions(itemEntity, itemEntity.getBoundingBox()).allMatch(VoxelShape::isEmpty);
    }

}
