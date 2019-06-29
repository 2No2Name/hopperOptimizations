package carpet_extension;

import carpet.settings.ParsedRule;
import carpet.settings.Rule;
import carpet.settings.SettingsManager;
import carpet.settings.Validator;
import carpet.utils.Messenger;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;

import static carpet.settings.RuleCategory.CREATIVE;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
public class ExampleSettings
{
    /**
     *  Custom validator class for your setting. If validate returns null - settings is not changed.
     */
    private static class CheckValue extends Validator<Integer>
    {
        @Override
        public Integer validate(ServerCommandSource source, ParsedRule<Integer> currentRule, Integer newValue, String typedString)
        {
            Messenger.m(source, "rb Congrats, you just changed a setting to "+newValue);
            return newValue < 20000000 ? newValue : null;
        }
    }

    /**
     *  Simple numeric setting
     */
    @Rule(
            desc = "Example numerical setting",
            options = {"32768", "250000", "1000000"},
            validate = {Validator.POSITIVE_NUMBER.class, CheckValue.class},
            category = {CREATIVE, "examplemod"}
    )
    public static int uselessNumericalSetting = 32768;


    /**
     * You can define your own catergories. It makes sense to create new category for all settings in your mod.
     */
    @Rule(desc="makes mobs dance Makarena", category = {"fun", "examplemod"})
    public static boolean makarena = false;

    static
    {
        // just make sure your settings are used in Mixins, which would load this class and register your settings
        // otherwise this static code won't be found.
        // you can always add more rules in runtime later.

        // register your settings
        SettingsManager.parseSettingsClass(ExampleSettings.class);

        // set-up a snooper to observe how rules are changing in carpet
        SettingsManager.addRuleObserver( (source, rule, str) ->
        {
            if (rule.categories.contains("examplemod"))
            {
                Messenger.m(source, "gi Psssst... make sure not to change not to touch original carpet rules");
            }
            else
            {
                try
                {
                    Messenger.print_server_message(
                            source.getMinecraftServer(),
                            "Ehlo everybody, "+source.getPlayer().getName().getString()+" is cheating..."
                    );
                }
                catch (CommandSyntaxException ignored) { }
            }
        });
    }
}
