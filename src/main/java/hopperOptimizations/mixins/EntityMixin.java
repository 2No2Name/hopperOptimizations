package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.workarounds.EntityHopperInteraction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Feature("optimizedEntityHopperInteraction")
@Mixin(Entity.class)
public class EntityMixin {
    @Shadow
    public World world;

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At(value = "HEAD"))
    private void rememberNearbyHoppers(MovementType type, Vec3d movement, CallbackInfo ci) {
        if (!this.world.isClient && EntityHopperInteraction.canInteractWithHopper(this)) {
            EntityHopperInteraction.findHoppers = Settings.optimizedEntityHopperInteraction;
        }
    }

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At(value = "RETURN"))
    private void notifyHoppersOfExistence(CallbackInfo ci) {
        if (EntityHopperInteraction.findHoppers && !this.world.isClient && Settings.optimizedEntityHopperInteraction) {
            EntityHopperInteraction.notifyHoppersObj(this);
        }
    }
}
