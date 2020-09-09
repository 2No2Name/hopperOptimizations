package hopperOptimizations.workarounds;

public class ComparatorUpdateFakeMode {
    public static final ComparatorUpdateFakeMode UNDETERMINED = new ComparatorUpdateFakeMode(null, null, 0); //replace references to this mode with one of the others when it is used
    public static final ComparatorUpdateFakeMode NO_UPDATE = new ComparatorUpdateFakeMode(null, null, 1);
    public static final ComparatorUpdateFakeMode UPDATE = new ComparatorUpdateFakeMode(null, null, 2);

    //crazy workaround to send stupid comparator updates to comparators and make the comparators send updates to even more redstone components
    //required for comparator to schedule useless but detectable updates on themselves

    public static final ComparatorUpdateFakeMode DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE = new ComparatorUpdateFakeMode(null, null, 3);

    public static final int DEFAULT_MODE_COUNT;
    public static final ComparatorUpdateFakeMode[] DOUBLE_CHEST_MODES;

    static {
        ComparatorUpdateFakeMode[] defaultModes = {UNDETERMINED, NO_UPDATE, UPDATE, DECREMENT_STRENGTH_UPDATE_INCREMENT_STRENGTH_UPDATE};
        DEFAULT_MODE_COUNT = defaultModes.length;
        DOUBLE_CHEST_MODES = new ComparatorUpdateFakeMode[DEFAULT_MODE_COUNT * DEFAULT_MODE_COUNT];
        for (ComparatorUpdateFakeMode mode1 : defaultModes) {
            for (ComparatorUpdateFakeMode mode2 : defaultModes) {
                DOUBLE_CHEST_MODES[mode1.index + DEFAULT_MODE_COUNT * mode2.index] = new ComparatorUpdateFakeMode(mode1, mode2, -1);
            }
        }
    }

    private final ComparatorUpdateFakeMode first;
    private final ComparatorUpdateFakeMode second;
    private final int index;

    private ComparatorUpdateFakeMode(ComparatorUpdateFakeMode first, ComparatorUpdateFakeMode second, int index) {
        this.first = first;
        this.second = second;
        this.index = index;
    }

    public static ComparatorUpdateFakeMode of(ComparatorUpdateFakeMode first, ComparatorUpdateFakeMode second) {
        if (first.index >= 0 && second.index >= 0) {
            return DOUBLE_CHEST_MODES[first.index + DEFAULT_MODE_COUNT * second.index];
        }
        //only used when some other mod adds nested double inventories
        return new ComparatorUpdateFakeMode(first, second, -1);
    }

    public ComparatorUpdateFakeMode getFirst() {
        return this.first;
    }

    public ComparatorUpdateFakeMode getSecond() {
        return this.second;
    }

    public boolean isSimple() {
        return this.index >= 0;
    }

    public boolean is(ComparatorUpdateFakeMode a, ComparatorUpdateFakeMode b) {
        return this.first == a && this.second == b;
    }
}
