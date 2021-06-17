package unprotesting.com.github.Events;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.Config.Config;

public class IPCheckEvent extends Event{

    @Getter
    private final HandlerList Handlers = new HandlerList();

    public IPCheckEvent(boolean isAsync){
        super(isAsync);
        Main.setServerIPStrings(getIP());
    }

    private String[] getIP(){
        String hostIP;
        try {
            URL url_name = new URL("http://bot.whatismyipaddress.com");
            BufferedReader sc = new BufferedReader(new InputStreamReader(url_name.openStream()));
            hostIP = sc.readLine().trim();
        } catch (IOException e) {
            e.printStackTrace();
            String offical_website = "http://autotune.xyz";
            return new String[] {offical_website, offical_website};
        }
        String base = "http://" + hostIP + ":" + Config.getPort();
        String[] output = {base + "/trade.html", base + "/trade-short.html"};
        return output;
    }
    
}
