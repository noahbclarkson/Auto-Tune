package unprotesting.com.github.events.async;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import lombok.Getter;
import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;

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
            URL whatIsmMyIp = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatIsmMyIp.openStream()));
            hostIP = in.readLine().trim();
        } catch (IOException e) {
            e.printStackTrace();
            String official_website = "http://autotune.xyz";
            return new String[] {official_website, official_website};
        }
        String base = "http://" + hostIP + ":" + Config.getPort();
        String[] output = {base + "/trade.html", base + "/trade-short.html"};
        return output;
    }
    
}
