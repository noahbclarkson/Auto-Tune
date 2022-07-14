package unprotesting.com.github.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class AutoTuneEvent extends Event {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    public AutoTuneEvent(boolean isAsync) {
        super(isAsync);
    }

    // for classic spigot
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

}
