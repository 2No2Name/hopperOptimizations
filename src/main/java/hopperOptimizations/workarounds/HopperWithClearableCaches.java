package hopperOptimizations.workarounds;

public interface HopperWithClearableCaches {
    default void clearOutputInventoryEntityCache(long timelimit) {
    }

    default void clearInputInventoryEntityCache(long timelimit) {
    }

    default void clearInputItemEntityCache(long timelimit) {
    }

    default void clearInventoryCache() {
    }
}
