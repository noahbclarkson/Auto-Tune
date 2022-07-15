package unprotesting.com.github.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * AutoTuneEvent class for events that are fired by AutoTune.
 */
public class AutoTuneEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public AutoTuneEvent(boolean isAsync) {
        super(isAsync);
    }

    /**
     * For classic spigot.
     *
     * @return The handler list.
     */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * For other server types.
     */
    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

}
