package hopperOptimizations;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.settings.SettingsManager;
import hopperOptimizations.settings.Settings;

/**
 * Hopper Optimization Mod
 *
 * @author 2No2Name
 * adapted from the fabric carpet extension example mod.
 * bloom filter contribution originally by skyrising
 */
public class HopperOptimizationsExtension implements CarpetExtension {
    private static SettingsManager mySettingManager;

    static {
        mySettingManager = new SettingsManager("0.1.15", "hopperoptimizations", "Hopper Optimizations Mod");
        CarpetServer.manageExtension(new HopperOptimizationsExtension());
    }

    public static void noop() {
    }

    @Override
    public void onGameStarted() {
        // let's /carpet handle our few simple settings
        CarpetServer.settingsManager.parseSettingsClass(Settings.class);
    }

    @Override
    public SettingsManager customSettingsManager() {
        // this will ensure that our settings are loaded properly when world loads
        return mySettingManager;
    }
}
