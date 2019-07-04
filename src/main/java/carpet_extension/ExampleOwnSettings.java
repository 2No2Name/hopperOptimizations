package carpet_extension;

import carpet.settings.Rule;

public class ExampleOwnSettings
{
    public enum Option
    {
        OPTION_A, OPTION_B, OPTION_C
    }

    @Rule(desc = "Example integer setting", category = "misc")
    public static int intSetting = 10;

    @Rule(
            desc = "Example string type setting",
            options = {"foo", "bar", "baz"},
            extra = {
                    "This can take multiple values",
                    "that you can tab-complete in chat",
                    "but it can take any value you want"
            },
            category = "misc",
            strict = false
    )
    public static String stringSetting = "foo";

    @Rule(
            desc = "Example enum setting",
            extra = {"This is another string-type option","that conveniently parses and validates for you"},
            category = "misc")
    public static Option optionSetting = Option.OPTION_A;

    @Rule(desc = "Example bool setting", category = "misc")
    public static boolean boolSetting;
}
