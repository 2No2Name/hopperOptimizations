package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.utils.EntityHopperInteraction;
import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Feature("optimizedEntityHopperInteraction")
@Mixin(WorldChunk.class)
public class WorldChunkMixin {
    @Shadow
    private boolean loadedToWorld;

    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)V", at = @At(value = "RETURN"))
    private void notifyHoppersOfNewEntity(Entity entity, CallbackInfo ci) {
        if (this.loadedToWorld) //don't do anything if this chunk is not ticked yet. Neighboring hoppers don't have caches yet anyway. //deadlock without this line on world load!
            EntityHopperInteraction.notifyHoppersOfNewOrTeleportedEntity(entity);
    }
}
