package hopperOptimizations.workarounds;

import hopperOptimizations.annotation.Feature;
import hopperOptimizations.settings.Settings;
import hopperOptimizations.utils.IHopper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

@Feature("optimizedEntityHopperInteraction")
public class EntityHopperInteraction {

    public static final List<BlockPos> hopperLocationsToNotify = new ArrayList<>();
    //used to track when the rule was changed, incrementing makes all cached optimization states invalid
    public static int ruleUpdates = 0;
    public static boolean findHoppers = false;
    public static boolean searchedForHoppers = false;

    public static void notifyHoppersObj(Object object) {
        if (object instanceof Entity) notifyHoppers((Entity) object);
    }

    public static void notifyHoppers(Entity targetEntity) {
        if (!searchedForHoppers) {
            if (targetEntity.prevX != targetEntity.getX() || targetEntity.prevY != targetEntity.getY() || targetEntity.prevZ != targetEntity.getZ())
                findAndNotifyHoppers(targetEntity);
            findHoppers = false;
        } else {
            for (BlockPos pos : hopperLocationsToNotify) {
                BlockEntity hopper = targetEntity.world.getBlockEntity(pos);
                if (hopper instanceof HopperBlockEntity) {
                    ((IHopper) hopper).notifyOfNearbyEntity(targetEntity);
                }
            }
            hopperLocationsToNotify.clear();
            findHoppers = false;
            searchedForHoppers = false;
        }
    }

    public static void findAndNotifyHoppers(Entity targetEntity) {
        searchedForHoppers = true;
        findHoppers = true;

        Box box = targetEntity.getBoundingBox();
        int minX, maxX, minY, maxY, minZ, maxZ;
        minX = (int) Math.floor(box.x1) - 1;
        minY = (int) Math.floor(box.y1) - 1;
        minZ = (int) Math.floor(box.z1) - 1;
        maxX = (int) Math.ceil(box.x2);
        maxY = (int) Math.ceil(box.y2);
        maxZ = (int) Math.ceil(box.z2);

        BlockPos.Mutable blockPos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; ++x)
            for (int y = minY; y <= maxY; ++y)
                for (int z = minZ; z <= maxZ; ++z) {
                    blockPos.set(x, y, z);
                    BlockState blockState = targetEntity.world.getBlockState(blockPos);
                    if (blockState.getBlock() == Blocks.HOPPER) {
                        hopperLocationsToNotify.add(blockPos.toImmutable());
                    }
                }

        notifyHoppers(targetEntity);
    }

    public Boolean validate(ServerCommandSource source, Object rule, Boolean newValue, String previous) {
        if (ruleUpdates != -1)
            ++ruleUpdates;
        return newValue;
    }

    public static boolean canInteractWithHopper(Object object) {
        return object instanceof ItemEntity || object instanceof Inventory;
    }


    public static void notifyHoppersOfNewOrTeleportedEntity(Entity entity) {
        if (Settings.optimizedEntityHopperInteraction && !entity.removed) {
            //when rememberHoppers is true, we are already checking for hoppers, so calling it would be redundant
            //only call for entity types that hoppers can interact with
            if (!EntityHopperInteraction.findHoppers && (canInteractWithHopper(entity)))
                EntityHopperInteraction.findAndNotifyHoppers(entity);
        }
    }
}
