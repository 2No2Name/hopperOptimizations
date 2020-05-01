package hopperOptimizations.lithium;

import me.jellysquid.mods.lithium.common.LithiumMod;
import me.jellysquid.mods.lithium.common.config.LithiumConfig;
import me.jellysquid.mods.lithium.common.entity.tracker.nearby.ExactPositionListener;

public class LithiumInteractions {

    static boolean lithiumEntityTrackerEngineAvailable;

    boolean canUseLithiumEntityTrackerEngine() {
        try {
            LithiumConfig lithiumConfig = LithiumMod.CONFIG;
            if (lithiumConfig == null) {
                //throw exception somewhere so the catch block doesn't complain
                throw new ClassNotFoundException();
            }
            Class exactPositionListener = ExactPositionListener.class;
            useVariable(exactPositionListener);


            return lithiumConfig.ai.useNearbyEntityTracking;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private int useVariable(Object o) {
        int i = 0;
        return i;
    }

    boolean getLithiumEntityTrackerEngine() {
        return false;
    }
}
