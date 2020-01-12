package hopperOptimizations.mixins;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.InventoryListOptimized;
import hopperOptimizations.utils.InventoryOptimizer;
import hopperOptimizations.utils.OptimizedInventory;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DefaultedList;
import net.minecraft.util.Tickable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Arrays;

@Feature("optimizedInventories")
@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin extends LootableContainerBlockEntity implements OptimizedInventory, Tickable {
    @Shadow
    protected int viewerCount;
    @Shadow
    private DefaultedList<ItemStack> inventory;
    private int invalidCount;

    protected ChestBlockEntityMixin(BlockEntityType<?> beType) {
        super(beType);
    }

    //Redirects and Injects to replace the inventory with an optimized Inventory
    @Redirect(method = "<init>(Lnet/minecraft/block/entity/BlockEntityType;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Inject(method = "setInvStackList", at = @At("RETURN"))
    private void onSetStackList(DefaultedList<ItemStack> stackList, CallbackInfo ci) {
        if (!(inventory instanceof InventoryListOptimized))
            inventory = new InventoryListOptimized(Arrays.asList((ItemStack[]) inventory.toArray()), ItemStack.EMPTY);
    }

    @Redirect(method = "fromTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/DefaultedList;ofSize(ILjava/lang/Object;)Lnet/minecraft/util/DefaultedList;"))
    private DefaultedList<ItemStack> createInventory2(int int_1, Object object_1) {
        return InventoryListOptimized.ofSize(int_1, (ItemStack) object_1);
    }

    @Nullable
    public InventoryOptimizer getOptimizer() {
        return mayHaveOptimizer() && inventory instanceof InventoryListOptimized ? ((InventoryListOptimized) inventory).getCreateOrRemoveOptimizer(this) : null;
    }

    @Override
    public void invalidateOptimizer() {
        if (inventory instanceof InventoryListOptimized) ((InventoryListOptimized) inventory).invalidateOptimizer();
    }

    @Inject(method = "onInvOpen(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "HEAD"))
    private void onInventoryOpened(PlayerEntity playerEntity_1, CallbackInfo ci) {
        if (Settings.playerInventoryDeoptimization && !playerEntity_1.isSpectator())
            invalidateOptimizer();
    }

    @Override
    public boolean mayHaveOptimizer() {
        return !this.world.isClient && (!Settings.playerInventoryDeoptimization || viewerCount <= 0);
    }


    //Making sure that DoubleInventories don't act on invalid chest halfs using counter comparison.
    public void markRemoved() {
        this.invalidCount++;
        if (invalidCount == 0) invalidCount = -1;
        super.markRemoved();
    }

    public int getInvalidCount() {
        return invalidCount;
    }
}
