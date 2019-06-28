package carpet_extension;

import carpet.settings.ParsedRule;
import carpet.settings.Rule;
import carpet.settings.SettingsManager;
import carpet.settings.Validator;
import carpet.utils.Messenger;
import net.minecraft.server.command.ServerCommandSource;

import static carpet.settings.RuleCategory.CREATIVE;

public class ExampleSettings
{
    private static class CheckFillLimitLimits extends Validator<Integer>
    {
        @Override
        public Integer validate(ServerCommandSource source, ParsedRule<Integer> currentRule, Integer newValue, String typedString)
        {
            Messenger.m(source, "rb Congrats, you just changed a setting to "+newValue);
            return newValue < 20000000 ? newValue : null;
        }
    }
    @Rule(
            desc = "Example numerical setting",
            options = {"32768", "250000", "1000000"},
            validate = {Validator.POSITIVE_NUMBER.class, CheckFillLimitLimits.class},
            category = {CREATIVE, "skyblock"}
    )
    public static int sciblockFillLimit = 32768;

    static
    {
        SettingsManager.parseSettingsClass(ExampleSettings.class);
    }
}
