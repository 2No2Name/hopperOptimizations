package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.utils.EntityHopperInteraction;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Feature("optimizedEntityHopperInteraction")
@Mixin(TeleportCommand.class)
public class TeleportCommandMixin {
    @Redirect(method = "teleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setHeadYaw(F)V", ordinal = 1))
    private static void notifyHoppersAndSetHeadYaw(Entity entity, float headYaw) {
        entity.setHeadYaw(headYaw);
        EntityHopperInteraction.notifyHoppersOfNewOrTeleportedEntity(entity);
    }
}
