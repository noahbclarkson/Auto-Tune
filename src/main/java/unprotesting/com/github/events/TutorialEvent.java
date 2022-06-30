package unprotesting.com.github.events;

import lombok.Getter;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.config.Config;
import unprotesting.com.github.util.Format;



public class TutorialEvent extends Event {

  @Getter
  private final HandlerList handlers = new HandlerList();

  private int position;

  /**
   * Sends the tutorial messages to all players.
   */
  public TutorialEvent(boolean isAsync) {
    super(isAsync);

    if (Config.get().getTutorial().isEmpty() || Bukkit.getOnlinePlayers().size() < 1) {
      return;
    }

    if (position >= Config.get().getTutorial().size()) {
      position = 0;
    }

    Component message = Format.getComponent(Config.get().getTutorial().get(position));
    Audience audience = Audience.audience(Bukkit.getOnlinePlayers());
    audience.sendMessage(message);
    position++;
  }
  
}
