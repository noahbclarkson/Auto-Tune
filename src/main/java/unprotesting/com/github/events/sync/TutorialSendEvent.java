package unprotesting.com.github.events.sync;

import java.util.HashMap;
import java.util.UUID;

import lombok.Getter;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Config;
import unprotesting.com.github.config.Messages;

public class TutorialSendEvent extends Event {

  private static HashMap<UUID, Integer> tutorialSteps = new HashMap<>();

  @Getter
  private final HandlerList handlers = new HandlerList();

  /**
   * Sends the tutorial messages.
   */
  public TutorialSendEvent() {

    if (Config.getConfig().isTutorial()) {
      sendTutorialMessages();
    }

  }

  private void sendTutorialMessages() {

    for (Player player : Bukkit.getOnlinePlayers()) {

      if (!player.hasPermission("at.tutorial")) {
        continue;
      }

      UUID uuid = player.getUniqueId();

      if (tutorialSteps.containsKey(uuid)) {
        
        if (tutorialSteps.get(uuid) >= Messages.getMessages().getTutorial().size()) {
          tutorialSteps.put(uuid, 0);
        } else {
          tutorialSteps.put(uuid, tutorialSteps.get(uuid) + 1);
        }

      } else {
        tutorialSteps.put(uuid, 0);
      }

      Component message = Main.getInstance().getMm().deserialize(
          Messages.getMessages().getTutorial().get(tutorialSteps.get(uuid)));

      player.sendMessage(message);

    }

  }
}
