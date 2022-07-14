package unprotesting.com.github.events;

import lombok.Getter;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.AutoTuneLogger;
import unprotesting.com.github.util.Format;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * The event for getting the IP of the server.
 */
public class IpCheckEvent extends AutoTuneEvent {

    @Getter
    private static String ip;

    /**
     * Get the IP of the server from http://checkip.amazonaws.com.
     *
     * @param isAsync Whether to run the check in a separate thread.
     */
    public IpCheckEvent(boolean isAsync) {
        super(isAsync);

        try {
            getIpString();
        } catch (IOException e) {
            AutoTuneLogger logger = Format.getLog();
            logger.severe("Could not get IP!");
            logger.config(e.toString());
            ip = "http://autotune.xyz";
        }

    }

    private void getIpString() throws IOException {
        URL whatIsmMyIp = new URL("http://checkip.amazonaws.com");
        BufferedReader in = new BufferedReader(new InputStreamReader(whatIsmMyIp.openStream()));
        String hostIp = in.readLine();
        ip = "http://" + hostIp + ":" + Config.get().getPort() + "/trade.html";
    }

}
