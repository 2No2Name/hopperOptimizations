package carpet_extension.mixins;

import carpet_extension.ExampleSimpleSettings;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntity_makarenaMixin extends LivingEntity
{
    @Shadow public abstract LookControl getLookControl();

    protected MobEntity_makarenaMixin(EntityType<? extends LivingEntity> entityType_1, World world_1)
    {
        super(entityType_1, world_1);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void makarena(CallbackInfo ci)
    {
        if (ExampleSimpleSettings.makarena && onGround)
        {
            int stage = age % 200;
            if (stage > 155 && world.getClosestPlayer(this, 32.0) != null)
            {
                headYaw += 2;
                prevHeadYaw += 2;
                if (stage == 199)
                {
                    addVelocity(0.0, 0.4, 0.0);
                }
            }
        }
    }

}
