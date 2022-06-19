package unprotesting.com.github.events.sync;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import unprotesting.com.github.Main;
import unprotesting.com.github.config.Messages;
import unprotesting.com.github.util.UtilFunctions;

public class JoinMessageEventHandler implements Listener {

  /**
   * Handles the PlayerJoinEvent.
   * @param e The PlayerJoinEvent.
   * @see PlayerJoinEvent
   */
  @EventHandler
  public void onPlayerJoin(PlayerJoinEvent e) {

    double gdp = Main.getInstance().getDb().getGdp();
    double balance = Main.getInstance().getDb().getBalance();
    double loss = Main.getInstance().getDb().getLoss();
    double inflation = Main.getInstance().getDb().getInflation();
    double population = Main.getInstance().getDb().getPopulation();

    TagResolver resolver = TagResolver.resolver(Placeholder.parsed(
        "player", e.getPlayer().getName()),
        Placeholder.parsed("gdp", UtilFunctions.getDf().format(gdp)),
        Placeholder.parsed("balance", UtilFunctions.getDf().format(balance)),
        Placeholder.parsed("loss", UtilFunctions.getDf().format(loss)),
        Placeholder.parsed("inflation", UtilFunctions.getDf().format(inflation)),
        Placeholder.parsed("gdp-per-capita", UtilFunctions.getDf().format(gdp / population)),
        Placeholder.parsed("balance-per-capita",
         UtilFunctions.getDf().format(balance / population)),
        Placeholder.parsed("loss-per-capita", UtilFunctions.getDf().format(loss / population)),
        Placeholder.parsed("inflation-per-capita",
         UtilFunctions.getDf().format(inflation / population)));

    // Loop through all the onJoin messages and send them to the player.
    for (String message : Messages.getMessages().getOnJoin()) {
      e.getPlayer().sendMessage(Main.getInstance().getMm().deserialize(message, resolver));
    }

  }
}
