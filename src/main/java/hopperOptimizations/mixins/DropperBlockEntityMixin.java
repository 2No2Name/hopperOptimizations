package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

@Feature("optimizedInventories")
@Mixin(DropperBlockEntity.class)
public class DropperBlockEntityMixin extends DispenserBlockEntity implements OptimizedInventory {
    private int viewerCount = 0;

    public DropperBlockEntityMixin() {
        throw new AssertionError();
    }

    @Nullable
    public InventoryOptimizer getOptimizer() {
        DefaultedList<ItemStack> inventory;
        return mayHaveOptimizer() && (inventory = this.getInvStackList()) instanceof InventoryListOptimized ? ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this) : null;
    }

    @Override
    public void invalidateOptimizer() {
        DefaultedList<ItemStack> inventory;
        if ((inventory = this.getInvStackList()) instanceof InventoryListOptimized)
            ((InventoryListOptimized) inventory).invalidateOptimizer();
    }

    //@Inject(method = "onInvOpen(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "HEAD"))
    public void onInvOpen(PlayerEntity playerEntity_1) {
        if (!playerEntity_1.isSpectator()) {
            invalidateOptimizer();
            viewerCount++;
        }
    }

    public void onInvClose(PlayerEntity playerEntity_1) {
        if (!playerEntity_1.isSpectator()) {
            viewerCount--;
            if (viewerCount < 0) {
                System.out.println("Dropper viewer count inconsistency, might affect performance of optimizedInventories!");
                viewerCount = 0;
            }
        }
    }

    @Override
    public boolean mayHaveOptimizer() {
        return viewerCount <= 0;
    }

}
