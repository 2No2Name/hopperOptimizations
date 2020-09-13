package hopperOptimizations.mixins.fix_vanilla;


import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;


@Mixin(StorageMinecartEntity.class)
public abstract class StorageMinecartEntityMixin extends AbstractMinecartEntity implements Inventory {

    protected StorageMinecartEntityMixin(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @ModifyConstant(method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V", constant = @Constant(intValue = 36))
    private int setCorrectSize(int int_1) {
        return this.size();
    }

    @ModifyConstant(method = "<init>(Lnet/minecraft/entity/EntityType;DDDLnet/minecraft/world/World;)V", constant = @Constant(intValue = 36))
    private int setCorrectSize2(int int_1) {
        return this.size();
    }
}
