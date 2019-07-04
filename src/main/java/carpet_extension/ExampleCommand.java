package carpet_extension;

import carpet.CarpetServer;
import carpet.utils.Messenger;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;

import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ExampleCommand
{
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher)
    {
        dispatcher.register(literal("test").
                then(literal("dump").executes((c) -> CarpetServer.settingsManager.printAllRulesToLog())).
                then(argument("first",word()).
                        executes( (c)-> {
                            Messenger.m(c.getSource(), "gi Shhhh.....");
                            return 1;
                        })).
                then(argument("second", word()).
                        executes( (c)-> listSettings(c.getSource()))));

    }

    private static int listSettings(ServerCommandSource source)
    {
        Messenger.m(source, "w Here is all the settings we manage:");
        Messenger.m(source, "w Own stuff:");
        Messenger.m(source, "w  - boolean: "+ExampleOwnSettings.boolSetting);
        Messenger.m(source, "w  - string: "+ExampleOwnSettings.stringSetting);
        Messenger.m(source, "w  - int: "+ExampleOwnSettings.intSetting);
        Messenger.m(source, "w  - enum: "+ExampleOwnSettings.optionSetting);
        Messenger.m(source, "w Carpet Managed:");
        Messenger.m(source, "w  - makarena: "+ExampleSimpleSettings.makarena);
        Messenger.m(source, "w  - useless numerical setting: "+ExampleSimpleSettings.uselessNumericalSetting);
        return 1;
    }
}
