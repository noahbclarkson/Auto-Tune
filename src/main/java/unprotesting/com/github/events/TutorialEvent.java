package unprotesting.com.github.events;

import java.util.List;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;

/**
 * The event for sending tutorial messages to players.
 */
public class TutorialEvent extends AutoTuneEvent {

    @Getter
    private final HandlerList handlers = new HandlerList();

    private static int position;

    /**
     * Sends the tutorial messages to all players.
     */
    public TutorialEvent(boolean isAsync) {
        super(isAsync);
        List<String> tutorial = Config.get().getTutorial();
        if (tutorial.isEmpty() || Bukkit.getOnlinePlayers().size() < 1) {
            return;
        }

        if (position >= tutorial.size()) {
            position = 0;
        }

        Component message = Format.getComponent(tutorial.get(position));
        Audience audience = Audience.audience(Bukkit.getOnlinePlayers());
        audience.sendMessage(message);
        position++;
    }

}
