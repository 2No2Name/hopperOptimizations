package hopperOptimizations.utils;

import carpet.settings.ParsedRule;
import carpet.settings.Validator;
import net.minecraft.server.command.ServerCommandSource;

public class OptimizedInventoriesRule extends Validator<Boolean> {

    //used to track when the rule was changed, incrementing makes all previous optimization states invalid
    public static int ruleUpdates = 0;

    @Override
    public Boolean validate(ServerCommandSource source, ParsedRule<Boolean> rule, Boolean newValue, String previous) {
        if (ruleUpdates != -1)
            ++ruleUpdates;
        return newValue;
    }
}
