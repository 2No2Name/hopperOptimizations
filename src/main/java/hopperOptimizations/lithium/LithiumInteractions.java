package hopperOptimizations.lithium;

import me.jellysquid.mods.lithium.common.LithiumMod;
import me.jellysquid.mods.lithium.common.config.LithiumConfig;

public class LithiumInteractions {

    boolean canUseLithiumEntityTrackerEngine() {
        try {
            LithiumConfig lithiumConfig = LithiumMod.CONFIG;
            if (lithiumConfig == null) {
                //throw exception somewhere so the catch block doesn't complain
                throw new ClassNotFoundException();
            }

            return lithiumConfig.ai.useNearbyEntityTracking;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    boolean getLithiumEntityTrackerEngine() {
        return false;
    }
}
