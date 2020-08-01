package unprotesting.com.github.util;

import javax.script.ScriptEngineManager;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.io.FileReader;
import java.io.File;
import java.io.FileNotFoundException;

public class JavaScriptManager {

    public static void executeJSMain(){
        String path = new File("plugins/Auto-Tune/Javascript/Main.js")
                                                           .getAbsolutePath();
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        try {
            engine.eval(new FileReader(path));
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (ScriptException ex) {
            ex.printStackTrace();
        }
    }
}