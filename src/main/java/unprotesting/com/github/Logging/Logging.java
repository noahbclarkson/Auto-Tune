package unprotesting.com.github.Logging;

//  Global logging functions

public class Logging {

    private static String[] errors = {
        "Only players can execut this command",
        "Disabled Auto-Tune due to no Vault dependency found! Please make sure Vault and another economy plugin, such as EssentialsX is installed!",
        "A maximum of 8 sections can be allocated"
    };

    public static void log(String input){
        System.out.println("Auto-Tune: " + input);
    }

    public static void debug(String input){
        System.out.println("Auto-Tune-DEBUG: " + input);
    }

    public static void error(int error){
        System.out.println("Auto-Tune-ERROR: " + errors[error]);
    }

}
