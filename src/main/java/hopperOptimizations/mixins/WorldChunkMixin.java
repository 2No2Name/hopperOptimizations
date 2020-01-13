package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.EntityHopperInteraction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Feature("optimizedEntityHopperInteraction")
@Mixin(WorldChunk.class)
public class WorldChunkMixin {
    @Inject(method = "addEntity(Lnet/minecraft/entity/Entity;)V", at = @At(value = "RETURN"))
    private void notifyHoppersOfNewEntity(Entity entity, CallbackInfo ci) {
        if (Settings.optimizedEntityHopperInteraction && !entity.removed/* && entity.firstUpdate //no access, probably would eliminate some useless hopper notify calls */) {
            //when rememberHoppers is true, we are already checking for hoppers, so calling it would be redundant
            //only call for entity types that hoppers can interact with
            if (!EntityHopperInteraction.findHoppers && (entity instanceof ItemEntity || entity instanceof Inventory))
                EntityHopperInteraction.findAndNotifyHoppers(entity);
        }
    }
}
