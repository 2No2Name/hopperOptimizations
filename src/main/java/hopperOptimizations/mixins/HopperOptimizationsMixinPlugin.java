package hopperOptimizations.mixins;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class HopperOptimizationsMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {

    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("hopperOptimizations.mixins.")) {
//            if (mixinClassName.startsWith("hopperOptimizations.mixins.entityTracking.")) {
//                try {
//                    ExactPositionListener.class.getClass();
//                    return true;
//                } catch (Exception ignored) {
//                    System.out.println("Mixin for entity tracking disabled because the required mod 'lithium fork by 2No2Name' is not present");
//                    return false;
//                }
//            }
            return !mixinClassName.startsWith("hopperOptimizations.mixins.nonVanillaLagReduction");
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {

    }
}
